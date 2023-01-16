/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Point
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.android.systemui.unfold.UnfoldTransitionProgressProvider
import java.lang.ref.WeakReference

/**
 * Creates an animation where all registered views are moved into their final location
 * by moving from the center of the screen to the sides
 */
class UnfoldMoveFromCenterAnimator @JvmOverloads constructor(
    private val windowManager: WindowManager,
    /**
     * Allows to set custom translation applier
     * Could be useful when a view could be translated from
     * several sources and we want to set the translation
     * using custom methods instead of [View.setTranslationX] or
     * [View.setTranslationY]
     */
    private val translationApplier: TranslationApplier = object : TranslationApplier {},
    /**
     * Allows to set custom implementation for getting
     * view location. Could be useful if logical view bounds
     * are different than actual bounds (e.g. view container may
     * have larger width than width of the items in the container)
     */
    private val viewCenterProvider: ViewCenterProvider = object : ViewCenterProvider {}
) : UnfoldTransitionProgressProvider.TransitionProgressListener {

    private val screenSize = Point()
    private var isVerticalFold = false

    private val animatedViews: MutableList<AnimatedView> = arrayListOf()

    private var lastAnimationProgress: Float = 0f

    /**
     * Updates display properties in order to calculate the initial position for the views
     * Must be called before [registerViewForAnimation]
     */
    fun updateDisplayProperties() {
        windowManager.defaultDisplay.getSize(screenSize)

        // Simple implementation to get current fold orientation,
        // this might not be correct on all devices
        // TODO: use JetPack WindowManager library to get the fold orientation
        isVerticalFold = windowManager.defaultDisplay.rotation == Surface.ROTATION_0 ||
            windowManager.defaultDisplay.rotation == Surface.ROTATION_180
    }

    /**
     * If target view positions have changed (e.g. because of layout changes) call this method
     * to re-query view positions and update the translations
     */
    fun updateViewPositions() {
        animatedViews.forEach { animatedView ->
            animatedView.view.get()?.let {
                animatedView.updateAnimatedView(it)
            }
        }
        onTransitionProgress(lastAnimationProgress)
    }

    /**
     * Registers a view to be animated, the view should be measured and layouted
     * After finishing the animation it is necessary to clear
     * the views using [clearRegisteredViews]
     */
    fun registerViewForAnimation(view: View) {
        val animatedView = createAnimatedView(view)
        animatedViews.add(animatedView)
    }

    /**
     * Unregisters all registered views and resets their translation
     */
    fun clearRegisteredViews() {
        onTransitionProgress(1f)
        animatedViews.clear()
    }

    override fun onTransitionProgress(progress: Float) {
        animatedViews.forEach {
            it.view.get()?.let { view ->
                translationApplier.apply(
                    view = view,
                    x = it.startTranslationX * (1 - progress),
                    y = it.startTranslationY * (1 - progress)
                )
            }
        }
        lastAnimationProgress = progress
    }

    private fun createAnimatedView(view: View): AnimatedView =
        AnimatedView(view = WeakReference(view)).updateAnimatedView(view)

    private fun AnimatedView.updateAnimatedView(view: View): AnimatedView {
        val viewCenter = Point()
        viewCenterProvider.getViewCenter(view, viewCenter)

        val viewCenterX = viewCenter.x
        val viewCenterY = viewCenter.y

        if (isVerticalFold) {
            val distanceFromScreenCenterToViewCenter = screenSize.x / 2 - viewCenterX
            startTranslationX = distanceFromScreenCenterToViewCenter * TRANSLATION_PERCENTAGE
            startTranslationY = 0f
        } else {
            val distanceFromScreenCenterToViewCenter = screenSize.y / 2 - viewCenterY
            startTranslationX = 0f
            startTranslationY = distanceFromScreenCenterToViewCenter * TRANSLATION_PERCENTAGE
        }

        return this
    }

    /**
     * Interface that allows to use custom logic to apply translation to view
     */
    interface TranslationApplier {
        /**
         * Called when we need to apply [x] and [y] translation to [view]
         */
        fun apply(view: View, x: Float, y: Float) {
            view.translationX = x
            view.translationY = y
        }
    }

    /**
     * Interface that allows to use custom logic to get the center of the view
     */
    interface ViewCenterProvider {
        /**
         * Called when we need to get the center of the view
         */
        fun getViewCenter(view: View, outPoint: Point) {
            val viewLocation = IntArray(2)
            view.getLocationOnScreen(viewLocation)

            val viewX = viewLocation[0]
            val viewY = viewLocation[1]

            outPoint.x = viewX + view.width / 2
            outPoint.y = viewY + view.height / 2
        }
    }

    private class AnimatedView(
        val view: WeakReference<View>,
        var startTranslationX: Float = 0f,
        var startTranslationY: Float = 0f
    )
}

private const val TRANSLATION_PERCENTAGE = 0.3f
