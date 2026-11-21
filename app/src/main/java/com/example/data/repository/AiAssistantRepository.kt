package com.example.data.repository

import android.util.Log
import com.example.data.model.AiChatMessage
import com.example.data.model.CategoryBudget
import com.example.data.model.DailyTask
import com.example.data.model.DebtItem
import com.example.data.model.Expense
import com.example.data.model.FinanceCatalog
import com.example.data.model.Income
import com.example.data.model.ParsedExpenseDraft
import com.example.data.model.SavingsGoal
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt
import com.example.util.SalaryTrackingSummary
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class FinancialContextSnapshot(
    val userName: String,
    val monthlyBudgetLimit: Double,
    val expenses: List<Expense>,
    val incomes: List<Income>,
    val categoryBudgets: List<CategoryBudget>,
    val savingsGoals: List<SavingsGoal>,
    val debts: List<DebtItem>,
    val tasks: List<DailyTask>,
    val salarySummary: SalaryTrackingSummary? = null
)

data class SalaryDistributionProposal(
    val allocations: Map<String, Double>,
    val rationale: String
)

sealed interface AiParseResult {
    data class ExpensesDetected(
        val replyIntro: String,
        val drafts: List<ParsedExpenseDraft>
    ) : AiParseResult

    data object ConversationalQuery : AiParseResult
}

class AiAssistantRepository {

    private val aiClient by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val chatModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                maxOutputTokens = 4096
            },
            systemInstruction = content {
                text(
                    """
                    És o My Day iA, o assistente financeiro e de organização pessoal do utilizador em Angola.
                    Responde sempre em Português claro, conciso e prático, usando o Kwanza (Kz) como moeda.
                    REGRAS CRÍTICAS DE INTEGRIDADE:
                    1. Utiliza EXCLUSIVAMENTE os dados reais fornecidos no contexto financeiro abaixo.
                    2. NUNCA inventes despesas, receitas, saldos, estatísticas ou datas. Se não houver registos suficientes, informa claramente o utilizador que ainda não existem dados para essa análise.
                    3. Dá recomendações personalizadas, objectivas e accionáveis com base nos valores reais.
                    """.trimIndent()
                )
            }
        )
    }

    private val expenseParserSchema = Schema.obj(
        properties = mapOf(
            "isExpenseEntry" to Schema.boolean(
                description = "True only if the user is reporting or logging one or more concrete expenses they spent or paid."
            ),
            "confirmationPrompt" to Schema.string(
                description = "Short Portuguese message asking the user to confirm before saving the parsed expenses."
            ),
            "expenses" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "description" to Schema.string(description = "Short description of the expense in Portuguese."),
                        "amount" to Schema.double(description = "Numeric amount in Kwanza (Kz), greater than 0."),
                        "category" to Schema.enumeration(
                            values = FinanceCatalog.expenseCategories,
                            description = "Best matching category from the list."
                        ),
                        "paymentMethod" to Schema.enumeration(
                            values = FinanceCatalog.paymentMethods,
                            description = "Payment method if mentioned, otherwise Dinheiro."
                        ),
                        "isFixed" to Schema.boolean(description = "True if fixed recurring expense, otherwise false.")
                    )
                ),
                description = "List of parsed expenses if isExpenseEntry is true, otherwise empty array."
            )
        )
    )

    private val fastParserModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = expenseParserSchema
            }
        )
    }

    private val fastInsightModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                maxOutputTokens = 4096
            },
            systemInstruction = content {
                text(
                    """
                    És o My Day iA, o assistente financeiro e de organização pessoal do utilizador em Angola.
                    Responde sempre em Português de Angola, claro, conciso e prático, usando o Kwanza (Kz) como moeda.
                    Utiliza EXCLUSIVAMENTE os dados reais fornecidos no contexto financeiro e nunca inventes números, despesas, saldos ou datas.
                    """.trimIndent()
                )
            }
        )
    }

    private val salaryDistributionSchema = Schema.obj(
        properties = mapOf(
            "rationale" to Schema.string(
                description = "Resumo curto em Português explicando a proposta de distribuição salarial."
            ),
            "allocations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "category" to Schema.enumeration(
                            values = FinanceCatalog.salaryDistributionCategories,
                            description = "Categoria de distribuição do salário."
                        ),
                        "amount" to Schema.double(
                            description = "Valor sugerido em Kwanza (Kz), maior ou igual a 0."
                        )
                    )
                ),
                description = "Lista de valores por categoria cuja soma total nunca ultrapasse o salário recebido."
            )
        )
    )

    private val salaryProposalModel by lazy {
        aiClient.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = salaryDistributionSchema
            }
        )
    }

    internal fun shouldRunExpenseParser(userMessage: String): Boolean {
        return userMessage.any { it.isDigit() }
    }

    internal fun buildConversationHistory(messages: List<AiChatMessage>): List<Content> {
        val validMessages = messages
            .filter { msg ->
                msg.id != "welcome" &&
                    !msg.isStreaming &&
                    msg.text.isNotBlank() &&
                    !msg.text.startsWith("Não foi possível obter resposta") &&
                    !msg.text.startsWith("O tempo de resposta da IA expirou")
            }
            .sortedBy { it.timestampMillis }
            .takeLast(12)

        val normalized = mutableListOf<AiChatMessage>()
        for (msg in validMessages) {
            if (normalized.isEmpty()) {
                if (msg.isUser) normalized.add(msg)
            } else if (normalized.last().isUser != msg.isUser) {
                normalized.add(msg)
            } else if (!msg.isUser) {
                normalized[normalized.lastIndex] = msg
            } else {
                normalized[normalized.lastIndex] = msg
            }
        }
        // History passed to startChat() precedes a new "user" message, so it must end with a "model" turn.
        if (normalized.lastOrNull()?.isUser == true) {
            normalized.removeAt(normalized.lastIndex)
        }
        val capped = if (normalized.size > 8) normalized.takeLast(8) else normalized
        return capped.map { msg ->
            content(role = if (msg.isUser) "user" else "model") {
                text(msg.text.take(2000))
            }
        }
    }

    suspend fun detectNaturalLanguageExpenses(userMessage: String): AiParseResult {
        // Pure conversational questions without numbers cannot describe monetary expense entries;
        // skipping the extra parser call cuts response latency in half.
        if (!shouldRunExpenseParser(userMessage)) {
            return AiParseResult.ConversationalQuery
        }
        return try {
            val response = withContext(Dispatchers.IO) {
                withTimeout(30_000L) {
                    fastParserModel.generateContent(
                        """
                        Analisa a mensagem do utilizador e decide se ele está a tentar registar uma ou mais despesas concretas (com valores monetários).
                        Se for uma pergunta (ex: "quanto gastei?", "posso comprar?", "qual foi a maior despesa?"), define isExpenseEntry = false.
                        Mensagem: "$userMessage"
                        """.trimIndent()
                    )
                }
            }
            val rawJson = response.text ?: return AiParseResult.ConversationalQuery
            val json = JSONObject(rawJson)
            val isEntry = json.optBoolean("isExpenseEntry", false)
            if (!isEntry) {
                AiParseResult.ConversationalQuery
            } else {
                val array = json.optJSONArray("expenses")
                val drafts = mutableListOf<ParsedExpenseDraft>()
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val amount = obj.optDouble("amount", 0.0)
                        val desc = obj.optString("description", "").trim()
                        val category = obj.optString("category", "Outros")
                        val method = obj.optString("paymentMethod", "Dinheiro")
                        val isFixed = obj.optBoolean("isFixed", false)
                        if (amount > 0.0 && desc.isNotEmpty()) {
                            drafts.add(
                                ParsedExpenseDraft(
                                    description = desc,
                                    amount = amount,
                                    category = if (category in FinanceCatalog.expenseCategories) category else "Outros",
                                    paymentMethod = if (method in FinanceCatalog.paymentMethods) method else "Dinheiro",
                                    isFixed = isFixed
                                )
                            )
                        }
                    }
                }
                if (drafts.isEmpty()) {
                    AiParseResult.ConversationalQuery
                } else {
                    val prompt = json.optString(
                        "confirmationPrompt",
                        "Identifiquei estes registos de despesa. Confirma se pretende guardar na sua conta:"
                    )
                    AiParseResult.ExpensesDetected(prompt, drafts)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Natural language expense detection timed out; falling back to chat", e)
            AiParseResult.ConversationalQuery
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Natural language expense detection fallback", e)
            AiParseResult.ConversationalQuery
        }
    }

    fun streamAssistantReply(
        userMessage: String,
        snapshot: FinancialContextSnapshot,
        recentHistory: List<AiChatMessage> = emptyList()
    ): Flow<String> = flow {
        val contextSummary = buildContextPrompt(snapshot)
        val fullPrompt = """
            $contextSummary

            PERGUNTA DO UTILIZADOR:
            $userMessage
        """.trimIndent()
        val historyContent = buildConversationHistory(recentHistory)

        val accumulated = StringBuilder()
        var streamTimeoutOrError: Throwable? = null

        try {
            withTimeout(30_000L) {
                val streamFlow = if (historyContent.isEmpty()) {
                    chatModel.generateContentStream(fullPrompt)
                } else {
                    chatModel.startChat(history = historyContent).sendMessageStream(fullPrompt)
                }
                streamFlow.collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Primary AI stream timed out after 30s; accumulated=${accumulated.isNotBlank()}", e)
            streamTimeoutOrError = e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Primary AI stream failed; attempting fast fallback", e)
            streamTimeoutOrError = e
        }

        if (accumulated.isNotBlank()) {
            return@flow
        }

        // Fallback to ultra-fast unary model if streaming stalled or returned no chunks
        try {
            val fallbackResponse = withTimeout(30_000L) {
                if (historyContent.isEmpty()) {
                    fastInsightModel.generateContent(fullPrompt)
                } else {
                    fastInsightModel.startChat(history = historyContent).sendMessage(fullPrompt)
                }
            }
            val fallbackText = fallbackResponse.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("O assistente não devolveu resposta.")
            emit(fallbackText)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Fallback AI also timed out", e)
            val primaryCause = streamTimeoutOrError
            if (primaryCause != null && primaryCause !is TimeoutCancellationException) {
                throw primaryCause
            }
            throw IllegalStateException("O tempo de resposta da IA expirou. Tente novamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallback AI failed", e)
            val primaryCause = streamTimeoutOrError
            if (primaryCause != null && primaryCause !is TimeoutCancellationException) {
                throw primaryCause
            }
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateBudgetAdvice(snapshot: FinancialContextSnapshot): String {
        val contextSummary = buildContextPrompt(snapshot)
        val prompt = """
            $contextSummary

            Com base estritamente nestes dados reais, identifica categorias com gastos elevados ou próximos do limite e dá 3 sugestões práticas e curtas para reduzir despesas este mês. Se não houver despesas registadas ainda, indica como começar a definir limites saudáveis.
        """.trimIndent()
        return runFastPrompt(prompt)
    }

    suspend fun generateSavingsAdvice(
        goal: SavingsGoal,
        snapshot: FinancialContextSnapshot
    ): String {
        val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
        val daysLeft = DateUtilsPt.daysUntil(goal.deadlineMillis).coerceAtLeast(1)
        val dailyNeed = remaining / daysLeft
        val weeklyNeed = remaining / (daysLeft / 7.0).coerceAtLeast(1.0)
        val monthlyNeed = remaining / (daysLeft / 30.0).coerceAtLeast(1.0)

        val contextSummary = buildContextPrompt(snapshot)
        val prompt = """
            $contextSummary

            OBJECTIVO EM ANÁLISE:
            - Nome: ${goal.name}
            - Pretendido: ${CurrencyUtils.formatKz(goal.targetAmount)}
            - Já poupado: ${CurrencyUtils.formatKz(goal.savedAmount)}
            - Em falta: ${CurrencyUtils.formatKz(remaining)}
            - Dias restantes até ao prazo: $daysLeft dias
            - Ritmo matemático necessário: ${CurrencyUtils.formatKz(dailyNeed)}/dia | ${CurrencyUtils.formatKz(weeklyNeed)}/semana | ${CurrencyUtils.formatKz(monthlyNeed)}/mês

            Com base na capacidade financeira real do utilizador acima, avalia se este ritmo é realista e dá 2 dicas objectivas para atingir esta meta no prazo.
        """.trimIndent()
        return runFastPrompt(prompt)
    }

    suspend fun organizeDailySchedule(
        tasks: List<DailyTask>,
        availableHoursNote: String
    ): String {
        val pending = tasks.filter { !it.isCompleted }
        if (pending.isEmpty()) {
            return "Não existem tarefas pendentes para organizar neste momento. Adicione tarefas para que o My Day iA crie o seu plano diário."
        }
        val taskListText = pending.joinToString("\n") { t ->
            "- [${t.priority}] (${t.itemType}) ${t.title} ${if (t.timeLabel.isNotBlank()) "às ${t.timeLabel}" else ""} | Data: ${DateUtilsPt.formatFullDate(t.dateMillis)}"
        }
        val prompt = """
            TAREFAS E COMPROMISSOS PENDENTES REAIS:
            $taskListText

            DISPONIBILIDADE DO UTILIZADOR:
            ${availableHoursNote.ifBlank { "Dia normal de trabalho (08:00 às 18:00)" }}

            Cria uma ordem recomendada de execução passo a passo para hoje, respeitando compromissos com hora marcada e priorizando tarefas de prioridade ALTA. Sê directo, claro e motivador.
        """.trimIndent()
        return runFastPrompt(prompt)
    }

    suspend fun proposeSalaryDistribution(
        receivedAmount: Double,
        snapshot: FinancialContextSnapshot
    ): SalaryDistributionProposal {
        val contextSummary = buildContextPrompt(snapshot)
        val categoriesList = FinanceCatalog.salaryDistributionCategories.joinToString(", ")
        val prompt = """
            $contextSummary

            TAREFA:
            O utilizador acabou de receber um salário de ${CurrencyUtils.formatKz(receivedAmount)}.
            Propõe uma distribuição equilibrada e realista para Angola por entre as seguintes categorias: $categoriesList.
            REGRAS OBRIGATÓRIAS:
            1. A soma de todos os valores em "allocations" TEM de ser <= $receivedAmount (podes deixar uma pequena margem por distribuir como dinheiro livre ou distribuir até 100%).
            2. Considera as dívidas/obrigações pendentes reais, os objectivos de poupança e os hábitos reais de despesa apresentados acima.
            3. Usa valores redondos em Kz (ex.: múltiplos de 500 ou 1000).
        """.trimIndent()

        return try {
            val response = withContext(Dispatchers.IO) {
                withTimeout(45_000L) {
                    salaryProposalModel.generateContent(prompt)
                }
            }
            val rawJson = response.text ?: throw IllegalStateException("Resposta vazia do Gemini.")
            val json = JSONObject(rawJson)
            val rationale = json.optString("rationale", "Proposta gerada com base nos seus dados reais.")
            val array = json.optJSONArray("allocations")
            val rawMap = linkedMapOf<String, Double>()
            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val cat = obj.optString("category", "")
                    val amt = obj.optDouble("amount", 0.0).coerceAtLeast(0.0)
                    if (cat in FinanceCatalog.salaryDistributionCategories && amt > 0.0) {
                        rawMap[cat] = kotlin.math.round(amt)
                    }
                }
            }
            // Enforce strict invariant: sum(allocations) <= receivedAmount
            val total = rawMap.values.sum()
            val normalized = if (total > receivedAmount && total > 0.0) {
                val factor = (receivedAmount * 0.98) / total
                rawMap.mapValues { (_, v) -> kotlin.math.floor(v * factor) }
            } else {
                rawMap
            }
            SalaryDistributionProposal(
                allocations = normalized,
                rationale = rationale
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Salary distribution AI proposal timed out", e)
            throw IllegalStateException("O tempo de resposta da IA expirou. Tente novamente.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Salary distribution AI proposal failed", e)
            throw e
        }
    }

    private suspend fun runFastPrompt(prompt: String): String {
        return try {
            val response = withContext(Dispatchers.IO) {
                withTimeout(45_000L) {
                    fastInsightModel.generateContent(prompt)
                }
            }
            response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("A IA devolveu uma resposta vazia.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Fast AI prompt timed out", e)
            throw IllegalStateException("O pedido à IA expirou após 45 segundos.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fast AI prompt failed", e)
            throw e
        }
    }

    internal fun buildContextPrompt(snapshot: FinancialContextSnapshot): String {
        val now = System.currentTimeMillis()
        val monthStart = DateUtilsPt.startOfMonthMillis(now, 0)
        val monthEnd = DateUtilsPt.endOfMonthMillis(now, 0)
        val prevMonthStart = DateUtilsPt.startOfMonthMillis(now, -1)
        val prevMonthEnd = DateUtilsPt.endOfMonthMillis(now, -1)
        val weekStart = DateUtilsPt.startOfWeekMillis(now)

        val monthExpenses = snapshot.expenses.filter { it.dateMillis in monthStart..monthEnd }
        val prevMonthExpenses = snapshot.expenses.filter { it.dateMillis in prevMonthStart..prevMonthEnd }
        val weekExpenses = snapshot.expenses.filter { it.dateMillis in weekStart..now }

        val monthIncomes = snapshot.incomes.filter { it.dateMillis in monthStart..monthEnd }
        val totalIncomesAll = snapshot.incomes.sumOf { it.amount }
        val totalExpensesAll = snapshot.expenses.sumOf { it.amount }
        val availableBalance = totalIncomesAll - totalExpensesAll

        val monthIncomeTotal = monthIncomes.sumOf { it.amount }
        val monthExpenseTotal = monthExpenses.sumOf { it.amount }
        val weekExpenseTotal = weekExpenses.sumOf { it.amount }
        val prevMonthExpenseTotal = prevMonthExpenses.sumOf { it.amount }
        val daysRemaining = DateUtilsPt.daysRemainingInMonth(now)

        val byCategory = monthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }

        val biggestExpense = monthExpenses.maxByOrNull { it.amount }

        val categoryBreakdown = if (byCategory.isEmpty()) {
            "Sem despesas este mês."
        } else {
            byCategory.joinToString("\n") { "  - ${it.key}: ${CurrencyUtils.formatKz(it.value)}" }
        }

        val budgetsInfo = if (snapshot.categoryBudgets.isEmpty()) {
            "Sem limites por categoria definidos."
        } else {
            snapshot.categoryBudgets.joinToString("\n") { b ->
                val spent = byCategory.firstOrNull { it.key == b.category }?.value ?: 0.0
                val pct = if (b.monthlyLimit > 0) (spent / b.monthlyLimit * 100).toInt() else 0
                "  - ${b.category}: gasto ${CurrencyUtils.formatKz(spent)} de ${CurrencyUtils.formatKz(b.monthlyLimit)} ($pct%)"
            }
        }

        val recentExpensesText = snapshot.expenses.take(15).joinToString("\n") { e ->
            "  - ${DateUtilsPt.formatFullDate(e.dateMillis)} | ${e.description} | ${CurrencyUtils.formatKz(e.amount)} | ${e.category} | ${e.paymentMethod}"
        }.ifBlank { "  Nenhuma despesa registada." }

        val savingsText = snapshot.savingsGoals.joinToString("\n") { g ->
            "  - ${g.name}: ${CurrencyUtils.formatKz(g.savedAmount)} / ${CurrencyUtils.formatKz(g.targetAmount)} (prazo ${DateUtilsPt.formatFullDate(g.deadlineMillis)})"
        }.ifBlank { "  Nenhum objectivo de poupança." }

        val debtsText = snapshot.debts.filter { !it.isSettled }.joinToString("\n") { d ->
            val dirLabel = if (d.direction == DebtItem.DIRECTION_OWE) "DEVO" else "DEVEM-ME"
            "  - [$dirLabel] ${d.title} (${d.counterparty}): em falta ${CurrencyUtils.formatKz(d.remainingAmount)} | vencimento ${DateUtilsPt.formatFullDate(d.dueDateMillis)}"
        }.ifBlank { "  Sem dívidas pendentes." }

        val tasksText = snapshot.tasks.filter { !it.isCompleted }.take(10).joinToString("\n") { t ->
            "  - [${t.priority}] ${t.title} (${t.itemType}) ${DateUtilsPt.formatFullDate(t.dateMillis)} ${t.timeLabel}"
        }.ifBlank { "  Sem tarefas pendentes." }

        val salarySection = snapshot.salarySummary?.let { s ->
            val cfg = s.config
            val active = s.activeCycle
            val configText = if (cfg != null && cfg.monthlySalary > 0) {
                "Salário configurado: ${CurrencyUtils.formatKz(cfg.monthlySalary)} (${cfg.frequency}, dia habitual ${cfg.paydayOfMonth})"
            } else {
                "Salário ainda não configurado."
            }
            val activeText = if (active != null) {
                val allocLines = if (s.allocationBreakdowns.isEmpty()) {
                    "    Sem categorias distribuídas neste ciclo."
                } else {
                    s.allocationBreakdowns.joinToString("\n") { a ->
                        "    * ${a.category}: reservado ${CurrencyUtils.formatKz(a.allocatedAmount)} | gasto da reserva ${CurrencyUtils.formatKz(a.spentFromReserve)} | restante ${CurrencyUtils.formatKz(a.remainingReserve)}"
                    }
                }
                """
                - Ciclo salarial activo (${active.cycleKey} — ${active.description}, recebido em ${DateUtilsPt.formatFullDate(active.receivedDateMillis)}):
                  * Salário recebido: ${CurrencyUtils.formatKz(s.salaryReceived)}
                  * Receitas extras no ciclo: ${CurrencyUtils.formatKz(s.extraIncomesInCycle)}
                  * Dinheiro já gasto no ciclo: ${CurrencyUtils.formatKz(s.totalSpentInCycle)}
                  * Dinheiro reservado restante: ${CurrencyUtils.formatKz(s.totalReservedRemaining)} (inicialmente distribuído: ${CurrencyUtils.formatKz(s.totalReservedInitial)})
                  * Dinheiro disponível (livre após gastos e reservas): ${CurrencyUtils.formatKz(s.availableMoney)}
                  * Obrigações / dívidas pendentes (a pagar): ${CurrencyUtils.formatKz(s.pendingObligations)}
                  * Dias restantes até ao próximo recebimento: ${s.daysUntilNextPayday} dia(s)
                  * Limite diário seguro sugerido (disponível líquido / dias restantes): ${CurrencyUtils.formatKz(s.safeDailySpend)}/dia
                  * Detalhe das reservas por categoria:
                $allocLines
                """.trimIndent()
            } else {
                "- Sem ciclo salarial confirmado ainda. Próximo recebimento previsto em ${s.daysUntilNextPayday} dia(s). Obrigações pendentes: ${CurrencyUtils.formatKz(s.pendingObligations)}."
            }
            val historyText = if (s.cycleHistory.size > 1) {
                s.cycleHistory.drop(1).take(4).joinToString("\n") { c ->
                    "  - ${c.cycleKey}: ${CurrencyUtils.formatKz(c.receivedAmount)} (${DateUtilsPt.formatFullDate(c.receivedDateMillis)})"
                }
            } else "  Sem ciclos anteriores."
            """
            GESTÃO DO MEU SALÁRIO ("O MEU SALÁRIO"):
            - $configText
            $activeText
            - Histórico anterior:
            $historyText
            """.trimIndent()
        } ?: "GESTÃO DO MEU SALÁRIO: Sem dados salariais."

        return """
            DADOS REAIS DA CONTA (${snapshot.userName}) — DATA ACTUAL: ${DateUtilsPt.formatFullDate(now)}
            - Saldo total disponível (todas receitas - todas despesas): ${CurrencyUtils.formatKz(availableBalance)}
            - Receitas deste mês: ${CurrencyUtils.formatKz(monthIncomeTotal)}
            - Despesas deste mês: ${CurrencyUtils.formatKz(monthExpenseTotal)}
            - Poupança líquida do mês (receitas mês - despesas mês): ${CurrencyUtils.formatKz(monthIncomeTotal - monthExpenseTotal)}
            - Despesas desta semana: ${CurrencyUtils.formatKz(weekExpenseTotal)}
            - Despesas do mês anterior: ${CurrencyUtils.formatKz(prevMonthExpenseTotal)}
            - Orçamento mensal global definido: ${if (snapshot.monthlyBudgetLimit > 0) CurrencyUtils.formatKz(snapshot.monthlyBudgetLimit) else "Não definido"}
            - Dias restantes no mês: $daysRemaining dias
            - Maior despesa deste mês: ${biggestExpense?.let { "${it.description} (${CurrencyUtils.formatKz(it.amount)} em ${it.category}, ${DateUtilsPt.formatFullDate(it.dateMillis)})" } ?: "Nenhuma"}

            $salarySection

            GASTOS POR CATEGORIA ESTE MÊS:
            $categoryBreakdown

            ORÇAMENTOS POR CATEGORIA:
            $budgetsInfo

            ÚLTIMAS DESPESAS REGISTADAS (máx. 15):
            $recentExpensesText

            OBJECTIVOS DE POUPANÇA:
            $savingsText

            DÍVIDAS E PAGAMENTOS PENDENTES:
            $debtsText

            TAREFAS E COMPROMISSOS PENDENTES:
            $tasksText
        """.trimIndent()
    }

    private companion object {
        const val TAG = "AiAssistantRepo"
    }
}
