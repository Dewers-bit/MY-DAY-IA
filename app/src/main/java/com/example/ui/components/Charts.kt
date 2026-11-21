package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FinanceCatalog
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldIncome
import com.example.util.CurrencyUtils

@Composable
fun IncomeVsExpenseChart(
    incomeTotal: Double,
    expenseTotal: Double,
    prevIncomeTotal: Double,
    prevExpenseTotal: Double,
    modifier: Modifier = Modifier
) {
    val maxVal = maxOf(incomeTotal, expenseTotal, prevIncomeTotal, prevExpenseTotal, 1.0)
    val currIncRatio by animateFloatAsState(
        targetValue = (incomeTotal / maxVal).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "currInc"
    )
    val currExpRatio by animateFloatAsState(
        targetValue = (expenseTotal / maxVal).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "currExp"
    )
    val prevIncRatio by animateFloatAsState(
        targetValue = (prevIncomeTotal / maxVal).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "prevInc"
    )
    val prevExpRatio by animateFloatAsState(
        targetValue = (prevExpenseTotal / maxVal).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "prevExp"
    )

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val prevIncomeColor = EmeraldIncome.copy(alpha = 0.35f)
    val prevExpenseColor = CoralExpense.copy(alpha = 0.35f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            val h = size.height
            val w = size.width
            // Horizontal guide lines
            for (i in 0..3) {
                val y = h * (i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val barWidth = (w / 10f).coerceAtMost(32.dp.toPx())
            val gap = barWidth * 0.45f

            // Group 1: Mês Anterior
            val g1Center = w * 0.28f
            val prevIncH = (h * prevIncRatio).coerceAtLeast(if (prevIncomeTotal > 0) 6f else 0f)
            val prevExpH = (h * prevExpRatio).coerceAtLeast(if (prevExpenseTotal > 0) 6f else 0f)

            drawRoundRect(
                color = prevIncomeColor,
                topLeft = Offset(g1Center - barWidth - gap / 2, h - prevIncH),
                size = Size(barWidth, prevIncH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = prevExpenseColor,
                topLeft = Offset(g1Center + gap / 2, h - prevExpH),
                size = Size(barWidth, prevExpH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )

            // Group 2: Mês Actual
            val g2Center = w * 0.72f
            val currIncH = (h * currIncRatio).coerceAtLeast(if (incomeTotal > 0) 6f else 0f)
            val currExpH = (h * currExpRatio).coerceAtLeast(if (expenseTotal > 0) 6f else 0f)

            drawRoundRect(
                color = EmeraldIncome,
                topLeft = Offset(g2Center - barWidth - gap / 2, h - currIncH),
                size = Size(barWidth, currIncH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
                color = CoralExpense,
                topLeft = Offset(g2Center + gap / 2, h - currExpH),
                size = Size(barWidth, currExpH),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mês Anterior",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+${CurrencyUtils.formatCompactKz(prevIncomeTotal)} / -${CurrencyUtils.formatCompactKz(prevExpenseTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Mês Actual",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "+${CurrencyUtils.formatCompactKz(incomeTotal)} / -${CurrencyUtils.formatCompactKz(expenseTotal)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = EmeraldIncome, label = "Receitas")
            Spacer(modifier = Modifier.width(18.dp))
            LegendDot(color = CoralExpense, label = "Despesas")
        }
    }
}

@Composable
fun CategoryDonutChart(
    categoryTotals: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val sorted = categoryTotals.entries
        .filter { it.value > 0.0 }
        .sortedByDescending { it.value }
    val grandTotal = sorted.sumOf { it.value }

    if (sorted.isEmpty() || grandTotal <= 0.0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sem despesas registadas para este período.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val topSlices = sorted.take(6)
    val othersSum = sorted.drop(6).sumOf { it.value }
    val slices = if (othersSum > 0.0) {
        topSlices.map { it.key to it.value } + ("Outras categorias" to othersSum)
    } else {
        topSlices.map { it.key to it.value }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(128.dp)) {
                val strokeWidth = 22.dp.toPx()
                var startAngle = -90f
                val gapAngle = if (slices.size > 1) 3f else 0f
                slices.forEach { (cat, value) ->
                    val sweep = ((value / grandTotal).toFloat() * 360f - gapAngle).coerceAtLeast(4f)
                    drawArc(
                        color = FinanceCatalog.categoryColor(cat),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += (value / grandTotal).toFloat() * 360f
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyUtils.formatCompactKz(grandTotal),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { (cat, value) ->
                val pct = ((value / grandTotal) * 100).toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(FinanceCatalog.categoryColor(cat))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
