package com.jstensix.cardspending.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDateTime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

const val WARNING_NOT_CONNECTED = "구글 드라이브 백업이 연결되지 않았어요"

data class BackupStatus(val email: String?, val lastSuccessAt: LocalDateTime?, val lastError: String?)

/** Connected account and last backup outcome. Not secret: the token itself is never stored. */
class BackupPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_EMAIL, value) }

    var lastSuccessAt: LocalDateTime?
        get() = prefs.getString(KEY_LAST_SUCCESS, null)?.let(LocalDateTime::parse)
        set(value) = prefs.edit { putString(KEY_LAST_SUCCESS, value?.toString()) }

    var lastError: String?
        get() = prefs.getString(KEY_LAST_ERROR, null)
        set(value) = prefs.edit { putString(KEY_LAST_ERROR, value) }

    fun clear() = prefs.edit { clear() }

    fun status() = BackupStatus(email, lastSuccessAt, lastError)

    fun observe(): Flow<BackupStatus> = callbackFlow {
        trySend(status())
        // SharedPreferences keeps listeners weakly; this local reference lives as long as the flow is collected.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(status()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun observeWarning(): Flow<String?> = observe().map { status ->
        when {
            status.email == null -> WARNING_NOT_CONNECTED
            else -> status.lastError
        }
    }.distinctUntilChanged()

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_LAST_SUCCESS = "lastSuccessAt"
        const val KEY_LAST_ERROR = "lastError"
    }
}
