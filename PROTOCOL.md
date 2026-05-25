# SmartCraft BLE Protocol

Reverse-engineered from the [Signal K BT Sensors Plugin](https://github.com/naugehyde/bt-sensors-plugin-sk/blob/main/sensor_classes/MercurySmartcraft.js) and confirmed via VesselView Mobile APK decompilation.

## Device Identification

The gateway advertises with name `VVM_<BT address without colons>`.

## BLE Services

| Service UUID | Name | Purpose |
|---|---|---|
| `00000000-0000-1000-8000-ec55f9f5b963` | Module Service | Write `0x0D 0x01` to characteristic `00000001-...` to enable data stream |
| `00000100-0000-1000-8000-ec55f9f5b963` | Engine Data Service | Each metric on a separate characteristic (notify) |
| `00000200-0000-1000-8000-ec55f9f5b963` | Fault Service | Fault alerts |
| `00000300-0000-1000-8000-ec55f9f5b963` | Reflash Service | Firmware update |
| `00000400-0000-1000-8000-ec55f9f5b963` | Security Service | Security control |

## Data Characteristics (Engine Data Service `00000100-...`)

All values are UInt16LE at byte offset 2.

| UUID | Ch | VardecName | Conversion | Status |
|---|---|---|---|---|
| `00000101-...` | - | Protocol Version | — | — |
| `00000102-...` | 1 | RPM | raw = RPM | ✅ Implemented |
| `00000103-...` | 2 | Starboard Coolant Temp | raw = °C | ✅ Implemented |
| `00000104-...` | 3 | Voltage | raw / 1000 = Volts | ✅ Implemented |
| `00000105-...` | 4 | Fuel Used | raw = total fuel consumed (unit TBD) | ✅ Parsed |
| `00000106-...` | 5 | Engine Hours | raw = minutes | ✅ Implemented |
| `00000107-...` | 6 | Fuel Flow Average | raw / 100000 = m³/h | ✅ Implemented |
| `00000108-...` | 7 | Fuel Remaining | raw / 100 = % | ✅ Implemented |
| `00000109-...` | 8 | Actual Gear | raw = gear state (encoding TBD) | ✅ Parsed |
| `0000010a-...` | 9 | Oil Pressure | raw / 100 = kPa | ✅ Implemented |
| `0000010b-...` | 10 | Block Pressure | raw / 100 = kPa | ✅ Parsed |
| `0000010c-...` | 11 | Oil Temperature | raw = °C | ✅ Parsed |
| `0000010d-...` | 12 | Seawater Temperature | raw = °C | ✅ Parsed |
| `0000010e-...` | 13 | (Unknown) | — | Not yet seen |
| `0000010f-...` | 14 | (Unknown) | — | Not yet seen |
| `00000110-...` | 15 | (Unknown) | — | Not yet seen |
| `00000111-...` | - | UserVar Command | — | Control channel |

## Reverse Engineering Notes

- The protocol mapping was confirmed by decompiling the VesselView Mobile APK (Xamarin/.NET assemblies contain a JSON config with all channel definitions).
- The VesselView Mobile app is a Xamarin/.NET app with .NET DLLs gzip-compressed inside `libmonodroid_bundle_app.so`.
- The channel definitions were found as embedded JSON in `VesselViewMobile_Shared_dll`.
- Gear encoding is uncertain: raw values like 102701/102700 observed — may use a 3-byte payload rather than standard UInt16LE.
- Fuel Used unit is TBD (value ~17384 observed, could be mL, cL, or other).
