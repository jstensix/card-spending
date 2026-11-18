package com.jstensix.cardspending.parsing

import java.time.LocalDateTime

/** One parseable unit of a notification: a single message with its title and the time it arrived. */
data class NotificationMessage(
    val packageName: String,
    val title: String?,
    val body: String,
    val postedAt: LocalDateTime,
    /** From a chat-style notification: the body is someone's message, so only the title (sender/channel) may be trusted. */
    val conversation: Boolean = false,
)
