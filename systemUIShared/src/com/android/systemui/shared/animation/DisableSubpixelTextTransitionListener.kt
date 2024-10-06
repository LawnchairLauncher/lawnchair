/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.shared.animation

import android.graphics.Paint
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEach
import com.android.app.tracing.traceSection
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.lang.ref.WeakReference

/**
 * A listener which disables subpixel flag for all TextView children of a given parent ViewGroup
 * during fold/unfold transitions.
 */
class DisableSubpixelTextTransitionListener(private val rootView: ViewGroup?) :
    TransitionProgressListener {
    private val childrenTextViews: MutableList<WeakReference<TextView>> = mutableListOf()
    private var isTransitionInProgress: Boolean = false

    override fun onTransitionStarted() {
        isTransitionInProgress = true
        traceSection("subpixelFlagSetForTextView") {
            traceSection("subpixelFlagTraverseHierarchy") {
                getAllChildTextView(rootView, childrenTextViews)
            }
            traceSection("subpixelFlagDisableForTextView") {
                childrenTextViews.forEach { child ->
                    val childTextView = child.get() ?: return@forEach
                    childTextView.paintFlags = childTextView.paintFlags or Paint.SUBPIXEL_TEXT_FLAG
                }
            }
        }
    }

    override fun onTransitionFinished() {
        if (!isTransitionInProgress) return
        isTransitionInProgress = false
        traceSection("subpixelFlagEnableForTextView") {
            childrenTextViews.forEach { child ->
                val childTextView = child.get() ?: return@forEach
                childTextView.paintFlags =
                    childTextView.paintFlags and Paint.SUBPIXEL_TEXT_FLAG.inv()
            }
            childrenTextViews.clear()
        }
    }

    /**
     * Populates a list of all TextView children of a given parent ViewGroup
     *
     * @param parent the root ViewGroup for which to retrieve TextView children
     * @param childrenTextViews the list to store the retrieved TextView children
     */
    private fun getAllChildTextView(
        parent: ViewGroup?,
        childrenTextViews: MutableList<WeakReference<TextView>>
    ) {
        parent?.forEach { child ->
            when (child) {
                is ViewGroup -> getAllChildTextView(child, childrenTextViews)
                is TextView -> {
                    if ((child.paintFlags and Paint.SUBPIXEL_TEXT_FLAG) <= 0) {
                        childrenTextViews.add(WeakReference(child))
                    }
                }
            }
        }
    }
}
