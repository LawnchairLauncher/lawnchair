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

package com.android.launcher3

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.ActivityContextWrapper
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilitiesTest {

    companion object {
        const val SEED = 827
    }

    private lateinit var mContext: Context

    @Before
    fun setUp() {
        mContext = ActivityContextWrapper(getApplicationContext())
    }

    @Test
    fun testIsPropertyEnabled() {
        // This assumes the property "propertyName" is not enabled by default
        assertFalse(Utilities.isPropertyEnabled("propertyName"))
    }

    @Test
    fun testGetDescendantCoordRelativeToAncestor() {
        val ancestor =
            object : ViewGroup(mContext) {
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {}
            }
        val descendant = View(mContext)

        descendant.x = 50f
        descendant.y = 30f
        descendant.scaleX = 2f
        descendant.scaleY = 2f

        ancestor.addView(descendant)

        val coord = floatArrayOf(10f, 15f)
        val scale =
            Utilities.getDescendantCoordRelativeToAncestor(descendant, ancestor, coord, false)

        assertEquals(2f, scale) // Expecting scale to be 2f
        assertEquals(70f, coord[0])
        assertEquals(60f, coord[1])
    }

    @Test
    fun testRoundArray() {
        val floatArray = floatArrayOf(1.2f, 3.7f, 5.5f)
        val intArray = IntArray(3)
        Utilities.roundArray(floatArray, intArray)
        assertArrayEquals(intArrayOf(1, 4, 6), intArray)
    }

    @Test
    fun testOffsetPoints() {
        val points = floatArrayOf(1f, 2f, 3f, 4f)
        Utilities.offsetPoints(points, 5f, 6f)

        val expected = listOf(6f, 8f, 8f, 10f)
        assertEquals(expected, points.toList())
    }

    @Test
    fun testPointInView() {
        val view = View(mContext)
        view.layout(0, 0, 100, 100)

        assertTrue(Utilities.pointInView(view, 50f, 50f, 0f)) // Inside view
        assertFalse(Utilities.pointInView(view, -10f, -10f, 0f)) // Outside view
        assertTrue(Utilities.pointInView(view, -5f, -5f, 10f)) // Inside slop
        assertFalse(Utilities.pointInView(view, 115f, 115f, 10f)) // Outside slop
    }

    @Test
    fun testNumberBounding() {
        assertEquals(887.99f, Utilities.boundToRange(887.99f, 0f, 1000f))
        assertEquals(2.777f, Utilities.boundToRange(887.99f, 0f, 2.777f))
        assertEquals(900f, Utilities.boundToRange(887.99f, 900f, 1000f))

        assertEquals(9383667L, Utilities.boundToRange(9383667L, -999L, 9999999L))
        assertEquals(9383668L, Utilities.boundToRange(9383667L, 9383668L, 9999999L))
        assertEquals(42L, Utilities.boundToRange(9383667L, -999L, 42L))

        assertEquals(345, Utilities.boundToRange(345, 2, 500))
        assertEquals(400, Utilities.boundToRange(345, 400, 500))
        assertEquals(300, Utilities.boundToRange(345, 2, 300))

        val random = Random(SEED)
        for (i in 1..300) {
            val value = random.nextFloat()
            val lowerBound = random.nextFloat()
            val higherBound = lowerBound + random.nextFloat()

            assertEquals(
                "Utilities.boundToRange doesn't match Kotlin coerceIn",
                value.coerceIn(lowerBound, higherBound),
                Utilities.boundToRange(value, lowerBound, higherBound)
            )
            assertEquals(
                "Utilities.boundToRange doesn't match Kotlin coerceIn",
                value.toInt().coerceIn(lowerBound.toInt(), higherBound.toInt()),
                Utilities.boundToRange(value.toInt(), lowerBound.toInt(), higherBound.toInt())
            )
            assertEquals(
                "Utilities.boundToRange doesn't match Kotlin coerceIn",
                value.toLong().coerceIn(lowerBound.toLong(), higherBound.toLong()),
                Utilities.boundToRange(value.toLong(), lowerBound.toLong(), higherBound.toLong())
            )
            assertEquals(
                "If the lower bound is higher than lower bound, it should return the lower bound",
                higherBound,
                Utilities.boundToRange(value, higherBound, lowerBound)
            )
        }
    }

    @Test
    fun testTranslateOverlappingView() {
        testConcentricOverlap()
        leftDownCornerOverlap()
        noOverlap()
    }

    /*
        Test Case: Rectangle Contained Within Another Rectangle

           +-------------+  <-- exclusionBounds
           |             |
           |   +-----+   |
           |   |     |   |  <-- targetViewBounds
           |   |     |   |
           |   +-----+   |
           |             |
           +-------------+
    */
    private fun testConcentricOverlap() {
        val targetView = View(ContextWrapper(getApplicationContext()))
        val targetViewBounds = Rect(40, 40, 60, 60)
        val inclusionBounds = Rect(0, 0, 100, 100)
        val exclusionBounds = Rect(30, 30, 70, 70)

        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_RIGHT
        )
        assertEquals(30f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_LEFT
        )
        assertEquals(-30f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_DOWN
        )
        assertEquals(30f, targetView.translationY)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_UP
        )
        assertEquals(-30f, targetView.translationY)
    }

    /*
    Test Case: Non-Overlapping Rectangles

        +-----------------+      <-- targetViewBounds
        |                 |
        |                 |
        +-----------------+

                 +-----------+     <-- exclusionBounds
                 |           |
                 |           |
                 +-----------+
    */
    private fun noOverlap() {
        val targetView = View(ContextWrapper(getApplicationContext()))
        val targetViewBounds = Rect(10, 10, 20, 20)

        val inclusionBounds = Rect(0, 0, 100, 100)
        val exclusionBounds = Rect(30, 30, 40, 40)

        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_RIGHT
        )
        assertEquals(0f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_LEFT
        )
        assertEquals(0f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_DOWN
        )
        assertEquals(0f, targetView.translationY)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_UP
        )
        assertEquals(0f, targetView.translationY)
    }

    /*
    Test Case: Rectangles Overlapping at Corners

       +------------+         <-- exclusionBounds
       |            |
    +-------+       |
    |  |    |       |          <-- targetViewBounds
    |  +------------+
    |       |
    +-------+
    */
    private fun leftDownCornerOverlap() {
        val targetView = View(ContextWrapper(getApplicationContext()))
        val targetViewBounds = Rect(20, 20, 30, 30)

        val inclusionBounds = Rect(0, 0, 100, 100)
        val exclusionBounds = Rect(25, 25, 35, 35)

        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_RIGHT
        )
        assertEquals(15f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_LEFT
        )
        assertEquals(-5f, targetView.translationX)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_DOWN
        )
        assertEquals(15f, targetView.translationY)
        Utilities.translateOverlappingView(
            targetView,
            targetViewBounds,
            inclusionBounds,
            exclusionBounds,
            Utilities.TRANSLATE_UP
        )
        assertEquals(-5f, targetView.translationY)
    }

    @Test
    fun trim() {
        val expectedString = "Hello World"
        assertEquals(expectedString, Utilities.trim("Hello World   "))
        // Basic trimming
        assertEquals(expectedString, Utilities.trim("   Hello World  "))
        assertEquals(expectedString, Utilities.trim("   Hello World"))

        // Non-breaking whitespace
        assertEquals("Hello World", Utilities.trim("\u00A0\u00A0Hello World\u00A0\u00A0"))

        // Whitespace combinations
        assertEquals(expectedString, Utilities.trim("\t \r\n Hello World \n\r"))
        assertEquals(expectedString, Utilities.trim("\nHello World   "))

        // Null input
        assertEquals("", Utilities.trim(null))

        // Empty String
        assertEquals("", Utilities.trim(""))
    }

    @Test
    fun getProgress() {
        // Basic test
        assertEquals(0.5f, Utilities.getProgress(50f, 0f, 100f), 0.001f)

        // Negative values
        assertEquals(0.5f, Utilities.getProgress(-20f, -50f, 10f), 0.001f)

        // Outside of range
        assertEquals(1.2f, Utilities.getProgress(120f, 0f, 100f), 0.001f)
    }

    @Test
    fun scaleRectFAboutPivot() {
        // Enlarge
        var rectF = RectF(10f, 20f, 50f, 80f)
        Utilities.scaleRectFAboutPivot(rectF, 30f, 50f, 1.5f)
        assertEquals(RectF(0f, 5f, 60f, 95f), rectF)

        // Shrink
        rectF = RectF(10f, 20f, 50f, 80f)
        Utilities.scaleRectFAboutPivot(rectF, 30f, 50f, 0.5f)
        assertEquals(RectF(20f, 35f, 40f, 65f), rectF)

        // No scale
        rectF = RectF(10f, 20f, 50f, 80f)
        Utilities.scaleRectFAboutPivot(rectF, 30f, 50f, 1.0f)
        assertEquals(RectF(10f, 20f, 50f, 80f), rectF)
    }

    @Test
    fun rotateBounds() {
        var rect = Rect(20, 70, 60, 80)
        Utilities.rotateBounds(rect, 100, 100, 0)
        assertEquals(Rect(20, 70, 60, 80), rect)

        rect = Rect(20, 70, 60, 80)
        Utilities.rotateBounds(rect, 100, 100, 1)
        assertEquals(Rect(70, 40, 80, 80), rect)

        // case removed for b/28435189
        //        rect = Rect(20, 70, 60, 80)
        //        Utilities.rotateBounds(rect, 100, 100, 2)
        //        assertEquals(Rect(40, 20, 80, 30), rect)

        rect = Rect(20, 70, 60, 80)
        Utilities.rotateBounds(rect, 100, 100, 3)
        assertEquals(Rect(20, 20, 30, 60), rect)
    }
}
