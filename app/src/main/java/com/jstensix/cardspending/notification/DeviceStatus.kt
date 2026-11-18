package com.jstensix.cardspending.notification

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

object DeviceStatus {
    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
}
