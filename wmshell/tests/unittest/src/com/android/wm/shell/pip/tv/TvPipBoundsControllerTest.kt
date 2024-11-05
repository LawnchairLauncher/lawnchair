/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.pip.tv

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.Handler
import android.os.test.TestLooper
import android.testing.AndroidTestingRunner

import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_RIGHT
import com.android.wm.shell.pip.tv.TvPipBoundsController.POSITION_DEBOUNCE_TIMEOUT_MILLIS
import com.android.wm.shell.pip.tv.TvPipKeepClearAlgorithm.Placement

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.AdditionalAnswers.returnsFirstArg
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunWith(AndroidTestingRunner::class)
class TvPipBoundsControllerTest : ShellTestCase() {
    val ANIMATION_DURATION = 100
    val STASH_DURATION = 5000
    val FAR_FUTURE = 60 * 60000L
    val ANCHOR_BOUNDS = Rect(90, 90, 100, 100)
    val STASHED_BOUNDS = Rect(99, 90, 109, 100)
    val MOVED_BOUNDS = Rect(90, 80, 100, 90)
    val STASHED_MOVED_BOUNDS = Rect(99, 80, 109, 90)
    val ANCHOR_PLACEMENT = Placement(ANCHOR_BOUNDS, ANCHOR_BOUNDS)
    val STASHED_PLACEMENT = Placement(STASHED_BOUNDS, ANCHOR_BOUNDS,
            STASH_TYPE_RIGHT, ANCHOR_BOUNDS, false)
    val STASHED_PLACEMENT_RESTASH = Placement(STASHED_BOUNDS, ANCHOR_BOUNDS,
            STASH_TYPE_RIGHT, ANCHOR_BOUNDS, true)
    val MOVED_PLACEMENT = Placement(MOVED_BOUNDS, ANCHOR_BOUNDS)
    val STASHED_MOVED_PLACEMENT = Placement(STASHED_MOVED_BOUNDS, ANCHOR_BOUNDS,
            STASH_TYPE_RIGHT, MOVED_BOUNDS, false)
    val STASHED_MOVED_PLACEMENT_RESTASH = Placement(STASHED_MOVED_BOUNDS, ANCHOR_BOUNDS,
            STASH_TYPE_RIGHT, MOVED_BOUNDS, true)

    lateinit var boundsController: TvPipBoundsController
    var time = 0L
    lateinit var testLooper: TestLooper
    lateinit var mainHandler: Handler

    var inMenu = false
    var inMoveMode = false

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var resources: Resources
    @Mock
    lateinit var tvPipBoundsState: TvPipBoundsState
    @Mock
    lateinit var tvPipBoundsAlgorithm: TvPipBoundsAlgorithm
    @Mock
    lateinit var listener: TvPipBoundsController.PipBoundsListener

    @Before
    fun setUp() {
        if (!isTelevision) {
            return
        }
        MockitoAnnotations.initMocks(this)
        time = 0L
        inMenu = false
        inMoveMode = false

        testLooper = TestLooper { time }
        mainHandler = Handler(testLooper.getLooper())

        whenever(mockContext.resources).thenReturn(resources)
        whenever(resources.getInteger(R.integer.config_pipStashDuration)).thenReturn(STASH_DURATION)
        whenever(tvPipBoundsAlgorithm.adjustBoundsForTemporaryDecor(any()))
                .then(returnsFirstArg<Rect>())

        boundsController = TvPipBoundsController(
                mockContext,
                { time },
                mainHandler,
                tvPipBoundsState,
                tvPipBoundsAlgorithm)
        boundsController.setListener(listener)
    }

    @Test
    fun testPlacement_MovedAfterDebounceTimeout() {
        assumeTelevision()
        triggerPlacement(MOVED_PLACEMENT)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, MOVED_BOUNDS)
        assertNoMovementUpTo(time + FAR_FUTURE)
    }

    @Test
    fun testStashedPlacement_MovedAfterDebounceTimeout_Unstashes() {
        assumeTelevision()
        triggerPlacement(STASHED_PLACEMENT_RESTASH)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, STASHED_BOUNDS)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS + STASH_DURATION, ANCHOR_BOUNDS)
    }

    @Test
    fun testDebounceSamePlacement_MovesDebounceTimeoutAfterFirstPlacement() {
        assumeTelevision()
        triggerPlacement(MOVED_PLACEMENT)
        advanceTimeTo(POSITION_DEBOUNCE_TIMEOUT_MILLIS / 2)
        triggerPlacement(MOVED_PLACEMENT)

        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, MOVED_BOUNDS)
    }

    @Test
    fun testNoMovementUntilPlacementStabilizes() {
        assumeTelevision()
        triggerPlacement(ANCHOR_PLACEMENT)
        advanceTimeTo(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS / 10)
        triggerPlacement(MOVED_PLACEMENT)
        advanceTimeTo(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS / 10)
        triggerPlacement(ANCHOR_PLACEMENT)
        advanceTimeTo(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS / 10)
        triggerPlacement(MOVED_PLACEMENT)

        assertMovementAt(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS, MOVED_BOUNDS)
    }

    @Test
    fun testUnstashIfStashNoLongerNecessary() {
        assumeTelevision()
        triggerPlacement(STASHED_PLACEMENT_RESTASH)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, STASHED_BOUNDS)

        triggerPlacement(ANCHOR_PLACEMENT)
        assertMovementAt(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS, ANCHOR_BOUNDS)
    }

    @Test
    fun testRestashingPlacementDelaysUnstash() {
        assumeTelevision()
        triggerPlacement(STASHED_PLACEMENT_RESTASH)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, STASHED_BOUNDS)

        assertNoMovementUpTo(time + STASH_DURATION / 2)
        triggerPlacement(STASHED_PLACEMENT_RESTASH)
        assertNoMovementUpTo(time + POSITION_DEBOUNCE_TIMEOUT_MILLIS)
        assertMovementAt(time + STASH_DURATION, ANCHOR_BOUNDS)
    }

    @Test
    fun testNonRestashingPlacementDoesNotDelayUnstash() {
        assumeTelevision()
        triggerPlacement(STASHED_PLACEMENT_RESTASH)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS, STASHED_BOUNDS)

        assertNoMovementUpTo(time + STASH_DURATION / 2)
        triggerPlacement(STASHED_PLACEMENT)
        assertMovementAt(POSITION_DEBOUNCE_TIMEOUT_MILLIS + STASH_DURATION, ANCHOR_BOUNDS)
    }

    @Test
    fun testImmediatePlacement() {
        assumeTelevision()
        triggerImmediatePlacement(STASHED_PLACEMENT_RESTASH)
        assertMovement(STASHED_BOUNDS)
        assertMovementAt(time + STASH_DURATION, ANCHOR_BOUNDS)
    }

    @Test
    fun testImmediatePlacement_DoNotStashIfAlreadyUnstashed() {
        assumeTelevision()
        triggerImmediatePlacement(STASHED_PLACEMENT_RESTASH)
        assertMovement(STASHED_BOUNDS)
        assertMovementAt(time + STASH_DURATION, ANCHOR_BOUNDS)

        triggerImmediatePlacement(STASHED_PLACEMENT)
        assertNoMovementUpTo(time + FAR_FUTURE)
    }

    @Test
    fun testInMoveMode_KeepAtAnchor() {
        assumeTelevision()
        startMoveMode()
        triggerImmediatePlacement(STASHED_MOVED_PLACEMENT_RESTASH)
        assertMovement(ANCHOR_BOUNDS)
        assertNoMovementUpTo(time + FAR_FUTURE)
    }

    @Test
    fun testInMenu_Unstashed() {
        assumeTelevision()
        openPipMenu()
        triggerImmediatePlacement(STASHED_MOVED_PLACEMENT_RESTASH)
        assertMovement(MOVED_BOUNDS)
        assertNoMovementUpTo(time + FAR_FUTURE)
    }

    @Test
    fun testCloseMenu_DoNotRestash() {
        assumeTelevision()
        openPipMenu()
        triggerImmediatePlacement(STASHED_MOVED_PLACEMENT_RESTASH)
        assertMovement(MOVED_BOUNDS)

        closePipMenu()
        triggerPlacement(STASHED_MOVED_PLACEMENT)
        assertNoMovementUpTo(time + FAR_FUTURE)
    }

    fun assertMovement(bounds: Rect) {
        verify(listener).onPipTargetBoundsChange(eq(bounds), anyInt())
        reset(listener)
    }

    fun assertMovementAt(timeMs: Long, bounds: Rect) {
        assertNoMovementUpTo(timeMs - 1)
        advanceTimeTo(timeMs)
        assertMovement(bounds)
    }

    fun assertNoMovementUpTo(timeMs: Long) {
        advanceTimeTo(timeMs)
        verify(listener, never()).onPipTargetBoundsChange(any(), anyInt())
    }

    fun triggerPlacement(placement: Placement, immediate: Boolean = false) {
        whenever(tvPipBoundsAlgorithm.getTvPipPlacement()).thenReturn(placement)
        val stayAtAnchorPosition = inMoveMode
        val disallowStashing = inMenu || stayAtAnchorPosition
        boundsController.recalculatePipBounds(stayAtAnchorPosition, disallowStashing,
                ANIMATION_DURATION, immediate)
    }

    fun triggerImmediatePlacement(placement: Placement) {
        triggerPlacement(placement, true)
    }

    fun openPipMenu() {
        inMenu = true
        inMoveMode = false
    }

    fun closePipMenu() {
        inMenu = false
        inMoveMode = false
    }

    fun startMoveMode() {
        inMenu = true
        inMoveMode = true
    }

    fun advanceTimeTo(ms: Long) {
        time = ms
        testLooper.dispatchAll()
    }
}
