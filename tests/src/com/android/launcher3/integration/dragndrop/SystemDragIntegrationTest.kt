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

package com.android.launcher3.integration.dragndrop

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipDescription.MIMETYPE_TEXT_PLAIN
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns.DISPLAY_NAME
import android.provider.MediaStore.Files.FileColumns.MIME_TYPE
import android.provider.MediaStore.Files.FileColumns.RELATIVE_PATH
import android.provider.MediaStore.Files.FileColumns._ID
import android.util.Log
import android.view.DragEvent
import android.view.DragEvent.ACTION_DRAG_LOCATION
import android.view.DragEvent.ACTION_DRAG_STARTED
import android.view.DragEvent.ACTION_DROP
import android.view.View
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_FILE_SYSTEM_FILE
import com.android.launcher3.dragndrop.SystemDragController
import com.android.launcher3.dragndrop.SystemDragControllerImpl
import com.android.launcher3.homescreenfiles.HomeScreenFilesNoOpProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider
import com.android.launcher3.homescreenfiles.HomeScreenFilesProvider.Companion.HOME_SCREEN_FOLDER_RELATIVE_PATH
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.BaseLauncherActivityTest
import com.android.launcher3.util.ReflectionHelpers
import com.android.launcher3.util.Wait.atMost
import com.android.launcher3.util.workspace.FavoriteItemsTransaction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Integration tests for system-level drag-and-drop. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SystemDragIntegrationTest : BaseLauncherActivityTest<Launcher>() {

    private val context: Context = targetContext()

    @Before
    fun setUp() {
        deleteAllHomeScreenFiles()
        FavoriteItemsTransaction(context).commit()
        loadLauncherSync()
    }

    @After
    fun tearDown() {
        FavoriteItemsTransaction(context).commit()
        deleteAllHomeScreenFiles()
    }

    @Test
    fun testDragAndDropWhenPayloadContainsImmovableUri() {
        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)),
            listOf(ClipData.Item("content://test/path/id".toUri())),
        )
    }

    @Test
    fun testDragAndDropWhenPayloadContainsMediaStoreUris() {
        val uniqueDisplayName = "${System.currentTimeMillis()}"

        val mediaStoreUris =
            listOf("$uniqueDisplayName (1).txt", "$uniqueDisplayName (2).txt").map { displayName ->
                context.contentResolver
                    .insert(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        ContentValues().apply {
                            put(DISPLAY_NAME, displayName)
                            put(MIME_TYPE, MIMETYPE_TEXT_PLAIN)
                            put(RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                        },
                    )
                    ?.also { mediaStoreUri ->
                        context.contentResolver.openOutputStream(mediaStoreUri)?.use { stream ->
                            stream.write(displayName.toByteArray())
                        }
                    }
            }

        assertTrue(mediaStoreUris.all(this::isMediaStoreUri))

        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)),
            mediaStoreUris.map(ClipData::Item),
        )
    }

    @Test
    fun testDragAndDropWhenPayloadContainsText() {
        testDragAndDrop(
            ClipDescription(/* label= */ "", /* mimeTypes= */ arrayOf(MIMETYPE_TEXT_PLAIN)),
            listOf(ClipData.Item("text")),
        )
    }

    private fun testDragAndDrop(description: ClipDescription, itemList: List<ClipData.Item>) {
        // Expect a workspace item to be created on system-level drag-and-drop if and only if:
        // (a) the home screen files provider is implemented,
        // (b) the system-level drag controller is implemented, and
        // (c) the dropped payload solely contains media store URIs.
        val expectWorkspaceItemCreated =
            HomeScreenFilesProvider.INSTANCE[context] !is HomeScreenFilesNoOpProvider &&
                SystemDragController.INSTANCE[context] is SystemDragControllerImpl &&
                itemList.map(ClipData.Item::getUri).all(this::isMediaStoreUri)

        val workspaceItemView =
            launcherActivity.getFromLauncher { launcher ->
                // Simulate a system-level drag-and-drop sequence.
                val bounds = Rect().apply(launcher.dragLayer::getBoundsOnScreen)
                val start = PointF(bounds.left.toFloat(), bounds.right.toFloat())
                val end = PointF(bounds.exactCenterX(), bounds.exactCenterY())
                launcher.dragLayer.dispatchDragAndDropSequence(start, end, description, itemList)

                // Expect workspace item creation (or lack thereof).
                assertThrowsIf(
                    "Workspace item created",
                    { findWorkspaceItem("Workspace item not created") },
                    !expectWorkspaceItemCreated,
                )
            }

        // Verify workspace item creation (or lack thereof).
        val workspaceItemCreated = workspaceItemView != null
        assertEquals(expectWorkspaceItemCreated, workspaceItemCreated)

        // If a workspace item was not created, there's nothing left to verify.
        if (!workspaceItemCreated) {
            return
        }

        // If external storage permissions are held, verify expected file system changes.
        if (Environment.isExternalStorageManager()) {
            return itemList.forEach { item ->
                atMost(
                    "'${item.uri}' not moved to '$HOME_SCREEN_FOLDER_RELATIVE_PATH'",
                    {
                        context.contentResolver
                            .query(
                                item.uri,
                                /*projection=*/ arrayOf(_ID),
                                /*selection=*/ "$RELATIVE_PATH = ?",
                                /*selectionArgs=*/ arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
                                /*sortOrder=*/ null,
                            )
                            ?.use { cursor -> cursor.count } == 1
                    },
                )
            }
        }

        // If external storage permissions are not held, verify workspace item removal.
        atMost("Workspace item not removed", { isRemovedFromLayout(workspaceItemView) })
    }

    private fun assertThrows(message: String, block: () -> Unit) {
        assertThrows(message, AssertionError::class.java, block)
    }

    private fun <T> assertThrowsIf(message: String, block: () -> T, condition: Boolean): T? {
        if (condition) {
            assertThrows(message, { block() })
            return null
        }
        return block()
    }

    private fun deleteAllHomeScreenFiles() {
        try {
            context.contentResolver.delete(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                "$RELATIVE_PATH = ?",
                arrayOf(HOME_SCREEN_FOLDER_RELATIVE_PATH),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to delete all home screen files", e)
        }
    }

    private fun findWorkspaceItem(message: String) =
        launcherActivity.getOnceNotNull(message) { launcher ->
            launcher.workspace.mapOverItems { itemInfo, _ -> isFileSystemFile(itemInfo) }
        }

    private fun isFileSystemFile(itemInfo: ItemInfo?) =
        itemInfo?.itemType == ITEM_TYPE_FILE_SYSTEM_FILE

    private fun isMediaStoreUri(uri: Uri?) =
        uri?.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY

    private fun isRemovedFromLayout(view: View?) =
        launcherActivity.getFromLauncher { view?.parent } == null

    private fun obtainDragEvent(
        action: Int,
        point: PointF,
        description: ClipDescription,
        items: List<ClipData.Item>? = null,
    ): DragEvent {
        val mockDragEvent = mock<DragEvent>()

        // NOTE: Reflection is necessary because `ViewGroup` inspects the `DragEvent.mAction` field
        // during event dispatching rather than using the mockable `DragEvent.getAction()` method.
        ReflectionHelpers.setField(mockDragEvent, "mAction", action)

        whenever(mockDragEvent.action).thenReturn(action)
        whenever(mockDragEvent.clipDescription).thenReturn(description)
        whenever(mockDragEvent.x).thenReturn(point.x)
        whenever(mockDragEvent.y).thenReturn(point.y)

        // NOTE: In production, clip data is only available during `ACTION_DROP` events.
        // See https://developer.android.com/reference/android/view/DragEvent.
        if (action == ACTION_DROP) {
            val item = if (items?.isEmpty() == false) items.first() else null
            val data = ClipData(description, item).apply { items?.drop(1)?.forEach(this::addItem) }
            whenever(mockDragEvent.clipData).thenReturn(data)
        }

        return mockDragEvent
    }

    private fun View.dispatchDragAndDropSequence(
        start: PointF,
        end: PointF,
        description: ClipDescription,
        items: List<ClipData.Item>,
    ) {
        val midpoint = PointF((start.x + end.x) / 2.0f, (start.y + end.y) / 2.0f)
        dispatchDragEvent(obtainDragEvent(ACTION_DRAG_STARTED, start, description))
        dispatchDragEvent(obtainDragEvent(ACTION_DRAG_LOCATION, midpoint, description))
        dispatchDragEvent(obtainDragEvent(ACTION_DROP, end, description, items))
    }

    companion object {
        private const val TAG = "SystemDragIntegrationTest"
    }
}
