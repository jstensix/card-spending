package com.jstensix.cardspending.backup

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/**
 * Drive access through Google Identity's AuthorizationClient. Only the account email is remembered;
 * access tokens are requested fresh from Play services on every backup.
 */
sealed interface ConsentResult {
    data class Granted(val accessToken: String) : ConsentResult
    data class Failed(val reason: String) : ConsentResult
}

class DriveAuth(private val context: Context, private val prefs: BackupPrefs) : TokenProvider {
    companion object {
        const val TAG = "CardSpendingAuth"

        val SCOPES = listOf(
            Scope("https://www.googleapis.com/auth/drive.appdata"),
            Scope("https://www.googleapis.com/auth/userinfo.email"),
        )

        /** "DEVELOPER_ERROR (코드 10)" etc.: Google's status name and number, so setup problems can be told apart. */
        fun describe(e: ApiException, resultCode: Int? = null): String {
            val name = CommonStatusCodes.getStatusCodeString(e.statusCode)
            val code = resultCode?.let { ", resultCode $it" }.orEmpty()
            return "$name (코드 ${e.statusCode}$code)"
        }
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

    /**
     * Reads the consent screen's result. Failures keep Google's status code: "cancelled" alone hid setup
     * problems such as a missing/mismatched Android OAuth client (DEVELOPER_ERROR, code 10).
     */
    fun tokenFromResult(activity: Activity, resultCode: Int, data: Intent?): ConsentResult = try {
        val token = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data).accessToken
        if (token != null) ConsentResult.Granted(token) else ConsentResult.Failed("토큰을 받지 못했어요 (resultCode $resultCode)")
    } catch (e: ApiException) {
        Log.w(TAG, "authorization failed: status=${e.statusCode} resultCode=$resultCode", e)
        ConsentResult.Failed(describe(e, resultCode))
    }
}
