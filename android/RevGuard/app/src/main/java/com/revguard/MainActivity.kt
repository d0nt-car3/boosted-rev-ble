package com.revguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.revguard.ui.RevGuardApp
import com.revguard.ui.theme.RevGuardTheme

/**
 * Entry point for Rev Guard.
 *
 * This activity is intentionally thin: it handles permission
 * requests, starts the foreground service, and hands off
 * everything else to Compose via the ViewModel.
 */
class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions granted or denied; the UI reacts via bond status checks */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        startBleService()

        setContent {
            RevGuardTheme {
                val vm: RevGuardViewModel = viewModel()

                // Bind to the foreground service once. The ViewModel
                // survives configuration changes, so this only runs
                // on the first composition after process creation.
                LaunchedEffect(Unit) {
                    vm.bindService(this@MainActivity)
                }

                RevGuardApp(vm)
            }
        }
    }

    /**
     * Start the BLE foreground service so it survives screen-off.
     * The service handles its own notification and lifecycle.
     */
    private fun startBleService() {
        val intent = Intent(this, BleService::class.java)
        startForegroundService(intent)
    }

    /**
     * Request all BLE and notification permissions up front.
     *
     * Android 12+ requires BLUETOOTH_CONNECT and BLUETOOTH_SCAN.
     * Android 13+ additionally requires POST_NOTIFICATIONS for
     * the foreground service notification. Android 10-11 need
     * ACCESS_FINE_LOCATION for BLE scanning.
     */
    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()

        // BLE permissions (Android 12+, API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        // Location fallback for BLE scanning on Android 10-11
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Notification permission (Android 13+, API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
