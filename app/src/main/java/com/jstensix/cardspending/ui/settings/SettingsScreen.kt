package com.jstensix.cardspending.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.backup.ConnectStep
import com.jstensix.cardspending.backup.DriveAuth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, vm: SettingsViewModel, driveAuth: DriveAuth, onBack: () -> Unit) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmRestore by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = activity?.let { driveAuth.tokenFromResult(it, result.data) }
        if (token != null) vm.onTokenGranted(token) else vm.onConnectFailed(null)
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("설정") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("구글 드라이브 백업", style = MaterialTheme.typography.titleMedium)
                    Text(state.email?.let { "연결된 계정: $it" } ?: "연결된 계정이 없어요")
                    Text("마지막 백업: ${state.lastSuccessAt?.format(BACKUP_TIME) ?: "없음"}")
                    state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            if (state.email == null) {
                Button(
                    onClick = {
                        val host = activity ?: return@Button
                        scope.launch {
                            try {
                                when (val step = driveAuth.beginConnect(host)) {
                                    is ConnectStep.NeedsUi ->
                                        consent.launch(IntentSenderRequest.Builder(step.pendingIntent.intentSender).build())
                                    is ConnectStep.Granted -> vm.onTokenGranted(step.accessToken)
                                }
                            } catch (e: Exception) {
                                // Play services reports setup problems (e.g. missing OAuth client) as exceptions.
                                vm.onConnectFailed(e.message)
                            }
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("구글 계정 연결") }
            } else {
                Button(onClick = vm::backupNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("지금 백업") }
                OutlinedButton(onClick = { confirmRestore = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("드라이브에서 복원")
                }
                OutlinedButton(onClick = vm::disconnect, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("연결 해제") }
            }
        }
    }

    if (state.showRestorePrompt) {
        AlertDialog(
            onDismissRequest = vm::skipRestore,
            title = { Text("드라이브에 백업이 있어요") },
            text = { Text("복원할까요?") },
            confirmButton = { TextButton(onClick = vm::restore) { Text("복원") } },
            dismissButton = { TextButton(onClick = vm::skipRestore) { Text("나중에") } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("드라이브에서 복원") },
            text = { Text("지금 폰에 있는 데이터를 드라이브의 최신 백업으로 덮어써요. 계속할까요?") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restore() }) { Text("덮어쓰기") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("취소") } },
        )
    }
}
