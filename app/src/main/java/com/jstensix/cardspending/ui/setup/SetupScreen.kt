package com.jstensix.cardspending.ui.setup

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.jstensix.cardspending.ui.components.BrandTopBar
import com.jstensix.cardspending.ui.components.PillButton
import com.jstensix.cardspending.ui.components.SectionLabel
import com.jstensix.cardspending.ui.components.SoftBox
import com.jstensix.cardspending.ui.components.StatusTag
import com.jstensix.cardspending.ui.components.TagKind
import com.jstensix.cardspending.ui.components.WhiteCard
import com.jstensix.cardspending.ui.theme.Brand
import com.jstensix.cardspending.ui.theme.BrandText

private val GUIDE = listOf(
    "카카오톡: 알림과 메시지 미리보기를 켜 주세요. 롯데카드 결제 알림이 카카오톡으로 와요.",
    "카카오톡: 'PC 버전 사용 중 모바일 알림 끄기' 같은 옵션이 켜져 있으면 결제를 놓쳐요. 꺼 주세요.",
    "하나카드 앱: 결제 푸시 알림을 켜 주세요.",
    "APK 파일로 설치했다면, 알림 접근이 막힐 때 앱 정보 → 오른쪽 위 메뉴 → '제한된 설정 허용'을 먼저 눌러 주세요.",
)

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

    Column(Modifier.fillMaxSize().background(Brand.Canvas)) {
        BrandTopBar("권한 안내", onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            StatusCard("알림 접근", "카드 결제 알림을 읽으려면 필요해요.", listenerEnabled, "설정 열기") {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            Spacer(Modifier.height(12.dp))
            StatusCard("배터리 최적화 제외", "폰이 배터리를 아끼느라 알림 읽기를 멈추지 않게 해요.", batteryExempt, "요청") {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
                )
            }
            SectionLabel("함께 확인할 것")
            SoftBox(GUIDE.joinToString("\n\n") { "• $it" })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(title: String, description: String, enabled: Boolean, action: String, onAction: () -> Unit) {
    WhiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = BrandText.body, color = Brand.Ink, modifier = Modifier.weight(1f))
            StatusTag(if (enabled) "켜짐" else "꺼짐", if (enabled) TagKind.Accent else TagKind.Excluded)
        }
        Spacer(Modifier.height(4.dp))
        Text(description, style = BrandText.caption, color = Brand.InkMute)
        if (!enabled) {
            Spacer(Modifier.height(12.dp))
            PillButton(action, onClick = onAction, modifier = Modifier.fillMaxWidth())
        }
    }
}
