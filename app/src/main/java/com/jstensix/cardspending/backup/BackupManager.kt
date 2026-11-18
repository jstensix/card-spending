package com.jstensix.cardspending.backup

import android.app.PendingIntent
import com.jstensix.cardspending.data.SpendingRepository
import com.jstensix.cardspending.model.MINUTE_FORMAT
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerializationException

sealed interface TokenResult {
    data class Token(val value: String) : TokenResult
    data object NotConnected : TokenResult
    data object NeedsReconnect : TokenResult
}

interface TokenProvider {
    suspend fun accessToken(): TokenResult
}

sealed interface ConnectStep {
    data class NeedsUi(val pendingIntent: PendingIntent) : ConnectStep
    data class Granted(val accessToken: String) : ConnectStep
}

sealed interface BackupResult {
    data object Success : BackupResult
    data object NotConnected : BackupResult
    data object NeedsReconnect : BackupResult
    data class Failed(val message: String) : BackupResult
}

const val ERROR_RECONNECT = "구글 계정을 다시 연결해야 해요"
private const val KEEP_BACKUPS = 2
private val FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

class BackupManager(
    private val repo: SpendingRepository,
    private val drive: DriveApi,
    private val tokens: TokenProvider,
    private val prefs: BackupPrefs,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    /** Uploads first and prunes only after the upload succeeded, so a failure never costs an existing backup. */
    suspend fun backupNow(): BackupResult {
        val token = when (val t = tokens.accessToken()) {
            is TokenResult.Token -> t.value
            else -> return notReady(t)
        }
        return try {
            val (cards, transactions, unparsed) = repo.snapshot()
            val time = now()
            val data = BackupData(exportedAt = time.format(MINUTE_FORMAT), cards = cards, transactions = transactions, unparsed = unparsed)
            drive.upload(token, "backup-${time.format(FILE_STAMP)}.json", BackupCodec.encode(data))
            drive.listBackups(token).sortedByDescending { it.createdTime }.drop(KEEP_BACKUPS).forEach { drive.delete(token, it.id) }
            prefs.lastSuccessAt = time
            prefs.lastError = null
            BackupResult.Success
        } catch (e: IOException) {
            failed(e)
        }
    }

    suspend fun hasRemoteBackup(): Boolean {
        val token = (tokens.accessToken() as? TokenResult.Token)?.value ?: return false
        return try {
            drive.listBackups(token).isNotEmpty()
        } catch (e: IOException) {
            false
        }
    }

    suspend fun restoreLatest(): BackupResult {
        val token = when (val t = tokens.accessToken()) {
            is TokenResult.Token -> t.value
            else -> return notReady(t)
        }
        return try {
            val latest = drive.listBackups(token).maxByOrNull { it.createdTime }
                ?: return BackupResult.Failed("드라이브에 백업이 없어요")
            val data = BackupCodec.decode(drive.download(token, latest.id))
            repo.replaceAll(data.cards, data.transactions, data.unparsed)
            BackupResult.Success
        } catch (e: IOException) {
            failed(e)
        } catch (e: SerializationException) {
            BackupResult.Failed("백업 파일을 읽을 수 없어요")
        }
    }

    private fun notReady(result: TokenResult): BackupResult = when (result) {
        TokenResult.NeedsReconnect -> {
            prefs.lastError = ERROR_RECONNECT
            BackupResult.NeedsReconnect
        }
        else -> BackupResult.NotConnected
    }

    private fun failed(e: Exception): BackupResult {
        val message = e.message ?: e.javaClass.simpleName
        prefs.lastError = "백업에 실패했어요: $message"
        return BackupResult.Failed(message)
    }
}
