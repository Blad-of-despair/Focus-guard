package com.example.engine

import com.example.data.model.DetoxDayStep
import com.example.data.model.DetoxIntensity
import com.example.data.model.DetoxPlan

object DetoxPlanGenerator {

    data class StepConfig(
        val daysPerStep: Int,
        val reductionMinutes: Int
    )

    fun getStepConfig(intensity: String): StepConfig {
        return when (intensity) {
            DetoxIntensity.GENTLE -> StepConfig(daysPerStep = 3, reductionMinutes = 10)
            DetoxIntensity.STRICT -> StepConfig(daysPerStep = 1, reductionMinutes = 25)
            else -> StepConfig(daysPerStep = 2, reductionMinutes = 15) // MODERATE
        }
    }

    /**
     * Generates a step-by-step reduction schedule until targetGoalMinutes is reached.
     */
    fun generateSchedule(
        initialMinutes: Int,
        targetGoalMinutes: Int,
        intensity: String
    ): List<DetoxDayStep> {
        val config = getStepConfig(intensity)
        val steps = mutableListOf<DetoxDayStep>()

        var currentLimit = maxOf(initialMinutes, targetGoalMinutes)
        val goal = minOf(initialMinutes, targetGoalMinutes)
        var currentDay = 1

        while (currentLimit > goal) {
            val stepStartDay = currentDay
            val stepEndDay = currentDay + config.daysPerStep - 1
            val dayDesc = if (config.daysPerStep > 1) {
                "Days $stepStartDay-$stepEndDay: ${currentLimit}m per day"
            } else {
                "Day $stepStartDay: ${currentLimit}m per day"
            }

            for (d in stepStartDay..stepEndDay) {
                steps.add(
                    DetoxDayStep(
                        dayNumber = d,
                        allowedDailyMinutes = currentLimit,
                        description = dayDesc
                    )
                )
            }

            currentDay = stepEndDay + 1
            currentLimit = maxOf(goal, currentLimit - config.reductionMinutes)
        }

        // Add maintenance step at target goal for final week
        val maintenanceEnd = currentDay + 6
        val maintenanceDesc = "Days $currentDay-$maintenanceEnd: ${goal}m (Target Maintained)"
        for (d in currentDay..maintenanceEnd) {
            steps.add(
                DetoxDayStep(
                    dayNumber = d,
                    allowedDailyMinutes = goal,
                    description = maintenanceDesc
                )
            )
        }

        return steps
    }

    /**
     * Calculates the allowed daily minutes for today based on plan start date.
     */
    fun getCurrentAllowedMinutes(
        plan: DetoxPlan,
        schedule: List<DetoxDayStep>,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Int {
        if (schedule.isEmpty()) return plan.targetGoalMinutes
        val elapsedDays = ((currentTimeMillis - plan.startDateMillis) / (1000 * 60 * 60 * 24)).toInt()
        val dayIndex = maxOf(0, elapsedDays)
        return if (dayIndex < schedule.size) {
            schedule[dayIndex].allowedDailyMinutes
        } else {
            plan.targetGoalMinutes
        }
    }
}
