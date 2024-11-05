/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.flicker.pip.tv

import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.filters.RequiresDevice
import com.android.wm.shell.flicker.utils.NotificationListener.Companion.findNotification
import com.android.wm.shell.flicker.utils.NotificationListener.Companion.startNotificationListener
import com.android.wm.shell.flicker.utils.NotificationListener.Companion.stopNotificationListener
import com.android.wm.shell.flicker.utils.NotificationListener.Companion.waitForNotificationToAppear
import com.android.wm.shell.flicker.utils.NotificationListener.Companion.waitForNotificationToDisappear
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test Pip Notifications on TV. To run this test: `atest
 * WMShellFlickerTests:TvPipNotificationTests`
 */
@RequiresDevice
class TvPipNotificationTests : TvPipTestBase() {
    @Before
    fun tvPipNotificationTestsSetUp() {
        val started = startNotificationListener()
        if (!started) {
            error("NotificationListener hasn't started")
        }
    }

    @After
    override fun tearDown() {
        stopNotificationListener()
        super.tearDown()
    }

    @Test
    fun pipNotification_postedAndDismissed() {
        testApp.launchViaIntent()
        testApp.clickEnterPipButton(wmHelper)

        assertNotNull(
            "Pip notification should have been posted",
            waitForNotificationToAppear { it.isPipNotificationWithTitle(testApp.appName) }
        )

        testApp.closePipWindow()

        assertTrue(
            "Pip notification should have been dismissed",
            waitForNotificationToDisappear { it.isPipNotificationWithTitle(testApp.appName) }
        )
    }

    @Test
    fun pipNotification_closeIntent() {
        testApp.launchViaIntent()
        testApp.clickEnterPipButton(wmHelper)

        val notification: StatusBarNotification =
            waitForNotificationToAppear { it.isPipNotificationWithTitle(testApp.appName) }
                ?: fail("Pip notification should have been posted")

        notification.deleteIntent?.send() ?: fail("Pip notification should contain `delete_intent`")

        assertTrue(
            "Pip should have closed by sending the `delete_intent`",
            testApp.waitUntilClosed()
        )
        assertTrue(
            "Pip notification should have been dismissed",
            waitForNotificationToDisappear { it.isPipNotificationWithTitle(testApp.appName) }
        )
    }

    @Test
    fun pipNotification_menuIntent() {
        testApp.launchViaIntent(wmHelper)
        testApp.clickEnterPipButton(wmHelper)

        val notification: StatusBarNotification =
            waitForNotificationToAppear { it.isPipNotificationWithTitle(testApp.appName) }
                ?: fail("Pip notification should have been posted")

        notification.contentIntent?.send()
            ?: fail("Pip notification should contain `content_intent`")

        assertNotNull(
            "Pip menu should have been shown after sending `content_intent`",
            uiDevice.waitForTvPipMenu()
        )

        uiDevice.pressBack()
        testApp.closePipWindow()
    }

    @Test
    fun pipNotification_mediaSessionTitle_isDisplayed() {
        testApp.launchViaIntent(wmHelper)
        // Start media session and to PiP
        testApp.clickStartMediaSessionButton()
        testApp.clickEnterPipButton(wmHelper)

        // Wait for the correct notification to show up...
        waitForNotificationToAppear { it.isPipNotificationWithTitle(TITLE_MEDIA_SESSION_PLAYING) }
            ?: fail("Pip notification with media session title should have been posted")
        // ... and make sure "regular" PiP notification is now shown
        assertNull(
            "Regular notification should not have been posted",
            findNotification { it.isPipNotificationWithTitle(testApp.appName) }
        )

        // Pause the media session. When paused the application updates the title for the media
        // session. This change should be reflected in the notification.
        testApp.pauseMedia()

        // Wait for the "paused" notification to show up...
        waitForNotificationToAppear { it.isPipNotificationWithTitle(TITLE_MEDIA_SESSION_PAUSED) }
            ?: fail("Pip notification with media session title should have been posted")
        // ... and make sure "playing" PiP notification is gone
        assertNull(
            "Regular notification should not have been posted",
            findNotification { it.isPipNotificationWithTitle(TITLE_MEDIA_SESSION_PLAYING) }
        )

        // Now stop the media session, which should revert the title to the "default" one.
        testApp.stopMedia()

        // Wait for the "regular" notification to show up...
        waitForNotificationToAppear { it.isPipNotificationWithTitle(testApp.appName) }
            ?: fail("Pip notification with media session title should have been posted")
        // ... and make sure previous ("paused") notification is gone
        assertNull(
            "Regular notification should not have been posted",
            findNotification { it.isPipNotificationWithTitle(TITLE_MEDIA_SESSION_PAUSED) }
        )

        testApp.closePipWindow()
    }

    companion object {
        private const val TITLE_MEDIA_SESSION_PLAYING = "TestApp media is playing"
        private const val TITLE_MEDIA_SESSION_PAUSED = "TestApp media is paused"
    }
}

private val StatusBarNotification.extras: Bundle?
    get() = notification?.extras

private val StatusBarNotification.title: String
    get() = extras?.getString(Notification.EXTRA_TITLE) ?: ""

/** Get TV extensions with [android.app.Notification.TvExtender.EXTRA_TV_EXTENDER]. */
private val StatusBarNotification.tvExtensions: Bundle?
    get() = extras?.getBundle("android.tv.EXTENSIONS")

/** "Content" TV intent with key [android.app.Notification.TvExtender.EXTRA_CONTENT_INTENT]. */
private val StatusBarNotification.contentIntent: PendingIntent?
    get() = tvExtensions?.getParcelable("content_intent")

/** "Delete" TV intent with key [android.app.Notification.TvExtender.EXTRA_DELETE_INTENT]. */
private val StatusBarNotification.deleteIntent: PendingIntent?
    get() = tvExtensions?.getParcelable("delete_intent")

private fun StatusBarNotification.isPipNotificationWithTitle(expectedTitle: String): Boolean =
    tag == "TvPip" && title == expectedTitle
