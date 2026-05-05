"""
Boosted Rev — Brake + Regen Capture (Round 2)
Shorter, focused test. Polls lights + power + speed.
"""
import asyncio
from datetime import datetime
from bleak import BleakClient, BleakScanner

SCOOTER_MAC = "E9:DE:B1:9D:B6:82"

CHARS = {
    "ea32b761-d410-42e2-848a-1218201468fc": "BRAKE_DEF",
    "ea324d8c-d410-42e2-848a-1218201468fc": "BRAKE_PAT",
    "ea32dcac-d410-42e2-848a-1218201468fc": "LIGHT_ST",
    "ea326b96-d410-42e2-848a-1218201468fc": "BRIGHT",
    "7dc56bfc-c61f-11e5-9912-ba0be0483c18": "POWER",
    "7dc56b34-c61f-11e5-9912-ba0be0483c18": "SPEED",
}

SPEED_COEFF = 0.00223694


async def capture():
    print(f"Scanning...")
    device = await BleakScanner.find_device_by_address(SCOOTER_MAC, timeout=10.0)
    if not device:
        print("Not found.")
        return

    async with BleakClient(device, timeout=30.0) as client:
        print(f"Connected!\n")
        print(">>> THROTTLE UP NOW — get to full speed <<<\n")
        print(f"{'Sec':>4s}  {'BRAKE_PAT':>9s}  {'LIGHT':>5s}  {'BRIGHT':>6s}  {'POWER':>6s}  {'MPH':>6s}  {'Phase'}")
        print(f"{'─'*4}  {'─'*9}  {'─'*5}  {'─'*6}  {'─'*6}  {'─'*6}  {'─'*25}")

        start = datetime.now()
        
        for i in range(60):  # 30 seconds at 2 Hz
            elapsed = (datetime.now() - start).total_seconds()
            
            vals = {}
            for uuid, name in CHARS.items():
                try:
                    vals[name] = await client.read_gatt_char(uuid)
                except:
                    vals[name] = None

            bp = vals["BRAKE_PAT"].hex().upper() if vals.get("BRAKE_PAT") else "?"
            ls = vals["LIGHT_ST"].hex().upper() if vals.get("LIGHT_ST") else "?"
            br = vals["BRIGHT"].hex().upper() if vals.get("BRIGHT") else "?"
            pw = int.from_bytes(vals["POWER"][:2], "little", signed=True) if vals.get("POWER") else 0
            sp = int.from_bytes(vals["SPEED"][:2], "little") * SPEED_COEFF if vals.get("SPEED") else 0

            # Phase prompts
            if i == 0:
                phase = "← THROTTLE UP"
            elif i == 16:
                phase = "← REVERSE THROTTLE NOW!"
            elif i == 17:
                phase = "← HOLD REVERSE..."
            elif i == 26:
                phase = "← RELEASE, IDLE"
            elif i == 36:
                phase = "← BRAKE LEVER NOW!"
            elif i == 37:
                phase = "← HOLD BRAKE..."
            elif i == 46:
                phase = "← RELEASE"
            else:
                phase = ""

            print(f"{elapsed:4.0f}s  {bp:>9s}  {ls:>5s}  {br:>6s}  {pw:>6d}  {sp:>6.1f}  {phase}")
            await asyncio.sleep(0.5)

        print("\nDone!")

asyncio.run(capture())
