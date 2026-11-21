package com.example.util

import com.example.data.model.DebtItem
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.SalaryAllocation
import com.example.data.model.SalaryConfig
import com.example.data.model.SalaryCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SalaryTrackingCalculatorTest {

    private fun millis(year: Int, monthZeroBased: Int, day: Int, hour: Int = 10): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `monthly schedule clamps day 31 in february`() {
        val feb15 = millis(2026, Calendar.FEBRUARY, 15)
        val schedule = DateUtilsPt.computeSalarySchedule(
            paydayOfMonth = 31,
            frequency = SalaryConfig.FREQUENCY_MONTHLY,
            nowMillis = feb15
        )
        assertEquals("2026-02", schedule.currentCycleKey)
        val cal = Calendar.getInstance().apply { timeInMillis = schedule.currentPaydayMillis }
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `biweekly and weekly schedules produce valid keys and upcoming dates`() {
        val midMonth = millis(2026, Calendar.MARCH, 18)
        val biweekly = DateUtilsPt.computeSalarySchedule(5, SalaryConfig.FREQUENCY_BIWEEKLY, midMonth)
        assertEquals("2026-03-Q1", biweekly.currentCycleKey)
        assertTrue(biweekly.nextPaydayMillis > midMonth)

        val weekly = DateUtilsPt.computeSalarySchedule(1, SalaryConfig.FREQUENCY_WEEKLY, midMonth)
        assertTrue(weekly.currentCycleKey.contains("-W"))
        assertTrue(weekly.nextPaydayMillis >= DateUtilsPt.startOfDayMillis(midMonth))
    }

    @Test
    fun `payday prompt due only when payday reached and neither confirmed nor snoozed`() {
        val config = SalaryConfig(
            monthlySalary = 350_000.0,
            paydayOfMonth = 25,
            frequency = SalaryConfig.FREQUENCY_MONTHLY
        )
        val march24 = millis(2026, Calendar.MARCH, 24)
        val beforePayday = SalaryTrackingCalculator.compute(
            config = config,
            cycles = emptyList(),
            expenses = emptyList(),
            incomes = emptyList(),
            debts = emptyList(),
            nowMillis = march24
        )
        assertFalse(beforePayday.isPaydayPromptDue)

        val march25 = millis(2026, Calendar.MARCH, 25)
        val onPayday = SalaryTrackingCalculator.compute(
            config = config,
            cycles = emptyList(),
            expenses = emptyList(),
            incomes = emptyList(),
            debts = emptyList(),
            nowMillis = march25
        )
        assertTrue(onPayday.isPaydayPromptDue)

        val snoozed = SalaryTrackingCalculator.compute(
            config = config.copy(
                lastPromptedPeriodKey = "2026-03",
                snoozedUntilMillis = march25 + 86_400_000L
            ),
            cycles = emptyList(),
            expenses = emptyList(),
            incomes = emptyList(),
            debts = emptyList(),
            nowMillis = march25 + 3_600_000L
        )
        assertFalse(snoozed.isPaydayPromptDue)

        val confirmedCycle = SalaryCycle(
            id = "cyc1",
            cycleKey = "2026-03",
            description = "Salário Março",
            receivedAmount = 350_000.0,
            receivedDateMillis = march25,
            nextPaydayMillis = millis(2026, Calendar.APRIL, 25)
        )
        val afterConfirm = SalaryTrackingCalculator.compute(
            config = config,
            cycles = listOf(confirmedCycle),
            expenses = emptyList(),
            incomes = emptyList(),
            debts = emptyList(),
            nowMillis = march25 + 7_200_000L
        )
        assertFalse(afterConfirm.isPaydayPromptDue)
    }

    @Test
    fun `reserve-paid expense updates reserve without double counting available money`() {
        val payDay = millis(2026, Calendar.MARCH, 25)
        val nextPayDay = millis(2026, Calendar.APRIL, 25)
        val now = millis(2026, Calendar.MARCH, 30)

        val cycle = SalaryCycle(
            id = "cyc_march",
            cycleKey = "2026-03",
            description = "Salário Março",
            receivedAmount = 200_000.0,
            receivedDateMillis = payDay,
            nextPaydayMillis = nextPayDay,
            linkedIncomeId = "inc_salary",
            allocations = listOf(
                SalaryAllocation("Alimentação", 60_000.0),
                SalaryAllocation("Poupança", 40_000.0)
            )
        )
        // Linked salary income + 20.000 Kz extra freelance income
        val incomes = listOf(
            Income(id = "inc_salary", description = "Salário", amount = 200_000.0, dateMillis = payDay),
            Income(id = "inc_extra", description = "Freelance", amount = 20_000.0, dateMillis = now)
        )
        // Expense 1: 15.000 Kz paid from Alimentação reserve (reduces Alimentação reserve from 60k to 45k)
        // Expense 2: 10.000 Kz paid from general available money
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Supermercado",
                amount = 15_000.0,
                category = "Alimentação",
                dateMillis = now,
                salaryCycleId = "cyc_march",
                reservedCategory = "Alimentação"
            ),
            Expense(
                id = "e2",
                description = "Táxi",
                amount = 10_000.0,
                category = "Transporte",
                dateMillis = now
            )
        )
        val debts = listOf(
            DebtItem(
                id = "d1",
                title = "Unitel Fibra",
                amount = 15_000.0,
                paidAmount = 0.0,
                direction = DebtItem.DIRECTION_OWE,
                isSettled = false
            )
        )

        val summary = SalaryTrackingCalculator.compute(
            config = SalaryConfig(monthlySalary = 200_000.0, paydayOfMonth = 25, frequency = SalaryConfig.FREQUENCY_MONTHLY),
            cycles = listOf(cycle),
            expenses = expenses,
            incomes = incomes,
            debts = debts,
            nowMillis = now
        )

        // Reserves: 60k + 40k = 100k initially; 15k spent from Alimentação -> 85k remaining reserved
        assertEquals(200_000.0, summary.salaryReceived, 0.01)
        assertEquals(20_000.0, summary.extraIncomesInCycle, 0.01)
        assertEquals(25_000.0, summary.totalSpentInCycle, 0.01)
        assertEquals(85_000.0, summary.totalReservedRemaining, 0.01)
        // Free available: 200k + 20k - 25k spent - 85k still reserved = 110k (NOT 95k!)
        assertEquals(110_000.0, summary.availableMoney, 0.01)
        assertEquals(15_000.0, summary.pendingObligations, 0.01)

        val foodAlloc = summary.allocationBreakdowns.first { it.category == "Alimentação" }
        assertEquals(15_000.0, foodAlloc.spentFromReserve, 0.01)
        assertEquals(45_000.0, foodAlloc.remainingReserve, 0.01)
    }

    @Test
    fun `overspending reserve clamps remaining reserve to zero and spills over to available money`() {
        val payDay = millis(2026, Calendar.MARCH, 25)
        val nextPayDay = millis(2026, Calendar.APRIL, 25)
        val now = millis(2026, Calendar.MARCH, 28)

        val cycle = SalaryCycle(
            id = "cyc1",
            cycleKey = "2026-03",
            receivedAmount = 100_000.0,
            receivedDateMillis = payDay,
            nextPaydayMillis = nextPayDay,
            allocations = listOf(SalaryAllocation("Lazer", 10_000.0))
        )
        val expenses = listOf(
            Expense(
                id = "e_over",
                description = "Jantar",
                amount = 18_000.0,
                category = "Lazer",
                dateMillis = now,
                salaryCycleId = "cyc1",
                reservedCategory = "Lazer"
            )
        )

        val summary = SalaryTrackingCalculator.compute(
            config = null,
            cycles = listOf(cycle),
            expenses = expenses,
            incomes = emptyList(),
            debts = emptyList(),
            nowMillis = now
        )

        assertEquals(0.0, summary.totalReservedRemaining, 0.01)
        // 100k - 18k spent - 0 remaining reserved = 82k
        assertEquals(82_000.0, summary.availableMoney, 0.01)
    }
}
