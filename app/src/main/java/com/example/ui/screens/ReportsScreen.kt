package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ReportPeriod
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.SectionCard
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.ui.viewmodel.MyDayDashboardData
import com.example.util.CurrencyUtils
import com.example.util.DateUtilsPt
import com.example.util.ReportExporter

@Composable
fun ReportsScreen(
    data: MyDayDashboardData,
    onExportFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(ReportPeriod.MONTH) }

    val now = System.currentTimeMillis()
    val (rangeStart, rangeEnd, prevStart, prevEnd, periodLabel) = remember(selectedPeriod, now) {
        when (selectedPeriod) {
            ReportPeriod.DAY -> {
                val s = DateUtilsPt.startOfDayMillis(now)
                val e = DateUtilsPt.endOfDayMillis(now)
                val ps = s - 86_400_000L
                val pe = e - 86_400_000L
                ReportWindow(s, e, ps, pe, DateUtilsPt.formatFullDate(now))
            }
            ReportPeriod.WEEK -> {
                val s = DateUtilsPt.startOfWeekMillis(now)
                val e = DateUtilsPt.endOfDayMillis(now)
                val ps = s - 7L * 86_400_000L
                val pe = s - 1L
                ReportWindow(s, e, ps, pe, "Semana Actual")
            }
            ReportPeriod.MONTH -> {
                val s = DateUtilsPt.startOfMonthMillis(now, 0)
                val e = DateUtilsPt.endOfMonthMillis(now, 0)
                val ps = DateUtilsPt.startOfMonthMillis(now, -1)
                val pe = DateUtilsPt.endOfMonthMillis(now, -1)
                ReportWindow(s, e, ps, pe, DateUtilsPt.formatMonthYear(now))
            }
            ReportPeriod.YEAR -> {
                val s = DateUtilsPt.startOfYearMillis(now, 0)
                val e = DateUtilsPt.endOfYearMillis(now, 0)
                val ps = DateUtilsPt.startOfYearMillis(now, -1)
                val pe = DateUtilsPt.endOfYearMillis(now, -1)
                ReportWindow(s, e, ps, pe, "Ano Actual")
            }
        }
    }

    val periodIncomes = remember(data.incomes, rangeStart, rangeEnd) {
        data.incomes.filter { it.dateMillis in rangeStart..rangeEnd }
    }
    val periodExpenses = remember(data.expenses, rangeStart, rangeEnd) {
        data.expenses.filter { it.dateMillis in rangeStart..rangeEnd }
    }
    val prevIncomes = remember(data.incomes, prevStart, prevEnd) {
        data.incomes.filter { it.dateMillis in prevStart..prevEnd }
    }
    val prevExpenses = remember(data.expenses, prevStart, prevEnd) {
        data.expenses.filter { it.dateMillis in prevStart..prevEnd }
    }

    val totalInc = periodIncomes.sumOf { it.amount }
    val totalExp = periodExpenses.sumOf { it.amount }
    val netBalance = totalInc - totalExp
    val prevIncTotal = prevIncomes.sumOf { it.amount }
    val prevExpTotal = prevExpenses.sumOf { it.amount }

    val categoryMap = remember(periodExpenses) {
        periodExpenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            ReportExporter.writePdfReport(
                context = context,
                uri = uri,
                period = selectedPeriod,
                periodLabel = periodLabel,
                incomes = periodIncomes,
                expenses = periodExpenses,
                savingsGoals = data.savingsGoals
            ).onSuccess {
                onExportFeedback("Relatório PDF exportado com sucesso!")
            }.onFailure {
                onExportFeedback("Erro ao exportar PDF: ${it.localizedMessage}")
            }
        }
    }

    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            ReportExporter.writeExcelCsvReport(
                context = context,
                uri = uri,
                period = selectedPeriod,
                periodLabel = periodLabel,
                incomes = periodIncomes,
                expenses = periodExpenses,
                savingsGoals = data.savingsGoals
            ).onSuccess {
                onExportFeedback("Relatório Excel (CSV) exportado com sucesso!")
            }.onFailure {
                onExportFeedback("Erro ao exportar Excel: ${it.localizedMessage}")
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReportKpiCard(
                    label = "Receitas",
                    value = CurrencyUtils.formatCompactKz(totalInc),
                    color = EmeraldIncome,
                    modifier = Modifier.weight(1f)
                )
                ReportKpiCard(
                    label = "Despesas",
                    value = CurrencyUtils.formatCompactKz(totalExp),
                    color = CoralExpense,
                    modifier = Modifier.weight(1f)
                )
                ReportKpiCard(
                    label = "Saldo Líquido",
                    value = CurrencyUtils.formatCompactKz(netBalance),
                    color = if (netBalance >= 0) EmeraldIncome else CoralExpense,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionCard(title = "Comparação com Período Anterior") {
                val expDiff = totalExp - prevExpTotal
                val incDiff = totalInc - prevIncTotal
                Text(
                    text = "Receitas: ${CurrencyUtils.formatKz(totalInc)} vs ${CurrencyUtils.formatKz(prevIncTotal)} (${if (incDiff >= 0) "+" else ""}${CurrencyUtils.formatCompactKz(incDiff)})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Despesas: ${CurrencyUtils.formatKz(totalExp)} vs ${CurrencyUtils.formatKz(prevExpTotal)} (${if (expDiff >= 0) "+" else ""}${CurrencyUtils.formatCompactKz(expDiff)})",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionCard(title = "Categorias com Maiores Gastos ($periodLabel)") {
                CategoryDonutChart(categoryTotals = categoryMap)
            }
        }

        item {
            SectionCard(title = "Evolução das Poupanças") {
                if (data.savingsGoals.isEmpty()) {
                    Text(
                        text = "Sem objectivos de poupança registados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    data.savingsGoals.forEach { goal ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(goal.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${CurrencyUtils.formatCompactKz(goal.savedAmount)} / ${CurrencyUtils.formatCompactKz(goal.targetAmount)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldIncome
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { goal.progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = EmeraldIncome,
                            strokeCap = StrokeCap.Round
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        item {
            SectionCard(title = "Exportar Relatório") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { pdfLauncher.launch("MyDayIA_Relatorio_${selectedPeriod.label}.pdf") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_pdf_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = { excelLauncher.launch("MyDayIA_Relatorio_${selectedPeriod.label}.csv") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_excel_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excel (CSV)")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ficheiros guardados directamente via Android Storage Access Framework.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private data class ReportWindow(
    val start: Long,
    val end: Long,
    val prevStart: Long,
    val prevEnd: Long,
    val label: String
)

@Composable
private fun ReportKpiCard(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
