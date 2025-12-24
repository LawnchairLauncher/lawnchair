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

import android.content.Context
import android.content.res.Configuration
import android.graphics.Region
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.window.flags2.Flags
import com.android.window.flags2.Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.util.StubTransaction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Tests for [PooledWindowDecorViewHostSupplier].
 *
 * Build/Install/Run: atest WMShellUnitTests:PooledWindowDecorViewHostSupplierTest
 */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class PooledWindowDecorViewHostSupplierTest : ShellTestCase() {

    private val testExecutor = TestShellExecutor()
    private val testShellInit = ShellInit(testExecutor)

    @Mock private lateinit var mockDisplayController: DisplayController
    private lateinit var supplier: PooledWindowDecorViewHostSupplier
    private lateinit var secondContext: Context
    private val fakeDesktopState: FakeDesktopState = FakeDesktopState()
    private val onDisplaysChangedListenerCaptor =
        argumentCaptor<DisplayController.OnDisplaysChangedListener>()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        secondContext = spy(context)

        fakeDesktopState.overrideDesktopModeSupportPerDisplay[DEFAULT_DISPLAY] = true
        fakeDesktopState.overrideDesktopModeSupportPerDisplay[SECOND_DISPLAY_ID] = true
        whenever(mockDisplayController.getDisplayContext(context.display.displayId))
            .thenReturn(context)
        whenever(mockDisplayController.getDisplayContext(SECOND_DISPLAY_ID))
            .thenReturn(secondContext)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun onInit_warmsAndPoolsViewHosts_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5, preWarmSize = 2)

        testExecutor.flushAll()
        advanceUntilIdle()

        val viewHost1 = supplier.acquire(context, context.display) as ReusableWindowDecorViewHost
        val viewHost2 = supplier.acquire(context, context.display) as ReusableWindowDecorViewHost

        // Acquired warmed up view hosts from the pool.
        assertThat(viewHost1.viewHostAdapter.isInitialized()).isTrue()
        assertThat(viewHost2.viewHostAdapter.isInitialized()).isTrue()
    }

    @Test(expected = Throwable::class)
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun onInit_warmUpSizeExceedsPoolSize_throws_perDisplayPoolDisabled() = runTest {
        createSupplier(maxPoolSize = 3, preWarmSize = 4)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun acquire_poolBelowLimit_caches_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction())

        assertThat(supplier.acquire(context, context.display)).isEqualTo(viewHost)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolBelowLimit_doesNotReleaseViewHost_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        val mockT = mock<SurfaceControl.Transaction>()
        supplier.release(viewHost, mockT)

        assertThat(viewHost.released).isFalse()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolAtLimit_doesNotCache_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 1)
        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction()) // Maxes pool.

        val viewHost2 = FakeWindowDecorViewHost()
        supplier.release(viewHost2, StubTransaction()) // Beyond limit.

        assertThat(supplier.acquire(context, context.display)).isEqualTo(viewHost)
        // Second one wasn't cached, so the acquired one should've been a new instance.
        assertThat(supplier.acquire(context, context.display)).isNotEqualTo(viewHost2)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolAtLimit_releasesViewHost_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 1)
        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction()) // Maxes pool.

        val viewHost2 = FakeWindowDecorViewHost()
        val mockT = mock<SurfaceControl.Transaction>()
        supplier.release(viewHost2, mockT) // Beyond limit.

        // Second one doesn't fit, so it needs to be released.
        assertThat(viewHost2.released).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_CLEAR_REUSABLE_SCVH_ON_RELEASE)
    @DisableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_resetsViewHost_perDisplayPoolDisabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction())

        assertThat(viewHost.reset).isEqualTo(true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun onInit_warmsAndPoolsViewHosts_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5, preWarmSize = 2)

        testExecutor.flushAll()
        advanceUntilIdle()

        val viewHost1 = supplier.acquire(context, context.display) as ReusableWindowDecorViewHost
        val viewHost2 = supplier.acquire(context, context.display) as ReusableWindowDecorViewHost

        // Acquired warmed up view hosts from the pool.
        assertThat(viewHost1.viewHostAdapter.isInitialized()).isTrue()
        assertThat(viewHost2.viewHostAdapter.isInitialized()).isTrue()
    }

    @Test(expected = Throwable::class)
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun onInit_warmUpSizeExceedsPoolSize_throws_perDisplayPoolEnabled() = runTest {
        createSupplier(maxPoolSize = 3, preWarmSize = 4)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun acquire_poolBelowLimit_caches_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction())

        assertThat(supplier.acquire(context, context.display)).isEqualTo(viewHost)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolBelowLimit_doesNotReleaseViewHost_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        val mockT = mock<SurfaceControl.Transaction>()
        supplier.release(viewHost, mockT)

        assertThat(viewHost.released).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolAtLimit_doesNotCache_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 1)
        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction()) // Maxes pool.

        val viewHost2 = FakeWindowDecorViewHost()
        supplier.release(viewHost2, StubTransaction()) // Beyond limit.

        assertThat(supplier.acquire(context, context.display)).isEqualTo(viewHost)
        // Second one wasn't cached, so the acquired one should've been a new instance.
        assertThat(supplier.acquire(context, context.display)).isNotEqualTo(viewHost2)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun release_poolAtLimit_releasesViewHost_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 1)
        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction()) // Maxes pool.

        val viewHost2 = FakeWindowDecorViewHost()
        val mockT = mock<SurfaceControl.Transaction>()
        supplier.release(viewHost2, mockT) // Beyond limit.

        // Second one doesn't fit, so it needs to be released.
        assertThat(viewHost2.released).isTrue()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_CLEAR_REUSABLE_SCVH_ON_RELEASE,
        Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL,
    )
    fun release_resetsViewHost_perDisplayPoolEnabled() = runTest {
        supplier = createSupplier(maxPoolSize = 5)

        val viewHost = FakeWindowDecorViewHost()
        supplier.release(viewHost, StubTransaction())

        assertThat(viewHost.reset).isEqualTo(true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun acquire_differentDisplay_differentPools() = runTest {
        supplier = createSupplier(maxPoolSize = 5, preWarmSize = 1, hasSecondaryDisplay = true)

        testExecutor.flushAll()
        advanceUntilIdle()

        val viewHost1 = supplier.acquire(context, context.display) as ReusableWindowDecorViewHost
        val viewHost2 =
            supplier.acquire(secondContext, secondContext.display) as ReusableWindowDecorViewHost

        // Acquired warmed up view hosts from the pool.
        assertThat(viewHost1.viewHostAdapter.isInitialized()).isTrue()
        assertThat(viewHost2.viewHostAdapter.isInitialized()).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL)
    fun removeDisplayCallback_clearPool() = runTest {
        supplier = createSupplier(maxPoolSize = 5, preWarmSize = 2, hasSecondaryDisplay = true)

        testExecutor.flushAll()
        advanceUntilIdle()

        val viewHost =
            supplier.acquire(secondContext, secondContext.display) as ReusableWindowDecorViewHost
        assertThat(viewHost.viewHostAdapter.isInitialized())
            .isTrue() // This should be the warmed up one.

        onDisplaysChangedListenerCaptor.lastValue.onDisplayRemoved(SECOND_DISPLAY_ID)
        val viewHost2 =
            supplier.acquire(secondContext, secondContext.display) as ReusableWindowDecorViewHost
        assertThat(viewHost2.viewHostAdapter.isInitialized())
            .isFalse() // Pool is dead, so should be fresh one.
    }

    private fun CoroutineScope.createSupplier(
        maxPoolSize: Int,
        preWarmSize: Int = 0,
        hasSecondaryDisplay: Boolean = false,
    ) =
        PooledWindowDecorViewHostSupplier(
                context,
                this,
                testShellInit,
                fakeDesktopState,
                mockDisplayController,
                maxPoolSize,
                preWarmSize,
            )
            .also {
                testShellInit.init()

                if (Flags.enablePerDisplayWindowDecorViewHostPool()) {
                    verify(mockDisplayController)
                        .addDisplayWindowListener(onDisplaysChangedListenerCaptor.capture())
                    onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(
                        context.display.displayId
                    )
                    if (hasSecondaryDisplay) {
                        prepareSecondaryDisplay()
                    }
                }
            }

    private fun prepareSecondaryDisplay() {
        val secondDisplay: Display = spy(context.display)
        whenever(secondDisplay.getDisplayId()).thenReturn(SECOND_DISPLAY_ID)
        whenever(secondContext.getDisplay()).thenReturn(secondDisplay)

        onDisplaysChangedListenerCaptor.lastValue.onDisplayAdded(SECOND_DISPLAY_ID)
    }

    private class FakeWindowDecorViewHost(private val dispId: Int = 0) : WindowDecorViewHost {
        var released = false
            private set

        var reset = false
            private set

        override val surfaceControl: SurfaceControl
            get() = SurfaceControl()

        override val displayId: Int
            get() = dispId

        override fun updateView(
            view: View,
            attrs: WindowManager.LayoutParams,
            configuration: Configuration,
            touchableRegion: Region?,
            onDrawTransaction: SurfaceControl.Transaction?,
        ) {}

        override fun updateViewAsync(
            view: View,
            attrs: WindowManager.LayoutParams,
            configuration: Configuration,
            touchableRegion: Region?,
        ) {}

        override fun release(t: SurfaceControl.Transaction) {
            released = true
        }

        override fun reset() {
            reset = true
        }
    }

    companion object {
        private const val SECOND_DISPLAY_ID = 2
    }
}
