"""
Boosted Rev: Mode Enforcer + Watchdog
========================================
Connects to the Boosted Rev over BLE, sets Mode 2 (18 mph), and
actively monitors for any mode changes (e.g. physical button presses).
If the mode is changed away from the target, it is immediately reverted.

Requirements:
    pip install bleak

Usage:
    python enforce_mode.py              # enforce Mode 2, watchdog active
    python enforce_mode.py --mode 1     # enforce Mode 1 (12 mph)
    python enforce_mode.py --read       # read current mode only, no watchdog

Characteristic: 7dc55f22-c61f-11e5-9912-ba0be0483c18
  0x00 = Mode 1 (12 mph)
  0x01 = Mode 2 (18 mph)  <-- Utah-legal default
  0x02 = Mode 3 (24 mph)
"""

import asyncio
import argparse
import os
import sys
import signal
from bleak import BleakClient, BleakScanner

# ── Configuration ──────────────────────────────────────────────────────────────

SCOOTER_ADDRESS    = "XX:XX:XX:XX:XX:XX"  # Replace with your scooter's BLE MAC address (find via scan.py or nRF Connect)
MODE_CHARACTERISTIC = "7dc55f22-c61f-11e5-9912-ba0be0483c18"

MODE_MAP = {
    1: (0x00, "Mode 1, 12 mph"),
    2: (0x01, "Mode 2, 18 mph (Utah-legal)"),
    3: (0x02, "Mode 3, 24 mph"),
}

# ── Helpers ────────────────────────────────────────────────────────────────────

def byte_to_mode(b: int) -> int:
    """Convert raw BLE byte (0/1/2) to mode number (1/2/3)."""
    return b + 1


def mode_label(mode_num: int) -> str:
    return MODE_MAP.get(mode_num, (None, f"Unknown (mode {mode_num})"))[1]


async def find_scooter(timeout: float = 15.0):
    print(f"Scanning for Boosted Rev ({SCOOTER_ADDRESS})...")
    device = await BleakScanner.find_device_by_address(SCOOTER_ADDRESS, timeout=timeout)
    if device is None:
        print("ERROR: Scooter not found. Is it powered on and nearby?")
        sys.exit(1)
    print(f"Found: {device.name} ({device.address})")
    return device


# ── Read-only mode ─────────────────────────────────────────────────────────────

async def read_mode_only():
    device = await find_scooter()
    async with BleakClient(device) as client:
        raw = await client.read_gatt_char(MODE_CHARACTERISTIC)
        mode_num = byte_to_mode(raw[0])
        print(f"Current mode: {mode_label(mode_num)}")


# ── Watchdog mode ──────────────────────────────────────────────────────────────

async def run_watchdog(target_mode: int):
    target_byte, target_label = MODE_MAP[target_mode]

    device = await find_scooter()

    # Revert callback: called on every NOTIFY from the scooter
    revert_lock = asyncio.Lock()

    async def revert(client: BleakClient):
        async with revert_lock:
            await client.write_gatt_char(
                MODE_CHARACTERISTIC, bytes([target_byte]), response=False
            )

    async with BleakClient(device) as client:
        print(f"Connected. Setting {target_label}...")

        # Set initial mode
        await client.write_gatt_char(
            MODE_CHARACTERISTIC, bytes([target_byte])
        )
        raw = await client.read_gatt_char(MODE_CHARACTERISTIC)
        if raw[0] != target_byte:
            print(f"WARNING: Write may not have taken. Read back: 0x{raw[0]:02X}")
        else:
            print(f"[OK] Mode set: {target_label}")

        # Subscribe to NOTIFY
        def on_notify(sender, data: bytearray):
            current_byte = data[0]
            current_mode = byte_to_mode(current_byte)
            if current_byte != target_byte:
                print(
                    f"[WARN] Mode change detected: {mode_label(current_mode)} "
                    f"→ reverting to {target_label}..."
                )
                asyncio.ensure_future(revert(client))
            # else: mode is already correct, ignore

        await client.start_notify(MODE_CHARACTERISTIC, on_notify)

        print(
            f"\n[LOCKED] Watchdog active, mode locked to {target_label}\n"
            f"   Any physical button mode changes will be reverted automatically.\n"
            f"   Press Ctrl+C to stop.\n"
        )

        # Keep running until interrupted
        stop_event = asyncio.Event()

        def on_signal(*_):
            print("\nShutting down watchdog...")
            stop_event.set()

        loop = asyncio.get_running_loop()
        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, on_signal)
            except NotImplementedError:
                pass  # Windows doesn't support add_signal_handler for all signals

        try:
            await stop_event.wait()
        except KeyboardInterrupt:
            pass
        finally:
            await client.stop_notify(MODE_CHARACTERISTIC)
            print("Watchdog stopped.")


# ── Entry Point ────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Lock Boosted Rev to a specific ride mode via BLE watchdog."
    )
    parser.add_argument(
        "--mode", type=int, default=2, choices=[1, 2, 3],
        help="Mode to enforce: 1 (12 mph), 2 (18 mph, default), 3 (24 mph)"
    )
    parser.add_argument(
        "--read", action="store_true",
        help="Read current mode only, no watchdog"
    )
    args = parser.parse_args()

    if args.read:
        asyncio.run(read_mode_only())
    else:
        asyncio.run(run_watchdog(args.mode))
