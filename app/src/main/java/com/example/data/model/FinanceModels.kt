package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val monthlyBudgetLimit: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "Outros",
    val dateMillis: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Dinheiro",
    val notes: String = "",
    val isFixed: Boolean = false,
    val salaryCycleId: String = "",
    val reservedCategory: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Income(
    @DocumentId val id: String = "",
    val userId: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val source: String = "Salário",
    val dateMillis: Long = System.currentTimeMillis(),
    val incomeType: String = "Fixo",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SalaryConfig(
    @DocumentId val id: String = "default",
    val userId: String = "",
    val monthlySalary: Double = 0.0,
    val paydayOfMonth: Int = 25,
    val frequency: String = FREQUENCY_MONTHLY,
    val lastPromptedPeriodKey: String = "",
    val snoozedUntilMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val FREQUENCY_MONTHLY = "Mensal"
        const val FREQUENCY_BIWEEKLY = "Quinzenal"
        const val FREQUENCY_WEEKLY = "Semanal"
        const val DEFAULT_DOC_ID = "default"
    }
}

@IgnoreExtraProperties
data class SalaryAllocation(
    val category: String = "",
    val allocatedAmount: Double = 0.0
)

@IgnoreExtraProperties
data class SalaryCycle(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cycleKey: String = "",
    val description: String = "",
    val receivedAmount: Double = 0.0,
    val receivedDateMillis: Long = System.currentTimeMillis(),
    val nextPaydayMillis: Long = System.currentTimeMillis(),
    val linkedIncomeId: String = "",
    val allocations: List<SalaryAllocation> = emptyList(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val totalAllocated: Double
        get() = allocations.sumOf { it.allocatedAmount.coerceAtLeast(0.0) }

    val unallocatedAmount: Double
        get() = (receivedAmount - totalAllocated).coerceAtLeast(0.0)
}

@IgnoreExtraProperties
data class CategoryBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "",
    val monthlyLimit: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavingsGoal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetAmount: Double = 0.0,
    val savedAmount: Double = 0.0,
    val deadlineMillis: Long = System.currentTimeMillis(),
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressRatio: Float
        get() = if (targetAmount > 0.0) (savedAmount / targetAmount).toFloat().coerceIn(0f, 1f) else 0f
}

@IgnoreExtraProperties
data class DebtItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val counterparty: String = "",
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val direction: String = DIRECTION_OWE,
    val debtType: String = "Empréstimo",
    val dueDateMillis: Long = System.currentTimeMillis(),
    val isSettled: Boolean = false,
    val isRecurring: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val remainingAmount: Double
        get() = (amount - paidAmount).coerceAtLeast(0.0)

    companion object {
        const val DIRECTION_OWE = "OWE"
        const val DIRECTION_OWED = "OWED"
    }
}

@IgnoreExtraProperties
data class DailyTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val notes: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val priority: String = PRIORITY_MEDIUM,
    val itemType: String = TYPE_TASK,
    val timeLabel: String = "",
    val isCompleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val PRIORITY_HIGH = "ALTA"
        const val PRIORITY_MEDIUM = "MÉDIA"
        const val PRIORITY_LOW = "BAIXA"

        const val TYPE_TASK = "TAREFA"
        const val TYPE_APPOINTMENT = "COMPROMISSO"
        const val TYPE_REMINDER = "LEMBRETE"
    }
}

@IgnoreExtraProperties
data class ParsedExpenseDraft(
    val description: String = "",
    val amount: Double = 0.0,
    val category: String = "Outros",
    val paymentMethod: String = "Dinheiro",
    val isFixed: Boolean = false
)

@IgnoreExtraProperties
data class AiChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val isUser: Boolean = false,
    val text: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val pendingExpenses: List<ParsedExpenseDraft> = emptyList(),
    val expensesConfirmed: Boolean = false,
    @get:Exclude val isStreaming: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ReportPeriod(val label: String) {
    DAY("Dia"),
    WEEK("Semana"),
    MONTH("Mês"),
    YEAR("Ano")
}

object FinanceCatalog {
    val expenseCategories = listOf(
        "Alimentação",
        "Transporte",
        "Internet e comunicação",
        "Saúde",
        "Educação",
        "Igreja e contribuições",
        "Família",
        "Lazer",
        "Compras pessoais",
        "Negócios e trabalho",
        "Outros"
    )

    val salaryDistributionCategories = listOf(
        "Despesas essenciais",
        "Alimentação",
        "Transporte",
        "Internet",
        "Família",
        "Igreja",
        "Poupança",
        "Investimentos",
        "Lazer",
        "Reserva de emergência"
    )

    val salaryFrequencies = listOf(
        SalaryConfig.FREQUENCY_MONTHLY,
        SalaryConfig.FREQUENCY_BIWEEKLY,
        SalaryConfig.FREQUENCY_WEEKLY
    )

    val paymentMethods = listOf(
        "Multicaixa",
        "Express",
        "Dinheiro",
        "Transferência",
        "Cartão",
        "Outro"
    )

    val incomeSources = listOf(
        "Salário",
        "Freelance",
        "Design gráfico",
        "Desenvolvimento de websites",
        "Serviços digitais",
        "Negócios",
        "Outros rendimentos"
    )

    val incomeTypes = listOf(
        "Fixo",
        "Variável",
        "Pontual"
    )

    val debtTypes = listOf(
        "Empréstimo",
        "Prestação",
        "Recorrente",
        "Pessoal"
    )

    val taskPriorities = listOf(
        DailyTask.PRIORITY_HIGH,
        DailyTask.PRIORITY_MEDIUM,
        DailyTask.PRIORITY_LOW
    )

    val taskTypes = listOf(
        DailyTask.TYPE_TASK,
        DailyTask.TYPE_APPOINTMENT,
        DailyTask.TYPE_REMINDER
    )

    fun categoryColor(category: String): Color = when (category) {
        "Alimentação" -> Color(0xFF10B981)
        "Transporte" -> Color(0xFF3B82F6)
        "Internet e comunicação", "Internet" -> Color(0xFF6366F1)
        "Saúde", "Despesas essenciais" -> Color(0xFFEF4444)
        "Educação", "Investimentos" -> Color(0xFF8B5CF6)
        "Igreja e contribuições", "Igreja" -> Color(0xFFF59E0B)
        "Família" -> Color(0xFFEC4899)
        "Lazer" -> Color(0xFF14B8A6)
        "Compras pessoais" -> Color(0xFFF97316)
        "Negócios e trabalho" -> Color(0xFF0EA5E9)
        "Poupança" -> Color(0xFF059669)
        "Reserva de emergência" -> Color(0xFF0284C7)
        else -> Color(0xFF64748B)
    }
}
