package com.example.data.model

data class BadgeInfo(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val isUnlocked: Boolean,
    val unlockDate: String = ""
) {
    companion object {
        fun getDefaultBadges(currentStreak: Int, totalMinutesSaved: Int): List<BadgeInfo> {
            return listOf(
                BadgeInfo(
                    id = "first_step",
                    title = "First Step",
                    description = "Created your first restriction or detox plan.",
                    iconName = "flag",
                    isUnlocked = true
                ),
                BadgeInfo(
                    id = "streak_3",
                    title = "3-Day Warrior",
                    description = "Maintained a 3-day digital detox streak.",
                    iconName = "local_fire_department",
                    isUnlocked = currentStreak >= 3
                ),
                BadgeInfo(
                    id = "streak_7",
                    title = "7-Day Champion",
                    description = "Completed the full 7-day commitment lock.",
                    iconName = "shield",
                    isUnlocked = currentStreak >= 7
                ),
                BadgeInfo(
                    id = "saved_5h",
                    title = "Time Reclaimed",
                    description = "Saved more than 5 hours of screen time.",
                    iconName = "hourglass_empty",
                    isUnlocked = totalMinutesSaved >= 300
                ),
                BadgeInfo(
                    id = "zen_master",
                    title = "Zen Master",
                    description = "Completed 10 mindful pause breathing sessions.",
                    iconName = "self_improvement",
                    isUnlocked = currentStreak >= 10 || totalMinutesSaved >= 600
                )
            )
        }
    }
}
