package com.jstensix.cardspending.ui.model

import com.jstensix.cardspending.model.Transaction
import com.jstensix.cardspending.model.TxKind
import com.jstensix.cardspending.model.TxSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
