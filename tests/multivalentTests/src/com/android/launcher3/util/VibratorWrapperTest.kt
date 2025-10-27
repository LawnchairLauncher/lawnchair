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

package com.android.launcher3.util

import android.media.AudioAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK
import android.os.VibrationEffect.Composition.PRIMITIVE_TICK
import android.os.Vibrator
import androidx.test.filters.SmallTest
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.VibratorWrapper.HAPTIC_FEEDBACK_URI
import com.android.launcher3.util.VibratorWrapper.OVERVIEW_HAPTIC
import com.android.launcher3.util.VibratorWrapper.VIBRATION_ATTRS
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.same

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class VibratorWrapperTest {

    @Mock private lateinit var settingsCache: SettingsCache
    private lateinit var vibrator: Vibrator
    private val context: SandboxModelContext = SandboxModelContext()
    @Captor private lateinit var vibrationEffectCaptor: ArgumentCaptor<VibrationEffect>
    @Mock private lateinit var tracker: DaggerSingletonTracker
    private lateinit var underTest: VibratorWrapper

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        vibrator = context.spyService(Vibrator::class.java)
        `when`(settingsCache.getValue(HAPTIC_FEEDBACK_URI, 0)).thenReturn(true)
        `when`(vibrator.hasVibrator()).thenReturn(true)
        `when`(vibrator.areAllPrimitivesSupported(PRIMITIVE_TICK)).thenReturn(true)
        `when`(vibrator.areAllPrimitivesSupported(PRIMITIVE_LOW_TICK)).thenReturn(true)
        `when`(vibrator.getPrimitiveDurations(PRIMITIVE_LOW_TICK)).thenReturn(intArrayOf(10))

        underTest = VibratorWrapper(context, settingsCache, tracker)
    }

    @Test
    fun init_register_onChangeListener() {
        TestUtil.runOnExecutorSync(Executors.MAIN_EXECUTOR) {}
        verify(settingsCache).register(HAPTIC_FEEDBACK_URI, underTest.mHapticChangeListener)
    }

    @Test
    fun vibrate() {
        underTest.vibrate(OVERVIEW_HAPTIC)

        awaitTasksCompleted()
        verify(vibrator).vibrate(OVERVIEW_HAPTIC, VIBRATION_ATTRS)
    }

    @Test
    fun vibrate_primitive_id() {
        underTest.vibrate(PRIMITIVE_TICK, 1f, OVERVIEW_HAPTIC)

        awaitTasksCompleted()
        verify(vibrator).vibrate(vibrationEffectCaptor.capture(), same(VIBRATION_ATTRS))
        val expectedEffect =
            VibrationEffect.startComposition().addPrimitive(PRIMITIVE_TICK, 1f).compose()
        assertThat(vibrationEffectCaptor.value).isEqualTo(expectedEffect)
    }

    @Test
    fun vibrate_with_invalid_primitive_id_use_fallback_effect() {
        underTest.vibrate(-1, 1f, OVERVIEW_HAPTIC)

        awaitTasksCompleted()
        verify(vibrator).vibrate(OVERVIEW_HAPTIC, VIBRATION_ATTRS)
    }

    @Test
    fun vibrate_for_taskbar_unstash() {
        underTest.vibrateForTaskbarUnstash()

        awaitTasksCompleted()
        verify(vibrator).vibrate(vibrationEffectCaptor.capture(), same(VIBRATION_ATTRS))
        val expectedEffect =
            VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_LOW_TICK, VibratorWrapper.LOW_TICK_SCALE)
                .compose()
        assertThat(vibrationEffectCaptor.value).isEqualTo(expectedEffect)
    }

    @Test
    fun haptic_feedback_disabled_no_vibrate() {
        `when`(vibrator.hasVibrator()).thenReturn(false)
        underTest = VibratorWrapper(context, settingsCache, tracker)

        underTest.vibrate(OVERVIEW_HAPTIC)

        awaitTasksCompleted()
        verify(vibrator, never())
            .vibrate(any(VibrationEffect::class.java), any(AudioAttributes::class.java))
    }

    @Test
    fun cancel_vibrate() {
        underTest.cancelVibrate()

        awaitTasksCompleted()
        verify(vibrator).cancel()
    }

    private fun awaitTasksCompleted() {
        Executors.UI_HELPER_EXECUTOR.submit<Any> { null }.get()
    }
}
