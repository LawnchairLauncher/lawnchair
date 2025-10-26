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

package com.android.launcher3.statehandlers

import android.content.res.Resources
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragLayer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.same
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class DepthControllerTest {

    private lateinit var underTest: DepthController
    @Mock private lateinit var launcher: Launcher
    @Mock private lateinit var resource: Resources
    @Mock private lateinit var dragLayer: DragLayer
    @Mock private lateinit var viewTreeObserver: ViewTreeObserver

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        `when`(launcher.resources).thenReturn(resource)
        `when`(resource.getInteger(R.integer.max_depth_blur_radius)).thenReturn(30)
        `when`(launcher.dragLayer).thenReturn(dragLayer)
        `when`(dragLayer.viewTreeObserver).thenReturn(viewTreeObserver)

        underTest = DepthController(launcher)
    }

    @Test
    fun setActivityStarted_add_onDrawListener() {
        underTest.setActivityStarted(true)

        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStopped_not_remove_onDrawListener() {
        underTest.setActivityStarted(false)

        // Because underTest.mOnDrawListener is never added
        verifyNoMoreInteractions(viewTreeObserver)
    }

    @Test
    fun setActivityStared_then_stopped_remove_onDrawListener() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)

        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun setActivityStared_then_stopped_multiple_times_remove_onDrawListener_once() {
        underTest.setActivityStarted(true)
        reset(viewTreeObserver)

        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)
        underTest.setActivityStarted(false)

        // Should just remove mOnDrawListener once
        verify(viewTreeObserver).removeOnDrawListener(same(underTest.mOnDrawListener))
    }

    @Test
    fun test_onInvalidSurface_multiple_times_add_onDrawListener_once() {
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()
        underTest.onInvalidSurface()

        // We should only call addOnDrawListener 1 time
        verify(viewTreeObserver).addOnDrawListener(same(underTest.mOnDrawListener))
    }
}
