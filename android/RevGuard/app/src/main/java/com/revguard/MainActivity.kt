package com.revguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

/**
 * Main (and only) activity for Rev Guard.
 * Binds to [BleService], displays connection state, mode lock status,
 * battery level, and a truncated event log (last 3 entries).
 * Full log is available via an expand button.
 * Handles BLE scanning and bonding wizard on first run.
 */
@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity() {

    private var bleService: BleService? = null
    private var bound = false

    /** Number of recent log entries to display on the main screen. */
    private val VISIBLE_LOG_COUNT = 3

    /** Whether the full log is currently expanded. */
    private var logExpanded = false

    // ── Views ────────────────────────────────────────────────────────────
    private lateinit var txtStatus: TextView
    private lateinit var txtMode: TextView
    private lateinit var txtBattery: TextView
    private lateinit var txtEventLog: TextView
    private lateinit var txtFullLog: TextView
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnExportLog: Button
    private lateinit var btnExpandLog: MaterialButton
    private lateinit var bondCard: MaterialCardView
    private lateinit var bondInstructions: TextView
    private lateinit var btnOpenBtSettings: MaterialButton
    private lateinit var txtScanResults: TextView

    private val PERMISSION_REQUEST_CODE = 100

    // ── Listener references (so we can remove them on destroy) ────────────
    private val uiStateListener: (BleManager.State) -> Unit = { state ->
        runOnUiThread { updateStatusUI(state) }
    }
    private val uiModeListener: (Byte) -> Unit = { modeByte ->
        runOnUiThread { updateModeUI(modeByte) }
    }
    private val uiBatteryListener: (Int) -> Unit = { percent ->
        runOnUiThread { txtBattery.text = "$percent%" }
    }

    // ── Service connection ───────────────────────────────────────────────
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.service
            bound = true
            setupServiceCallbacks()
            checkBondAndUpdateUI()
            refreshLog()
            loadCachedState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            bound = false
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind views
        txtStatus = findViewById(R.id.txtStatus)
        txtMode = findViewById(R.id.txtMode)
        txtBattery = findViewById(R.id.txtBattery)
        txtEventLog = findViewById(R.id.txtEventLog)
        txtFullLog = findViewById(R.id.txtFullLog)
        btnConnect = findViewById(R.id.btnConnect)
        btnExportLog = findViewById(R.id.btnExportLog)
        btnExpandLog = findViewById(R.id.btnExpandLog)
        bondCard = findViewById(R.id.bondCard)
        bondInstructions = findViewById(R.id.bondInstructions)
        btnOpenBtSettings = findViewById(R.id.btnOpenBtSettings)
        txtScanResults = findViewById(R.id.txtScanResults)

        // Button listeners
        btnConnect.setOnClickListener { onConnectToggle() }
        btnExportLog.setOnClickListener { onExportLog() }
        btnOpenBtSettings.setOnClickListener { onStartScan() }
        btnExpandLog.setOnClickListener { toggleLogExpansion() }

        requestPermissions()

        // Start and bind to the foreground service
        val serviceIntent = Intent(this, BleService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        if (bound) checkBondAndUpdateUI()
    }

    override fun onDestroy() {
        BondingHelper.cleanup(this)
        if (bound) {
            bleService?.bleManager?.removeStateListener(uiStateListener)
            bleService?.bleManager?.removeModeListener(uiModeListener)
            bleService?.bleManager?.removeBatteryListener(uiBatteryListener)
            bleService?.eventLog?.removeListener(logListener)
            unbindService(serviceConnection)
            bound = false
        }
        super.onDestroy()
    }

    // ── Permissions ──────────────────────────────────────────────────────

    /** Request all BLE and notification permissions at launch. */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // ── Service Callbacks ────────────────────────────────────────────────

    /** Register UI listeners alongside the watchdog's listeners (additive, not replacing). */
    private fun setupServiceCallbacks() {
        val manager = bleService?.bleManager ?: return
        manager.addStateListener(uiStateListener)
        manager.addModeListener(uiModeListener)
        manager.addBatteryListener(uiBatteryListener)
        bleService?.eventLog?.addListener(logListener)
    }

    /**
     * Load cached values from BleManager for cases where the BLE connection
     * was established before the UI bound to the service.
     */
    private fun loadCachedState() {
        val manager = bleService?.bleManager ?: return
        updateStatusUI(manager.state)
        manager.lastModeByte?.let { updateModeUI(it) }
        manager.lastBatteryPercent?.let { txtBattery.text = "$it%" }
    }

    /** Receives each new log entry and refreshes the truncated view. */
    private val logListener: (EventLog.Entry) -> Unit = { _ ->
        runOnUiThread { refreshLog() }
    }

    /**
     * Refresh the log displays:
     * - Main view: last [VISIBLE_LOG_COUNT] entries
     * - Full view: all entries (only if expanded)
     */
    private fun refreshLog() {
        val entries = bleService?.eventLog?.allEntries ?: return

        // Truncated view — last 3
        val recent = entries.takeLast(VISIBLE_LOG_COUNT)
        txtEventLog.text = recent.joinToString("\n") { it.toString() }

        // Update expand button count
        val total = entries.size
        if (total > VISIBLE_LOG_COUNT) {
            btnExpandLog.visibility = View.VISIBLE
            btnExpandLog.text = if (logExpanded) "Collapse Log" else "View Full Log ($total entries)"
        } else {
            btnExpandLog.visibility = View.GONE
        }

        // Full log (if expanded)
        if (logExpanded) {
            txtFullLog.text = entries.joinToString("\n") { it.toString() }
        }
    }

    /** Toggle between showing last 3 entries and the full event log. */
    private fun toggleLogExpansion() {
        logExpanded = !logExpanded
        if (logExpanded) {
            txtFullLog.visibility = View.VISIBLE
            refreshLog()
        } else {
            txtFullLog.visibility = View.GONE
        }
        // Update button text
        val total = bleService?.eventLog?.allEntries?.size ?: 0
        btnExpandLog.text = if (logExpanded) "Collapse Log" else "View Full Log ($total entries)"
    }

    // ── UI Updates ───────────────────────────────────────────────────────

    /** Update the connection status indicator text and color. */
    private fun updateStatusUI(state: BleManager.State) {
        when (state) {
            BleManager.State.DISCONNECTED -> {
                txtStatus.text = getString(R.string.status_disconnected)
                txtStatus.setTextColor(getColor(R.color.status_error))
                btnConnect.text = getString(R.string.btn_connect)
                btnConnect.isEnabled = true
                txtMode.text = getString(R.string.mode_unknown)
                txtBattery.text = getString(R.string.battery_unknown)
            }
            BleManager.State.CONNECTING -> {
                txtStatus.text = getString(R.string.status_connecting)
                txtStatus.setTextColor(getColor(R.color.status_connecting))
                btnConnect.isEnabled = false
            }
            BleManager.State.CONNECTED -> {
                txtStatus.text = getString(R.string.status_connected)
                txtStatus.setTextColor(getColor(R.color.status_locked))
                btnConnect.text = getString(R.string.btn_disconnect)
                btnConnect.isEnabled = true
            }
            BleManager.State.RECONNECTING -> {
                txtStatus.text = getString(R.string.status_reconnecting)
                txtStatus.setTextColor(getColor(R.color.status_warning))
                btnConnect.text = getString(R.string.btn_disconnect)
                btnConnect.isEnabled = true
            }
        }
    }

    /** Update the mode lock indicator based on current mode byte. */
    private fun updateModeUI(modeByte: Byte) {
        if (modeByte == Constants.TARGET_MODE_BYTE) {
            txtMode.text = getString(R.string.mode_locked)
            txtMode.setTextColor(getColor(R.color.status_locked))
        } else {
            txtMode.text = getString(R.string.mode_reverting)
            txtMode.setTextColor(getColor(R.color.status_warning))
        }
    }

    // ── Bond Check ───────────────────────────────────────────────────────

    /** Show or hide the bonding wizard card based on whether a Boosted device is bonded. */
    private fun checkBondAndUpdateUI() {
        val status = BondingHelper.checkBondStatus()
        if (status.isBonded) {
            bondCard.visibility = View.GONE
            btnConnect.isEnabled = true
        } else {
            bondCard.visibility = View.VISIBLE
            bondInstructions.text = BondingHelper.instructions.joinToString("\n")
            txtScanResults.text = ""
            btnConnect.isEnabled = false
        }
    }

    // ── BLE Scan + Bond ──────────────────────────────────────────────────

    /** Start a BLE scan for Boosted devices and auto-bond the first one found. */
    private fun onStartScan() {
        btnOpenBtSettings.isEnabled = false
        btnOpenBtSettings.text = "Scanning..."
        txtScanResults.text = "Scanning for Boosted Rev...\n"

        BondingHelper.startScan(
            onDeviceFound = { device ->
                runOnUiThread {
                    BondingHelper.stopScan()
                    val name = device.name ?: device.address
                    txtScanResults.text = "Found: $name\nBonding..."
                    btnOpenBtSettings.text = "Bonding..."

                    BondingHelper.bondDevice(this, device,
                        onBonded = {
                            runOnUiThread {
                                txtScanResults.text = "✅ Bonded to $name!"
                                btnOpenBtSettings.text = "Scan for Scooter"
                                btnOpenBtSettings.isEnabled = true
                                checkBondAndUpdateUI()
                            }
                        },
                        onFailed = { error ->
                            runOnUiThread {
                                txtScanResults.text = "❌ $error\nTry again — press handlebar button 5 times first."
                                btnOpenBtSettings.text = "Scan for Scooter"
                                btnOpenBtSettings.isEnabled = true
                            }
                        }
                    )
                }
            },
            onScanError = { error ->
                runOnUiThread {
                    txtScanResults.text = "❌ $error"
                    btnOpenBtSettings.text = "Scan for Scooter"
                    btnOpenBtSettings.isEnabled = true
                }
            }
        )

        // Auto-stop scan after 30 seconds if nothing found
        btnOpenBtSettings.postDelayed({
            if (!btnOpenBtSettings.isEnabled) {
                BondingHelper.stopScan()
                runOnUiThread {
                    txtScanResults.append("\nNo scooter found. Make sure it's in pairing mode (5 button presses).")
                    btnOpenBtSettings.text = "Scan for Scooter"
                    btnOpenBtSettings.isEnabled = true
                }
            }
        }, 30_000)
    }

    // ── Actions ──────────────────────────────────────────────────────────

    /** Toggle between connect and disconnect based on current BLE state. */
    private fun onConnectToggle() {
        val service = bleService ?: return
        if (service.bleManager.state == BleManager.State.DISCONNECTED) {
            if (!service.connect()) {
                txtStatus.text = "No bonded Boosted device found"
                txtStatus.setTextColor(getColor(R.color.status_error))
            }
        } else {
            service.disconnect()
        }
    }

    /** Export the full event log via Android share sheet. */
    private fun onExportLog() {
        val file = bleService?.eventLog?.exportLogFile() ?: return
        if (!file.exists()) return

        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "$packageName.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export Rev Guard Log"))
    }
}
