package com.jstensix.cardspending.ui

import java.time.YearMonth
import java.util.Locale

fun formatWon(amount: Long): String = String.format(Locale.US, "%,d원", amount)

fun formatMonth(month: YearMonth): String = "${month.year}년 ${month.monthValue}월"
