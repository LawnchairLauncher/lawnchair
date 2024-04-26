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

package com.android.launcher3.celllayout

import android.content.Context
import android.util.ArrayMap
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.CellLayout
import com.android.launcher3.Reorderable
import com.android.launcher3.celllayout.ReorderPreviewAnimation.Companion.HINT_DURATION
import com.android.launcher3.celllayout.ReorderPreviewAnimation.Companion.PREVIEW_DURATION
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_REORDER_BOUNCE_OFFSET
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class Mock(context: Context) : Reorderable, View(context) {

    init {
        mLeft = 0
        mRight = 100
    }

    private val translateDelegate = MultiTranslateDelegate(this)

    private var scaleForReorderBounce = 1f
    override fun getTranslateDelegate(): MultiTranslateDelegate {
        return translateDelegate
    }

    override fun setReorderBounceScale(scale: Float) {
        scaleForReorderBounce = scale
    }

    override fun getReorderBounceScale(): Float {
        return scaleForReorderBounce
    }

    fun toAnimationValues(): AnimationValues {
        return AnimationValues(
            (translateDelegate.getTranslationX(INDEX_REORDER_BOUNCE_OFFSET).value * 100).toInt(),
            (translateDelegate.getTranslationY(INDEX_REORDER_BOUNCE_OFFSET).value * 100).toInt(),
            (scaleForReorderBounce * 100).toInt()
        )
    }
}

data class AnimationValues(val dx: Int, val dy: Int, val scale: Int)

@SmallTest
@RunWith(AndroidJUnit4::class)
class ReorderPreviewAnimationTest {

    @JvmField @Rule var cellLayoutBuilder = UnitTestCellLayoutBuilderRule()

    private val applicationContext =
        ActivityContextWrapper(ApplicationProvider.getApplicationContext())

    /**
     * @param animationTime the time of the animation we will check the state against.
     * @param mode the mode either PREVIEW_DURATION or HINT_DURATION.
     * @param valueToMatch the state of the animation we expect to see at animationTime.
     * @param isAfterReverse if the animation is finish and we are returning to the beginning.
     */
    private fun testAnimationAtGivenProgress(
        animationTime: Int,
        mode: Int,
        valueToMatch: AnimationValues
    ) {
        val view = Mock(applicationContext)
        val cellLayout = cellLayoutBuilder.createCellLayout(100, 100, false)
        val map = ArrayMap<Reorderable, ReorderPreviewAnimation<Mock>>()
        val animation =
            ReorderPreviewAnimation(
                view,
                mode,
                3,
                3,
                1,
                7,
                1,
                1,
                CellLayout.REORDER_PREVIEW_MAGNITUDE,
                cellLayout,
                map
            )
        // Remove delay because it's randomly generated and it can slightly change the results.
        animation.animator.startDelay = 0
        animation.animator.currentPlayTime = animationTime.toLong()
        val currentValue = view.toAnimationValues()
        assert(currentValue == valueToMatch) {
            "The value of the animation $currentValue at $animationTime time (milliseconds) doesn't match the given value $valueToMatch"
        }
    }

    @Test
    fun testAnimationModePreview() {
        testAnimationAtGivenProgress(
            PREVIEW_DURATION * 0,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 0, dy = 0, scale = 100)
        )
        testAnimationAtGivenProgress(
            PREVIEW_DURATION / 2,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 2, dy = -5, scale = 98)
        )
        testAnimationAtGivenProgress(
            PREVIEW_DURATION / 3,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 1, dy = -2, scale = 99)
        )
        testAnimationAtGivenProgress(
            PREVIEW_DURATION,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 5, dy = -10, scale = 96)
        )

        // MODE_PREVIEW oscillates and goes back to 0,0
        testAnimationAtGivenProgress(
            PREVIEW_DURATION * 2,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 0, dy = 0, scale = 100)
        )
        // (b/339313407) Temporarily disable this test as the behavior is
        // inconsistent between Soong & Gradle builds.
        //
        // testAnimationAtGivenProgress(
        //     PREVIEW_DURATION * 99,
        //     ReorderPreviewAnimation.MODE_PREVIEW,
        //     AnimationValues(dx = 5, dy = -10, scale = 96)
        // )
        testAnimationAtGivenProgress(
            PREVIEW_DURATION * 98,
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 0, dy = 0, scale = 100)
        )
        testAnimationAtGivenProgress(
            (PREVIEW_DURATION * 1.5).toInt(),
            ReorderPreviewAnimation.MODE_PREVIEW,
            AnimationValues(dx = 2, dy = -5, scale = 98)
        )
    }

    @Test
    fun testAnimationModeHint() {
        testAnimationAtGivenProgress(
            HINT_DURATION * 0,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = 0, dy = 0, scale = 100)
        )
        testAnimationAtGivenProgress(
            HINT_DURATION,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -5, dy = 10, scale = 96)
        )
        testAnimationAtGivenProgress(
            HINT_DURATION / 2,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -2, dy = 5, scale = 98)
        )
        testAnimationAtGivenProgress(
            HINT_DURATION / 3,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -1, dy = 2, scale = 99)
        )
        testAnimationAtGivenProgress(
            HINT_DURATION,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -5, dy = 10, scale = 96)
        )

        // After one cycle the animationValues should always be the top values and don't cycle.
        testAnimationAtGivenProgress(
            HINT_DURATION * 2,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -5, dy = 10, scale = 96)
        )
        testAnimationAtGivenProgress(
            HINT_DURATION * 99,
            ReorderPreviewAnimation.MODE_HINT,
            AnimationValues(dx = -5, dy = 10, scale = 96)
        )
    }
}
