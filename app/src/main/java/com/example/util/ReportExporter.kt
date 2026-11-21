package com.example.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.data.model.Expense
import com.example.data.model.Income
import com.example.data.model.ReportPeriod
import com.example.data.model.SavingsGoal
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ReportExporter {

    fun writePdfReport(
        context: Context,
        uri: Uri,
        period: ReportPeriod,
        periodLabel: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        savingsGoals: List<SavingsGoal>
    ): Result<Unit> = runCatching {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.rgb(11, 18, 32)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(100, 116, 139)
            textSize = 11f
        }
        val sectionPaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        var y = 48f
        val left = 40f
        val right = 555f

        fun ensureSpace(needed: Float = 24f) {
            if (y + needed > 800f) {
                document.finishPage(page)
                val nextInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
                page = document.startPage(nextInfo)
                canvas = page.canvas
                y = 48f
            }
        }

        canvas.drawText("My Day iA — Relatório Financeiro", left, y, titlePaint)
        y += 18f
        canvas.drawText("Período (${period.label}): $periodLabel", left, y, subtitlePaint)
        y += 12f
        canvas.drawLine(left, y, right, y, linePaint)
        y += 22f

        val totalIncome = incomes.sumOf { it.amount }
        val totalExpense = expenses.sumOf { it.amount }
        val netBalance = totalIncome - totalExpense

        canvas.drawText("Resumo Executivo", left, y, sectionPaint)
        y += 18f
        canvas.drawText("Total de Receitas: ${CurrencyUtils.formatKz(totalIncome)}", left, y, bodyPaint)
        y += 16f
        canvas.drawText("Total de Despesas: ${CurrencyUtils.formatKz(totalExpense)}", left, y, bodyPaint)
        y += 16f
        canvas.drawText("Saldo Líquido: ${CurrencyUtils.formatKz(netBalance)}", left, y, bodyPaint)
        y += 24f

        canvas.drawText("Despesas por Categoria", left, y, sectionPaint)
        y += 18f
        val categories = expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries.sortedByDescending { it.value }
        if (categories.isEmpty()) {
            canvas.drawText("Sem despesas neste período.", left, y, subtitlePaint)
            y += 16f
        } else {
            for (cat in categories) {
                ensureSpace(20f)
                val pct = if (totalExpense > 0) (cat.value / totalExpense * 100).toInt() else 0
                canvas.drawText("${cat.key}: ${CurrencyUtils.formatKz(cat.value)} ($pct%)", left, y, bodyPaint)
                y += 16f
            }
        }

        y += 14f
        ensureSpace(36f)
        canvas.drawText("Movimentações de Despesas (${expenses.size})", left, y, sectionPaint)
        y += 18f
        if (expenses.isEmpty()) {
            canvas.drawText("Nenhuma despesa registada.", left, y, subtitlePaint)
            y += 16f
        } else {
            for (exp in expenses.take(60)) {
                ensureSpace(18f)
                val line = "${DateUtilsPt.formatFullDate(exp.dateMillis)} • ${exp.description.take(28)} (${exp.category}) — ${CurrencyUtils.formatKz(exp.amount)}"
                canvas.drawText(line, left, y, bodyPaint)
                y += 15f
            }
        }

        y += 14f
        ensureSpace(36f)
        canvas.drawText("Movimentações de Receitas (${incomes.size})", left, y, sectionPaint)
        y += 18f
        if (incomes.isEmpty()) {
            canvas.drawText("Nenhuma receita registada.", left, y, subtitlePaint)
            y += 16f
        } else {
            for (inc in incomes.take(60)) {
                ensureSpace(18f)
                val line = "${DateUtilsPt.formatFullDate(inc.dateMillis)} • ${inc.description.take(28)} (${inc.source}) — ${CurrencyUtils.formatKz(inc.amount)}"
                canvas.drawText(line, left, y, bodyPaint)
                y += 15f
            }
        }

        y += 14f
        ensureSpace(36f)
        canvas.drawText("Evolução das Poupanças (${savingsGoals.size})", left, y, sectionPaint)
        y += 18f
        if (savingsGoals.isEmpty()) {
            canvas.drawText("Nenhum objectivo de poupança definido.", left, y, subtitlePaint)
        } else {
            for (goal in savingsGoals) {
                ensureSpace(18f)
                val pct = (goal.progressRatio * 100).toInt()
                val line = "${goal.name}: ${CurrencyUtils.formatKz(goal.savedAmount)} / ${CurrencyUtils.formatKz(goal.targetAmount)} ($pct%)"
                canvas.drawText(line, left, y, bodyPaint)
                y += 15f
            }
        }

        document.finishPage(page)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            document.writeTo(out)
        } ?: error("Não foi possível abrir o ficheiro de destino.")
        document.close()
    }

    fun writeExcelCsvReport(
        context: Context,
        uri: Uri,
        period: ReportPeriod,
        periodLabel: String,
        incomes: List<Income>,
        expenses: List<Expense>,
        savingsGoals: List<SavingsGoal>
    ): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            // UTF-8 BOM so Microsoft Excel opens Portuguese accents cleanly
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                writer.appendLine("My Day iA - Relatório Financeiro;Período: ${period.label};$periodLabel")
                writer.appendLine()
                val totalIncome = incomes.sumOf { it.amount }
                val totalExpense = expenses.sumOf { it.amount }
                writer.appendLine("Tipo;Data;Descrição;Categoria/Origem;Pagamento/Tipo;Valor (Kz);Fixo/Observações")
                for (inc in incomes) {
                    writer.appendLine(
                        listOf(
                            "Receita",
                            DateUtilsPt.formatFullDate(inc.dateMillis),
                            csvSafe(inc.description),
                            csvSafe(inc.source),
                            csvSafe(inc.incomeType),
                            String.format("%.2f", inc.amount),
                            csvSafe(inc.notes)
                        ).joinToString(";")
                    )
                }
                for (exp in expenses) {
                    writer.appendLine(
                        listOf(
                            "Despesa",
                            DateUtilsPt.formatFullDate(exp.dateMillis),
                            csvSafe(exp.description),
                            csvSafe(exp.category),
                            csvSafe(exp.paymentMethod),
                            String.format("-%.2f", exp.amount),
                            csvSafe(if (exp.isFixed) "Fixa - ${exp.notes}" else exp.notes)
                        ).joinToString(";")
                    )
                }
                writer.appendLine()
                writer.appendLine("Resumo;Total Receitas;${String.format("%.2f", totalIncome)}")
                writer.appendLine("Resumo;Total Despesas;${String.format("%.2f", totalExpense)}")
                writer.appendLine("Resumo;Saldo Líquido;${String.format("%.2f", totalIncome - totalExpense)}")
                writer.appendLine()
                writer.appendLine("Objectivos de Poupança;Poupado (Kz);Meta (Kz);Progresso")
                for (g in savingsGoals) {
                    writer.appendLine(
                        "${csvSafe(g.name)};${String.format("%.2f", g.savedAmount)};${String.format("%.2f", g.targetAmount)};${(g.progressRatio * 100).toInt()}%"
                    )
                }
            }
        } ?: error("Não foi possível abrir o ficheiro de destino.")
    }

    private fun csvSafe(value: String): String {
        val cleaned = value.replace("\"", "'").replace("\n", " ").replace(";", ",")
        return "\"$cleaned\""
    }
}
