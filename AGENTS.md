# AGENTS.md

Guide for coding agents working on **SmartCraft for OsmAnd** (`com.diaznet.osmandsmartcraft`).

## What this project is

A small single-module Android app (Kotlin, ~1300 LOC) that:

1. Connects over **Bluetooth LE** to a **Mercury Marine SmartCraft gateway** (the "VesselView Mobile" / VVM module on a boat engine).
2. Decodes the engine telemetry (RPM, temperatures, voltage, fuel, pressures, gear, engine hours).
3. Derives **fuel efficiency** from engine fuel flow and the phone's **GPS speed** over ground.
4. Pushes each value as a **custom map widget into OsmAnd / OsmAnd+** through OsmAnd's public **AIDL API**.

The app has one screen (settings/status/debug) and one foreground service that does all the work. There is no backend, no network, no database. User-facing docs: `README.md`. BLE protocol reference: `PROTOCOL.md` — read it before touching anything BLE related.

```
SmartCraft gateway ──BLE notify──▶ SmartCraftService ──▶ SmartCraftParser ──▶ OsmAndBridge ──AIDL──▶ OsmAnd widgets
                                    (foreground svc)      (bytes → data)       (format + push)
Phone GPS ──LocationManager──▶ SpeedProvider ──▶ SmartCraftService efficiency loop (1 s) ──▶ OsmAndBridge.updateEfficiency
        MainActivity ◀── polls static state every 500 ms ── SmartCraftService.companion
```

## Repository map

| Path | Purpose |
|---|---|
| `app/src/main/java/com/diaznet/osmandsmartcraft/MainActivity.kt` | Only UI. Start/stop button, auto-start, simulator toggle, OsmAnd target spinner (Auto/OsmAnd/OsmAnd+), unit buttons, debug log panel with category filters, file-logging toggle, version + GitHub link. Handles runtime permissions. |
| `.../SmartCraftService.kt` | Foreground service (`connectedDevice|location` types). BLE scan → GATT connect → enable stream → subscribe notifications → parse → push to OsmAnd. Also: fuel-efficiency loop, simulator, staleness monitor, reconnect/backoff, wake lock, RSSI polling, notification, and the global debug logger. |
| `.../SmartCraftParser.kt` | `SmartCraftData` (immutable data class holding all metrics in SI-ish base units) and `SmartCraftParser` (UUID constants + stateful per-characteristic decode). Pure JVM — unit-testable. |
| `.../OsmAndBridge.kt` | Binds to OsmAnd's AIDL service, registers the 13 widgets, formats values with `UnitPrefs`, updates widgets, handles `DeadObjectException` by rebinding. Holds widget definitions (IDs, titles, icons). |
| `.../UnitPrefs.kt` | Thin wrapper over `SharedPreferences("smartcraft")` for units and OsmAnd target, plus `formatTemp/formatPressure/formatFlow/formatEfficiency` → `Pair<value, unitLabel>`. |
| `.../SpeedProvider.kt` | GPS speed over ground via `LocationManager.GPS_PROVIDER` (1 s updates, main looper, `LocationListenerCompat`). `currentSpeedKmh()` returns null if the last fix is older than 5 s. |
| `.../FuelEfficiency.kt` | Pure math: `kmPerLiter(flowLph, speedKmh)` (null below `MIN_SPEED_KMH` = 4 km/h or flow ≤ 0) and `convert(kmPerL, EfficiencyUnit)`. Unit-testable. |
| `.../RollingAverage.kt` | Time-windowed mean used to smooth fuel flow and speed. Not thread-safe (only touched from the efficiency coroutine). Unit-testable. |
| `app/src/main/aidl/net/osmand/aidl/**` | Minimal hand-written copy of OsmAnd's AIDL interface (only what we need). |
| `app/src/main/java/net/osmand/aidl/mapwidget/*.java` | Hand-written Parcelables matching OsmAnd's wire format (`AMapWidget`, `Add/Update/RemoveMapWidgetParams`). |
| `app/src/main/res/layout/activity_main.xml` | Single LinearLayout screen, dark theme colors hardcoded. |
| `app/src/test/.../SmartCraftParserTest.kt` | JUnit4 tests for parser decoding and state accumulation. |
| `app/src/test/.../UnitConversionTest.kt` | Checks conversion *formulas* only (duplicates math; does not call `UnitPrefs`). |
| `app/src/test/.../FuelEfficiencyTest.kt`, `RollingAverageTest.kt` | Efficiency calculation/conversions and averaging window. |
| `app/lint-baseline.xml` | Lint baseline; existing warnings are accepted. |
| `.github/workflows/ci.yml` | On push to `releasecandidate/**` and PRs to `main`: test → lint → assembleDebug → upload APK. |
| `.github/workflows/release.yml` | On tag `v*`: test → assembleRelease → sign (if secrets) → GitHub Release. |
| `assets/` | README media only (not Android assets). |
| `fastlane/metadata/android/en-US/` | F-Droid store listing: title, descriptions, icon, screenshots, `changelogs/<versionCode>.txt`. |
| `LICENSE`, `NOTICE` | Apache-2.0; NOTICE carries the required attribution, OsmAnd AIDL interop note and trademark disclaimer. |
| `graft/` (gitignored) | Local code-graph cache for the `graft` tool; regenerate with `graft build`. |
| `.kiro/`, `.claude/`, `.mcp.json`, `.ignore` (gitignored, local only) | Agent tooling config for graft — see "Agent tooling" below. |
| `icons_list.txt`, `local.properties` (gitignored) | Leftover investigation output / local SDK path. Not part of the build. |

## Build, test, run

- Toolchain: Gradle 8.11 wrapper, AGP 8.7.3, Kotlin 2.0.21, Java/Kotlin target 17 (CI runs JDK 21). `compileSdk`/`targetSdk` 34, `minSdk` 26.
- Commands (use `./gradlew` on bash, `.\gradlew.bat` on Windows PowerShell):
  - `./gradlew test` — unit tests (JVM only, no device needed).
  - `./gradlew lint` — non-fatal (`abortOnError = false`), but keep it clean vs. the baseline.
  - `./gradlew assembleDebug` — APK in `app/build/outputs/apk/debug/`.
  - `./gradlew assembleRelease` — minified (R8) unsigned APK; signing happens only in CI.
- No instrumented tests exist (`androidTest` deps are declared but there is no source set).
- To exercise the app without a boat, enable **Simulator mode** in the UI: it generates sine-wave data (including a simulated 10–40 km/h speed for the efficiency widget; GPS is not used) once per second and pushes it to OsmAnd (still needs OsmAnd installed; data is only pushed once OsmAnd is bound).
- Device testing: `./gradlew installDebug` installs on the connected device/emulator (Gradle finds `adb` via `local.properties`; `adb` is at `<sdk>/platform-tools/adb` and may not be on PATH). Launch with `adb shell am start -n com.diaznet.osmandsmartcraft/.MainActivity`. Then in the app: Simulator mode → Start; in OsmAnd: Menu → Configure Screen → enable the SmartCraft widgets.
  - `[AIDL] addMapWidget(...) = false` / `updateWidget(...)=false` means OsmAnd rejected the call — typically because OsmAnd isn't fully initialized (e.g. it is crash-looping). Check that OsmAnd runs on its own before debugging this app.
  - **OsmAnd is unreliable on emulators.** Observed on a Pixel Tablet API 35 AVD with `ro.hardware.egl=emulation`: OsmAnd 5.4.6 crashes with SIGSEGV in `OpenGLThread` (unbounded recursion in `GPUAPI_OpenGL::ProgramVariablesLookupContext::lookupLocation`), and OsmAnd+ with SIGILL at startup — both with this app stopped. Prefer a physical phone (also required for BLE), or change the AVD graphics mode / use a phone API 34 image. Crash logs: `adb logcat -b crash`.
  - An older build with a different app ID (`com.smartcraft.osmand`) may exist on test devices; it also pushes widgets to OsmAnd — uninstall it to avoid confusion. It shows the label "SmartCraft OsmAnd"; the current app is labelled "SmartCraft for OsmAnd" and shows `v<versionName>` at the bottom of its screen.
  - Real GPS without a boat: disable simulator; efficiency stays `--` without engine data (expected), but the log should show `GPS speed updates started`. Emulator location routes (Extended controls → Location) can feed speed.
- Diagnostics: the in-app debug panel shows the last 200 log lines; "Log to file" writes `smartcraft_log_<ts>.txt` into `getExternalFilesDir(null)` (`/sdcard/Android/data/com.diaznet.osmandsmartcraft/files/`). Logcat tag: `SmartCraftService`.

### Versioning (important)

The release version lives in **`gradle.properties`** (`appVersionName=X.Y.Z`, `appVersionCode` = X×10000 + Y×100 + Z). It must be a literal there because F-Droid's update checker reads it with a regex (`UpdateCheckData`), it can't run Gradle code.
- `versionCode` = `appVersionCode`, always.
- `versionName` = `appVersionName` on the exact tag `vX.Y.Z` or a detached checkout (F-Droid builds, CI tag builds); otherwise a suffix from the branch: `release*` (incl. `releasecandidate/`) → `-rc`, `hotfix*` → `-fix`, anything else → `-dev`.
- `release.yml` fails if the tag doesn't equal `v$appVersionName` or `appVersionCode` doesn't match the formula.
- When starting a release, bump both properties and add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. Don't compute versions from commit count again.

### Branching / release flow

- Work happens on `releasecandidate/vX.Y.Z` branches (CI runs on push), merged into `main` via PR.
- Release = bump `gradle.properties` + changelog, then push tag `vX.Y.Z` on `main`. Existing tags: v1.0.1–v1.0.3 (they used commit-count versionCodes ≤ 6; 1.0.4 = 10004).
- Distributed on F-Droid (built from source by F-Droid from the tag, metadata in `fdroiddata` at `metadata/com.diaznet.osmandsmartcraft.yml`, `UpdateCheckMode: Tags`). Keep the build free of proprietary dependencies and network access.
- Release signing uses secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD` and variable `KEY_ALIAS`; if absent, only the unsigned APK is released.

## How it works (runtime flow)

### Startup
1. `MainActivity.onCreate` loads prefs, wires UI; if *auto-start* is on and permissions are granted, starts the service. Permissions requested: `ACCESS_FINE_LOCATION`, plus `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT` (API 31+) and `POST_NOTIFICATIONS` (API 33+).
2. `SmartCraftService.onStartCommand` → `startForegroundWithTypes()` (`connectedDevice`, plus `location` only if `ACCESS_FINE_LOCATION` is granted — claiming it without permission throws on API 34) → `OsmAndBridge.connect()` → launches a 1 s loop mirroring `osmAndBridge.isOsmAndConnected()` into the static `osmAndConnected`. Returns `START_STICKY`.
3. Simulator mode → `startSimulator()`. Otherwise → wake lock, `speedProvider.start()`, `startBleScan()`, `startStalenessMonitor()`. In both modes → `startEfficiencyMonitor()`.

### BLE pipeline (`SmartCraftService`)
1. **Scan** unfiltered, `SCAN_MODE_LOW_LATENCY`. Match device whose name equals `"VVM_" + address without colons`. Stop scan, record RSSI, `connectGatt(autoConnect = true)`.
2. **Connected** → `discoverServices()`.
3. **Services discovered** → write `0x0D 0x01` to Module Service char `00000001-…-ec55f9f5b963` to enable the data stream.
4. **onCharacteristicWrite** (for that char) → start subscription chain.
5. **Subscription chain**: for each of the 12 data UUIDs in `dataCharUuids`, `setCharacteristicNotification` + write CCCD `0x2902`; the next one is triggered from `onDescriptorWrite` (Android allows only one outstanding GATT op). When done → status `Receiving`, start RSSI polling every 5 s.
6. **onCharacteristicChanged** → update `lastDataTimestamp`, log hex under `[DATA]`, `parser.parseCharacteristic(uuid, bytes)`, `osmAndBridge.updateData(parsed)` (every notification pushes all 12 engine widgets; the efficiency widget has its own 1 s loop).
7. **Staleness**: every 2 s, if no data for 10 s → `pushStale()` sets all widgets to `--`.
8. **Disconnect**: `scheduleReconnect` — for status `0` (success), `8` (timeout) or `19` (remote terminated) with a known device, rely on GATT `autoConnect` ("Waiting for gateway"). Otherwise close GATT and rescan with exponential backoff 3s → 6s → 12s → 24s (cap 30 s); `reconnectAttempt` resets on connect.

### Fuel efficiency (derived metric)
- `startEfficiencyMonitor()` runs every 1 s: if engine data is fresh (simulator, or last BLE data ≤ 10 s ago) it samples `latestData.fuelFlowLph` into a 10 s `RollingAverage`; it samples the GPS speed (or `simSpeedKmh` in simulator) into another. Then `FuelEfficiency.kmPerLiter(avgFlow, avgSpeed)` → `osmAndBridge.updateEfficiency()`. Logs a `[DATA] Efficiency: …` line every 10 s.
- Sampling on a timer (not per notification) keeps the average valid if the gateway only notifies on value change, and lets values age out naturally when BLE or GPS drops.
- Result is `null` → widget shows `--` when speed < 4 km/h (~2 kn), no GPS fix within 5 s, or flow ≤ 0.
- Base unit km/L; display unit from `UnitPrefs.efficiencyUnit` (default **km/L**; also L/100km, NM/L, L/NM, US mpg), cycled by the efficiency button in `MainActivity`.
- `latestData` is set from `parser.parseCharacteristic` in BLE mode and from the generated data in simulator mode. Efficiency is not part of `SmartCraftData` and is not pushed by `updateData()`.
- Accuracy depends on the fuel flow conversion (`raw / 100000 m³/h`), which has not been validated against a reference gauge.

### Protocol decoding (`SmartCraftParser`)
Every characteristic value is **UInt16 little-endian at byte offset 2**; payloads shorter than 4 bytes decode to 0. The parser keeps a running `SmartCraftData` and returns a `copy()` with one field updated. Unknown UUIDs leave state unchanged. UUID comparison is on the lowercased string.

| Field | UUID suffix | Conversion |
|---|---|---|
| `rpm` | `0102` | raw |
| `coolantTempC` | `0103` | raw °C |
| `voltageV` | `0104` | raw / 1000 |
| `fuelUsed` | `0105` | raw (unit unknown) |
| `runtimeMin` | `0106` | raw minutes |
| `fuelFlowLph` | `0107` | raw / 100000 m³/h × 1000 → L/h |
| `fuelLevelPct` | `0108` | raw / 100 |
| `gear` | `0109` | raw (encoding uncertain; 0=N,1=F,2=R assumed in UI) |
| `oilPressureKpa` | `010a` | raw / 100 |
| `blockPressureKpa` | `010b` | raw / 100 |
| `oilTempC` | `010c` | raw °C |
| `seawaterTempC` | `010d` | raw °C |

Full UUID pattern: `0000XXXX-0000-1000-8000-ec55f9f5b963`. Channels `010e`–`0110` are unknown and not subscribed. See `PROTOCOL.md` for services, RE notes, and open questions.

### OsmAnd integration (`OsmAndBridge`)
- Target package: `net.osmand` (free) or `net.osmand.plus`; **Auto** prefers OsmAnd+ if installed. Resolved at `connect()` time only — changing the spinner while running takes effect on the next service start. `MainActivity` duplicates the same resolution logic for the "Open OsmAnd" link and labels.
- Binds explicitly to component `<pkg>/net.osmand.aidl.OsmandAidlService`, falling back to implicit action `net.osmand.aidl.OsmandAidlService` + `setPackage`. The manifest `<queries>` block is required for package visibility on API 30+.
- On bind: for each widget, `removeMapWidget` then `addMapWidget` with text `--` and `order = index + 1`. Users must still enable widgets in OsmAnd (Configure Screen).
- `updateData` formats values via `UnitPrefs` (read live from prefs each call, so unit changes apply immediately) and calls `updateMapWidget` for the 12 engine widgets (the efficiency widget, order 13, is updated separately via `updateEfficiency`). The unit label is sent in the widget `description` field. Runtime is shown as integer hours (`runtimeMin / 60`).
- `DeadObjectException` on update → mark disconnected, unbind, `connect()` again.
- On service destroy → remove widgets and unbind.
- Icons are OsmAnd's built-in OBD drawables (`widget_obd_*_day/_night`), referenced by name — they must exist inside OsmAnd, not this app.
- `connectAndRun()` and `clearAllWidgets()` are currently unused.

Widget IDs (stable — OsmAnd persists user widget visibility by ID, so **do not rename**):
`smartcraft_rpm, smartcraft_temp, smartcraft_voltage, smartcraft_fuel_flow, smartcraft_fuel_level, smartcraft_oil_pressure, smartcraft_runtime, smartcraft_fuel_used, smartcraft_gear, smartcraft_block_pressure, smartcraft_oil_temp, smartcraft_seawater_temp, smartcraft_fuel_efficiency`.

### Shared state between Activity and Service
There is no binding/IPC between them. `SmartCraftService.companion` holds `@Volatile` static fields (`isRunning`, `simulatorMode`, `osmAndConnected`, `bleStatus`, `bleDeviceName`, `bleRssi`, `fileLogging`) and the `debugLog` list (guard with `synchronized(debugLog)`). `MainActivity` polls these every 500 ms while resumed. `bleStatus` strings (`Receiving`, `Connected`, `Scanning`, `Connecting`, …) are matched literally in `MainActivity.updateUi()` for coloring — keep them in sync.

Logging: always use `SmartCraftService.log(category, msg)` with one of the categories **`BLE`, `AIDL`, `SIM`, `DATA`** — the UI filters rely on the `[CATEGORY]` substring.

### Preferences (`SharedPreferences` file `"smartcraft"`)
| Key | Values | Owner |
|---|---|---|
| `auto_start` | bool | MainActivity |
| `simulator` | bool | MainActivity (mirrored into `SmartCraftService.simulatorMode`) |
| `temp_unit` | `"C"` / `"F"` | UnitPrefs |
| `pressure_unit` | `"kPa"` / `"bar"` / `"PSI"` | UnitPrefs |
| `flow_unit` | `"Lph"` / `"GPH"` | UnitPrefs |
| `osmand_target` | `"auto"` / `"osmand"` / `"osmand_plus"` | UnitPrefs |
| `efficiency_unit` | `"km/L"` (default) / `"L/100km"` / `"NM/L"` / `"L/NM"` / `"mpg"` (= `EfficiencyUnit.label`) | UnitPrefs |

Changing stored string values breaks existing installs — add migration if needed.

## Critical invariants / gotchas

- **AIDL method order is the wire protocol.** `IOsmAndAidlInterface.aidl` must declare methods in the same order as OsmAnd's real interface, because transaction codes are sequential. The first three (`addMapMarker`, `removeMapMarker`, `updateMapMarker`) are placeholders that reuse widget param types just to occupy codes 1–3 — **never call them**. To use another OsmAnd API method, you must add every preceding method from OsmAnd's `IOsmAndAidlInterface` in exact order.
- **Parcelable field order must match OsmAnd's** `AMapWidget` exactly (`id, menuIconName, menuTitle, lightIconName, darkIconName, text, description, order, intentOnClick`). Package names `net.osmand.aidl.*` must not change. ProGuard keeps `net.osmand.aidl.**` — keep that rule.
- **GATT operations must be serialized.** Don't issue a new write/read while another is pending; chain via callbacks as done now. Known weakness: if a data characteristic or its CCCD is missing, `subscribeNext` writes nothing, so no `onDescriptorWrite` arrives and the chain stalls silently.
- `startRssiPolling()` launches a new coroutine every time the subscription chain completes (i.e. on each reconnect) without cancelling the previous one.
- Deprecated BLE APIs are used (`characteristic.value =`, `writeCharacteristic(char)`, `writeDescriptor(desc)`, `onCharacteristicChanged(gatt, char)`), fine at targetSdk 34 but would need the API 33 overloads when modernized. The service is `@SuppressLint("MissingPermission")`; permissions are checked in the Activity.
- Staleness monitor, wake lock and GPS only run in BLE mode, not simulator mode. `pushStale()` also blanks the efficiency widget; the efficiency loop then keeps it `--` once the 10 s flow window empties.
- Manifest declares `FOREGROUND_SERVICE_LOCATION` and `android.hardware.location.gps` (`required=false`). No background-location permission is needed because the service is started from the foreground activity after `ACCESS_FINE_LOCATION` is granted.
- Renaming an `EfficiencyUnit.label` changes the stored pref value — treat labels as persisted data.
- Parser reads only 16 bits; `PROTOCOL.md` notes gear may be a larger/3-byte payload and fuel-used unit is unknown. Update `PROTOCOL.md` together with parser changes.
- UI strings are hardcoded in Kotlin/XML (no `strings.xml` localization, no ViewBinding, framework `Switch` widgets) — match this style unless doing a deliberate refactor.
- `README.md` widget table, `PROTOCOL.md` status column, `widgetDefs` in `OsmAndBridge`, `dataCharUuids` in the service, and the simulator's `SmartCraftData` must all be updated together when adding a metric.

## Adding a new metric (checklist)

For a BLE-sourced metric (for a derived one, follow the fuel-efficiency pattern instead: pure calc object + own update method in `OsmAndBridge` + loop in the service):

1. `SmartCraftParser`: add UUID constant, field on `SmartCraftData` (base unit), `when` branch with conversion.
2. `SmartCraftService.dataCharUuids`: add the UUID (subscription order).
3. `SmartCraftService.startSimulator`: generate a plausible value.
4. `OsmAndBridge.widgetDefs`: add `WidgetDef` (new stable ID, title, OsmAnd `widget_obd_*` icon names) and an `updateWidget(...)` line in `updateData`. The `order` passed there must equal the def's position in `widgetDefs` + 1 (that's what `registerWidgets`/`pushStale` use). Appending after `smartcraft_fuel_efficiency` gives 14; add unit formatting to `UnitPrefs` if applicable.
5. Tests in `SmartCraftParserTest`; update `README.md` and `PROTOCOL.md`.
6. `./gradlew test lint assembleDebug`.

## Agent tooling (graft)

The repo is indexed by [graft](https://www.npmjs.com/package/@nanonets/graft), a code-context graph stored in `graft/` (gitignored, rebuild with `graft build`; re-admitted to ripgrep search by `.ignore`). It's wired up for Kiro (`.kiro/settings/mcp.json`, steering rule `.kiro/steering/graft.md` with `inclusion: always`) and Claude Code (`.mcp.json`, `.claude/settings.json` hooks, `.claude/skills/graft`). All of them run the MCP server via `npx -y @nanonets/graft mcp`.

Useful commands:
- `graft map` gives an orientation overview.
- `graft ask "<question>" --source` returns ranked nodes with the code spans inline.
- `graft grep "<literal>"` finds every occurrence.
- `graft callers <symbol> [--direction out] [--depth N]` shows call edges.
- `graft skeleton <file>` lists signatures only.

The codebase is small enough (8 Kotlin files) that reading files directly is also fine. Run `graft build` after significant changes.

## Conventions

- Kotlin, 4-space indent, compact one-line functions are common; comments are sparse. Java only for the AIDL Parcelables.
- Commit messages: short imperative summaries (e.g. "Add OsmAnd+ support with auto-detect, version display, GitHub link").
- Keep changes dependency-light (currently only core-ktx, appcompat, coroutines).
