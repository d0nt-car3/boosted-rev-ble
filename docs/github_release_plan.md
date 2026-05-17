# GitHub Release Plan

This document preserves the structure and planned contents of the GitHub repository release for the Boosted Rev BLE project.

## Important Note on Legal Compliance
**DO NOT** include the `apk_decompiled` or `jadx` directories when publishing to GitHub. While reverse engineering the protocol is protected, distributing the raw decompiled Java source code from the original Boosted app poses a copyright risk. The knowledge derived from the APK (decoders, coefficients, UUIDs) is safe to publish.

## Planned Repository Structure

```text
boosted-rev-ble/
├── README.md                  ← Primary project overview, quick start, and findings
├── GATT_MAP.md                ← Full characteristic table with live-validated values
├── scripts/
│   ├── discover_all.py        ← Non-destructive full GATT reader + NOTIFY telemetry logger
│   ├── analyze_log.py         ← Telemetry log parser with speed/odometer/power decoders
│   └── pair_and_test.py       ← Connection test + initial GATT dump
├── watchdog/
│   ├── enforce_mode.py        ← BLE Watchdog script to lock ride mode and revert button presses
│   ├── scan.py                ← BLE advertisement scanner to find scooter MAC address
│   └── requirements.txt       ← bleak Python dependency
└── docs/
    ├── methodology.md         ← Documentation of the reverse engineering process (nRF Connect + JADX)
    ├── bonding.md             ← Instructions for triggering pairing mode via the handlebar button
    ├── legal_context.md       ← Utah UCA § 41-6a-1115 context and general speed compliance notes
    └── GATT_protocol_full.md  ← APK-derived protocol map
```

## Content Status
* All core documentation (`README.md`, `GATT_MAP.md`, `docs/`) is written and updated with the latest live telemetry data.
* All Python scripts (`scripts/`, `watchdog/`) are functioning and tested against the Linux/BlueZ environment.
* The Reddit post (`docs/reddit_post_draft.md`) has been adapted to launch the announcement first, with this repository to follow.
