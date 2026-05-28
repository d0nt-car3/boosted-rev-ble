"""
Boosted Rev: BLE Connect + GATT Read Test (bleak 3.x, post-bonding)
Scooter must already be bonded via bluetoothctl.
"""
import asyncio
import os
from bleak import BleakClient, BleakScanner

SCOOTER_MAC = os.environ.get("BOOSTED_MAC", "YOUR_SCOOTER_MAC_HERE")

async def connect_and_read():
    print(f"Scanning for scooter ({SCOOTER_MAC})...")
    device = await BleakScanner.find_device_by_address(SCOOTER_MAC, timeout=10.0)

    if not device:
        print("Scooter not found in scan. Is it powered on?")
        return

    print(f"Found: {device.name}")
    print("Connecting (already bonded, should get full GATT)...")

    # address_type random is important for this device
    async with BleakClient(device, timeout=30.0) as client:
        print(f"Connected: {client.is_connected}")

        services = client.services
        print(f"\nDiscovered {len(services.services)} services, "
              f"{sum(len(s.characteristics) for s in services)} characteristics:\n")

        for service in services:
            print(f"  [{service.uuid}]")
            for char in service.characteristics:
                props = ", ".join(char.properties)
                value_str = ""
                if "read" in char.properties:
                    try:
                        val = await client.read_gatt_char(char.uuid)
                        # Try to display as string if printable, otherwise hex
                        try:
                            text = val.decode("utf-8")
                            if all(32 <= ord(c) < 127 for c in text):
                                value_str = f' = "{text}"'
                            else:
                                value_str = f" = {val.hex()}"
                        except:
                            value_str = f" = {val.hex()}"
                    except Exception as e:
                        value_str = f" = <error: {e}>"
                print(f"    {char.uuid}  [{props}]{value_str}")

        # Ride mode summary
        RIDE_MODE_UUID = "7dc55f22-c61f-11e5-9912-ba0be0483c18"
        try:
            mode_val = await client.read_gatt_char(RIDE_MODE_UUID)
            mode_names = {0: "Mode 1 (12 mph)", 1: "Mode 2 (18 mph)", 2: "Mode 3 (24 mph)"}
            mode = int.from_bytes(mode_val, "little")
            print(f"\n{'='*50}")
            print(f"  RIDE MODE: {mode_names.get(mode, f'Unknown ({mode})')}")
            print(f"{'='*50}")
        except Exception as e:
            print(f"\n[WARN] Could not read ride mode: {e}")

asyncio.run(connect_and_read())
