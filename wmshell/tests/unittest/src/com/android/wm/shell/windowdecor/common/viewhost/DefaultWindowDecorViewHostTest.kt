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
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/**
 * Tests for [DefaultWindowDecorViewHost].
 *
 * Build/Install/Run: atest WMShellUnitTests:DefaultWindowDecorViewHostTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DefaultWindowDecorViewHostTest : ShellTestCase() {

    @Test
    fun updateView_layoutInViewHost() = runTest {
        val windowDecorViewHost = createDefaultViewHost()
        val view = View(context)

        windowDecorViewHost.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
            onDrawTransaction = null,
        )

        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isTrue()
        assertThat(windowDecorViewHost.view()).isEqualTo(view)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateView_clearsPendingAsyncJob() = runTest {
        val windowDecorViewHost = createDefaultViewHost()
        val asyncView = View(context)
        val syncView = View(context)
        val asyncAttrs = WindowManager.LayoutParams(100, 100)
        val syncAttrs = WindowManager.LayoutParams(200, 200)

        windowDecorViewHost.updateViewAsync(
            view = asyncView,
            attrs = asyncAttrs,
            configuration = context.resources.configuration,
        )

        // No view host yet, since the coroutine hasn't run.
        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isFalse()

        windowDecorViewHost.updateView(
            view = syncView,
            attrs = syncAttrs,
            configuration = context.resources.configuration,
            onDrawTransaction = null,
        )

        // Would run coroutine if it hadn't been cancelled.
        advanceUntilIdle()

        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isTrue()
        assertThat(windowDecorViewHost.view()).isNotNull()
        // View host view/attrs should match the ones from the sync call, plus, since the
        // sync/async were made with different views, if the job hadn't been cancelled there
        // would've been an exception thrown as replacing views isn't allowed.
        assertThat(windowDecorViewHost.view()).isEqualTo(syncView)
        assertThat(windowDecorViewHost.view()!!.layoutParams.width).isEqualTo(syncAttrs.width)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateViewAsync() = runTest {
        val windowDecorViewHost = createDefaultViewHost()
        val view = View(context)
        val attrs = WindowManager.LayoutParams(100, 100)

        windowDecorViewHost.updateViewAsync(
            view = view,
            attrs = attrs,
            configuration = context.resources.configuration,
        )

        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isFalse()

        advanceUntilIdle()

        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun updateViewAsync_clearsPendingAsyncJob() = runTest {
        val windowDecorViewHost = createDefaultViewHost()

        val view = View(context)
        windowDecorViewHost.updateViewAsync(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
        )
        val otherView = View(context)
        windowDecorViewHost.updateViewAsync(
            view = otherView,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
        )

        advanceUntilIdle()

        assertThat(windowDecorViewHost.viewHostAdapter.isInitialized()).isTrue()
        assertThat(windowDecorViewHost.view()).isEqualTo(otherView)
    }

    @Test
    fun release() = runTest {
        val windowDecorViewHost = createDefaultViewHost()

        val view = View(context)
        windowDecorViewHost.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100),
            configuration = context.resources.configuration,
            onDrawTransaction = null,
        )

        val t = mock(SurfaceControl.Transaction::class.java)
        windowDecorViewHost.release(t)

        verify(windowDecorViewHost.viewHostAdapter).release(t)
    }

    private fun CoroutineScope.createDefaultViewHost() =
        DefaultWindowDecorViewHost(
            context = context,
            mainScope = this,
            display = context.display,
            viewHostAdapter = spy(SurfaceControlViewHostAdapter(context, context.display)),
        )

    private fun DefaultWindowDecorViewHost.view(): View? = viewHostAdapter.viewHost?.view
}
