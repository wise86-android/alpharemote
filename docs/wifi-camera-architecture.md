# Wi-Fi camera module (`:feature:wificamera`)

Remote control over the camera's own Wi-Fi access point, using Sony's legacy Camera Remote API
(HTTP + JSON-RPC). Separate from the app module's BLE remote, which stays as it is: BLE sends
blind button presses, this reads and writes actual values and will carry live view.

Protocol reference: `/Users/wise/Android/SonyOriginal/PROTOCOL.md`. Section numbers below point
into it.

## Getting the credentials: NFC

Touching the camera is the whole setup. The tag carries the SSID and password, and the camera's
own NFC controller switches its Wi-Fi on in response — **nothing is sent to the camera**. The app
reads the tag, then waits for that access point to appear, which the existing
`WifiNetworkSpecifier` request does naturally (PROTOCOL.md §1.2).

An `NDEF_DISCOVERED` filter on MIME type `application/x-sony-pmm` in the app manifest launches the
app straight into the Wi-Fi screen on a tap. `MainActivity` is `singleTop` and also enables
foreground dispatch while resumed, so a tap during a live session arrives at `onNewIntent` rather
than being handed to another app or relaunching this one.

Credentials are cached in the module's own DataStore so reconnecting needs no second tap; the
screen offers "Forget this camera" to clear it. There are no hardcoded credentials anywhere.

### The TLV length is not certain

`SonyNfcTagParser` walks a `tag(2) length(2) value` list. The decompiled app computes the length
as `high * 10 + low` — decimal, not a big-endian shift — which the protocol flags as looking wrong
above 99 and asks to be verified against real hardware.

The two readings agree for every length below 100, and an SSID caps at 32 bytes with a password at
63, so a real tag almost certainly never distinguishes them. Rather than gamble, the parser walks
with the standard big-endian reading and retries with the decompiled one if that produces a
structure that does not fit the buffer. A tag that fits neither is rejected rather than
half-read.

Tag `10 03`, when a body writes it, carries the device description URL. It is tried once before
SSDP as a fast path and never relied on — it goes stale as soon as the camera restarts its Wi-Fi.

## Layering

```
       WifiCameraScreen
              │  observes UiState, calls select()
       WifiCameraViewModel
              │  depends only on the interface
  domain/WifiCameraRepository ◄──── implemented by ─── data/DefaultWifiCameraRepository
                                                              │
                 ┌──────────────────┬────────────────────┬────┴──────────┐
            CameraNetwork      SsdpDiscovery        ScalarWebClient   LiveViewStream
          (join AP, bind)   (M-SEARCH + NOTIFY)     (JSON-RPC)      (frame parser)
                 └──────────────────┴─── NetworkHttpClient ──┴───────────┘
```

State flows up as `StateFlow`; commands go down as suspend calls. Nothing above the repository
sees a socket, a `Network`, or a `JsonObject`.

### `domain/`

Pure Kotlin, no Android, no JSON.

- `CameraSettingId` — the settings we understand. Each entry records both its `getEvent` type and
  its API name, so no other file hardcodes a method name or a JSON key.
- `CameraOption` — `label` (for people) plus `param` (the exact JSON the camera expects). They
  differ only for exposure compensation, which travels as an index and reads as "+0.3", but
  keeping them separate everywhere means the UI never special-cases a setting.
- `CameraSetting` — current value, what is selectable *right now*, and whether a setter exists.
- `CameraSnapshot` — the whole camera state as one immutable value.
- `WifiCameraConnection` — the connect pipeline as distinct states, because each stage fails
  differently and the advice to the user differs with it.
- `WifiCameraRepository` — the module's entire surface.

### `data/net/`

`CameraNetwork` joins the AP with a `WifiNetworkSpecifier` and hands out the resulting `Network`.
`NetworkHttpClient` opens every connection through `Network.openConnection`.

**This binding is the single most important detail in the module.** The camera AP has no uplink,
so an unbound request goes out over cellular and times out in a way that looks exactly like a
broken camera (§1.3, and the first entry in §8's list of pitfalls).

### `data/discovery/`

`SsdpDiscovery` runs a *repeating* M-SEARCH and a NOTIFY listener at the same time. Either alone
loses cameras: a camera whose Wi-Fi is still coming up ignores the first M-SEARCH, and one that
has just restarted announces itself by NOTIFY and may never answer a search (§1.4).

`DeviceDescriptionParser` reads the two XML documents. It matches elements on their *local* name
because the Sony extensions arrive under an `av:` prefix, and it joins relative URLs by string
concatenation rather than proper resolution — that is what the official app does and what the
camera's paths require (§1.5).

### `data/rpc/`

`ScalarWebClient` speaks JSON-RPC and negotiates versions: the same method exists at different
versions on different bodies, so it walks a preference list down and caches what worked.

`CameraEventParser` turns `getEvent` responses into snapshots. **It keys entries on their `type`
field, not on their array position.** The documented positional layout is inferred from the
official app's constructor order, grows at the end with every API version, and §2.5 warns to
verify it before shipping. Every entry carries its own `type`, so keying on that is both simpler
and immune to the indices being wrong.

Merging rather than replacing is required: a long poll reports only what changed and nulls the
rest.

### `data/liveview/`

`LiveViewStream` strips the 136-byte Sony header that wraps every frame (§3.2). The stream is
**not** MJPEG and must never be handed to a video player. It resynchronises byte by byte after
corruption, because one dropped byte would otherwise ruin every following frame.

## How a change on the camera reaches the screen

`getEvent` is called in a loop with `longPoll = true`. The camera holds the connection open until
something changes, then answers with just the difference. The repository merges that into
`camera`, the ViewModel maps it, Compose recomposes. Nothing polls, and no code path is special
to "changed on the body" versus "changed from the app" — writes go to the camera and come back
the same way, which is what stops the two views from drifting apart.

## Live view

`WifiCameraRepository.liveView()` is a cold `Flow<LiveViewFrame>`: collecting it calls
`startLiveview`, and ending collection calls `stopLiveview`.

Three things about it are deliberate.

**Frames are dropped, never queued.** The flow is `conflate()`d at both the transport and the
decode stage. A viewfinder that falls behind and catches up later is worse than one that skips —
latency must not grow without bound.

**Cancelling closes the socket.** A thread blocked in a socket read is not at a suspension point
and cannot observe cancellation, so the read loop registers `invokeOnCompletion` to close the
stream. Without it, leaving the screen would leave the camera streaming until the read timeout
expired. `stopLiveview` then runs under `NonCancellable`, but time-bounded — the usual reason for
cancelling is that the network went away, and an unbounded call would hang on its connect timeout.

**Frames are their own flow, not part of `UiState`.** At ~30 fps, folding them into the state
object would make every chip and readout a recomposition candidate at that rate for a value none
of them read. `WifiCameraViewModel.liveView` is separate, and `WhileSubscribed` on it is what
starts and stops the camera's stream as the screen comes and goes.

**Decoding reuses its bitmaps.** `LiveViewBitmapPool` decodes through
`BitmapFactory.Options.inBitmap` into a ring of three buffers, so a steady stream settles at zero
allocations instead of ~30 full-size bitmaps a second.

The hazard with reuse is overwriting a bitmap that is still being drawn. Compose's draw phase only
records the bitmap into a display list; the GPU reads it later on the render thread, so "the
composable returned" does not mean the pixels are finished with, and no callback means that either.
Rather than trying to track it, the ring makes the reuse distance long enough not to matter: three
buffers at 30 fps leaves a buffer untouched for ~100 ms against a render pipeline measured in
single-digit milliseconds. `CAPACITY` is the dial if a frame ever tears.

Two details that are easy to get wrong: the `ImageBitmap` wrapper is new every frame even though
the bitmap is not — its identity is what tells `StateFlow` and Compose there is a new frame, and
reusing it freezes the viewfinder on the first image. And `clear()` drops references rather than
calling `recycle()`, because the last frame is very likely still on screen.

Frame counts and allocation counts are logged every 300 frames on tag `WifiCameraLiveView`.

Frame-info payloads (type `0x02`, AF boxes) are parsed and discarded — the α6600 cannot send them
anyway, since it rejects `setLiveviewFrameInfo`.

## UI

`WifiCameraScreen` routes: connected shows `CameraControlScreen`, anything else shows the
connection panel with the failure detail. The mockup assumes a live camera, so the states before
that have to live somewhere.

`CameraControlScreen` is the camera back — live view behind a HUD, values along the bottom. It has
its own fixed dark palette (`ui/theme/CameraTheme.kt`) rather than the app's Material theme,
because it sits over a picture and follows camera conventions: dark regardless of system setting,
values in amber, labels quiet.

Nothing in it comes from a preset list. Every value, and every option offered for it, is what the
camera reported — which is the only correct source, since what is selectable changes with the body
and the shooting mode. A setting the camera has not mentioned reads `--`; a setting with no setter
in the current mode is shown dimmed and is not touchable.

`liveView` is a slot parameter defaulting to a placeholder, so the video stream drops in without
this file changing.

**The drum picker commits on settle, never while moving.** Every commit is a `set<X>` request; one
per scroll frame would flood the camera. The centred item is measured from `layoutInfo` rather than
derived from scroll offsets, so content padding and item sizes cannot skew it.

Two `@Preview`s cover the connected and nothing-reported-yet states, so the layout can be worked on
without a camera.

## Shooting

**The half-press is part of the capture sequence, not an extra.** The camera refuses
`actTakePicture` with error 40400 until autofocus has been engaged, and it returns that code with
an *empty message*, so the refusal looks like a mode problem or a busy camera. PROTOCOL.md §2.4
confirms this on the α6600 and ranks it the most expensive pitfall in the document.

The shutter button is therefore press-and-hold, mapping onto the protocol directly:

| gesture | call |
|---|---|
| press | `actHalfPressShutter` |
| release inside the button | `actTakePicture`, then `cancelHalfPressShutter` |
| release outside / cancelled | `cancelHalfPressShutter` |

`cancelHalfPressShutter` is sent whatever happens, including on failure, or the camera is left
holding focus.

A quick tap gives autofocus no time at all, so a 40400 straight after the half-press is treated as
"not settled yet" rather than a refusal: it waits ~350 ms and tries again, a few times. 40403 is
not a failure either — the shutter fired and the exposure is running, so it polls
`awaitTakePicture` until that yields.

### Focus feedback

There is no dependable focus-locked signal on the α6600. `setLiveviewFrameInfo` returns error 12,
so no AF boxes arrive on the live view stream (§3.3), and `getEvent` has no documented focus
entry.

Two things hedge against that. `CameraSnapshot.focusStatus` is parsed opportunistically from a
`focusStatus` event entry if a body ever sends one — null means "this camera does not say", not
"not focused" — and the shutter ring turns green when it reports `Focused`. And any event entry
the parser does not handle is named in the log once, so an undocumented focus report would be
discovered rather than silently ignored.

## Downloading photos (push transfer)

The camera has two modes and they are not interchangeable. `WifiCameraConnection.Connected`
carries a `CameraMode`, discovered from the advertised services on every connection, and the UI
routes on it: `CameraControlScreen` for remote shooting, `DownloadScreen` for transfer. In
transfer mode there is no live view, no settings and no shutter, so there is nothing of the camera
back left to show.

Switching mode on the body **restarts the camera's access point**, so the session loop re-issues
its network request after a drop rather than giving up — a lost `WifiNetworkSpecifier` request is
not re-satisfied on its own.

### The session is a contract

`X_TransferStart` → `X_GetPushRoot` → per-file `X_TransferProgress` → `X_TransferEnd`. The last is
sent from a `finally` under `NonCancellable`, on every path including failure and cancellation
(`ErrCode` 0/1/2). Skip it and the camera sits on "Connecting…" indefinitely.

Two control URLs come from the same description and are not interchangeable: `XPushList` takes the
session actions, `ContentDirectory` takes `Browse`. Swapping them returns HTTP 500.

Several strings are byte-exact on purpose — the space in `encoding= "UTF-8"`, Sony's misspelled
`NumTransferd`, the `X-AV-Client-Info` header. The camera's parser was written against Sony's own
client, not against the spec.

### Listing

Single selections carry their URLs in the Digital Imaging document and need no browsing at all.
Everything else is browsed, **paging** on `TotalMatches` (a page is capped at 50, so a 91-image
selection silently loses 41 without the loop) and **descending into containers** (`PushRoot` may
hold date folders rather than items). Both are required; either alone loses photos.

### Picking the rendition

Quality is read from `sony.com_PN`, falling back to `DLNA.ORG_PN`. **A missing profile means the
original**, which is the opposite of the intuitive reading and the documented way to end up
downloading thumbnails — the full-size file usually carries no profile at all.

Only `LARGE` and above are saved; an item offering nothing better than a thumbnail is skipped and
counted, not silently downgraded. RAW is not retrievable over this protocol at all, so an `.ARW`
selection yields the camera's JPEG rendition.

Files land in `Pictures/AlphaRemote` via MediaStore, written `IS_PENDING` and published on
completion so an interrupted transfer leaves no truncated image in the gallery.

### Why a Worker

`PhotoDownloadWorker` is a foreground `CoroutineWorker`. A full card is minutes of work that must
outlive the screen, and the foreground promise also keeps the process alive — which matters more
than usual, because the camera's network is held by a request owned by this process. The UI reads
progress back from `WorkManager` rather than owning it, so closing and reopening the screen shows
the transfer still running.

## Cleartext HTTP

The camera serves everything over plain HTTP, which Android refuses by default from API 28. A
network security config permits cleartext for the camera's address only, rather than switching it
on app-wide — the camera is its own access point with no uplink, so the grant covers traffic that
cannot leave the link.

Android's config supports **neither wildcards nor CIDR**, so a range like `192.168.*` cannot be
expressed and each address must be listed exactly. `192.168.122.1` is the SoftAP gateway Sony
bodies use in Wi-Fi Direct mode, which is the only mode this module uses.

To keep a mismatch from being mysterious, the repository asks `NetworkSecurityPolicy` about the
discovered address *before* connecting and reports `CLEARTEXT_BLOCKED` naming the address and the
file to add it to — rather than letting it surface as an opaque transport error.

## Known limits

- **Legacy bodies only.** A7 III/IV, A7R IV/V, A9, A1 and ZV bodies speak PTP/IP on port 15740,
  a different transport entirely. The module detects this and reports `UNSUPPORTED_PROTOCOL`
  rather than failing obscurely (§0).
- **The camera must be in "Ctrl w/ Smartphone".** In "Send to Smartphone" it advertises transfer
  services and no `camera` service, and there is no API to switch it back — `setCameraFunction`
  does not exist on these bodies. Reported as `WRONG_CAMERA_MODE` with instructions (§1.1).
- **No metering mode.** The mockup shows a metering chip, but the legacy API has no such property
  on any documented version. It cannot be read or written over this transport.
- **Battery and shots remaining** come from `getEvent` only on bodies and versions that report
  them; both are nullable for that reason. The α6600's `camera` service answers at version 1.0,
  where `batteryInfo` is absent.
- **Candidates come only from `getEvent`.** Settings whose candidate list the camera omits — white
  balance is the usual one — show a current value but no options. Calling `getAvailable<X>` on
  connect would fill these in; not done yet.
