package com.jstensix.cardspending.model

/** How an automatically registered payment reached the phone. */
enum class Channel { SMS, PUSH }

private val SMS_PACKAGES = setOf(
    "com.samsung.android.messaging",
    "com.google.android.apps.messaging",
    "com.android.mms",
    "com.android.messaging",
)

/** Text-message apps are SMS; card apps, KakaoTalk and anything unknown are PUSH. */
fun channelOf(sourcePackage: String?): Channel = if (sourcePackage in SMS_PACKAGES) Channel.SMS else Channel.PUSH
