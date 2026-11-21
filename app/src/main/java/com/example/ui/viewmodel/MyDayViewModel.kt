package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CategoryBudget
import com.example.data.model.DailyTask
import com.example.data.model.DebtItem
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.SalaryAllocation
import com.example.data.model.SalaryConfig
import com.example.data.model.SalaryCycle
import com.example.data.model.SavingsGoal
import com.example.data.model.UserProfile
import com.example.data.repository.FinanceRepository
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt
import com.example.util.SalaryTrackingCalculator
import com.example.util.SalaryTrackingSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppDataState {
    data object Loading : AppDataState
    data class Success(val data: MyDayDashboardData) : AppDataState
    data class Error(val message: String) : AppDataState
}

sealed interface AdminUsersCountState {
    data object Idle : AdminUsersCountState
    data object Loading : AdminUsersCountState
    data class Success(val totalUsers: Long) : AdminUsersCountState
    data class Error(val message: String) : AdminUsersCountState
}

enum class BudgetAlertLevel(val label: String, val threshold: Int) {
    WARNING_70("70% atingido", 70),
    HIGH_85("85% atingido", 85),
    EXCEEDED_100("Limite excedido", 100)
}

data class BudgetProgressItem(
    val budget: CategoryBudget?,
    val category: String,
    val spentThisMonth: Double,
    val limit: Double,
    val ratio: Float,
    val alertLevel: BudgetAlertLevel?
)

data class MyDayDashboardData(
    val userProfile: UserProfile?,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val categoryBudgets: List<CategoryBudget>,
    val savingsGoals: List<SavingsGoal>,
    val debts: List<DebtItem>,
    val tasks: List<DailyTask>,
    val totalAvailableBalance: Double,
    val monthIncomeTotal: Double,
    val monthExpenseTotal: Double,
    val monthNetSaved: Double,
    val prevMonthIncomeTotal: Double,
    val prevMonthExpenseTotal: Double,
    val globalBudgetLimit: Double,
    val globalBudgetUsedRatio: Float,
    val globalAlertLevel: BudgetAlertLevel?,
    val categorySpendThisMonth: Map<String, Double>,
    val budgetProgressItems: List<BudgetProgressItem>,
    val overdueDebts: List<DebtItem>,
    val upcomingDebts: List<DebtItem>,
    val todayTasks: List<DailyTask>,
    val salarySummary: SalaryTrackingSummary
)

class MyDayViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserEmail: String
) : ViewModel() {

    private val _snackbarEvents = MutableSharedFlow<String>()
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _recentlyConfirmedCycleId = MutableStateFlow<String?>(null)
    val recentlyConfirmedCycleId: StateFlow<String?> = _recentlyConfirmedCycleId.asStateFlow()

    val isAdmin: Boolean = FinanceRepository.isAdminEmail(currentUserEmail)

    private val _adminUsersCountState = MutableStateFlow<AdminUsersCountState>(AdminUsersCountState.Idle)
    val adminUsersCountState: StateFlow<AdminUsersCountState> = _adminUsersCountState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserName, currentUserEmail)
                .onFailure { Log.w(TAG, "Profile init warning: ${it.message}") }
        }
    }

    private fun <T> Flow<T>.unwrapFirestoreErrors(label: String): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.e(TAG, "Error observing $label", rootError)
        throw rootError
    }

    val dashboardState: StateFlow<AppDataState> = combine(
        repository.observeUserProfile(currentUserId).unwrapFirestoreErrors("profile"),
        repository.observeExpenses(currentUserId).unwrapFirestoreErrors("expenses"),
        repository.observeIncomes(currentUserId).unwrapFirestoreErrors("incomes"),
        repository.observeCategoryBudgets(currentUserId).unwrapFirestoreErrors("budgets"),
        repository.observeSavingsGoals(currentUserId).unwrapFirestoreErrors("savings"),
        repository.observeDebts(currentUserId).unwrapFirestoreErrors("debts"),
        repository.observeDailyTasks(currentUserId).unwrapFirestoreErrors("tasks"),
        repository.observeSalaryConfig(currentUserId).unwrapFirestoreErrors("salarySettings"),
        repository.observeSalaryCycles(currentUserId).unwrapFirestoreErrors("salaryCycles")
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val profile = values[0] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val expenses = values[1] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val incomes = values[2] as List<Income>
        @Suppress("UNCHECKED_CAST")
        val budgets = values[3] as List<CategoryBudget>
        @Suppress("UNCHECKED_CAST")
        val savings = values[4] as List<SavingsGoal>
        @Suppress("UNCHECKED_CAST")
        val debts = values[5] as List<DebtItem>
        @Suppress("UNCHECKED_CAST")
        val tasks = values[6] as List<DailyTask>
        @Suppress("UNCHECKED_CAST")
        val salaryConfig = values[7] as SalaryConfig?
        @Suppress("UNCHECKED_CAST")
        val salaryCycles = values[8] as List<SalaryCycle>

        AppDataState.Success(
            computeDashboardData(profile, expenses, incomes, budgets, savings, debts, tasks, salaryConfig, salaryCycles)
        ) as AppDataState
    }.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        emit(AppDataState.Error(rootError.localizedMessage ?: "Falha ao sincronizar dados com o servidor."))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppDataState.Loading
    )

    private fun computeDashboardData(
        profile: UserProfile?,
        expenses: List<Expense>,
        incomes: List<Income>,
        budgets: List<CategoryBudget>,
        savings: List<SavingsGoal>,
        debts: List<DebtItem>,
        tasks: List<DailyTask>,
        salaryConfig: SalaryConfig?,
        salaryCycles: List<SalaryCycle>
    ): MyDayDashboardData {
        val now = System.currentTimeMillis()
        val monthStart = DateUtilsPt.startOfMonthMillis(now, 0)
        val monthEnd = DateUtilsPt.endOfMonthMillis(now, 0)
        val prevMonthStart = DateUtilsPt.startOfMonthMillis(now, -1)
        val prevMonthEnd = DateUtilsPt.endOfMonthMillis(now, -1)
        val todayStart = DateUtilsPt.startOfDayMillis(now)
        val todayEnd = DateUtilsPt.endOfDayMillis(now)
        val sevenDaysLater = todayEnd + 7L * 86_400_000L

        val totalIncomeAll = incomes.sumOf { it.amount }
        val totalExpenseAll = expenses.sumOf { it.amount }
        val totalAvailableBalance = totalIncomeAll - totalExpenseAll

        val monthIncomes = incomes.filter { it.dateMillis in monthStart..monthEnd }
        val monthExpenses = expenses.filter { it.dateMillis in monthStart..monthEnd }
        val prevIncomes = incomes.filter { it.dateMillis in prevMonthStart..prevMonthEnd }
        val prevExpenses = expenses.filter { it.dateMillis in prevMonthStart..prevMonthEnd }

        val monthIncomeTotal = monthIncomes.sumOf { it.amount }
        val monthExpenseTotal = monthExpenses.sumOf { it.amount }
        val monthNetSaved = monthIncomeTotal - monthExpenseTotal
        val prevMonthIncomeTotal = prevIncomes.sumOf { it.amount }
        val prevMonthExpenseTotal = prevExpenses.sumOf { it.amount }

        val globalLimit = profile?.monthlyBudgetLimit ?: 0.0
        val globalRatio = if (globalLimit > 0.0) (monthExpenseTotal / globalLimit).toFloat() else 0f
        val globalAlert = alertForRatio(globalRatio, globalLimit)

        val categorySpend = monthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val budgetByCat = budgets.associateBy { it.category }
        val allCategoriesWithData = (budgets.map { it.category } + categorySpend.keys).distinct()
        val progressItems = allCategoriesWithData.map { cat ->
            val b = budgetByCat[cat]
            val spent = categorySpend[cat] ?: 0.0
            val limit = b?.monthlyLimit ?: 0.0
            val ratio = if (limit > 0.0) (spent / limit).toFloat() else 0f
            BudgetProgressItem(
                budget = b,
                category = cat,
                spentThisMonth = spent,
                limit = limit,
                ratio = ratio,
                alertLevel = alertForRatio(ratio, limit)
            )
        }.sortedByDescending { it.ratio }

        val pendingDebts = debts.filter { !it.isSettled }
        val overdueDebts = pendingDebts.filter { it.dueDateMillis < todayStart }
        val upcomingDebts = pendingDebts.filter { it.dueDateMillis in todayStart..sevenDaysLater }

        val todayTasks = tasks.filter {
            DateUtilsPt.isSameDay(it.dateMillis, now) || (!it.isCompleted && it.dateMillis < todayStart)
        }

        val salarySummary = SalaryTrackingCalculator.compute(
            config = salaryConfig,
            cycles = salaryCycles,
            expenses = expenses,
            incomes = incomes,
            debts = debts,
            nowMillis = now
        )

        return MyDayDashboardData(
            userProfile = profile,
            expenses = expenses,
            incomes = incomes,
            categoryBudgets = budgets,
            savingsGoals = savings,
            debts = debts,
            tasks = tasks,
            totalAvailableBalance = totalAvailableBalance,
            monthIncomeTotal = monthIncomeTotal,
            monthExpenseTotal = monthExpenseTotal,
            monthNetSaved = monthNetSaved,
            prevMonthIncomeTotal = prevMonthIncomeTotal,
            prevMonthExpenseTotal = prevMonthExpenseTotal,
            globalBudgetLimit = globalLimit,
            globalBudgetUsedRatio = globalRatio,
            globalAlertLevel = globalAlert,
            categorySpendThisMonth = categorySpend,
            budgetProgressItems = progressItems,
            overdueDebts = overdueDebts,
            upcomingDebts = upcomingDebts,
            todayTasks = todayTasks,
            salarySummary = salarySummary
        )
    }

    private fun alertForRatio(ratio: Float, limit: Double): BudgetAlertLevel? {
        if (limit <= 0.0) return null
        return when {
            ratio >= 1.0f -> BudgetAlertLevel.EXCEEDED_100
            ratio >= 0.85f -> BudgetAlertLevel.HIGH_85
            ratio >= 0.70f -> BudgetAlertLevel.WARNING_70
            else -> null
        }
    }

    // --- EXPENSES ---
    fun addExpense(expense: Expense) = launchMutation("Despesa registada!") {
        repository.addExpense(expense).getOrThrow()
    }

    fun updateExpense(expense: Expense) = launchMutation("Despesa actualizada!") {
        repository.updateExpense(expense).getOrThrow()
    }

    fun deleteExpense(id: String) = launchMutation("Despesa eliminada.") {
        repository.deleteExpense(id).getOrThrow()
    }

    // --- INCOMES ---
    fun addIncome(income: Income) = launchMutation("Receita registada!") {
        repository.addIncome(income).getOrThrow()
    }

    fun updateIncome(income: Income) = launchMutation("Receita actualizada!") {
        repository.updateIncome(income).getOrThrow()
    }

    fun deleteIncome(id: String) = launchMutation("Receita eliminada.") {
        repository.deleteIncome(id).getOrThrow()
    }

    // --- BUDGETS ---
    fun updateMonthlyBudgetLimit(limit: Double) = launchMutation("Orçamento mensal actualizado!") {
        repository.updateMonthlyBudgetLimit(limit).getOrThrow()
    }

    fun upsertCategoryBudget(budget: CategoryBudget) = launchMutation("Limite de ${budget.category} guardado!") {
        repository.upsertCategoryBudget(budget).getOrThrow()
    }

    fun deleteCategoryBudget(budgetId: String) = launchMutation("Limite removido.") {
        repository.deleteCategoryBudget(budgetId).getOrThrow()
    }

    // --- SAVINGS ---
    fun addSavingsGoal(goal: SavingsGoal) = launchMutation("Objectivo de poupança criado!") {
        repository.addSavingsGoal(goal).getOrThrow()
    }

    fun updateSavingsGoal(goal: SavingsGoal) = launchMutation("Objectivo actualizado!") {
        repository.updateSavingsGoal(goal).getOrThrow()
    }

    fun addSavingsContribution(goal: SavingsGoal, extraAmount: Double) =
        launchMutation("Poupança reforçada em ${CurrencyUtils.formatKz(extraAmount)}!") {
            repository.updateSavingsGoal(
                goal.copy(savedAmount = (goal.savedAmount + extraAmount).coerceAtLeast(0.0))
            ).getOrThrow()
        }

    fun deleteSavingsGoal(id: String) = launchMutation("Objectivo eliminado.") {
        repository.deleteSavingsGoal(id).getOrThrow()
    }

    // --- DEBTS ---
    fun addDebt(debt: DebtItem) = launchMutation("Registo de dívida/pagamento criado!") {
        repository.addDebt(debt).getOrThrow()
    }

    fun updateDebt(debt: DebtItem) = launchMutation("Registo actualizado!") {
        repository.updateDebt(debt).getOrThrow()
    }

    fun recordDebtPayment(debt: DebtItem, paymentAmount: Double) =
        launchMutation("Pagamento registado!") {
            val newPaid = (debt.paidAmount + paymentAmount).coerceAtMost(debt.amount)
            repository.updateDebt(
                debt.copy(
                    paidAmount = newPaid,
                    isSettled = newPaid >= debt.amount
                )
            ).getOrThrow()
        }

    fun toggleDebtSettled(debt: DebtItem) = launchMutation("Estado actualizado!") {
        val settled = !debt.isSettled
        repository.updateDebt(
            debt.copy(
                isSettled = settled,
                paidAmount = if (settled) debt.amount else debt.paidAmount
            )
        ).getOrThrow()
    }

    fun deleteDebt(id: String) = launchMutation("Registo eliminado.") {
        repository.deleteDebt(id).getOrThrow()
    }

    // --- DAILY TASKS ---
    fun addDailyTask(task: DailyTask) = launchMutation("Adicionado ao My Day!") {
        repository.addDailyTask(task).getOrThrow()
    }

    fun updateDailyTask(task: DailyTask) = launchMutation("Tarefa actualizada!") {
        repository.updateDailyTask(task).getOrThrow()
    }

    fun toggleTaskCompleted(task: DailyTask) = launchMutation(
        if (!task.isCompleted) "Tarefa concluída!" else "Tarefa reaberta."
    ) {
        repository.updateDailyTask(task.copy(isCompleted = !task.isCompleted)).getOrThrow()
    }

    fun deleteDailyTask(id: String) = launchMutation("Item removido.") {
        repository.deleteDailyTask(id).getOrThrow()
    }

    // --- SALARY MANAGEMENT ---
    fun saveSalaryConfig(monthlySalary: Double, paydayOfMonth: Int, frequency: String) =
        launchMutation("Configuração de salário guardada!") {
            val current = (dashboardState.value as? AppDataState.Success)?.data?.salarySummary?.config
            repository.upsertSalaryConfig(
                SalaryConfig(
                    monthlySalary = monthlySalary,
                    paydayOfMonth = paydayOfMonth.coerceIn(1, 31),
                    frequency = frequency,
                    lastPromptedPeriodKey = current?.lastPromptedPeriodKey ?: "",
                    snoozedUntilMillis = current?.snoozedUntilMillis ?: 0L
                )
            ).getOrThrow()
        }

    fun snoozeSalaryPrompt(periodKey: String) = launchMutation("Lembrete adiado por 24 horas.") {
        val tomorrow = System.currentTimeMillis() + 86_400_000L
        repository.snoozeSalaryPrompt(periodKey, tomorrow).getOrThrow()
    }

    fun confirmSalaryReceipt(
        cycleKey: String,
        description: String,
        receivedAmount: Double,
        receivedDateMillis: Long,
        nextPaydayMillis: Long,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                val cycleId = repository.confirmSalaryReceipt(
                    cycleKey = cycleKey,
                    description = description,
                    receivedAmount = receivedAmount,
                    receivedDateMillis = receivedDateMillis,
                    nextPaydayMillis = nextPaydayMillis,
                    notes = notes
                ).getOrThrow()
                _recentlyConfirmedCycleId.value = cycleId
                _snackbarEvents.emit("Salário registado! Pode distribuir agora por categorias.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Salary confirmation failed", e)
                _snackbarEvents.emit(e.localizedMessage ?: "Erro ao registar recebimento salarial.")
            }
        }
    }

    fun consumeRecentlyConfirmedCycle() {
        _recentlyConfirmedCycleId.value = null
    }

    fun updateSalaryCycleAllocations(
        cycleId: String,
        receivedAmount: Double,
        allocations: List<SalaryAllocation>
    ) = launchMutation("Distribuição do salário guardada!") {
        repository.updateSalaryCycleAllocations(cycleId, receivedAmount, allocations).getOrThrow()
    }

    fun updateSalaryCycleDetails(cycle: SalaryCycle) = launchMutation("Ciclo salarial actualizado!") {
        repository.updateSalaryCycleDetails(cycle).getOrThrow()
    }

    fun deleteSalaryCycle(cycle: SalaryCycle) = launchMutation("Ciclo salarial eliminado.") {
        repository.deleteSalaryCycle(cycle, deleteLinkedIncome = true).getOrThrow()
    }

    fun refreshAdminUsersCount() {
        if (!isAdmin) return
        viewModelScope.launch {
            _adminUsersCountState.value = AdminUsersCountState.Loading
            repository.fetchTotalUsersCount()
                .onSuccess { count ->
                    _adminUsersCountState.value = AdminUsersCountState.Success(count)
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _adminUsersCountState.value = AdminUsersCountState.Error(
                        error.localizedMessage ?: "Não foi possível obter o total de utilizadores."
                    )
                }
        }
    }

    private fun launchMutation(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                _snackbarEvents.emit(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mutation failed", e)
                _snackbarEvents.emit(e.localizedMessage ?: "Operação falhou. Tente novamente.")
            }
        }
    }

    private companion object {
        const val TAG = "MyDayViewModel"
    }
}
