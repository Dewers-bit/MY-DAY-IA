package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AiChatMessage
import com.example.data.model.DailyTask
import com.example.data.model.DebtItem
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.ParsedExpenseDraft
import com.example.data.model.SalaryAllocation
import com.example.data.model.SalaryConfig
import com.example.data.model.SavingsGoal
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FinanceRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun owner_crud_operations_succeed_across_collections() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FinanceRepository(firestore, auth)

        val profileRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Ana Luanda", ALICE_EMAIL)
        }
        assertTrue(profileRes.isSuccess)

        val expId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addExpense(
                Expense(
                    description = "Táxi Mutamba",
                    amount = 2500.0,
                    category = "Transporte",
                    paymentMethod = "Dinheiro",
                    isFixed = false
                )
            ).getOrThrow()
        }
        assertTrue(expId.isNotBlank())

        val observedExpenses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExpenses(aliceUid).first { list -> list.any { it.id == expId } }
        }
        assertEquals(2500.0, observedExpenses.first { it.id == expId }.amount, 0.01)

        val incId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addIncome(
                Income(
                    description = "Salário",
                    amount = 250000.0,
                    source = "Salário",
                    incomeType = "Fixo"
                )
            ).getOrThrow()
        }
        assertTrue(incId.isNotBlank())

        val goalId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addSavingsGoal(
                SavingsGoal(
                    name = "Portátil",
                    targetAmount = 450000.0,
                    savedAmount = 50000.0,
                    deadlineMillis = System.currentTimeMillis() + 86_400_000L * 60
                )
            ).getOrThrow()
        }
        assertTrue(goalId.isNotBlank())

        val debtId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addDebt(
                DebtItem(
                    title = "Internet Fibra",
                    counterparty = "Unitel",
                    amount = 15000.0,
                    paidAmount = 0.0,
                    direction = DebtItem.DIRECTION_OWE,
                    debtType = "Recorrente",
                    dueDateMillis = System.currentTimeMillis() + 86_400_000L * 5,
                    isRecurring = true
                )
            ).getOrThrow()
        }
        assertTrue(debtId.isNotBlank())

        val taskId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addDailyTask(
                DailyTask(
                    title = "Rever orçamento semanal",
                    priority = DailyTask.PRIORITY_HIGH,
                    itemType = DailyTask.TYPE_TASK,
                    timeLabel = "09:00"
                )
            ).getOrThrow()
        }
        assertTrue(taskId.isNotBlank())

        val deleteRes = withTimeout(DEFAULT_TIMEOUT_MS) { repo.deleteExpense(expId) }
        assertTrue(deleteRes.isSuccess)
    }

    @Test
    fun salary_config_cycle_confirm_dedup_allocations_and_reserve_expense_succeed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FinanceRepository(firestore, auth)
        val uniqueCycleKey = "test-${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertSalaryConfig(
                SalaryConfig(
                    monthlySalary = 350_000.0,
                    paydayOfMonth = 25,
                    frequency = SalaryConfig.FREQUENCY_MONTHLY
                )
            ).getOrThrow()
        }

        val observedCfg = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSalaryConfig(aliceUid).first { it != null }
        }
        assertEquals(350_000.0, observedCfg?.monthlySalary ?: 0.0, 0.01)

        val now = System.currentTimeMillis()
        val firstCycleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.confirmSalaryReceipt(
                cycleKey = uniqueCycleKey,
                description = "Salário Teste",
                receivedAmount = 350_000.0,
                receivedDateMillis = now,
                nextPaydayMillis = now + 86_400_000L * 30,
                notes = "Teste ciclo"
            ).getOrThrow()
        }

        // Duplicate confirm with identical cycleKey must return same cycleId without duplicating income
        val duplicateCycleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.confirmSalaryReceipt(
                cycleKey = uniqueCycleKey,
                description = "Salário Teste Duplicado",
                receivedAmount = 350_000.0,
                receivedDateMillis = now,
                nextPaydayMillis = now + 86_400_000L * 30
            ).getOrThrow()
        }
        assertEquals(firstCycleId, duplicateCycleId)

        // Exceeding received amount must fail cleanly
        val overAllocRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateSalaryCycleAllocations(
                cycleId = firstCycleId,
                receivedAmount = 350_000.0,
                allocations = listOf(SalaryAllocation("Poupança", 400_000.0))
            )
        }
        assertTrue(overAllocRes.isFailure)

        // Valid allocation succeeds
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateSalaryCycleAllocations(
                cycleId = firstCycleId,
                receivedAmount = 350_000.0,
                allocations = listOf(
                    SalaryAllocation("Alimentação", 80_000.0),
                    SalaryAllocation("Poupança", 100_000.0)
                )
            ).getOrThrow()
        }

        // Expense linked to reserve
        val reserveExpenseId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addExpense(
                Expense(
                    description = "Compras Kero",
                    amount = 25_000.0,
                    category = "Alimentação",
                    paymentMethod = "Multicaixa",
                    salaryCycleId = firstCycleId,
                    reservedCategory = "Alimentação"
                )
            ).getOrThrow()
        }
        assertTrue(reserveExpenseId.isNotBlank())

        val cycles = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSalaryCycles(aliceUid).first { list ->
                list.any { it.id == firstCycleId && it.allocations.size == 2 }
            }
        }
        val storedCycle = cycles.first { it.id == firstCycleId }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateSalaryCycleDetails(
                storedCycle.copy(description = "Salário Corrigido", receivedAmount = 360_000.0)
            ).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteExpense(reserveExpenseId).getOrThrow()
            repo.deleteSalaryCycle(storedCycle, deleteLinkedIncome = true).getOrThrow()
        }
    }

    @Test
    fun ai_chat_history_crud_and_cross_user_isolation_succeed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FinanceRepository(firestore, auth)

        val userMsgId = UUID.randomUUID().toString()
        val assistantMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveAiMessage(
                AiChatMessage(
                    id = userMsgId,
                    userId = aliceUid,
                    isUser = true,
                    text = "Hoje gastei 2.500 Kz em transporte e 3.000 Kz em alimentação",
                    timestampMillis = now
                )
            ).getOrThrow()

            aliceRepo.saveAiMessage(
                AiChatMessage(
                    id = assistantMsgId,
                    userId = aliceUid,
                    isUser = false,
                    text = "Identifiquei 2 despesas para confirmar:",
                    timestampMillis = now + 1L,
                    pendingExpenses = listOf(
                        ParsedExpenseDraft("Transporte", 2500.0, "Transporte"),
                        ParsedExpenseDraft("Alimentação", 3000.0, "Alimentação")
                    )
                )
            ).getOrThrow()
        }

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeAiMessages(aliceUid).first { list ->
                list.any { it.id == userMsgId } && list.any { it.id == assistantMsgId }
            }
        }
        assertEquals(2, observed.first { it.id == assistantMsgId }.pendingExpenses.size)

        // Cross-user isolation: Bob cannot read Alice's chat messages
        signInTestUser(BOB_EMAIL)
        val bobRepo = FinanceRepository(firestore, auth)
        var denied: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeAiMessages(aliceUid).first()
            }
        } catch (t: Throwable) {
            denied = t
        }
        val firestoreEx = denied?.unwrapFirestoreException()
        assertNotNull("Expected PERMISSION_DENIED for Bob reading Alice's aiMessages", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)

        // Sign back in as Alice and clear history
        signInTestUser(ALICE_EMAIL)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.clearAiMessages().getOrThrow()
        }
        val cleared = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeAiMessages(aliceUid).first { it.isEmpty() }
        }
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FinanceRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addExpense(
                Expense(
                    description = "Almoço",
                    amount = 3500.0,
                    category = "Alimentação",
                    paymentMethod = "Dinheiro"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FinanceRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeExpenses(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FinanceRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeExpenses("alice_uid_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val firestoreEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_myday@test.com"
        const val BOB_EMAIL = "bob_myday@test.com"
        const val DEFAULT_TIMEOUT_MS = 10_000L
        const val FLOW_TIMEOUT_MS = 10_000L
    }
}
