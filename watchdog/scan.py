"""
Boosted Rev: BLE Discovery Scanner
Non-destructive: reads only, no writes.
"""
import asyncio
import os
from bleak import BleakScanner

SCOOTER_MAC = os.environ.get("BOOSTED_MAC", "YOUR_SCOOTER_MAC_HERE")

async def scan():
    print("Scanning for BLE devices (10 seconds)...")
    devices = await BleakScanner.discover(timeout=10.0, return_adv=True)
    
    found = False
    for device, adv in devices.values():
        is_target = device.address.upper() == SCOOTER_MAC.upper()
        marker = " ◄◄◄ BOOSTED REV" if is_target else ""
        print(f"  {device.address}  RSSI:{adv.rssi:4d}  {device.name or '(unnamed)'}{marker}")
        if is_target:
            found = True
            print(f"\n  Service UUIDs advertised: {adv.service_uuids}")
            print(f"  Manufacturer data:        {adv.manufacturer_data}")

    if not found:
        print(f"\nScooter ({SCOOTER_MAC}) not found in scan.")
        print("Make sure the scooter is powered on and nearby.")
    else:
        print(f"\nScooter detected [OK]")

asyncio.run(scan())
