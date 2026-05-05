# Methodology — How the Boosted Rev BLE Protocol Was Reverse Engineered

## Tools Required

- **nRF Connect for Mobile** (Android) — free, Nordic Semiconductor
- **Python 3.8+** with `bleak` library
- A USB BLE adapter supporting Central role (built-in laptop adapters often don't support this)

---

## Step 1 — Initial Discovery

The Boosted Rev advertises a single primary service UUID:

```
7dc55a86-c61f-11e5-9912-ba0be0483c18
```

This UUID is visible in nRF Connect's Scanner tab without bonding. However, **connecting without bonding only exposes the two standard services** (`0x1800`, `0x1801`). The full GATT table is hidden behind bonding.

---

## Step 2 — Triggering Pairing Mode

The Rev does not automatically enter BLE pairing mode. It requires a specific physical gesture:

> **Press the handlebar button 4–5 times rapidly.**

The scooter enters a short pairing window (approximately 30 seconds). During this window:
- The BLE advertisement changes
- nRF Connect can initiate bonding successfully
- Without this step, bonding attempts return `Error 133 (GATT ERROR)`

---

## Step 3 — Full Service Discovery

After bonding, four custom services become visible. In nRF Connect, tap **CLIENT** to see the full GATT table including all services and characteristics.

Screenshot and document every characteristic UUID and its properties (READ/WRITE/NOTIFY). See [GATT_MAP.md](../GATT_MAP.md) for the complete table.

---

## Step 4 — Mode Correlation Test

To find which characteristic controls ride mode:

1. Note the current mode on the scooter display
2. Read every short READ characteristic in the primary `7dc5` service
3. Physically change the mode via the handlebar button
4. Re-read each characteristic
5. The characteristic whose value changes = the mode register

**Result:** `7dc55f22` changes value when mode changes. It is a zero-indexed single byte:
- `0x00` = Mode 1
- `0x01` = Mode 2
- `0x02` = Mode 3

---

## Step 5 — Write Confirmation

In nRF Connect, tap the ↑ (write) icon on `7dc55f22`, enter `01` (BYTE ARRAY / HEX), and tap WRITE.

The scooter display immediately updates to Mode 2. Re-reading the characteristic confirms `0x01`.

**Persistence test:** Power cycle the scooter. On reconnect, `7dc55f22` still reads `0x01`. The ESC saves the mode register to flash.

---

## Step 6 — Automated Exploration (USB BLE Adapter)

With a BLE Central-capable USB adapter and Python/bleak:

```bash
python scripts/discover_all.py
```

This script reads every characteristic and subscribes to all NOTIFY characteristics simultaneously, logging streamed data during riding. This is how the remaining unknown characteristics will be decoded.

---

## Key Architectural Observations

- **Speed caps are firmware-coded:** The per-mode speed limits (12/18/24 mph) are not stored in BLE-accessible memory. They are hard-coded in the ESC firmware binary. Changing them requires JTAG firmware extraction and binary patching — not worth pursuing for minor adjustments.
- **The `5885` service is cryptographically protected:** The cross-product Boosted service uses a rolling challenge-response mechanism. The original Boosted app holds the key. This channel remains uncracked.
- **The `ea32` service is global config:** Values in this service do not change across ride modes, confirming it is not per-mode speed configuration.
