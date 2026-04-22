/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.input

import android.app.PendingIntent
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_COMPLETE
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_START
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SandboxApplication
import com.android.quickstep.input.QuickstepKeyGestureEventsHandlerTest.FakeOverviewHandler.OverviewEvent
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler.OverviewType
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler.OverviewType.ALT_TAB
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler.OverviewType.UNDEFINED
import com.android.window.flags2.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickstepKeyGestureEventsHandlerTest {
    @get:Rule val context = SandboxApplication()

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val inputManager = context.spyService(InputManager::class.java)
    private val allAppsPendingIntent: PendingIntent = mock()
    private val keyGestureEventsCaptor: KArgumentCaptor<List<Int>> = argumentCaptor()
    private val fakeOverviewHandler = FakeOverviewHandler()
    private lateinit var keyGestureEventsManager: QuickstepKeyGestureEventsManager

    @Before
    fun setup() {
        doNothing().whenever(inputManager).registerKeyGestureEventHandler(any(), any())
        doNothing().whenever(inputManager).unregisterKeyGestureEventHandler(any())
        keyGestureEventsManager = QuickstepKeyGestureEventsManager(context)
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(/* isEnabled= */ true)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandler_flagEnabled_registerWithExpectedKeyGestureEvents() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.allAppsKeyGestureEventHandler),
            )
        assertThat(keyGestureEventsCaptor.firstValue).containsExactly(KEY_GESTURE_TYPE_ALL_APPS)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandler_flagDisabled_noRegister() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandler_flagEnabled_registerWithExpectedKeyGestureEvents() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.overviewKeyGestureEventHandler),
            )
        assertThat(keyGestureEventsCaptor.firstValue)
            .containsExactly(KEY_GESTURE_TYPE_RECENT_APPS, KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandler_flagDisabled_noRegister() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterAllAppsHandler_flagEnabled_unregisterHandler() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()

        verify(inputManager)
            .unregisterKeyGestureEventHandler(
                eq(keyGestureEventsManager.allAppsKeyGestureEventHandler)
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterAllAppsHandler_flagDisabled_noUnregister() {
        keyGestureEventsManager.unregisterAllAppsKeyGestureEvent()

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterOverviewHandler_flagEnabled_unregisterHandler() {
        keyGestureEventsManager.unregisterOverviewKeyGestureEvent()

        verify(inputManager)
            .unregisterKeyGestureEventHandler(
                eq(keyGestureEventsManager.overviewKeyGestureEventHandler)
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun unregisterOverviewHandler_flagDisabled_noUnregister() {
        keyGestureEventsManager.unregisterOverviewKeyGestureEvent()

        verifyNoInteractions(inputManager)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagEnabled_toggleAllAppsSearch() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        keyGestureEventsManager.allAppsKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verify(allAppsPendingIntent).send()
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagEnabled_userSetupIncomplete_noInteractionWithTaskbar() {
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(
            /* isEnabled= */ false
        )
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        keyGestureEventsManager.allAppsKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleAllAppsEvent_flagDisabled_noInteractionWithTaskbar() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)

        keyGestureEventsManager.allAppsKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(allAppsPendingIntent)
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_flagEnabled_showOverviewWithUndefinedType() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent)
            .isEqualTo(OverviewEvent(shouldShowOverview = true, type = UNDEFINED))
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_userSetupIncomplete_noOverviewEventInFake() {
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(
            /* isEnabled= */ false
        )
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsEvent_flagDisabled_noOverviewEventInFake() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_flagEnabled_showOverviewWithAltTabType() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent)
            .isEqualTo(OverviewEvent(shouldShowOverview = true, type = ALT_TAB))
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_userSetupIncomplete_noOverviewEventInFake() {
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(
            /* isEnabled= */ false
        )
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherStartEvent_flagDisabled_noOverviewEventInFake() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_START)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_flagEnabled_hideOverviewWithAltTabType() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent)
            .isEqualTo(OverviewEvent(shouldShowOverview = false, type = ALT_TAB))
    }

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_userSetupIncomplete_noOverviewEventInFake() {
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(
            /* isEnabled= */ false
        )
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    @Test
    @DisableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_flagDisabled_noOverviewEventInFake() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        keyGestureEventsManager.overviewKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER)
                .setAction(ACTION_GESTURE_COMPLETE)
                .build(),
            /* focusedToken= */ null,
        )

        assertThat(fakeOverviewHandler.overviewEvent).isNull()
    }

    private class FakeOverviewHandler : OverviewGestureHandler {
        data class OverviewEvent(val shouldShowOverview: Boolean, val type: OverviewType)

        var overviewEvent: OverviewEvent? = null
            private set

        override fun showOverview(type: OverviewType) {
            overviewEvent = OverviewEvent(shouldShowOverview = true, type)
        }

        override fun hideOverview(type: OverviewType) {
            overviewEvent = OverviewEvent(shouldShowOverview = false, type)
        }
    }

    private companion object {
        const val TEST_DISPLAY_ID = 6789
    }
}
