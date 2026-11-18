package com.jstensix.cardspending.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.backup.BackupManager
import com.jstensix.cardspending.backup.BackupPrefs
import com.jstensix.cardspending.backup.BackupResult
import com.jstensix.cardspending.backup.DriveApi
import com.jstensix.cardspending.backup.DriveFile
import com.jstensix.cardspending.data.SpendingRepository
import java.io.IOException
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val email: String? = null,
    val lastSuccessAt: LocalDateTime? = null,
    val lastError: String? = null,
    val busy: Boolean = false,
    /** Drive already holds a backup for the account being connected: the user must pick restore or overwrite. */
    val remoteChoice: DriveFile? = null,
    val message: String? = null,
)

/**
 * Connecting never turns on background backups until it is safe: prefs.email (which enables them) is saved only
 * after Drive was checked and, if a backup exists there, the user chose what wins. Otherwise a fresh install
 * could upload its near-empty data over the real backup and later prune the real one away.
 */
class SettingsViewModel(
    private val repo: SpendingRepository,
    private val drive: DriveApi,
    private val manager: BackupManager,
    private val prefs: BackupPrefs,
    private val scheduleBackup: () -> Unit,
    private val cancelBackup: () -> Unit,
) : ViewModel() {
    private val ui = MutableStateFlow(SettingsState())
    private var pending: Pending? = null

    private data class Pending(val token: String, val email: String)

    val state: StateFlow<SettingsState> = combine(ui, prefs.observe()) { u, status ->
        u.copy(email = status.email, lastSuccessAt = status.lastSuccessAt, lastError = status.lastError)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    fun onTokenGranted(token: String) = work {
        val email = drive.fetchEmail(token)
        val latest = drive.listBackups(token).maxByOrNull { it.createdTime }
        if (latest == null) {
            connect(email)
            scheduleBackup()
            say("연결했어요. 곧 첫 백업을 올릴게요.")
        } else {
            pending = Pending(token, email)
            ui.update { it.copy(remoteChoice = latest) }
        }
    }

    fun chooseRestore() = work {
        val p = pending ?: return@work
        when (val result = manager.restoreWith(p.token)) {
            BackupResult.Success -> {
                connect(p.email)
                say("드라이브에서 복원했어요")
            }
            else -> say("복원하지 못했어요: ${describe(result)}")
        }
        clearPending()
    }

    fun chooseOverwrite() = work {
        val p = pending ?: return@work
        when (val result = manager.backupWith(p.token)) {
            BackupResult.Success -> {
                connect(p.email)
                say("이 폰의 데이터로 백업했어요")
            }
            else -> say("백업하지 못했어요: ${describe(result)}")
        }
        clearPending()
    }

    fun cancelConnect() {
        clearPending()
        say("연결하지 않았어요")
    }

    fun onConnectFailed(reason: String?) = say("연결하지 못했어요: ${reason ?: "취소됨"}")

    fun backupNow() = work {
        val result = manager.backupNow()
        say(if (result == BackupResult.Success) "백업했어요" else describe(result))
    }

    fun restore() = work {
        val result = manager.restoreLatest()
        say(if (result == BackupResult.Success) "드라이브에서 복원했어요" else "복원하지 못했어요: ${describe(result)}")
    }

    fun disconnect() {
        prefs.clear()
        cancelBackup()
        say("연결을 해제했어요")
    }

    fun consumeMessage() = ui.update { it.copy(message = null) }

    private fun connect(email: String) {
        prefs.email = email
        prefs.lastError = null
    }

    private fun clearPending() {
        pending = null
        ui.update { it.copy(remoteChoice = null) }
    }

    private fun say(message: String) = ui.update { it.copy(message = message) }

    private fun describe(result: BackupResult): String = when (result) {
        BackupResult.Success -> "완료"
        BackupResult.NotConnected -> "구글 계정을 먼저 연결해 주세요"
        BackupResult.NeedsReconnect -> "구글 계정을 다시 연결해야 해요"
        is BackupResult.Failed -> result.message
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                say("드라이브를 확인하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.")
            } catch (e: Exception) {
                say("문제가 생겼어요: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                ui.update { it.copy(busy = false) }
            }
        }
    }
}
