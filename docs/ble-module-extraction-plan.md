# Plan: extract `core:ble` and `feature:ble`, and reach the camera's Wi-Fi over BLE

**Status: all six steps are implemented.** Every step below now carries a short "As built" note
where the real implementation sharpened or departed from the original sketch — read those before
touching the code. Step 6 in particular departed from its own sketch in several load-bearing ways
(see its note): a new `core:ui` module had to be introduced, the `*Entries.kt` split turned out to
cut through `camera/` and `ui/settings/` at the file level rather than the folder level, and a
cross-feature dependency that didn't exist when this plan was written (`feature:ble` registering
`feature:wificamera`'s standing BLE manager) had to be relocated to a new `app`-level
`Application` subclass to keep the "no feature depends on another feature" rule intact.

## Goal

`feature:wificamera` gets the camera's SSID and password, and switches the camera's Wi-Fi on,
**over BLE** — with no NFC tap and no hardcoded credentials.

That is worth doing for one specific reason: Android already starts this app through Companion
Device when the paired camera appears. So the whole flow becomes *power on the camera → app
starts → camera Wi-Fi comes up → connected*, with nothing to tap. BLE is also the only path in
PROTOCOL.md §1.2 that does all three of waking the camera, enabling its Wi-Fi, and supplying
credentials.

It also sidesteps a problem NFC cannot solve: Sony's tag carries an Android Application Record
naming `com.sony.playmemories.mobile`, which outranks our intent filter whenever the app is not
already in the foreground, sending a cold tap to the Play Store. Foreground dispatch wins; a cold
start cannot. See `CameraNfcReader.logRecords`, which prints the AAR to confirm this on real
hardware.

## End state

```
                    app  (composition root: MainActivity, Navigation, *Entries.kt, theme wiring)
                  ╱   │   ╲
      feature:ble     │    feature:wificamera      feature:dof
             ╲        │       ╱          ╲
              ╲       │      ╱            ╲
               core:ble  (connection …)   core:ui (theme, shared by app + feature:ble)
```

No feature depends on another feature. Both BLE-touching features contribute a `BleServiceManager`
to one shared connection and never refer to each other — `app`'s `AlphaRemoteApplication` is what
wires `feature:wificamera`'s standing manager into `feature:ble`'s connection, since neither feature
may know about the other (see step 6's "As built" note). `core:ui` did not exist when this plan was
first drafted; it was added during step 6 once `ui/theme` turned out to be needed by both `app` and
`feature:ble` with no valid one-sided placement.

**Split the responsibilities by this rule:**

- `core:ble` — *how to talk to a camera over GATT.* No Sony product knowledge beyond framing.
- `feature:ble` — *when to be connected*, plus the remote control feature itself.
- `feature:wificamera` — owns the Wi-Fi handover protocol, because reading `CC06`/`CC07` is Wi-Fi
  knowledge, not BLE-remote knowledge.

### Why not a port/adapter instead

An earlier option was to leave the BLE code in `app`, have `feature:wificamera` declare an
interface, and let `app` implement it. That works and is less churn. It was rejected for the end
state because the handover protocol would then live in `app` — far from the module that owns
everything else about Wi-Fi.

**The port is still the right fallback** if steps 3–5 turn out too disruptive: stop after step 2,
declare `CameraWifiActivator` in `feature:wificamera/domain`, implement it in `app`. Steps 1 and 2
are not wasted either way.

## Order of work

Each step is independently shippable and leaves the app working. **Steps 1–5 deliver the goal.**
Step 6 is hygiene and deliberately off the critical path.

---

### Step 1 — Parameterise `CameraBLE`'s service managers

The enabling change, and the smallest.

`camera/CameraBLE.kt` currently hardcodes its three managers:

```kotlin
private val genericAccessService = GenericAccessService()
private val remoteControlService = RemoteControlService()
private val locationService = LocationService()
private var managedService = listOf(...)
```

Make the list a constructor parameter. `service/AlphaRemoteService.kt:234` is the only construction
site and becomes the place that decides which managers exist.

**Done when:** the app builds and behaves identically, with the manager list supplied from outside
`CameraBLE`.

**As built:** the coupling ran deeper than the sketch above shows. `CameraBLE` did not just
construct its three managers — it exposed *typed* pass-through accessors for them
(`deviceName`, `deviceStatus`, `remoteCommandStatus`, `locationUpdateStatus`) and Sony-specific
methods (`executeCameraActionStep`, `setCameraLocation`, `enableLocationSync`) that reached
directly into `RemoteControlService`/`LocationService`. That is exactly the kind of Sony product
knowledge `core:ble` is not supposed to carry. Fixed by removing all of it: `CameraBLE` now only
does GATT plumbing and callback fan-out, and `AlphaRemoteService` constructs
`GenericAccessService`/`RemoteControlService`/`LocationService` itself, holds the references it
needs (`remoteControlService` as a field), and reads their state directly instead of through
`CameraBLE`. `LocationSyncController.start()` now takes a `LocationService` instead of a
`CameraBLE` for the same reason.

---

### Step 2 — Create `core:ble` and move the mechanism into it

A pure file move — no behaviour change, no ownership change.

| From `app` | To `core:ble` |
|---|---|
| `camera/CameraBLE.kt` | `CameraBLE.kt` |
| `camera/ble/BLEOperation.kt` | `BLEOperation.kt` |
| `camera/ble/BleCommandQueue.kt` | `BleCommandQueue.kt` |
| `camera/ble/BleConnectionState.kt` | `BleConnectionState.kt` |
| `camera/ble/BleServiceManager.kt` | `BleServiceManager.kt` |
| `camera/ble/utils.kt` | `utils.kt` |
| `camera/ble/GenericAccessService.kt` | `GenericAccessService.kt` (device name — generic) |

`RemoteControlService` and `LocationService` **stay in `app`** for now: they are the remote's
policy, and they move again in step 6.

Namespace `org.staacks.alpharemote.core.ble`. Needs `BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN` in its own
manifest. Keep `utils/PermissionUtils.kt` reachable — either duplicate the guard into `core:ble` or
move it to a `core:common`; do not let `core:ble` depend on `app`.

**Done when:** `app` builds against `core:ble` and the remote still works on hardware.

**As built:** exactly as planned — `GenericAccessService.kt` was the only file whose move was
worth a second look, and it turned out to have no Sony-specific knowledge either (it reads the
standard GATT Generic Access service), so it belongs in `core:ble` as documented. Nothing in
`RemoteControlService`/`LocationService` needed a new `PermissionUtils` dependency on `core:ble` —
neither file calls a permission-check function, only the `@RequiresPermission` annotation, so that
concern in the original plan text turned out not to apply.

---

### Step 3 — Move connection ownership into `core:ble`

The real work, and the part that makes a second consumer possible at all.

Today `AlphaRemoteService` *owns* the `CameraBLE` instance. A GATT connection is a single physical
resource; two owners would fight over it. Introduce a process singleton:

```kotlin
// core:ble
object CameraBleConnection {
    val state: StateFlow<BleConnectionState>
    fun register(vararg managers: BleServiceManager)   // called once, at composition
    fun connect(device: BluetoothDevice)
    fun disconnect()
}
```

`AlphaRemoteService` keeps the foreground service, the notification and the camera-action queue,
but becomes a **client** of the connection rather than its owner. `CompanionAlphaRemoteService`
keeps driving connect/disconnect on device presence.

Watch: the Companion Device lifecycle currently creates and destroys `CameraBLE` directly, so the
teardown path needs care — a stale singleton holding a dead `BluetoothGatt` is the likely bug.

**Done when:** connect, disconnect, bonding and the remote buttons behave as before, including
after the camera goes out of range and returns.

**As built:** the single `register(vararg managers)` in the sketch turned out to conflate two
things that need different lifetimes, and splitting them is the load-bearing decision of this
step:

- **Per-connection managers** — `GenericAccessService`/`RemoteControlService`/`LocationService`.
  Fresh instances every time, exactly as `CameraBLE` itself always was, so no manager's state (a
  cached device name, a pending command) survives into a reconnect that might be to a different
  camera. These are passed to `connect(context, device, managers)` directly, not through
  `register`.
- **Standing managers** — anything that wants to be attached whenever a connection exists, without
  itself deciding when one should. `WifiHandoverService` (step 4) is the only one so far. These go
  through `register(manager)`, which had to be made idempotent by reference: the component calling
  it (`AlphaRemoteService.onCreate`) is itself recreated on every reconnect cycle, so `register`
  being safe to call every time is what keeps the standing list from growing unbounded.

`CameraBleConnection.connect()` also self-clears its held `CameraBLE` the moment the connection
reports `Disconnected`, rather than relying on `AlphaRemoteService.onDisconnect()` to remember to —
this is the direct fix for the "stale singleton" risk named below, not just documentation of it.

One more consequence: with the connection itself now living independently of any one
`AlphaRemoteService` instance, that service no longer needs to cancel and rebuild its collectors
on every reconnect (`connectionJob`/`connectionScope`). It sets them up once, in `onCreate`, and
they simply keep observing `CameraBleConnection.state` and the per-connection services' flows
across however many connect/disconnect cycles follow.

---

### Step 4 — `feature:wificamera` contributes `WifiHandoverService`

Add `feature:wificamera → core:ble`. New file in
`feature/wificamera/data/ble/WifiHandoverService.kt`, implementing `BleServiceManager` against
service `8000CC00-CC00-FFFF-FFFF-FFFFFFFFFFFF` (characteristics matched by UUID **prefix**, MTU
158). Sequence from PROTOCOL.md §6:

1. Subscribe to `CC05` (write CCCD `00002902-…`).
2. Write `01` to `CC08` — turn Wi-Fi on.
3. Await the `CC05` notification; `[3] == 1` means Wi-Fi launched, `[4]` is a failure reason.
4. Read `CC06` → SSID. Read `CC07` → password. **Both ASCII from byte 3 onward.**

`BleCommandQueue` already exposes `suspend` `write`/`read`/`subscribe` and `BleServiceManager`
already receives a per-connection `CoroutineScope`, so this is a linear coroutine, not a callback
machine.

Public surface, consumed by the existing Wi-Fi code:

```kotlin
// feature:wificamera
object WifiHandover {
    fun serviceManager(): BleServiceManager        // handed to the connection by `app`
    val availability: StateFlow<Availability>      // is a camera BLE-connected right now?
    suspend fun activateWifi(): Result<WifiCredentials>
}
```

`app` wires it at composition:

```kotlin
CameraBleConnection.register(
    RemoteControlService(),
    LocationService(),
    WifiHandover.serviceManager(),
)
```

**Only trigger `activateWifi()` on demand** — from the Connect button, never automatically on every
BLE connect. Writing `01` to `CC08` turns the camera's Wi-Fi on, and doing that unprompted drains
the camera's battery all day.

**Done when:** `WifiHandover.activateWifi()` returns real credentials from the camera.

**As built:** matches the sketch, plus two things the sketch didn't mention. First,
`CameraBLE.PREFERRED_CONNECTION_MTU` was 153, tuned previously for the remote-control service;
PROTOCOL.md §6 documents 158 for this service. Since one MTU is negotiated for the whole GATT
connection (not per-service), it was raised to 158 — larger only ever gives the remote-control
characteristics more room, never less, but this is the one change in the whole plan that really
does need hardware to confirm, since there is no way to verify a BLE MTU negotiation without a
camera. Second, the byte-level decoding (launch-status byte, failure-reason byte, ASCII-from-byte-3)
was factored into a pure `WifiHandoverParsing` object specifically so it has unit tests — it is
otherwise the only part of this whole plan that had any test coverage at all, everything else
being GATT/Android-framework code with no practical way to exercise it off a real camera.

---

### Step 5 — Use it in the Wi-Fi connection flow

Feed the result into what already exists. `WifiCredentials` has been an injected value since the
first commit precisely so this costs nothing downstream:

- `WifiCameraViewModel.connect()` prefers `WifiHandover` when `availability` says a camera is
  connected, falling back to stored credentials, then to the NFC prompt.
- Write the result through `CameraCredentialsStore` so later connections work without BLE.
- `ConnectionPanel` gains a third state: BLE camera present → "Turn on camera Wi-Fi" instead of
  "Touch your camera to the phone".

**Runtime coupling to state plainly:** `feature:wificamera` has no *compile* dependency on
`feature:ble`, but handover only works when something is driving the connection — which is
`feature:ble`'s job. That is acceptable (they ship together) and already degrades gracefully, but
it should be written down rather than discovered.

**`CameraCredentialsStore` should shrink, not grow, once this ships.** It exists today only because
NFC is a one-shot tap with nothing to re-ask later — the store is what lets a second connection
skip a second tap. BLE handover has no such gap: it can be asked for fresh credentials on every
connect, so a BLE-paired camera has no need to cache anything to disk. After this step,
`CameraCredentialsStore` becomes the NFC-only fallback path (a camera never BLE-paired, or BLE
briefly unavailable), not the primary source of truth it is today. Revisit whether it is still
worth keeping as a DataStore at that point, rather than an in-memory cache.

**Done when:** powering on a paired camera leads to a live Wi-Fi session with no user interaction
beyond opening the app.

**As built:** matches the sketch. One addition the sketch didn't need to mention because it
predates the Wi-Fi module's `BLUETOOTH_CONNECT` requirement existing at all: `activateWifi()` is
gated behind an explicit `PermissionChecker` check in `WifiCameraViewModel.connect()`, not just the
`@RequiresPermission` annotation on the call chain. A live BLE connection already implies the
permission was granted once, but a user can revoke it at any time — including between that
connection existing and the tap that calls `connect()` — so the check is load-bearing, not just
satisfying Lint. `feature:wificamera`'s manifest gained a `BLUETOOTH_CONNECT` declaration to match.

**Update:** `CameraCredentialsStore` has since been removed entirely, not just shrunk. `connect()`
now only knows about BLE (`WifiHandover`); a fresh NFC tap is a separate, tap-driven path
(`connectTo()`) that never touches a store. The real consequence, not just a cleanup: there is no
longer any way to reconnect without either an active BLE-paired connection or a physical NFC tap —
closing the app and reopening it with the camera out of BLE range now always needs a fresh tap,
where before a cached credential would offer a "Connect" button. `ConnectionPanel` lost its
"known camera" state and the "Forget this camera" affordance along with it (nothing left to
forget). `DefaultWifiCameraRepository.discover()` also lost the `deviceDescriptionUrl` SSDP-skip
hint the store used to supply — a minor speed optimization, not a capability; discovery still
runs, just always via full SSDP now.

---

### Step 6 — Extract `feature:ble` (optional, later)

Pure hygiene: no behaviour change, no new capability. Keep it off the critical path and out of any
release that also changes behaviour.

Moves from `app` into `feature:ble`:

- `camera/CameraAction.kt`, `CameraActionIcon.kt`, `CameraActionStep.kt`, `CameraState.kt`
- `camera/ble/RemoteControlService.kt`, `camera/ble/LocationService.kt`
- `service/` — all five files
- `ui/camera/` — all eight files
- `data/AppearanceSettings.kt`, `data/BehaviorSettings.kt`, `data/SettingsDataStore.kt`
- `CameraBroadcastReceiver.kt`
- The BLE-specific parts of `ui/settings/` (custom buttons, `CompanionDeviceHelper`,
  `CameraActionPicker`, `NotificationButtonRow`, `NotificationButtonSizeSettings`)
- Their `androidTest` counterparts — `data/`, `ui/camera/`, most of `ui/settings/`

Stays in `app`: `MainActivity`, `ui/Navigation.kt`, `ui/MainScreen.kt`, `ui/CommonEntries.kt`,
`ui/about/`, `ui/theme/`, `ui/components/`, `utils/`, and the `*Entries.kt` files that build nav
entries.

**Measured costs** (checked, not estimated):

- **100 distinct `R.string` references** to re-home. Mechanical; the compiler finds every one.
- **Only 2 locale dirs** — German plus the base. Translation splitting is far cheaper than it would
  be on a heavily localised project.
- **24 files reference `ui/theme`.** Cheapest fix: features consume `MaterialTheme.*` and `app`
  supplies the theme at the top, exactly as `dof` and `wificamera` already do. `Colors.kt`/
  `Dimens.kt` move to `feature:ble` or a small `core:ui`.
- Manifest components move with their `<service>`/`<receiver>` entries.

**Two contracts that must not change:**

- `CameraBroadcastReceiver`'s action `org.staacks.alpharemote.EXT_BUTTON` is public API for Tasker
  users — byte-identical, whichever module it lives in.
- The app's `applicationId` stays `org.staacks.alpharemote`. Module namespaces change; the package
  users have installed does not.

`ui/settings/SettingScreen.kt` is a mix of BLE-specific and app-level concerns and needs splitting
rather than moving wholesale — expect this to be the fiddliest file.

**As built:** several things in the sketch above turned out wrong once actually checked against
usage, each discovered the same way — grep every consumer of a thing before deciding where it goes,
not just the folder it happened to sit in:

- **`ui/components/` and `utils/PermissionUtils.kt` do *not* stay in `app`.** Both were checked
  against every call site first: `PermissionWarning`/`LabeledSwitchRow`/`SettingsSection` were used
  by nothing outside `ui/settings/`+`ui/camera/`, and `hasBluetoothPermission`/
  `hasLocationPermission`/`rememberBlePermissionState` likewise had no caller outside what was
  moving. Leaving them in `app` would have made `feature:ble` depend on `app` — backwards. Both
  moved into `feature:ble` in full; only `utils/IntentUtils.kt` (`Context.openUrl`) actually stays,
  since its only callers (`AboutEntries.kt`, `SettingsEntries.kt`) both stay too.
- **`ui/theme/` needed a real `core:ui` module, not just a note about one.** The sketch floated
  "`Colors.kt`/`Dimens.kt` move to `feature:ble` or a small `core:ui`" as an open choice. It isn't
  open once checked: `MainActivity` and `ui/about/` (both staying in `app`) use the theme directly,
  so parking it inside `feature:ble` would make `app` depend on a feature for its own root theme.
  `core:ui` (`Colors.kt`/`Dimens.kt`/`Theme.kt`/`Type.kt`, namespace `org.staacks.alpharemote.core.ui`)
  is the only placement both `app` and `feature:ble` can reach without a backwards edge.
- **The `*Entries.kt` split cuts through `camera/` and `ui/settings/` at the file level, not the
  folder level.** `CameraEntries.kt` and `SettingsEntries.kt` reference `AlphaRemoteNavKey` and
  `Navigator` (both staying in `app`, per this plan's own "stays in app" list) — so they cannot
  move into `feature:ble` themselves, even though nearly everything else in their folders does.
  They stay at their original paths, in the original (unmoved) `org.staacks.alpharemote.ui.camera`
  / `org.staacks.alpharemote.ui.settings` packages — matching the existing pattern
  `WifiCameraEntries.kt`/`DofEntries.kt` already set, living in `app/ui/wificamera/`,
  `app/ui/dof/` rather than inside their features — and pick up explicit imports for the types
  (`CameraViewModel`, `CameraScreen`, `SettingsViewModel`, `SettingScreen`, `CompanionDeviceHelper`,
  `CameraActionPickerContent`) that used to be same-package and now aren't.
- **A cross-feature dependency had appeared since this plan was written.** `AlphaRemoteService`
  (added after step 5 shipped) called `CameraBleConnection.register(WifiHandover.serviceManager())`
  — fine while it lived in `app`, but once it moved into `feature:ble` that becomes `feature:ble`
  importing `feature:wificamera`, which the plan's own rule forbids. Moved to a new
  `AlphaRemoteApplication : Application()` in `app`, registered via `android:name` on
  `<application>`, calling that one line in `onCreate()`. Has to be `Application.onCreate()` and
  not `MainActivity.onCreate()`: a paired camera can start `CompanionAlphaRemoteService` directly
  from Companion Device presence with no activity ever created first.
- **Two direct `MainActivity` references had to be replaced, not just re-pointed.** Three files
  used `MainActivity.TAG` purely as a shared logcat tag — given each a local `TAG` constant instead.
  `NotificationUI` builds a `PendingIntent` that opens `MainActivity` on tap — genuinely functional,
  not just logging, and `feature:ble` cannot import `app`'s `MainActivity` class. Replaced
  `Intent(context, MainActivity::class.java)` with
  `context.packageManager.getLaunchIntentForPackage(context.packageName)`, which resolves to the
  same launcher activity without a compile-time reference, then applies the same
  `FLAG_ACTIVITY_NEW_TASK`/`FLAG_ACTIVITY_SINGLE_TOP` flags as before.
- **`ui/settings/CameraActionPicker.kt` carried a dead `DialogFragment` class** (superseded by the
  Navigation3 dialog entry in `SettingsEntries.kt`, zero references anywhere else in the codebase).
  Dropped rather than migrated — moving it would have pulled an otherwise-unneeded
  `androidx.fragment` dependency into `feature:ble` for code nothing calls.
- **Resource ownership needed the same per-usage check as the Kotlin files.** 66 of the ~90 checked
  `R.string` entries turned out to be used only by what's moving and were re-homed into
  `feature:ble/src/main/res/values{,-de}/strings.xml`; `app_name` and `title_settings` are needed
  by both sides (manifest label / notification channel name vs. bottom-nav label) and are the only
  two duplicated rather than shared. `colors.xml`, `dimens.xml`, `permission.xml`, and both
  notification `layout/` files moved wholesale (nothing outside the moving code touched them).
  `ca_stop.xml` and the adaptive-launcher-icon drawables (needed by `NotificationUI`'s
  `setSmallIcon`, since it cannot reach `app`'s `R.mipmap`) are the two cases duplicated rather than
  moved, because `app`'s About screen and manifest still need their own copies respectively.
  `permission.xml` picked up a `values-de/` translation it didn't have before — a pre-existing gap,
  fixed in passing since splitting it into its own module surfaced it as that module's own
  first-class lint failure rather than one line lost in `app`'s pre-existing count.
- Manifest components moved as expected: `AlphaRemoteService`, `CompanionAlphaRemoteService`, and
  `CameraBroadcastReceiver` (plus the permissions only they need) are declared in
  `feature/ble/src/main/AndroidManifest.xml` now, merged into `app`'s manifest as normal.

## Risks

| Risk | Mitigation |
|---|---|
| Connection ownership move (step 3) breaks reconnect after the camera sleeps | Test out-of-range and return explicitly; it is the path least covered by tests |
| Camera cannot hold the BLE remote and an active Wi-Fi AP at once | Test early, in step 4. `CLAUDE.md` already records that some bodies (α6400) cannot run the BLE remote and the location link together. If it fails, the handover must drop BLE after reading credentials — which changes `WifiHandover`'s contract to "hand over and stand down" |
| A stale `CameraBleConnection` singleton outlives a dead GATT | Reset explicitly on disconnect; do not rely on garbage collection |
| Step 6 silently breaks a settings screen | Real instrumented tests already cover `data/` and `ui/settings/` — run `connectedAndroidTest` before and after |

## Verification

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
./gradlew test
./gradlew connectedAndroidTest    # needs a device; covers data/ and ui/settings/
```

Note `./gradlew build` already fails on pre-existing missing German translations in `:app:lintDebug`
— that is not caused by this work. After step 6, `:app:lintDebug` still fails, but on 3 strings
now (`settings_bluetooth_required_title`/`_message`, `settings_open_settings` — all used only by
`SettingsEntries.kt`, which stays in `app`); the other 4 pre-existing gaps were `permission.xml`'s
strings, which moved to `feature:ble` and got German translations added there as part of the move,
so they no longer count against anyone. `core:ble:lintDebug`, `core:ui:lintDebug`,
`feature:ble:lintDebug`, `feature:dof:lintDebug`, and `feature:wificamera:lintDebug` are all clean.

All six steps have run through `:app:assembleDebug`, `:app:assembleDebugAndroidTest` (compiles —
not run, no device here), unit tests across every module, and lint for every module except `app`
(the pre-existing failure above). None of that has been run against real hardware — nothing in this
plan can be, short of a camera. The one change worth specifically re-verifying on a device before
trusting it is the MTU bump to 158 (step 4's "As built" note); step 6 is a pure code-motion
refactor with no behavioural change to re-verify, beyond confirming the app still launches, pairs,
and controls a camera exactly as before.

On hardware, watch the handover with:

```bash
adb logcat -s WifiCameraEvent WifiCameraNfc AlphaRemote-BLE CameraBleConnection WifiHandoverService
```

## Reference

- PROTOCOL.md §6 — BLE handover: manufacturer ID 301 (0x012D), service and characteristic table,
  `Connect → DiscoverServices → ChangeMtu → Communication → Finished` phases.
- PROTOCOL.md §1.2 — why BLE is the only fully automatic path.
- `docs/wifi-camera-architecture.md` — the Wi-Fi module as it stands today.
