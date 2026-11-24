package com.jstensix.cardspending.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelTest {
    @Test fun messagingApps_areSms() {
        assertEquals(Channel.SMS, channelOf("com.samsung.android.messaging"))
        assertEquals(Channel.SMS, channelOf("com.google.android.apps.messaging"))
    }

    @Test fun everythingElse_isPush() {
        assertEquals(Channel.PUSH, channelOf("com.hanacard.app"))
        assertEquals(Channel.PUSH, channelOf("com.kakao.talk"))
        assertEquals(Channel.PUSH, channelOf(null))
    }
}
