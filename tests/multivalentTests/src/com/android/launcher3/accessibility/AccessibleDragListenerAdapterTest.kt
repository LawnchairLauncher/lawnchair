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

package com.android.launcher3.accessibility

import android.content.Context
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.util.ActivityContextWrapper
import java.util.function.Function
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

@SmallTest
@RunWith(AndroidJUnit4::class)
class AccessibleDragListenerAdapterTest {

    private lateinit var mockViewGroup: ViewGroup
    private lateinit var mockChildOne: CellLayout
    private lateinit var mockChildTwo: CellLayout
    private lateinit var mDelegateFactory: Function<CellLayout, DragAndDropAccessibilityDelegate>
    private lateinit var adapter: AccessibleDragListenerAdapter
    private lateinit var mContext: Context

    @Before
    fun setup() {
        mContext = ActivityContextWrapper(getApplicationContext())
        mockViewGroup = mock(ViewGroup::class.java)
        mockChildOne = mock(CellLayout::class.java)
        mockChildTwo = mock(CellLayout::class.java)
        `when`(mockViewGroup.context).thenReturn(mContext)
        `when`(mockViewGroup.childCount).thenReturn(2)
        `when`(mockViewGroup.getChildAt(0)).thenReturn(mockChildOne)
        `when`(mockViewGroup.getChildAt(1)).thenReturn(mockChildTwo)
        // Mock Delegate factory
        mDelegateFactory =
            mock(Function::class.java) as Function<CellLayout, DragAndDropAccessibilityDelegate>
        `when`(mDelegateFactory.apply(any()))
            .thenReturn(mock(DragAndDropAccessibilityDelegate::class.java))
        adapter = AccessibleDragListenerAdapter(mockViewGroup, mDelegateFactory)
    }

    @Test
    fun `onDragStart enables accessible drag for all view children`() {
        // Create mock view children
        val mockDragObject = mock(DragObject::class.java)
        val mockDragOptions = mock(DragOptions::class.java)

        // Action
        adapter.onDragStart(mockDragObject, mockDragOptions)

        // Assertion
        verify(mockChildOne).setDragAndDropAccessibilityDelegate(any())
        verify(mockChildTwo).setDragAndDropAccessibilityDelegate(any())
    }

    @Test
    fun `onDragEnd removes the accessibility delegate`() {
        // Action
        adapter = AccessibleDragListenerAdapter(mockViewGroup, mDelegateFactory)
        adapter.onDragEnd()

        // Assertion
        verify(mockChildOne).setDragAndDropAccessibilityDelegate(null)
        verify(mockChildTwo).setDragAndDropAccessibilityDelegate(null)
    }

    @Test
    fun `onChildViewAdded sets enabled as true for childview`() {
        // Action
        adapter = AccessibleDragListenerAdapter(mockViewGroup, mDelegateFactory)
        adapter.onChildViewAdded(mockViewGroup, mockChildOne)

        // Assertion
        verify(mockChildOne).setDragAndDropAccessibilityDelegate(any())
    }

    @Test
    fun `onChildViewRemoved sets enabled as false for childview`() {
        // Action
        adapter = AccessibleDragListenerAdapter(mockViewGroup, mDelegateFactory)
        adapter.onChildViewRemoved(mockViewGroup, mockChildOne)

        // Assertion
        verify(mockChildOne).setDragAndDropAccessibilityDelegate(null)
    }
}
