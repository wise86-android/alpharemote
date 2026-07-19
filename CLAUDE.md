# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

α-Remote is a native Android app (Kotlin, Jetpack Compose) that acts as a Bluetooth Low Energy remote control for Sony Alpha cameras. It uses Android's Companion Device feature: the app does not scan for the camera itself — Android starts it when the paired camera appears and tears it down when the camera disappears. Controls live primarily in a notification (including lock screen), not just the in-app UI.

The BLE protocol is one-directional for control (send button/jog presses) with only minimal status feedback from the camera: focus state, shutter state, and recording state. There is no image transfer, live view, or ability to read/set camera settings.

## Build & Test

Single-module Gradle project (`app`) using Kotlin DSL and a version catalog (`gradle/libs.versions.toml`).

```bash
./gradlew assembleDebug              # Build debug APK
./gradlew build                      # Full build + lint + unit tests
./gradlew test                       # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest       # Instrumented tests (needs device/emulator; app/src/androidTest)
./gradlew lint                       # Android lint
./gradlew testDebugUnitTest --tests "org.staacks.alpharemote.ExampleUnitTest.someMethod"  # Single test
```

Note: `compileSdk`/`targetSdk = 37` and `minSdk = 36` are intentionally very recent — the app relies on new Companion Device APIs (`DevicePresenceEvent`, `CompanionDeviceManager.Callback`). Building/running requires an equally recent SDK and device.

## Architecture

The app is layered as **UI (Compose + ViewModels) → Repository → bound Service → CameraBLE → BLE service managers → GATT**. State flows upward via Kotlin `StateFlow`; commands flow downward via method calls and Android `Intent`s.

### Service & lifecycle (`service/`)
- `CompanionAlphaRemoteService` — a `CompanionDeviceService`. Android calls `onDevicePresenceEvent` when the associated camera (dis)appears; it resolves the BLE device and fires connect/disconnect intents at `AlphaRemoteService`. This is the entry point that makes the app "just appear."
- `AlphaRemoteService` — the long-lived foreground `Service` and the heart of the app. It owns the single `CameraBLE` instance, drives the **camera action step queue** (`pendingActionSteps`), and owns the notification (`NotificationUI`). It exposes `cameraState: StateFlow<CameraState>` and is also bindable via `LocalBinder`. Commands arrive both by binder call (`executeCameraAction`) and by `Intent` action (`BUTTON_INTENT_ACTION`, `ADVANCED_SEQUENCE_INTENT_ACTION`, connect/disconnect).
- `LocationSyncController` — pushes phone GPS to the camera for geotagging, **gated behind the `updateCameraLocation` setting**. The camera-side location-link enable sequence (`CameraBLE.enableLocationSync`) must only run when the user opted in: some cameras (e.g. α6400) cannot use the BLE remote and the location link at the same time. When the setting is off the controller is passive — it never writes to the camera's location characteristics.
- `AlphaRemoteRepository` — process-wide singleton (`getInstance`) that binds to `AlphaRemoteService` and is the single source of truth for ViewModels. Re-exposes camera state plus Bluetooth/location enabled flags and companion associations. UI never touches the service directly.
- `NotificationUI` — builds the notification with custom action buttons and the countdown display.

### Camera action model (`camera/`)
This is the core domain abstraction — understand it before touching button behavior:
- `CameraAction` (`@Serializable`, also `java.io.Serializable` so it can ride in Intents and be persisted in settings) = a user-configurable button: a `CameraActionPreset` plus options (toggle, self-timer, hold duration, jog step/speed).
- `CameraActionPreset` — enum of built-in templates (SHUTTER, TRIGGER_ONCE, TRIGGER_ON_FOCUS, RECORD, AF_ON, C1, ZOOM_*, FOCUS_*, …). Each carries a `CameraActionTemplate` describing the press/release step lists and which user options apply.
- `CameraActionStep` (sealed) — the low-level executable units the service runs sequentially:
  - `CAButton` / `CAJog` — sent immediately to BLE.
  - `CACountdown` — a timed wait (self-timer, bulb, interval) with a label shown in the notification.
  - `CAWaitFor` — blocks the queue until the camera reports a `WaitTarget` (FOCUS acquired / SHUTTER pressed) — this is how "Trigger once" and "Trigger on focus" work.
- The service's queue loop (`startCameraAction` → `executeNextCameraActionStep` → `checkWaitAction`/`countdownActionComplete`) drains `CAButton`/`CAJog` steps synchronously, then parks on the next `CACountdown` (timer) or `CAWaitFor` (camera status), resuming when the condition is met. Pressing another button cancels a pending long-running sequence rather than queuing.

### BLE layer (`camera/` + `camera/ble/`)
- `CameraBLE` — wraps `BluetoothGatt`, handles connect/bond/MTU/service-discovery lifecycle, and fans GATT callbacks out to the `BleServiceManager`s. Bonding matters: an unbonded device triggers `createBond()`; losing bond mid-use moves to `BleConnectionState.BoundLost`.
- `BleCommandQueue` — serializes all GATT operations (`BLEOperation`: `Read`/`Write`/`SubscribeForUpdate`/`ChangeMtu`), because Android's GATT stack only allows one outstanding operation at a time. Everything BLE goes through here. It also offers `suspend` wrappers (`write`/`read`/`subscribe`) so service managers can be written as linear coroutines running in the per-connection scope that `CameraBLE` hands to `BleServiceManager.onConnect` (cancelled on disconnect).
- `BleServiceManager` implementations, one per GATT service:
  - `RemoteControlService` — the actual Sony protocol. Encodes `CameraActionStep`s into command bytes (see the byte constants in its companion object) written to the command characteristic, and decodes status notifications into `CameraStatus` (focus/shutter/recording). Sony service/characteristic UUIDs live here.
  - `GenericAccessService` — reads the device name.
  - `LocationService` — the camera's location-link GATT service. Reports readiness via its `status` flow but performs the enable sequence (`enableSync()`) only on explicit request — see `LocationSyncController` above.

### State model
`CameraState` (sealed) is the app-wide state: `Disconnected`, `NotBonded`, `Connected.RemoteDisabled` (bonded but the camera's BLE-remote setting is off — detected when commands fail), `Connected.Ready` (holds live focus/shutter/recording, pressed buttons/jogs, countdown, pending trigger count), and `Error`. `FocusState`/`ShutterState` are exported as enums (not booleans).

### UI (`ui/`)
Jetpack Compose with `AndroidViewModel`s that read from `AlphaRemoteRepository`. Screens: `camera/` (remote + advanced intervalometer/bulb sequences), `settings/` (custom notification buttons, companion pairing via `CompanionDeviceHelper`, permissions, location, external-broadcast toggle), `about/`. Navigation in `ui/Navigation.kt`. Persistence via DataStore Preferences, split by domain over one shared store (`data/SettingsDataStore.kt`): `data/AppearanceSettings.kt` (notification custom buttons + size, stored as serialized `CameraAction`s) and `data/BehaviorSettings.kt` (location sync, broadcast control, camera identity). ViewModels derive settings state from the store flows (`stateIn`) — the store is the single source of truth; don't cache settings in ViewModel fields.

### External automation
`CameraBroadcastReceiver` accepts `org.staacks.alpharemote.EXT_BUTTON` broadcasts (preset/toggle/selftimer/duration/step/down/up extras) so other apps (e.g. Tasker) can trigger actions — gated behind the user's `handleExternalBroadcastMessage` setting.

## Conventions

- All BLE-touching methods require `BLUETOOTH_CONNECT` and are guarded with `hasBluetoothPermission(...)` at call sites (see `utils/PermissionUtils.kt`); `@SuppressLint("MissingPermission")` is applied only after that runtime check.
- New camera capabilities are added by extending `CameraActionPreset`/`CameraActionTemplate` and, if a new protocol command is needed, the byte encoding in `RemoteControlService`. Per the README, only things achievable via blind button presses are feasible (e.g. intervalometer, bulb timer) — anything needing absolute settings or values is not.
- GPLv3 licensed; also distributed on F-Droid with reproducible builds, so avoid non-free dependencies or anything that would break reproducibility.
