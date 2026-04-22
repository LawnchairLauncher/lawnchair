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
 * limitations under the License.
 */

package com.android.launcher3.tapl

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.uiautomator.UiObject2
import org.junit.Assert

/**
 * **THE BubbleBar CONSTRUCTOR IS NOT INTENDED TO BE USED FROM TESTS**
 *
 * Provides an API for interacting with the bubble bar within launcher automation tests tests.
 *
 * Note that this class does not represent the state of the bubble bar being stashed.
 */
class BubbleBar(private val mLauncher: LauncherInstrumentation) {

    private val bubbleBarViewSelector = mLauncher.getLauncherObjectSelector(RES_ID_NAME_BUBBLE_BAR)
    private val dismissViewSelector = mLauncher.getLauncherObjectSelector(RES_ID_NAME_DISMISS_VIEW)

    init {
        Assert.assertFalse(bubbles.isEmpty())
    }

    /**
     * Returns the selected bubble in the bubble bar.
     *
     * Bubbles in the collapsed bubble bar are reversed. The selected bubble is the last bubble in
     * the view hierarchy.
     */
    private val selectedBubble: UiObject2
        get() = bubbles.last()

    /**
     * Returns all the bubbles in the bubble bar.
     *
     * Note that the overflow bubble is not included in the result because it is never visible when
     * the bubble bar is collapsed.
     */
    private val bubbles: List<UiObject2>
        get() = mLauncher.waitForLauncherObject(bubbleBarViewSelector).children

    /** Collapse the bubble bar if it is expanded */
    fun collapse() {
        mLauncher.eventsCheck().use {
            verifyExpanded {
                mLauncher.waitForSystemUiObject(RES_ID_EXPANDED_VIEW)
                mLauncher.addContextLayer("Clicked to collapse bubble bar").use {
                    selectedBubble.click()
                    verifyCollapsed()
                }
            }
        }
    }

    /** Expands the bubble bar if it is collapsed */
    fun expand() {
        mLauncher.eventsCheck().use {
            verifyCollapsed {
                mLauncher.addContextLayer("Expand bubble bar").use {
                    mLauncher.waitForLauncherObject(bubbleBarViewSelector).click()
                    verifyExpanded()
                }
            }
        }
    }

    /** Verifies that the bubble bar is collapsed. */
    fun verifyCollapsed(furtherChecks: (() -> Unit)? = null) {
        mLauncher.addContextLayer("Check bubble bar expanded view is gone").use {
            mLauncher.waitUntilSystemUiObjectGone(RES_ID_EXPANDED_VIEW)
            furtherChecks?.invoke()
        }
    }

    /** Verifies that the bubble bar is expanded. */
    fun verifyExpanded(furtherChecks: (() -> Unit)? = null) {
        mLauncher.addContextLayer("Check bubble bar expanded view is visible").use {
            mLauncher.waitForSystemUiObject(RES_ID_EXPANDED_VIEW)
            furtherChecks?.invoke()
        }
    }

    /**
     * Drags the bubble bar to the dismiss target. At the end of the gesture the bubble bar will be
     * gone.
     */
    fun dragToDismiss() {
        mLauncher.eventsCheck().use {
            mLauncher.addContextLayer("Bubble bar dragToDismiss").use {
                val downTime = SystemClock.uptimeMillis()
                val dragStart = mLauncher.waitForLauncherObject(bubbleBarViewSelector).visibleCenter
                mLauncher.sendPointer(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    dragStart,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER,
                )
                val endPoint = mLauncher.waitForLauncherObject(dismissViewSelector).visibleCenter

                mLauncher.movePointer(
                    dragStart,
                    endPoint,
                    LaunchedAppState.DEFAULT_DRAG_STEPS,
                    /* isDecelerating= */ true,
                    downTime,
                    SystemClock.uptimeMillis(),
                    /* slowDown= */ false,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER,
                )

                mLauncher.sendPointer(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    endPoint,
                    LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER,
                )

                mLauncher.addContextLayer("Wait until bubble bar is gone").use {
                    mLauncher.waitUntilLauncherObjectGone(bubbleBarViewSelector)
                }
            }
        }
    }

    companion object {
        const val RES_ID_NAME_BUBBLE_BAR = "taskbar_bubbles"
        const val RES_ID_NAME_DISMISS_VIEW = "dismiss_view"
        const val RES_ID_EXPANDED_VIEW = "bubble_expanded_view"
    }
}
