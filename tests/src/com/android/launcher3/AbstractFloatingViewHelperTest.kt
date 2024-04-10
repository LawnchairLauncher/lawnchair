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

import android.view.View
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.views.ActivityContext
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever

/** Test for AbstractFloatingViewHelper */
class AbstractFloatingViewHelperTest {
    private val activityContext: ActivityContext = mock()
    private val dragLayer: DragLayer = mock()
    private val view: View = mock()
    private val folderView: AbstractFloatingView = mock()
    private val taskMenuView: AbstractFloatingView = mock()
    private val abstractFloatingViewHelper = AbstractFloatingViewHelper()

    @Before
    fun setup() {
        whenever(activityContext.dragLayer).thenReturn(dragLayer)
        whenever(dragLayer.childCount).thenReturn(3)
        whenever(dragLayer.getChildAt(0)).thenReturn(view)
        whenever(dragLayer.getChildAt(1)).thenReturn(folderView)
        whenever(dragLayer.getChildAt(2)).thenReturn(taskMenuView)
        whenever(folderView.isOfType(any())).thenAnswer {
            (it.getArgument<Int>(0) and AbstractFloatingView.TYPE_FOLDER) != 0
        }
        whenever(taskMenuView.isOfType(any())).thenAnswer {
            (it.getArgument<Int>(0) and AbstractFloatingView.TYPE_TASK_MENU) != 0
        }
    }

    @Test
    fun closeOpenViews_all() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_ALL
        )

        verifyZeroInteractions(view)
        verify(folderView).close(true)
        verify(taskMenuView).close(true)
    }

    @Test
    fun closeOpenViews_taskMenu() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_TASK_MENU
        )

        verifyZeroInteractions(view)
        verify(folderView, never()).close(any())
        verify(taskMenuView).close(true)
    }

    @Test
    fun closeOpenViews_other() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            true,
            AbstractFloatingView.TYPE_PIN_IME_POPUP
        )

        verifyZeroInteractions(view)
        verify(folderView, never()).close(any())
        verify(taskMenuView, never()).close(any())
    }

    @Test
    fun closeOpenViews_both_animationOff() {
        abstractFloatingViewHelper.closeOpenViews(
            activityContext,
            false,
            AbstractFloatingView.TYPE_FOLDER or AbstractFloatingView.TYPE_TASK_MENU
        )

        verifyZeroInteractions(view)
        verify(folderView).close(false)
        verify(taskMenuView).close(false)
    }
}
