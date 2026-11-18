package com.jstensix.cardspending.backup

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Drive access through Google Identity's AuthorizationClient. Only the account email is remembered;
 * access tokens are requested fresh from Play services on every backup.
 */
class DriveAuth(private val context: Context, private val prefs: BackupPrefs) : TokenProvider {
    companion object {
        val SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("https://www.googleapis.com/auth/userinfo.email"),
        )
    }

    /** Background use: never shows UI, so a needed consent screen means the user must reconnect in Settings. */
    override suspend fun accessToken(): TokenResult {
        val email = prefs.email ?: return TokenResult.NotConnected
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(SCOPES)
            .setAccount(Account(email, "com.google"))
            .build()
        val result = try {
            Identity.getAuthorizationClient(context).authorize(request).await()
        } catch (e: ApiException) {
            return TokenResult.NeedsReconnect
        }
        if (result.hasResolution()) return TokenResult.NeedsReconnect
        return result.accessToken?.let { TokenResult.Token(it) } ?: TokenResult.NeedsReconnect
    }

    /** Interactive connect from Settings: may need the account chooser / consent screen. */
    suspend fun beginConnect(activity: Activity): ConnectStep {
        val request = AuthorizationRequest.builder().setRequestedScopes(SCOPES).build()
        val result = Identity.getAuthorizationClient(activity).authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            ConnectStep.NeedsUi(pending)
        } else {
            ConnectStep.Granted(requireNotNull(result.accessToken) { "no access token" })
        }
    }

    fun tokenFromResult(activity: Activity, data: Intent?): String? = try {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
    } catch (e: ApiException) {
        null
    }
}
