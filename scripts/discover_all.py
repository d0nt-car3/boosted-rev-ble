"""
Boosted Rev — Full GATT Discovery Scanner
==========================================
Connects to the Boosted Rev, reads every readable characteristic,
subscribes to all NOTIFY characteristics, and logs streaming data.

Non-destructive: no writes performed.

Requirements:
    pip install -r requirements.txt
    Device must already be bonded to this machine.
    A BLE adapter supporting Central role is required.
    (Built-in laptop adapters often do NOT support this — use a USB BLE dongle.)

Usage:
    python scripts/discover_all.py
    python scripts/discover_all.py --duration 60   # ride log for 60 seconds

Set BOOSTED_MAC environment variable to your scooter's BLE address.
    export BOOSTED_MAC="XX:XX:XX:XX:XX:XX"
"""

import asyncio
import argparse
import os
from datetime import datetime
from bleak import BleakClient, BleakScanner

SCOOTER_ADDRESS = os.environ.get("BOOSTED_MAC", "YOUR_SCOOTER_MAC_HERE")

# Known characteristic names for annotated output
KNOWN = {
    "7dc55f22-c61f-11e5-9912-ba0be0483c18": "RIDE MODE REGISTER",
    "7dc55dec-c61f-11e5-9912-ba0be0483c18": "NUM MODES",
    "7dc59643-c61f-11e5-9912-ba0be0483c18": "FEATURE FLAGS",
    "7dc5bb39-c61f-11e5-9912-ba0be0483c18": "DEVICE IDENTITY",
    "7dc5c19d-c61f-11e5-9912-ba0be0483c18": "UNKNOWN FLAG",
    "58856524-0065-11e6-8d22-5e5517507c66": "CRYPTO CHALLENGE",
    "58856525-0065-11e6-8d22-5e5517507c66": "CRYPTO ACK",
}


def label(uuid: str) -> str:
    return KNOWN.get(str(uuid).lower(), "unknown")


async def discover(duration: int):
    print(f"Scanning for Boosted Rev ({SCOOTER_ADDRESS})...")
    device = await BleakScanner.find_device_by_address(SCOOTER_ADDRESS, timeout=15.0)
    if not device:
        print("ERROR: Device not found. Is it powered on and bonded?")
        return

    print(f"Found: {device.name} ({device.address})\n")

    async with BleakClient(device) as client:
        print("=" * 60)
        print("STATIC READS — All Readable Characteristics")
        print("=" * 60)

        notify_chars = []

        for service in client.services:
            print(f"\nService: {service.uuid}")
            for char in service.characteristics:
                props = ", ".join(char.properties)
                name = label(char.uuid)

                if "read" in char.properties:
                    try:
                        val = await client.read_gatt_char(char.uuid)
                        hex_val = val.hex(" ").upper()
                        try:
                            ascii_val = val.decode("utf-8")
                        except Exception:
                            ascii_val = ""
                        ascii_str = f'  → "{ascii_val}"' if ascii_val.isprintable() and ascii_val else ""
                        print(f"  [{name:25s}] {str(char.uuid)}  [{props}]")
                        print(f"    Value: {hex_val}{ascii_str}")
                    except Exception as e:
                        print(f"  [{name:25s}] {str(char.uuid)}  [{props}]")
                        print(f"    Read error: {e}")
                else:
                    print(f"  [{name:25s}] {str(char.uuid)}  [{props}]  (not readable)")

                if "notify" in char.properties or "indicate" in char.properties:
                    notify_chars.append(char)

        if notify_chars and duration > 0:
            print("\n" + "=" * 60)
            print(f"NOTIFY STREAM — Logging for {duration} seconds")
            print("=" * 60)

            log = []

            def make_handler(uuid, name):
                def handler(sender, data: bytearray):
                    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
                    hex_val = data.hex(" ").upper()
                    entry = f"[{ts}] {name:25s} {uuid}  {hex_val}"
                    print(entry)
                    log.append(entry)
                return handler

            for char in notify_chars:
                try:
                    await client.start_notify(
                        char.uuid,
                        make_handler(char.uuid, label(char.uuid))
                    )
                except Exception as e:
                    print(f"  Could not subscribe to {char.uuid}: {e}")

            print(f"Subscribed to {len(notify_chars)} NOTIFY characteristics.")
            print("Ride now — logging all notifications...\n")
            await asyncio.sleep(duration)

            for char in notify_chars:
                try:
                    await client.stop_notify(char.uuid)
                except Exception:
                    pass

            if log:
                logfile = f"notify_log_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"
                with open(logfile, "w") as f:
                    f.write("\n".join(log))
                print(f"\nLog saved to {logfile}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Non-destructive full GATT discovery for Boosted Rev."
    )
    parser.add_argument(
        "--duration", type=int, default=30,
        help="Seconds to log NOTIFY streams (default: 30). Set to 0 to skip."
    )
    args = parser.parse_args()
    asyncio.run(discover(args.duration))
