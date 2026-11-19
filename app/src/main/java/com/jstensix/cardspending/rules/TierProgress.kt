package com.jstensix.cardspending.rules

import com.jstensix.cardspending.model.Tier

/**
 * Where a month's recognized spending stands against a card's 실적 구간.
 * [fill] and [marks] are fractions of the highest tier, for drawing a gauge.
 */
data class TierProgress(
    /** Ascending by amount. */
    val tiers: List<Tier>,
    /** How many tiers are reached (recognized >= amount). */
    val achieved: Int,
    val fill: Float,
    val marks: List<Float>,
    /** The first tier not yet reached; null once every tier is reached. */
    val next: Tier?,
    /** Won still needed for [next]. */
    val remaining: Long?,
)

/** Null when the card has no tiers. A month whose cancels outweigh its payments counts as zero. */
fun tierProgress(recognized: Long, tiers: List<Tier>): TierProgress? {
    if (tiers.isEmpty()) return null
    val sorted = tiers.sortedBy { it.amount }
    val spent = recognized.coerceAtLeast(0)
    val top = sorted.last().amount.toFloat()
    val achieved = sorted.count { spent >= it.amount }
    val next = sorted.getOrNull(achieved)
    return TierProgress(
        tiers = sorted,
        achieved = achieved,
        fill = (spent / top).coerceIn(0f, 1f),
        marks = sorted.map { it.amount / top },
        next = next,
        remaining = next?.let { it.amount - spent },
    )
}
