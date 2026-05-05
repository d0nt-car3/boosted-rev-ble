"""
Boosted Rev — Lights Service Monitor
Reads all ea32 characteristics, subscribes to NOTIFY, then watches
while you toggle the lights on/off via the scooter's button.

Non-destructive: no writes performed.
"""
import asyncio
from datetime import datetime
from bleak import BleakClient, BleakScanner

SCOOTER_MAC = "E9:DE:B1:9D:B6:82"

# Lights service (ea32) characteristics
LIGHTS = {
    "ea32b761-d410-42e2-848a-1218201468fc": "LIGHTS_DEFAULT_MODE",
    "ea324d8c-d410-42e2-848a-1218201468fc": "BRAKE_LIGHTS_PATTERN",
    "ea32dcac-d410-42e2-848a-1218201468fc": "LIGHTS_STATUS",
    "ea326b96-d410-42e2-848a-1218201468fc": "LIGHTS_BRIGHTNESS",
}

DURATION = 30  # seconds to monitor


async def monitor_lights():
    print(f"Scanning for scooter ({SCOOTER_MAC})...")
    device = await BleakScanner.find_device_by_address(SCOOTER_MAC, timeout=10.0)
    if not device:
        print("Scooter not found.")
        return

    print(f"Found: {device.name}\nConnecting...")

    async with BleakClient(device, timeout=30.0) as client:
        print(f"Connected: {client.is_connected}\n")

        # Initial read of all lights characteristics
        print("=" * 60)
        print("INITIAL STATE — Lights Service (ea32)")
        print("=" * 60)
        for uuid, name in LIGHTS.items():
            try:
                val = await client.read_gatt_char(uuid)
                print(f"  {name:25s}  {val.hex(' ').upper():>12s}  (int: {int.from_bytes(val, 'little')})")
            except Exception as e:
                print(f"  {name:25s}  ERROR: {e}")

        # Subscribe to NOTIFY on LIGHTS_STATUS
        print(f"\n{'=' * 60}")
        print(f"MONITORING — Toggle lights now! ({DURATION}s)")
        print(f"{'=' * 60}\n")

        log = []

        def on_notify(uuid_key, name):
            def handler(sender, data: bytearray):
                ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
                hex_val = data.hex(" ").upper()
                int_val = int.from_bytes(data, "little")
                entry = f"[{ts}] {name:25s}  {hex_val:>12s}  (int: {int_val})"
                print(entry)
                log.append(entry)
            return handler

        # Subscribe to ALL lights chars that support notify
        lights_status_uuid = "ea32dcac-d410-42e2-848a-1218201468fc"
        try:
            await client.start_notify(lights_status_uuid, on_notify(lights_status_uuid, "LIGHTS_STATUS"))
            print("Subscribed to LIGHTS_STATUS NOTIFY ✅")
        except Exception as e:
            print(f"Could not subscribe to LIGHTS_STATUS: {e}")

        print("Waiting for notifications... toggle lights on the scooter!\n")
        
        # Poll-read the other characteristics every 2 seconds to catch changes
        for i in range(DURATION // 2):
            await asyncio.sleep(2)
            # Read all 4 characteristics
            ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
            readings = []
            for uuid, name in LIGHTS.items():
                try:
                    val = await client.read_gatt_char(uuid)
                    readings.append(f"{name}={val.hex().upper()}")
                except:
                    pass
            print(f"  [{ts}] POLL: {' | '.join(readings)}")

        # Final read
        print(f"\n{'=' * 60}")
        print("FINAL STATE — Lights Service (ea32)")
        print("=" * 60)
        for uuid, name in LIGHTS.items():
            try:
                val = await client.read_gatt_char(uuid)
                print(f"  {name:25s}  {val.hex(' ').upper():>12s}  (int: {int.from_bytes(val, 'little')})")
            except Exception as e:
                print(f"  {name:25s}  ERROR: {e}")

        try:
            await client.stop_notify(lights_status_uuid)
        except:
            pass

        if log:
            print(f"\n{len(log)} NOTIFY events captured.")
        else:
            print("\nNo NOTIFY events received from LIGHTS_STATUS.")
            print("The lights may not trigger notifications, or the characteristic may need a write to activate.")

asyncio.run(monitor_lights())
