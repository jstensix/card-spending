package com.jstensix.cardspending.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.test.core.app.ApplicationProvider
import com.jstensix.cardspending.parsing.NotificationMessage
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationExtractorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("Asia/Seoul")
    private fun epoch(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()
    private val postTime = epoch(LocalDateTime.of(2026, 11, 18, 17, 30))
    private fun builder() = NotificationCompat.Builder(context, "test").setSmallIcon(android.R.drawable.ic_dialog_info)
    private fun extract(notification: Notification, pkg: String = "com.hanacard.app") =
        NotificationExtractor.extract(pkg, notification, postTime, zone)

    @Test fun bigText_usesTitleAndBigText() {
        val body = "거궁찬판교점 / 신용(일시불,0*8*) / 11.18 17:29 / 누적이용금액 532,231원"
        val notification = builder()
            .setContentTitle("(결제) 9,900원")
            .setContentText("거궁찬판교점")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        assertEquals(
            listOf(NotificationMessage("com.hanacard.app", "(결제) 9,900원", body, LocalDateTime.of(2026, 11, 18, 17, 30))),
            extract(notification),
        )
    }

    @Test fun messagingStyle_oneMessagePerEntry() {
        val sender = Person.Builder().setName("롯데카드").build()
        val notification = builder()
            .setContentTitle("롯데카드")
            .setStyle(
                NotificationCompat.MessagingStyle(Person.Builder().setName("나").build())
                    .addMessage("첫 번째", epoch(LocalDateTime.of(2026, 11, 18, 11, 43)), sender)
                    .addMessage("두 번째", epoch(LocalDateTime.of(2026, 11, 18, 12, 10)), sender),
            )
            .build()
        val messages = extract(notification, "com.kakao.talk")
        assertEquals(listOf("첫 번째", "두 번째"), messages.map { it.body })
        assertEquals(listOf("롯데카드", "롯데카드"), messages.map { it.title })
        assertEquals(LocalDateTime.of(2026, 11, 18, 12, 10), messages[1].postedAt)
    }

    @Test fun plainText_usesText() {
        val notification = builder().setContentTitle("알림").setContentText("결제 12,000원 완료").build()
        assertEquals(listOf("결제 12,000원 완료"), extract(notification).map { it.body })
    }

    @Test fun empty_returnsEmpty() {
        assertTrue(extract(builder().setContentTitle("제목만").build()).isEmpty())
    }
}
