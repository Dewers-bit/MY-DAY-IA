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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryBudget
import com.example.data.model.FinanceCatalog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.viewmodel.BudgetAlertLevel
import com.example.ui.viewmodel.MyDayDashboardData
import com.example.util.CurrencyUtils

@Composable
fun BudgetScreen(
    data: MyDayDashboardData,
    onUpdateGlobalLimit: (Double) -> Unit,
    onSaveCategoryBudget: (CategoryBudget) -> Unit,
    onDeleteCategoryBudget: (String) -> Unit
) {
    var showGlobalDialog by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudget?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCategoryBudget = null
                    showCategoryDialog = true
                },
                modifier = Modifier.testTag("add_category_budget_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Definir limite por categoria")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Global Monthly Budget
            item {
                SectionCard(
                    title = "Orçamento Mensal Global",
                    trailing = {
                        OutlinedButton(onClick = { showGlobalDialog = true }) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (data.globalBudgetLimit > 0) "Ajustar" else "Definir")
                        }
                    }
                ) {
                    val limit = data.globalBudgetLimit
                    val spent = data.monthExpenseTotal
                    val available = (limit - spent).coerceAtLeast(0.0)
                    val pct = if (limit > 0) (spent / limit * 100).toInt() else 0

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Gasto este mês: ${CurrencyUtils.formatKz(spent)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (limit > 0) {
                                    "Disponível: ${CurrencyUtils.formatKz(available)} de ${CurrencyUtils.formatKz(limit)}"
                                } else {
                                    "Defina um limite mensal para activar alertas de 70%, 85% e 100%."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (data.globalAlertLevel != null) {
                            val c = alertColor(data.globalAlertLevel)
                            StatusBadge(
                                text = "$pct% • ${data.globalAlertLevel.label}",
                                containerColor = c.copy(alpha = 0.16f),
                                contentColor = c
                            )
                        }
                    }

                    if (limit > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { data.globalBudgetUsedRatio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = alertBarColor(data.globalBudgetUsedRatio),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            // Category Limits List
            item {
                Text(
                    text = "Limites por Categoria",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (data.budgetProgressItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Tune,
                        title = "Sem limites por categoria",
                        subtitle = "Defina limites individuais (ex: Alimentação, Transporte, Lazer) para receber alertas automáticos aos 70%, 85% e 100%.",
                        actionLabel = "Adicionar Limite",
                        onAction = {
                            editingCategoryBudget = null
                            showCategoryDialog = true
                        }
                    )
                }
            } else {
                items(data.budgetProgressItems, key = { it.category }) { item ->
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
                                Text(
                                    text = item.category,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.alertLevel != null) {
                                    val c = alertColor(item.alertLevel)
                                    StatusBadge(
                                        text = "${(item.ratio * 100).toInt()}% • ${item.alertLevel.label}",
                                        containerColor = c.copy(alpha = 0.16f),
                                        contentColor = c
                                    )
                                }
                                if (item.budget != null) {
                                    IconButton(
                                        onClick = {
                                            editingCategoryBudget = item.budget
                                            showCategoryDialog = true
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteCategoryBudget(item.budget.id) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Remover",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    TextButton(
                                        onClick = {
                                            editingCategoryBudget = CategoryBudget(category = item.category)
                                            showCategoryDialog = true
                                        }
                                    ) {
                                        Text("Definir limite")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Gasto: ${CurrencyUtils.formatKz(item.spentThisMonth)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (item.limit > 0) {
                                    val avail = item.limit - item.spentThisMonth
                                    Text(
                                        text = if (avail >= 0) {
                                            "Disponível: ${CurrencyUtils.formatKz(avail)}"
                                        } else {
                                            "Excedido em ${CurrencyUtils.formatKz(-avail)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (avail >= 0) EmeraldIncome else CoralExpense,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (item.limit > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { item.ratio.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50)),
                                    color = alertBarColor(item.ratio),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Limite mensal: ${CurrencyUtils.formatKz(item.limit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGlobalDialog) {
        GlobalBudgetDialog(
            currentLimit = data.globalBudgetLimit,
            onDismiss = { showGlobalDialog = false },
            onSave = {
                onUpdateGlobalLimit(it)
                showGlobalDialog = false
            }
        )
    }

    if (showCategoryDialog) {
        CategoryBudgetDialog(
            initial = editingCategoryBudget,
            existingBudgets = data.categoryBudgets,
            onDismiss = { showCategoryDialog = false },
            onSave = {
                onSaveCategoryBudget(it)
                showCategoryDialog = false
            }
        )
    }
}

private fun alertColor(level: BudgetAlertLevel) = when (level) {
    BudgetAlertLevel.EXCEEDED_100 -> CoralExpense
    BudgetAlertLevel.HIGH_85 -> AmberAlert
    BudgetAlertLevel.WARNING_70 -> AmberAlert
}

private fun alertBarColor(ratio: Float) = when {
    ratio >= 1.0f -> CoralExpense
    ratio >= 0.70f -> AmberAlert
    else -> EmeraldIncome
}

@Composable
private fun GlobalBudgetDialog(
    currentLimit: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var limitText by remember {
        mutableStateOf(if (currentLimit > 0) currentLimit.toLong().toString() else "")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Orçamento Mensal Global") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Limite mensal total (Kz)") },
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
                    val v = CurrencyUtils.parseAmountInput(limitText)
                    if (v == null || v <= 0.0) {
                        errorMsg = "Indique um valor maior que zero."
                    } else {
                        onSave(v)
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBudgetDialog(
    initial: CategoryBudget?,
    existingBudgets: List<CategoryBudget>,
    onDismiss: () -> Unit,
    onSave: (CategoryBudget) -> Unit
) {
    var category by remember { mutableStateOf(initial?.category ?: FinanceCatalog.expenseCategories.first()) }
    var limitText by remember {
        mutableStateOf(if (initial != null && initial.monthlyLimit > 0) initial.monthlyLimit.toLong().toString() else "")
    }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial?.id?.isNotBlank() == true) "Editar Limite" else "Novo Limite por Categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Limite mensal (Kz)") },
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
                    val v = CurrencyUtils.parseAmountInput(limitText)
                    if (v == null || v <= 0.0) {
                        errorMsg = "Indique um valor maior que zero."
                    } else {
                        val existingId = initial?.id?.takeIf { it.isNotBlank() }
                            ?: existingBudgets.firstOrNull { it.category == category }?.id
                            ?: ""
                        onSave(
                            CategoryBudget(
                                id = existingId,
                                category = category,
                                monthlyLimit = v
                            )
                        )
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
