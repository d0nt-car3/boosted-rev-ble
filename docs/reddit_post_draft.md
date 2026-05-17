# Draft Reddit Post — r/boostedboards

**Suggested title:**
> I reverse engineered the Boosted Rev's Bluetooth protocol to comply with my state's new 20mph scooter law — full GATT map (23/36 chars validated) + speed limiter + live telemetry [first public documentation]

**Suggested cross-posts:** r/ReverseEngineering, r/ElectricScooters, r/DIYelectricscooter

---

## Post Body (Reddit Markdown)

---

**TL;DR:** Utah just passed a 20mph limit on pedestrian scooters. Boosted is defunct, no firmware update coming. I reverse engineered the Rev's BLE GATT protocol, found the ride mode control register, decoded all streaming telemetry (speed, odometer, battery, motor power, lights), and wrote a Python script to lock it to 18mph permanently. 23 of 36 characteristics validated with live data. Full GATT map below.

---

### Background

Utah HB 117 limits pedestrian scooters to 20mph effective 2026. My Boosted Rev tops out at 24mph in Mode 3. Boosted went out of business in 2020, so there's no OTA update coming.

The only path was to do it myself.

---

### Methodology

**Tools used:**
- nRF Connect for Mobile (Android) — initial GATT exploration
- jadx — decompiled `com.boostedboards.android` v1.4.5 (5,823 Java source files)
- Python + `bleak` 3.0.2 (Linux/BlueZ backend)
- Panasonic CF-32 mk1 — Ubuntu 24.04, Intel Bluetooth 4.2

**The key insight:** Boosted's mobile app communicates with the ESC over BLE GATT, not over the CAN bus (the CAN bus only handles ESC-to-battery communication). So ride mode configuration lives in BLE-accessible memory.

**Bonding:** The Rev doesn't expose its custom services until bonded. Press the handlebar button **4–5 times rapidly** — this puts it into BLE pairing mode. Bond via nRF Connect or `bluetoothctl`, then the full GATT table appears.

**APK Decompilation — the decoder ring:**

The raw GATT scan gives you UUIDs and hex blobs, but no names or decoders. The breakthrough was decompiling Boosted's Android app (`com.boostedboards.android` v1.4.5) using jadx. 5,823 Java source files.

Inside `com.boostedboards.android.ble.h0.g.java`, every characteristic has a human-readable name — `VEHICLE_SPEED`, `VEHICLE_ODOMETER`, `BATTERY_REMAINING`, etc. The decoders were right there:

- Speed: 2 bytes LE × `0.00223694` = mph (from `VehicleType.getSpeedToMphCoeff()`)
- Odometer: 4 bytes LE × `3.6128e-5` = miles (from `VehicleType.getOdoToMilesCoeff()`)
- Battery: 1 byte = direct percentage

The APK also revealed that the crypto challenge-response channel (`58856524`) forwards the challenge to Boosted's cloud servers for signing — **no local key exists in the APK at all**. No `Cipher.getInstance()`, no `SecretKeySpec`, no HMAC. The app was a thin client. Since the servers are dead, the serial command protocol is permanently locked without firmware RE.

We then validated every decoder against the scooter's physical display using live BLE captures.

---

### The GATT Map (first public documentation)

**Device info:**
- Firmware: v3.1.3
- Model ID: BoostedRev81268245
- MAC format: `E9:DE:B1:9D:B6:82` (yours will differ)

**Services:**

    Standard services: 0x1800, 0x1801, 0x180A (normal BLE stuff)

    7dc55a86-c61f-11e5-9912-ba0be0483c18  ← Rev primary service
    65a8eaa8-c61f-11e5-9912-ba0be0483c18  ← Rev secondary service  
    ea32b817-d410-42e2-848a-1218201468fc  ← CSR config/OTA service
    588560e2-0065-11e6-8d22-5e5517507c66  ← Cross-product Boosted service*

*The `588560e2` service shares the same UUID base (`11e6-8d22-5e5517507c66`) as the Boosted **skateboard's** BLE services. The skateboard used sub-group `0056`, the Rev uses `0065`. Same codebase, different product.

---

### The Money Characteristic

    Service:    7dc55a86-c61f-11e5-9912-ba0be0483c18
    Char UUID:  7dc55f22-c61f-11e5-9912-ba0be0483c18
    Properties: NOTIFY, READ, WRITE

**Mode encoding (zero-indexed):**

| Write | Mode | Speed |
|-------|------|-------|
| `0x00` | Mode 1 | 12 mph |
| `0x01` | Mode 2 | 18 mph |
| `0x02` | Mode 3 | 24 mph |

**Key properties confirmed:**
- Write **persists across power cycles** (ESC saves to flash)
- **Mode change is buffered** — ESC applies it only when the motor is fully stopped
- No challenge-response auth required — just bond and write

Other confirmed reads in the primary service:

    7dc55dec  READ        → 0x03  (number of ride modes = 3)
    7dc5bb39  READ,WRITE  → "BoostedRev81268245" (device identity)
    7dc5c19d  READ,WRITE  → 0x00=mph, 0x01=km/h (display units — switches live)
    7dc59643  READ        → 0x07  (vehicle model / feature flags)

The `588560e2` service (cross-product) exposes a rolling 16-byte nonce — it's a cryptographic challenge-response channel. See "What's Still Locked" below.

---

### The Problem: Physical Button Override

The BLE mode write persists across power cycles — the ESC saves it to flash. But there's a catch: **the physical button on the handlebar still cycles through modes.** A single button press takes you from Mode 2 (18 mph, legal) to Mode 3 (24 mph, illegal). The BLE write is a default, not a lock.

For one-time use this is fine — set it and forget it. But for actual legal compliance where you need to *guarantee* the scooter can't exceed 20 mph, you need something more.

### The Enforcer Script

The watchdog subscribes to NOTIFY on the mode characteristic. If the rider (or anyone) presses the physical button and changes the mode, the script detects it via the BLE notification and **reverts it back within ~100ms** — before the ESC even applies the new mode (since mode changes require the motor to be stopped).

Requires Python 3.8+ and `bleak`:

    pip install bleak
    export BOOSTED_MAC="XX:XX:XX:XX:XX:XX"

**One-shot mode set** (persistent, survives power cycle):

    python watchdog/enforce_mode.py --mode 2   # 18mph, Utah-legal

**Active watchdog** (reverts physical button presses automatically):

    python watchdog/enforce_mode.py            # connects, sets Mode 2, monitors NOTIFY
                                               # any button press is reverted in ~100ms

**Limitation:** The watchdog requires an active BLE connection from a nearby computer/phone. It's not a firmware-level lock. If the BLE device disconnects, the button works normally again.

Full code + all scripts: **[GitHub link — I will be posting a repository with the full Python codebase soon once it's cleaned up]**

---

### Validated Telemetry (all confirmed with live data on blocks)

**Speed** (`7dc56b34`) — 2 bytes LE × `0.00223694` = mph. Matches the display exactly.

**Odometer** (`7dc56594`) — 4 bytes LE × `3.6128e-5` = miles. Reading: 302.7 mi, monotonically increasing.

**Battery %** (`65a8eeae`) — 1 byte = direct percentage. 71% matched 4/5 display segments.

**Motor Power** (`7dc56bfc`) — 2 bytes LE uint16. Raw motor effort, scales with speed/load:

| Mode | Avg Speed | Peak Speed | Avg Power | Peak Power |
|------|-----------|------------|-----------|------------|
| Mode 1 (12 mph) | 13.8 mph | 14.2 mph | 37 | 55 |
| Mode 2 (18 mph) | 19.7 mph | 20.7 mph | 78 | 134 |
| Mode 3 (24 mph) | 26.2 mph | 27.9 mph | 92 | 168 |

**Lights** (`ea32dcac`) — 1 byte: `0x00`=off, `0x01`=on. NOTIFY fires on every button toggle. Brightness register (`ea326b96`) = `0xFF` (max). Brake light pattern (`ea324d8c`) is a config register, not live status.

**Battery service** (`65a8eaa8`) — Also decoded: battery FW (`v3.1.4`), model (`B3SR`), pack ID, charging state.

---

### What's Still Locked

**The serial command channel (`58856524`)** requires a challenge-response handshake:

1. ESC sends 16-byte random nonce (confirmed: rolls on every read)
2. App was supposed to POST this to Boosted's cloud API
3. Server returns signed response → written back to ESC
4. ESC verifies → grants access

**We tested this:** Writes of `RT` (telemetry request prefix from APK) are accepted but produce zero response. The APK's `Observable.just(true)` auth skip is app-side only — the ESC firmware still requires the handshake. Boosted's servers have been dead since 2020.

**No local key exists in the APK** — no `Cipher`, no `SecretKeySpec`, no HMAC. The app was a thin client.

Direct GATT access (mode, telemetry, lights) works fine without auth.

---

### Safety Notes

- **Do not blindly write to unknown characteristics.** We don't know what `65a8f835`, `7dc5c19d`, or the `ea32` service characteristics do when written to.
- Writing to `7dc55f22` with `0x00`/`0x01`/`0x02` is confirmed safe and reversible.
- If your scooter enters a weird state, power cycle it — the mode register persists but the BLE stack resets.

---

### Call for Contributions

If you have a Boosted Rev and want to help finish the remaining 13 characteristics:

1. **HCI snoop logs** — If you have an old device with the original Boosted app, a Bluetooth HCI log during connection would let us crack the serial command channel
2. **Different firmware versions** — We've only tested v3.1.3. Does yours differ?
3. **Battery capacity unit** — `65a8f3c2` reads `6,698,488` — what unit is this? (Rev battery is ~187 Wh)
4. **MITM captures** — Old mitmproxy/Charles captures of the Boosted app's HTTPS traffic

Comment or submit a PR.

---

*Firmware v3.1.3 | 23/36 characteristics validated | Tested: Boosted Rev (model 81268245) | Utah, May 2026*

---

## Notes

- The GATT map is 64% validated (23/36). The remaining characteristics are static serials/hashes, battery internals, and the locked crypto channel.
- All decoders (speed coefficient, odometer coefficient, battery format) were extracted from the decompiled APK and confirmed against the scooter's physical display.
