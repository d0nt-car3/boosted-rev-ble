package com.revguard

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

/**
 * Foreground service that keeps the BLE connection alive with the screen off.
 * Manages the watchdog loop: monitors mode NOTIFY events and auto-reverts
 * any mode changes back to the target mode.
 */
class BleService : Service() {

    inner class LocalBinder : Binder() {
        val service: BleService get() = this@BleService
    }

    private val binder = LocalBinder()
    lateinit var eventLog: EventLog
        private set
    lateinit var bleManager: BleManager
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var batteryJob: Job? = null

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        eventLog = EventLog(applicationContext)
        bleManager = BleManager(applicationContext, eventLog)
        createNotificationChannel()
        setupWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Waiting for connection...")
        startForeground(Constants.NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        batteryJob?.cancel()
        scope.cancel()
        bleManager.destroy()
        super.onDestroy()
    }

    // ── Watchdog Setup ───────────────────────────────────────────────────

    private fun setupWatchdog() {
        // Mode watchdog — auto-revert on any mode change
        bleManager.addModeListener { modeByte ->
            if (modeByte != Constants.TARGET_MODE_BYTE) {
                val fromLabel = Constants.modeLabel(modeByte)
                eventLog.log("MODE_REVERT",
                    "$fromLabel → ${Constants.TARGET_MODE_LABEL} (forced)")
                bleManager.writeTargetMode()
                updateNotification("⚠️ Mode change blocked — reverted to ${Constants.TARGET_MODE_LABEL}")
            } else {
                updateNotification("🔒 ${Constants.TARGET_MODE_LABEL} — Locked")
            }
        }

        // Connection state handler
        bleManager.addStateListener { state ->
            when (state) {
                BleManager.State.CONNECTED -> {
                    updateNotification("🔒 Connected — enforcing ${Constants.TARGET_MODE_LABEL}")
                    // Set initial mode
                    bleManager.writeTargetMode()
                    startBatteryPolling()
                }
                BleManager.State.DISCONNECTED -> {
                    updateNotification("❌ Disconnected")
                    batteryJob?.cancel()
                }
                BleManager.State.CONNECTING -> {
                    updateNotification("Connecting...")
                }
                BleManager.State.RECONNECTING -> {
                    updateNotification("🔄 Reconnecting...")
                    batteryJob?.cancel()
                }
            }
        }
    }

    // ── Battery Polling ──────────────────────────────────────────────────

    private fun startBatteryPolling() {
        batteryJob?.cancel()
        batteryJob = scope.launch {
            while (isActive) {
                bleManager.readBattery()
                delay(Constants.BATTERY_READ_INTERVAL_MS)
            }
        }
    }

    // ── Connect / Disconnect ─────────────────────────────────────────────

    fun connect(): Boolean {
        return bleManager.connect()
    }

    fun disconnect() {
        bleManager.disconnect()
    }

    // ── Notification ─────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Rev Guard Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for BLE speed compliance monitoring"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Rev Guard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, buildNotification(text))
    }
}
