package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FinanceCatalog
import com.example.data.model.SalaryAllocation
import com.example.data.model.SalaryConfig
import com.example.data.model.SalaryCycle
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt
import com.example.util.SalaryTrackingSummary

@Composable
fun SalaryPaydayNotificationBanner(
    summary: SalaryTrackingSummary,
    onConfirmYes: () -> Unit,
    onNotYet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val expectedAmount = summary.config?.monthlySalary ?: 0.0
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("salary_payday_notification_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = EmeraldIncome.copy(alpha = 0.14f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EmeraldIncome.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = EmeraldIncome,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Já recebeste o teu salário deste mês?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (expectedAmount > 0) {
                        Text(
                            text = "Valor configurado: ${CurrencyUtils.formatKz(expectedAmount)} (${summary.config?.frequency ?: "Mensal"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onConfirmYes,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("salary_confirm_yes_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldIncome,
                        contentColor = NavyDeep
                    )
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sim, já recebi.", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onNotYet,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("salary_confirm_no_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Ainda não recebi.")
                }
            }
        }
    }
}

@Composable
fun SalaryScreen(
    summary: SalaryTrackingSummary,
    autoOpenDistributionCycleId: String?,
    onConsumeAutoOpenDistribution: () -> Unit,
    onSaveConfig: (Double, Int, String) -> Unit,
    onSnoozePrompt: (String) -> Unit,
    onConfirmSalaryReceipt: (cycleKey: String, description: String, amount: Double, dateMillis: Long, nextPaydayMillis: Long, notes: String) -> Unit,
    onSaveAllocations: (cycleId: String, receivedAmount: Double, allocations: List<SalaryAllocation>) -> Unit,
    onUpdateCycleDetails: (SalaryCycle) -> Unit,
    onDeleteCycle: (SalaryCycle) -> Unit
) {
    var showConfigDialog by remember { mutableStateOf(false) }
    var showConfirmReceiptDialog by remember { mutableStateOf(false) }
    var distributingCycle by remember { mutableStateOf<SalaryCycle?>(null) }
    var editingCycle by remember { mutableStateOf<SalaryCycle?>(null) }

    LaunchedEffect(autoOpenDistributionCycleId, summary.cycleHistory) {
        if (!autoOpenDistributionCycleId.isNullOrBlank()) {
            val found = summary.cycleHistory.firstOrNull { it.id == autoOpenDistributionCycleId }
            if (found != null) {
                distributingCycle = found
                onConsumeAutoOpenDistribution()
            }
        }
    }

    val activeConfig = summary.config
    val activeCycle = summary.activeCycle

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Payday confirmation notification banner
        if (summary.isPaydayPromptDue) {
            item {
                SalaryPaydayNotificationBanner(
                    summary = summary,
                    onConfirmYes = { showConfirmReceiptDialog = true },
                    onNotYet = {
                        val key = summary.scheduleWindow?.currentCycleKey ?: ""
                        if (key.isNotBlank()) onSnoozePrompt(key)
                    }
                )
            }
        }

        // 1. Salary Configuration Card ("Meu Salário")
        item {
            SectionCard(
                title = "Configuração Salarial",
                trailing = {
                    TextButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.testTag("edit_salary_config_button")
                    ) {
                        Text(if (activeConfig == null || activeConfig.monthlySalary <= 0.0) "Configurar" else "Actualizar")
                    }
                }
            ) {
                if (activeConfig == null || activeConfig.monthlySalary <= 0.0) {
                    Text(
                        text = "Defina o valor do seu salário, o dia habitual de recebimento e a frequência para activar alertas e controlo inteligente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setup_salary_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldIncome,
                            contentColor = NavyDeep
                        )
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configurar Meu Salário", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Salário Base (${activeConfig.frequency})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatKz(activeConfig.monthlySalary),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldIncome
                            )
                        }
                        StatusBadge(
                            text = "Dia ${activeConfig.paydayOfMonth}",
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val currentConfirmed = summary.scheduleWindow?.let { w ->
                        summary.cycleHistory.any { it.cycleKey == w.currentCycleKey }
                    } ?: false

                    Button(
                        onClick = { showConfirmReceiptDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("register_salary_receipt_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (currentConfirmed) "Novo Recebimento" else "Confirmar Recebimento")
                    }
                }
            }
        }

        // 2. Active Cycle Tracking & Reserves
        if (activeCycle != null) {
            item {
                SectionCard(
                    title = "Ciclo Actual: ${activeCycle.description}",
                    trailing = {
                        TextButton(
                            onClick = { distributingCycle = activeCycle },
                            modifier = Modifier.testTag("distribute_active_salary_button")
                        ) {
                            Text("Distribuir")
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalaryMetricTile(
                            label = "Recebido",
                            value = CurrencyUtils.formatCompactKz(summary.salaryReceived),
                            color = EmeraldIncome,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryMetricTile(
                            label = "Já Gasto",
                            value = CurrencyUtils.formatCompactKz(summary.totalSpentInCycle),
                            color = CoralExpense,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryMetricTile(
                            label = "Reservado",
                            value = CurrencyUtils.formatCompactKz(summary.totalReservedRemaining),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SalaryMetricTile(
                            label = "Disponível Livre",
                            value = CurrencyUtils.formatCompactKz(summary.availableMoney),
                            color = if (summary.availableMoney >= 0) EmeraldIncome else CoralExpense,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryMetricTile(
                            label = "Desp. Pendentes",
                            value = CurrencyUtils.formatCompactKz(summary.pendingObligations),
                            color = AmberAlert,
                            modifier = Modifier.weight(1f)
                        )
                        SalaryMetricTile(
                            label = "Próx. Salário",
                            value = "${summary.daysUntilNextPayday} dias",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (summary.daysUntilNextPayday > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ritmo seguro até ao próximo recebimento: ${CurrencyUtils.formatKz(summary.safeDailySpend)}/dia (já descontando reservas e dívidas pendentes).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Reservas & Distribuição por Categoria",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (summary.allocationBreakdowns.isEmpty()) {
                        Text(
                            text = "Ainda não distribuiu este salário. Toque em \"Distribuir\" para organizar por categorias.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        summary.allocationBreakdowns.forEach { item ->
                            val ratio = if (item.allocatedAmount > 0.0) {
                                (item.remainingReserve / item.allocatedAmount).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            val catColor = FinanceCatalog.categoryColor(item.category)

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${CurrencyUtils.formatCompactKz(item.remainingReserve)} / ${CurrencyUtils.formatCompactKz(item.allocatedAmount)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (item.remainingReserve > 0) catColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = catColor,
                                    strokeCap = StrokeCap.Round
                                )
                                if (item.spentFromReserve > 0.0) {
                                    Text(
                                        text = "Utilizado desta reserva: ${CurrencyUtils.formatKz(item.spentFromReserve)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { distributingCycle = activeCycle },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PieChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Distribuir Salário por Categorias")
                    }
                }
            }
        }

        // 3. History of Salary Cycles
        item {
            Text(
                text = "Histórico de Ciclos Salariais",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (summary.cycleHistory.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "Sem ciclos salariais confirmados",
                    subtitle = "Configure o seu salário e confirme quando o receber para começar a distribuir reservas e acompanhar o saldo.",
                    actionLabel = if (activeConfig == null || activeConfig.monthlySalary <= 0.0) "Configurar Salário" else "Confirmar Recebimento",
                    onAction = {
                        if (activeConfig == null || activeConfig.monthlySalary <= 0.0) {
                            showConfigDialog = true
                        } else {
                            showConfirmReceiptDialog = true
                        }
                    }
                )
            }
        } else {
            items(summary.cycleHistory, key = { it.id }) { cycle ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cycle.description,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${DateUtilsPt.formatFullDate(cycle.receivedDateMillis)} • Ciclo ${cycle.cycleKey}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyUtils.formatKz(cycle.receivedAmount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldIncome
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Distribuído: ${CurrencyUtils.formatCompactKz(cycle.totalAllocated)} • Livre: ${CurrencyUtils.formatCompactKz(cycle.unallocatedAmount)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                IconButton(onClick = { distributingCycle = cycle }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.PieChart, contentDescription = "Distribuir", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { editingCycle = cycle }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Editar ciclo", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteCycle(cycle) }, modifier = Modifier.size(36.dp)) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Eliminar ciclo",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        SalaryConfigDialog(
            initial = activeConfig,
            onDismiss = { showConfigDialog = false },
            onSave = { amount, payday, freq ->
                onSaveConfig(amount, payday, freq)
                showConfigDialog = false
            }
        )
    }

    if (showConfirmReceiptDialog) {
        ConfirmSalaryReceiptDialog(
            summary = summary,
            onDismiss = { showConfirmReceiptDialog = false },
            onConfirm = { cycleKey, desc, amt, dateMillis, nextPayMillis, notes ->
                onConfirmSalaryReceipt(cycleKey, desc, amt, dateMillis, nextPayMillis, notes)
                showConfirmReceiptDialog = false
            }
        )
    }

    distributingCycle?.let { cycle ->
        SalaryDistributionDialog(
            cycle = cycle,
            onDismiss = { distributingCycle = null },
            onSave = { allocations ->
                onSaveAllocations(cycle.id, cycle.receivedAmount, allocations)
                distributingCycle = null
            }
        )
    }

    editingCycle?.let { cycle ->
        EditSalaryCycleDialog(
            cycle = cycle,
            onDismiss = { editingCycle = null },
            onSave = { updated ->
                onUpdateCycleDetails(updated)
                editingCycle = null
            }
        )
    }
}

@Composable
private fun SalaryMetricTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalaryConfigDialog(
    initial: SalaryConfig?,
    onDismiss: () -> Unit,
    onSave: (monthlySalary: Double, paydayOfMonth: Int, frequency: String) -> Unit
) {
    var salaryText by remember {
        mutableStateOf(if (initial != null && initial.monthlySalary > 0) initial.monthlySalary.toLong().toString() else "")
    }
    var paydayText by remember { mutableStateOf((initial?.paydayOfMonth ?: 25).toString()) }
    var frequency by remember { mutableStateOf(initial?.frequency ?: SalaryConfig.FREQUENCY_MONTHLY) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Meu Salário") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = salaryText,
                    onValueChange = { salaryText = it },
                    label = { Text("Valor do salário mensal (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("salary_amount_input")
                )
                OutlinedTextField(
                    value = paydayText,
                    onValueChange = { paydayText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Dia habitual de recebimento (1 a 31)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("salary_payday_input")
                )
                Text("Frequência de recebimento", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinanceCatalog.salaryFrequencies.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq) }
                        )
                    }
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseAmountInput(salaryText)
                    val day = paydayText.toIntOrNull()
                    when {
                        amount == null || amount <= 0.0 -> errorMsg = "Indique um salário válido em Kz."
                        day == null || day !in 1..31 -> errorMsg = "Escolha um dia entre 1 e 31."
                        else -> onSave(amount, day, frequency)
                    }
                },
                modifier = Modifier.testTag("save_salary_config_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ConfirmSalaryReceiptDialog(
    summary: SalaryTrackingSummary,
    onDismiss: () -> Unit,
    onConfirm: (cycleKey: String, description: String, amount: Double, dateMillis: Long, nextPaydayMillis: Long, notes: String) -> Unit
) {
    val cfg = summary.config
    val schedule = summary.scheduleWindow ?: DateUtilsPt.computeSalarySchedule(
        paydayOfMonth = cfg?.paydayOfMonth ?: 25,
        frequency = cfg?.frequency ?: SalaryConfig.FREQUENCY_MONTHLY
    )
    // If current period key already has a confirmed cycle, append a unique suffix so manual extra confirmations don't collide
    val alreadyExists = summary.cycleHistory.any { it.cycleKey == schedule.currentCycleKey }
    val targetCycleKey = if (!alreadyExists) {
        schedule.currentCycleKey
    } else {
        "${schedule.currentCycleKey}-${summary.cycleHistory.count { it.cycleKey.startsWith(schedule.currentCycleKey) } + 1}"
    }

    var description by remember { mutableStateOf(schedule.suggestedDescription) }
    var amountText by remember {
        mutableStateOf(
            if (cfg != null && cfg.monthlySalary > 0) cfg.monthlySalary.toLong().toString() else ""
        )
    }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registar Recebimento do Salário") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Confirme os dados para registar esta entrada nas Receitas e iniciar a distribuição por categorias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor recebido (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Data de recebimento",
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseAmountInput(amountText)
                    when {
                        description.isBlank() -> errorMsg = "Indique uma descrição."
                        amount == null || amount <= 0.0 -> errorMsg = "Indique um valor válido em Kz."
                        else -> onConfirm(
                            targetCycleKey,
                            description.trim(),
                            amount,
                            dateMillis,
                            schedule.nextPaydayMillis,
                            notes.trim()
                        )
                    }
                },
                modifier = Modifier.testTag("confirm_salary_receipt_submit"),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldIncome, contentColor = NavyDeep)
            ) {
                Text("Confirmar Recebimento", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun SalaryDistributionDialog(
    cycle: SalaryCycle,
    onDismiss: () -> Unit,
    onSave: (List<SalaryAllocation>) -> Unit
) {
    val categoryInputs = remember(cycle.id) {
        mutableStateMapOf<String, String>().apply {
            FinanceCatalog.salaryDistributionCategories.forEach { cat ->
                val existing = cycle.allocations.firstOrNull { it.category == cat }?.allocatedAmount ?: 0.0
                this[cat] = if (existing > 0.0) existing.toLong().toString() else ""
            }
        }
    }

    val parsedTotals = FinanceCatalog.salaryDistributionCategories.associateWith { cat ->
        CurrencyUtils.parseAmountInput(categoryInputs[cat] ?: "")?.coerceAtLeast(0.0) ?: 0.0
    }
    val totalDistributed = parsedTotals.values.sum()
    val undistributed = cycle.receivedAmount - totalDistributed
    val exceedsSalary = totalDistributed > cycle.receivedAmount + 0.01
    val ratio = if (cycle.receivedAmount > 0) (totalDistributed / cycle.receivedAmount).toFloat().coerceIn(0f, 1f) else 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Distribuir Salário") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = NavyPrimary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Salário recebido: ${CurrencyUtils.formatKz(cycle.receivedAmount)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Distribuído: ${CurrencyUtils.formatKz(totalDistributed)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exceedsSalary) CoralExpense else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (exceedsSalary) {
                                "Excesso: ${CurrencyUtils.formatKz(totalDistributed - cycle.receivedAmount)}"
                            } else {
                                "Sem distribuição (livre): ${CurrencyUtils.formatKz(undistributed)}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (exceedsSalary) CoralExpense else EmeraldIncome
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = if (exceedsSalary) CoralExpense else EmeraldIncome,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                HorizontalDivider()

                FinanceCatalog.salaryDistributionCategories.forEach { cat ->
                    OutlinedTextField(
                        value = categoryInputs[cat] ?: "",
                        onValueChange = { categoryInputs[cat] = it },
                        label = { Text("$cat (Kz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (exceedsSalary) {
                    Text(
                        text = "A soma dos valores distribuídos não pode ultrapassar o salário recebido.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val list = parsedTotals.entries
                        .filter { it.value > 0.0 }
                        .map { SalaryAllocation(category = it.key, allocatedAmount = it.value) }
                    onSave(list)
                },
                enabled = !exceedsSalary,
                modifier = Modifier.testTag("save_salary_distribution_button")
            ) {
                Text("Guardar Distribuição")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fechar") }
        }
    )
}

@Composable
fun EditSalaryCycleDialog(
    cycle: SalaryCycle,
    onDismiss: () -> Unit,
    onSave: (SalaryCycle) -> Unit
) {
    var description by remember { mutableStateOf(cycle.description) }
    var amountText by remember { mutableStateOf(cycle.receivedAmount.toLong().toString()) }
    var dateMillis by remember { mutableLongStateOf(cycle.receivedDateMillis) }
    var notes by remember { mutableStateOf(cycle.notes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Corrigir Registo Salarial") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor recebido (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Data de recebimento",
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseAmountInput(amountText)
                    when {
                        description.isBlank() -> errorMsg = "Indique uma descrição."
                        amount == null || amount <= 0.0 -> errorMsg = "Valor inválido."
                        amount + 0.01 < cycle.totalAllocated ->
                            errorMsg = "O valor não pode ser menor que o já distribuído (${CurrencyUtils.formatKz(cycle.totalAllocated)})."
                        else -> onSave(
                            cycle.copy(
                                description = description.trim(),
                                receivedAmount = amount,
                                receivedDateMillis = dateMillis,
                                notes = notes.trim()
                            )
                        )
                    }
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
