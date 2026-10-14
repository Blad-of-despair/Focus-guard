package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BadgeInfo
import com.example.data.model.DetoxDayStep
import com.example.data.model.DetoxIntensity
import com.example.data.model.DetoxPlan
import com.example.engine.CommitmentLockValidator
import com.example.engine.DetoxPlanGenerator
import com.example.ui.viewmodel.FocusGuardViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetoxScreen(viewModel: FocusGuardViewModel) {
    val detoxPlans by viewModel.detoxPlans.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }

    val activePlan = detoxPlans.firstOrNull { it.isActive }
    val badges = BadgeInfo.getDefaultBadges(
        currentStreak = activePlan?.streakDays ?: 0,
        totalMinutesSaved = activePlan?.totalMinutesSaved ?: 0
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("detox_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Addiction Remover",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Icon(
                            imageVector = Icons.Default.SelfImprovement,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (activePlan != null) {
                        Text(
                            text = activePlan.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Current Streak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${activePlan.streakDays} Days",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Reclaimed Time",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "${activePlan.totalMinutesSaved}m Saved",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.checkInPlan(activePlan) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detox_daily_checkin_button")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Daily Check-In Completed Today")
                        }
                    } else {
                        Text(
                            text = "Rewire your dopamine reflexes with step-by-step gradual reduction plans.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detox_start_program_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start New Detox Program")
                        }
                    }
                }
            }
        }

        // Active Program Schedule Roadmap
        if (activePlan != null) {
            val schedule = DetoxPlanGenerator.generateSchedule(
                activePlan.initialMinutes,
                activePlan.targetGoalMinutes,
                activePlan.intensity
            )

            item {
                Text(
                    text = "Gradual Reduction Roadmap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(schedule.distinctBy { it.description }.take(6)) { step ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = step.description,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${step.allowedDailyMinutes}m daily maximum allowed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (step.allowedDailyMinutes == activePlan.targetGoalMinutes) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Target Goal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Badges and Milestones Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Milestones & Badges",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(badges) { badge ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (badge.isUnlocked)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.width(160.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (badge.isUnlocked) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (badge.iconName) {
                                        "local_fire_department" -> Icons.Default.LocalFireDepartment
                                        "shield" -> Icons.Default.Lock
                                        "hourglass_empty" -> Icons.Default.HourglassEmpty
                                        "self_improvement" -> Icons.Default.SelfImprovement
                                        else -> Icons.Default.Flag
                                    },
                                    contentDescription = null,
                                    tint = if (badge.isUnlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = badge.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = badge.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (badge.isUnlocked) "UNLOCKED" else "LOCKED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (badge.isUnlocked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Detox Plan Dialog
    if (showCreateDialog) {
        CreateDetoxPlanDialog(
            installedApps = installedApps,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, category, pkgs, initial, target, intensity, lock ->
                viewModel.createDetoxPlan(title, category, pkgs, initial, target, intensity, lock)
                showCreateDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateDetoxPlanDialog(
    installedApps: List<com.example.data.model.InstalledAppItem>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>, Int, Int, String, Boolean) -> Unit
) {
    var category by remember { mutableStateOf("REELS") }
    var title by remember { mutableStateOf("Reels & Shorts Reset") }
    var currentUsage by remember { mutableIntStateOf(120) }
    var targetGoal by remember { mutableIntStateOf(30) }
    var intensity by remember { mutableStateOf(DetoxIntensity.MODERATE) }
    var enable7DayLock by remember { mutableStateOf(true) }

    val selectedPackages = remember { mutableStateOf(setOf<String>()) }

    // Preset titles based on category
    fun updateCategory(newCat: String) {
        category = newCat
        when (newCat) {
            "REELS" -> {
                title = "Short-Form Video Detox"
                val pkgs = installedApps.filter {
                    val p = it.packageName.lowercase()
                    p.contains("instagram") || p.contains("tiktok") || p.contains("youtube")
                }.map { it.packageName }
                selectedPackages.value = pkgs.toSet()
            }
            "GAMING" -> {
                title = "Mobile Gaming Reduction"
                val pkgs = installedApps.filter { it.category == "Games" }.map { it.packageName }
                selectedPackages.value = pkgs.toSet()
            }
            "SOCIAL" -> {
                title = "Social Media Reset"
                val pkgs = installedApps.filter { it.category == "Social & Video" }.map { it.packageName }
                selectedPackages.value = pkgs.toSet()
            }
            else -> {
                title = "Custom Focus Protocol"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Detox Program", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Category Pills
                item {
                    Text("Detox Focus Area", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("REELS" to "Reels", "GAMING" to "Gaming", "SOCIAL" to "Social", "CUSTOM" to "Custom").forEach { (key, label) ->
                            FilterChip(
                                selected = category == key,
                                onClick = { updateCategory(key) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // Title Input
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Program Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Initial usage vs target
                item {
                    Text("Current Daily Usage: $currentUsage minutes", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = currentUsage.toFloat(),
                        onValueChange = { currentUsage = it.toInt() },
                        valueRange = 30f..360f,
                        steps = 32
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Target Daily Goal: $targetGoal minutes", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = targetGoal.toFloat(),
                        onValueChange = { targetGoal = it.toInt() },
                        valueRange = 0f..120f,
                        steps = 23
                    )
                }

                // Intensity
                item {
                    Text("Detox Intensity", fontWeight = FontWeight.SemiBold)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = intensity == DetoxIntensity.GENTLE,
                                onClick = { intensity = DetoxIntensity.GENTLE }
                            )
                            Text("Gentle (3 days per step, -10 min drop)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = intensity == DetoxIntensity.MODERATE,
                                onClick = { intensity = DetoxIntensity.MODERATE }
                            )
                            Text("Moderate (2 days per step, -15 min drop)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = intensity == DetoxIntensity.STRICT,
                                onClick = { intensity = DetoxIntensity.STRICT }
                            )
                            Text("Strict (1 day per step, -25 min drop)")
                        }
                    }
                }

                // 7-Day Commitment Lock
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
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
                                    text = "Enforce unbreakable focus. Settings cannot be relaxed for 7 days.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Switch(
                                checked = enable7DayLock,
                                onCheckedChange = { enable7DayLock = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalPkgs = if (selectedPackages.value.isNotEmpty()) {
                        selectedPackages.value.toList()
                    } else {
                        installedApps.take(2).map { it.packageName }
                    }
                    onCreate(title, category, finalPkgs, currentUsage, targetGoal, intensity, enable7DayLock)
                },
                modifier = Modifier.testTag("create_detox_confirm_button")
            ) {
                Text("Activate Plan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
