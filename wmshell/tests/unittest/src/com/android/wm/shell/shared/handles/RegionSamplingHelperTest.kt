/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.shared.handles


import android.graphics.Rect
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.view.View
import android.view.ViewRootImpl
import androidx.concurrent.futures.DirectExecutor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.argumentCaptor

@RunWith(AndroidJUnit4::class)
@SmallTest
@RunWithLooper
class RegionSamplingHelperTest : ShellTestCase() {

    @Mock
    lateinit var sampledView: View
    @Mock
    lateinit var samplingCallback: RegionSamplingHelper.SamplingCallback
    @Mock
    lateinit var compositionListener: RegionSamplingHelper.SysuiCompositionSamplingListener
    @Mock
    lateinit var viewRootImpl: ViewRootImpl
    @Mock
    lateinit var surfaceControl: SurfaceControl
    @Mock
    lateinit var wrappedSurfaceControl: SurfaceControl
    @JvmField @Rule
    var rule = MockitoJUnit.rule()
    lateinit var regionSamplingHelper: RegionSamplingHelper

    @Before
    fun setup() {
        whenever(sampledView.isAttachedToWindow).thenReturn(true)
        whenever(sampledView.viewRootImpl).thenReturn(viewRootImpl)
        whenever(viewRootImpl.surfaceControl).thenReturn(surfaceControl)
        whenever(surfaceControl.isValid).thenReturn(true)
        whenever(wrappedSurfaceControl.isValid).thenReturn(true)
        whenever(samplingCallback.isSamplingEnabled).thenReturn(true)
        getInstrumentation().runOnMainSync(Runnable {
            regionSamplingHelper = object : RegionSamplingHelper(
                sampledView, samplingCallback,
                DirectExecutor.INSTANCE, DirectExecutor.INSTANCE, compositionListener
            ) {
                override fun wrap(stopLayerControl: SurfaceControl?): SurfaceControl {
                    return wrappedSurfaceControl
                }
            }
        })
        regionSamplingHelper.setWindowVisible(true)
    }

    @Test
    fun testStart_register() {
        regionSamplingHelper.start(Rect(0, 0, 100, 100))
        verify(compositionListener).register(any(), anyInt(), eq(wrappedSurfaceControl), any())
    }

    @Test
    fun testStart_unregister() {
        regionSamplingHelper.start(Rect(0, 0, 100, 100))
        regionSamplingHelper.setWindowVisible(false)
        verify(compositionListener).unregister(any())
    }

    @Test
    fun testStart_hasBlur_neverRegisters() {
        regionSamplingHelper.setWindowHasBlurs(true)
        regionSamplingHelper.start(Rect(0, 0, 100, 100))
        verify(compositionListener, never())
            .register(any(), anyInt(), eq(wrappedSurfaceControl), any())
    }

    @Test
    fun testStart_stopAndDestroy() {
        regionSamplingHelper.start(Rect(0, 0, 100, 100))
        regionSamplingHelper.stopAndDestroy()
        verify(compositionListener).unregister(any())
    }

    @Test
    fun testCompositionSamplingListener_has_nonEmptyRect() {
        // simulate race condition
        val fakeExecutor = TestShellExecutor() // pass in as backgroundExecutor
        val fakeSamplingCallback = mock(RegionSamplingHelper.SamplingCallback::class.java)

        whenever(fakeSamplingCallback.isSamplingEnabled).thenReturn(true)
        whenever(wrappedSurfaceControl.isValid).thenReturn(true)
        getInstrumentation().runOnMainSync(Runnable {
            regionSamplingHelper = object : RegionSamplingHelper(
                sampledView, fakeSamplingCallback,
                DirectExecutor.INSTANCE, fakeExecutor, compositionListener
            ) {
                override fun wrap(stopLayerControl: SurfaceControl?): SurfaceControl {
                    return wrappedSurfaceControl
                }
            }
        })
        regionSamplingHelper.setWindowVisible(true)
        regionSamplingHelper.start(Rect(0, 0, 100, 100))

        // make sure background task is enqueued
        assertThat(fakeExecutor.getCallbacks().size).isEqualTo(1)

        // make sure regionSamplingHelper will have empty Rect
        whenever(fakeSamplingCallback.getSampledRegion(any())).thenReturn(Rect(0, 0, 0, 0))
        regionSamplingHelper.onLayoutChange(sampledView, 0, 0, 0, 0, 0, 0, 0, 0)

        // resume running of background thread
        fakeExecutor.flushAll()

        // grab Rect passed into compositionSamplingListener and make sure it's not empty
        val argumentGrabber = argumentCaptor<Rect>()
        verify(compositionListener).register(any(), anyInt(), eq(wrappedSurfaceControl),
            argumentGrabber.capture())
        assertThat(argumentGrabber.firstValue.isEmpty).isFalse()
    }
}