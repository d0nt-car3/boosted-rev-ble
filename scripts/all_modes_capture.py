"""
Boosted Rev — All Modes Power + Speed Capture
Cycles through Mode 1 → Mode 2 → Mode 3, capturing telemetry at each.
Returns to Mode 1 at the end (safe default).

IMPORTANT: Scooter must be on blocks! Mode 3 = 24 mph.
Hold throttle at full throughout the entire test.

Writes to VEHICLE_MODE (7dc55f22) only — confirmed safe.
"""
import asyncio
from datetime import datetime
from bleak import BleakClient, BleakScanner

SCOOTER_MAC = "E9:DE:B1:9D:B6:82"

VEHICLE_MODE_UUID = "7dc55f22-c61f-11e5-9912-ba0be0483c18"
VEHICLE_SPEED_UUID = "7dc56b34-c61f-11e5-9912-ba0be0483c18"
VEHICLE_POWER_UUID = "7dc56bfc-c61f-11e5-9912-ba0be0483c18"

SPEED_COEFF = 0.00223694
MODE_NAMES = {0: "Mode 1 (12 mph)", 1: "Mode 2 (18 mph)", 2: "Mode 3 (24 mph)"}
SETTLE_TIME = 5   # seconds to let speed stabilize after mode switch
CAPTURE_TIME = 10  # seconds to capture data per mode


async def capture_all_modes():
    print(f"Scanning for scooter ({SCOOTER_MAC})...")
    device = await BleakScanner.find_device_by_address(SCOOTER_MAC, timeout=10.0)
    if not device:
        print("Scooter not found.")
        return

    print(f"Found: {device.name}\nConnecting...")

    async with BleakClient(device, timeout=30.0) as client:
        print(f"Connected!\n")

        # Read current mode
        current = await client.read_gatt_char(VEHICLE_MODE_UUID)
        current_mode = int.from_bytes(current, "little")
        print(f"Current mode: {MODE_NAMES.get(current_mode, current_mode)}")

        print("\n" + "=" * 70)
        print("  >>> HOLD FULL THROTTLE NOW — DO NOT RELEASE UNTIL TEST ENDS <<<")
        print("  >>> SCOOTER MUST BE ON BLOCKS — Mode 3 will reach 24 mph    <<<")
        print("=" * 70)

        await asyncio.sleep(3)  # Give user time to start throttling

        all_data = {}

        for mode in [0, 1, 2]:
            print(f"\n{'─' * 70}")
            print(f"  SWITCHING TO {MODE_NAMES[mode]}")
            print(f"{'─' * 70}")

            # Write the mode
            await client.write_gatt_char(VEHICLE_MODE_UUID, bytes([mode]))
            verify = await client.read_gatt_char(VEHICLE_MODE_UUID)
            print(f"  Mode set: 0x{verify.hex()} ✅")

            # Settle
            print(f"  Settling ({SETTLE_TIME}s)...")
            await asyncio.sleep(SETTLE_TIME)

            # Capture
            print(f"  Capturing ({CAPTURE_TIME}s)...")
            print(f"  {'Time':>12s}  {'POWER':>6s}  {'MPH':>8s}")

            mode_speeds = []
            mode_powers = []

            for i in range(CAPTURE_TIME * 2):  # 2 Hz
                try:
                    speed_raw = await client.read_gatt_char(VEHICLE_SPEED_UUID)
                    power_raw = await client.read_gatt_char(VEHICLE_POWER_UUID)

                    speed_val = int.from_bytes(speed_raw[:2], "little")
                    power_val = int.from_bytes(power_raw[:2], "little", signed=True)
                    mph = speed_val * SPEED_COEFF

                    ts = datetime.now().strftime("%H:%M:%S.%f")[:-3]
                    print(f"  {ts:>12s}  {power_val:>6d}  {mph:>8.1f}")

                    mode_speeds.append(mph)
                    mode_powers.append(power_val)
                except Exception as e:
                    print(f"  Read error: {e}")

                await asyncio.sleep(0.5)

            all_data[mode] = {"speeds": mode_speeds, "powers": mode_powers}

        # Return to Mode 1 (safe default)
        print(f"\n{'─' * 70}")
        print(f"  RETURNING TO MODE 1 (safe default)")
        print(f"{'─' * 70}")
        await client.write_gatt_char(VEHICLE_MODE_UUID, bytes([0]))
        verify = await client.read_gatt_char(VEHICLE_MODE_UUID)
        print(f"  Mode set: 0x{verify.hex()} ✅")

        # Summary
        print(f"\n{'=' * 70}")
        print(f"  RESULTS SUMMARY")
        print(f"{'=' * 70}\n")
        print(f"  {'Mode':<22s}  {'Avg MPH':>8s}  {'Peak MPH':>9s}  {'Avg Power':>10s}  {'Peak Power':>11s}")
        print(f"  {'─' * 22}  {'─' * 8}  {'─' * 9}  {'─' * 10}  {'─' * 11}")

        for mode in [0, 1, 2]:
            d = all_data[mode]
            # Filter out ramp-up/down — only use samples above 50% of peak speed
            speeds = d["speeds"]
            powers = d["powers"]

            if speeds:
                peak_speed = max(speeds)
                # Use only steady-state samples (above 80% of peak)
                steady = [(s, p) for s, p in zip(speeds, powers) if s > peak_speed * 0.8]
                if steady:
                    ss, sp = zip(*steady)
                    avg_mph = sum(ss) / len(ss)
                    peak_mph = max(ss)
                    avg_pow = sum(sp) / len(sp)
                    peak_pow = max(sp)
                    print(f"  {MODE_NAMES[mode]:<22s}  {avg_mph:>8.1f}  {peak_mph:>9.1f}  {avg_pow:>10.1f}  {peak_pow:>11d}")
                else:
                    print(f"  {MODE_NAMES[mode]:<22s}  No steady-state data")
            else:
                print(f"  {MODE_NAMES[mode]:<22s}  No data")

        print(f"\n  Mode returned to Mode 1 ✅")
        print(f"  You can release the throttle now.\n")

asyncio.run(capture_all_modes())
