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

package com.android.launcher3.util

import android.view.View
import android.view.ViewTreeObserver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Launcher
import com.android.launcher3.Workspace
import com.android.launcher3.pageindicators.PageIndicator
import java.util.function.Consumer
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.reset
import org.mockito.kotlin.same
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class ViewOnDrawExecutorTest<T> where T : View, T : PageIndicator {

    @Mock private lateinit var runnable: Runnable
    @Mock private lateinit var consumer: Consumer<ViewOnDrawExecutor>
    @Mock private lateinit var launcher: Launcher
    @Mock private lateinit var workspace: Workspace<T>
    @Mock private lateinit var rootView: View
    @Mock private lateinit var viewTreeObserver: ViewTreeObserver

    private lateinit var underTest: ViewOnDrawExecutor
    private lateinit var runnableList: RunnableList

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        runnableList = RunnableList()
        runnableList.add(runnable)
        underTest = ViewOnDrawExecutor(runnableList, consumer)

        `when`(launcher.workspace).thenReturn(workspace)
        `when`(workspace.rootView).thenReturn(rootView)
        `when`(workspace.viewTreeObserver).thenReturn(viewTreeObserver)
    }

    @Test
    fun attachToLauncher_alreadyAttachedToWindow() {
        `when`(workspace.isAttachedToWindow).thenReturn(true)

        underTest.attachTo(launcher)

        verify(workspace).addOnAttachStateChangeListener(same(underTest))
        verify(viewTreeObserver).addOnDrawListener(same(underTest))
        verify(rootView).invalidate()
    }

    @Test
    fun attachToLauncher_notAttachedToWindow() {
        `when`(workspace.isAttachedToWindow).thenReturn(false)

        underTest.attachTo(launcher)

        verify(workspace).addOnAttachStateChangeListener(same(underTest))
        verifyNoMoreInteractions(viewTreeObserver)
        verifyNoMoreInteractions(rootView)
    }

    @Test
    fun onViewAttachedToWindow_registerObserver() {
        `when`(workspace.isAttachedToWindow).thenReturn(false)
        underTest.attachTo(launcher)

        underTest.onViewAttachedToWindow(rootView)

        verify(viewTreeObserver).addOnDrawListener(same(underTest))
        verify(rootView).invalidate()
    }

    @Test
    fun complete_then_onViewAttachedToWindow_registerObserver() {
        underTest.markCompleted()
        reset(viewTreeObserver)
        reset(rootView)

        underTest.onViewAttachedToWindow(rootView)

        verifyNoMoreInteractions(viewTreeObserver)
        verifyNoMoreInteractions(rootView)
    }

    @Test
    fun onDraw_postRunnable() {
        underTest.attachTo(launcher)

        underTest.onDraw()

        verify(workspace).post(same(underTest))
    }

    @Test
    fun run_before_onDraw_noOp() {
        underTest.run()

        verifyNoMoreInteractions(runnable)
        verifyNoMoreInteractions(viewTreeObserver)
        verifyNoMoreInteractions(workspace)
        verifyNoMoreInteractions(consumer)
    }

    @Test
    fun first_run_executeRunnable() {
        underTest.attachTo(launcher)
        underTest.onDraw()

        underTest.run()

        verify(runnable).run()
        verify(viewTreeObserver).removeOnDrawListener(same(underTest))
        verify(workspace).removeOnAttachStateChangeListener(same(underTest))
        verify(consumer).accept(same(underTest))
    }

    @Test
    fun second_run_noOp() {
        underTest.attachTo(launcher)
        underTest.onDraw()
        underTest.run()
        reset(runnable)
        reset(viewTreeObserver)
        reset(workspace)
        reset(consumer)

        underTest.run()

        verifyNoMoreInteractions(runnable)
        verifyNoMoreInteractions(viewTreeObserver)
        verifyNoMoreInteractions(workspace)
        verifyNoMoreInteractions(consumer)
    }

    @Test
    fun markCompleted_viewNotAttached() {
        underTest.markCompleted()

        verify(runnable).run()
        verify(consumer).accept(underTest)
        verifyNoMoreInteractions(workspace)
    }

    @Test
    fun markCompleted_viewAttached() {
        underTest.attachTo(launcher)

        underTest.markCompleted()

        verify(runnable).run()
        verify(consumer).accept(underTest)
        verify(workspace).removeOnAttachStateChangeListener(same(underTest))
        verify(viewTreeObserver).removeOnDrawListener(same(underTest))
    }

    @Test
    fun cancel_notRun() {
        underTest.cancel()

        verifyNoMoreInteractions(runnable)
        verify(consumer).accept(underTest)
        verifyNoMoreInteractions(workspace)
    }
}
