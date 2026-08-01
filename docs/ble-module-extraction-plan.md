# Plan: extract `core:ble` and `feature:ble`, and reach the camera's Wi-Fi over BLE

Written to be picked up cold, later. Nothing here has been implemented yet.

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
                    app  (composition root: MainActivity, Navigation, theme)
                  ╱   │   ╲
      feature:ble     │    feature:wificamera      feature:dof
             ╲        │       ╱
              ╲       │      ╱
               core:ble  (connection + command queue + BleServiceManager SPI)
```

No feature depends on another feature. Both BLE-touching features contribute a `BleServiceManager`
to one shared connection and never refer to each other.

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
— that is not caused by this work.

On hardware, watch the handover with:

```bash
adb logcat -s WifiCameraEvent WifiCameraNfc CameraBLE
```

## Reference

- PROTOCOL.md §6 — BLE handover: manufacturer ID 301 (0x012D), service and characteristic table,
  `Connect → DiscoverServices → ChangeMtu → Communication → Finished` phases.
- PROTOCOL.md §1.2 — why BLE is the only fully automatic path.
- `docs/wifi-camera-architecture.md` — the Wi-Fi module as it stands today.
