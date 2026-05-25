package com.revguard

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Manages the BLE connection lifecycle to the Boosted Rev.
 * Handles connect, disconnect, read, write, NOTIFY subscriptions,
 * and auto-reconnect with exponential backoff.
 *
 * Uses listener lists so multiple consumers (watchdog + UI) can
 * register without overwriting each other.
 */
@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val eventLog: EventLog
) {
    // ── State ────────────────────────────────────────────────────────────
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    var state: State = State.DISCONNECTED
        private set

    private var gatt: BluetoothGatt? = null
    private var modeCharacteristic: BluetoothGattCharacteristic? = null
    private var batteryCharacteristic: BluetoothGattCharacteristic? = null
    private var userDisconnected = false

    // ── Listener lists (multiple consumers can register) ─────────────────
    private val stateListeners = mutableListOf<(State) -> Unit>()
    private val modeListeners = mutableListOf<(Byte) -> Unit>()
    private val batteryListeners = mutableListOf<(Int) -> Unit>()

    fun addStateListener(listener: (State) -> Unit) { stateListeners.add(listener) }
    fun addModeListener(listener: (Byte) -> Unit) { modeListeners.add(listener) }
    fun addBatteryListener(listener: (Int) -> Unit) { batteryListeners.add(listener) }

    fun removeStateListener(listener: (State) -> Unit) { stateListeners.remove(listener) }
    fun removeModeListener(listener: (Byte) -> Unit) { modeListeners.remove(listener) }
    fun removeBatteryListener(listener: (Int) -> Unit) { batteryListeners.remove(listener) }

    // ── Last known values (for late-binding UI) ──────────────────────────
    var lastModeByte: Byte? = null
        private set
    var lastBatteryPercent: Int? = null
        private set

    // ── GATT operation queue ─────────────────────────────────────────────
    // Android BLE allows only one outstanding GATT operation at a time.
    // Issuing a read/write/descriptor-write while another is pending
    // causes silent failures. This queue serializes all GATT operations.
    private val opQueue = ConcurrentLinkedQueue<() -> Unit>()
    private var opInProgress = false

    /** Add a GATT operation to the serial queue. Drains immediately if idle. */
    private fun enqueueOp(op: () -> Unit) {
        opQueue.add(op)
        drainQueue()
    }

    /** Execute the next queued operation if none is in progress. */
    private fun drainQueue() {
        if (opInProgress) return
        val next = opQueue.poll() ?: return
        opInProgress = true
        next()
    }

    /** Signal that the current GATT operation finished; drain the next. */
    private fun opComplete() {
        opInProgress = false
        drainQueue()
    }

    // ── Reconnect logic ──────────────────────────────────────────────────
    private var reconnectJob: Job? = null
    private var reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── CCCD UUID for enabling notifications ─────────────────────────────
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // ── GATT Callback ────────────────────────────────────────────────────
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    eventLog.log("BLE", "Connected to ${gatt.device.name ?: gatt.device.address}")
                    reconnectDelay = Constants.RECONNECT_INITIAL_DELAY_MS
                    opQueue.clear()
                    opInProgress = false
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    eventLog.log("BLE", "Disconnected (status=$status)")
                    modeCharacteristic = null
                    batteryCharacteristic = null
                    opQueue.clear()
                    opInProgress = false
                    updateState(State.DISCONNECTED)
                    if (!userDisconnected) {
                        startReconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                eventLog.log("BLE", "Service discovery failed (status=$status)")
                return
            }

            val escService = gatt.getService(Constants.SERVICE_ESC)
            modeCharacteristic = escService?.getCharacteristic(Constants.CHAR_VEHICLE_MODE)

            val batteryService = gatt.getService(Constants.SERVICE_BATTERY)
            batteryCharacteristic = batteryService?.getCharacteristic(Constants.CHAR_BATTERY_REMAINING)

            if (modeCharacteristic == null) {
                eventLog.log("BLE", "ERROR: Mode characteristic not found. Is the device bonded?")
                return
            }

            eventLog.log("BLE", "Services discovered: mode and battery characteristics ready")
            updateState(State.CONNECTED)

            // Queue operations in order: enable notify → read mode → read battery
            enqueueOp { enableNotify(gatt, modeCharacteristic!!) }
            enqueueOp { gatt.readCharacteristic(modeCharacteristic!!) }
            if (batteryCharacteristic != null) {
                enqueueOp { gatt.readCharacteristic(batteryCharacteristic!!) }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                eventLog.log("BLE", "NOTIFY enabled on mode characteristic")
            } else {
                eventLog.log("BLE", "NOTIFY enable failed (status=$status)")
            }
            opComplete()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    Constants.CHAR_VEHICLE_MODE -> {
                        val modeByte = value[0]
                        lastModeByte = modeByte
                        eventLog.log("MODE", "Read: ${Constants.modeLabel(modeByte)}")
                        modeListeners.forEach { it(modeByte) }
                    }
                    Constants.CHAR_BATTERY_REMAINING -> {
                        val percent = value[0].toInt() and 0xFF
                        lastBatteryPercent = percent
                        batteryListeners.forEach { it(percent) }
                    }
                }
            }
            opComplete()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == Constants.CHAR_VEHICLE_MODE) {
                val modeByte = value[0]
                lastModeByte = modeByte
                modeListeners.forEach { it(modeByte) }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == Constants.CHAR_VEHICLE_MODE) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    eventLog.log("MODE", "Write confirmed: ${Constants.TARGET_MODE_LABEL}")
                    // Update UI — ESC may not send a NOTIFY for a value we just wrote
                    lastModeByte = Constants.TARGET_MODE_BYTE
                    modeListeners.forEach { it(Constants.TARGET_MODE_BYTE) }
                } else {
                    eventLog.log("MODE", "Write FAILED (status=$status)")
                }
            }
            opComplete()
        }
    }

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Find the bonded Boosted Rev and initiate a BLE connection.
     * Returns false if no bonded device is found or Bluetooth is unavailable.
     * Clears the userDisconnected flag so auto-reconnect is enabled.
     */
    fun connect(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = findBondedDevice(adapter) ?: return false

        userDisconnected = false
        reconnectJob?.cancel()
        updateState(State.CONNECTING)
        eventLog.log("BLE", "Connecting to ${device.name ?: device.address}...")
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        return true
    }

    /**
     * Explicitly disconnect from the scooter.
     * Sets userDisconnected=true to suppress auto-reconnect — the GATT
     * callback fires asynchronously and would otherwise restart the loop.
     */
    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        modeCharacteristic = null
        batteryCharacteristic = null
        opQueue.clear()
        opInProgress = false
        updateState(State.DISCONNECTED)
        eventLog.log("BLE", "Disconnected by user")
    }

    /** Write the enforcement target mode (Mode 2 / 18mph) to the ESC. Queued. */
    fun writeTargetMode() {
        val gatt = this.gatt ?: return
        val char = this.modeCharacteristic ?: return
        enqueueOp {
            gatt.writeCharacteristic(
                char,
                byteArrayOf(Constants.TARGET_MODE_BYTE),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }

    /** Request a battery percentage read from the scooter. Queued. */
    fun readBattery() {
        val gatt = this.gatt ?: return
        val char = this.batteryCharacteristic ?: return
        enqueueOp { gatt.readCharacteristic(char) }
    }

    /** Tear down all resources. Called when the service is destroyed. */
    fun destroy() {
        reconnectJob?.cancel()
        scope.cancel()
        disconnect()
    }

    // ── Internals ────────────────────────────────────────────────────────

    private fun findBondedDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        return adapter.bondedDevices?.firstOrNull { device ->
            device.name?.uppercase()?.startsWith(Constants.DEVICE_NAME_PREFIX) == true
        }
    }

    private fun enableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            opComplete()
        }
    }

    /**
     * Begin auto-reconnect with exponential backoff (1s → 2s → 4s → ... → 30s max).
     * Only triggered on unexpected disconnects (not user-initiated).
     */
    private fun startReconnect() {
        reconnectJob?.cancel()
        updateState(State.RECONNECTING)
        reconnectJob = scope.launch {
            while (isActive && state != State.CONNECTED) {
                eventLog.log("BLE", "Reconnecting in ${reconnectDelay / 1000}s...")
                delay(reconnectDelay)
                reconnectDelay = (reconnectDelay * 2).coerceAtMost(Constants.RECONNECT_MAX_DELAY_MS)
                connect()
            }
        }
    }

    private fun updateState(newState: State) {
        state = newState
        stateListeners.forEach { it(newState) }
    }
}
