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

package com.android.systemui.util

import android.app.Dialog
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import com.android.systemui.animation.back.BackAnimationSpec
import com.android.systemui.animation.back.BackTransformation
import com.android.systemui.animation.back.applyTo
import com.android.systemui.animation.back.floatingSystemSurfacesForSysUi
import com.android.systemui.animation.back.onBackAnimationCallbackFrom
import com.android.systemui.animation.back.registerOnBackInvokedCallbackOnViewAttached
import com.android.systemui.animation.view.LaunchableFrameLayout

/**
 * Register on the Dialog's [OnBackInvokedDispatcher] an animation using the [BackAnimationSpec].
 * The [BackTransformation] will be applied on the [targetView].
 */
@JvmOverloads
fun Dialog.registerAnimationOnBackInvoked(
    targetView: View,
    backAnimationSpec: BackAnimationSpec =
        BackAnimationSpec.floatingSystemSurfacesForSysUi(
            displayMetricsProvider = { targetView.resources.displayMetrics },
        ),
) {
    targetView.registerOnBackInvokedCallbackOnViewAttached(
        onBackInvokedDispatcher = onBackInvokedDispatcher,
        onBackAnimationCallback =
            onBackAnimationCallbackFrom(
                backAnimationSpec = backAnimationSpec,
                displayMetrics = targetView.resources.displayMetrics,
                onBackProgressed = { backTransformation -> backTransformation.applyTo(targetView) },
                onBackInvoked = { dismiss() },
            ),
    )
}

/**
 * Make the dialog window (and therefore its DecorView) fullscreen to make it possible to animate
 * outside its bounds. No-op if the dialog is already fullscreen.
 *
 * <p>Returns null if the dialog is already fullscreen. Otherwise, returns a pair containing a view
 * and a layout listener. The new view matches the original dialog DecorView in size, position, and
 * background. This new view will be a child of the modified, transparent, fullscreen DecorView. The
 * layout listener is listening to changes to the modified DecorView. It is the responsibility of
 * the caller to deregister the listener when the dialog is dismissed.
 */
fun Dialog.maybeForceFullscreen(): Pair<LaunchableFrameLayout, View.OnLayoutChangeListener>? {
    // Create the dialog so that its onCreate() method is called, which usually sets the dialog
    // content.
    create()

    val window = window!!
    val decorView = window.decorView as ViewGroup

    val isWindowFullscreen =
        window.attributes.width == MATCH_PARENT && window.attributes.height == MATCH_PARENT
    if (isWindowFullscreen) {
        return null
    }

    // We will make the dialog window (and therefore its DecorView) fullscreen to make it possible
    // to animate outside its bounds.
    //
    // Before that, we add a new View as a child of the DecorView with the same size and gravity as
    // that DecorView, then we add all original children of the DecorView to that new View. Finally
    // we remove the background of the DecorView and add it to the new View, then we make the
    // DecorView fullscreen. This new View now acts as a fake (non fullscreen) window.
    //
    // On top of that, we also add a fullscreen transparent background between the DecorView and the
    // view that we added so that we can dismiss the dialog when this view is clicked. This is
    // necessary because DecorView overrides onTouchEvent and therefore we can't set the click
    // listener directly on the (now fullscreen) DecorView.
    val fullscreenTransparentBackground = FrameLayout(context)
    decorView.addView(
        fullscreenTransparentBackground,
        0 /* index */,
        FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    )

    val dialogContentWithBackground = LaunchableFrameLayout(context)
    dialogContentWithBackground.background = decorView.background

    // Make the window background transparent. Note that setting the window (or DecorView)
    // background drawable to null leads to issues with background color (not being transparent) or
    // with insets that are not refreshed. Therefore we need to set it to something not null, hence
    // we are using android.R.color.transparent here.
    window.setBackgroundDrawableResource(android.R.color.transparent)

    // Close the dialog when clicking outside of it.
    fullscreenTransparentBackground.setOnClickListener { dismiss() }
    dialogContentWithBackground.isClickable = true

    // Make sure the transparent and dialog backgrounds are not focusable by accessibility
    // features.
    fullscreenTransparentBackground.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    dialogContentWithBackground.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO

    fullscreenTransparentBackground.addView(
        dialogContentWithBackground,
        FrameLayout.LayoutParams(
            window.attributes.width,
            window.attributes.height,
            window.attributes.gravity
        )
    )

    // Move all original children of the DecorView to the new View we just added.
    for (i in 1 until decorView.childCount) {
        val view = decorView.getChildAt(1)
        decorView.removeViewAt(1)
        dialogContentWithBackground.addView(view)
    }

    // Make the window fullscreen and add a layout listener to ensure it stays fullscreen.
    window.setLayout(MATCH_PARENT, MATCH_PARENT)
    val decorViewLayoutListener =
        View.OnLayoutChangeListener {
            v,
            left,
            top,
            right,
            bottom,
            oldLeft,
            oldTop,
            oldRight,
            oldBottom ->
            if (
                window.attributes.width != MATCH_PARENT || window.attributes.height != MATCH_PARENT
            ) {
                // The dialog size changed, copy its size to dialogContentWithBackground and make
                // the dialog window full screen again.
                val layoutParams = dialogContentWithBackground.layoutParams
                layoutParams.width = window.attributes.width
                layoutParams.height = window.attributes.height
                dialogContentWithBackground.layoutParams = layoutParams
                window.setLayout(MATCH_PARENT, MATCH_PARENT)
            }
        }
    decorView.addOnLayoutChangeListener(decorViewLayoutListener)

    return dialogContentWithBackground to decorViewLayoutListener
}
