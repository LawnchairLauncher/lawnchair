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
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.DragEvent
import com.android.launcher3.Launcher
import com.android.launcher3.icons.IconCache
import dagger.Lazy

/**
 * Listener for a single system-level drag-and-drop sequence.
 *
 * @param launcher The launcher associated with the sequence.
 * @param iconCache The icon cache used to generate drag images.
 */
class SystemDragListener(launcher: Launcher, private val iconCache: Lazy<IconCache>) :
    BaseItemDragListener(
        /*previewRect=*/ Rect(),
        /*previewBitmapWidth=*/ 0,
        /*previewViewWidth*/ 0,
    ) {

    private var cleanupCallback: Runnable? = null
    private var itemInfo: SystemDragItemInfo? = null

    init {
        init(launcher, /* isHomeStarted= */ launcher.isStarted)
    }

    /**
     * NOTE: This wildcard mime type will cause the listener to handle a system-level drag-and-drop
     * sequence for any {@link ClipData}, even if its payload will be rejected on drop. This is
     * necessary because {@link ClipData} is not exposed until {@link DragEvent.ACTION_DROP}, so
     * there is no app-agnostic way of determining payload acceptability during {@link
     * DragEvent.ACTION_DRAG_STARTED}.
     *
     * @see https://developer.android.com/reference/android/view/DragEvent
     */
    override fun getMimeType(): String = "*/*"

    /**
     * Sets a callback to be run when the listener is cleaned up.
     *
     * @param callback The callback to be run.
     */
    fun setCleanupCallback(callback: Runnable?) {
        cleanupCallback = callback
    }

    override fun onDrag(event: DragEvent): Boolean {
        if (event.action == DragEvent.ACTION_DROP) {
            try {
                itemInfo?.apply {
                    permissions = mLauncher.requestDragAndDropPermissions(event)
                    uriList =
                        event.clipData?.let { clipData ->
                            (0 until clipData.itemCount)
                                .mapNotNull(clipData::getItemAt)
                                .mapNotNull(ClipData.Item::getUri)
                                .distinct()
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to obtain URI permissions", e)
            }
        }
        return super.onDrag(event)
    }

    override fun startDrag(
        previewRect: Rect,
        previewBitmapWidth: Int,
        previewViewWidth: Int,
        screenPos: Point,
        options: DragOptions,
    ) {
        mLauncher.dragController?.run {
            itemInfo = SystemDragItemInfo()

            // TODO(b/440196506): Use a more appropriate drag image.
            val dragImage = iconCache.get().getDefaultIcon(itemInfo!!.user).newIcon(mLauncher)

            startDrag(
                dragImage,
                DraggableView.ofType(DraggableView.DRAGGABLE_ICON),
                /*dragLayerX=*/ screenPos.x - (dragImage.intrinsicWidth / 2),
                /*dragLayerY=*/ screenPos.y - (dragImage.intrinsicHeight / 2),
                /*source=*/ this@SystemDragListener,
                itemInfo,
                previewRect,
                /*initialDragViewScale=*/ 1.0f,
                /*dragViewScaleOnDrop=*/ 1.0f,
                options,
            )
        }
    }

    override fun postCleanup() {
        super.postCleanup()
        cleanupCallback?.run()
    }

    companion object {
        private const val TAG = "SystemDragListener"
    }
}
