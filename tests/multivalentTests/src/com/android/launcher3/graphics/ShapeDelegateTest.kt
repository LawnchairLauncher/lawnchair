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

package com.android.launcher3.graphics

import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit.FILL
import android.graphics.Path
import android.graphics.Path.Direction
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.platform.uiautomatorhelpers.DeviceHelpers.context
import android.view.View
import androidx.core.graphics.PathParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.graphics.ShapeDelegate.Companion.DEFAULT_PATH_SIZE
import com.android.launcher3.graphics.ShapeDelegate.Companion.pickBestShape
import com.android.launcher3.graphics.ShapeDelegate.GenericPathShape
import com.android.launcher3.graphics.ShapeDelegate.RoundedSquare
import com.android.launcher3.icons.RoundRectEstimator
import com.android.launcher3.views.ClipPathView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShapeDelegateTest {

    @Test
    fun `pickBestShape picks rounded rect`() {
        val factor = 0.5f
        var shape = pickBestShape(roundedRectPath(factor), roundedRectString(factor))
        assertThat(shape).isInstanceOf(RoundedSquare::class.java)
        assertThat((shape as RoundedSquare).radiusRatio).isEqualTo(factor)

        val factor2 = 0.2f
        shape = pickBestShape(roundedRectPath(factor2), roundedRectString(factor2))
        assertThat(shape).isInstanceOf(RoundedSquare::class.java)
        assertThat((shape as RoundedSquare).radiusRatio).isEqualTo(factor2)
    }

    @Test
    fun `pickBestShape picks generic shape`() {
        val path = cookiePath()
        val pathStr = FOUR_SIDED_COOKIE
        val shape = pickBestShape(path, pathStr)
        assertThat(shape).isInstanceOf(GenericPathShape::class.java)

        assertThat(RoundRectEstimator.estimateRadius(path, DEFAULT_PATH_SIZE)).isLessThan(0f)
    }

    @Test
    fun `generic shape creates smooth animation`() {
        val animationTestSize = 1000
        val shape = GenericPathShape(FOUR_SIDED_COOKIE)
        val target = TestClipView()
        val anim =
            shape.createRevealAnimator(
                target,
                Rect(0, 0, animationTestSize, animationTestSize),
                Rect(0, 0, animationTestSize, animationTestSize),
                animationTestSize * .25f,
                false,
            )

        // Verify that the start rect is similar to initial path
        anim.setCurrentFraction(0f)
        assertThat(
                getAreaDiff(
                    target.currentClip!!,
                    cookiePath().resize(animationTestSize),
                    animationTestSize,
                )
            )
            .isLessThan(animationTestSize)

        // Verify that end rect is similar to end path
        anim.setCurrentFraction(1f)
        assertThat(
                getAreaDiff(
                    target.currentClip!!,
                    roundedRectPath(0.5f).resize(animationTestSize),
                    animationTestSize,
                )
            )
            .isLessThan(animationTestSize)

        // Ensure that when running animation, area increases smoothly. We run the animation over
        // [steps] and verify increase of max 5 times the linear diff increase
        val steps = 1000
        val incrementalDiff =
            getAreaDiff(
                cookiePath().resize(animationTestSize),
                roundedRectPath(0.5f).resize(animationTestSize),
                animationTestSize,
            ) * 5 / steps
        var lastPath = cookiePath().resize(animationTestSize)
        for (progress in 1..steps) {
            anim.setCurrentFraction(progress / 1000f)
            val currentPath = Path(target.currentClip!!)
            assertThat(getAreaDiff(lastPath, currentPath, animationTestSize))
                .isLessThan(incrementalDiff)
            lastPath = currentPath
        }
        assertThat(
                getAreaDiff(
                    lastPath,
                    roundedRectPath(0.5f).resize(animationTestSize),
                    animationTestSize,
                )
            )
            .isLessThan(animationTestSize)
    }

    private fun roundedRectPath(factor: Float) =
        Path().apply {
            val r = factor * DEFAULT_PATH_SIZE / 2
            addRoundRect(0f, 0f, DEFAULT_PATH_SIZE, DEFAULT_PATH_SIZE, r, r, Direction.CW)
        }

    private fun roundedRectString(factor: Float): String {
        val s = 100f
        val r = (factor * s / 2)
        val t = s - r
        return "M $r 0 " +
            "L $t 0 " +
            "A $r $r 0 0 1 $s $r " +
            "L $s $t " +
            "A $r $r 0 0 1 $t $s " +
            "L $r $s " +
            "A $r $r 0 0 1 0 $t " +
            "L 0 $r " +
            "A $r $r 0 0 1 $r 0 Z"
    }

    private fun getAreaDiff(p1: Path, p2: Path, maxSize: Int): Int {
        val fullRegion = Region(0, 0, maxSize, maxSize)
        val iconRegion = Region().apply { setPath(p1, fullRegion) }
        val shapeRegion = Region().apply { setPath(p2, fullRegion) }
        shapeRegion.op(iconRegion, Region.Op.XOR)
        return RoundRectEstimator.getArea(shapeRegion)
    }

    class TestClipView : View(context), ClipPathView {

        var currentClip: Path? = null

        override fun setClipPath(clipPath: Path?) {
            currentClip = clipPath
        }
    }

    companion object {
        const val FOUR_SIDED_COOKIE =
            "M63.605 3C84.733 -6.176 106.176 15.268 97 36.395L95.483 39.888C92.681 46.338 92.681 53.662 95.483 60.112L97 63.605C106.176 84.732 84.733 106.176 63.605 97L60.112 95.483C53.662 92.681 46.338 92.681 39.888 95.483L36.395 97C15.267 106.176 -6.176 84.732 3 63.605L4.517 60.112C7.319 53.662 7.319 46.338 4.517 39.888L3 36.395C -6.176 15.268 15.267 -6.176 36.395 3L39.888 4.517C46.338 7.319 53.662 7.319 60.112 4.517L63.605 3Z"

        private fun cookiePath() = PathParser.createPathFromPathData(FOUR_SIDED_COOKIE)

        private fun Path.resize(size: Int) = apply {
            transform(
                Matrix().apply {
                    setRectToRect(
                        RectF(0f, 0f, 100f, 100f),
                        RectF(0f, 0f, size.toFloat(), size.toFloat()),
                        FILL,
                    )
                }
            )
        }
    }
}
