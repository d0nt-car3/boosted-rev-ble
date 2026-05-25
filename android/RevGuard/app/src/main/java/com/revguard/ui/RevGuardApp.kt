package com.revguard.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.revguard.BleManager
import com.revguard.Constants
import com.revguard.EventLog
import com.revguard.R
import com.revguard.RevGuardViewModel
import com.revguard.RevGuardViewModel.ScanStatus
import com.revguard.ui.theme.StatusConnecting
import com.revguard.ui.theme.StatusError
import com.revguard.ui.theme.StatusLocked
import com.revguard.ui.theme.StatusWarning

/**
 * Root composable for the Rev Guard UI.
 *
 * Single-screen app: status overview at the top, connect button
 * in the middle, and a collapsible event log at the bottom.
 * The bond wizard card appears conditionally when no scooter
 * is paired to the phone.
 */
@Composable
fun RevGuardApp(viewModel: RevGuardViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val modeByte by viewModel.modeByte.collectAsStateWithLifecycle()
    val batteryPercent by viewModel.batteryPercent.collectAsStateWithLifecycle()
    val logEntries by viewModel.logEntries.collectAsStateWithLifecycle()
    val bondStatus by viewModel.bondStatus.collectAsStateWithLifecycle()
    val scanStatus by viewModel.scanStatus.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(20.dp))

            // Bond wizard (hidden when the scooter is already paired)
            if (!bondStatus.isBonded) {
                BondCard(
                    scanStatus = scanStatus,
                    onScanClick = { viewModel.startScan() },
                    onDeviceTap = { device -> viewModel.bondDevice(device) }
                )
                Spacer(Modifier.height(12.dp))
            }

            // Connection status
            StatusCard(connectionState)

            Spacer(Modifier.height(12.dp))

            // Mode and battery side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeCard(
                    modeByte = modeByte,
                    connectionState = connectionState,
                    modifier = Modifier.weight(1f)
                )
                BatteryCard(
                    percent = batteryPercent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Main action button
            ConnectButton(
                connectionState = connectionState,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() }
            )

            Spacer(Modifier.height(12.dp))

            // Event log with expand/collapse
            EventLogCard(
                entries = logEntries,
                onExport = {
                    viewModel.getLogShareIntent()?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Export Log"))
                    }
                }
            )
        }
    }
}

// -- Status Card -------------------------------------------------------------

/**
 * Displays the current BLE connection state with a color-coded label.
 */
@Composable
private fun StatusCard(state: BleManager.State) {
    val (statusText, statusColor) = when (state) {
        BleManager.State.CONNECTED -> "Connected" to StatusLocked
        BleManager.State.CONNECTING -> "Connecting..." to StatusConnecting
        BleManager.State.RECONNECTING -> "Reconnecting..." to StatusWarning
        BleManager.State.DISCONNECTED -> "Disconnected" to StatusError
    }

    InfoCard {
        SectionLabel(stringResource(R.string.label_status))
        Text(
            text = statusText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

// -- Mode Card ---------------------------------------------------------------

/**
 * Shows the current ride mode and lock status.
 * Color reflects whether the target mode is enforced.
 */
@Composable
private fun ModeCard(
    modeByte: Byte?,
    connectionState: BleManager.State,
    modifier: Modifier = Modifier
) {
    val (text, color) = when {
        connectionState != BleManager.State.CONNECTED -> "--" to MaterialTheme.colorScheme.onSurface
        modeByte == null -> "--" to MaterialTheme.colorScheme.onSurface
        modeByte == Constants.TARGET_MODE_BYTE -> "LOCKED\n${Constants.modeLabel(modeByte)}" to StatusLocked
        else -> "REVERTING\n${Constants.modeLabel(modeByte)}" to StatusWarning
    }

    InfoCard(modifier) {
        SectionLabel(stringResource(R.string.label_mode))
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// -- Battery Card ------------------------------------------------------------

@Composable
private fun BatteryCard(
    percent: Int?,
    modifier: Modifier = Modifier
) {
    InfoCard(modifier) {
        SectionLabel(stringResource(R.string.label_battery))
        Text(
            text = if (percent != null) "$percent%" else "--%",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// -- Connect / Disconnect Button ---------------------------------------------

@Composable
private fun ConnectButton(
    connectionState: BleManager.State,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState == BleManager.State.CONNECTED
    val isTransitional = connectionState == BleManager.State.CONNECTING ||
            connectionState == BleManager.State.RECONNECTING

    Button(
        onClick = if (isConnected) onDisconnect else onConnect,
        enabled = !isTransitional,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isConnected)
                StatusError.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            text = if (isConnected)
                stringResource(R.string.btn_disconnect)
            else
                stringResource(R.string.btn_connect),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (isConnected) StatusError else Color.White
        )
    }
}

// -- Bond Wizard Card --------------------------------------------------------

/**
 * Shown only when no Boosted Rev is bonded to the phone.
 * Guides the user through scanning and pairing.
 */
@Composable
private fun BondCard(
    scanStatus: ScanStatus,
    onScanClick: () -> Unit,
    onDeviceTap: (android.bluetooth.BluetoothDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.bond_required_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = StatusWarning
            )

            Spacer(Modifier.height(8.dp))

            // Step-by-step bonding instructions
            Text(
                text = com.revguard.BondingHelper.instructions.joinToString("\n"),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = scanStatus !is ScanStatus.Scanning && scanStatus !is ScanStatus.Bonding
            ) {
                Text(
                    text = when (scanStatus) {
                        is ScanStatus.Scanning -> "Scanning..."
                        is ScanStatus.Bonding -> "Bonding..."
                        else -> "Scan for Scooter"
                    },
                    fontSize = 15.sp
                )
            }

            // Show scan results below the button
            when (scanStatus) {
                is ScanStatus.Found -> {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { onDeviceTap(scanStatus.device) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Tap to bond: ${scanStatus.deviceName}",
                            color = StatusConnecting,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
                is ScanStatus.Bonded -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Bonded to ${scanStatus.deviceName}",
                        color = StatusLocked,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
                is ScanStatus.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = scanStatus.message,
                        color = StatusError,
                        fontSize = 13.sp
                    )
                }
                else -> { /* Idle or Scanning: no extra content */ }
            }
        }
    }
}

// -- Event Log Card ----------------------------------------------------------

/**
 * Displays the most recent 3 log entries by default with an
 * expand button to show the full history. The full log animates
 * in/out to avoid a jarring layout jump.
 */
@Composable
private fun EventLogCard(
    entries: List<EventLog.Entry>,
    onExport: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val recentCount = 3
    val recentEntries = entries.takeLast(recentCount)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row: label + export button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(stringResource(R.string.label_event_log))
                TextButton(onClick = onExport) {
                    Text(
                        text = stringResource(R.string.btn_export_log),
                        fontSize = 11.sp
                    )
                }
            }

            // Recent entries (always visible)
            if (recentEntries.isEmpty()) {
                Text(
                    text = "No events recorded",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                recentEntries.forEach { entry ->
                    LogEntryText(entry)
                }
            }

            // Expand/collapse toggle
            if (entries.size > recentCount) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (expanded) "Hide Full Log" else "View Full Log (${entries.size})",
                        fontSize = 13.sp
                    )
                }

                // Full log entries with slide animation
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(Modifier.padding(top = 8.dp)) {
                        entries.forEach { entry ->
                            LogEntryText(entry)
                        }
                    }
                }
            }
        }
    }
}

// -- Shared Components -------------------------------------------------------

/**
 * Uppercase section label used across all cards (STATUS, MODE, BATTERY, etc).
 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.5.sp
    )
}

/**
 * Reusable card wrapper. All info cards share the same shape,
 * background color, and internal padding.
 */
@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(20.dp)) {
            content()
        }
    }
}

/**
 * Single log entry rendered in monospace with timestamp.
 */
@Composable
private fun LogEntryText(entry: EventLog.Entry) {
    Text(
        text = entry.toString(),
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
