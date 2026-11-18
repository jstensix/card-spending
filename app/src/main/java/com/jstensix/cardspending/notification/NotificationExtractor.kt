package com.jstensix.cardspending.notification

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.jstensix.cardspending.parsing.NotificationMessage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationExtractor {
    /**
     * KakaoTalk bundles a channel's unread messages into one MessagingStyle notification and re-posts it as
     * new ones arrive, so each message becomes its own unit. Other apps give one unit: big text, else text.
     */
    fun extract(
        packageName: String,
        notification: Notification,
        postTime: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<NotificationMessage> {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        val messages = style?.messages.orEmpty()
        if (messages.isNotEmpty()) {
            val messageTitle = title ?: style?.conversationTitle?.toString()
            return messages.mapNotNull { message ->
                val body = message.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val time = if (message.timestamp > 0) message.timestamp else postTime
                NotificationMessage(packageName, messageTitle, body, toLocal(time, zone), conversation = true)
            }
        }

        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()
        if (body.isNullOrBlank()) return emptyList()
        return listOf(NotificationMessage(packageName, title, body, toLocal(postTime, zone)))
    }

    private fun toLocal(epochMillis: Long, zone: ZoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
}
