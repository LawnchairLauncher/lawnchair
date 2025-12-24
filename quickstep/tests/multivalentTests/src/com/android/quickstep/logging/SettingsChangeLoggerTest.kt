/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.logging

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.ALLOW_ROTATION
import com.android.launcher3.SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.logging.InstanceId
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_ROTATION_DISABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_ROTATION_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NOTIFICATION_DOT_ENABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_THEMED_ICON_DISABLED
import com.android.launcher3.states.RotationHelper.ALLOW_ROTATION_PREFERENCE_KEY
import com.android.launcher3.util.AllModulesMinusApiWrapper
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SandboxApplication
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.capture
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SettingsChangeLoggerTest {

    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val context = SandboxApplication()

    private val mInstanceId = InstanceId.fakeInstanceId(1)

    @Mock(answer = Answers.RETURNS_SELF)
    private lateinit var mMockLogger: StatsLogManager.StatsLogger

    @Mock private lateinit var mStatsLogFactory: StatsLogManager.StatsLogManagerFactory
    @Mock private lateinit var mStatsLogManager: StatsLogManager

    @Captor private lateinit var mEventCaptor: ArgumentCaptor<StatsLogManager.EventEnum>

    @Before
    fun setUp() {
        whenever(mStatsLogFactory.create(context)).doReturn(mStatsLogManager)
        whenever(mStatsLogManager.logger()).doReturn(mMockLogger)
        context.initDaggerComponent(
            DaggerSettingsChangeLoggerTest_TestComponent.builder()
                .bindStatsLogManagerFactory(mStatsLogFactory)
        )

        // To match the default value of THEMED_ICONS
        ThemeManager.INSTANCE.get(context).isMonoThemeEnabled = false
        // To match the default value of ALLOW_ROTATION
        LauncherPrefs.get(context).put(item = ALLOW_ROTATION, value = false)
    }

    @Test
    fun loggingPrefs_correctDefaultValue() {
        val systemUnderTest = SettingsChangeLogger.INSTANCE.get(context)

        assertThat(systemUnderTest.loggingPrefs[ALLOW_ROTATION_PREFERENCE_KEY]!!.defaultValue)
            .isFalse()
        assertThat(systemUnderTest.loggingPrefs[ADD_ICON_PREFERENCE_KEY]!!.defaultValue).isTrue()
        assertThat(systemUnderTest.loggingPrefs[OVERVIEW_SUGGESTED_ACTIONS]!!.defaultValue).isTrue()
        assertThat(systemUnderTest.loggingPrefs[KEY_ENABLE_MINUS_ONE]!!.defaultValue).isTrue()
    }

    @Test
    fun logSnapshot_defaultValue() {
        SettingsChangeLogger.INSTANCE.get(context).logSnapshot(mInstanceId)

        verify(mMockLogger, atLeastOnce()).log(capture(mEventCaptor))
        val capturedEvents = mEventCaptor.allValues
        assertThat(capturedEvents.isNotEmpty()).isTrue()
        verifyDefaultEvent(capturedEvents)
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_ROTATION_DISABLED.id })
            .isTrue()
    }

    @Test
    fun logSnapshot_updateAllowRotation() {
        LauncherPrefs.get(context).put(item = ALLOW_ROTATION, value = true)

        // Create it after changing the launcher prefs so that mLoggablePrefs will be different
        SettingsChangeLogger.INSTANCE.get(context).logSnapshot(mInstanceId)

        verify(mMockLogger, atLeastOnce()).log(capture(mEventCaptor))
        val capturedEvents = mEventCaptor.allValues
        assertThat(capturedEvents.isNotEmpty()).isTrue()
        verifyDefaultEvent(capturedEvents)
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_ROTATION_ENABLED.id })
            .isTrue()
    }

    private fun verifyDefaultEvent(capturedEvents: MutableList<StatsLogManager.EventEnum>) {
        assertThat(capturedEvents.any { it.id == LAUNCHER_NOTIFICATION_DOT_ENABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_THEMED_ICON_DISABLED.id }).isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ADD_NEW_APPS_TO_HOME_SCREEN_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_ALL_APPS_SUGGESTIONS_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_HOME_SCREEN_SUGGESTIONS_ENABLED.id })
            .isTrue()
        assertThat(capturedEvents.any { it.id == LAUNCHER_GOOGLE_APP_SWIPE_LEFT_ENABLED }).isTrue()
    }

    companion object {
        private const val KEY_ENABLE_MINUS_ONE = "pref_enable_minus_one"
        private const val OVERVIEW_SUGGESTED_ACTIONS = "pref_overview_action_suggestions"

        private const val LAUNCHER_GOOGLE_APP_SWIPE_LEFT_ENABLED = 617
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesMinusApiWrapper::class, FakePrefsModule::class])
    interface TestComponent : LauncherAppComponent {

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance
            fun bindStatsLogManagerFactory(factory: StatsLogManager.StatsLogManagerFactory): Builder

            override fun build(): TestComponent
        }
    }
}
