package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DebtItem
import com.example.data.model.FinanceCatalog
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.IncomeVsExpenseChart
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.viewmodel.BudgetAlertLevel
import com.example.ui.viewmodel.MyDayDashboardData
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt

@Composable
fun DashboardScreen(
    data: MyDayDashboardData,
    userName: String,
    onNavigateToRoute: (String) -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickAddIncome: () -> Unit,
    onConfirmSalaryReceipt: (cycleKey: String, description: String, amount: Double, dateMillis: Long, nextPaydayMillis: Long, notes: String) -> Unit = { _, _, _, _, _, _ -> },
    onSnoozeSalaryPrompt: (String) -> Unit = {}
) {
    var showConfirmSalaryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Greeting & Balance Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(NavyDeep, NavyPrimary, Color(0xFF172554))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Olá, ${userName.substringBefore(" ")}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = DateUtilsPt.formatMonthYear(System.currentTimeMillis()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            if (data.globalAlertLevel != null) {
                                val badgeColor = when (data.globalAlertLevel) {
                                    BudgetAlertLevel.EXCEEDED_100 -> CoralExpense
                                    BudgetAlertLevel.HIGH_85 -> AmberAlert
                                    BudgetAlertLevel.WARNING_70 -> Color(0xFFFBBF24)
                                }
                                StatusBadge(
                                    text = data.globalAlertLevel.label,
                                    containerColor = badgeColor.copy(alpha = 0.2f),
                                    contentColor = badgeColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Saldo Total Disponível",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = CurrencyUtils.formatKz(data.totalAvailableBalance),
                            style = MaterialTheme.typography.displayMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HeroMetricPill(
                                label = "Receitas (Mês)",
                                value = CurrencyUtils.formatCompactKz(data.monthIncomeTotal),
                                icon = Icons.Rounded.ArrowUpward,
                                accent = EmeraldIncome,
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricPill(
                                label = "Despesas (Mês)",
                                value = CurrencyUtils.formatCompactKz(data.monthExpenseTotal),
                                icon = Icons.Rounded.ArrowDownward,
                                accent = CoralExpense,
                                modifier = Modifier.weight(1f)
                            )
                            HeroMetricPill(
                                label = "Poupado (Mês)",
                                value = CurrencyUtils.formatCompactKz(data.monthNetSaved),
                                icon = Icons.Rounded.Savings,
                                accent = if (data.monthNetSaved >= 0) EmeraldIncome else CoralExpense,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        val budgetPct = (data.globalBudgetUsedRatio * 100).toInt().coerceAtLeast(0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (data.globalBudgetLimit > 0) {
                                    "Orçamento utilizado: $budgetPct%"
                                } else {
                                    "Defina o seu orçamento mensal"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFE2E8F0)
                            )
                            if (data.globalBudgetLimit > 0) {
                                Text(
                                    text = CurrencyUtils.formatCompactKz(data.globalBudgetLimit),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { data.globalBudgetUsedRatio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = when {
                                data.globalBudgetUsedRatio >= 1f -> CoralExpense
                                data.globalBudgetUsedRatio >= 0.85f -> AmberAlert
                                else -> EmeraldIncome
                            },
                            trackColor = Color.White.copy(alpha = 0.14f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }

        // Quick Actions
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    QuickActionChip(
                        icon = Icons.Rounded.Add,
                        label = "Despesa",
                        tint = CoralExpense,
                        tag = "quick_add_expense",
                        onClick = onQuickAddExpense
                    )
                }
                item {
                    QuickActionChip(
                        icon = Icons.Rounded.Add,
                        label = "Receita",
                        tint = EmeraldIncome,
                        tag = "quick_add_income",
                        onClick = onQuickAddIncome
                    )
                }
                item {
                    QuickActionChip(
                        icon = Icons.Rounded.Payments,
                        label = "Meu Salário",
                        tint = EmeraldIncome,
                        tag = "quick_salary",
                        onClick = { onNavigateToRoute("salary") }
                    )
                }
                item {
                    QuickActionChip(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "My Day iA",
                        tint = MaterialTheme.colorScheme.secondary,
                        tag = "quick_ai",
                        onClick = { onNavigateToRoute("ai_assistant") }
                    )
                }
                item {
                    QuickActionChip(
                        icon = Icons.Rounded.Assessment,
                        label = "Relatórios",
                        tint = MaterialTheme.colorScheme.primary,
                        tag = "quick_reports",
                        onClick = { onNavigateToRoute("reports") }
                    )
                }
            }
        }

        // Payday Confirmation Notification ("Já recebeste o teu salário deste mês?")
        if (data.salarySummary.isPaydayPromptDue) {
            item {
                SalaryPaydayNotificationBanner(
                    summary = data.salarySummary,
                    onConfirmYes = { showConfirmSalaryDialog = true },
                    onNotYet = {
                        val key = data.salarySummary.scheduleWindow?.currentCycleKey ?: ""
                        if (key.isNotBlank()) onSnoozeSalaryPrompt(key)
                    }
                )
            }
        }

        // O Meu Salário Section
        item {
            val salary = data.salarySummary
            SectionCard(
                title = "O Meu Salário",
                trailing = {
                    TextButton(
                        onClick = { onNavigateToRoute("salary") },
                        modifier = Modifier.testTag("dashboard_open_salary_button")
                    ) {
                        Text(if (salary.hasActiveCycle) "Gerir / Distribuir" else "Configurar")
                    }
                }
            ) {
                if (salary.hasActiveCycle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalaryDashboardTile(
                            label = "Salário recebido",
                            value = CurrencyUtils.formatCompactKz(salary.salaryReceived),
                            color = EmeraldIncome,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryDashboardTile(
                            label = "Dinheiro já gasto",
                            value = CurrencyUtils.formatCompactKz(salary.totalSpentInCycle),
                            color = CoralExpense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalaryDashboardTile(
                            label = "Dinheiro reservado",
                            value = CurrencyUtils.formatCompactKz(salary.totalReservedRemaining),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryDashboardTile(
                            label = "Dinheiro disponível",
                            value = CurrencyUtils.formatCompactKz(salary.availableMoney),
                            color = if (salary.availableMoney >= 0) EmeraldIncome else CoralExpense,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalaryDashboardTile(
                            label = "Despesas pendentes",
                            value = CurrencyUtils.formatCompactKz(salary.pendingObligations),
                            color = AmberAlert,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryDashboardTile(
                            label = "Próximo recebimento",
                            value = "${salary.daysUntilNextPayday} dias",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onNavigateToRoute("salary") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ver Reservas")
                        }
                        Button(
                            onClick = { onNavigateToRoute("ai_assistant") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldIncome,
                                contentColor = NavyDeep
                            )
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Consultar iA", fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text(
                        text = if (salary.config != null && salary.config.monthlySalary > 0) {
                            "Salário configurado: ${CurrencyUtils.formatKz(salary.config.monthlySalary)} (dia ${salary.config.paydayOfMonth}, faltam ${salary.daysUntilNextPayday} dias). Confirme quando receber."
                        } else {
                            "Configure o seu salário mensal para controlar reservas, disponibilidade real e receber previsões inteligentes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (salary.config != null && salary.config.monthlySalary > 0) {
                                showConfirmSalaryDialog = true
                            } else {
                                onNavigateToRoute("salary")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldIncome,
                            contentColor = NavyDeep
                        )
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (salary.config != null && salary.config.monthlySalary > 0) "Confirmar Recebimento" else "Configurar Salário",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Alerts Banner for Overdue Debts or High Budget Utilization
        if (data.overdueDebts.isNotEmpty() || data.budgetProgressItems.any { it.alertLevel != null }) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Alertas Prioritários",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (data.overdueDebts.isNotEmpty()) {
                            Text(
                                text = "• ${data.overdueDebts.size} dívida(s)/pagamento(s) vencido(s) que requerem atenção.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        data.budgetProgressItems.filter { it.alertLevel != null }.take(3).forEach { alert ->
                            Text(
                                text = "• ${alert.category}: ${(alert.ratio * 100).toInt()}% do limite (${alert.alertLevel?.label}).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        // Chart 1: Receitas vs Despesas (Comparação mês anterior)
        item {
            SectionCard(
                title = "Receitas vs Despesas",
                trailing = {
                    TextButton(onClick = { onNavigateToRoute("reports") }) {
                        Text("Ver Relatório")
                    }
                }
            ) {
                IncomeVsExpenseChart(
                    incomeTotal = data.monthIncomeTotal,
                    expenseTotal = data.monthExpenseTotal,
                    prevIncomeTotal = data.prevMonthIncomeTotal,
                    prevExpenseTotal = data.prevMonthExpenseTotal
                )
            }
        }

        // Chart 2: Despesas por Categoria
        item {
            SectionCard(
                title = "Despesas por Categoria (Mês)",
                trailing = {
                    TextButton(onClick = { onNavigateToRoute("budget") }) {
                        Text("Orçamentos")
                    }
                }
            ) {
                CategoryDonutChart(categoryTotals = data.categorySpendThisMonth)
            }
        }

        // Resumo Diário: Tarefas, Compromissos, Pagamentos, Objectivos
        item {
            SectionCard(
                title = "Resumo Diário",
                trailing = {
                    TextButton(onClick = { onNavigateToRoute("my_day") }) {
                        Text("Abrir My Day")
                    }
                }
            ) {
                val pendingToday = data.todayTasks.filter { !it.isCompleted }
                val completedToday = data.todayTasks.count { it.isCompleted }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DailyCountChip(
                        icon = Icons.Rounded.CheckCircle,
                        count = "${pendingToday.size}",
                        label = "Pendentes hoje ($completedToday concl.)",
                        modifier = Modifier.weight(1f)
                    )
                    DailyCountChip(
                        icon = Icons.Rounded.NotificationsActive,
                        count = "${data.upcomingDebts.size + data.overdueDebts.size}",
                        label = "Pagamentos próx.",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (pendingToday.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    pendingToday.take(3).forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            StatusBadge(
                                text = task.priority,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (data.savingsGoals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Objectivos em Andamento",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    data.savingsGoals.take(2).forEach { goal ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = goal.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${(goal.progressRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldIncome
                            )
                        }
                        LinearProgressIndicator(
                            progress = { goal.progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = EmeraldIncome
                        )
                    }
                }
            }
        }

        // Últimas Movimentações
        item {
            val combined = (
                data.expenses.map {
                    MovementPreview(
                        title = it.description,
                        subtitle = "${it.category} • ${DateUtilsPt.formatShortDate(it.dateMillis)}",
                        amount = -it.amount,
                        dateMillis = it.dateMillis,
                        dotColor = FinanceCatalog.categoryColor(it.category)
                    )
                } + data.incomes.map {
                    MovementPreview(
                        title = it.description,
                        subtitle = "${it.source} • ${DateUtilsPt.formatShortDate(it.dateMillis)}",
                        amount = it.amount,
                        dateMillis = it.dateMillis,
                        dotColor = EmeraldIncome
                    )
                }
            ).sortedByDescending { it.dateMillis }.take(6)

            SectionCard(
                title = "Últimas Movimentações",
                trailing = {
                    TextButton(onClick = { onNavigateToRoute("expenses") }) {
                        Text("Ver Todas")
                    }
                }
            ) {
                if (combined.isEmpty()) {
                    Text(
                        text = "Ainda não tem receitas nem despesas registadas. Use os botões acima ou o My Day iA para começar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    combined.forEachIndexed { idx, mov ->
                        if (idx > 0) HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(mov.dotColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mov.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = mov.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = (if (mov.amount >= 0) "+" else "") + CurrencyUtils.formatKz(mov.amount),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (mov.amount >= 0) EmeraldIncome else CoralExpense,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmSalaryDialog) {
        ConfirmSalaryReceiptDialog(
            summary = data.salarySummary,
            onDismiss = { showConfirmSalaryDialog = false },
            onConfirm = { cycleKey, desc, amt, dateMillis, nextPayMillis, notes ->
                onConfirmSalaryReceipt(cycleKey, desc, amt, dateMillis, nextPayMillis, notes)
                showConfirmSalaryDialog = false
            }
        )
    }
}

@Composable
private fun SalaryDashboardTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class MovementPreview(
    val title: String,
    val subtitle: String,
    val amount: Double,
    val dateMillis: Long,
    val dotColor: Color
)

@Composable
private fun HeroMetricPill(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .testTag(tag)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun DailyCountChip(
    icon: ImageVector,
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
