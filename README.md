# Boosted Rev BLE Protocol — Reverse Engineering Project

> **First public documentation of the Boosted Rev's Bluetooth Low Energy GATT interface.**  
> 23 of 36 characteristics validated with live telemetry data.

---

## Background

Utah HB 117 (effective 2026) limits pedestrian scooters to **20 mph**. The Boosted Rev tops out at **24 mph** in Mode 3. With Boosted defunct since 2020, no OTA update is coming.

This project reverse engineered the Rev's BLE GATT protocol to:
1. Identify the ride mode control register and lock to a legal speed
2. Decode all streaming telemetry (speed, odometer, battery, motor power)
3. Document the full GATT map for community use
4. Characterize the locked crypto channel for future work

**Result:** Mode 2 (18 mph) can be set via a single BLE write. The write persists across power cycles — the ESC saves it to flash.

---

## Quick Start

### Requirements
- Python 3.8+
- A BLE adapter supporting Central role (built-in Intel BT 4.2 confirmed working)
- Boosted Rev bonded to your device (see [docs/bonding.md](docs/bonding.md))

```bash
pip install bleak
export BOOSTED_MAC="XX:XX:XX:XX:XX:XX"  # your scooter's BLE address
```

### Set Mode 2 (18 mph) — Utah Legal
```bash
python watchdog/enforce_mode.py --mode 2
```

### Lock Mode + Watchdog (reverts physical button presses)
```bash
python watchdog/enforce_mode.py
```

### Read Current Mode
```bash
python watchdog/enforce_mode.py --read
```

### Full GATT Discovery + Telemetry Logging
```bash
python scripts/discover_all.py --duration 60
```

### Analyze Captured Telemetry
```bash
python scripts/analyze_log.py
```

---

## Key Findings

### Ride Mode Control

**Characteristic:** `7dc55f22-c61f-11e5-9912-ba0be0483c18`

| Value | Mode | Speed | Utah Legal (≤20 mph) |
|-------|------|-------|----------------------|
| `0x00` | Mode 1 | 12 mph | ✅ |
| `0x01` | Mode 2 | 18 mph | ✅ **Recommended** |
| `0x02` | Mode 3 | 24 mph | ❌ |

- Write **persists across power cycles** — ESC saves to flash
- **Mode change is buffered** — ESC applies it only when the motor is fully stopped
- No challenge-response required — just bond and write

### Validated Telemetry

| Characteristic | UUID | Format | Validated |
|---|---|---|---|
| **Speed** | `7dc56b34` | 2 bytes LE × `0.00223694` = mph | ✅ Matches display |
| **Odometer** | `7dc56594` | 4 bytes LE × `3.6128e-5` = miles | ✅ Monotonically increasing |
| **Battery %** | `65a8eeae` | 1 byte = direct percentage | ✅ 71% = 4/5 segments |
| **Motor Power** | `7dc56bfc` | 2 bytes LE uint16, raw motor effort | ✅ All 3 modes characterized |
| **Lights Status** | `ea32dcac` | 1 byte: `0x00`=off, `0x01`=on | ✅ NOTIFY on toggle |
| **Units** | `7dc5c19d` | `0x00`=mph, `0x01`=km/h | ✅ Display switches live |

### Motor Power Across All Modes (no-load, on blocks)

| Mode | Avg Speed | Peak Speed | Avg Power | Peak Power |
|------|-----------|------------|-----------|------------|
| Mode 1 (12 mph) | 13.8 mph | 14.2 mph | 37 | 55 |
| Mode 2 (18 mph) | 19.7 mph | 20.7 mph | 78 | 134 |
| Mode 3 (24 mph) | 26.2 mph | 27.9 mph | 92 | 168 |

---

## Hardware Notes

Confirmed working:
- **Linux** — BlueZ + any BLE 4.0+ adapter. Intel BT 4.2 tested.
- **Windows** — bleak's WinRT backend works. Use a USB BLE 4.0+ dongle if your built-in adapter doesn't support Central role.
- **Mac** — bleak supports CoreBluetooth; untested on Rev but should work.

**Finding your scooter's MAC address:**
```bash
python watchdog/scan.py
# Look for "BoostedRev" in the scan results
```

---

## Crypto Channel (Locked)

The serial command channel (`58856524`) uses a challenge-response handshake:

1. ESC sends 16-byte random nonce (rolls on every read)
2. App was supposed to POST this to Boosted's cloud API
3. Server returns signed response → written back to ESC
4. ESC verifies and grants access

**Status:** Boosted's servers are dead since 2020. No local signing key exists in the APK. We tested writing `RT` commands — writes are accepted but produce no response. The ESC requires authentication before processing serial commands.

Direct GATT characteristic access (mode, telemetry, lights) works fine without auth.

**Community help wanted:** If you have old HCI snoop logs or MITM captures from when the Boosted app still worked, that could crack this open. See [GATT_MAP.md](GATT_MAP.md#service-4--cross-product-boosted-serialcrypto) for details.

---

## Safety Warning

- **Do not write to unknown characteristics.** Only `7dc55f22` (ride mode) and `7dc5c19d` (units) have been confirmed safe.
- **Mode changes take effect when the motor stops.** The scooter will continue at the old speed until you release the throttle.
- If your scooter enters a bad state, power cycle it.
- This project is for personal legal compliance, not performance modification.

---

## Contributing

The GATT map is 64% validated (23/36). Help finish it:

1. Bond your Rev via nRF Connect (Android/iOS) or your OS Bluetooth settings
2. Run `scripts/discover_all.py --duration 60` while riding
3. Open a PR or Issue with your telemetry log

**High-value contributions:**
- HCI snoop log captured while the original Boosted app was connected and riding — would reveal the `SERIAL_CMD` text protocol
- Testing on different firmware versions (only v3.1.3 confirmed so far)
- Battery capacity unit identification (`65a8f3c2` reads `6,698,488` — Wh? mAh?)

**Coming soon:** A browser-based PWA that runs directly in Chrome on Android — no Python, no laptop required.

---

## License

MIT

---

*Tested on Boosted Rev firmware v3.1.3 — Utah, May 2026*
