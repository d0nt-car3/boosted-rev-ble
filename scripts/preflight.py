"""
Boosted Rev BLE — Pre-flight Diagnostic
========================================
Validates:
  1. bleak is importable
  2. A BLE adapter is present and active
  3. A short scan can be performed
  4. The target MAC address is visible (if scooter is on)

Run before each session:
    python scripts/preflight.py
"""

import asyncio
import sys
from importlib.metadata import version as pkg_version
from bleak import BleakScanner
bleak_version = pkg_version("bleak")

# Force UTF-8 output on Windows (cp1252 can't render emoji)
sys.stdout.reconfigure(encoding="utf-8")

SCOOTER_ADDRESS = "E9:DE:B1:9D:B6:82"
SCOOTER_NAME_PREFIX = "BOOSTED"
SCAN_TIMEOUT = 5.0

async def main():
    print("=" * 55)
    print("  Boosted Rev BLE — Pre-flight Check")
    print("=" * 55)

    # 1. Library version
    print(f"\n[1] bleak version : {bleak_version}")
    print(f"    Python version : {sys.version.split()[0]}")

    # 2. Adapter + scan
    print(f"\n[2] Scanning for {SCAN_TIMEOUT}s...")
    try:
        device_map = await BleakScanner.discover(timeout=SCAN_TIMEOUT, return_adv=True)
    except Exception as e:
        err = str(e)
        print(f"    ERROR: BLE scan failed -- {err}")
        print()
        if "NO_BLE_CENTRAL_ROLE" in err or "central" in err.lower():
            print("    ADAPTER ISSUE: The active adapter does not support BLE Central role.")
            print("    Your USB adapter may not be selected as the active device.")
            print()
            print("    Fix: run the adapter swap script (as Administrator):")
            print("      powershell -ExecutionPolicy Bypass scripts\\adapter_swap.ps1 status")
            print("      powershell -ExecutionPolicy Bypass scripts\\adapter_swap.ps1 use-usb")
            print()
            print("    When done with BLE work, restore with:")
            print("      powershell -ExecutionPolicy Bypass scripts\\adapter_swap.ps1 restore")
        else:
            print("    Possible causes:")
            print("    - USB adapter not plugged in or not recognized")
            print("    - Bluetooth service not running (services.msc)")
            print("    - WinRT runtime error -- try unplugging and re-plugging adapter")
        sys.exit(1)

    print(f"    Adapter OK — {len(device_map)} device(s) found\n")

    # 3. List visible devices (bleak 3.x returns {address: (BLEDevice, AdvertisementData)})
    scooter_found = False
    sorted_devices = sorted(device_map.values(), key=lambda pair: pair[1].rssi or -999, reverse=True)
    for device, adv_data in sorted_devices:
        name = device.name or adv_data.local_name or "(unnamed)"
        rssi = adv_data.rssi if adv_data.rssi is not None else "?"
        marker = ""
        if device.address.upper() == SCOOTER_ADDRESS.upper():
            marker = "  ← ✅ SCOOTER FOUND"
            scooter_found = True
        elif SCOOTER_NAME_PREFIX in (name).upper():
            marker = "  ← ⚠️  Boosted device (different MAC?)"
        print(f"    {device.address}  {name:30s}  RSSI {rssi}{marker}")

    # 4. Summary
    print("\n" + "=" * 55)
    if scooter_found:
        print("  ✅  Adapter working  |  Scooter visible")
        print("  Ready to connect — you may turn on the scooter now")
        print("  (or it's already on and advertising)")
    else:
        print("  ✅  Adapter working  |  Scooter NOT YET visible")
        print("  → Turn on the scooter, then re-run this script")
        print("  → Or put it in pairing mode (4-5 button presses)")
        print(f"  → Waiting for MAC: {SCOOTER_ADDRESS}")
    print("=" * 55 + "\n")

if __name__ == "__main__":
    asyncio.run(main())
