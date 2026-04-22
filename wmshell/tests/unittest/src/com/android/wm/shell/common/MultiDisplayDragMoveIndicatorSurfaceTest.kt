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
package com.android.wm.shell.common

import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.shared.R
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests for [MultiDisplayDragMoveIndicatorSurface].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayDragMoveIndicatorSurfaceTest
 */
@RunWith(AndroidTestingRunner::class)
class MultiDisplayDragMoveIndicatorSurfaceTest : ShellTestCase() {
    private lateinit var display: Display
    private val taskInfo = TestRunningTaskInfoBuilder().build()
    private val mockSurface = mock<SurfaceControl>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()

    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var dragIndicatorSurface: MultiDisplayDragMoveIndicatorSurface

    @Before
    fun setUp() {
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SurfaceControl::class.java)
                .startMocking()

        display = mContext.display
        whenever(
                mContext.orCreateTestableResources.resources.getDimensionPixelSize(
                    R.dimen.desktop_windowing_freeform_rounded_corner_radius
                )
            )
            .thenReturn(CORNER_RADIUS)

        whenever(mockTransaction.remove(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCrop(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setCornerRadius(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.show(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.hide(any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setColor(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setLayer(any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setAlpha(any(), any())).thenReturn(mockTransaction)
        whenever(SurfaceControl.mirrorSurface(any())).thenReturn(mockSurface)

        dragIndicatorSurface =
            MultiDisplayDragMoveIndicatorSurface(mContext, mock<SurfaceControl>())
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun dispose_removesVeil() {
        dragIndicatorSurface.dispose(mockTransaction)

        verify(mockTransaction).remove(eq(mockSurface))
    }

    @Test
    fun dispose_doesNothingIfAlreadyDisposed() {
        dragIndicatorSurface.dispose(mockTransaction)
        clearInvocations(mockTransaction)

        dragIndicatorSurface.dispose(mockTransaction)

        verify(mockTransaction, never()).remove(any())
    }

    @Test
    fun show_reparentsSetsCropShowsSetsColorAppliesTransaction() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
            SCALE,
        )

        verify(mockRootTaskDisplayAreaOrganizer)
            .reparentToDisplayArea(eq(DEFAULT_DISPLAY), eq(mockSurface), eq(mockTransaction))
        verify(mockTransaction).setCornerRadius(eq(mockSurface), eq(CORNER_RADIUS.toFloat()))
        verify(mockTransaction)
            .setPosition(eq(mockSurface), eq(BOUNDS.left.toFloat()), eq(BOUNDS.top.toFloat()))
        verify(mockTransaction).show(eq(mockSurface))
        verify(mockTransaction).setScale(eq(mockSurface), eq(SCALE), eq(SCALE))
    }

    @Test
    fun relayout_whenVisibleAndDoesntChangeVisibility_setsCropAndPosition() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
            SCALE,
        )
        clearInvocations(mockTransaction)

        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction)
            .setPosition(
                eq(mockSurface),
                eq(NEW_BOUNDS.left.toFloat()),
                eq(NEW_BOUNDS.top.toFloat()),
            )
    }

    @Test
    fun relayout_whenVisibleAndShouldBeInvisible_setsCropAndPosition() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
            SCALE,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE,
        )

        verify(mockTransaction)
            .setPosition(
                eq(mockSurface),
                eq(NEW_BOUNDS.left.toFloat()),
                eq(NEW_BOUNDS.top.toFloat()),
            )
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeVisible_setsCropAndPosition() {
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction)
            .setPosition(
                eq(mockSurface),
                eq(NEW_BOUNDS.left.toFloat()),
                eq(NEW_BOUNDS.top.toFloat()),
            )
    }

    @Test
    fun relayout_whenInvisibleAndShouldBeInvisible_doesNotSetCropOrPosition() {
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.INVISIBLE,
        )

        verify(mockTransaction, never()).setPosition(any(), any(), any())
    }

    @Test
    fun relayout_whenVisibleAndShouldBeTranslucent_setAlpha() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
            SCALE,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT,
        )

        verify(mockTransaction).setAlpha(eq(mockSurface), eq(ALPHA_FOR_TRANSLUCENT))
    }

    @Test
    fun relayout_whenTranslucentAndShouldBeVisible_setAlpha() {
        dragIndicatorSurface.show(
            mockTransaction,
            taskInfo,
            mockRootTaskDisplayAreaOrganizer,
            DEFAULT_DISPLAY,
            BOUNDS,
            MultiDisplayDragMoveIndicatorSurface.Visibility.TRANSLUCENT,
            SCALE,
        )
        clearInvocations(mockTransaction)
        dragIndicatorSurface.relayout(
            NEW_BOUNDS,
            mockTransaction,
            MultiDisplayDragMoveIndicatorSurface.Visibility.VISIBLE,
        )

        verify(mockTransaction).setAlpha(eq(mockSurface), eq(ALPHA_FOR_VISIBLE))
    }

    companion object {
        private const val CORNER_RADIUS = 32
        private const val SCALE = 1.5f
        private val BOUNDS = Rect(10, 20, 100, 200)
        private val NEW_BOUNDS = Rect(50, 50, 150, 250)
        private val ALPHA_FOR_TRANSLUCENT = 0.7f
        private val ALPHA_FOR_VISIBLE = 1.0f
    }
}
