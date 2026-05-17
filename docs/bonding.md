# Bonding the Boosted Rev via BLE

The Rev requires bonding before it exposes its custom GATT services.
Without bonding, only the two standard BLE services (`0x1800`, `0x1801`) are visible.

---

## Triggering Pairing Mode

> **Press the handlebar button 5 times rapidly.**

The scooter enters a BLE pairing window of approximately 30 seconds.
You will see the BLE advertisement become discoverable for bonding in nRF Connect or Windows Bluetooth settings.

### Troubleshooting
- If you get **Error 133 (GATT ERROR)** — the scooter is not in pairing mode. Try again with 5 rapid presses.
- The pairing window closes after ~30 seconds. If you miss it, repeat the button sequence.
- You do not need to un-pair and re-pair each time — once bonded, the device connects normally on subsequent attempts.

---

## Bonding via nRF Connect (Android)

1. Open nRF Connect → **Scanner** tab
2. Find `BOOSTED...XXXXC` in the device list
3. Tap **Connect**
4. When prompted, tap **Bond** (or the bond icon in the top-right of the device view)
5. After bonding, tap **CLIENT** — you should now see all 4 custom services

---

## Bonding via Windows Bluetooth Settings

1. Press the handlebar button 5 times to enter pairing mode
2. Open **Windows Settings → Bluetooth & devices → Add device**
3. Select **Bluetooth**
4. Select `BOOSTED...XXXXC` from the list
5. Windows will pair and bond automatically
6. The device will now appear under "Other devices" or "Paired devices"

After pairing via Windows, `bleak` scripts can connect directly without re-pairing.

---

## Multiple Bond Slots

The Rev supports at least 2 simultaneous bond slots (confirmed: Android phone + Windows computer bonded concurrently). If you encounter issues bonding a second device, power cycle the scooter and try again.
