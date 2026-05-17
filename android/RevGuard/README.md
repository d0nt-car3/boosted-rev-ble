# Rev Guard — BLE Speed Compliance App

> **Android app that locks your Boosted Rev to Mode 2 (18 mph) and auto-reverts any mode changes — even with the screen off.**

---

## What It Does

Rev Guard connects to your Boosted Rev scooter over Bluetooth Low Energy and:

1. **Sets Mode 2 (18 mph)** — Utah HB 117 compliant
2. **Monitors for mode changes** via real-time BLE NOTIFY (push, not polling)
3. **Auto-reverts** any mode change back to Mode 2 within ~50-200ms
4. **Logs all events** with timestamps — exportable as evidence
5. **Runs with the screen off** — foreground service keeps BLE alive in your pocket

## Requirements

- **Android 10+** (API 29) — any phone with BLE and sideloading enabled
- **Tested on:** Pixel 8 / GrapheneOS
- Boosted Rev scooter (firmware v3.1.3 confirmed)

## Install

### Build from source
```bash
# Requires JDK 17 + Android SDK (platforms;android-35, build-tools;35.0.0)
cd android/RevGuard
./gradlew assembleDebug

# Install via ADB
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Pre-built APK
Check [Releases](../../releases) for the latest APK.

## First Run

1. **Launch Rev Guard** — grant Bluetooth and notification permissions when prompted
2. **Bond your scooter** (one-time):
   - Turn on the Boosted Rev
   - Press the handlebar button **5 times** rapidly
   - Tap **"Scan for Scooter"** in the app
   - Tap your scooter when it appears to bond
3. **Tap Connect** — the app sets Mode 2 and starts the watchdog
4. **Lock your screen and pocket the phone** — the foreground service keeps everything running

## How It Works

- The app subscribes to BLE NOTIFY on the ride mode characteristic (`7dc55f22`)
- The Boosted Rev's ESC fires a notification the instant the mode register changes (e.g., physical button press)
- Rev Guard receives this event and immediately writes `0x01` (Mode 2) back
- Round-trip revert time: **~50-200ms** (as fast as the BLE stack allows)
- If BLE disconnects (e.g., out of range), the app auto-reconnects with exponential backoff

## UI

| Element | Meaning |
|---|---|
| 🔒 Mode 2 — 18 mph Locked | Watchdog active, mode is correct |
| ⚠️ Reverting… | Mode change detected, writing Mode 2 back |
| ❌ Disconnected | No BLE connection |
| 🔄 Reconnecting… | Lost connection, attempting to re-establish |
| Battery % | Current scooter battery level (updates every 30s) |
| Event Log | Timestamped log of all connects, disconnects, and reverts |

## Event Log

All events are logged with timestamps and persisted to a file. Tap **"Export Log"** to share via any Android share target (email, files, etc.).

Example log:
```
[2026-05-15 21:05:32] BLE: Connected to BOOSTED...8245C
[2026-05-15 21:05:32] MODE: Read: Mode 2 — 18 mph
[2026-05-15 21:05:32] MODE: Write confirmed: Mode 2 — 18 mph
[2026-05-15 21:12:47] MODE_REVERT: Mode 3 — 24 mph → Mode 2 — 18 mph (forced)
[2026-05-15 21:12:47] MODE: Write confirmed: Mode 2 — 18 mph
```

## Architecture

```
RevGuard/app/src/main/java/com/revguard/
├── MainActivity.kt    — UI, permissions, BLE scan + bond wizard
├── BleService.kt      — Foreground service, watchdog logic, battery polling
├── BleManager.kt      — BLE connection lifecycle, GATT operation queue, auto-reconnect
├── BondingHelper.kt   — BLE scanning, programmatic device bonding
├── EventLog.kt        — Thread-safe timestamped logger with file persistence
└── Constants.kt       — BLE UUIDs, mode bytes, timing constants
```

**No third-party BLE libraries** — uses raw `android.bluetooth` APIs only.

## License

MIT
