# Boosted Rev: Full BLE GATT Protocol Map
> Source: `com.boostedboards.android` v1.4.5, decompiled via jadx  
> APK SHA256: `2c0deabe7ce1a5ea66500112d63513a71e52d0b361619e9bc528cecb0daaefdd`  
> VirusTotal: **0 / 63** (clean)

---

## Primary BLE Service

| Field | Value |
|---|---|
| **Service UUID** | `7DC55A86-C61F-11E5-9912-BA0BE0483C18` |
| **BLE Library** | `com.polidea.rxandroidble2` (RxAndroidBle) |

---

## GATT Characteristics: Full Map

| # | Name | UUID | Notify | Notes |
|---|---|---|---|---|
| 0 | `VEHICLE_ID` | `7DC5BB39-C61F-11E5-9912-BA0BE0483C18` | No | Device identifier |
| 1 | `VEHICLE_MODEL` | `7DC59643-C61F-11E5-9912-BA0BE0483C18` | No | Model string |
| 2 | `MOTORDRIVER_FW_VERSION` | `00002a26-0000-1000-8000-00805f9b34fb` | No | Standard BLE FW Rev char |
| 3 | `SCOOTER_SERIAL` | `00002a25-0000-1000-8000-00805f9b34fb` | No | Standard BLE Serial Number char |
| 4 | `VEHICLE_MODES_COUNT` | `7DC55DEC-C61F-11E5-9912-BA0BE0483C18` | No | Number of available ride modes |
| 5 | `VEHICLE_MODE` ⭐ | `7DC55F22-C61F-11E5-9912-BA0BE0483C18` | **Yes** | `0x00`=Mode1(12mph), `0x01`=Mode2(18mph), `0x02`=Mode3(24mph). **Persistent write.** |
| 6 | `VEHICLE_ODOMETER` | `7DC56594-C61F-11E5-9912-BA0BE0483C18` | **Yes** | 20-byte offset; coeff = `VehicleType.getOdoToMilesCoeff()` (REV: `3.6128e-5`) |
| 7 | `VEHICLE_SPEED` | `7DC56B34-C61F-11E5-9912-BA0BE0483C18` | **Yes** | Byte offset 34, 2 bytes. Speed (mph) = raw × `0.00223694` (REV) or `0.00284091` (board) |
| 8 | `VEHICLE_POWER` | `7DC56BFC-C61F-11E5-9912-BA0BE0483C18` | No | No parser registered |
| 9 | `VEHICLE_NAME` | `7DC5BB39-C61F-11E5-9912-BA0BE0483C18` | No | Same UUID as VEHICLE_ID (aliased) |
| 10 | `VEHICLE_UNITS` | `7DC5C19D-C61F-11E5-9912-BA0BE0483C18` | No | mph vs km/h setting |
| 11 | `VEHICLE_MD_SERIAL` | `7DC5C201-C61F-11E5-9912-BA0BE0483C18` | No | Motor driver serial number |
| 12 | `VEHICLE_MD_FW_VERSION` | `7DC5C202-C61F-11E5-9912-BA0BE0483C18` | No | Motor driver firmware version |
| 13 | `BATTERY_ID` | `65A8F834-C61F-11E5-9912-BA0BE0483C18` | No | Battery pack identifier |
| 14 | `BATTERY_FW` | `65A8F833-C61F-11E5-9912-BA0BE0483C18` | No | Battery firmware version |
| 15 | `BATTERY_REMAINING` | `65A8EEAE-C61F-11E5-9912-BA0BE0483C18` | **Yes** | Battery % remaining |
| 16 | `BATTERY_CAPACITY` | `65A8F3C2-C61F-11E5-9912-BA0BE0483C18` | No | Total capacity |
| 17 | `BATTERY_CHARGING` | `65A8F5D4-C61F-11E5-9912-BA0BE0483C18` | **Yes** | Charging state flag |
| 18 | `LIGHTS_DEFAULT_MODE` | `EA32B761-D410-42E2-848A-1218201468FC` | No | Default light mode |
| 19 | `LIGHTS_STATUS` | `EA32DCAC-D410-42E2-848A-1218201468FC` | **Yes** | Current light state |
| 20 | `LIGHTS_BRIGHTNESS` | `EA326B96-D410-42E2-848A-1218201468FC` | No | Brightness level |
| 21 | `BRAKE_LIGHTS_PATTERN` | `EA324D8C-D410-42E2-848A-1218201468FC` | No | Brake light pattern |
| 22 | `SERIAL_CMD` 🔑 | `58856524-0065-11E6-8D22-5E5517507C66` | No | **Write** serial commands to ESC |
| 23 | `SERIAL_AUTH` 🔑 | `58856525-0065-11E6-8D22-5E5517507C66` | **Yes** | Auth challenge/response channel |

---

## SERIAL_AUTH Handshake: Architecture Analysis

> Source: `com/boostedboards/android/ble/h0/o.java`, lines 207–280

### Flow Diagram
```
Phone reads SERIAL_AUTH (UUID 58856525)
    → ESC sends 16-byte random challenge

Phone calls: repository.authorizeSerialCommunication(boardId, challengeHex)
    → HTTP POST to Boosted cloud servers  ← ⛔ SERVERS ARE OFFLINE

Servers return: signed response string
    → Phone writes response to SERIAL_CMD (UUID 58856524)

ESC reads SERIAL_AUTH notification
    → byte[0] != 0x00 → access granted
    → byte[0] == 0x00 → access denied
```

### Key Finding: No Local Crypto
The signing key is **NOT in the APK**. The app is a thin client that forwards the challenge to Boosted's backend. There is:
- ❌ No `Cipher.getInstance()` call in the Boosted package
- ❌ No `SecretKeySpec` or hardcoded key material  
- ❌ No local HMAC or AES implementation
- ✅ A `repository.authorizeSerialCommunication()` network call (dead since 2020)

### Implication for Our Use Case
**The handshake does not apply to us.** From `o.java` line 404–410:
```java
if (this.f1998j) {  // authorize == true
    just = h.b(g.H, connection).toObservable().flatMap(new g());  // runs auth flow
} else {
    just = Observable.just(true);  // skips auth entirely
}
```
The `authorize` boolean is passed in at connection time. Bonded/trusted connections skip the auth entirely. Our nRF Connect sessions write directly to `VEHICLE_MODE` (char 5) without triggering the handshake, consistent with our successful mode writes.

---

## Speed Decoding (VEHICLE_SPEED)

```
raw = bytes[34..35] as uint16 (little-endian)
speed_mph = raw × 0.00223694   (REV scooter)
speed_mph = raw × 0.00284091   (skateboard)
```

## Mode Encoding (VEHICLE_MODE)

| Byte | Mode | Speed Cap |
|---|---|---|
| `0x00` | Mode 1 | 12 mph |
| `0x01` | Mode 2 | **18 mph** ← Utah legal |
| `0x02` | Mode 3 | 24 mph |

Write is persistent across power cycles (stored to ESC flash).

---

## Services Not Yet Mapped
- `65A8xxxx` series (Battery): parsers present, byte offsets not fully decoded
- `EA32xxxx` series (Lights): parsers present, encoding TBD
- SERIAL_CMD protocol (text commands over BLE serial): `RT` prefix seen for telemetry request
