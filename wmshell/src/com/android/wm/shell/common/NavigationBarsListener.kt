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

import android.graphics.Insets
import android.graphics.Rect
import android.view.InsetsState
import android.view.WindowInsets.Type
import com.android.wm.shell.common.DisplayInsetsController.OnInsetsChangedListener

abstract class NavigationBarsListener(
    private val displayController: DisplayController,
    val displayId: Int
) : OnInsetsChangedListener {
    private var oldInsets = Insets.NONE

    override fun insetsChanged(insetsState: InsetsState) {
        getNavigationBarsInsets(insetsState).takeIf { it != oldInsets }?.let { newInsets ->
            oldInsets = newInsets
            onNavigationBarsVisibilityChanged(newInsets)
        }
    }

    private fun getNavigationBarsInsets(insetsState: InsetsState): Insets {
        val layout = displayController.getDisplayLayout(displayId) ?: return Insets.NONE
        val displayBounds = Rect(0, 0, layout.width(), layout.height())
        return insetsState.calculateInsets(displayBounds, displayBounds,
            Type.navigationBars(), /* ignoreVisibility= */true)
    }

    protected abstract fun onNavigationBarsVisibilityChanged(insets: Insets)
}