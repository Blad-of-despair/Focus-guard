package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.AppRestriction
import com.example.data.model.InstalledAppItem
import com.example.data.model.RestrictionMode
import com.example.engine.CommitmentLockValidator
import com.example.engine.RestrictionEngine
import com.example.ui.viewmodel.FocusGuardViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RestrictionsScreen(
    viewModel: FocusGuardViewModel
) {
    val restrictions by viewModel.restrictions.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()

    var showEditDialogForApp by remember { mutableStateOf<InstalledAppItem?>(null) }
    var showAppPickerSheet by remember { mutableStateOf(false) }

    val categories = listOf("All", "Social & Video", "Games", "Productivity", "Other")

    val filteredApps = installedApps.filter { app ->
        val matchesQuery = app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        val matchesCategory = if (selectedCategory == "All") true else app.category == selectedCategory
        matchesQuery && matchesCategory
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("restrictions_screen_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Search & Filters
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("restrictions_search_input"),
                    singleLine = true
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setCategoryFilter(cat) },
                            label = { Text(cat) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Section 1: Active Configured Restrictions
            item {
                Text(
                    text = "Configured Restrictions (${restrictions.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (restrictions.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No active restrictions configured.",
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap any installed app below or click the + button to add a rule.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(restrictions) { restriction ->
                    val isLocked = restriction.isCommitmentActive()
                    val countdown = if (isLocked) CommitmentLockValidator.formatLockCountdown(restriction.commitmentLockedUntil) else ""

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (restriction.isPaused)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restriction_card_${restriction.packageName}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = restriction.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (restriction.mode) {
                                            RestrictionMode.BLOCK_COMPLETELY -> "Completely Blocked"
                                            RestrictionMode.WINDOW_ONLY -> "Allowed: ${RestrictionEngine.formatMinutes(restriction.windowStartMinutes)} - ${RestrictionEngine.formatMinutes(restriction.windowEndMinutes)}"
                                            else -> "Daily Limit: ${restriction.dailyLimitMinutes} min"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Pause toggle
                                    IconButton(
                                        onClick = { viewModel.togglePauseRestriction(restriction) },
                                        enabled = !isLocked
                                    ) {
                                        Icon(
                                            imageVector = if (restriction.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = if (restriction.isPaused) "Resume" else "Pause",
                                            tint = if (isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Edit button
                                    IconButton(
                                        onClick = {
                                            val appItem = installedApps.firstOrNull { it.packageName == restriction.packageName }
                                                ?: InstalledAppItem(packageName = restriction.packageName, appName = restriction.appName)
                                            showEditDialogForApp = appItem
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                                    }

                                    // Delete button
                                    IconButton(
                                        onClick = { viewModel.deleteRestriction(restriction) },
                                        enabled = !isLocked
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = if (isLocked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Badges row
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLocked) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = countdown,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                }

                                if (restriction.isAppLockProtected) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Fingerprint,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "PIN/Biometric Locked",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${restriction.getDaysList().size} days/week",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Installed Apps List
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Installed Apps (${filteredApps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(filteredApps) { appItem ->
                val existingRestriction = restrictions.firstOrNull { it.packageName == appItem.packageName }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditDialogForApp = appItem }
                        .testTag("app_item_${appItem.packageName}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (appItem.icon != null) {
                                Image(
                                    bitmap = appItem.icon.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = appItem.appName,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = appItem.appName,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (existingRestriction != null) "Restricted • ${existingRestriction.dailyLimitMinutes}m limit" else appItem.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (existingRestriction != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Button(
                            onClick = { showEditDialogForApp = appItem },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (existingRestriction != null) "Edit" else "Restrict")
                        }
                    }
                }
            }
        }

        // Floating Action Button to pick app
        FloatingActionButton(
            onClick = {
                if (installedApps.isNotEmpty()) {
                    showEditDialogForApp = installedApps.first()
                }
            },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_restriction_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Restriction")
        }
    }

    // Edit / Create Restriction Dialog
    showEditDialogForApp?.let { appItem ->
        val existing = restrictions.firstOrNull { it.packageName == appItem.packageName }
        RestrictionConfigDialog(
            appItem = appItem,
            existing = existing,
            onDismiss = { showEditDialogForApp = null },
            onSave = { updated, enableCommitmentLock ->
                viewModel.saveRestriction(updated, enableCommitmentLock)
                showEditDialogForApp = null
            }
        )
    }
}

@Composable
fun RestrictionConfigDialog(
    appItem: InstalledAppItem,
    existing: AppRestriction?,
    onDismiss: () -> Unit,
    onSave: (AppRestriction, Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(existing?.mode ?: RestrictionMode.DAILY_LIMIT_ONLY) }
    var dailyLimit by remember { mutableIntStateOf(existing?.dailyLimitMinutes ?: 60) }
    var windowStartMinutes by remember { mutableIntStateOf(existing?.windowStartMinutes ?: 1260) }
    var windowEndMinutes by remember { mutableIntStateOf(existing?.windowEndMinutes ?: 1320) }
    var selectedDays by remember { mutableStateOf(existing?.getDaysList() ?: listOf(1, 2, 3, 4, 5, 6, 7)) }
    var isAppLockProtected by remember { mutableStateOf(existing?.isAppLockProtected ?: false) }
    var enable7DayCommitment by remember { mutableStateOf(existing?.isCommitmentActive() ?: false) }

    val isAlreadyLocked = existing?.isCommitmentActive() == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Configure ${appItem.appName}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                if (isAlreadyLocked) {
                    Text(
                        text = "7-Day Commitment Lock active. Rules may only be made stricter.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Restriction Mode Selector
                item {
                    Text("Restriction Mode", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = mode == RestrictionMode.DAILY_LIMIT_ONLY,
                                onClick = { mode = RestrictionMode.DAILY_LIMIT_ONLY }
                            )
                            Text("Daily Limit Only")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = mode == RestrictionMode.WINDOW_ONLY,
                                onClick = { mode = RestrictionMode.WINDOW_ONLY }
                            )
                            Text("Allow Only in Window")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = mode == RestrictionMode.BLOCK_COMPLETELY,
                                onClick = { mode = RestrictionMode.BLOCK_COMPLETELY }
                            )
                            Text("Block Completely")
                        }
                    }
                }

                // Daily Limit Slider
                if (mode == RestrictionMode.DAILY_LIMIT_ONLY) {
                    item {
                        Text(
                            text = "Daily Limit: $dailyLimit minutes",
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = dailyLimit.toFloat(),
                            onValueChange = {
                                // If locked, can only reduce
                                val newLimit = it.toInt()
                                if (!isAlreadyLocked || newLimit <= (existing?.dailyLimitMinutes ?: 1440)) {
                                    dailyLimit = newLimit
                                }
                            },
                            valueRange = 5f..300f,
                            steps = 58,
                            modifier = Modifier.testTag("daily_limit_slider")
                        )
                    }
                }

                // Time Window Selector
                if (mode == RestrictionMode.WINDOW_ONLY) {
                    item {
                        Text(
                            text = "Allowed Window: ${RestrictionEngine.formatMinutes(windowStartMinutes)} to ${RestrictionEngine.formatMinutes(windowEndMinutes)}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Outside this window, access to ${appItem.appName} will be blocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Days of the week
                item {
                    Text("Active Days", fontWeight = FontWeight.SemiBold)
                    val days = listOf("M" to 1, "T" to 2, "W" to 3, "T" to 4, "F" to 5, "S" to 6, "S" to 7)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for ((name, dayNum) in days) {
                            val isSelected = selectedDays.contains(dayNum)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedDays = if (isSelected) {
                                        if (isAlreadyLocked) selectedDays else selectedDays - dayNum
                                    } else {
                                        selectedDays + dayNum
                                    }
                                },
                                label = { Text(name) },
                                shape = CircleShape
                            )
                        }
                    }
                }

                // App Lock PIN/Biometric Protection Toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PIN / Biometric Lock", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Require authentication before opening this app",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = isAppLockProtected,
                            onCheckedChange = { isAppLockProtected = it },
                            modifier = Modifier.testTag("app_lock_switch")
                        )
                    }
                }

                // 7-Day Commitment Lock
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "7-Day Commitment Lock",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Text(
                                        text = "Cannot be loosened or deleted for 7 days.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Switch(
                                    checked = enable7DayCommitment,
                                    onCheckedChange = {
                                        if (!isAlreadyLocked) {
                                            enable7DayCommitment = it
                                        }
                                    },
                                    enabled = !isAlreadyLocked,
                                    modifier = Modifier.testTag("commitment_lock_switch")
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = AppRestriction(
                        packageName = appItem.packageName,
                        appName = appItem.appName,
                        mode = mode,
                        dailyLimitMinutes = dailyLimit,
                        windowStartMinutes = windowStartMinutes,
                        windowEndMinutes = windowEndMinutes,
                        daysOfWeek = selectedDays.joinToString(","),
                        isPaused = existing?.isPaused ?: false,
                        commitmentLockedUntil = existing?.commitmentLockedUntil ?: 0L,
                        minLimitCommitted = minOf(dailyLimit, existing?.minLimitCommitted ?: dailyLimit),
                        isAppLockProtected = isAppLockProtected
                    )
                    onSave(updated, enable7DayCommitment && !isAlreadyLocked)
                },
                modifier = Modifier.testTag("save_restriction_confirm_button")
            ) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
