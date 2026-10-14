package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.FocusGuardViewModel
import com.example.util.PermissionHelper

@Composable
fun SettingsScreen(
    viewModel: FocusGuardViewModel,
    onOpenPinSetup: () -> Unit
) {
    val context = LocalContext.current
    val permissions by viewModel.permissions.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isPinSet by viewModel.isPinSet.collectAsState()
    val lockFocusGuard by viewModel.lockFocusGuard.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()

    var showOemDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: Security & App Lock
        item {
            Text(
                text = "Security & App Lock",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Security PIN", fontWeight = FontWeight.Bold)
                                Text(
                                    if (isPinSet) "Configured & Salted (Keystore)" else "Not set yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Button(
                            onClick = onOpenPinSetup,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("setup_pin_button")
                        ) {
                            Text(if (isPinSet) "Change PIN" else "Set PIN")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Biometric Authentication", fontWeight = FontWeight.Bold)
                                Text(
                                    "Use fingerprint / face unlock with PIN fallback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = biometricsEnabled,
                            onCheckedChange = { viewModel.setBiometrics(it) },
                            modifier = Modifier.testTag("biometrics_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Lock FocusGuard App", fontWeight = FontWeight.Bold)
                                Text(
                                    "Auto-lock when leaving; hide recents content",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Switch(
                            checked = lockFocusGuard,
                            onCheckedChange = { viewModel.setLockFocusGuard(it) },
                            modifier = Modifier.testTag("lock_focusguard_toggle_switch")
                        )
                    }
                }
            }
        }

        // Section: Permissions Checklist
        item {
            Text(
                text = "Permissions & System Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(permissions) { perm ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = PermissionHelper.createIntentForPermission(context, perm.id)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .testTag("permission_item_${perm.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = perm.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (perm.isRequired) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "*Required",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = perm.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (perm.isGranted)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (perm.isGranted) "Granted" else "Grant",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (perm.isGranted)
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Section: OEM Battery Saver Guidance
        item {
            OutlinedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOemDialog = true }
                    .testTag("oem_battery_guide_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("OEM Battery Optimization Quirks", fontWeight = FontWeight.Bold)
                            Text(
                                "Samsung, Xiaomi, Oppo, Vivo background guide",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
        }

        // Section: Appearance & Theme
        item {
            Text(
                text = "Theme & Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Theme Mode", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // About FocusGuard Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FocusGuard v1.0",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hardware monotonic clock tamper protection, 7-Day unbreakable commitments, PBKDF2/Keystore biometric protection, and zero-latency accessibility enforcement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    // OEM Guidance Dialog
    if (showOemDialog) {
        AlertDialog(
            onDismissRequest = { showOemDialog = false },
            title = { Text("OEM Battery Optimization Guide", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(
                            text = "Certain manufacturers aggressively kill background services. Follow these steps to ensure uninterrupted protection:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    item {
                        Text("• Samsung OneUI:", fontWeight = FontWeight.Bold)
                        Text(
                            "Settings > Apps > FocusGuard > Battery > Select 'Unrestricted'. Also ensure FocusGuard is NOT in 'Sleeping Apps'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text("• Xiaomi MIUI / HyperOS:", fontWeight = FontWeight.Bold)
                        Text(
                            "Settings > Apps > Manage Apps > FocusGuard > Enable 'Autostart' and set Battery Saver to 'No restrictions'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text("• Oppo / Realme (ColorOS):", fontWeight = FontWeight.Bold)
                        Text(
                            "App info > Battery usage > Enable 'Allow background activity' and 'Allow auto-launch'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    item {
                        Text("• Vivo (FuntouchOS):", fontWeight = FontWeight.Bold)
                        Text(
                            "Settings > Battery > Background power consumption > Set FocusGuard to 'High background power usage'.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOemDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}
