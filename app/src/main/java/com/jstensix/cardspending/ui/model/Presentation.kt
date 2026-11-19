package com.jstensix.cardspending.ui.model

import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import com.jstensix.cardspending.rules.TierProgress
import com.jstensix.cardspending.ui.formatWon
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Share of the payment that counts toward 실적. No ratio for months whose payments net to zero or below. */
fun recognizedRatio(payment: Long, recognized: Long): Float? {
    if (payment <= 0) return null
    return (recognized.toFloat() / payment.toFloat()).coerceIn(0f, 1f)
}

fun recognizedPercent(payment: Long, recognized: Long): Int? =
    recognizedRatio(payment, recognized)?.let { (it * 100).toInt() }

private val WEEKDAY = mapOf(
    DayOfWeek.MONDAY to "월",
    DayOfWeek.TUESDAY to "화",
    DayOfWeek.WEDNESDAY to "수",
    DayOfWeek.THURSDAY to "목",
    DayOfWeek.FRIDAY to "금",
    DayOfWeek.SATURDAY to "토",
    DayOfWeek.SUNDAY to "일",
)

fun dayLabel(date: LocalDate): String = "${date.monthValue}월 ${date.dayOfMonth}일 (${WEEKDAY.getValue(date.dayOfWeek)})"

/** Short tier label in 만 원: 300,000 → 30만, 125,000 → 12.5만, 15,000,000 → 1,500만. */
fun compactManwon(amount: Long): String =
    if (amount % 10_000 == 0L) {
        String.format(Locale.US, "%,d만", amount / 10_000)
    } else {
        String.format(Locale.US, "%,.1f만", amount / 10_000.0)
    }

/** One line under a card's tier gauge. Remaining amounts stay exact: they say how much more to spend. */
fun tierStatus(progress: TierProgress): String {
    val next = progress.next ?: return "최고 구간 달성"
    val toNext = "${compactManwon(next.amount)}까지 ${formatWon(progress.remaining ?: 0)} 남음"
    if (progress.achieved == 0) return toNext
    return "${compactManwon(progress.tiers[progress.achieved - 1].amount)} 구간 달성 · $toNext"
}

/**
 * Left x of each tier label under a gauge, or null for a label that would collide. Each label is centred under
 * its mark but kept inside [totalWidth]. The top tier's label is always shown; going left to right, a label is
 * dropped when it would come closer than [gap] to the previous shown label or to the top label.
 */
fun tierLabelPositions(centers: List<Int>, widths: List<Int>, totalWidth: Int, gap: Int): List<Int?> {
    if (centers.isEmpty()) return emptyList()
    fun clampedX(i: Int) = (centers[i] - widths[i] / 2).coerceIn(0, (totalWidth - widths[i]).coerceAtLeast(0))
    val last = centers.lastIndex
    val topX = clampedX(last)
    var prevRight = Int.MIN_VALUE / 2
    return centers.indices.map { i ->
        if (i == last) return@map topX
        val x = clampedX(i)
        if (x >= prevRight + gap && x + widths[i] + gap <= topX) {
            prevRight = x + widths[i]
            x
        } else {
            null
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Secondary line for a payment: time, 일시불/할부, cancel mark, and how it was first recorded.
 * A hand-entered payment later matched by its alert holds the alert's data, so it reads 자동.
 */
fun txMeta(tx: Transaction): String = buildString {
    append(tx.approvedAt.format(TIME))
    append(if (tx.installment) " · 할부" else " · 일시불")
    if (tx.kind == TxKind.CANCEL) append(" · 취소")
    append(if (tx.source == TxSource.NOTIFICATION) " · 자동" else " · 직접 입력")
}

data class DayGroup<T>(val date: LocalDate, val label: String, val items: List<T>)

/** Groups consecutive items of the same day, keeping the input order (callers pass newest first). */
fun <T> groupByDay(items: List<T>, dateOf: (T) -> LocalDate): List<DayGroup<T>> {
    val groups = mutableListOf<DayGroup<T>>()
    for (item in items) {
        val date = dateOf(item)
        val last = groups.lastOrNull()
        if (last != null && last.date == date) {
            groups[groups.lastIndex] = last.copy(items = last.items + item)
        } else {
            groups += DayGroup(date, dayLabel(date), listOf(item))
        }
    }
    return groups
}
