package com.jstensix.cardspending.model

import kotlinx.serialization.Serializable

/** One 실적 구간 of a card: reaching [amount] of recognized spending in a month earns [benefit]. */
@Serializable
data class Tier(val amount: Long, val benefit: String = "")
