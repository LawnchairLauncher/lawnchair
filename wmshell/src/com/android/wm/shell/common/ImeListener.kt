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

package com.android.wm.shell.common

import android.graphics.Rect
import android.view.InsetsSource
import android.view.InsetsState
import com.android.wm.shell.common.DisplayInsetsController.OnInsetsChangedListener

abstract class ImeListener(
    private val displayController: DisplayController,
    val displayId: Int
) : OnInsetsChangedListener {
    // The last insets state
    private val mInsetsState = InsetsState()
    private val mTmpBounds = Rect()

    override fun insetsChanged(insetsState: InsetsState) {
        if (mInsetsState == insetsState) {
            return
        }

        // Get the stable bounds that account for display cutout and system bars to calculate the
        // relative IME height
        val layout = displayController.getDisplayLayout(displayId) ?: return
        layout.getStableBounds(mTmpBounds)

        val (wasVisible, oldHeight) = getImeVisibilityAndHeight(mInsetsState)
        val (isVisible, newHeight) = getImeVisibilityAndHeight(insetsState)

        mInsetsState.set(insetsState, true)
        if (wasVisible != isVisible || oldHeight != newHeight) {
            onImeVisibilityChanged(isVisible, newHeight)
        }
    }

    private fun getImeVisibilityAndHeight(insetsState: InsetsState): Pair<Boolean, Int> {
        val source = insetsState.peekSource(InsetsSource.ID_IME)
        val frame = if (source != null && source.isVisible) source.frame else null
        val height = if (frame != null) mTmpBounds.bottom - frame.top else 0
        val visible = source?.isVisible ?: false
        return Pair(visible, height)
    }

    /**
     * To be overridden by implementations to handle IME changes.
     */
    protected abstract fun onImeVisibilityChanged(imeVisible: Boolean, imeHeight: Int)
}