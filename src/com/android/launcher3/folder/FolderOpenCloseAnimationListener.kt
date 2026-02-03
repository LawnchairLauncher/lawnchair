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

package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import com.android.launcher3.CellLayout

class FolderOpenCloseAnimationListener(val folder: Folder, val isOpening: Boolean) :
    AnimatorListenerAdapter() {
    // Because {@link #onAnimationStart} and {@link #onAnimationEnd} callbacks are sent to
    // message queue and executed on separate frame, we should save states in
    // {@link #onAnimationStart} instead of before creating animator, so that cancelling
    // animation A and restarting animation B allows A to reset states in
    // {@link #onAnimationEnd} before B reads new UI state from {@link #onAnimationStart}.

    private var cellLayout: CellLayout? = null
    private var folderClipChildren = false
    private var folderClipToPadding = false
    private var contentClipChildren = false
    private var contentClipToPadding = false
    private var cellLayoutClipChildren = false
    private var cellLayoutClipPadding = false

    override fun onAnimationStart(animator: Animator) {
        super.onAnimationStart(animator)
        with(folder) {
            folderClipChildren = clipChildren
            folderClipToPadding = clipToPadding
            contentClipChildren = content.clipChildren
            contentClipToPadding = content.clipToPadding
            cellLayout = content.currentCellLayout
            cellLayoutClipChildren = cellLayout?.clipChildren ?: false
            cellLayoutClipPadding = cellLayout?.clipToPadding ?: false

            clipChildren = false
            clipToPadding = false
            content.clipChildren = false
            content.clipToPadding = false
            cellLayout?.clipChildren = false
            cellLayout?.clipToPadding = false
        }
    }

    override fun onAnimationEnd(animation: Animator) {
        super.onAnimationEnd(animation)
        with(folder) {
            translationX = 0.0f
            translationY = 0.0f
            translationZ = 0.0f
            content.scaleX = 1f
            content.scaleY = 1f
            mFooter.scaleX = 1f
            mFooter.scaleY = 1f
            mFooter.translationX = 0f
            folderName.alpha = 1f
            content.setClipPath(null)
            setClipPath(null)
            clipChildren = folderClipChildren
            clipToPadding = folderClipToPadding
            content.clipChildren = contentClipChildren
            content.clipToPadding = contentClipToPadding
            cellLayout?.clipChildren = cellLayoutClipChildren
            cellLayout?.clipToPadding = cellLayoutClipPadding
        }
    }
}
