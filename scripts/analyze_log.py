"""
Analyze the telemetry log from discover_all.py
Decode VEHICLE_SPEED, VEHICLE_ODOMETER, VEHICLE_POWER, BATTERY_REMAINING

Usage:
    python scripts/analyze_log.py <path_to_notify_log.txt>
"""
import re
import sys

if len(sys.argv) < 2:
    print("Usage: python analyze_log.py <notify_log_file>")
    sys.exit(1)
LOG_FILE = sys.argv[1]

# UUID mapping
UUIDS = {
    "7dc56594": "ODOMETER",
    "7dc56b34": "SPEED",
    "7dc56bfc": "POWER",
    "65a8eeae": "BATTERY_%",
    "65a8f5d4": "CHARGING",
    "58856524": "CRYPTO",
    "7dc55f22": "RIDE_MODE",
    "ea32dcac": "LIGHTS",
}

# APK-derived coefficients
SPEED_COEFF = 0.00223694  # mph per unit (from APK)
ODOMETER_COEFF = 3.6128e-5  # miles per unit (from APK)

print("=" * 80)
print("TELEMETRY ANALYSIS — Boosted Rev Notify Log")
print("=" * 80)

with open(LOG_FILE) as f:
    lines = f.readlines()

speed_values = []
odo_values = []
power_values = []
battery_values = []

for line in lines:
    line = line.strip()
    if not line:
        continue

    # Parse: [HH:MM:SS.mmm] label UUID hex_bytes
    match = re.match(r'\[(\S+)\]\s+\S+\s+(\S+)\s+(.*)', line)
    if not match:
        continue

    timestamp = match.group(1)
    uuid = match.group(2)
    hex_data = match.group(3).strip()

    uuid_short = uuid[:8]
    name = UUIDS.get(uuid_short, "?")

    if not hex_data:
        continue

    try:
        raw_bytes = bytes.fromhex(hex_data.replace(" ", ""))
    except ValueError:
        continue

    if uuid_short == "7dc56b34":  # SPEED
        # 2 bytes LE
        val = int.from_bytes(raw_bytes[:2], "little")
        mph = val * SPEED_COEFF
        speed_values.append((timestamp, val, mph))

    elif uuid_short == "7dc56594":  # ODOMETER
        # 4 bytes LE
        val = int.from_bytes(raw_bytes[:4], "little")
        miles = val * ODOMETER_COEFF
        odo_values.append((timestamp, val, miles))

    elif uuid_short == "7dc56bfc":  # POWER
        val = int.from_bytes(raw_bytes[:2], "little", signed=True)
        power_values.append((timestamp, val, raw_bytes.hex()))

    elif uuid_short == "65a8eeae":  # BATTERY
        val = raw_bytes[0]
        battery_values.append((timestamp, val))


# --- SPEED ---
print(f"\n{'─'*40}")
print(f"VEHICLE_SPEED (7dc56b34) — {len(speed_values)} samples")
print(f"  APK decoder: 2 bytes LE × {SPEED_COEFF}")
print(f"{'─'*40}")
if speed_values:
    print(f"  {'Time':>15s}  {'Raw':>6s}  {'MPH':>8s}")
    for ts, raw, mph in speed_values:
        bar = "█" * int(mph)
        print(f"  {ts:>15s}  {raw:>6d}  {mph:>8.2f}  {bar}")
    raw_vals = [v[1] for v in speed_values]
    mph_vals = [v[2] for v in speed_values]
    print(f"\n  Raw range: {min(raw_vals)} – {max(raw_vals)}")
    print(f"  MPH range: {min(mph_vals):.2f} – {max(mph_vals):.2f}")
    print(f"  Peak MPH:  {max(mph_vals):.2f}")

    # Check if the speed at 0 throttle is ~0
    zero_speeds = [v for v in speed_values if v[1] < 10]
    if zero_speeds:
        print(f"  Zero-throttle samples: {len(zero_speeds)} (raw < 10 → confirms 0 mph baseline)")

# --- ODOMETER ---
print(f"\n{'─'*40}")
print(f"VEHICLE_ODOMETER (7dc56594) — {len(odo_values)} samples")
print(f"  APK decoder: 4 bytes LE × {ODOMETER_COEFF}")
print(f"{'─'*40}")
if odo_values:
    first = odo_values[0]
    last = odo_values[-1]
    print(f"  First: raw={first[1]:>10d}  → {first[2]:.4f} miles")
    print(f"  Last:  raw={last[1]:>10d}  → {last[2]:.4f} miles")
    print(f"  Delta: raw={last[1]-first[1]:>10d}  → {last[2]-first[2]:.6f} miles")
    print(f"\n  Odometer reading: ~{last[2]:.1f} miles")
    # Check if monotonically increasing
    raw_vals = [v[1] for v in odo_values]
    is_mono = all(raw_vals[i] <= raw_vals[i+1] for i in range(len(raw_vals)-1))
    print(f"  Monotonically increasing: {'YES ✅' if is_mono else 'NO ⚠️'}")

# --- POWER ---
print(f"\n{'─'*40}")
print(f"VEHICLE_POWER (7dc56bfc) — {len(power_values)} samples")
print(f"  Format: 2 bytes LE (signed?)")
print(f"{'─'*40}")
if power_values:
    for ts, val, hexv in power_values:
        print(f"  {ts:>15s}  raw={val:>6d}  hex={hexv}")
    vals = [v[1] for v in power_values]
    print(f"\n  Range: {min(vals)} – {max(vals)}")

# --- BATTERY ---
print(f"\n{'─'*40}")
print(f"BATTERY_REMAINING (65a8eeae) — {len(battery_values)} samples")
print(f"{'─'*40}")
if battery_values:
    for ts, val in battery_values:
        print(f"  {ts:>15s}  {val}  (0x{val:02X})")
    print(f"\n  Interpretation: {battery_values[0][1]}% (if direct percentage)")

print(f"\n{'='*80}")
print("ANALYSIS COMPLETE")
print(f"{'='*80}")
