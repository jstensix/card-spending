package com.jstensix.cardspending.ui.cards

/** A copy with the element at [from] taken out and put back at [to]; the receiver is left untouched. */
fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().apply { add(to, removeAt(from)) }
