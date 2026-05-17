package com.revguard

import java.util.UUID

/**
 * BLE constants for the Boosted Rev scooter.
 * Derived from validated GATT map (GATT_MAP.md).
 */
object Constants {

    // ── Custom Service UUIDs ─────────────────────────────────────────────
    val SERVICE_ESC: UUID       = UUID.fromString("7dc55a86-c61f-11e5-9912-ba0be0483c18")
    val SERVICE_BATTERY: UUID   = UUID.fromString("65a8eaa8-c61f-11e5-9912-ba0be0483c18")

    // ── Characteristic UUIDs ─────────────────────────────────────────────
    val CHAR_VEHICLE_MODE: UUID      = UUID.fromString("7dc55f22-c61f-11e5-9912-ba0be0483c18")
    val CHAR_BATTERY_REMAINING: UUID = UUID.fromString("65a8eeae-c61f-11e5-9912-ba0be0483c18")

    // ── Mode bytes ───────────────────────────────────────────────────────
    const val MODE_1_BYTE: Byte = 0x00  // 12 mph
    const val MODE_2_BYTE: Byte = 0x01  // 18 mph (Utah-legal target)
    const val MODE_3_BYTE: Byte = 0x02  // 24 mph

    const val TARGET_MODE_BYTE: Byte = MODE_2_BYTE
    const val TARGET_MODE_LABEL: String = "Mode 2 — 18 mph"

    // ── Device identification ────────────────────────────────────────────
    const val DEVICE_NAME_PREFIX: String = "BOOSTED"

    // ── Timing ───────────────────────────────────────────────────────────
    const val BATTERY_READ_INTERVAL_MS: Long = 30_000L
    const val RECONNECT_INITIAL_DELAY_MS: Long = 1_000L
    const val RECONNECT_MAX_DELAY_MS: Long = 30_000L

    // ── Notification ─────────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID: String = "rev_guard_service"
    const val NOTIFICATION_ID: Int = 1

    /**
     * Convert raw mode byte (0/1/2) to human-readable label.
     */
    fun modeLabel(modeByte: Byte): String = when (modeByte) {
        MODE_1_BYTE -> "Mode 1 — 12 mph"
        MODE_2_BYTE -> "Mode 2 — 18 mph"
        MODE_3_BYTE -> "Mode 3 — 24 mph"
        else -> "Unknown (0x${String.format("%02X", modeByte)})"
    }
}
