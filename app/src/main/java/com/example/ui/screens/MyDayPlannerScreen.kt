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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.DailyTask
import com.example.data.model.FinanceCatalog
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.DateUtilsPt

@Composable
fun MyDayPlannerScreen(
    tasks: List<DailyTask>,
    onSaveTask: (DailyTask) -> Unit,
    onToggleCompleted: (DailyTask) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    var selectedDateMillis by remember { mutableLongStateOf(DateUtilsPt.startOfDayMillis()) }
    var filterMode by remember { mutableStateOf("DAY") } // DAY, PENDING, COMPLETED, ALL
    var editingTask by remember { mutableStateOf<DailyTask?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val filtered = remember(tasks, selectedDateMillis, filterMode) {
        when (filterMode) {
            "DAY" -> tasks.filter { DateUtilsPt.isSameDay(it.dateMillis, selectedDateMillis) }
            "PENDING" -> tasks.filter { !it.isCompleted }
            "COMPLETED" -> tasks.filter { it.isCompleted }
            else -> tasks
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showEditor = true
                },
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Nova tarefa ou compromisso")
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
                DatePickerField(
                    label = "Calendário",
                    dateMillis = selectedDateMillis,
                    onDateSelected = {
                        selectedDateMillis = it
                        filterMode = "DAY"
                    }
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filterMode == "DAY", onClick = { filterMode = "DAY" }, label = { Text("Dia") })
                    FilterChip(selected = filterMode == "PENDING", onClick = { filterMode = "PENDING" }, label = { Text("Pendentes") })
                    FilterChip(selected = filterMode == "COMPLETED", onClick = { filterMode = "COMPLETED" }, label = { Text("Concluídas") })
                    FilterChip(selected = filterMode == "ALL", onClick = { filterMode = "ALL" }, label = { Text("Todas") })
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.EventAvailable,
                        title = "Dia livre de registos",
                        subtitle = "Adicione tarefas, compromissos ou lembretes para manter o foco diário.",
                        actionLabel = "Adicionar Item",
                        onAction = {
                            editingTask = null
                            showEditor = true
                        }
                    )
                }
            } else {
                items(filtered, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { onToggleCompleted(task) },
                        onEdit = {
                            editingTask = task
                            showEditor = true
                        },
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            initial = editingTask,
            defaultDateMillis = selectedDateMillis,
            onDismiss = { showEditor = false },
            onSave = {
                onSaveTask(it)
                showEditor = false
            }
        )
    }
}

@Composable
private fun TaskCard(
    task: DailyTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val priorityColor = when (task.priority) {
        DailyTask.PRIORITY_HIGH -> CoralExpense
        DailyTask.PRIORITY_MEDIUM -> AmberAlert
        else -> EmeraldIncome
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                val timePart = if (task.timeLabel.isNotBlank()) " • ${task.timeLabel}" else ""
                Text(
                    text = "${DateUtilsPt.formatFullDate(task.dateMillis)}$timePart",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        text = task.priority,
                        containerColor = priorityColor.copy(alpha = 0.14f),
                        contentColor = priorityColor
                    )
                    StatusBadge(
                        text = task.itemType,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorDialog(
    initial: DailyTask?,
    defaultDateMillis: Long,
    onDismiss: () -> Unit,
    onSave: (DailyTask) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var dateMillis by remember { mutableLongStateOf(initial?.dateMillis ?: defaultDateMillis) }
    var priority by remember { mutableStateOf(initial?.priority ?: DailyTask.PRIORITY_MEDIUM) }
    var itemType by remember { mutableStateOf(initial?.itemType ?: DailyTask.TYPE_TASK) }
    var timeLabel by remember { mutableStateOf(initial?.timeLabel ?: "") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo Item no My Day" else "Editar Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DatePickerField(
                    label = "Data",
                    dateMillis = dateMillis,
                    onDateSelected = { dateMillis = it }
                )
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("Hora (ex: 09:30, opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Prioridade", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.taskPriorities.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text("Tipo", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinanceCatalog.taskTypes.forEach { t ->
                        FilterChip(
                            selected = itemType == t,
                            onClick = { itemType = t },
                            label = { Text(t, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / lembrete (opcional)") },
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
                    if (title.isBlank()) {
                        errorMsg = "Indique o título."
                    } else {
                        onSave(
                            (initial ?: DailyTask()).copy(
                                title = title.trim(),
                                notes = notes.trim(),
                                dateMillis = dateMillis,
                                priority = priority,
                                itemType = itemType,
                                timeLabel = timeLabel.trim()
                            )
                        )
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
