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

package com.android.wm.shell.flicker.bubbles.testcase

import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE_TASK_VIEW
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import android.tools.traces.component.IComponentNameMatcher
import com.android.server.wm.flicker.helpers.ImeAppHelper
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * The test cases to verify [testApp] goes to expanded bubble state, which verifies [testApp]
 * replaces [previousApp] to be top and visible and has rounded corner at the end of transition.
 */
interface BubbleAppBecomesExpandedTestCases : BubbleFlickerSubjects {

    val previousApp: IComponentNameMatcher
        get() = LAUNCHER

    /**
     * Verifies the focus changed from [previousApp] to bubble app.
     */
    @Test
    fun focusChanges() {
        eventLogSubject.focusChanges(previousApp.toWindowName(), testApp.toWindowName())
    }

    /**
     * Verifies the bubble app replaces [previousApp] to be the top window.
     */
    @Test
    fun appWindowReplacesPreviousAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(previousApp)
            .then()
            .isAppWindowOnTop(
                ComponentNameMatcher.SNAPSHOT
                    .or(ComponentNameMatcher.SPLASH_SCREEN),
                isOptional = true,
            )
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app is the top window at the end of transition.
     */
    @Test
    fun appWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(testApp)
    }

    /**
     * Verifies the bubble app becomes the top window.
     */
    @Test
    fun appWindowBecomesTopWindow() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowNotOnTop(testApp)
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app window becomes visible.
     */
    @Test
    fun appWindowBecomesVisible() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app layer becomes visible.
     */
    @Test
    fun appLayerBecomesVisible() {
        layersTraceSubject
            .isInvisible(testApp)
            .then()
            .isVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app window is visible at the end of transition.
     */
    @Test
    fun appWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
    }

    /**
     * Verifies the bubble app layer is visible at the end of transition.
     */
    @Test
    fun appLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /**
     * Verifies the bubble app layer has rounded corners at the end of transition.
     */
    @Test
    fun appLayerHasRoundedCorner() {
        layerTraceEntrySubjectAtEnd.hasRoundedCorners(testApp)
    }

    /**
     * Verifies the bubble window covers the bubble app.
     */
    @Test
    fun bubbleWindowCoversBubbleAppWindow() {
        wmStateSubjectAtEnd.visibleRegion(BUBBLE)
            .coversAtLeast(wmStateSubjectAtEnd.visibleRegion(testApp).region)
    }

    /**
     * Verifies the bubble layer covers the bubble app.
     */
    @Test
    fun bubbleLayerCoversBubbleAppLayer() {
        layerTraceEntrySubjectAtEnd.visibleRegion(BUBBLE)
            .coversAtLeast(layerTraceEntrySubjectAtEnd.visibleRegion(testApp).region)
    }

    /**
     * Verifies whether the below bounds match:
     * - Bubble task bounds in WM hierarchy
     * - Bubble task layer bounds
     * - Bubble task view layer bounds
     */
    @Test
    fun bubbleTaskBoundsMatchBubbleTaskView() {
        // Get the WM task bounds of bubble app.
        val bubbleAppTask = wmStateSubjectAtEnd.wmState.getTaskForActivity(testApp)
            ?: error("Bubble app task not found")
        val taskBounds = bubbleAppTask.bounds
        // Get the task layer bounds of bubble app.
        val taskLayer = layerTraceEntrySubjectAtEnd
            .findAncestorLayer(testApp) { it.isTask }
            ?: error("Bubble app task layer not found")
        val taskLayerBounds = taskLayer.screenBounds
        // Get the bounds of bubble task view layer.
        val bubbleTaskViewLayer = layerTraceEntrySubjectAtEnd
            .findAncestorLayer(testApp) {
                BUBBLE_TASK_VIEW.layerMatchesAnyOf(it)
            } ?: error("Bubble app task view not found")
        val bubbleTaskViewLayerBounds = bubbleTaskViewLayer.screenBounds

        if (testApp is ImeAppHelper) {
            // If the IME shows, the task and task view layer may be resized to fit the IME layer,
            // while WM task bounds remain unchanged.
            bubbleTaskViewLayerBounds.coversExactly(taskLayerBounds.region)
            bubbleTaskViewLayerBounds.coversAtMost(taskBounds)
        } else {
            // Otherwise, bubble task view bounds must match task bounds.
            bubbleTaskViewLayerBounds.coversExactly(taskLayerBounds.region)
            taskLayerBounds.coversExactly(taskBounds)
        }
    }
}