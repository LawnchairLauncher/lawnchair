/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.view.View
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.ViewGroup
import com.android.systemui.shared.animation.UnfoldConstantTranslateAnimator.ViewIdToTranslate
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.lang.ref.WeakReference

/**
 * Translates items away/towards the hinge when the device is opened/closed, according to the
 * direction specified in [ViewIdToTranslate.direction], for a maximum of [translationMax] when
 * progresses are 0.
 */
class UnfoldConstantTranslateAnimator(
    private val viewsIdToTranslate: Set<ViewIdToTranslate>,
    private val progressProvider: UnfoldTransitionProgressProvider
) : TransitionProgressListener {

    private var viewsToTranslate = listOf<ViewToTranslate>()
    private lateinit var rootView: ViewGroup
    private var translationMax = 0f

    fun init(rootView: ViewGroup, translationMax: Float) {
        this.rootView = rootView
        this.translationMax = translationMax
        progressProvider.addCallback(this)
    }

    override fun onTransitionStarted() {
        registerViewsForAnimation(rootView, viewsIdToTranslate)
    }

    override fun onTransitionProgress(progress: Float) {
        translateViews(progress)
    }

    override fun onTransitionFinished() {
        translateViews(progress = 1f)
    }

    private fun translateViews(progress: Float) {
        // progress == 0 -> -translationMax
        // progress == 1 -> 0
        val xTrans = (progress - 1f) * translationMax
        val rtlMultiplier =
            if (rootView.getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
                -1
            } else {
                1
            }
        viewsToTranslate.forEach { (view, direction) ->
            view.get()?.translationX = xTrans * direction.multiplier * rtlMultiplier
        }
    }

    /** Finds in [parent] all views specified by [ids] and register them for the animation. */
    private fun registerViewsForAnimation(parent: ViewGroup, ids: Set<ViewIdToTranslate>) {
        viewsToTranslate =
            ids.asSequence()
                .filter { it.shouldBeAnimated() }
                .mapNotNull {
                    parent.findViewById<View>(it.viewId)?.let { view ->
                        ViewToTranslate(WeakReference(view), it.direction)
                    }
                }
                .toList()
    }

    /**
     * Represents a view to animate. [rootView] should contain a view with [viewId] inside.
     * [shouldBeAnimated] is only evaluated when the viewsToTranslate is registered in
     * [registerViewsForAnimation].
     */
    data class ViewIdToTranslate(
        val viewId: Int,
        val direction: Direction,
        val shouldBeAnimated: () -> Boolean = { true }
    )

    /**
     * Represents a view whose animation process is in-progress. It should be immutable because the
     * started animation should be completed.
     */
    private data class ViewToTranslate(val view: WeakReference<View>, val direction: Direction)

    /** Direction of the animation. */
    enum class Direction(val multiplier: Float) {
        START(-1f),
        END(1f),
    }
}
