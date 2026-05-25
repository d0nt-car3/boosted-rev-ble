# Legal Context: Speed Regulations for Electric Scooters

> **Disclaimer:** This is informational only and not legal advice. Verify current regulations in your jurisdiction before riding.

---

## Why This Project Exists

Utah Code § 41-6a-1115 defines a "motor-assisted scooter" as a device with an electric motor ≤ 2,000W capable of no more than **20 mph** on a paved level surface. Devices exceeding this threshold may be classified as electric motorcycles, requiring a driver's license and insurance.

The Boosted Rev's stock Mode 3 top speed is **24 mph**, placing it outside the motor-assisted scooter classification. With Boosted having shut down in 2020, no official firmware update or speed-limiting option exists.

This project provides a software method to cap the Rev at **18 mph (Mode 2)**, keeping it within the 20 mph classification threshold with a 2 mph margin.

---

## Utah Motor-Assisted Scooter Law (UCA § 41-6a-1115)

This statute was established through the 2019 legislative session (Chapter 428) and updated by H.B. 381 (2026 session) with revised definitions for electric mobility devices.

### Classification Requirements (what makes it a "motor-assisted scooter")
- **Motor power:** ≤ 2,000 watts
- **Capable speed:** ≤ 20 mph on a paved level surface
- **Design:** 2+ wheels, handlebars, deck for standing/sitting
- **Treatment:** Subject to the same rules as bicycles

### Operating Speed Limit
- **15 mph maximum** on public ways (UCA § 41-6a-1115)
- This is a behavioral speed limit, like a posted road speed. The device *can* go faster, but you must not
- Local jurisdictions may further restrict speed or prohibit use on specific paths

**This project achieves classification compliance:** Mode 2 = 18 mph capable, under the 20 mph threshold.
**Operating speed is the rider's responsibility:** Stay ≤ 15 mph on public ways

---

## Other U.S. State Speed Regulations

| State | Speed Limit | Notes |
|---|---|---|
| California | 15 mph | On bike paths/sidewalks |
| New York | 20 mph | Class 1/2 e-scooters |
| Texas | 35 mph | On roads; lower limits in cities |
| Florida | 20 mph | Shared-use paths |
| Washington | 20 mph | Bike lanes and paths |
| Colorado | 20 mph | Most jurisdictions |

**Mode recommendations by limit:**

| Jurisdiction Limit | Recommended Mode | Write Value |
|---|---|---|
| ≥ 20 mph | Mode 2 (18 mph) | `0x01` |
| ≥ 15 mph, < 18 mph | Mode 1 (12 mph) | `0x00` |
| Any | Mode 1 (12 mph), conservative | `0x00` |

---

## International Context

Many countries limit electric scooters to 15–25 km/h (~9–15 mph) in public spaces:

| Country | Limit | Mode 1 compliant? | Mode 2 compliant? |
|---|---|---|---|
| UK | 15.5 mph (25 km/h) | Yes | Yes |
| EU (general) | 15.5 mph (25 km/h) | Yes | Yes |
| Australia | ~15 mph varies by state | Yes | Varies |
| Canada | Varies by province | Check locally | Check locally |

---

## Notes on Compliance

Setting the software mode does not prevent a user from physically pressing the handlebar button to change modes. For stricter compliance enforcement:

- **Python:** `watchdog/enforce_mode.py` reverts any mode change within ~100ms via BLE NOTIFY monitoring
- **Android:** [Rev Guard](../android/RevGuard/) runs as a foreground service on your phone, enforcing Mode 2 even with the screen off

The physical button performs multiple functions (power, mode, BLE pairing) and cannot be disabled without disabling the scooter entirely.

---

## Reverse Engineering: Legal Basis

> **Disclaimer:** This is informational context, not legal advice. Consult a lawyer if you have specific concerns.

This project reverse engineered the Boosted Rev's BLE GATT protocol by reading standard Bluetooth Low Energy characteristics from a device the author owns. The following legal frameworks support this activity:

### DMCA §1201(f): Interoperability

The Digital Millennium Copyright Act permits reverse engineering of computer programs for the purpose of achieving interoperability with independently created programs. This project creates interoperable software (Rev Guard, Python watchdog scripts) that communicates with the Boosted Rev's ESC firmware via its standard BLE interface. No access control or DRM was circumvented; BLE bonding is a standard Bluetooth security mechanism, not a technological protection measure.

### Right to Repair

The author owns the hardware being analyzed. Boosted Inc. ceased operations in 2020 and no longer provides software updates, support, or any means of achieving legal speed compliance. The owner's right to maintain, modify, and repair their own property is broadly recognized under U.S. law.

### No Copyrighted Material Redistributed

- The GATT characteristic map documents **empirical observations** (UUID values, byte formats, behavioral notes) gathered from the author's own device. Factual data is not copyrightable.
- The decompiled Boosted APK was used for reference only and is **not included** in this repository.
- All code in this repository is original work by the author.

### Purpose: Legal Compliance, Not Circumvention

This project **restricts** the device's capability (capping speed from 24 mph to 18 mph) to comply with traffic law. It does not unlock, jailbreak, or enhance the device beyond its factory specifications.
