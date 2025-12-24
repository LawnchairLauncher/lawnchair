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
package com.android.wm.shell.windowdecor.common.viewhost

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/**
 * Tests for [ReusableWindowDecorViewHost].
 *
 * Build/Install/Run: atest WMShellUnitTests:ReusableWindowDecorViewHostTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class ReusableWindowDecorViewHostTest : ShellTestCase() {

    @Test
    fun update_differentView_replacesView() = runTest {
        val view = View(context)
        val lp = WindowManager.LayoutParams()
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)
        reusableVH.updateView(view, lp, context.resources.configuration)

        assertThat(rootView.childCount).isEqualTo(1)
        assertThat(rootView.getChildAt(0)).isEqualTo(view)

        val newView = View(context)
        val newLp = WindowManager.LayoutParams()
        reusableVH.updateView(newView, newLp, context.resources.configuration)

        assertThat(rootView.childCount).isEqualTo(1)
        assertThat(rootView.getChildAt(0)).isEqualTo(newView)
    }

    @Test
    fun update_sameView_doesNotReplaceView() = runTest {
        val view = View(context)
        val lp = WindowManager.LayoutParams()
        val spyRootView = spy(FrameLayout(context))
        val reusableVH = createReusableViewHost(spyRootView)
        reusableVH.updateView(view, lp, context.resources.configuration)

        verify(spyRootView, times(1)).removeAllViews()
        assertThat(spyRootView.childCount).isEqualTo(1)
        assertThat(spyRootView.getChildAt(0)).isEqualTo(view)

        reusableVH.updateView(view, lp, context.resources.configuration)

        clearInvocations(spyRootView)
        verify(spyRootView, never()).removeAllViews()
        assertThat(spyRootView.childCount).isEqualTo(1)
        assertThat(spyRootView.getChildAt(0)).isEqualTo(view)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateView_clearsPendingAsyncJob() = runTest {
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)
        val asyncView = View(context)
        val syncView = View(context)
        val asyncAttrs = WindowManager.LayoutParams(100, 100)
        val syncAttrs = WindowManager.LayoutParams(200, 200)

        reusableVH.updateViewAsync(
            view = asyncView,
            attrs = asyncAttrs,
            configuration = context.resources.configuration,
        )

        // No view host yet, since the coroutine hasn't run.
        assertThat(reusableVH.viewHostAdapter.isInitialized()).isFalse()

        reusableVH.updateView(
            view = syncView,
            attrs = syncAttrs,
            configuration = context.resources.configuration,
        )

        // Would run coroutine if it hadn't been cancelled.
        advanceUntilIdle()

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isTrue()
        // View host view/attrs should match the ones from the sync call.
        assertThat(rootView.getChildAt(0)).isEqualTo(syncView)
        assertThat(reusableVH.view()!!.layoutParams.width).isEqualTo(syncAttrs.width)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateViewAsync() = runTest {
        val reusableVH = createReusableViewHost()
        val view = View(context)
        val attrs = WindowManager.LayoutParams(100, 100)

        reusableVH.updateViewAsync(
            view = view,
            attrs = attrs,
            configuration = context.resources.configuration,
        )

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isFalse()

        advanceUntilIdle()

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateViewAsync_clearsPendingAsyncJob() = runTest {
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)

        val view = View(context)
        reusableVH.updateViewAsync(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
        )
        val otherView = View(context)
        reusableVH.updateViewAsync(
            view = otherView,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
        )

        advanceUntilIdle()

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isTrue()
        assertThat(rootView.getChildAt(0)).isEqualTo(otherView)
    }

    @Test
    fun release() = runTest {
        val reusableVH = createReusableViewHost()

        val view = View(context)
        reusableVH.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
        )

        val t = mock(SurfaceControl.Transaction::class.java)
        reusableVH.release(t)

        verify(reusableVH.viewHostAdapter).release(t)
    }

    @Test
    fun warmUp_addsRootView() = runTest {
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView).apply { warmUp() }

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isTrue()
        assertThat(reusableVH.view()).isEqualTo(rootView)
    }

    @Test
    fun reset_removesChildViews() = runTest {
        val view = View(context)
        val lp = WindowManager.LayoutParams()
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)
        reusableVH.updateView(view, lp, context.resources.configuration)

        reusableVH.reset()

        assertThat(rootView.childCount).isEqualTo(0)
    }

    @Test
    fun reset_restoresDefaultLayoutParams() = runTest {
        val view = View(context)
        val lp =
            WindowManager.LayoutParams().apply {
                width = 10
                height = 10
            }
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)
        reusableVH.updateView(view, lp, context.resources.configuration)

        reusableVH.reset()

        verify(reusableVH.viewHostAdapter).updateView(rootView, reusableVH.defaultLayoutParams)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reset_clearsPendingAsyncJob() = runTest {
        val rootView = FrameLayout(context)
        val reusableVH = createReusableViewHost(rootView)

        val view = View(context)
        val params = WindowManager.LayoutParams(100, 100)
        reusableVH.updateViewAsync(
            view = view,
            attrs = params,
            configuration = context.resources.configuration,
        )
        reusableVH.reset()

        advanceUntilIdle()

        assertThat(reusableVH.viewHostAdapter.isInitialized()).isTrue()
        assertThat(rootView.childCount).isEqualTo(0)
        verify(reusableVH.viewHostAdapter, never()).updateView(rootView, params)
    }

    private fun CoroutineScope.createReusableViewHost(
        rootView: FrameLayout = FrameLayout(context)
    ) =
        ReusableWindowDecorViewHost(
            context = context,
            mainScope = this,
            display = context.display,
            id = 1,
            viewHostAdapter = spy(SurfaceControlViewHostAdapter(context, context.display)),
            rootView,
        )

    private fun ReusableWindowDecorViewHost.view(): View? = viewHostAdapter.viewHost?.view
}
