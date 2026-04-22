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

package com.android.launcher3.accessibility // Use the original package

// Imports
import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.CellLayout
import com.android.launcher3.folder.FolderPagedView
import com.android.launcher3.util.ActivityContextWrapper
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderAccessibilityHelperTest {

    // Context
    private lateinit var mContext: Context
    // Mocks
    @Mock private lateinit var mockParent: FolderPagedView
    @Mock private lateinit var mockLayout: CellLayout

    private var countX = 4
    private var countY = 3
    private var index = 1

    // System under test
    private lateinit var folderAccessibilityHelper: FolderAccessibilityHelper

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        mContext = ActivityContextWrapper(getApplicationContext())
        `when`(mockLayout.parent).thenReturn(mockParent)
        `when`(mockLayout.context).thenReturn(mContext)

        // mStartPosition isn't recalculated after the constructor
        // If you want to create new tests with different starting params,
        // rebuild the folderAccessibilityHelper object
        val countX = 4
        val countY = 3
        val index = 1
        `when`(mockParent.indexOfChild(mockLayout)).thenReturn(index)
        `when`(mockLayout.countX).thenReturn(countX)
        `when`(mockLayout.countY).thenReturn(countY)

        folderAccessibilityHelper = FolderAccessibilityHelper(mockLayout)
    }

    // Test for intersectsValidDropTarget()
    @Test
    fun testIntersectsValidDropTarget() {
        // Setup
        val id = 5
        val allocatedContentSize = 20
        // Make layout function public @VisibleForTesting
        `when`(mockParent.allocatedContentSize).thenReturn(allocatedContentSize)

        // Execute
        val result = folderAccessibilityHelper.intersectsValidDropTarget(id)

        // Verify
        val expectedResult = min(id, allocatedContentSize - (index * countX * countY) - 1)
        assertEquals(expectedResult, result)
    }

    // Test for getLocationDescriptionForIconDrop()
    @Test
    fun testGetLocationDescriptionForIconDrop() {
        // Setup
        val id = 5

        // Execute
        val result = folderAccessibilityHelper.getLocationDescriptionForIconDrop(id)

        // Verify
        val expectedResult = "Move to position ${id + (index * countX * countY) + 1}"
        assertEquals(expectedResult, result)
    }

    // Test for getConfirmationForIconDrop()
    @Test
    fun testGetConfirmationForIconDrop() {
        // Execute
        val result =
            folderAccessibilityHelper.getConfirmationForIconDrop(0) // Id doesn't matter here

        // Verify
        assertEquals("Item moved", result)
    }
}
