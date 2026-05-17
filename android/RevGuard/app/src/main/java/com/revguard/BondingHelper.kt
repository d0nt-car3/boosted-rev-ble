package com.revguard

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings

/**
 * Detects whether a Boosted Rev is bonded to this phone.
 * If not, scans for the scooter over BLE and initiates bonding.
 */
@SuppressLint("MissingPermission")
object BondingHelper {

    data class BondStatus(
        val isBonded: Boolean,
        val deviceName: String?,
        val deviceAddress: String?
    )

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var bondReceiver: BroadcastReceiver? = null

    /**
     * Check if any bonded Bluetooth device matches the Boosted Rev name prefix.
     */
    fun checkBondStatus(): BondStatus {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: return BondStatus(false, null, null)

        val device = adapter.bondedDevices?.firstOrNull { device ->
            device.name?.uppercase()?.startsWith(Constants.DEVICE_NAME_PREFIX) == true
        }

        return BondStatus(
            isBonded = device != null,
            deviceName = device?.name,
            deviceAddress = device?.address
        )
    }

    /**
     * Scan for Boosted Rev scooters over BLE.
     * Results are delivered via the onDeviceFound callback.
     */
    fun startScan(
        onDeviceFound: (BluetoothDevice) -> Unit,
        onScanError: (String) -> Unit
    ) {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onScanError("Bluetooth is not enabled")
            return
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onScanError("BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            private val seen = mutableSetOf<String>()

            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: return
                if (name.uppercase().startsWith(Constants.DEVICE_NAME_PREFIX)) {
                    if (seen.add(device.address)) {
                        onDeviceFound(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onScanError("BLE scan failed (error $errorCode)")
            }
        }

        scanner?.startScan(null, settings, scanCallback!!)
    }

    /**
     * Stop any active BLE scan.
     */
    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
        scanner = null
    }

    /**
     * Initiate bonding with a discovered device.
     * Register a BroadcastReceiver to detect when bonding completes.
     */
    fun bondDevice(
        context: Context,
        device: BluetoothDevice,
        onBonded: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        // Listen for bond state changes
        bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val bondedDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return
                if (bondedDevice.address != device.address) return

                when (bondedDevice.bondState) {
                    BluetoothDevice.BOND_BONDED -> {
                        context.unregisterReceiver(this)
                        bondReceiver = null
                        onBonded()
                    }
                    BluetoothDevice.BOND_NONE -> {
                        context.unregisterReceiver(this)
                        bondReceiver = null
                        onFailed("Bonding failed or was rejected")
                    }
                }
            }
        }

        context.registerReceiver(
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )

        device.createBond()
    }

    /**
     * Open Android Bluetooth Settings so the user can pair the scooter.
     */
    fun openBluetoothSettings(context: Context) {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Bonding instructions to display in the UI.
     */
    val instructions = listOf(
        "1. Turn on your Boosted Rev scooter",
        "2. Press the handlebar button 5 times rapidly",
        "3. Tap \"Scan for Scooter\" below",
        "4. Tap your scooter when it appears to bond"
    )

    /** Stop scanning and unregister any bond receiver. Call from Activity.onDestroy. */
    fun cleanup(context: Context) {
        stopScan()
        bondReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        bondReceiver = null
    }
}
