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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Expense
import com.example.data.model.FinanceCatalog
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt
import com.example.util.SalaryTrackingSummary

@Composable
fun ExpensesScreen(
    expenses: List<Expense>,
    salarySummary: SalaryTrackingSummary? = null,
    openCreateDialogImmediately: Boolean = false,
    onConsumeCreateTrigger: () -> Unit = {},
    onSaveExpense: (Expense) -> Unit,
    onDeleteExpense: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var fixedFilter by remember { mutableStateOf<Boolean?>(null) } // null = todas
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var showEditor by remember(openCreateDialogImmediately) {
        mutableStateOf(openCreateDialogImmediately).also {
            if (openCreateDialogImmediately) onConsumeCreateTrigger()
        }
    }

    val filtered = remember(expenses, searchQuery, selectedCategory, fixedFilter) {
        expenses.filter { e ->
            val matchesQuery = searchQuery.isBlank() ||
                e.description.contains(searchQuery, ignoreCase = true) ||
                e.category.contains(searchQuery, ignoreCase = true) ||
                e.notes.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCategory == null || e.category == selectedCategory
            val matchesType = fixedFilter == null || e.isFixed == fixedFilter
            matchesQuery && matchesCat && matchesType
        }
    }

    val totalFiltered = filtered.sumOf { it.amount }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingExpense = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_expense_fab"),
                containerColor = CoralExpense
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nova despesa")
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_expenses_input"),
                    placeholder = { Text("Pesquisar despesas ou observações...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = fixedFilter == null,
                            onClick = { fixedFilter = null },
                            label = { Text("Todas") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = fixedFilter == true,
                            onClick = { fixedFilter = if (fixedFilter == true) null else true },
                            label = { Text("Fixas") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = fixedFilter == false,
                            onClick = { fixedFilter = if (fixedFilter == false) null else false },
                            label = { Text("Variáveis") }
                        )
                    }
                    items(FinanceCatalog.expenseCategories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Filtrado",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatKz(totalFiltered),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = CoralExpense
                            )
                        }
                        StatusBadge(
                            text = "${filtered.size} registo(s)",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                        title = "Nenhuma despesa encontrada",
                        subtitle = "Registe as suas despesas para acompanhar categorias, limites e receber análises da IA.",
                        actionLabel = "Registar Despesa",
                        onAction = {
                            editingExpense = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { expense ->
                    ExpenseCard(
                        expense = expense,
                        onEdit = {
                            editingExpense = expense
                            showEditor = true
                        },
                        onDelete = { onDeleteExpense(expense.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        ExpenseEditorDialog(
            initial = editingExpense,
            salarySummary = salarySummary,
            onDismiss = { showEditor = false },
            onSave = { item ->
                onSaveExpense(item)
                showEditor = false
            }
        )
    }
}

@Composable
private fun ExpenseCard(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(FinanceCatalog.categoryColor(expense.category))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${expense.category} • ${expense.paymentMethod} • ${DateUtilsPt.formatFullDate(expense.dateMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (expense.notes.isNotBlank()) {
                    Text(
                        text = expense.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        text = if (expense.isFixed) "Fixa" else "Variável",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (expense.reservedCategory.isNotBlank()) {
                        StatusBadge(
                            text = "Reserva: ${expense.reservedCategory}",
                            containerColor = EmeraldIncome.copy(alpha = 0.16f),
                            contentColor = EmeraldIncome
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "-${CurrencyUtils.formatKz(expense.amount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CoralExpense
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditorDialog(
    initial: Expense?,
    salarySummary: SalaryTrackingSummary? = null,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var amountText by remember {
        mutableStateOf(if (initial != null && initial.amount > 0) initial.amount.toLong().toString() else "")
    }
    var category by remember { mutableStateOf(initial?.category ?: FinanceCatalog.expenseCategories.first()) }
    var paymentMethod by remember { mutableStateOf(initial?.paymentMethod ?: FinanceCatalog.paymentMethods.first()) }
    var dateMillis by remember { mutableLongStateOf(initial?.dateMillis ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isFixed by remember { mutableStateOf(initial?.isFixed ?: false) }
    var selectedSalaryCycleId by remember { mutableStateOf(initial?.salaryCycleId ?: "") }
    var selectedReservedCategory by remember { mutableStateOf(initial?.reservedCategory ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val activeCycle = salarySummary?.activeCycle
    val reserveOptions = remember(salarySummary, initial) {
        val base = salarySummary?.allocationBreakdowns?.filter {
            it.remainingReserve > 0.0 || it.category == initial?.reservedCategory
        } ?: emptyList()
        base
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova Despesa" else "Editar Despesa") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Nome ou descrição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Data",
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )

                Text("Categoria", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.expenseCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (activeCycle != null && reserveOptions.isNotEmpty()) {
                    Text("Fonte de pagamento (Salário)", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedReservedCategory.isBlank(),
                            onClick = {
                                selectedSalaryCycleId = ""
                                selectedReservedCategory = ""
                            },
                            label = { Text("Dinheiro disponível", style = MaterialTheme.typography.labelSmall) }
                        )
                        reserveOptions.forEach { alloc ->
                            FilterChip(
                                selected = selectedReservedCategory == alloc.category,
                                onClick = {
                                    selectedSalaryCycleId = if (initial?.salaryCycleId?.isNotBlank() == true && initial.reservedCategory == alloc.category) {
                                        initial.salaryCycleId
                                    } else {
                                        activeCycle.id
                                    }
                                    selectedReservedCategory = alloc.category
                                },
                                label = {
                                    Text(
                                        "${alloc.category} (${CurrencyUtils.formatCompactKz(alloc.remainingReserve)})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }

                Text("Forma de pagamento", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.paymentMethods.forEach { method ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method },
                            label = { Text(method, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Despesa fixa mensal", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isFixed, onCheckedChange = { isFixed = it })
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações (opcional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = CurrencyUtils.parseAmountInput(amountText)
                    when {
                        description.isBlank() -> errorMsg = "Indique a descrição da despesa."
                        amount == null || amount <= 0.0 -> errorMsg = "Indique um valor válido em Kz."
                        else -> onSave(
                            (initial ?: Expense()).copy(
                                description = description.trim(),
                                amount = amount,
                                category = category,
                                dateMillis = dateMillis,
                                paymentMethod = paymentMethod,
                                notes = notes.trim(),
                                isFixed = isFixed,
                                salaryCycleId = if (selectedReservedCategory.isNotBlank()) selectedSalaryCycleId else "",
                                reservedCategory = selectedReservedCategory
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
