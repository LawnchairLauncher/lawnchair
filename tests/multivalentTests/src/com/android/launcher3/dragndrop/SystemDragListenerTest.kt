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

package com.android.launcher3.dragndrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.graphics.Point
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.DragAndDropPermissions
import android.view.DragEvent
import android.view.View
import androidx.test.filters.SmallTest
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags.FLAG_ENABLE_SYSTEM_DRAG
import com.android.launcher3.Launcher
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.IconCache
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.android.launcher3.util.RoboApiWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Tests for {@link SystemDragListener}. */
@SmallTest
@EnableFlags(FLAG_ENABLE_SYSTEM_DRAG)
@RunWith(LauncherMultivalentJUnit::class)
class SystemDragListenerTest {

    @get:Rule val flags: SetFlagsRule = SetFlagsRule()
    @get:Rule val mockito = MockitoJUnit.rule()

    @Mock private lateinit var mockDragEvent: DragEvent
    @Mock private lateinit var mockDragImage: FastBitmapDrawable
    @Mock private lateinit var mockIconCache: IconCache
    @Mock private lateinit var mockLauncher: Launcher

    private lateinit var listener: SystemDragListener

    @Before
    fun setUp() {
        initMock(mockDragEvent)
        initMock(mockDragImage)
        initMock(mockIconCache)
        initMock(mockLauncher)

        listener = SystemDragListener(mockLauncher) { mockIconCache }

        // NOTE: The system drag listener registers itself with the launcher's drag controller
        // during construction. Verify the expected registration but then clear invocations so that
        // tests below don't need to be mindful of constructor-related interactions.
        verify(mockLauncher.dragController).addSystemDragHandler(listener)
        clearInvocations(mockLauncher.dragController)
    }

    @After
    fun tearDown() {
        // NOTE: Ensure that any tasks posted by the system drag listener under test have a chance
        // to run prior to test completion. Failure to do so may negatively impact subsequent tests.
        RoboApiWrapper.waitForLooperSync(Looper.getMainLooper())
    }

    @Test
    fun testCleanupCallback() {
        val callback = mock<Runnable>()
        listener.setCleanupCallback(callback)
        listener.onDropCompleted(mock<View>(), mock<DragObject>(), /* success= */ true)
        verify(callback).run()
    }

    @Test
    fun testDragLocation() {
        testDragLocation(/* dragInfoCaptor= */ null)
    }

    private fun testDragLocation(dragInfoCaptor: ArgumentCaptor<SystemDragItemInfo>?) {
        testDragStart(dragInfoCaptor)

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_LOCATION)
        whenever(mockLauncher.dragController.isDragging).thenReturn(true)

        assertTrue(listener.onDrag(mockDragEvent))
        verify(mockLauncher.dragController).isDragging
        verifyNoMoreInteractions(mockLauncher.dragController)
    }

    @Test
    fun testDragStart() {
        testDragStart(/* dragInfoCaptor= */ null)
    }

    private fun testDragStart(dragInfoCaptor: ArgumentCaptor<SystemDragItemInfo>?) {
        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DRAG_STARTED)
        whenever(mockLauncher.dragController.isDragging).thenReturn(false)

        val screenPos = Point(mockDragEvent.x.toInt(), mockDragEvent.y.toInt())
        val dragLayerX = screenPos.x - (mockDragImage.intrinsicWidth / 2)
        val dragLayerY = screenPos.y - (mockDragImage.intrinsicHeight / 2)

        assertTrue(listener.onDrag(mockDragEvent))
        verify(mockLauncher.dragController)
            .startDrag(
                /*drawable=*/ eq(mockDragImage),
                /*originalView=*/ argThat { viewType == DraggableView.DRAGGABLE_ICON },
                /*dragLayerX=*/ eq(dragLayerX),
                /*dragLayerY=*/ eq(dragLayerY),
                /*source=*/ eq(listener),
                /*dragInfo=*/ if (dragInfoCaptor != null) dragInfoCaptor.capture()
                else any<SystemDragItemInfo>(),
                /*dragRegion=*/ eq(Rect()),
                /*initialDragViewScale=*/ eq(1.0f),
                /*dragViewScaleOnDrop=*/ eq(1.0f),
                /*options=*/ argThat { simulatedDndStartPoint == screenPos },
            )
    }

    @Test
    fun testDropWhenRequestingPermissionsSucceeds() {
        testDrop(/* throwExceptionWhenRequestingPermissions= */ false)
    }

    @Test
    fun testDropWhenRequestingPermissionsThrowsException() {
        testDrop(/* throwExceptionWhenRequestingPermissions= */ true)
    }

    private fun testDrop(throwExceptionWhenRequestingPermissions: Boolean) {
        val dragInfoCaptor = ArgumentCaptor.forClass(SystemDragItemInfo::class.java)

        testDragLocation(dragInfoCaptor)
        clearInvocations(mockLauncher.dragController)

        assertNull(dragInfoCaptor.value.uriList)

        val mockUri1 = mock<Uri>()
        val mockUri2 = mock<Uri>()

        val mockClipItems =
            listOf(
                null,
                ClipData.Item(mock<CharSequence>()),
                ClipData.Item(mock<Intent>()),
                ClipData.Item(mockUri1),
                ClipData.Item(mockUri1),
                ClipData.Item(mockUri2),
            )

        val mockClipData =
            mock<ClipData>().apply {
                whenever(itemCount).thenReturn(mockClipItems.size)
                whenever(getItemAt(any())).thenAnswer { mockClipItems[it.getArgument(0)] }
            }

        whenever(mockDragEvent.action).thenReturn(DragEvent.ACTION_DROP)
        whenever(mockDragEvent.clipData).thenReturn(mockClipData)
        whenever(mockLauncher.requestDragAndDropPermissions(mockDragEvent)).thenAnswer {
            if (throwExceptionWhenRequestingPermissions) throw RuntimeException()
            mock<DragAndDropPermissions>()
        }

        assertTrue(listener.onDrag(mockDragEvent))
        verify(mockLauncher).requestDragAndDropPermissions(mockDragEvent)
        verify(mockLauncher.dragController).isDragging
        verifyNoMoreInteractions(mockLauncher.dragController)

        with(dragInfoCaptor.value) {
            if (throwExceptionWhenRequestingPermissions) {
                assertNull(permissions)
                assertNull(uriList)
            } else {
                assertNotNull(permissions)
                assertEquals(listOf(mockUri1, mockUri2), uriList)
            }
        }
    }

    private fun initMock(mockDragEvent: DragEvent) {
        val mockClipDescription = mock<ClipDescription>()
        whenever(mockClipDescription.hasMimeType("*/*")).thenReturn(true)
        whenever(mockDragEvent.clipDescription).thenReturn(mockClipDescription)
        whenever(mockDragEvent.x).thenReturn(DRAG_EVENT_X)
        whenever(mockDragEvent.y).thenReturn(DRAG_EVENT_Y)
    }

    private fun initMock(mockDragImage: FastBitmapDrawable) {
        whenever(mockDragImage.intrinsicHeight).thenReturn(DRAG_IMAGE_INTRINSIC_HEIGHT)
        whenever(mockDragImage.intrinsicWidth).thenReturn(DRAG_IMAGE_INTRINSIC_WIDTH)
    }

    private fun initMock(mockIconCache: IconCache) {
        val mockBitmapInfo = mock<BitmapInfo>()
        whenever(mockBitmapInfo.newIcon(any(), any(), anyOrNull())).thenReturn(mockDragImage)
        whenever(mockIconCache.getDefaultIcon(any())).thenReturn(mockBitmapInfo)
    }

    private fun initMock(mockLauncher: Launcher) {
        whenever(mockLauncher.dragController).thenReturn(mock())
        whenever(mockLauncher.dragLayer).thenReturn(mock())
        whenever(mockLauncher.intent).thenReturn(mock())
        whenever(mockLauncher.rotationHelper).thenReturn(mock())
        whenever(mockLauncher.stateManager).thenReturn(mock())
    }

    companion object {
        private const val DRAG_EVENT_X = 10.0f
        private const val DRAG_EVENT_Y = 20.0f
        private const val DRAG_IMAGE_INTRINSIC_HEIGHT = 24
        private const val DRAG_IMAGE_INTRINSIC_WIDTH = 48
    }
}
