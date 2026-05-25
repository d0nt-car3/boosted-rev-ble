package com.revguard

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the BLE service layer into Compose-observable state.
 *
 * This ViewModel owns the service binding lifecycle and converts
 * the listener-based callbacks from BleManager/BondingHelper into
 * StateFlows that Compose can collect reactively.
 *
 * Survives configuration changes (screen rotation), so the BLE
 * connection state is never lost during a UI rebuild.
 */
class RevGuardViewModel(application: Application) : AndroidViewModel(application) {

    // Scan/bond wizard state machine
    sealed interface ScanStatus {
        data object Idle : ScanStatus
        data object Scanning : ScanStatus
        data class Found(val deviceName: String, val device: android.bluetooth.BluetoothDevice) : ScanStatus
        data object Bonding : ScanStatus
        data class Bonded(val deviceName: String) : ScanStatus
        data class Error(val message: String) : ScanStatus
    }

    // -- Exposed state -------------------------------------------------------

    private val _connectionState = MutableStateFlow(BleManager.State.DISCONNECTED)
    val connectionState: StateFlow<BleManager.State> = _connectionState.asStateFlow()

    private val _modeByte = MutableStateFlow<Byte?>(null)
    val modeByte: StateFlow<Byte?> = _modeByte.asStateFlow()

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    private val _logEntries = MutableStateFlow<List<EventLog.Entry>>(emptyList())
    val logEntries: StateFlow<List<EventLog.Entry>> = _logEntries.asStateFlow()

    private val _bondStatus = MutableStateFlow(BondingHelper.checkBondStatus())
    val bondStatus: StateFlow<BondingHelper.BondStatus> = _bondStatus.asStateFlow()

    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    // -- Service binding -----------------------------------------------------

    private var bleService: BleService? = null
    private var serviceBound = false

    // Listener references kept so we can unregister on cleanup
    private var stateListener: ((BleManager.State) -> Unit)? = null
    private var modeListener: ((Byte) -> Unit)? = null
    private var batteryListener: ((Int) -> Unit)? = null
    private var logListener: ((EventLog.Entry) -> Unit)? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as BleService.LocalBinder).service
            bleService = service
            serviceBound = true
            attachListeners(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bleService = null
            serviceBound = false
        }
    }

    /**
     * Bind to the foreground BleService. Should be called once from
     * the Activity's onCreate (via LaunchedEffect). The ViewModel
     * holds the binding across configuration changes.
     */
    fun bindService(context: Context) {
        if (serviceBound) return
        val intent = Intent(context, BleService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Register listeners on the BLE service so state changes
     * flow into our StateFlows. Also seeds the UI with any
     * values the service already has (late-binding scenario).
     */
    private fun attachListeners(service: BleService) {
        val manager = service.bleManager
        val log = service.eventLog

        // Seed current values so the UI doesn't start blank if
        // the service was already connected before the UI appeared
        _connectionState.value = manager.state
        manager.lastModeByte?.let { _modeByte.value = it }
        manager.lastBatteryPercent?.let { _batteryPercent.value = it }
        _logEntries.value = log.allEntries

        stateListener = { state -> _connectionState.value = state }
        modeListener = { mode -> _modeByte.value = mode }
        batteryListener = { pct -> _batteryPercent.value = pct }
        logListener = { _ -> _logEntries.value = log.allEntries }

        manager.addStateListener(stateListener!!)
        manager.addModeListener(modeListener!!)
        manager.addBatteryListener(batteryListener!!)
        log.addListener(logListener!!)
    }

    // -- User actions --------------------------------------------------------

    fun connect(): Boolean {
        return bleService?.connect() ?: false
    }

    fun disconnect() {
        bleService?.disconnect()
    }

    fun refreshBondStatus() {
        _bondStatus.value = BondingHelper.checkBondStatus()
    }

    /**
     * Start a BLE scan for nearby Boosted Rev scooters.
     * Discovered devices update the scan status flow.
     */
    fun startScan() {
        _scanStatus.value = ScanStatus.Scanning
        BondingHelper.startScan(
            onDeviceFound = { device ->
                _scanStatus.value = ScanStatus.Found(
                    deviceName = device.name ?: device.address,
                    device = device
                )
            },
            onScanError = { message ->
                _scanStatus.value = ScanStatus.Error(message)
            }
        )
    }

    fun stopScan() {
        BondingHelper.stopScan()
        _scanStatus.value = ScanStatus.Idle
    }

    /**
     * Initiate BLE bonding with a discovered device.
     * On success, refreshes bond status so the bond card hides itself.
     */
    fun bondDevice(device: android.bluetooth.BluetoothDevice) {
        _scanStatus.value = ScanStatus.Bonding
        BondingHelper.stopScan()
        BondingHelper.bondDevice(
            context = getApplication(),
            device = device,
            onBonded = {
                _scanStatus.value = ScanStatus.Bonded(device.name ?: device.address)
                refreshBondStatus()
            },
            onFailed = { message ->
                _scanStatus.value = ScanStatus.Error(message)
            }
        )
    }

    /**
     * Build a share intent for the event log file.
     * Returns null if no service is bound.
     */
    fun getLogShareIntent(): Intent? {
        val service = bleService ?: return null
        val file = service.eventLog.exportLogFile()
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // -- Cleanup -------------------------------------------------------------

    override fun onCleared() {
        // Detach listeners to prevent leaks
        bleService?.let { service ->
            stateListener?.let { service.bleManager.removeStateListener(it) }
            modeListener?.let { service.bleManager.removeModeListener(it) }
            batteryListener?.let { service.bleManager.removeBatteryListener(it) }
            logListener?.let { service.eventLog.removeListener(it) }
        }

        if (serviceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (_: IllegalArgumentException) {
                // Service was already unbound
            }
            serviceBound = false
        }

        BondingHelper.cleanup(getApplication())
        super.onCleared()
    }
}
