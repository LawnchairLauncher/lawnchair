/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * limitations under the License.f
 */
package com.android.wm.shell.back

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.AppCompatTaskInfo
import android.app.WindowConfiguration
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.RemoteException
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.RemoteAnimationTarget
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.animation.Animation
import android.window.BackEvent
import android.window.BackMotionEvent
import android.window.BackNavigationInfo
import androidx.test.filters.SmallTest
import com.android.internal.policy.TransitionAnimation
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import junit.framework.TestCase.assertEquals
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class CustomCrossActivityBackAnimationTest : ShellTestCase() {
    @Mock private lateinit var backAnimationBackground: BackAnimationBackground
    @Mock private lateinit var mockCloseAnimation: Animation
    @Mock private lateinit var mockOpenAnimation: Animation
    @Mock private lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var transitionAnimation: TransitionAnimation
    @Mock private lateinit var appCompatTaskInfo: AppCompatTaskInfo
    @Mock private lateinit var transaction: Transaction
    @Mock private lateinit var handler: Handler

    private lateinit var customCrossActivityBackAnimation: CustomCrossActivityBackAnimation
    private lateinit var customAnimationLoader: CustomAnimationLoader

    @Before
    @Throws(Exception::class)
    fun setUp() {
        customAnimationLoader = CustomAnimationLoader(transitionAnimation)
        customCrossActivityBackAnimation =
            CustomCrossActivityBackAnimation(
                context,
                backAnimationBackground,
                rootTaskDisplayAreaOrganizer,
                transaction,
                customAnimationLoader,
                handler,
            )

        whenever(transitionAnimation.loadAppTransitionAnimation(eq(PACKAGE_NAME), eq(OPEN_RES_ID)))
            .thenReturn(mockOpenAnimation)
        whenever(transitionAnimation.loadAppTransitionAnimation(eq(PACKAGE_NAME), eq(CLOSE_RES_ID)))
            .thenReturn(mockCloseAnimation)
        whenever(transaction.setColor(any(), any())).thenReturn(transaction)
        whenever(transaction.setAlpha(any(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setCrop(any(), any())).thenReturn(transaction)
        whenever(transaction.setRelativeLayer(any(), any(), anyInt())).thenReturn(transaction)
        spy(customCrossActivityBackAnimation)
    }

    @Test
    @Throws(InterruptedException::class)
    fun receiveFinishAfterInvoke() {
        val finishCalled = startCustomAnimation()
        try {
            customCrossActivityBackAnimation.getRunner().callback.onBackInvoked()
        } catch (r: RemoteException) {
            Assert.fail("onBackInvoked throw remote exception")
        }
        finishCalled.await(1, TimeUnit.SECONDS)
    }

    @Test
    @Throws(InterruptedException::class)
    fun receiveFinishAfterCancel() {
        val finishCalled = startCustomAnimation()
        try {
            customCrossActivityBackAnimation.getRunner().callback.onBackCancelled()
        } catch (r: RemoteException) {
            Assert.fail("onBackCancelled throw remote exception")
        }
        finishCalled.await(1, TimeUnit.SECONDS)
    }

    @Test
    @Throws(InterruptedException::class)
    fun receiveFinishWithoutAnimationAfterInvoke() {
        val finishCalled = startCustomAnimation(targets = arrayOf())
        try {
            customCrossActivityBackAnimation.getRunner().callback.onBackInvoked()
        } catch (r: RemoteException) {
            Assert.fail("onBackInvoked throw remote exception")
        }
        finishCalled.await(1, TimeUnit.SECONDS)
    }

    @Test
    fun testLoadCustomAnimation() {
        testLoadCustomAnimation(OPEN_RES_ID, CLOSE_RES_ID, 0)
    }

    @Test
    fun testLoadCustomAnimationNoEnter() {
        testLoadCustomAnimation(0, CLOSE_RES_ID, 0)
    }

    @Test
    fun testLoadWindowAnimations() {
        testLoadCustomAnimation(0, 0, 30)
    }

    @Test
    fun testCustomAnimationHigherThanWindowAnimations() {
        testLoadCustomAnimation(OPEN_RES_ID, CLOSE_RES_ID, 30)
    }

    private fun testLoadCustomAnimation(enterResId: Int, exitResId: Int, windowAnimations: Int) {
        val builder =
            BackNavigationInfo.Builder()
                .setCustomAnimation(PACKAGE_NAME, enterResId, exitResId, Color.GREEN)
                .setWindowAnimations(PACKAGE_NAME, windowAnimations)
        val info = builder.build().customAnimationInfo!!
        whenever(
                transitionAnimation.loadAnimationAttr(
                    eq(PACKAGE_NAME),
                    eq(windowAnimations),
                    anyInt(),
                    anyBoolean()
                )
            )
            .thenReturn(mockCloseAnimation)
        whenever(transitionAnimation.loadDefaultAnimationAttr(anyInt(), anyBoolean()))
            .thenReturn(mockOpenAnimation)
        val result = customAnimationLoader.loadAll(info)!!
        if (exitResId != 0) {
            if (enterResId == 0) {
                verify(transitionAnimation, never())
                    .loadAppTransitionAnimation(eq(PACKAGE_NAME), eq(enterResId))
                verify(transitionAnimation).loadDefaultAnimationAttr(anyInt(), anyBoolean())
            } else {
                assertEquals(result.enterAnimation, mockOpenAnimation)
            }
            assertEquals(result.backgroundColor.toLong(), Color.GREEN.toLong())
            assertEquals(result.closeAnimation, mockCloseAnimation)
            verify(transitionAnimation, never())
                .loadAnimationAttr(eq(PACKAGE_NAME), anyInt(), anyInt(), anyBoolean())
        } else if (windowAnimations != 0) {
            verify(transitionAnimation, times(2))
                .loadAnimationAttr(eq(PACKAGE_NAME), anyInt(), anyInt(), anyBoolean())
            Assert.assertEquals(result.closeAnimation, mockCloseAnimation)
        }
    }

    private fun startCustomAnimation(
        targets: Array<RemoteAnimationTarget> =
            arrayOf(createAnimationTarget(false), createAnimationTarget(true))
    ): CountDownLatch {
        val backNavigationInfo =
            BackNavigationInfo.Builder()
                .setCustomAnimation(PACKAGE_NAME, OPEN_RES_ID, CLOSE_RES_ID, /*backgroundColor*/ 0)
                .build()
        customCrossActivityBackAnimation.prepareNextAnimation(
            backNavigationInfo.customAnimationInfo,
            0
        )
        val finishCalled = CountDownLatch(1)
        val finishCallback = Runnable { finishCalled.countDown() }
        customCrossActivityBackAnimation
            .getRunner()
            .startAnimation(targets, null, null, finishCallback)
        customCrossActivityBackAnimation.runner.callback.onBackStarted(backMotionEventFrom(0f, 0f))
        if (targets.isNotEmpty()) {
            verify(mockCloseAnimation)
                .initialize(eq(BOUND_SIZE), eq(BOUND_SIZE), eq(BOUND_SIZE), eq(BOUND_SIZE))
            verify(mockOpenAnimation)
                .initialize(eq(BOUND_SIZE), eq(BOUND_SIZE), eq(BOUND_SIZE), eq(BOUND_SIZE))
        }
        return finishCalled
    }

    private fun backMotionEventFrom(touchX: Float, progress: Float) =
        BackMotionEvent(
            /* touchX = */ touchX,
            /* touchY = */ 0f,
            /* frameTime = */ 0,
            /* progress = */ progress,
            /* triggerBack = */ false,
            /* swipeEdge = */ BackEvent.EDGE_LEFT,
            /* departingAnimationTarget = */ null
        )

    private fun createAnimationTarget(open: Boolean): RemoteAnimationTarget {
        val topWindowLeash = SurfaceControl()
        val taskInfo = RunningTaskInfo()
        taskInfo.appCompatTaskInfo = appCompatTaskInfo
        taskInfo.taskDescription = ActivityManager.TaskDescription()
        return RemoteAnimationTarget(
            1,
            if (open) RemoteAnimationTarget.MODE_OPENING else RemoteAnimationTarget.MODE_CLOSING,
            topWindowLeash,
            false,
            Rect(),
            Rect(),
            -1,
            Point(0, 0),
            Rect(0, 0, BOUND_SIZE, BOUND_SIZE),
            Rect(),
            WindowConfiguration(),
            true,
            null,
            null,
            taskInfo,
            false,
            -1
        )
    }

    companion object {
        private const val BOUND_SIZE = 100
        private const val OPEN_RES_ID = 1000
        private const val CLOSE_RES_ID = 1001
        private const val PACKAGE_NAME = "TestPackage"
    }
}
