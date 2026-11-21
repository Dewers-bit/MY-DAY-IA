package com.example.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

object CurrencyUtils {
    private val ptLocale = Locale.forLanguageTag("pt-AO")
    private val symbols = DecimalFormatSymbols(ptLocale).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val integerFormatter = DecimalFormat("#,##0", symbols)
    private val decimalFormatter = DecimalFormat("#,##0.00", symbols)

    fun formatKz(amount: Double, showDecimals: Boolean = false): String {
        val formatted = if (showDecimals || abs(amount % 1.0) > 0.009) {
            decimalFormatter.format(amount)
        } else {
            integerFormatter.format(amount)
        }
        return "$formatted Kz"
    }

    fun formatCompactKz(amount: Double): String {
        val absVal = abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            absVal >= 1_000_000.0 -> {
                val m = absVal / 1_000_000.0
                "${sign}${String.format(ptLocale, "%.1f", m)}M Kz"
            }
            absVal >= 1_000.0 -> {
                val k = absVal / 1_000.0
                "${sign}${String.format(ptLocale, if (k % 1.0 == 0.0) "%.0f" else "%.1f", k)} mil Kz"
            }
            else -> formatKz(amount)
        }
    }

    fun parseAmountInput(raw: String): Double? {
        val cleaned = raw.trim()
            .replace("Kz", "", ignoreCase = true)
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        val normalized = if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned.replace(".", "").replace(",", ".")
        } else if (cleaned.contains(",")) {
            cleaned.replace(",", ".")
        } else {
            // Handle "2.500" as 2500 if 3 digits follow dot
            val parts = cleaned.split(".")
            if (parts.size == 2 && parts[1].length == 3) {
                cleaned.replace(".", "")
            } else if (parts.size > 2) {
                cleaned.replace(".", "")
            } else {
                cleaned
            }
        }
        return normalized.toDoubleOrNull()
    }
}

object DateUtilsPt {
    private val ptLocale = Locale.forLanguageTag("pt-PT")
    private val dayMonthFormat = SimpleDateFormat("dd MMM", ptLocale)
    private val fullDateFormat = SimpleDateFormat("dd/MM/yyyy", ptLocale)
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", ptLocale)
    private val weekdayDateFormat = SimpleDateFormat("EEEE, dd 'de' MMMM", ptLocale)

    fun formatShortDate(millis: Long): String = dayMonthFormat.format(Date(millis))

    fun formatFullDate(millis: Long): String = fullDateFormat.format(Date(millis))

    fun formatMonthYear(millis: Long): String {
        val text = monthYearFormat.format(Date(millis))
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptLocale) else it.toString() }
    }

    fun formatWeekdayDate(millis: Long): String {
        val text = weekdayDateFormat.format(Date(millis))
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptLocale) else it.toString() }
    }

    fun startOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfDayMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun startOfWeekMillis(timeMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun startOfMonthMillis(timeMillis: Long = System.currentTimeMillis(), monthOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfMonthMillis(timeMillis: Long = System.currentTimeMillis(), monthOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun startOfYearMillis(timeMillis: Long = System.currentTimeMillis(), yearOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.YEAR, yearOffset)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun endOfYearMillis(timeMillis: Long = System.currentTimeMillis(), yearOffset: Int = 0): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            add(Calendar.YEAR, yearOffset)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun daysRemainingInMonth(nowMillis: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)
        return (maxDay - currentDay + 1).coerceAtLeast(1)
    }

    fun daysUntil(targetMillis: Long, fromMillis: Long = System.currentTimeMillis()): Int {
        val diff = targetMillis - fromMillis
        return ceil(diff / 86_400_000.0).toInt()
    }

    fun isSameDay(aMillis: Long, bMillis: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = aMillis }
        val cb = Calendar.getInstance().apply { timeInMillis = bMillis }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    data class SalaryScheduleWindow(
        val currentCycleKey: String,
        val currentPaydayMillis: Long,
        val nextPaydayMillis: Long,
        val suggestedDescription: String
    )

    fun computeSalarySchedule(
        paydayOfMonth: Int,
        frequency: String,
        nowMillis: Long = System.currentTimeMillis()
    ): SalaryScheduleWindow {
        val safeDay = paydayOfMonth.coerceIn(1, 31)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val monthTitle = formatMonthYear(nowMillis)

        return when (frequency) {
            "Semanal" -> {
                val weekCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    firstDayOfWeek = Calendar.MONDAY
                    val targetDow = when ((safeDay - 1) % 7) {
                        0 -> Calendar.MONDAY
                        1 -> Calendar.TUESDAY
                        2 -> Calendar.WEDNESDAY
                        3 -> Calendar.THURSDAY
                        4 -> Calendar.FRIDAY
                        5 -> Calendar.SATURDAY
                        else -> Calendar.SUNDAY
                    }
                    set(Calendar.DAY_OF_WEEK, targetDow)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (weekCal.timeInMillis > endOfDayMillis(nowMillis)) {
                    weekCal.add(Calendar.DAY_OF_YEAR, -7)
                }
                val currentPay = weekCal.timeInMillis
                val nextPay = currentPay + 7L * 86_400_000L
                val wYear = weekCal.get(Calendar.YEAR)
                val wNum = weekCal.get(Calendar.WEEK_OF_YEAR)
                SalaryScheduleWindow(
                    currentCycleKey = String.format(Locale.US, "%04d-W%02d", wYear, wNum),
                    currentPaydayMillis = currentPay,
                    nextPaydayMillis = nextPay,
                    suggestedDescription = "Salário Semanal (S$wNum/$wYear)"
                )
            }
            "Quinzenal" -> {
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val d1 = safeDay.coerceAtMost(maxDay)
                val d2 = if (d1 <= 15) (d1 + 15).coerceAtMost(maxDay) else (d1 - 15).coerceAtLeast(1)
                val firstDay = minOf(d1, d2)
                val secondDay = maxOf(d1, d2)
                val todayDom = cal.get(Calendar.DAY_OF_MONTH)

                val (slot, slotDay) = if (todayDom >= secondDay) {
                    2 to secondDay
                } else {
                    1 to firstDay
                }

                val currentPayCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, slotDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val nextPayCal = if (todayDom < firstDay) {
                    Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        set(Calendar.DAY_OF_MONTH, firstDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                } else if (todayDom < secondDay) {
                    Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        set(Calendar.DAY_OF_MONTH, secondDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                } else {
                    Calendar.getInstance().apply {
                        timeInMillis = nowMillis
                        add(Calendar.MONTH, 1)
                        val nextMax = getActualMaximum(Calendar.DAY_OF_MONTH)
                        set(Calendar.DAY_OF_MONTH, firstDay.coerceAtMost(nextMax))
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }

                SalaryScheduleWindow(
                    currentCycleKey = String.format(Locale.US, "%04d-%02d-Q%d", year, month, slot),
                    currentPaydayMillis = currentPayCal.timeInMillis,
                    nextPaydayMillis = nextPayCal.timeInMillis,
                    suggestedDescription = "Salário (${slot}ª Quinzena) - $monthTitle"
                )
            }
            else -> {
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val clampedDay = safeDay.coerceAtMost(maxDay)
                val currentPayCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, clampedDay)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val nextPayCal = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    if (cal.get(Calendar.DAY_OF_MONTH) >= clampedDay) {
                        add(Calendar.MONTH, 1)
                    }
                    val nextMax = getActualMaximum(Calendar.DAY_OF_MONTH)
                    set(Calendar.DAY_OF_MONTH, safeDay.coerceAtMost(nextMax))
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                SalaryScheduleWindow(
                    currentCycleKey = String.format(Locale.US, "%04d-%02d", year, month),
                    currentPaydayMillis = currentPayCal.timeInMillis,
                    nextPaydayMillis = nextPayCal.timeInMillis,
                    suggestedDescription = "Salário - $monthTitle"
                )
            }
        }
    }
}
