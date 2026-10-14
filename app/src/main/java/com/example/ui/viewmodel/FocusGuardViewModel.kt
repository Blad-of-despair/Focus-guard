package com.example.ui.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusGuardApplication
import com.example.data.model.AppRestriction
import com.example.data.model.AppUsageInfo
import com.example.data.model.BadgeInfo
import com.example.data.model.BlockEvent
import com.example.data.model.DailyUsageSummary
import com.example.data.model.DetoxDayStep
import com.example.data.model.DetoxPlan
import com.example.data.model.InstalledAppItem
import com.example.data.model.RestrictionMode
import com.example.engine.ClockTamperingResult
import com.example.engine.CommitmentLockValidator
import com.example.engine.DetoxPlanGenerator
import com.example.engine.ValidationResult
import com.example.service.CutoffScheduler
import com.example.util.PermissionHelper
import com.example.util.PermissionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusGuardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusGuardApplication
    private val database = app.database
    private val prefs = app.preferences
    private val securityManager = app.securityManager
    private val usageHelper = app.usageStatsHelper

    // Data streams from Room
    val restrictions: StateFlow<List<AppRestriction>> = database.restrictionDao()
        .getAllRestrictions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val detoxPlans: StateFlow<List<DetoxPlan>> = database.detoxPlanDao()
        .getAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockEvents: StateFlow<List<BlockEvent>> = database.blockEventDao()
        .getRecentBlockEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Installed apps
    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Usage Statistics
    private val _todayUsage = MutableStateFlow<List<AppUsageInfo>>(emptyList())
    val todayUsage: StateFlow<List<AppUsageInfo>> = _todayUsage.asStateFlow()

    private val _weeklyUsage = MutableStateFlow<List<DailyUsageSummary>>(emptyList())
    val weeklyUsage: StateFlow<List<DailyUsageSummary>> = _weeklyUsage.asStateFlow()

    // Permissions
    private val _permissions = MutableStateFlow<List<PermissionItem>>(emptyList())
    val permissions: StateFlow<List<PermissionItem>> = _permissions.asStateFlow()

    // Security & Theme
    val themeMode: StateFlow<String> = prefs.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM")

    val isPinSet: StateFlow<Boolean> = prefs.isPinSetFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lockFocusGuard: StateFlow<Boolean> = prefs.lockFocusGuardFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val biometricsEnabled: StateFlow<Boolean> = prefs.biometricsEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _clockTamperAlert = MutableStateFlow<ClockTamperingResult?>(null)
    val clockTamperAlert: StateFlow<ClockTamperingResult?> = _clockTamperAlert.asStateFlow()

    // UI Feedback Event
    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    init {
        loadInstalledApps()
        refreshStats()
        refreshPermissions()
        checkClockTampering()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun refreshPermissions() {
        _permissions.value = PermissionHelper.getPermissionsList(app)
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = usageHelper.getAllAppsUsageToday()
            val weekly = usageHelper.getWeeklyUsageSummary()
            _todayUsage.value = today
            _weeklyUsage.value = weekly
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = mutableListOf<InstalledAppItem>()

            for (appInfo in packages) {
                // Exclude FocusGuard itself
                if (appInfo.packageName == app.packageName) continue

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                // Categorize
                val lowerLabel = label.lowercase()
                val lowerPkg = appInfo.packageName.lowercase()
                val category = when {
                    lowerPkg.contains("instagram") || lowerPkg.contains("tiktok") ||
                            lowerPkg.contains("twitter") || lowerPkg.contains("facebook") ||
                            lowerPkg.contains("snapchat") || lowerPkg.contains("reddit") ||
                            lowerPkg.contains("youtube") -> "Social & Video"
                    lowerPkg.contains("game") || lowerPkg.contains("pubg") ||
                            lowerPkg.contains("roblox") || lowerPkg.contains("candy") -> "Games"
                    lowerPkg.contains("mail") || lowerPkg.contains("doc") ||
                            lowerPkg.contains("sheet") || lowerPkg.contains("slack") -> "Productivity"
                    else -> "Other"
                }

                list.add(
                    InstalledAppItem(
                        packageName = appInfo.packageName,
                        appName = label,
                        icon = icon,
                        isSystemApp = isSystem,
                        category = category
                    )
                )
            }
            _installedApps.value = list.sortedBy { it.appName.lowercase() }
        }
    }

    fun saveRestriction(
        restriction: AppRestriction,
        enableCommitmentLock: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.restrictionDao().getRestrictionSync(restriction.packageName)
            val finalRestriction = if (enableCommitmentLock) {
                val lockDuration = CommitmentLockValidator.SEVEN_DAYS_MILLIS
                restriction.copy(
                    commitmentLockedUntil = System.currentTimeMillis() + lockDuration,
                    minLimitCommitted = restriction.dailyLimitMinutes
                )
            } else {
                restriction
            }

            if (existing != null) {
                val validation = CommitmentLockValidator.validateRestrictionEdit(existing, finalRestriction)
                if (validation is ValidationResult.Blocked) {
                    _uiEvent.emit(validation.reason)
                    return@launch
                }
            }

            database.restrictionDao().insertRestriction(finalRestriction)
            if (CutoffScheduler.currentlyScheduledPackage == finalRestriction.packageName) {
                CutoffScheduler.cancel(app, finalRestriction.packageName, "Rule settings updated")
            }
            _uiEvent.emit("Restriction saved for ${finalRestriction.appName}")
        }
    }

    fun deleteRestriction(restriction: AppRestriction) {
        viewModelScope.launch(Dispatchers.IO) {
            val validation = CommitmentLockValidator.validateRestrictionDelete(restriction)
            if (validation is ValidationResult.Blocked) {
                _uiEvent.emit(validation.reason)
                return@launch
            }
            database.restrictionDao().deleteRestriction(restriction)
            if (CutoffScheduler.currentlyScheduledPackage == restriction.packageName) {
                CutoffScheduler.cancel(app, restriction.packageName, "Rule deleted")
            }
            _uiEvent.emit("Restriction removed for ${restriction.appName}")
        }
    }

    fun togglePauseRestriction(restriction: AppRestriction) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = restriction.copy(isPaused = !restriction.isPaused)
            val validation = CommitmentLockValidator.validateRestrictionEdit(restriction, updated)
            if (validation is ValidationResult.Blocked) {
                _uiEvent.emit(validation.reason)
                return@launch
            }
            database.restrictionDao().updateRestriction(updated)
            if (updated.isPaused && CutoffScheduler.currentlyScheduledPackage == updated.packageName) {
                CutoffScheduler.cancel(app, updated.packageName, "Rule paused")
            }
            val status = if (updated.isPaused) "paused" else "resumed"
            _uiEvent.emit("${restriction.appName} restriction $status")
        }
    }

    fun createDetoxPlan(
        title: String,
        category: String,
        targetPackages: List<String>,
        initialMinutes: Int,
        targetMinutes: Int,
        intensity: String,
        enable7DayLock: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val lockDuration = if (enable7DayLock) CommitmentLockValidator.SEVEN_DAYS_MILLIS else 0L
            val plan = DetoxPlan(
                title = title,
                category = category,
                targetPackageNames = targetPackages.joinToString(","),
                initialMinutes = initialMinutes,
                targetGoalMinutes = targetMinutes,
                intensity = intensity,
                startDateMillis = System.currentTimeMillis(),
                commitmentLockedUntil = if (enable7DayLock) System.currentTimeMillis() + lockDuration else 0L,
                streakDays = 1,
                lastCheckInDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                totalMinutesSaved = 0,
                isActive = true
            )

            val planId = database.detoxPlanDao().insertPlan(plan)

            // Auto-generate schedule and create active restrictions for target apps
            val schedule = DetoxPlanGenerator.generateSchedule(initialMinutes, targetMinutes, intensity)
            val initialDayLimit = schedule.firstOrNull()?.allowedDailyMinutes ?: initialMinutes

            val pm = app.packageManager
            for (pkg in targetPackages) {
                val appLabel = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    pkg
                }

                val restriction = AppRestriction(
                    packageName = pkg,
                    appName = appLabel,
                    mode = RestrictionMode.DAILY_LIMIT_ONLY,
                    dailyLimitMinutes = initialDayLimit,
                    commitmentLockedUntil = plan.commitmentLockedUntil,
                    minLimitCommitted = initialDayLimit,
                    isAppLockProtected = false
                )
                database.restrictionDao().insertRestriction(restriction)
            }

            _uiEvent.emit("Detox Plan '$title' activated!")
        }
    }

    fun checkInPlan(plan: DetoxPlan) {
        viewModelScope.launch(Dispatchers.IO) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (plan.lastCheckInDate == todayStr) {
                _uiEvent.emit("Already checked in for today!")
                return@launch
            }

            // Estimate minutes saved today
            val schedule = DetoxPlanGenerator.generateSchedule(
                plan.initialMinutes,
                plan.targetGoalMinutes,
                plan.intensity
            )
            val currentAllowed = DetoxPlanGenerator.getCurrentAllowedMinutes(plan, schedule)
            val minutesSavedToday = maxOf(0, plan.initialMinutes - currentAllowed)

            val updated = plan.copy(
                streakDays = plan.streakDays + 1,
                lastCheckInDate = todayStr,
                totalMinutesSaved = plan.totalMinutesSaved + minutesSavedToday
            )
            database.detoxPlanDao().updatePlan(updated)
            _uiEvent.emit("Day checked in! Streak: ${updated.streakDays} days! +${minutesSavedToday}m saved.")
        }
    }

    fun setupPin(pin: String, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            securityManager.setupNewPin(pin)
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun verifyPin(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val match = securityManager.verifyPin(pin)
            withContext(Dispatchers.Main) {
                onResult(match)
            }
        }
    }

    fun setBiometrics(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setBiometricsEnabled(enabled)
        }
    }

    fun setLockFocusGuard(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setLockFocusGuard(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.setThemeMode(mode)
        }
    }

    private fun checkClockTampering() {
        viewModelScope.launch(Dispatchers.IO) {
            val (anchorWall, anchorElapsed) = prefs.getClockAnchors()

            val currentWall = System.currentTimeMillis()
            val currentElapsed = SystemClock.elapsedRealtime()

            val result = CommitmentLockValidator.detectClockTampering(
                lastAnchorWallTime = anchorWall,
                lastAnchorElapsedRealtime = anchorElapsed,
                currentWallTime = currentWall,
                currentElapsedRealtime = currentElapsed
            )
            if (result.isTampered) {
                _clockTamperAlert.value = result
            }

            // Update anchor
            prefs.saveClockAnchor(currentWall, currentElapsed)
        }
    }

    fun clearBlockEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            database.blockEventDao().clearAllBlockEvents()
            _uiEvent.emit("Block event log cleared")
        }
    }
}
