package com.jstensix.cardspending.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jstensix.cardspending.backup.BackupManager
import com.jstensix.cardspending.backup.BackupPrefs
import com.jstensix.cardspending.backup.BackupResult
import com.jstensix.cardspending.backup.DriveApi
import com.jstensix.cardspending.data.SpendingRepository
import java.io.IOException
import java.time.LocalDateTime
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
    val showRestorePrompt: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(
    private val repo: SpendingRepository,
    private val drive: DriveApi,
    private val manager: BackupManager,
    private val prefs: BackupPrefs,
    private val scheduleBackup: () -> Unit,
    private val cancelBackup: () -> Unit,
) : ViewModel() {
    private val ui = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> = combine(ui, prefs.observe()) { u, status ->
        u.copy(email = status.email, lastSuccessAt = status.lastSuccessAt, lastError = status.lastError)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    /** After the consent screen: remember the account, then offer a restore only when this phone has no data yet. */
    fun onTokenGranted(token: String) = work {
        prefs.email = drive.fetchEmail(token)
        prefs.lastError = null
        if (repo.isEmpty() && manager.hasRemoteBackup()) {
            ui.update { it.copy(showRestorePrompt = true) }
        } else {
            scheduleBackup()
            ui.update { it.copy(message = "연결했어요. 곧 첫 백업을 올릴게요.") }
        }
    }

    fun onConnectFailed(reason: String?) = ui.update { it.copy(message = "연결하지 못했어요: ${reason ?: "취소됨"}") }

    fun backupNow() = work {
        val message = when (val result = manager.backupNow()) {
            BackupResult.Success -> "백업했어요"
            BackupResult.NotConnected -> "구글 계정을 먼저 연결해 주세요"
            BackupResult.NeedsReconnect -> "구글 계정을 다시 연결해야 해요"
            is BackupResult.Failed -> "백업에 실패했어요: ${result.message}"
        }
        ui.update { it.copy(message = message) }
    }

    fun restore() = work {
        val message = when (val result = manager.restoreLatest()) {
            BackupResult.Success -> "드라이브에서 복원했어요"
            BackupResult.NotConnected -> "구글 계정을 먼저 연결해 주세요"
            BackupResult.NeedsReconnect -> "구글 계정을 다시 연결해야 해요"
            is BackupResult.Failed -> "복원하지 못했어요: ${result.message}"
        }
        ui.update { it.copy(showRestorePrompt = false, message = message) }
    }

    fun skipRestore() {
        ui.update { it.copy(showRestorePrompt = false) }
        scheduleBackup()
    }

    fun disconnect() {
        prefs.clear()
        cancelBackup()
        ui.update { it.copy(message = "연결을 해제했어요") }
    }

    fun consumeMessage() = ui.update { it.copy(message = null) }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            ui.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: IOException) {
                ui.update { it.copy(message = "네트워크 오류: ${e.message}") }
            } finally {
                ui.update { it.copy(busy = false) }
            }
        }
    }
}
