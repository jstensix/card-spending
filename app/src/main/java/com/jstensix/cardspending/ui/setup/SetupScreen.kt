package com.jstensix.cardspending.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jstensix.cardspending.notification.DeviceStatus

private val GUIDE = listOf(
    "카카오톡: 알림과 메시지 미리보기를 켜 주세요. 롯데카드 결제 알림이 카카오톡으로 와요.",
    "카카오톡: 'PC 버전 사용 중 모바일 알림 끄기' 같은 옵션이 켜져 있으면 결제를 놓쳐요. 꺼 주세요.",
    "하나카드 앱: 결제 푸시 알림을 켜 주세요.",
    "APK 파일로 설치했다면, 알림 접근이 막힐 때 앱 정보 → 오른쪽 위 메뉴 → '제한된 설정 허용'을 먼저 눌러 주세요.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var listenerEnabled by remember { mutableStateOf(DeviceStatus.isListenerEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(DeviceStatus.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        listenerEnabled = DeviceStatus.isListenerEnabled(context)
        batteryExempt = DeviceStatus.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("권한 안내") }, navigationIcon = { TextButton(onClick = onBack) { Text("뒤로") } })
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(
                title = "알림 접근",
                description = "카드 결제 알림을 읽으려면 필요해요.",
                enabled = listenerEnabled,
                action = "설정 열기",
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            StatusCard(
                title = "배터리 최적화 제외",
                description = "폰이 배터리를 아끼느라 알림 읽기를 멈추지 않게 해요.",
                enabled = batteryExempt,
                action = "요청",
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }
            Text("함께 확인할 것", style = MaterialTheme.typography.titleSmall)
            GUIDE.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun StatusCard(title: String, description: String, enabled: Boolean, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (enabled) "켜짐" else "꺼짐",
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(description, style = MaterialTheme.typography.bodySmall)
            if (!enabled) Button(onClick = onAction) { Text(action) }
        }
    }
}
