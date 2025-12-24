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

import android.Manifest.permission.MANAGE_KEY_GESTURES
import android.app.PendingIntent
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.input.InputManager
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_COMPLETE
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_START
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SettingsCacheSandbox
import com.android.quickstep.OverviewCommandHelper
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class QuickstepKeyGestureEventsHandlerTest {
    @get:Rule
    val context =
        spy(SandboxApplication()) {
            on { checkSelfPermission(eq(MANAGE_KEY_GESTURES)) } doReturn PERMISSION_GRANTED
        }

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val settingsCacheSandbox = SettingsCacheSandbox()
    private val inputManager =
        context.spyService(InputManager::class.java).stub {
            doNothing().whenever(it).registerKeyGestureEventHandler(any(), any())
            doNothing().whenever(it).unregisterKeyGestureEventHandler(any())
        }
    private val allAppsPendingIntent: PendingIntent = mock()
    private val keyGestureEventsCaptor: KArgumentCaptor<List<Int>> = argumentCaptor()
    private val fakeOverviewHandler = FakeOverviewHandler()
    private val overviewCommandHelper = mock<OverviewCommandHelper>()
    private lateinit var keyGestureEventsManager: QuickstepKeyGestureEventsManager

    @Before
    fun setup() {
        keyGestureEventsManager =
            QuickstepKeyGestureEventsManager(context, settingsCacheSandbox.cache)
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
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerAllAppsHandlerTwice_flagEnabled_registerWithExpectedKeyGestureEventsOnce() {
        keyGestureEventsManager.registerAllAppsKeyGestureEvent(allAppsPendingIntent)
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
    fun registerAllAppsHandler_noPermission_noRegister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

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
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun registerOverviewHandlerTwice_flagEnabled_registerWithExpectedKeyGestureEventsOnce() {
        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)
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
    fun registerOverviewHandler_noPermission_unregisterHandler() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.registerOverviewKeyGestureEvent(fakeOverviewHandler)

        verifyNoInteractions(inputManager)
    }

    @Test
    fun registerHomeGestureHandler_registerWithExpectedKeyGestureEvents() {
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.homeKeyGestureEventHandler),
            )
        assertThat(keyGestureEventsCaptor.firstValue)
            .containsExactly(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
    }

    @Test
    fun registerHomeGestureHandlerTwice_registerWithExpectedKeyGestureEventsOnce() {
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        verify(inputManager)
            .registerKeyGestureEventHandler(
                keyGestureEventsCaptor.capture(),
                eq(keyGestureEventsManager.homeKeyGestureEventHandler),
            )
        assertThat(keyGestureEventsCaptor.firstValue)
            .containsExactly(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
    }

    @Test
    fun registerHomeGestureHandler_noPermission_unregisterHandler() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

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
    fun unregisterAllAppsHandler_noPermission_noUnregister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

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
    fun unregisterOverviewHandler_noPermission_noUnregister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.unregisterOverviewKeyGestureEvent()

        verifyNoInteractions(inputManager)
    }

    @Test
    fun unregisterHomeGestureHandler_unregisterHandler() {
        keyGestureEventsManager.unregisterHomeKeyGestureEvent()

        verify(inputManager)
            .unregisterKeyGestureEventHandler(
                eq(keyGestureEventsManager.homeKeyGestureEventHandler)
            )
    }

    @Test
    fun unregisterHomeGestureHandler_noPermission_noUnregister() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)

        keyGestureEventsManager.unregisterHomeKeyGestureEvent()

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
    fun handleAllAppsEvent_noPermission_noInteractionWithTaskbar() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
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
    fun handleRecentAppsEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
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
    fun handleRecentAppsSwitcherStartEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
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

    @Test
    @EnableFlags(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS)
    fun handleRecentAppsSwitcherCompleteEvent_noPermission_noOverviewEventInFake() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
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
    fun handleHomeEvent_addHomeCommand() {
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        keyGestureEventsManager.homeKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verify(overviewCommandHelper)
            .addCommand(OverviewCommandHelper.CommandType.HOME, TEST_DISPLAY_ID)
    }

    @Test
    fun handleHomeEvent_userSetupIncomplete_noInteractionWithOverviewCommandHelper() {
        keyGestureEventsManager.onUserSetupCompleteListener.onSettingsChanged(
            /* isEnabled= */ false
        )
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        keyGestureEventsManager.homeKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
    }

    @Test
    fun handleHomeEvent_noPermission_noInteractionWithOverviewCommandHelper() {
        whenever(context.checkSelfPermission(eq(MANAGE_KEY_GESTURES))).thenReturn(PERMISSION_DENIED)
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        keyGestureEventsManager.homeKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
    }

    @Test
    fun handleHomeEvent_wrongEventType_noInteractionWithOverviewCommandHelper() {
        keyGestureEventsManager.registerHomeKeyGestureEvent(overviewCommandHelper)

        // Use a different key gesture type
        keyGestureEventsManager.homeKeyGestureEventHandler.handleKeyGestureEvent(
            KeyGestureEvent.Builder()
                .setDisplayId(TEST_DISPLAY_ID)
                .setKeyGestureType(KEY_GESTURE_TYPE_ALL_APPS)
                .build(),
            /* focusedToken= */ null,
        )

        verifyNoInteractions(overviewCommandHelper)
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
