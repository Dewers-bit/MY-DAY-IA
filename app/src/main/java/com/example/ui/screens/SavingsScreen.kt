package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.data.model.SavingsGoal
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.EmeraldIncome
import com.example.ui.theme.NavyDeep
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt

@Composable
fun SavingsScreen(
    goals: List<SavingsGoal>,
    onSaveGoal: (SavingsGoal) -> Unit,
    onAddContribution: (SavingsGoal, Double) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var contributeGoal by remember { mutableStateOf<SavingsGoal?>(null) }

    val totalSaved = goals.sumOf { it.savedAmount }
    val totalTarget = goals.sumOf { it.targetAmount }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingGoal = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_goal_fab"),
                containerColor = EmeraldIncome,
                contentColor = NavyDeep
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Novo objectivo")
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Total Poupado em Objectivos",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyUtils.formatKz(totalSaved),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldIncome
                        )
                        if (totalTarget > 0) {
                            Text(
                                text = "Meta global combinada: ${CurrencyUtils.formatKz(totalTarget)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (goals.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Savings,
                        title = "Sem objectivos de poupança",
                        subtitle = "Crie metas como Reserva de emergência, Comprar computador, Negócio ou Viagem e acompanhe quanto poupar por dia ou mês.",
                        actionLabel = "Criar Objectivo",
                        onAction = {
                            editingGoal = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(goals, key = { it.id }) { goal ->
                    SavingsGoalCard(
                        goal = goal,
                        onContribute = { contributeGoal = goal },
                        onEdit = {
                            editingGoal = goal
                            showEditor = true
                        },
                        onDelete = { onDeleteGoal(goal.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        SavingsGoalDialog(
            initial = editingGoal,
            onDismiss = { showEditor = false },
            onSave = {
                onSaveGoal(it)
                showEditor = false
            }
        )
    }

    contributeGoal?.let { targetGoal ->
        ContributionDialog(
            goal = targetGoal,
            onDismiss = { contributeGoal = null },
            onConfirm = { amount ->
                onAddContribution(targetGoal, amount)
                contributeGoal = null
            }
        )
    }
}

@Composable
private fun SavingsGoalCard(
    goal: SavingsGoal,
    onContribute: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = (goal.progressRatio * 100).toInt()
    val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)
    val daysLeft = DateUtilsPt.daysUntil(goal.deadlineMillis).coerceAtLeast(1)
    val dailyNeed = remaining / daysLeft
    val weeklyNeed = remaining / (daysLeft / 7.0).coerceAtLeast(1.0)
    val monthlyNeed = remaining / (daysLeft / 30.0).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Prazo: ${DateUtilsPt.formatFullDate(goal.deadlineMillis)} ($daysLeft dias)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(
                    text = "$pct%",
                    containerColor = EmeraldIncome.copy(alpha = 0.16f),
                    contentColor = EmeraldIncome
                )
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

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { goal.progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50)),
                color = EmeraldIncome,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Poupado: ${CurrencyUtils.formatKz(goal.savedAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Meta: ${CurrencyUtils.formatKz(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (remaining > 0.0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Ritmo necessário para atingir a meta:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${CurrencyUtils.formatCompactKz(dailyNeed)}/dia • ${CurrencyUtils.formatCompactKz(weeklyNeed)}/sem • ${CurrencyUtils.formatCompactKz(monthlyNeed)}/mês",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onContribute,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reforçar Poupança")
            }
        }
    }
}

@Composable
private fun SavingsGoalDialog(
    initial: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var targetText by remember {
        mutableStateOf(if (initial != null && initial.targetAmount > 0) initial.targetAmount.toLong().toString() else "")
    }
    var savedText by remember {
        mutableStateOf(if (initial != null && initial.savedAmount > 0) initial.savedAmount.toLong().toString() else "0")
    }
    var deadlineMillis by remember {
        mutableLongStateOf(initial?.deadlineMillis ?: (System.currentTimeMillis() + 86_400_000L * 90))
    }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo Objectivo de Poupança" else "Editar Objectivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do objectivo (ex: Portátil, Reserva)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Valor pretendido (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = savedText,
                    onValueChange = { savedText = it },
                    label = { Text("Valor já poupado (Kz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Prazo",
                    dateMillis = deadlineMillis,
                    onDateSelected = { deadlineMillis = it }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (opcional)") },
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
                    val target = CurrencyUtils.parseAmountInput(targetText)
                    val saved = CurrencyUtils.parseAmountInput(savedText) ?: 0.0
                    when {
                        name.isBlank() -> errorMsg = "Indique o nome do objectivo."
                        target == null || target <= 0.0 -> errorMsg = "Indique um valor pretendido válido."
                        saved < 0.0 -> errorMsg = "Valor poupado não pode ser negativo."
                        else -> onSave(
                            (initial ?: SavingsGoal()).copy(
                                name = name.trim(),
                                targetAmount = target,
                                savedAmount = saved,
                                deadlineMillis = deadlineMillis,
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
private fun ContributionDialog(
    goal: SavingsGoal,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reforçar Poupança") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Adicionar valor a \"${goal.name}\"", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor a adicionar (Kz)") },
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
            ) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
