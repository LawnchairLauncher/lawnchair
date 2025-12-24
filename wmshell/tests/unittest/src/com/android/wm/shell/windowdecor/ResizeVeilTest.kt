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
package com.android.wm.shell.windowdecor

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowlessWindowManager
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.windowdecor.WindowDecoration.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/**
 * Tests for [ResizeVeil].
 *
 * Build/Install/Run: atest WMShellUnitTests:ResizeVeilTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
class ResizeVeilTest : ShellTestCase() {

    @Mock private lateinit var mockDisplayController: DisplayController
    @Mock private lateinit var mockAppIcon: Bitmap
    @Mock private lateinit var mockDisplay: Display
    @Mock private lateinit var mockSurfaceControlViewHost: SurfaceControlViewHost
    @Mock
    private lateinit var mockSurfaceControlBuilderFactory: ResizeVeil.SurfaceControlBuilderFactory
    @Mock private lateinit var mockSurfaceControlViewHostFactory: SurfaceControlViewHostFactory
    @Spy private val spyResizeVeilSurfaceBuilder = SurfaceControl.Builder()
    @Mock private lateinit var mockResizeVeilSurface: SurfaceControl
    @Spy private val spyBackgroundSurfaceBuilder = SurfaceControl.Builder()
    @Mock private lateinit var mockBackgroundSurface: SurfaceControl
    @Spy private val spyIconSurfaceBuilder = SurfaceControl.Builder()
    @Mock private lateinit var mockIconSurface: SurfaceControl
    @Mock private lateinit var mockTransaction: SurfaceControl.Transaction
    @Mock private lateinit var mockTaskResourceLoader: WindowDecorTaskResourceLoader

    private val taskInfo = TestRunningTaskInfoBuilder().build()

    @Before
    fun setUp() {
        whenever(mockSurfaceControlViewHostFactory.create(any(), any(), any(), any()))
            .thenReturn(mockSurfaceControlViewHost)
        whenever(mockSurfaceControlBuilderFactory.create("Resize veil of Task=" + taskInfo.taskId))
            .thenReturn(spyResizeVeilSurfaceBuilder)
        doReturn(mockResizeVeilSurface).whenever(spyResizeVeilSurfaceBuilder).build()
        whenever(
                mockSurfaceControlBuilderFactory.create(
                    eq("Resize veil background of Task=" + taskInfo.taskId)
                )
            )
            .thenReturn(spyBackgroundSurfaceBuilder)
        doReturn(mockBackgroundSurface).whenever(spyBackgroundSurfaceBuilder).build()
        whenever(
                mockSurfaceControlBuilderFactory.create(
                    "Resize veil icon of Task=" + taskInfo.taskId
                )
            )
            .thenReturn(spyIconSurfaceBuilder)
        doReturn(mockIconSurface).whenever(spyIconSurfaceBuilder).build()

        doReturn(mockTransaction).whenever(mockTransaction).setLayer(any(), anyInt())
        doReturn(mockTransaction).whenever(mockTransaction).setAlpha(any(), anyFloat())
        doReturn(mockTransaction).whenever(mockTransaction).show(any())
        doReturn(mockTransaction).whenever(mockTransaction).hide(any())
        doReturn(mockTransaction)
            .whenever(mockTransaction)
            .setPosition(any(), anyFloat(), anyFloat())
        doReturn(mockTransaction).whenever(mockTransaction).setWindowCrop(any(), anyInt(), anyInt())
    }

    @Test
    fun init_displayAvailable_viewHostCreated() = runTest {
        createResizeVeil(withDisplayAvailable = true)

        verify(mockSurfaceControlViewHostFactory)
            .create(any(), eq(mockDisplay), any(), eq("ResizeVeil"))
    }

    @Test
    fun init_displayUnavailable_viewHostNotCreatedUntilDisplayAppears() = runTest {
        createResizeVeil(withDisplayAvailable = false)

        verify(mockSurfaceControlViewHostFactory, never())
            .create(any(), eq(mockDisplay), any<WindowlessWindowManager>(), eq("ResizeVeil"))
        val captor = ArgumentCaptor.forClass(OnDisplaysChangedListener::class.java)
        verify(mockDisplayController).addDisplayWindowListener(captor.capture())

        whenever(mockDisplayController.getDisplay(taskInfo.displayId)).thenReturn(mockDisplay)
        captor.value.onDisplayAdded(taskInfo.displayId)

        verify(mockSurfaceControlViewHostFactory)
            .create(any(), eq(mockDisplay), any(), eq("ResizeVeil"))
        verify(mockDisplayController).removeDisplayWindowListener(any())
    }

    @Test
    fun dispose_removesDisplayWindowListener() = runTest {
        createResizeVeil().dispose()

        verify(mockDisplayController).removeDisplayWindowListener(any())
    }

    @Test
    fun showVeil() = runTest {
        val veil = createResizeVeil()

        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)

        verify(mockTransaction).show(mockResizeVeilSurface)
        verify(mockTransaction).show(mockBackgroundSurface)
        verify(mockTransaction).show(mockIconSurface)
        verify(mockTransaction).apply()
    }

    @Test
    fun showVeil_displayUnavailable_doesNotShow() = runTest {
        val veil = createResizeVeil(withDisplayAvailable = false)

        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)

        verify(mockTransaction, never()).show(mockResizeVeilSurface)
        verify(mockTransaction, never()).show(mockBackgroundSurface)
        verify(mockTransaction, never()).show(mockIconSurface)
        verify(mockTransaction).apply()
    }

    @Test
    fun showVeil_alreadyVisible_doesNotShowAgain() = runTest {
        val veil = createResizeVeil()

        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)
        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)

        verify(mockTransaction, times(1)).show(mockResizeVeilSurface)
        verify(mockTransaction, times(1)).show(mockBackgroundSurface)
        verify(mockTransaction, times(1)).show(mockIconSurface)
        verify(mockTransaction, times(2)).apply()
    }

    @Test
    fun showVeil_reparentsVeilToNewParent() = runTest {
        val veil = createResizeVeil(parent = mock())

        val newParent = mock<SurfaceControl>()
        veil.showVeil(
            mockTransaction,
            newParent,
            Rect(0, 0, 100, 100),
            taskInfo,
            false, /* fadeIn */
        )

        verify(mockTransaction).reparent(mockResizeVeilSurface, newParent)
    }

    @Test
    fun hideVeil_alreadyHidden_doesNothing() = runTest {
        val veil = createResizeVeil()

        veil.hideVeil()

        verifyNoMoreInteractions(mockTransaction)
    }

    @Test
    fun showVeil_loadsIconInBackground() = runTest {
        val veil = createResizeVeil()
        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)

        advanceUntilIdle()

        verify(mockTaskResourceLoader).getVeilIcon(taskInfo)
        assertThat((veil.iconView.drawable as BitmapDrawable).bitmap).isEqualTo(mockAppIcon)
    }

    @Test
    fun dispose_iconLoading_cancelsJob() = runTest {
        val veil = createResizeVeil()
        veil.showVeil(mockTransaction, mock(), Rect(0, 0, 100, 100), taskInfo, false /* fadeIn */)

        veil.dispose()
        advanceUntilIdle()

        assertThat(veil.iconView.drawable).isNull()
    }

    private suspend fun TestScope.createResizeVeil(
        withDisplayAvailable: Boolean = true,
        parent: SurfaceControl = mock(),
    ): ResizeVeil {
        whenever(mockDisplayController.getDisplay(taskInfo.displayId))
            .thenReturn(if (withDisplayAvailable) mockDisplay else null)
        whenever(mockTaskResourceLoader.getVeilIcon(taskInfo)).thenReturn(mockAppIcon)
        return ResizeVeil(
            context,
            mockDisplayController,
            mockTaskResourceLoader,
            StandardTestDispatcher(testScheduler),
            this,
            parent,
            { mockTransaction },
            mockSurfaceControlBuilderFactory,
            mockSurfaceControlViewHostFactory,
            taskInfo,
        )
    }
}
