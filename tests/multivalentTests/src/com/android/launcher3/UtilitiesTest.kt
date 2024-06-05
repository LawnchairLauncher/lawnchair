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
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.ActivityContextWrapper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilitiesTest {

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
}
