package com.jstensix.cardspending.rules

private val LAST4 = Regex("""^\d{4}$""")

fun isValidLast4(value: String): Boolean = LAST4.matches(value)

/** Card notifications show only the 1st and 3rd of the last four digits, e.g. 7512 -> "7*1*". */
fun maskOf(last4: String): String {
    require(isValidLast4(last4)) { "last4 must be 4 digits" }
    return "${last4[0]}*${last4[2]}*"
}
