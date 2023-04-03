/*
 *  Copyright (C) 2023 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.android.quickstep.util

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.quickstep.views.GroupedTaskView
import com.android.quickstep.views.IconView
import com.android.quickstep.views.TaskThumbnailView
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer
import com.android.systemui.shared.recents.model.Task
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when` as whenever

@RunWith(AndroidJUnit4::class)
class SplitAnimationControllerTest {

    private val taskId = 9

    @Mock lateinit var mockSplitSelectStateController: SplitSelectStateController
    // TaskView
    @Mock lateinit var mockTaskView: TaskView
    @Mock lateinit var mockThumbnailView: TaskThumbnailView
    @Mock lateinit var mockBitmap: Bitmap
    @Mock lateinit var mockIconView: IconView
    @Mock lateinit var mockTaskViewDrawable: Drawable
    // GroupedTaskView
    @Mock lateinit var mockGroupedTaskView: GroupedTaskView
    @Mock lateinit var mockTask: Task
    @Mock lateinit var mockTaskKey: Task.TaskKey
    @Mock lateinit var mockTaskIdAttributeContainer: TaskIdAttributeContainer

    // SplitSelectSource
    @Mock lateinit var splitSelectSource: SplitConfigurationOptions.SplitSelectSource
    @Mock lateinit var mockSplitSourceDrawable: Drawable
    @Mock lateinit var mockSplitSourceView: View

    lateinit var splitAnimationController: SplitAnimationController

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        whenever(mockTaskView.thumbnail).thenReturn(mockThumbnailView)
        whenever(mockThumbnailView.thumbnail).thenReturn(mockBitmap)
        whenever(mockTaskView.iconView).thenReturn(mockIconView)
        whenever(mockIconView.drawable).thenReturn(mockTaskViewDrawable)

        whenever(splitSelectSource.drawable).thenReturn(mockSplitSourceDrawable)
        whenever(splitSelectSource.view).thenReturn(mockSplitSourceView)

        splitAnimationController = SplitAnimationController(mockSplitSelectStateController)
    }

    @Test
    fun getFirstAnimInitViews_nullTaskViewIcon_useSplitSourceIcon() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        // Missing taskView icon
        whenever(mockIconView.drawable).thenReturn(null)

        val splitAnimInitProps : SplitAnimationController.Companion.SplitAnimInitProps =
                splitAnimationController.getFirstAnimInitViews(
                        { mockTaskView }, { splitSelectSource })

        assertEquals("Did not fallback to use splitSource icon drawable",
                mockSplitSourceDrawable, splitAnimInitProps.iconDrawable)
    }

    @Test
    fun getFirstAnimInitViews_validTaskViewIcon_useTaskViewIcon() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        val splitAnimInitProps : SplitAnimationController.Companion.SplitAnimInitProps =
                splitAnimationController.getFirstAnimInitViews(
                        { mockTaskView }, { splitSelectSource })

        assertEquals("Did not use taskView icon drawable", mockTaskViewDrawable,
                splitAnimInitProps.iconDrawable)
    }

    @Test
    fun getFirstAnimInitViews_validTaskViewNullSplitSource_useTaskViewIcon() {
        // Hit fullscreen task dismissal state
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        // Set split source to null
        whenever(splitSelectSource.drawable).thenReturn(null)

        val splitAnimInitProps : SplitAnimationController.Companion.SplitAnimInitProps =
                splitAnimationController.getFirstAnimInitViews(
                        { mockTaskView }, { splitSelectSource })

        assertEquals("Did not use taskView icon drawable", mockTaskViewDrawable,
                splitAnimInitProps.iconDrawable)
    }

    @Test
    fun getFirstAnimInitViews_nullTaskViewValidSplitSource_noTaskDismissal() {
        // Hit initiating split from home
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(false)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(false)

        val splitAnimInitProps : SplitAnimationController.Companion.SplitAnimInitProps =
                splitAnimationController.getFirstAnimInitViews(
                        { mockTaskView }, { splitSelectSource })

        assertEquals("Did not use splitSource icon drawable", mockSplitSourceDrawable,
                splitAnimInitProps.iconDrawable)
    }

    @Test
    fun getFirstAnimInitViews_nullTaskViewValidSplitSource_groupedTaskView() {
        // Hit groupedTaskView dismissal
        whenever(mockSplitSelectStateController.isAnimateCurrentTaskDismissal).thenReturn(true)
        whenever(mockSplitSelectStateController.isDismissingFromSplitPair).thenReturn(true)

        // Remove icon view from GroupedTaskView
        whenever(mockIconView.drawable).thenReturn(null)

        whenever(mockTaskIdAttributeContainer.task).thenReturn(mockTask)
        whenever(mockTaskIdAttributeContainer.iconView).thenReturn(mockIconView)
        whenever(mockTaskIdAttributeContainer.thumbnailView).thenReturn(mockThumbnailView)
        whenever(mockTask.getKey()).thenReturn(mockTaskKey)
        whenever(mockTaskKey.getId()).thenReturn(taskId)
        whenever(mockSplitSelectStateController.initialTaskId).thenReturn(taskId)
        whenever(mockGroupedTaskView.taskIdAttributeContainers)
                .thenReturn(Array(1) { mockTaskIdAttributeContainer })
        val splitAnimInitProps : SplitAnimationController.Companion.SplitAnimInitProps =
                splitAnimationController.getFirstAnimInitViews(
                        { mockGroupedTaskView }, { splitSelectSource })

        assertEquals("Did not use splitSource icon drawable", mockSplitSourceDrawable,
                splitAnimInitProps.iconDrawable)
    }
}