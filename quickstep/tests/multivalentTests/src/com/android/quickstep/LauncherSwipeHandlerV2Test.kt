/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.quickstep

import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManagerGlobal
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.DisplayAdjustments.DEFAULT_DISPLAY_ADJUSTMENTS
import android.view.DisplayInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppModule
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.systemui.contextualeducation.GestureType
import com.android.systemui.shared.system.InputConsumerController
import dagger.BindsInstance
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class LauncherSwipeHandlerV2Test {

    @Mock private lateinit var taskAnimationManager: TaskAnimationManager

    private lateinit var gestureState: GestureState
    @Mock private lateinit var inputConsumerController: InputConsumerController

    @Mock private lateinit var systemUiProxy: SystemUiProxy

    @Mock private lateinit var msdlPlayerWrapper: MSDLPlayerWrapper

    private lateinit var underTest: LauncherSwipeHandlerV2

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    private val launcherModelHelper = LauncherModelHelper()
    private val sandboxContext = launcherModelHelper.sandboxContext

    private val flingSpeed =
        -(sandboxContext.resources.getDimension(R.dimen.quickstep_fling_threshold_speed) + 1)

    private val displayManager: DisplayManager =
        sandboxContext.spyService(DisplayManager::class.java)

    @Before
    fun setup() {
        val display =
            Display(
                DisplayManagerGlobal.getInstance(),
                DEFAULT_DISPLAY,
                DisplayInfo(),
                DEFAULT_DISPLAY_ADJUSTMENTS,
            )
        whenever(displayManager.getDisplay(eq(DEFAULT_DISPLAY))).thenReturn(display)
        whenever(displayManager.displays).thenReturn(arrayOf(display))

        sandboxContext.initDaggerComponent(
            DaggerTestComponent.builder()
                .bindSystemUiProxy(systemUiProxy)
                .bindRotationHelper(mock(RotationTouchHelper::class.java))
                .bindRecentsState(mock(RecentsAnimationDeviceState::class.java))
        )
        gestureState =
            spy(
                GestureState(
                    OverviewComponentObserver.INSTANCE.get(sandboxContext),
                    DEFAULT_DISPLAY,
                    0,
                )
            )

        underTest =
            LauncherSwipeHandlerV2(
                sandboxContext,
                taskAnimationManager,
                gestureState,
                0,
                false,
                inputConsumerController,
                msdlPlayerWrapper,
            )
        underTest.onGestureStarted(/* isLikelyToStartNewTask= */ false)
    }

    @Test
    fun goHomeFromAppByTrackpad_updateEduStats() {
        gestureState.setTrackpadGestureType(GestureState.TrackpadGestureType.THREE_FINGER)
        underTest.onGestureEnded(flingSpeed, PointF(), /* horizontalTouchSlopPassed= */ false)
        verify(systemUiProxy)
            .updateContextualEduStats(/* isTrackpadGesture= */ eq(true), eq(GestureType.HOME))
    }

    @Test
    fun goHomeFromAppByTouch_updateEduStats() {
        underTest.onGestureEnded(flingSpeed, PointF(), /* horizontalTouchSlopPassed= */ false)
        verify(systemUiProxy)
            .updateContextualEduStats(/* isTrackpadGesture= */ eq(false), eq(GestureType.HOME))
    }
}

@LauncherAppSingleton
@Component(modules = [LauncherAppModule::class])
interface TestComponent : LauncherAppComponent {
    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindSystemUiProxy(proxy: SystemUiProxy): Builder

        @BindsInstance fun bindRotationHelper(helper: RotationTouchHelper): Builder

        @BindsInstance fun bindRecentsState(state: RecentsAnimationDeviceState): Builder

        override fun build(): TestComponent
    }
}
