# Wi-Fi camera module (`:feature:wificamera`)

Remote control over the camera's own Wi-Fi access point, using Sony's legacy Camera Remote API
(HTTP + JSON-RPC). Separate from the app module's BLE remote, which stays as it is: BLE sends
blind button presses, this reads and writes actual values and will carry live view.

Protocol reference: `/Users/wise/Android/SonyOriginal/PROTOCOL.md`. Section numbers below point
into it.

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

Already parseable, not yet drawn. `WifiCameraRepository.liveView()` is a cold `Flow<LiveViewFrame>`
that starts the stream on collection and stops it on cancellation, and is `conflate()`d so a slow
collector drops frames instead of building a queue — latency must not grow without bound. Drawing
it needs a `BitmapFactory` decode on a background thread and an `Image` composable; the transport
underneath will not change.

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
