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
package com.android.quickstep.util

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.ComponentName
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.uioverrides.QuickstepLauncher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.atLeastOnce
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class CrossDisplayMoveTransitionTest {
    private val LAUNCHING_TRANSITION_DURATION_MS = 500L
    private val UNLAUNCHING_TRANSITION_DURATION_MS = 250L

    @get:Rule
    val mockitoRule: MockitoRule = MockitoJUnit.rule()

    private val mockLauncher = mock<QuickstepLauncher>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockFinishCallback = mock<IRemoteTransitionFinishedCallback>()

    private lateinit var transition: CrossDisplayMoveTransition

    private inner class TransitionInfoBuilder(
        type: Int = WindowManager.TRANSIT_NONE,
        flags: Int = 0
    ) {
        private val info: TransitionInfo = TransitionInfo(type, flags)
        private var srcDisplayId: Int = 0
        private var dstDisplayId: Int = 1

        var srcRoot: TransitionInfo.Root? = null
            private set
        var dstRoot: TransitionInfo.Root? = null
            private set
        var crossDisplayTask: Change? = null
            private set
        var crossDisplayTaskHasSnapshot: Boolean = false
            private set
        var launcherChange: Change? = null
            private set

        fun setDisplayDirectionality(src: Int, dst: Int): TransitionInfoBuilder {
            srcDisplayId = src
            dstDisplayId = dst
            return this
        }

        private fun addChange(config: (Change) -> Unit): Change {
            val leash = mock<SurfaceControl>()
            val change = TransitionInfo.Change(null /* container */, leash)
            config(change)
            info.addChange(change)
            return change
        }

        fun addRoot(isSrc: Boolean): TransitionInfoBuilder {
            val displayId = if (isSrc) srcDisplayId else dstDisplayId
            val leash = mock<SurfaceControl>()
            val root = TransitionInfo.Root(displayId, leash, 0 /* offsetLeft */, 0 /* offsetTop */)
            if (isSrc) {
                srcRoot = root
            } else {
                dstRoot = root
            }
            info.addRoot(root)
            return this
        }

        fun addCrossDisplayTask(hasSnapshot: Boolean = true): TransitionInfoBuilder {
            crossDisplayTaskHasSnapshot = hasSnapshot
            crossDisplayTask = addChange { change ->
                val taskInfo = ActivityManager.RunningTaskInfo()
                taskInfo.topActivity = ComponentName("app.pkg", "App")
                change.taskInfo = taskInfo
                change.setDisplayId(srcDisplayId, dstDisplayId)
                if (hasSnapshot) {
                    change.setSnapshot(mock(), 0f /* luma */)
                }
            }
            return this
        }

        fun addGenericTask(mode: Int): Change {
            return addChange { change ->
                change.mode = mode
                val taskInfo = ActivityManager.RunningTaskInfo()
                taskInfo.topActivity = ComponentName("com.generic", "GenericActivity")
                change.taskInfo = taskInfo
            }
        }

        fun addLauncherChange(mode: Int = WindowManager.TRANSIT_TO_FRONT): TransitionInfoBuilder {
            launcherChange = addChange { change ->
                val taskInfo: ActivityManager.RunningTaskInfo = mock()
                val componentName = mockLauncher.componentName
                whenever(taskInfo.topActivity).thenReturn(componentName)
                whenever(taskInfo.activityType)
                    .thenReturn(WindowConfiguration.ACTIVITY_TYPE_HOME)
                change.taskInfo = taskInfo
                change.mode = mode
            }
            return this
        }

        fun build(): TransitionInfo = info
    }

    @Before
    fun setup() {
        transition = CrossDisplayMoveTransition(mockLauncher,
            LAUNCHING_TRANSITION_DURATION_MS, UNLAUNCHING_TRANSITION_DURATION_MS)
    }

    @Test
    fun isCrossDisplayMove_withEmptyInfo_returnsFalse() {
        val info = TransitionInfoBuilder().build()
        assertFalse(CrossDisplayMoveTransition.isCrossDisplayMove(info))
    }

    @Test
    fun isCrossDisplayMove_withCrossDisplayTask_returnsTrue() {
        val info = TransitionInfoBuilder().addCrossDisplayTask().build()
        assertTrue(CrossDisplayMoveTransition.isCrossDisplayMove(info))
    }

    @Test
    fun startAnimation_withEmptyInfo_doesNotCrash() {
        val info = TransitionInfoBuilder().build()
        transition.startCrossDisplayMoveAnimation(
            info,
            mockTransaction,
            mockFinishCallback
        )
        // No crash is a pass
    }

    private fun assertValidMoveInfo(
        builder: TransitionInfoBuilder,
        moveInfo: CrossDisplayMoveTransitionInfo?
    ) {
        assertNotNull(moveInfo)
        moveInfo!!

        val crossDisplayTask = builder.crossDisplayTask
        assertNotNull(crossDisplayTask)
        assertEquals(crossDisplayTask!!.startDisplayId, moveInfo.srcDisplayId)
        assertEquals(crossDisplayTask.endDisplayId, moveInfo.dstDisplayId)
        assertEquals(builder.crossDisplayTask, moveInfo.taskMovingBetweenDisplays)
        assertEquals(builder.srcRoot, moveInfo.srcRoot)
        assertEquals(builder.dstRoot, moveInfo.dstRoot)
        assertEquals(builder.launcherChange, moveInfo.launcherToFront)
        if (builder.srcRoot == null) {
            // If there is no srcRoot, then there should never be a srcTaskLeash
            assertNull(moveInfo.srcTaskLeash)
        } else {
            // Otherwise, we should have a srcTaskLeash iff we have a snapshot
            assertEquals(builder.crossDisplayTaskHasSnapshot, moveInfo.srcTaskLeash != null)
        }
    }

    @Test
    fun create_happyPath_returnsValidInfo() {
        val builder = TransitionInfoBuilder()
            .addRoot(isSrc = true)
            .addRoot(isSrc = false)
            .addCrossDisplayTask()
            .addLauncherChange()
        val info = builder.build()
        val moveInfo = CrossDisplayMoveTransitionInfo.create(info)
        assertValidMoveInfo(builder, moveInfo)
    }

    @Test
    fun create_noSrcRoot_returnsValidInfo() {
        val builder = TransitionInfoBuilder()
            .addRoot(isSrc = false)
            .addCrossDisplayTask()
        val info = builder.build()
        val moveInfo = CrossDisplayMoveTransitionInfo.create(info)
        assertValidMoveInfo(builder, moveInfo)
    }

    @Test
    fun create_noLauncherChange_returnsValidInfo() {
        val builder = TransitionInfoBuilder()
            .addRoot(isSrc = true)
            .addRoot(isSrc = false)
            .addCrossDisplayTask()
        val info = builder.build()
        val moveInfo = CrossDisplayMoveTransitionInfo.create(info)
        assertValidMoveInfo(builder, moveInfo)
    }

    @Test
    fun create_missingRequiredInfo_returnsNull() {
        val invalidBuilders =
            listOf(
                // Nothing
                TransitionInfoBuilder(),
                // No task moving between displays
                TransitionInfoBuilder()
                    .addRoot(isSrc = true)
                    .addRoot(isSrc = false)
                    .addLauncherChange(),
                // No destination root
                TransitionInfoBuilder().addRoot(isSrc = true).addCrossDisplayTask()
            )

        invalidBuilders.forEach { builder ->
            val info = builder.build()
            assertNull(CrossDisplayMoveTransitionInfo.create(info))
        }
    }

    private fun verifyInitializedAsVisible(leash: SurfaceControl) {
        verify(mockTransaction, atLeastOnce()).setAlpha(leash, 1f)
        verify(mockTransaction, atLeastOnce()).setPosition(eq(leash), any(), any())
        verify(mockTransaction, atLeastOnce()).show(leash)
    }

    @Test
    fun setupInitialAnimationState_happyPath_reparentsLeashes() {
        val builder = TransitionInfoBuilder()
            .addRoot(isSrc = true)
            .addRoot(isSrc = false)
            .addCrossDisplayTask()
            .addLauncherChange()
        val toFrontChange = builder.addGenericTask(mode = WindowManager.TRANSIT_TO_FRONT)
        val info = builder.build()
        val moveInfo = CrossDisplayMoveTransitionInfo.create(info)!!

        transition.setupInitialAnimationState(info, moveInfo, mockTransaction)

        // Verify reparenting
        verify(mockTransaction).reparent(moveInfo.srcTaskLeash!!, moveInfo.srcRoot!!.leash)
        verify(mockTransaction).reparent(moveInfo.dstTaskLeash, moveInfo.dstRoot.leash)

        // Verify visibility initialization
        verifyInitializedAsVisible(moveInfo.launcherToFront!!.leash)
        verifyInitializedAsVisible(moveInfo.dstTaskLeash)
        verifyInitializedAsVisible(moveInfo.srcTaskLeash!!)
        verifyInitializedAsVisible(toFrontChange.leash)
    }

    @Test
    fun setupInitialAnimationState_noSrcRoot_srcTaskLeashIsNull() {
        val builder = TransitionInfoBuilder()
            .addRoot(isSrc = false)
            .addCrossDisplayTask(hasSnapshot = true)
        val info = builder.build()
        val moveInfo = CrossDisplayMoveTransitionInfo.create(info)!!

        transition.setupInitialAnimationState(info, moveInfo, mockTransaction)

        // Verify reparenting
        verify(mockTransaction).reparent(moveInfo.dstTaskLeash, moveInfo.dstRoot.leash)

        // Null src leash means the src screenshot should have been set to null
        assertNull(moveInfo.srcTaskLeash)
    }
}