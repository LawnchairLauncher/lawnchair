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
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/**
 * Tests for [SurfaceControlViewHostAdapter].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:SurfaceControlViewHostAdapterTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class SurfaceControlViewHostAdapterTest : ShellTestCase() {

    private lateinit var adapter: SurfaceControlViewHostAdapter

    @Before
    fun setUp() {
        adapter = SurfaceControlViewHostAdapter(
            context,
            context.display,
            surfaceControlViewHostFactory = { c, d, wwm, s ->
                spy(SurfaceControlViewHost(c, d, wwm, s))
            }
        )
    }

    @Test
    fun prepareViewHost() {
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)

        assertThat(adapter.viewHost).isNotNull()
    }

    @Test
    fun prepareViewHost_alreadyCreated_skips() {
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)

        val viewHost = adapter.viewHost!!

        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)

        assertThat(adapter.viewHost).isEqualTo(viewHost)
    }

    @Test
    fun updateView_layoutInViewHost() {
        val view = View(context)
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)

        adapter.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100)
        )

        assertThat(adapter.isInitialized()).isTrue()
        assertThat(adapter.view()).isEqualTo(view)
    }

    @Test
    fun updateView_alreadyLaidOut_relayouts() {
        val view = View(context)
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)
        adapter.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100)
        )

        val otherParams = WindowManager.LayoutParams(200, 200)
        adapter.updateView(
            view = view,
            attrs = otherParams
        )

        assertThat(adapter.view()).isEqualTo(view)
        assertThat(adapter.view()!!.layoutParams.width).isEqualTo(otherParams.width)
    }

    @Test
    fun updateView_replacingView_throws() {
        val view = View(context)
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)
        adapter.updateView(
            view = view,
            attrs = WindowManager.LayoutParams(100, 100)
        )

        val otherView = View(context)
        assertThrows(Exception::class.java) {
            adapter.updateView(
                view = otherView,
                attrs = WindowManager.LayoutParams(100, 100)
            )
        }
    }

    @Test
    fun release() {
        adapter.prepareViewHost(context.resources.configuration, touchableRegion = null)
        adapter.updateView(
            view = View(context),
            attrs = WindowManager.LayoutParams(100, 100)
        )

        val mockT = mock(SurfaceControl.Transaction::class.java)
        adapter.release(mockT)

        verify(adapter.viewHost!!).release()
        verify(mockT).remove(adapter.rootSurface)
    }

    private fun SurfaceControlViewHostAdapter.view(): View? = viewHost?.view
}