# Boosted Rev: GATT Characteristic Map

**Device:** Boosted Rev | **Firmware:** v3.1.3 | **Model:** BoostedRev81268245  
**MAC:** `E9:DE:B1:9D:B6:82` (random) | **Bond required:** Yes (4–5 button presses to enter pairing mode)  
**Initial capture:** 2026-05-02 | **Live telemetry validated:** 2026-05-03 (Linux/bleak 3.0.2/BlueZ)

Status key: CONFIRMED = live-validated | PARTIAL = value read, role unclear | UNKNOWN = no data

---

## Standard Services

### Generic Access: `0x1800`
| Characteristic | UUID | Props | Value |
|---|---|---|---|
| Device Name | `0x2A00` | READ, WRITE | `BOOSTED...8245C` |
| Appearance | `0x2A01` | READ | `0x1440` (5184) |
| Peripheral Preferred Connection Params | `0x2A04` | READ | — |
| Central Address Resolution | `0x2AA6` | READ | — |

### Generic Attribute: `0x1801`
| Characteristic | UUID | Props | Value |
|---|---|---|---|
| Service Changed | `0x2A05` | INDICATE | — |

### Device Information: `0x180A`
| Characteristic | UUID | Props | Value | Status |
|---|---|---|---|---|
| Manufacturer Name | `0x2A29` | READ | `"Boosted Inc."` | CONFIRMED |
| Model Number | `0x2A24` | READ | `"81268245"` | CONFIRMED |
| Serial Number | `0x2A25` | READ | `"serial"` | CONFIRMED |
| Firmware Revision | `0x2A26` | READ | `"v3.1.3"` | CONFIRMED |
| Hardware Revision | `0x2A27` | READ | `" 0 19428"` | CONFIRMED |
| Software Revision | `0x2A28` | READ | `"9.175"` | CONFIRMED |
| System ID | `0x2A23` | READ | `5544332211887766` | CONFIRMED |
| PnP ID | `0x2A50` | READ | `010a004c010001` | CONFIRMED |

---

## Custom Services

### Service 1: Rev Primary (ESC)
**Service UUID:** `7dc55a86-c61f-11e5-9912-ba0be0483c18`  
**Role:** Main ESC interface for mode control and telemetry

| Name | UUID | Props | Value / Decode | Status |
|---|---|---|---|---|
| `VEHICLE_MODES_COUNT` | `7dc55dec` | READ | `0x03` (3 ride modes) | CONFIRMED |
| `VEHICLE_MODE` | `7dc55f22` | NOTIFY, READ, **WRITE** | `0x00`=Mode 1 (12 mph), `0x01`=Mode 2 (18 mph), `0x02`=Mode 3 (24 mph). Persists across power cycles. **Write is buffered; ESC applies mode change only when motor is stopped.** | CONFIRMED |
| `VEHICLE_MODEL` | `7dc59643` | READ | `0x07` (feature flags / model) | CONFIRMED |
| `VEHICLE_ODOMETER` | `7dc56594` | NOTIFY, READ | 4 bytes LE × `3.6128e-5` = miles. Validated: **302.7 mi**. Monotonically increasing. ~1 Hz update rate. | CONFIRMED |
| `VEHICLE_SERIAL_1` | `7dc56666` | READ | `37199b53c0d5b630e2ac3dc2595cc18f004f205f` (20 bytes, static identifier/hash) | PARTIAL |
| `VEHICLE_SERIAL_2` | `7dc56986` | READ | `54cf3aa34bb1adb81608cd9d835d13a5771dda6c` (20 bytes, static identifier/hash) | PARTIAL |
| `VEHICLE_SPEED` | `7dc56b34` | NOTIFY, READ | 2 bytes LE × `0.00223694` = mph. **Validated: matches display (13–14 mph on blocks in Mode 1)**. ~1 Hz update rate. | CONFIRMED |
| `VEHICLE_POWER` | `7dc56bfc` | NOTIFY, READ | 2 bytes LE uint16. **Raw motor effort**, 0 at rest, scales with speed/load. Mode 1: avg 37, peak 55. Mode 2: avg 78, peak 134. Mode 3: avg 92, peak 168. Not a percentage. ~1 Hz. | CONFIRMED |
| `VEHICLE_UNKNOWN` | `7dc573ec` | (none) | No properties, placeholder characteristic | UNKNOWN |
| `VEHICLE_ID` | `7dc5bb39` | READ, WRITE | `"BoostedRev81268245"` (device identity string) | CONFIRMED |
| `VEHICLE_UNITS` | `7dc5c19d` | READ, WRITE | `0x00` = mph, `0x01` = km/h. **Validated: display switches live** (no motor stop needed). | CONFIRMED |
| `VEHICLE_MD_SERIAL` | `7dc5c201` | READ | `516ef06a` (motor driver serial) | PARTIAL |
| `VEHICLE_MD_FW_VERSION` | `7dc5c202` | READ | `03010300` (motor driver FW v3.1.3) | CONFIRMED |

---

### Service 2: Battery
**Service UUID:** `65a8eaa8-c61f-11e5-9912-ba0be0483c18`  
**Role:** Battery management (charge level, capacity, identity, firmware)

| Name | UUID | Props | Value / Decode | Status |
|---|---|---|---|---|
| `BATTERY_REMAINING` | `65a8eeae` | NOTIFY, READ | 1 byte = **direct percentage** (0–100). Validated: `0x47` = 71%, display shows 4/5 segments. | CONFIRMED |
| `BATTERY_FW` | `65a8f833` | READ | `03010400` (battery FW v3.1.4) | CONFIRMED |
| `BATTERY_MODEL` | `65a8f831` | READ | `"B3SR"` (battery model identifier) | CONFIRMED |
| `BATTERY_WRITABLE` | `65a8f835` | READ, WRITE | `28080e0401051a` (7 bytes, **unknown config, DO NOT WRITE**) | PARTIAL |
| `BATTERY_CELLS` | `65a8f832` | READ | `0x03` (possibly cell count or chemistry type) | PARTIAL |
| `BATTERY_ID` | `65a8f834` | READ | `841c8813` (battery pack serial/ID) | CONFIRMED |
| `BATTERY_CAPACITY` | `65a8f3c2` | READ | `f8376600` = 6,698,488 LE, raw capacity (units TBD, possibly mAh x scale) | PARTIAL |
| `BATTERY_CHARGING` | `65a8f5d4` | NOTIFY, READ | 1 byte. `0x00` = not charging. | CONFIRMED |

---

### Service 3: Lights (CSR/Config)
**Service UUID:** `ea32b817-d410-42e2-848a-1218201468fc`  
**Role:** Light configuration (default mode, status, brightness, brake pattern)  
**Note:** All values constant across ride modes. `LIGHTS_STATUS` confirmed to NOTIFY on toggle.

| Name | UUID | Props | Value / Decode | Status |
|---|---|---|---|---|
| `LIGHTS_DEFAULT_MODE` | `ea32b761` | READ, WRITE | `0x00`: power-on default light state (did not change during toggle test) | CONFIRMED |
| `BRAKE_LIGHTS_PATTERN` | `ea324d8c` | READ, WRITE | `0x00`: brake light pattern (needs brake squeeze to test) | PARTIAL |
| `LIGHTS_STATUS` | `ea32dcac` | NOTIFY, READ, WRITE | 1 byte: `0x00` = lights off, `0x01` = lights on. **NOTIFY fires on every toggle.** Validated 2026-05-03. | CONFIRMED |
| `LIGHTS_BRIGHTNESS` | `ea326b96` | READ, WRITE | `0xFF` (255), max brightness. Writable; could potentially dim lights. | CONFIRMED |

---

### Service 4: Cross-Product Boosted (Serial/Crypto)
**Service UUID:** `588560e2-0065-11e6-8d22-5e5517507c66`  
**Role:** Authenticated command channel shared across Boosted product line  
**Note:** Same UUID base (`11e6-8d22-5e5517507c66`) as Boosted skateboard services. Skateboard used sub-group `0056`; Rev uses `0065`.

| Name | UUID | Props | Value / Role | Status |
|---|---|---|---|---|
| `SERIAL_CMD` | `58856524` | NOTIFY, READ, WRITE, WRITE NO RESP | Rolling 16-byte nonce. Confirmed: new random value on every read (3 consecutive reads all different). Writes accepted without GATT error but no response without auth. | CONFIRMED |
| `SERIAL_AUTH` | `58856525` | NOTIFY, READ | `0x00` (not authenticated). No spontaneous NOTIFY events observed. | CONFIRMED |

**Crypto channel, fully probed 2026-05-03:**
- `58856524` returns a new random 16-byte value on every read (rolling nonce)
- The original Boosted app POSTs the challenge to Boosted cloud servers (**dead since 2020**)
- **No local key exists in the APK**: no `Cipher`, no `SecretKeySpec`, no HMAC
- APK `Observable.just(true)` auth skip is **app-side only**; the ESC firmware still requires the handshake
- Tested: writing `RT`, `RT\n`, `RT\r\n` to `SERIAL_CMD`. Writes accepted, **zero response** on either channel
- **The serial command protocol is locked** without server-side signing key or ESC firmware dump
- Direct GATT characteristic access (mode, speed, odometer, battery, lights) works without auth

**Potential paths to unlock (community contribution welcome):**
- Old HCI snoop logs from pre-2020 (challenge→response pairs)
- Old MITM proxy captures of Boosted app HTTPS traffic
- ESC firmware dump via JTAG/SWD (definitive, but requires hardware RE)
- `BoostedUnbrickFirmware` GitHub project (skateboard-focused, but same company)

---

## Summary Stats

| Category | Count |
|---|---|
| Total services (std + custom) | 7 |
| Total characteristics | 36 |
| Fully confirmed (value + role + live data) | 23 |
| Value read, role partially known | 4 |
| Unknown / placeholder | 1 |
| Streaming NOTIFY (validated) | 6 (speed, odometer, battery %, power, lights status, charging) |
| Streaming NOTIFY (unvalidated) | 1 (ride mode change) |

---

## Live Telemetry Byte Layouts (Validated 2026-05-03)

### VEHICLE_SPEED (`7dc56b34`)
```
Bytes: [0:2] little-endian uint16
Coefficient: × 0.00223694 = mph
Update rate: ~1 Hz
Example: 0x17CF = 6095 -> 6095 * 0.00223694 = 13.63 mph
```

### VEHICLE_ODOMETER (`7dc56594`)
```
Bytes: [0:4] little-endian uint32
Coefficient: × 3.6128e-5 = miles
Update rate: ~1 Hz
Example: 0x007FD2BD = 8377021 -> 8377021 * 3.6128e-5 = 302.6 miles
```

### BATTERY_REMAINING (`65a8eeae`)
```
Bytes: [0] uint8
Value: direct percentage (0–100)
Update rate: ~every 4 seconds
Example: 0x47 = 71% (display: 4/5 segments)
```

### VEHICLE_POWER (`7dc56bfc`)
```
Bytes: [0:2] little-endian uint16
Value: raw motor effort metric (not a percentage, exceeds 100)
Unit: proportional to motor current/load (ESC internal units)
Update rate: ~1 Hz

All-modes characterization (no-load, on blocks):
  Mode 1 (12 mph): avg 37, peak 55,  speed ~14 mph
  Mode 2 (18 mph): avg 78, peak 134, speed ~20 mph
  Mode 3 (24 mph): avg 92, peak 168, speed ~26 mph

Behavior: 0 at rest, scales with speed/load, spikes during acceleration.
Does NOT go negative during braking; reports motor drive only.
```

---

## Behavioral Notes

### Mode Switching
- BLE writes to `VEHICLE_MODE` succeed immediately (register reads back new value)
- **ESC applies the mode change only when the motor is fully stopped**
- If the wheel is spinning when mode is written, the change is buffered until next stop
- Mode persists across power cycles (stored to ESC flash)
