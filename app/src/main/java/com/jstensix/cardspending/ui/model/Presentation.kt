package com.jstensix.cardspending.ui.model

import java.time.DayOfWeek
import java.time.LocalDate

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
