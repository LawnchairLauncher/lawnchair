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

package com.android.launcher3.folder

import android.content.Context
import android.graphics.Point
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Alarm
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APP_PAIR
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FOLDER
import com.android.launcher3.OnAlarmListener
import com.android.launcher3.R
import com.android.launcher3.celllayout.board.FolderPoint
import com.android.launcher3.celllayout.board.TestWorkspaceBuilder
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.folder.Folder.MIN_CONTENT_DIMEN
import com.android.launcher3.folder.Folder.ON_EXIT_CLOSE_DELAY
import com.android.launcher3.folder.Folder.SCROLL_LEFT
import com.android.launcher3.folder.Folder.SCROLL_NONE
import com.android.launcher3.folder.Folder.STATE_ANIMATING
import com.android.launcher3.folder.Folder.STATE_CLOSED
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.ModelTestExtensions.clearModelDb
import java.util.ArrayList
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

/** Tests for [Folder] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class FolderTest {

    private lateinit var dragController: DragController<*>
    private lateinit var context: Context
    private lateinit var workspaceBuilder: TestWorkspaceBuilder
    private lateinit var folder: Folder

    @Before
    fun setUp() {
        dragController = Mockito.mock(DragController::class.java)
        context =
            object : ActivityContextWrapper(ApplicationProvider.getApplicationContext()) {
                override fun <T : DragController<*>> getDragController(): T = dragController as T
            }

        workspaceBuilder = TestWorkspaceBuilder(context)
        folder = spy(Folder(context, null))
    }

    @After
    fun tearDown() {
        LauncherAppState.getInstance(context).model.clearModelDb()
    }

    @Test
    fun `Undo a folder with 1 icon when onDropCompleted is called`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = folderInfo
        folder.mInfo.getContents().removeAt(0)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val dragLayout = Mockito.mock(View::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        folder.deleteFolderOnDropCompleted = false

        folder.onDropCompleted(dragLayout, dragObject, true)

        verify(folder, times(1)).replaceFolderWithFinalItem()
        assertEquals(folder.deleteFolderOnDropCompleted, false)
    }

    @Test
    fun `Do not undo a folder with 2 icons when onDropCompleted is called`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = folderInfo
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val dragLayout = Mockito.mock(View::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        folder.deleteFolderOnDropCompleted = false

        folder.onDropCompleted(dragLayout, dragObject, true)

        verify(folder, times(0)).replaceFolderWithFinalItem()
        assertEquals(folder.deleteFolderOnDropCompleted, false)
    }

    @Test
    fun `Test that we accept valid item type ITEM_TYPE_APPLICATION`() {
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_APPLICATION

        val willAcceptResult = Folder.willAccept(itemInfo)

        assertTrue(willAcceptResult)
    }

    @Test
    fun `Test that we accept valid item type ITEM_TYPE_DEEP_SHORTCUT`() {
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_DEEP_SHORTCUT

        val willAcceptResult = Folder.willAccept(itemInfo)

        assertTrue(willAcceptResult)
    }

    @Test
    fun `Test that we accept valid item type ITEM_TYPE_APP_PAIR`() {
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_APP_PAIR

        val willAcceptResult = Folder.willAccept(itemInfo)

        assertTrue(willAcceptResult)
    }

    @Test
    fun `Test that we do not accept invalid item type ITEM_TYPE_APPWIDGET`() {
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_APPWIDGET

        val willAcceptResult = Folder.willAccept(itemInfo)

        assertFalse(willAcceptResult)
    }

    @Test
    fun `Test that we do not accept invalid item type ITEM_TYPE_FOLDER`() {
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_FOLDER

        val willAcceptResult = Folder.willAccept(itemInfo)

        assertFalse(willAcceptResult)
    }

    @Test
    fun `We should not animate open if items is null or less than or equal to 1`() {
        folder.mInfo = Mockito.mock(FolderInfo::class.java)
        val shouldAnimateOpenResult = folder.shouldAnimateOpen(null)

        assertFalse(shouldAnimateOpenResult)
        assertFalse(
            folder.shouldAnimateOpen(arrayListOf<ItemInfo>(Mockito.mock(ItemInfo::class.java)))
        )
    }

    @Test
    fun `We should animate open if items greater than 1`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = folderInfo

        val shouldAnimateOpenResult = folder.shouldAnimateOpen(folder.mInfo.getContents())

        assertTrue(shouldAnimateOpenResult)
    }

    @Test
    fun `Should be true if there is an open folder`() {
        val closeOpenFolderResult = folder.closeOpenFolder(Mockito.mock(Folder::class.java))

        assertTrue(closeOpenFolderResult)
    }

    @Test
    fun `Should be false if the open folder is this folder`() {
        val closeOpenFolderResult = folder.closeOpenFolder(folder)

        assertFalse(closeOpenFolderResult)
    }

    @Test
    fun `Should be false if there is not an open folder`() {
        val closeOpenFolderResult = folder.closeOpenFolder(null)

        assertFalse(closeOpenFolderResult)
    }

    @Test
    fun `If drag is in progress we should set mItemAddedBackToSelfViaIcon to true`() {
        folder.itemAddedBackToSelfViaIcon = false
        folder.isDragInProgress = true

        folder.notifyDrop()

        assertTrue(folder.itemAddedBackToSelfViaIcon)
    }

    @Test
    fun `If drag is not in progress we should not set mItemAddedBackToSelfViaIcon to true`() {
        folder.itemAddedBackToSelfViaIcon = false
        folder.isDragInProgress = false

        folder.notifyDrop()

        assertFalse(folder.itemAddedBackToSelfViaIcon)
    }

    @Test
    fun `If launcher dragging is not enabled onLongClick should return true`() {
        `when`(folder.isLauncherDraggingEnabled).thenReturn(false)

        val onLongClickResult = folder.onLongClick(Mockito.mock(View::class.java))

        assertTrue(onLongClickResult)
    }

    @Test
    fun `If launcher dragging is enabled we should return startDrag result`() {
        `when`(folder.isLauncherDraggingEnabled).thenReturn(true)
        val viewMock = Mockito.mock(View::class.java)
        val dragOptions = Mockito.mock(DragOptions::class.java)

        val onLongClickResult = folder.onLongClick(viewMock)

        assertEquals(onLongClickResult, folder.startDrag(viewMock, dragOptions))
        verify(folder, times(1)).startDrag(viewMock, dragOptions)
    }

    @Test
    fun `Verify start drag works as intended when view is instanceof ItemInfo`() {
        val itemInfo = ItemInfo()
        itemInfo.rank = 5
        val viewMock = Mockito.mock(View::class.java)
        val dragOptions = DragOptions()
        `when`(viewMock.tag).thenReturn(itemInfo)

        folder.startDrag(viewMock, dragOptions)

        assertEquals(folder.mEmptyCellRank, 5)
        assertEquals(folder.currentDragView, viewMock)
        verify(folder, times(1)).addDragListener(dragOptions)
        verify(folder, times(1)).callBeginDragShared(viewMock, dragOptions)
    }

    @Test
    fun `Verify start drag works as intended when view is not instanceof ItemInfo`() {
        val viewMock = Mockito.mock(View::class.java)
        val dragOptions = DragOptions()

        folder.startDrag(viewMock, dragOptions)

        verify(folder, times(0)).addDragListener(dragOptions)
        verify(folder, times(0)).callBeginDragShared(viewMock, dragOptions)
    }

    @Test
    fun `Verify that onDragStart has an effect if dragSource is this folder`() {
        folder.itemsInvalidated = false
        folder.isDragInProgress = false
        folder.itemAddedBackToSelfViaIcon = true
        folder.currentDragView = Mockito.mock(View::class.java)
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = spy(folderInfo)
        val dragObject = DragObject(context)
        dragObject.dragInfo = Mockito.mock(ItemInfo::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        dragObject.dragSource = folder

        folder.onDragStart(dragObject, DragOptions())

        verify(folder.mContent, times(1)).removeItem(folder.currentDragView)
        verify(folder, times(1)).removeFolderContent(true, dragObject.dragInfo)
        assertTrue(folder.itemsInvalidated)
        assertTrue(folder.isDragInProgress)
        assertFalse(folder.itemAddedBackToSelfViaIcon)
    }

    @Test
    fun `Verify that onDragStart has no effects if dragSource is not this folder`() {
        folder.itemsInvalidated = false
        folder.isDragInProgress = false
        folder.itemAddedBackToSelfViaIcon = true
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val dragObject = DragObject(context)
        dragObject.dragSource = Mockito.mock(DragSource::class.java)

        folder.onDragStart(dragObject, DragOptions())

        verify(folder.mContent, times(0)).removeItem(folder.currentDragView)
        assertFalse(folder.itemsInvalidated)
        assertFalse(folder.isDragInProgress)
        assertTrue(folder.itemAddedBackToSelfViaIcon)
    }

    @Test
    fun `Verify onDragEnd that we call completeDragExit and set drag in progress false`() {
        doNothing().`when`(folder).completeDragExit()
        folder.isExternalDrag = true
        folder.isDragInProgress = true

        folder.onDragEnd()

        verify(folder, times(1)).completeDragExit()
        verify(dragController, times(1)).removeDragListener(folder)
        assertFalse(folder.isDragInProgress)
    }

    @Test
    fun `Verify onDragEnd that we do not call completeDragExit and set drag in progress false`() {
        folder.isExternalDrag = false
        folder.isDragInProgress = true

        folder.onDragEnd()

        verify(folder, times(0)).completeDragExit()
        verify(dragController, times(1)).removeDragListener(folder)
        assertFalse(folder.isDragInProgress)
    }

    @Test
    fun `startEditingFolderName should set hint to empty and showLabelSuggestions`() {
        doNothing().`when`(folder).showLabelSuggestions()
        folder.isEditingName = false
        folder.folderName = FolderNameEditText(context)
        folder.folderName.hint = "hello"

        folder.startEditingFolderName()

        verify(folder, times(1)).showLabelSuggestions()
        assertEquals("", folder.folderName.hint)
        assertTrue(folder.isEditingName)
    }

    @Test
    fun `Ensure we set the title and hint correctly onBackKey when we have a new title`() {
        val expectedHint = null
        val expectedTitle = "hello"
        folder.isEditingName = true
        folder.folderName = spy(FolderNameEditText(context))
        folder.folderName.setText(expectedTitle)
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = spy(folderInfo)
        folder.mInfo.title = "world"
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)

        folder.onBackKey()

        assertEquals(expectedTitle, folder.mInfo.title)
        verify(folder.mFolderIcon, times(1)).onTitleChanged(expectedTitle)
        assertEquals(expectedHint, folder.folderName.hint)
        assertFalse(folder.isEditingName)
        verify(folder.folderName, times(1)).clearFocus()
    }

    @Test
    fun `Ensure we set the title and hint correctly onBackKey when we do not have a new title`() {
        val expectedHint = context.getString(R.string.folder_hint_text)
        val expectedTitle = ""
        folder.isEditingName = true
        folder.folderName = spy(FolderNameEditText(context))
        folder.folderName.setText(expectedTitle)
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = spy(folderInfo)
        folder.mInfo.title = "world"
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)

        folder.onBackKey()

        assertEquals(expectedTitle, folder.mInfo.title)
        verify(folder.mFolderIcon, times(1)).onTitleChanged(expectedTitle)
        assertEquals(expectedHint, folder.folderName.hint)
        assertFalse(folder.isEditingName)
        verify(folder.folderName, times(1)).clearFocus()
    }

    @Test
    fun `ensure onEditorAction calls dispatchBackKey when actionId is IME_ACTION_DONE`() {
        folder.folderName = Mockito.mock(FolderNameEditText::class.java)

        val result =
            folder.onEditorAction(
                Mockito.mock(TextView::class.java),
                EditorInfo.IME_ACTION_DONE,
                Mockito.mock(KeyEvent::class.java),
            )

        assertTrue(result)
        verify(folder.folderName, times(1)).dispatchBackKey()
    }

    @Test
    fun `ensure onEditorAction does not call dispatchBackKey when actionId is not IME_ACTION_DONE`() {
        folder.folderName = Mockito.mock(FolderNameEditText::class.java)

        val result =
            folder.onEditorAction(
                Mockito.mock(TextView::class.java),
                EditorInfo.IME_ACTION_NONE,
                Mockito.mock(KeyEvent::class.java),
            )

        assertFalse(result)
        verify(folder.folderName, times(0)).dispatchBackKey()
    }

    @Test
    fun `in completeDragExit we close the folder when mIsOpen`() {
        doNothing().`when`(folder).close(true)
        folder.setIsOpen(true)
        folder.rearrangeOnClose = false

        folder.completeDragExit()

        verify(folder, times(1)).close(true)
        assertTrue(folder.rearrangeOnClose)
    }

    @Test
    fun `in completeDragExit we want to rearrange on close when it is animating`() {
        folder.setIsOpen(false)
        folder.rearrangeOnClose = false
        folder.state = STATE_ANIMATING

        folder.completeDragExit()

        verify(folder, times(0)).close(true)
        assertTrue(folder.rearrangeOnClose)
    }

    @Test
    fun `in completeDragExit we want to call rearrangeChildren and clearDragInfo when not open and not animating`() {
        doNothing().`when`(folder).rearrangeChildren()
        doNothing().`when`(folder).clearDragInfo()
        folder.setIsOpen(false)
        folder.rearrangeOnClose = false
        folder.state = STATE_CLOSED

        folder.completeDragExit()

        verify(folder, times(0)).close(true)
        assertFalse(folder.rearrangeOnClose)
        verify(folder, times(1)).rearrangeChildren()
        verify(folder, times(1)).clearDragInfo()
    }

    @Test
    fun `clearDragInfo should set current drag view to null and isExternalDrag to false`() {
        folder.currentDragView = Mockito.mock(DragView::class.java)
        folder.isExternalDrag = true

        folder.clearDragInfo()

        assertNull(folder.currentDragView)
        assertFalse(folder.isExternalDrag)
    }

    @Test
    fun `onDragExit should set alarm if drag is not complete`() {
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        dragObject.dragComplete = false

        folder.onDragExit(dragObject)

        verify(folder.onExitAlarm, times(1)).setOnAlarmListener(folder.mOnExitAlarmListener)
        verify(folder.onExitAlarm, times(1)).setAlarm(ON_EXIT_CLOSE_DELAY.toLong())
    }

    @Test
    fun `onDragExit should not set alarm if drag is complete`() {
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        dragObject.dragComplete = true

        folder.onDragExit(dragObject)

        verify(folder.onExitAlarm, times(0)).setOnAlarmListener(folder.mOnExitAlarmListener)
        verify(folder.onExitAlarm, times(0)).setAlarm(ON_EXIT_CLOSE_DELAY.toLong())
    }

    @Test
    fun `onDragExit should not clear scroll hint if already SCROLL_NONE`() {
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        folder.scrollHintDir = SCROLL_NONE
        folder.mContent = Mockito.mock(FolderPagedView::class.java)

        folder.onDragExit(dragObject)

        verify(folder.mContent, times(0)).clearScrollHint()
    }

    @Test
    fun `onDragExit should clear scroll hint if not SCROLL_NONE and then set scroll hint to scroll none`() {
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        folder.scrollHintDir = SCROLL_LEFT
        folder.mContent = Mockito.mock(FolderPagedView::class.java)

        folder.onDragExit(dragObject)

        verify(folder.mContent, times(1)).clearScrollHint()
        assertEquals(folder.scrollHintDir, SCROLL_NONE)
    }

    @Test
    fun `onDragExit we should cancel reorder pause and hint alarms`() {
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        val dragObject = Mockito.mock(DragObject::class.java)
        folder.scrollHintDir = SCROLL_NONE
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.reorderAlarm = Mockito.mock(Alarm::class.java)
        folder.onScrollHintAlarm = Mockito.mock(Alarm::class.java)
        folder.scrollPauseAlarm = Mockito.mock(Alarm::class.java)

        folder.onDragExit(dragObject)

        verify(folder.reorderAlarm, times(1)).cancelAlarm()
        verify(folder.onScrollHintAlarm, times(1)).cancelAlarm()
        verify(folder.scrollPauseAlarm, times(1)).cancelAlarm()
        assertEquals(folder.scrollHintDir, SCROLL_NONE)
    }

    @Test
    fun `when calling prepareAccessibilityDrop we should cancel pending reorder alarm and call onAlarm`() {
        folder.reorderAlarm = Mockito.mock(Alarm::class.java)
        folder.mReorderAlarmListener = Mockito.mock(OnAlarmListener::class.java)
        `when`(folder.reorderAlarm.alarmPending()).thenReturn(true)

        folder.prepareAccessibilityDrop()

        verify(folder.reorderAlarm, times(1)).cancelAlarm()
        verify(folder.mReorderAlarmListener, times(1)).onAlarm(folder.reorderAlarm)
    }

    @Test
    fun `when calling prepareAccessibilityDrop we should not do anything if there is no pending alarm`() {
        folder.reorderAlarm = Mockito.mock(Alarm::class.java)
        folder.mReorderAlarmListener = Mockito.mock(OnAlarmListener::class.java)
        `when`(folder.reorderAlarm.alarmPending()).thenReturn(false)

        folder.prepareAccessibilityDrop()

        verify(folder.reorderAlarm, times(0)).cancelAlarm()
        verify(folder.mReorderAlarmListener, times(0)).onAlarm(folder.reorderAlarm)
    }

    @Test
    fun `isDropEnabled should be true as long as state is not STATE_ANIMATING`() {
        folder.state = STATE_CLOSED

        val isDropEnabled = folder.isDropEnabled

        assertTrue(isDropEnabled)
    }

    @Test
    fun `isDropEnabled should be false if state is STATE_ANIMATING`() {
        folder.state = STATE_ANIMATING

        val isDropEnabled = folder.isDropEnabled

        assertFalse(isDropEnabled)
    }

    @Test
    fun `getItemCount should return the number of items in the folder`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = folderInfo

        val itemCount = folder.itemCount

        assertEquals(itemCount, 2)
    }

    @Test
    fun `hideItem should set the visibility of the corresponding ItemInfo to invisible`() {
        val itemInfo = ItemInfo()
        val view = View(context)
        view.isVisible = true
        doReturn(view).whenever(folder).getViewForInfo(itemInfo)

        folder.hideItem(itemInfo)

        assertFalse(view.isVisible)
    }

    @Test
    fun `showItem should set the visibility of the corresponding ItemInfo to visible`() {
        val itemInfo = ItemInfo()
        val view = View(context)
        view.isVisible = false
        doReturn(view).whenever(folder).getViewForInfo(itemInfo)

        folder.showItem(itemInfo)

        assertTrue(view.isVisible)
    }

    @Test
    fun `onDragEnter should cancel exit alarm and set the scroll area offset to dragRegionWidth divided by two minus xOffset`() {
        folder.mPrevTargetRank = 1
        val dragObject = Mockito.mock(DragObject::class.java)
        val dragView = Mockito.mock(DragView::class.java)
        dragObject.dragView = dragView
        folder.onExitAlarm = Mockito.mock(Alarm::class.java)
        `when`(dragObject.dragView.getDragRegionWidth()).thenReturn(100)
        dragObject.xOffset = 20

        folder.onDragEnter(dragObject)

        verify(folder.onExitAlarm, times(1)).cancelAlarm()
        assertEquals(-1, folder.mPrevTargetRank)
        assertEquals(30, folder.scrollAreaOffset)
    }

    @Test
    fun `acceptDrop should return true with the correct item type as a parameter`() {
        val dragObject = Mockito.mock(DragObject::class.java)
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_APP_PAIR
        dragObject.dragInfo = itemInfo

        val result = folder.acceptDrop(dragObject)

        assertTrue(result)
    }

    @Test
    fun `acceptDrop should return false with the incorrect item type as a parameter`() {
        val dragObject = Mockito.mock(DragObject::class.java)
        val itemInfo = Mockito.mock(ItemInfo::class.java)
        itemInfo.itemType = ITEM_TYPE_APPWIDGET
        dragObject.dragInfo = itemInfo

        val result = folder.acceptDrop(dragObject)

        assertFalse(result)
    }

    @Test
    fun `rearrangeChildren should return early if content view are not bound`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.itemsInvalidated = false
        doReturn(false).whenever(folder.mContent).areViewsBound()

        folder.rearrangeChildren()

        verify(folder.mContent, times(0)).arrangeChildren(folder.iconsInReadingOrder)
        assertFalse(folder.itemsInvalidated)
    }

    @Test
    fun `rearrangeChildren should call arrange children and invalidate items`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.itemsInvalidated = false
        doReturn(true).whenever(folder.mContent).areViewsBound()
        val iconsInReadingOrderList = ArrayList<View>()
        `when`(folder.iconsInReadingOrder).thenReturn(iconsInReadingOrderList)
        doNothing().`when`(folder.mContent).arrangeChildren(iconsInReadingOrderList)

        folder.rearrangeChildren()

        verify(folder.mContent, times(1)).arrangeChildren(folder.iconsInReadingOrder)
        assertTrue(folder.itemsInvalidated)
    }

    @Test
    fun `getItemCount should return the size of info getContents size`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        folder.mInfo = folderInfo

        val itemCount = folder.itemCount

        assertEquals(2, itemCount)
    }

    @Test
    fun `replaceFolderWithFinalItem should set mDestroyed to true if we replace folder with final item`() {
        val launcherDelegate = Mockito.mock(LauncherDelegate::class.java)
        folder.mLauncherDelegate = launcherDelegate
        `when`(folder.mLauncherDelegate.replaceFolderWithFinalItem(folder)).thenReturn(true)

        folder.replaceFolderWithFinalItem()

        assertTrue(folder.isDestroyed)
    }

    @Test
    fun `replaceFolderWithFinalItem should set mDestroyed to false if we do not replace folder with final item`() {
        val launcherDelegate = Mockito.mock(LauncherDelegate::class.java)
        folder.mLauncherDelegate = launcherDelegate
        `when`(folder.mLauncherDelegate.replaceFolderWithFinalItem(folder)).thenReturn(false)

        folder.replaceFolderWithFinalItem()

        assertFalse(folder.isDestroyed)
    }

    @Test
    fun `getContentAreaHeight should return maxContentAreaHeight`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredHeight).thenReturn(100)
        `when`(folder.maxContentAreaHeight).thenReturn(50)

        val height = folder.contentAreaHeight

        assertEquals(50, height)
    }

    @Test
    fun `getContentAreaHeight should return desiredHeight`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredHeight).thenReturn(50)
        `when`(folder.maxContentAreaHeight).thenReturn(100)

        val height = folder.contentAreaHeight

        assertEquals(50, height)
    }

    @Test
    fun `getContentAreaHeight should return MIN_CONTENT_DIMEN`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredHeight).thenReturn(1)
        `when`(folder.maxContentAreaHeight).thenReturn(2)

        val height = folder.contentAreaHeight

        assertEquals(MIN_CONTENT_DIMEN, height)
    }

    @Test
    fun `getContentAreaWidth should return desired width`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredWidth).thenReturn(50)

        val width = folder.contentAreaWidth

        assertEquals(50, width)
    }

    @Test
    fun `getContentAreaWidth should return MIN_CONTENT_DIMEN`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredWidth).thenReturn(1)

        val width = folder.contentAreaWidth

        assertEquals(MIN_CONTENT_DIMEN, width)
    }

    @Test
    fun `getFolderWidth should return padding left plus padding right plus desired width`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.mContent.desiredWidth).thenReturn(1)
        `when`(folder.paddingLeft).thenReturn(10)
        `when`(folder.paddingRight).thenReturn(10)

        val width = folder.folderWidth

        assertEquals(21, width)
    }

    @Test
    fun `getFolderHeight with no params should return getFolderHeight`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.contentAreaHeight).thenReturn(100)
        `when`(folder.getFolderHeight(folder.contentAreaHeight)).thenReturn(120)

        val height = folder.folderHeight

        assertEquals(120, height)
    }

    @Test
    fun `getFolderWidth with contentAreaHeight should return padding top plus padding bottom plus contentAreaHeight plus footer height`() {
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        `when`(folder.footerHeight).thenReturn(100)
        `when`(folder.paddingTop).thenReturn(10)
        `when`(folder.paddingBottom).thenReturn(10)

        val height = folder.getFolderHeight(100)

        assertEquals(220, height)
    }

    @Test
    fun `onRemove should call removeItem with the correct views`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        val items = folderInfo.getContents().toTypedArray()
        folder.mInfo = folderInfo
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val view1 = Mockito.mock(View::class.java)
        val view2 = Mockito.mock(View::class.java)
        doReturn(view1).whenever(folder).getViewForInfo(items[0])
        doReturn(view2).whenever(folder).getViewForInfo(items[1])
        doReturn(2).whenever(folder).itemCount

        folder.removeFolderContent(false, *items)

        verify(folder.mContent, times(1)).removeItem(view1)
        verify(folder.mContent, times(1)).removeItem(view2)
    }

    @Test
    fun `onRemove should set mRearrangeOnClose to true and not call rearrangeChildren if animating`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        val items = folderInfo.getContents().toTypedArray()
        folder.mInfo = folderInfo
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.state = STATE_ANIMATING
        val view1 = Mockito.mock(View::class.java)
        val view2 = Mockito.mock(View::class.java)
        doReturn(view1).whenever(folder).getViewForInfo(items[0])
        doReturn(view2).whenever(folder).getViewForInfo(items[1])
        doReturn(2).whenever(folder).itemCount

        folder.removeFolderContent(true, *items)

        assertTrue(folder.rearrangeOnClose)
        verify(folder, times(0)).rearrangeChildren()
    }

    @Test
    fun `onRemove should set not change mRearrangeOnClose and not call rearrangeChildren if not animating`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        val items = folderInfo.getContents().toTypedArray()
        folder.mInfo = folderInfo
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        folder.state = STATE_CLOSED
        folder.rearrangeOnClose = false
        val view1 = Mockito.mock(View::class.java)
        val view2 = Mockito.mock(View::class.java)
        doReturn(view1).whenever(folder).getViewForInfo(items[0])
        doReturn(view2).whenever(folder).getViewForInfo(items[1])
        doReturn(2).whenever(folder).itemCount

        folder.removeFolderContent(false, *items)

        assertFalse(folder.rearrangeOnClose)
        verify(folder, times(1)).rearrangeChildren()
    }

    @Test
    fun `onRemove should call close if mIsOpen is true and item count is less than or equal to one`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        val items = folderInfo.getContents().toTypedArray()
        folder.mInfo = folderInfo
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val view1 = Mockito.mock(View::class.java)
        val view2 = Mockito.mock(View::class.java)
        doReturn(view1).whenever(folder).getViewForInfo(items[0])
        doReturn(view2).whenever(folder).getViewForInfo(items[1])
        doReturn(1).whenever(folder).itemCount
        folder.setIsOpen(true)
        doNothing().`when`(folder).close(true)

        folder.removeFolderContent(false, *items)

        verify(folder, times(1)).close(true)
    }

    @Test
    fun `onRemove should call replaceFolderWithFinalItem if mIsOpen is false and item count is less than or equal to one`() {
        val folderInfo =
            workspaceBuilder.createFolderInCell(FolderPoint(Point(1, 0), TWO_ICON_FOLDER_TYPE), 0)
        val items = folderInfo.getContents().toTypedArray()
        folder.mInfo = folderInfo
        folder.mFolderIcon = Mockito.mock(FolderIcon::class.java)
        folder.mContent = Mockito.mock(FolderPagedView::class.java)
        val view1 = Mockito.mock(View::class.java)
        val view2 = Mockito.mock(View::class.java)
        doReturn(view1).whenever(folder).getViewForInfo(items[0])
        doReturn(view2).whenever(folder).getViewForInfo(items[1])
        doReturn(1).whenever(folder).itemCount
        folder.setIsOpen(false)

        folder.removeFolderContent(false, *items)

        verify(folder, times(1)).replaceFolderWithFinalItem()
    }

    companion object {
        const val TWO_ICON_FOLDER_TYPE = 'A'
    }
}
