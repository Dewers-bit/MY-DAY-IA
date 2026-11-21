package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AiChatMessage
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
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Utilizador deve estar autenticado com Google.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> observeSubcollection(
        uid: String,
        collectionName: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val path = "users/$uid/$collectionName"
        emitAll(
            userDoc(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = userDoc(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, email: String): Result<Unit> {
        return runCatching {
            val uid = requireUserId()
            val docRef = userDoc(uid)
            val safeName = displayName.trim().ifBlank { "Utilizador" }.take(80)
            val safeEmail = email.trim().take(120)
            try {
                val snapshot = docRef.get().await()
                if (!snapshot.exists()) {
                    val payload = mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "email" to safeEmail,
                        "monthlyBudgetLimit" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(payload).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                throw e
            }
        }
    }

    suspend fun updateMonthlyBudgetLimit(limit: Double): Result<Unit> {
        return runCatching {
            val uid = requireUserId()
            require(limit >= 0.0) { "O orçamento mensal não pode ser negativo." }
            val docRef = userDoc(uid)
            try {
                val snapshot = docRef.get().await()
                if (!snapshot.exists()) {
                    val user = auth.currentUser
                    val payload = mapOf(
                        "userId" to uid,
                        "displayName" to (user?.displayName?.take(80)?.ifBlank { "Utilizador" } ?: "Utilizador"),
                        "email" to (user?.email?.take(120) ?: ""),
                        "monthlyBudgetLimit" to limit,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    docRef.set(payload).await()
                } else {
                    docRef.update(
                        mapOf(
                            "monthlyBudgetLimit" to limit,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
    }

    // --- EXPENSES ---
    fun observeExpenses(uid: String): Flow<List<Expense>> =
        observeSubcollection(uid, "expenses", Expense::class.java)
            .map { list -> list.sortedByDescending { it.dateMillis } }

    suspend fun addExpense(expense: Expense): Result<String> = runCatching {
        val uid = requireUserId()
        require(expense.description.isNotBlank()) { "Descrição obrigatória." }
        require(expense.amount > 0.0) { "O valor deve ser maior que zero." }
        val docRef = userDoc(uid).collection("expenses").document()
        val payload = mapOf(
            "userId" to uid,
            "description" to expense.description.trim().take(140),
            "amount" to expense.amount,
            "category" to expense.category.trim().take(60),
            "dateMillis" to expense.dateMillis,
            "paymentMethod" to expense.paymentMethod.trim().take(40),
            "notes" to expense.notes.trim().take(400),
            "isFixed" to expense.isFixed,
            "salaryCycleId" to expense.salaryCycleId.trim().take(128),
            "reservedCategory" to expense.reservedCategory.trim().take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateExpense(expense: Expense): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(expense.id.isNotBlank()) { "ID da despesa inválido." }
        require(expense.description.isNotBlank()) { "Descrição obrigatória." }
        require(expense.amount > 0.0) { "O valor deve ser maior que zero." }
        val docRef = userDoc(uid).collection("expenses").document(expense.id)
        val updates = mapOf(
            "description" to expense.description.trim().take(140),
            "amount" to expense.amount,
            "category" to expense.category.trim().take(60),
            "dateMillis" to expense.dateMillis,
            "paymentMethod" to expense.paymentMethod.trim().take(40),
            "notes" to expense.notes.trim().take(400),
            "isFixed" to expense.isFixed,
            "salaryCycleId" to expense.salaryCycleId.trim().take(128),
            "reservedCategory" to expense.reservedCategory.trim().take(60),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(expenseId.isNotBlank())
        val docRef = userDoc(uid).collection("expenses").document(expenseId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- INCOMES ---
    fun observeIncomes(uid: String): Flow<List<Income>> =
        observeSubcollection(uid, "incomes", Income::class.java)
            .map { list -> list.sortedByDescending { it.dateMillis } }

    suspend fun addIncome(income: Income): Result<String> = runCatching {
        val uid = requireUserId()
        require(income.description.isNotBlank()) { "Descrição obrigatória." }
        require(income.amount > 0.0) { "O valor deve ser maior que zero." }
        val docRef = userDoc(uid).collection("incomes").document()
        val payload = mapOf(
            "userId" to uid,
            "description" to income.description.trim().take(140),
            "amount" to income.amount,
            "source" to income.source.trim().take(60),
            "dateMillis" to income.dateMillis,
            "incomeType" to income.incomeType.trim().take(40),
            "notes" to income.notes.trim().take(400),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateIncome(income: Income): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(income.id.isNotBlank())
        require(income.description.isNotBlank())
        require(income.amount > 0.0)
        val docRef = userDoc(uid).collection("incomes").document(income.id)
        val updates = mapOf(
            "description" to income.description.trim().take(140),
            "amount" to income.amount,
            "source" to income.source.trim().take(60),
            "dateMillis" to income.dateMillis,
            "incomeType" to income.incomeType.trim().take(40),
            "notes" to income.notes.trim().take(400),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteIncome(incomeId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(incomeId.isNotBlank())
        val docRef = userDoc(uid).collection("incomes").document(incomeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- CATEGORY BUDGETS ---
    fun observeCategoryBudgets(uid: String): Flow<List<CategoryBudget>> =
        observeSubcollection(uid, "categoryBudgets", CategoryBudget::class.java)

    suspend fun upsertCategoryBudget(budget: CategoryBudget): Result<String> = runCatching {
        val uid = requireUserId()
        require(budget.category.isNotBlank())
        require(budget.monthlyLimit > 0.0) { "O limite deve ser maior que zero." }
        val collectionRef = userDoc(uid).collection("categoryBudgets")
        try {
            if (budget.id.isNotBlank()) {
                val docRef = collectionRef.document(budget.id)
                docRef.update(
                    mapOf(
                        "category" to budget.category.trim().take(60),
                        "monthlyLimit" to budget.monthlyLimit,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                docRef.id
            } else {
                val docRef = collectionRef.document()
                val payload = mapOf(
                    "userId" to uid,
                    "category" to budget.category.trim().take(60),
                    "monthlyLimit" to budget.monthlyLimit,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                docRef.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, collectionRef.path)
            throw e
        }
    }

    suspend fun deleteCategoryBudget(budgetId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("categoryBudgets").document(budgetId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SAVINGS GOALS ---
    fun observeSavingsGoals(uid: String): Flow<List<SavingsGoal>> =
        observeSubcollection(uid, "savingsGoals", SavingsGoal::class.java)
            .map { list -> list.sortedBy { it.deadlineMillis } }

    suspend fun addSavingsGoal(goal: SavingsGoal): Result<String> = runCatching {
        val uid = requireUserId()
        require(goal.name.isNotBlank()) { "Nome do objectivo obrigatório." }
        require(goal.targetAmount > 0.0) { "Valor pretendido deve ser positivo." }
        val docRef = userDoc(uid).collection("savingsGoals").document()
        val payload = mapOf(
            "userId" to uid,
            "name" to goal.name.trim().take(100),
            "targetAmount" to goal.targetAmount,
            "savedAmount" to goal.savedAmount.coerceAtLeast(0.0),
            "deadlineMillis" to goal.deadlineMillis,
            "notes" to goal.notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSavingsGoal(goal: SavingsGoal): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(goal.id.isNotBlank())
        val docRef = userDoc(uid).collection("savingsGoals").document(goal.id)
        val updates = mapOf(
            "name" to goal.name.trim().take(100),
            "targetAmount" to goal.targetAmount,
            "savedAmount" to goal.savedAmount.coerceAtLeast(0.0),
            "deadlineMillis" to goal.deadlineMillis,
            "notes" to goal.notes.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteSavingsGoal(goalId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("savingsGoals").document(goalId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- DEBTS ---
    fun observeDebts(uid: String): Flow<List<DebtItem>> =
        observeSubcollection(uid, "debts", DebtItem::class.java)
            .map { list -> list.sortedWith(compareBy<DebtItem> { it.isSettled }.thenBy { it.dueDateMillis }) }

    suspend fun addDebt(debt: DebtItem): Result<String> = runCatching {
        val uid = requireUserId()
        require(debt.title.isNotBlank())
        require(debt.counterparty.isNotBlank())
        require(debt.amount > 0.0)
        val clampedPaid = debt.paidAmount.coerceIn(0.0, debt.amount)
        val docRef = userDoc(uid).collection("debts").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to debt.title.trim().take(120),
            "counterparty" to debt.counterparty.trim().take(100),
            "amount" to debt.amount,
            "paidAmount" to clampedPaid,
            "direction" to debt.direction,
            "debtType" to debt.debtType.trim().take(40),
            "dueDateMillis" to debt.dueDateMillis,
            "isSettled" to (debt.isSettled || clampedPaid >= debt.amount),
            "isRecurring" to debt.isRecurring,
            "notes" to debt.notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateDebt(debt: DebtItem): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(debt.id.isNotBlank())
        val clampedPaid = debt.paidAmount.coerceIn(0.0, debt.amount)
        val docRef = userDoc(uid).collection("debts").document(debt.id)
        val updates = mapOf(
            "title" to debt.title.trim().take(120),
            "counterparty" to debt.counterparty.trim().take(100),
            "amount" to debt.amount,
            "paidAmount" to clampedPaid,
            "direction" to debt.direction,
            "debtType" to debt.debtType.trim().take(40),
            "dueDateMillis" to debt.dueDateMillis,
            "isSettled" to (debt.isSettled || clampedPaid >= debt.amount),
            "isRecurring" to debt.isRecurring,
            "notes" to debt.notes.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDebt(debtId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("debts").document(debtId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- DAILY TASKS ---
    fun observeDailyTasks(uid: String): Flow<List<DailyTask>> =
        observeSubcollection(uid, "dailyTasks", DailyTask::class.java)
            .map { list ->
                list.sortedWith(
                    compareBy<DailyTask> { it.isCompleted }
                        .thenBy {
                            when (it.priority) {
                                DailyTask.PRIORITY_HIGH -> 0
                                DailyTask.PRIORITY_MEDIUM -> 1
                                else -> 2
                            }
                        }
                        .thenBy { it.dateMillis }
                )
            }

    suspend fun addDailyTask(task: DailyTask): Result<String> = runCatching {
        val uid = requireUserId()
        require(task.title.isNotBlank())
        val docRef = userDoc(uid).collection("dailyTasks").document()
        val payload = mapOf(
            "userId" to uid,
            "title" to task.title.trim().take(140),
            "notes" to task.notes.trim().take(300),
            "dateMillis" to task.dateMillis,
            "priority" to task.priority,
            "itemType" to task.itemType,
            "timeLabel" to task.timeLabel.trim().take(20),
            "isCompleted" to task.isCompleted,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateDailyTask(task: DailyTask): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(task.id.isNotBlank())
        val docRef = userDoc(uid).collection("dailyTasks").document(task.id)
        val updates = mapOf(
            "title" to task.title.trim().take(140),
            "notes" to task.notes.trim().take(300),
            "dateMillis" to task.dateMillis,
            "priority" to task.priority,
            "itemType" to task.itemType,
            "timeLabel" to task.timeLabel.trim().take(20),
            "isCompleted" to task.isCompleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteDailyTask(taskId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("dailyTasks").document(taskId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SALARY MANAGEMENT ---
    fun observeSalaryConfig(uid: String): Flow<SalaryConfig?> = flow {
        val docRef = userDoc(uid).collection("salarySettings").document(SalaryConfig.DEFAULT_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SalaryConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertSalaryConfig(config: SalaryConfig): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(config.monthlySalary >= 0.0) { "O salário mensal não pode ser negativo." }
        require(config.paydayOfMonth in 1..31) { "Dia de recebimento inválido." }
        val docRef = userDoc(uid).collection("salarySettings").document(SalaryConfig.DEFAULT_DOC_ID)
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "monthlySalary" to config.monthlySalary,
                    "paydayOfMonth" to config.paydayOfMonth,
                    "frequency" to config.frequency,
                    "lastPromptedPeriodKey" to config.lastPromptedPeriodKey.take(40),
                    "snoozedUntilMillis" to config.snoozedUntilMillis.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "monthlySalary" to config.monthlySalary,
                        "paydayOfMonth" to config.paydayOfMonth,
                        "frequency" to config.frequency,
                        "lastPromptedPeriodKey" to config.lastPromptedPeriodKey.take(40),
                        "snoozedUntilMillis" to config.snoozedUntilMillis.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun snoozeSalaryPrompt(periodKey: String, snoozedUntilMillis: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = userDoc(uid).collection("salarySettings").document(SalaryConfig.DEFAULT_DOC_ID)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "lastPromptedPeriodKey" to periodKey.take(40),
                        "snoozedUntilMillis" to snoozedUntilMillis.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeSalaryCycles(uid: String): Flow<List<SalaryCycle>> =
        observeSubcollection(uid, "salaryCycles", SalaryCycle::class.java)
            .map { list -> list.sortedByDescending { it.receivedDateMillis } }

    suspend fun confirmSalaryReceipt(
        cycleKey: String,
        description: String,
        receivedAmount: Double,
        receivedDateMillis: Long,
        nextPaydayMillis: Long,
        notes: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(cycleKey.isNotBlank()) { "Chave do ciclo salarial inválida." }
        require(description.isNotBlank()) { "Descrição do salário obrigatória." }
        require(receivedAmount > 0.0) { "O valor do salário deve ser maior que zero." }

        val cyclesCollection = userDoc(uid).collection("salaryCycles")
        val incomesCollection = userDoc(uid).collection("incomes")
        val settingsRef = userDoc(uid).collection("salarySettings").document(SalaryConfig.DEFAULT_DOC_ID)

        try {
            // Prevent duplicating salary in incomes for the same salary cycle
            val existingCycleSnap = cyclesCollection
                .whereEqualTo("cycleKey", cycleKey.trim().take(40))
                .limit(1)
                .get()
                .await()

            if (!existingCycleSnap.isEmpty) {
                val existingDoc = existingCycleSnap.documents.first()
                return@runCatching existingDoc.id
            }

            val incomeRef = incomesCollection.document()
            val cycleRef = cyclesCollection.document()
            val cleanDesc = description.trim().take(140)
            val cleanNotes = notes.trim().take(300)

            val batch = db.batch()
            batch.set(
                incomeRef,
                mapOf(
                    "userId" to uid,
                    "description" to cleanDesc,
                    "amount" to receivedAmount,
                    "source" to "Salário",
                    "dateMillis" to receivedDateMillis,
                    "incomeType" to "Fixo",
                    "notes" to (if (cleanNotes.isNotBlank()) cleanNotes else "Ciclo salarial $cycleKey").take(400),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                cycleRef,
                mapOf(
                    "userId" to uid,
                    "cycleKey" to cycleKey.trim().take(40),
                    "description" to cleanDesc,
                    "receivedAmount" to receivedAmount,
                    "receivedDateMillis" to receivedDateMillis,
                    "nextPaydayMillis" to nextPaydayMillis.coerceAtLeast(receivedDateMillis),
                    "linkedIncomeId" to incomeRef.id,
                    "allocations" to emptyList<Map<String, Any>>(),
                    "notes" to cleanNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val settingsSnap = settingsRef.get().await()
            if (settingsSnap.exists()) {
                batch.update(
                    settingsRef,
                    mapOf(
                        "lastPromptedPeriodKey" to cycleKey.trim().take(40),
                        "snoozedUntilMillis" to 0L,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            cycleRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, cyclesCollection.path)
            throw e
        }
    }

    suspend fun updateSalaryCycleAllocations(
        cycleId: String,
        receivedAmount: Double,
        allocations: List<SalaryAllocation>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(cycleId.isNotBlank()) { "ID do ciclo salarial inválido." }
        val cleanAllocations = allocations
            .filter { it.category.isNotBlank() && it.allocatedAmount > 0.0 }
            .take(20)
        val total = cleanAllocations.sumOf { it.allocatedAmount }
        require(total <= receivedAmount + 0.01) {
            "A soma das distribuições (${total.toLong()} Kz) não pode ultrapassar o salário recebido (${receivedAmount.toLong()} Kz)."
        }

        val docRef = userDoc(uid).collection("salaryCycles").document(cycleId)
        val rawAllocations = cleanAllocations.map {
            mapOf(
                "category" to it.category.trim().take(60),
                "allocatedAmount" to it.allocatedAmount
            )
        }
        try {
            docRef.update(
                mapOf(
                    "allocations" to rawAllocations,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateSalaryCycleDetails(cycle: SalaryCycle): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(cycle.id.isNotBlank()) { "ID do ciclo salarial inválido." }
        require(cycle.description.isNotBlank()) { "Descrição obrigatória." }
        require(cycle.receivedAmount > 0.0) { "O valor deve ser maior que zero." }
        require(cycle.totalAllocated <= cycle.receivedAmount + 0.01) {
            "O valor do salário não pode ser inferior ao total já distribuído nas reservas."
        }

        val cycleRef = userDoc(uid).collection("salaryCycles").document(cycle.id)
        try {
            val batch = db.batch()
            batch.update(
                cycleRef,
                mapOf(
                    "description" to cycle.description.trim().take(140),
                    "receivedAmount" to cycle.receivedAmount,
                    "receivedDateMillis" to cycle.receivedDateMillis,
                    "nextPaydayMillis" to cycle.nextPaydayMillis.coerceAtLeast(cycle.receivedDateMillis),
                    "notes" to cycle.notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (cycle.linkedIncomeId.isNotBlank()) {
                val incomeRef = userDoc(uid).collection("incomes").document(cycle.linkedIncomeId)
                val incomeSnap = incomeRef.get().await()
                if (incomeSnap.exists()) {
                    batch.update(
                        incomeRef,
                        mapOf(
                            "description" to cycle.description.trim().take(140),
                            "amount" to cycle.receivedAmount,
                            "dateMillis" to cycle.receivedDateMillis,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, cycleRef.path)
            throw e
        }
    }

    suspend fun deleteSalaryCycle(cycle: SalaryCycle, deleteLinkedIncome: Boolean = true): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(cycle.id.isNotBlank())
        val cycleRef = userDoc(uid).collection("salaryCycles").document(cycle.id)
        try {
            val batch = db.batch()
            batch.delete(cycleRef)
            if (deleteLinkedIncome && cycle.linkedIncomeId.isNotBlank()) {
                val incomeRef = userDoc(uid).collection("incomes").document(cycle.linkedIncomeId)
                val incomeSnap = incomeRef.get().await()
                if (incomeSnap.exists()) {
                    batch.delete(incomeRef)
                }
            }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, cycleRef.path)
            throw e
        }
    }

    // --- AI CHAT HISTORY ---
    fun observeAiMessages(uid: String): Flow<List<AiChatMessage>> =
        observeSubcollection(uid, "aiMessages", AiChatMessage::class.java)
            .map { list -> list.sortedBy { it.timestampMillis } }

    suspend fun saveAiMessage(message: AiChatMessage): Result<String> = runCatching {
        val uid = requireUserId()
        val collectionRef = userDoc(uid).collection("aiMessages")
        val docRef = if (message.id.isNotBlank()) {
            collectionRef.document(message.id)
        } else {
            collectionRef.document()
        }
        val cleanText = message.text.take(8000)
        val rawDrafts = message.pendingExpenses.take(20).map { draft ->
            mapOf(
                "description" to draft.description.trim().take(140),
                "amount" to draft.amount,
                "category" to draft.category.trim().take(60),
                "paymentMethod" to draft.paymentMethod.trim().take(40),
                "isFixed" to draft.isFixed
            )
        }
        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "isUser" to message.isUser,
                    "text" to cleanText,
                    "timestampMillis" to message.timestampMillis.coerceAtLeast(1L),
                    "pendingExpenses" to rawDrafts,
                    "expensesConfirmed" to message.expensesConfirmed,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "text" to cleanText,
                    "pendingExpenses" to rawDrafts,
                    "expensesConfirmed" to message.expensesConfirmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun clearAiMessages(): Result<Unit> = runCatching {
        val uid = requireUserId()
        val collectionRef = userDoc(uid).collection("aiMessages")
        try {
            val docs = collectionRef.get().await().documents
            docs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, collectionRef.path)
            throw e
        }
    }
}
