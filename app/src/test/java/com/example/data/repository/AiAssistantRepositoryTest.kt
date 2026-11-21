package com.example.data.repository

import com.example.data.model.AiChatMessage
import com.example.data.model.DailyTask
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.SalaryAllocation
import com.example.data.model.SalaryConfig
import com.example.data.model.SalaryCycle
import com.example.util.SalaryTrackingCalculator
import com.google.firebase.ai.type.TextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantRepositoryTest {

    private val repo = AiAssistantRepository()

    @Test
    fun shouldRunExpenseParser_skipsConversationalGreetingsAndSalaryQuestions() {
        assertFalse(repo.shouldRunExpenseParser("Olá, quem és tu?"))
        assertFalse(repo.shouldRunExpenseParser("Quanto ainda tenho do meu salário?"))
        assertFalse(repo.shouldRunExpenseParser("Como posso poupar mais este mês?"))

        assertTrue(repo.shouldRunExpenseParser("Hoje gastei 2.500 Kz em transporte"))
        assertTrue(repo.shouldRunExpenseParser("Gastei 3000 em alimentação e 1500 em táxi"))
    }

    @Test
    fun buildConversationHistory_filtersNoiseAndEnforcesAlternatingUserModelTurns() {
        val messages = listOf(
            AiChatMessage(id = "welcome", isUser = false, text = "Olá!", timestampMillis = 1L),
            AiChatMessage(id = "orphan_model", isUser = false, text = "Orphan", timestampMillis = 2L),
            AiChatMessage(id = "u1", isUser = true, text = "Olá, quem és tu?", timestampMillis = 10L),
            AiChatMessage(id = "m1", isUser = false, text = "Sou o My Day iA.", timestampMillis = 11L),
            AiChatMessage(id = "u2", isUser = true, text = "Erro antes", timestampMillis = 12L),
            AiChatMessage(
                id = "err",
                isUser = false,
                text = "O tempo de resposta da IA expirou. Tente novamente.",
                timestampMillis = 13L
            ),
            AiChatMessage(id = "u3", isUser = true, text = "Quanto ainda tenho?", timestampMillis = 14L),
            AiChatMessage(id = "m2", isUser = false, text = "Tens 120.000 Kz livres.", timestampMillis = 15L),
            AiChatMessage(id = "u_pending", isUser = true, text = "Nova pergunta", timestampMillis = 16L),
            AiChatMessage(id = "streaming", isUser = false, text = "", timestampMillis = 17L, isStreaming = true)
        )

        val history = repo.buildConversationHistory(messages)

        // Strict alternation starting with user and ending with model: u1 -> m1 -> u3 -> m2
        assertEquals(4, history.size)
        assertEquals("user", history[0].role)
        assertEquals("model", history[1].role)
        assertEquals("user", history[2].role)
        assertEquals("model", history[3].role)

        assertEquals("Olá, quem és tu?", (history[0].parts.first() as TextPart).text)
        assertEquals("Sou o My Day iA.", (history[1].parts.first() as TextPart).text)
        assertEquals("Quanto ainda tenho?", (history[2].parts.first() as TextPart).text)
        assertEquals("Tens 120.000 Kz livres.", (history[3].parts.first() as TextPart).text)
    }

    @Test
    fun buildContextPrompt_includesRealFinancialAndSalaryDataInKwanza() {
        val now = System.currentTimeMillis()
        val cfg = SalaryConfig(monthlySalary = 300_000.0, paydayOfMonth = 25)
        val cycle = SalaryCycle(
            id = "cyc1",
            cycleKey = "2026-11",
            description = "Salário Novembro",
            receivedAmount = 300_000.0,
            receivedDateMillis = now - 86_400_000L * 2,
            nextPaydayMillis = now + 86_400_000L * 28,
            allocations = listOf(SalaryAllocation("Alimentação", 80_000.0))
        )
        val expenses = listOf(
            Expense(
                id = "e1",
                description = "Compras Supermercado",
                amount = 20_000.0,
                category = "Alimentação",
                dateMillis = now,
                paymentMethod = "Multicaixa",
                salaryCycleId = "cyc1",
                reservedCategory = "Alimentação"
            )
        )
        val incomes = listOf(
            Income(
                id = "i1",
                description = "Salário Novembro",
                amount = 300_000.0,
                source = "Salário",
                dateMillis = now - 86_400_000L * 2
            )
        )
        val tasks = listOf(
            DailyTask(id = "t1", title = "Pagar água", priority = DailyTask.PRIORITY_HIGH)
        )
        val summary = SalaryTrackingCalculator.compute(
            config = cfg,
            cycles = listOf(cycle),
            expenses = expenses,
            incomes = incomes,
            debts = emptyList(),
            nowMillis = now
        )
        val snapshot = FinancialContextSnapshot(
            userName = "Joana",
            monthlyBudgetLimit = 200_000.0,
            expenses = expenses,
            incomes = incomes,
            categoryBudgets = emptyList(),
            savingsGoals = emptyList(),
            debts = emptyList(),
            tasks = tasks,
            salarySummary = summary
        )

        val prompt = repo.buildContextPrompt(snapshot)

        assertTrue(prompt.contains("Joana"))
        assertTrue(prompt.contains("GESTÃO DO MEU SALÁRIO"))
        assertTrue(prompt.contains("Compras Supermercado"))
        assertTrue(prompt.contains("Pagar água"))
        assertTrue(prompt.contains("Kz"))
    }
}
