package com.jstensix.cardspending.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.jstensix.cardspending.CardSpendingApp
import kotlinx.coroutines.launch

class CardNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val messages = NotificationExtractor.extract(sbn.packageName, sbn.notification, sbn.postTime)
        if (messages.isEmpty()) return
        val container = (application as CardSpendingApp).container
        container.appScope.launch { container.ingestor.ingestAll(messages) }
    }

    // Some vendors unbind listeners under battery pressure; ask the system to bind us again.
    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, CardNotificationListener::class.java))
    }
}
