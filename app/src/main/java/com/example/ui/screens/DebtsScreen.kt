package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.DebtItem
import com.example.data.model.FinanceCatalog
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt

@Composable
fun DebtsScreen(
    debts: List<DebtItem>,
    overdueDebts: List<DebtItem>,
    upcomingDebts: List<DebtItem>,
    onSaveDebt: (DebtItem) -> Unit,
    onRecordPayment: (DebtItem, Double) -> Unit,
    onToggleSettled: (DebtItem) -> Unit,
    onDeleteDebt: (String) -> Unit
) {
    var filterTab by remember { mutableStateOf("ALL") } // ALL, OWE, OWED, SETTLED
    var editingDebt by remember { mutableStateOf<DebtItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var paymentTarget by remember { mutableStateOf<DebtItem?>(null) }

    val totalOwe = debts.filter { !it.isSettled && it.direction == DebtItem.DIRECTION_OWE }.sumOf { it.remainingAmount }
    val totalOwed = debts.filter { !it.isSettled && it.direction == DebtItem.DIRECTION_OWED }.sumOf { it.remainingAmount }

    val filtered = remember(debts, filterTab) {
        when (filterTab) {
            "OWE" -> debts.filter { !it.isSettled && it.direction == DebtItem.DIRECTION_OWE }
            "OWED" -> debts.filter { !it.isSettled && it.direction == DebtItem.DIRECTION_OWED }
            "SETTLED" -> debts.filter { it.isSettled }
            else -> debts
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingDebt = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_debt_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nova dívida ou pagamento")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Dinheiro que Devo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatKz(totalOwe),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = CoralExpense
                            )
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Dinheiro que me Devem", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyUtils.formatKz(totalOwed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldIncome
                            )
                        }
                    }
                }
            }

            if (overdueDebts.isNotEmpty() || upcomingDebts.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = AmberAlert.copy(alpha = 0.14f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberAlert)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Alertas de Vencimento",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (overdueDebts.isNotEmpty()) {
                                Text(
                                    text = "• ${overdueDebts.size} dívida(s) vencida(s): ${overdueDebts.joinToString { it.title }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CoralExpense
                                )
                            }
                            if (upcomingDebts.isNotEmpty()) {
                                Text(
                                    text = "• ${upcomingDebts.size} pagamento(s) nos próximos 7 dias.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterTab == "ALL", onClick = { filterTab = "ALL" }, label = { Text("Todas") })
                    FilterChip(selected = filterTab == "OWE", onClick = { filterTab = "OWE" }, label = { Text("Devo") })
                    FilterChip(selected = filterTab == "OWED", onClick = { filterTab = "OWED" }, label = { Text("Devem-me") })
                    FilterChip(selected = filterTab == "SETTLED", onClick = { filterTab = "SETTLED" }, label = { Text("Liquidadas") })
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.CreditCard,
                        title = "Sem registos nesta vista",
                        subtitle = "Controle empréstimos, prestações, pagamentos recorrentes e valores que lhe devem.",
                        actionLabel = "Registar Dívida / Pagamento",
                        onAction = {
                            editingDebt = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { debt ->
                    DebtCard(
                        debt = debt,
                        onRecordPayment = { paymentTarget = debt },
                        onToggleSettled = { onToggleSettled(debt) },
                        onEdit = {
                            editingDebt = debt
                            showEditor = true
                        },
                        onDelete = { onDeleteDebt(debt.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        DebtEditorDialog(
            initial = editingDebt,
            onDismiss = { showEditor = false },
            onSave = {
                onSaveDebt(it)
                showEditor = false
            }
        )
    }

    paymentTarget?.let { target ->
        DebtPaymentDialog(
            debt = target,
            onDismiss = { paymentTarget = null },
            onConfirm = { amount ->
                onRecordPayment(target, amount)
                paymentTarget = null
            }
        )
    }
}

@Composable
private fun DebtCard(
    debt: DebtItem,
    onRecordPayment: () -> Unit,
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val todayStart = DateUtilsPt.startOfDayMillis()
    val isOverdue = !debt.isSettled && debt.dueDateMillis < todayStart
    val isDueSoon = !debt.isSettled && debt.dueDateMillis in todayStart..(todayStart + 7L * 86_400_000L)
    val progress = if (debt.amount > 0) (debt.paidAmount / debt.amount).toFloat().coerceIn(0f, 1f) else 0f
    val accent = if (debt.direction == DebtItem.DIRECTION_OWE) CoralExpense else EmeraldIncome

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
                    Text(debt.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${debt.counterparty} • ${debt.debtType} • Vence: ${DateUtilsPt.formatFullDate(debt.dueDateMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    text = if (debt.direction == DebtItem.DIRECTION_OWE) "Devo" else "Devem-me",
                    containerColor = accent.copy(alpha = 0.14f),
                    contentColor = accent
                )
                if (debt.isRecurring) {
                    StatusBadge(
                        text = "Recorrente",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when {
                    debt.isSettled -> StatusBadge("Liquidada", EmeraldIncome.copy(alpha = 0.14f), EmeraldIncome)
                    isOverdue -> StatusBadge("Vencida", CoralExpense.copy(alpha = 0.16f), CoralExpense)
                    isDueSoon -> StatusBadge("Vence em breve", AmberAlert.copy(alpha = 0.18f), AmberAlert)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
                color = accent,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "Pago: ${CurrencyUtils.formatKz(debt.paidAmount)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Em falta: ${CurrencyUtils.formatKz(debt.remainingAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            if (!debt.isSettled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRecordPayment, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Registar Pagamento")
                    }
                    OutlinedButton(onClick = onToggleSettled, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Liquidar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtEditorDialog(
    initial: DebtItem?,
    onDismiss: () -> Unit,
    onSave: (DebtItem) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var counterparty by remember { mutableStateOf(initial?.counterparty ?: "") }
    var amountText by remember {
        mutableStateOf(if (initial != null && initial.amount > 0) initial.amount.toLong().toString() else "")
    }
    var paidText by remember {
        mutableStateOf(if (initial != null && initial.paidAmount > 0) initial.paidAmount.toLong().toString() else "0")
    }
    var direction by remember { mutableStateOf(initial?.direction ?: DebtItem.DIRECTION_OWE) }
    var debtType by remember { mutableStateOf(initial?.debtType ?: FinanceCatalog.debtTypes.first()) }
    var dueDateMillis by remember {
        mutableLongStateOf(initial?.dueDateMillis ?: (System.currentTimeMillis() + 86_400_000L * 7))
    }
    var isRecurring by remember { mutableStateOf(initial?.isRecurring ?: false) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova Dívida / Pagamento" else "Editar Registo") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = direction == DebtItem.DIRECTION_OWE,
                        onClick = { direction = DebtItem.DIRECTION_OWE },
                        label = { Text("Dinheiro que devo") }
                    )
                    FilterChip(
                        selected = direction == DebtItem.DIRECTION_OWED,
                        onClick = { direction = DebtItem.DIRECTION_OWED },
                        label = { Text("Dinheiro que me devem") }
                    )
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Pessoa ou instituição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor total (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = paidText,
                    onValueChange = { paidText = it },
                    label = { Text("Já pago / recebido (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Data de vencimento",
                    dateMillis = dueDateMillis,
                    onDateSelected = { dueDateMillis = it }
                )
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.debtTypes.forEach { t ->
                        FilterChip(
                            selected = debtType == t,
                            onClick = { debtType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pagamento recorrente", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseAmountInput(amountText)
                    val paid = CurrencyUtils.parseAmountInput(paidText) ?: 0.0
                    when {
                        title.isBlank() -> errorMsg = "Indique a descrição."
                        counterparty.isBlank() -> errorMsg = "Indique a pessoa ou entidade."
                        amount == null || amount <= 0.0 -> errorMsg = "Valor total inválido."
                        paid < 0.0 || paid > amount -> errorMsg = "Valor pago inválido."
                        else -> onSave(
                            (initial ?: DebtItem()).copy(
                                title = title.trim(),
                                counterparty = counterparty.trim(),
                                amount = amount,
                                paidAmount = paid,
                                direction = direction,
                                debtType = debtType,
                                dueDateMillis = dueDateMillis,
                                isRecurring = isRecurring,
                                isSettled = paid >= amount,
                                notes = notes.trim()
                            )
                        )
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DebtPaymentDialog(
    debt: DebtItem,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registar Pagamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${debt.title} — Em falta: ${CurrencyUtils.formatKz(debt.remainingAmount)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor pago (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = CurrencyUtils.parseAmountInput(amountText)
                    if (v == null || v <= 0.0) errorMsg = "Indique um valor válido."
                    else onConfirm(v)
                }
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
