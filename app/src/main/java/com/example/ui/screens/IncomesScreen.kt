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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
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
import com.example.data.model.FinanceCatalog
import com.example.data.model.Income
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.NavyDeep
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt

@Composable
fun IncomesScreen(
    incomes: List<Income>,
    openCreateDialogImmediately: Boolean = false,
    onConsumeCreateTrigger: () -> Unit = {},
    onSaveIncome: (Income) -> Unit,
    onDeleteIncome: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf<String?>(null) }
    var editingIncome by remember { mutableStateOf<Income?>(null) }
    var showEditor by remember(openCreateDialogImmediately) {
        mutableStateOf(openCreateDialogImmediately).also {
            if (openCreateDialogImmediately) onConsumeCreateTrigger()
        }
    }

    val filtered = remember(incomes, searchQuery, selectedSource) {
        incomes.filter { inc ->
            val matchesQuery = searchQuery.isBlank() ||
                inc.description.contains(searchQuery, ignoreCase = true) ||
                inc.source.contains(searchQuery, ignoreCase = true)
            val matchesSource = selectedSource == null || inc.source == selectedSource
            matchesQuery && matchesSource
        }
    }

    val totalFiltered = filtered.sumOf { it.amount }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingIncome = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_income_fab"),
                containerColor = EmeraldIncome,
                contentColor = NavyDeep
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nova receita")
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
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Pesquisar receitas...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedSource == null,
                            onClick = { selectedSource = null },
                            label = { Text("Todas as origens") }
                        )
                    }
                    items(FinanceCatalog.incomeSources) { src ->
                        FilterChip(
                            selected = selectedSource == src,
                            onClick = { selectedSource = if (selectedSource == src) null else src },
                            label = { Text(src) }
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
                                text = "Total de Receitas",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyUtils.formatKz(totalFiltered),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldIncome
                            )
                        }
                        StatusBadge(
                            text = "${filtered.size} entrada(s)",
                            containerColor = EmeraldIncome.copy(alpha = 0.14f),
                            contentColor = EmeraldIncome
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        title = "Sem receitas registadas",
                        subtitle = "Registe o seu salário, trabalhos de freelance, design ou negócios para controlar o saldo disponível.",
                        actionLabel = "Registar Receita",
                        onAction = {
                            editingIncome = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { inc ->
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
                                    .background(EmeraldIncome)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = inc.description,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${inc.source} • ${DateUtilsPt.formatFullDate(inc.dateMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                StatusBadge(
                                    text = inc.incomeType,
                                    containerColor = EmeraldIncome.copy(alpha = 0.14f),
                                    contentColor = EmeraldIncome
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+${CurrencyUtils.formatKz(inc.amount)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldIncome
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            editingIncome = inc
                                            showEditor = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteIncome(inc.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
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
            }
        }
    }

    if (showEditor) {
        IncomeEditorDialog(
            initial = editingIncome,
            onDismiss = { showEditor = false },
            onSave = { item ->
                onSaveIncome(item)
                showEditor = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IncomeEditorDialog(
    initial: Income?,
    onDismiss: () -> Unit,
    onSave: (Income) -> Unit
) {
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var amountText by remember {
        mutableStateOf(if (initial != null && initial.amount > 0) initial.amount.toLong().toString() else "")
    }
    var source by remember { mutableStateOf(initial?.source ?: FinanceCatalog.incomeSources.first()) }
    var incomeType by remember { mutableStateOf(initial?.incomeType ?: FinanceCatalog.incomeTypes.first()) }
    var dateMillis by remember { mutableLongStateOf(initial?.dateMillis ?: System.currentTimeMillis()) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nova Receita" else "Editar Receita") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição da receita") },
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

                Text("Origem da receita", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.incomeSources.forEach { src ->
                        FilterChip(
                            selected = source == src,
                            onClick = { source = src },
                            label = { Text(src, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Tipo de receita", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.incomeTypes.forEach { t ->
                        FilterChip(
                            selected = incomeType == t,
                            onClick = { incomeType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
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
                        description.isBlank() -> errorMsg = "Indique a descrição da receita."
                        amount == null || amount <= 0.0 -> errorMsg = "Indique um valor válido em Kz."
                        else -> onSave(
                            (initial ?: Income()).copy(
                                description = description.trim(),
                                amount = amount,
                                source = source,
                                dateMillis = dateMillis,
                                incomeType = incomeType,
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
