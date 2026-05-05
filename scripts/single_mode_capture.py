"""
Boosted Rev — Single Mode Capture
Sets the mode, waits for user to throttle, captures data.
"""
import asyncio
import sys
from datetime import datetime
from bleak import BleakClient, BleakScanner

SCOOTER_MAC = "E9:DE:B1:9D:B6:82"
VEHICLE_MODE_UUID = "7dc55f22-c61f-11e5-9912-ba0be0483c18"
VEHICLE_SPEED_UUID = "7dc56b34-c61f-11e5-9912-ba0be0483c18"
VEHICLE_POWER_UUID = "7dc56bfc-c61f-11e5-9912-ba0be0483c18"
SPEED_COEFF = 0.00223694
MODE_NAMES = {0: "Mode 1 (12 mph)", 1: "Mode 2 (18 mph)", 2: "Mode 3 (24 mph)"}


async def capture_mode(target_mode: int):
    print(f"Scanning...")
    device = await BleakScanner.find_device_by_address(SCOOTER_MAC, timeout=10.0)
    if not device:
        print("Not found.")
        return

    async with BleakClient(device, timeout=30.0) as client:
        print(f"Connected!\n")

        # Set mode while scooter is stopped
        await client.write_gatt_char(VEHICLE_MODE_UUID, bytes([target_mode]))
        verify = await client.read_gatt_char(VEHICLE_MODE_UUID)
        print(f"Mode set to {MODE_NAMES[target_mode]} (0x{verify.hex()}) ✅")
        print(f"\n>>> THROTTLE UP NOW — hold for 15 seconds <<<\n")

        print(f"{'Time':>12s}  {'POWER':>6s}  {'MPH':>8s}")
        print(f"{'─'*12}  {'─'*6}  {'─'*8}")

        speeds = []
        powers = []

        for i in range(30):  # 15 seconds at 2 Hz
            try:
                speed_raw = await client.read_gatt_char(VEHICLE_SPEED_UUID)
                power_raw = await client.read_gatt_char(VEHICLE_POWER_UUID)
                speed_val = int.from_bytes(speed_raw[:2], "little")
                power_val = int.from_bytes(power_raw[:2], "little", signed=True)
                mph = speed_val * SPEED_COEFF
                ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
                print(f"{ts:>12s}  {power_val:>6d}  {mph:>8.1f}")
                speeds.append(mph)
                powers.append(power_val)
            except Exception as e:
                print(f"  Error: {e}")
            await asyncio.sleep(0.5)

        # Return to Mode 1
        await client.write_gatt_char(VEHICLE_MODE_UUID, bytes([0]))
        print(f"\nReturned to Mode 1 ✅")

        # Summary
        if speeds:
            peak_speed = max(speeds)
            steady = [(s, p) for s, p in zip(speeds, powers) if s > peak_speed * 0.8 and s > 1.0]
            if steady:
                ss, sp = zip(*steady)
                print(f"\n{MODE_NAMES[target_mode]} steady-state:")
                print(f"  Avg speed:  {sum(ss)/len(ss):.1f} mph")
                print(f"  Peak speed: {max(ss):.1f} mph")
                print(f"  Avg power:  {sum(sp)/len(sp):.1f}")
                print(f"  Peak power: {max(sp)}")


mode = int(sys.argv[1]) if len(sys.argv) > 1 else 1
asyncio.run(capture_mode(mode))
