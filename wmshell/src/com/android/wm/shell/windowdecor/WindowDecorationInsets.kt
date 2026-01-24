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

package com.android.wm.shell.windowdecor

import android.graphics.Insets
import android.graphics.Rect
import android.os.Binder
import android.view.InsetsSource
import android.view.WindowInsets
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction

/** Adds, removes, and updates caption insets. */
data class WindowDecorationInsets(
    private val token: WindowContainerToken,
    private val owner: Binder,
    private val frame: Rect,
    private val taskFrame: Rect? = null,
    private val boundingRects: Array<Rect>? = null,
    @InsetsSource.Flags private val flags: Int = 0,
    private val shouldAddCaptionInset: Boolean = false,
    private val excludedFromAppBounds: Boolean = false,
) {

    /** Updates the caption insets. */
    fun update(wct: WindowContainerTransaction) {
        if (!shouldAddCaptionInset) return
        if (com.android.window.flags.Flags.relativeInsets()) {
            val insets = Insets.of(0, frame.height(), 0, 0)
            wct.addInsetsSource(
                token,
                owner,
                INDEX,
                WindowInsets.Type.captionBar(),
                insets,
                boundingRects,
                flags,
            )
            wct.addInsetsSource(
                token,
                owner,
                INDEX,
                WindowInsets.Type.mandatorySystemGestures(),
                insets,
                boundingRects,
                /* flags= */ 0,
            )
        } else {
            wct.addInsetsSource(
                token,
                owner,
                INDEX,
                WindowInsets.Type.captionBar(),
                frame,
                boundingRects,
                flags,
            )
            wct.addInsetsSource(
                token,
                owner,
                INDEX,
                WindowInsets.Type.mandatorySystemGestures(),
                frame,
                boundingRects,
                /* flags= */ 0,
            )
        }
        if (excludedFromAppBounds) {
            val appBounds = Rect(taskFrame)
            appBounds.top += frame.height()
            wct.setAppBounds(token, appBounds)
        }
    }

    /** Removes the caption insets. */
    fun remove(wct: WindowContainerTransaction) {
        wct.removeInsetsSource(token, owner, INDEX, WindowInsets.Type.captionBar())
        wct.removeInsetsSource(
            token,
            owner,
            INDEX,
            WindowInsets.Type.mandatorySystemGestures()
        )
        if (excludedFromAppBounds) {
            wct.setAppBounds(token, Rect())
        }
    }

    companion object {
        /** Index for caption insets source. */
        private const val INDEX = 0
    }
}
