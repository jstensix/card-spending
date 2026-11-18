package com.jstensix.cardspending.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jstensix.cardspending.backup.ConnectStep
import com.jstensix.cardspending.backup.DriveAuth
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.PillStyle
import com.jstensix.cardspending.ui.components.SectionLabel
import com.jstensix.cardspending.ui.components.StatusTag
import com.jstensix.cardspending.ui.components.TagKind
import com.jstensix.cardspending.ui.components.WhiteCard
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.launch

private val BACKUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** Drive reports RFC 3339 UTC times. */
private fun driveTime(value: String): String = try {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(BACKUP_TIME)
} catch (e: DateTimeParseException) {
    value
}

@Composable
fun SettingsScreen(state: SettingsState, vm: SettingsViewModel, driveAuth: DriveAuth, onOpenSetup: () -> Unit) {
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

    Box(Modifier.fillMaxSize().background(Brand.Canvas)) {
        Column(Modifier.fillMaxSize()) {
            BrandTopBar("설정")
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Brand.Primary, trackColor = Brand.Hairline)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                SectionLabel("구글 드라이브 백업", Modifier.padding(top = 0.dp))
                WhiteCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.email ?: "연결된 계정이 없어요", style = BrandText.body, color = Brand.Ink, modifier = Modifier.weight(1f))
                        StatusTag(if (state.email != null) "연결됨" else "꺼짐", if (state.email != null) TagKind.Accent else TagKind.Excluded)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("마지막 백업: ${state.lastSuccessAt?.format(BACKUP_TIME) ?: "없음"}", style = BrandText.caption, color = Brand.InkMute)
                    state.lastError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = BrandText.caption, color = Brand.Ruby)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (state.email == null) {
                        PillButton(
                            "구글 계정 연결",
                            onClick = {
                                val host = activity ?: return@PillButton
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
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("지금 백업", onClick = vm::backupNow, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("드라이브에서 복원", onClick = { confirmRestore = true }, enabled = !state.busy, style = PillStyle.Secondary, modifier = Modifier.weight(1f))
                                PillButton("연결 해제", onClick = vm::disconnect, enabled = !state.busy, style = PillStyle.Secondary, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                SectionLabel("알림 읽기")
                WhiteCard(onClick = onOpenSetup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("권한 안내", style = BrandText.body, color = Brand.Ink)
                            Text("알림 접근, 배터리 최적화, 카카오톡 설정", style = BrandText.caption, color = Brand.InkMute)
                        }
                        Text("›", style = BrandText.title, color = Brand.InkMute)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    state.remoteChoice?.let { latest ->
        AlertDialog(
            onDismissRequest = vm::cancelConnect,
            containerColor = Brand.Canvas,
            title = { Text("드라이브에 백업이 있어요", style = BrandText.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("마지막 백업: ${driveTime(latest.createdTime)}", style = BrandText.caption, color = Brand.InkMute)
                    Text("어느 쪽 데이터를 남길까요? 선택하기 전에는 연결되지 않아요.", style = BrandText.label, color = Brand.Ink)
                    // The destructive consequence lives in the body, never in a button label that could be cut off.
                    Text("드라이브 백업으로 복원하면 이 폰의 데이터는 지워져요.", style = BrandText.label, color = Brand.Ruby)
                    PillButton("드라이브 백업으로 복원", onClick = vm::chooseRestore, modifier = Modifier.fillMaxWidth())
                    PillButton("이 폰의 데이터로 덮어쓰기", onClick = vm::chooseOverwrite, style = PillStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = vm::cancelConnect) { Text("취소", color = Brand.InkMute) } },
        )
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = Brand.Canvas,
            title = { Text("드라이브에서 복원", style = BrandText.title) },
            text = { Text("지금 폰에 있는 데이터를 드라이브의 최신 백업으로 덮어써요. 계속할까요?", style = BrandText.label) },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restore() }) { Text("덮어쓰기", color = Brand.Primary) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("취소", color = Brand.InkMute) } },
        )
    }
}
