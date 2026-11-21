package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.SalaryConfig
import com.example.data.model.SalaryCycle

data class AllocationTrackingItem(
    val category: String,
    val allocatedAmount: Double,
    val spentFromReserve: Double,
    val remainingReserve: Double
)

data class SalaryTrackingSummary(
    val config: SalaryConfig?,
    val activeCycle: SalaryCycle?,
    val cycleHistory: List<SalaryCycle>,
    val scheduleWindow: DateUtilsPt.SalaryScheduleWindow?,
    val isPaydayPromptDue: Boolean,
    val daysUntilNextPayday: Int,
    val salaryReceived: Double,
    val extraIncomesInCycle: Double,
    val totalSpentInCycle: Double,
    val totalReservedInitial: Double,
    val totalReservedRemaining: Double,
    val availableMoney: Double,
    val pendingObligations: Double,
    val safeDailySpend: Double,
    val allocationBreakdowns: List<AllocationTrackingItem>
) {
    val hasActiveCycle: Boolean get() = activeCycle != null
}

object SalaryTrackingCalculator {

    fun compute(
        config: SalaryConfig?,
        cycles: List<SalaryCycle>,
        expenses: List<Expense>,
        incomes: List<Income>,
        debts: List<DebtItem>,
        nowMillis: Long = System.currentTimeMillis()
    ): SalaryTrackingSummary {
        val sortedCycles = cycles.sortedByDescending { it.receivedDateMillis }
        val activeCycle = sortedCycles.firstOrNull()

        val schedule = if (config != null && config.monthlySalary > 0.0) {
            DateUtilsPt.computeSalarySchedule(config.paydayOfMonth, config.frequency, nowMillis)
        } else null

        val alreadyConfirmedThisPeriod = schedule != null && sortedCycles.any { it.cycleKey == schedule.currentCycleKey }
        val isSnoozed = schedule != null && config != null &&
            config.lastPromptedPeriodKey == schedule.currentCycleKey &&
            nowMillis < config.snoozedUntilMillis

        val isPaydayPromptDue = schedule != null &&
            nowMillis >= schedule.currentPaydayMillis &&
            !alreadyConfirmedThisPeriod &&
            !isSnoozed

        val daysUntilNextPayday = when {
            schedule != null && activeCycle != null && activeCycle.cycleKey == schedule.currentCycleKey ->
                DateUtilsPt.daysUntil(schedule.nextPaydayMillis, nowMillis).coerceAtLeast(0)
            schedule != null && nowMillis < schedule.currentPaydayMillis ->
                DateUtilsPt.daysUntil(schedule.currentPaydayMillis, nowMillis).coerceAtLeast(0)
            schedule != null ->
                DateUtilsPt.daysUntil(schedule.nextPaydayMillis, nowMillis).coerceAtLeast(0)
            activeCycle != null ->
                DateUtilsPt.daysUntil(activeCycle.nextPaydayMillis, nowMillis).coerceAtLeast(0)
            else -> 0
        }

        val pendingObligations = debts
            .filter { !it.isSettled && it.direction == DebtItem.DIRECTION_OWE }
            .sumOf { it.remainingAmount }

        if (activeCycle == null) {
            return SalaryTrackingSummary(
                config = config,
                activeCycle = null,
                cycleHistory = sortedCycles,
                scheduleWindow = schedule,
                isPaydayPromptDue = isPaydayPromptDue,
                daysUntilNextPayday = daysUntilNextPayday,
                salaryReceived = 0.0,
                extraIncomesInCycle = 0.0,
                totalSpentInCycle = 0.0,
                totalReservedInitial = 0.0,
                totalReservedRemaining = 0.0,
                availableMoney = 0.0,
                pendingObligations = pendingObligations,
                safeDailySpend = 0.0,
                allocationBreakdowns = emptyList()
            )
        }

        val cycleStart = DateUtilsPt.startOfDayMillis(activeCycle.receivedDateMillis)
        val expensesInCycle = expenses.filter {
            it.salaryCycleId == activeCycle.id || it.dateMillis >= cycleStart
        }
        val extraIncomesInCycle = incomes
            .filter { it.dateMillis >= cycleStart && it.id != activeCycle.linkedIncomeId }
            .sumOf { it.amount }

        val totalSpentInCycle = expensesInCycle.sumOf { it.amount }

        val breakdowns = activeCycle.allocations.map { alloc ->
            val spentFromReserve = expenses
                .filter { it.salaryCycleId == activeCycle.id && it.reservedCategory == alloc.category }
                .sumOf { it.amount }
            val allocated = alloc.allocatedAmount.coerceAtLeast(0.0)
            val remaining = (allocated - spentFromReserve).coerceAtLeast(0.0)
            AllocationTrackingItem(
                category = alloc.category,
                allocatedAmount = allocated,
                spentFromReserve = spentFromReserve,
                remainingReserve = remaining
            )
        }

        val totalReservedInitial = breakdowns.sumOf { it.allocatedAmount }
        val totalReservedRemaining = breakdowns.sumOf { it.remainingReserve }

        // Available money distinguishes received, spent, and remaining reserves without double-counting
        // expenses that were paid directly out of a salary reserve.
        val availableMoney = activeCycle.receivedAmount + extraIncomesInCycle - totalSpentInCycle - totalReservedRemaining
        val netAfterPending = (availableMoney - pendingObligations).coerceAtLeast(0.0)
        val divisor = daysUntilNextPayday.coerceAtLeast(1)
        val safeDailySpend = netAfterPending / divisor

        return SalaryTrackingSummary(
            config = config,
            activeCycle = activeCycle,
            cycleHistory = sortedCycles,
            scheduleWindow = schedule,
            isPaydayPromptDue = isPaydayPromptDue,
            daysUntilNextPayday = daysUntilNextPayday,
            salaryReceived = activeCycle.receivedAmount,
            extraIncomesInCycle = extraIncomesInCycle,
            totalSpentInCycle = totalSpentInCycle,
            totalReservedInitial = totalReservedInitial,
            totalReservedRemaining = totalReservedRemaining,
            availableMoney = availableMoney,
            pendingObligations = pendingObligations,
            safeDailySpend = safeDailySpend,
            allocationBreakdowns = breakdowns
        )
    }
}
