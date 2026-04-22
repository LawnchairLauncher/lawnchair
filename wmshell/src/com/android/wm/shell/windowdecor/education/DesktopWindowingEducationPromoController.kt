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

package com.android.wm.shell.windowdecor.education

import android.annotation.ColorInt
import android.annotation.DimenRes
import android.annotation.LayoutRes
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayChangeController.OnDisplayChangingListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer

/**
 * Controls the lifecycle of an education promo, including showing and hiding it.
 */
class DesktopWindowingEducationPromoController(
    private val context: Context,
    private val additionalSystemViewContainerFactory: AdditionalSystemViewContainer.Factory,
    private val displayController: DisplayController,
) : OnDisplayChangingListener {
    private var educationView: View? = null
    private var animator: PhysicsAnimator<View>? = null
    private val springConfig by lazy {
        PhysicsAnimator.SpringConfig(
            SpringForce.STIFFNESS_MEDIUM,
            SpringForce.DAMPING_RATIO_LOW_BOUNCY
        )
    }
    private var popupWindow: AdditionalSystemViewContainer? = null

    override fun onDisplayChange(
        displayId: Int,
        fromRotation: Int,
        toRotation: Int,
        newDisplayAreaInfo: DisplayAreaInfo?,
        t: WindowContainerTransaction?
    ) {
        // Exit if the rotation hasn't changed or is changed by 180 degrees. [fromRotation] and
        // [toRotation] can be one of the [@Surface.Rotation] values.
        if ((fromRotation % 2 == toRotation % 2)) return
        hideEducation()
    }

    /**
     * Shows education promo.
     *
     * @param viewConfig features of the education.
     * @param taskId is used in the title of popup window created for the education view.
     */
    fun showEducation(
        viewConfig: EducationViewConfig,
        taskId: Int
    ) {
        hideEducation()
        educationView = createEducationView(viewConfig, taskId)
        animator = createAnimator()
        animateShowEducationTransition()
        displayController.addDisplayChangingController(this)
    }

    /** Hide the current education view if visible */
    private fun hideEducation() = animateHideEducationTransition { cleanUp() }

    /** Create education view by inflating layout provided. */
    private fun createEducationView(
        viewConfig: EducationViewConfig,
        taskId: Int
    ): View {
        val educationView =
            LayoutInflater.from(context)
                .inflate(
                    viewConfig.viewLayout, /* root= */ null, /* attachToRoot= */ false)
                .apply {
                    alpha = 0f
                    scaleX = 0f
                    scaleY = 0f

                    requireViewById<TextView>(R.id.education_text).apply {
                        text = viewConfig.educationText
                    }
                    setOnTouchListener { _, motionEvent ->
                        if (motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
                            hideEducation()
                            true
                        } else {
                            false
                        }
                    }
                    setOnClickListener {
                        hideEducation()
                    }
                    setEducationColorScheme(viewConfig.educationColorScheme)
                }

        createEducationPopupWindow(
            taskId,
            viewConfig.viewGlobalCoordinates,
            loadDimensionPixelSize(viewConfig.widthId),
            loadDimensionPixelSize(viewConfig.heightId),
            educationView = educationView)

        return educationView
    }

    /** Create animator for education transitions */
    private fun createAnimator(): PhysicsAnimator<View>? =
        educationView?.let {
            PhysicsAnimator.getInstance(it).apply { setDefaultSpringConfig(springConfig) }
        }

    /** Animate show transition for the education view */
    private fun animateShowEducationTransition() {
        animator
            ?.spring(DynamicAnimation.ALPHA, 1f)
            ?.spring(DynamicAnimation.SCALE_X, 1f)
            ?.spring(DynamicAnimation.SCALE_Y, 1f)
            ?.start()
    }

    /** Animate hide transition for the education view */
    private fun animateHideEducationTransition(endActions: () -> Unit) {
        animator
            ?.spring(DynamicAnimation.ALPHA, 0f)
            ?.spring(DynamicAnimation.SCALE_X, 0f)
            ?.spring(DynamicAnimation.SCALE_Y, 0f)
            ?.start()
        endActions()
    }

    /** Remove education promo and clean up all relative properties */
    private fun cleanUp() {
        educationView = null
        animator = null
        popupWindow?.releaseView()
        popupWindow = null
        displayController.removeDisplayChangingController(this)
    }

    private fun createEducationPopupWindow(
        taskId: Int,
        educationViewGlobalCoordinates: Point,
        width: Int,
        height: Int,
        educationView: View,
    ) {
        popupWindow =
            additionalSystemViewContainerFactory.create(
                windowManagerWrapper =
                WindowManagerWrapper(context.getSystemService(WindowManager::class.java)),
                taskId = taskId,
                x = educationViewGlobalCoordinates.x,
                y = educationViewGlobalCoordinates.y,
                width = width,
                height = height,
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                view = educationView)
    }

    private fun View.setEducationColorScheme(educationColorScheme: EducationColorScheme) {
        requireViewById<LinearLayout>(R.id.education_container).apply {
            background.setTint(educationColorScheme.container)
        }
        requireViewById<TextView>(R.id.education_text).apply {
            setTextColor(educationColorScheme.text)
        }
    }

    private fun loadDimensionPixelSize(@DimenRes resourceId: Int): Int {
        if (resourceId == Resources.ID_NULL) return 0
        return context.resources.getDimensionPixelSize(resourceId)
    }

    /**
     * The configuration for education view features:
     *
     * @property viewLayout Layout resource ID of the view to be used for education promo.
     * @property viewGlobalCoordinates Global (screen) coordinates of the education.
     * @property educationText Text to be added to the TextView of the promo.
     * @property widthId res Id for education width
     * @property heightId res Id for education height
     */
    data class EducationViewConfig(
        @LayoutRes val viewLayout: Int,
        val educationColorScheme: EducationColorScheme,
        val viewGlobalCoordinates: Point,
        val educationText: String,
        @DimenRes val widthId: Int,
        @DimenRes val heightId: Int
    )

    /**
     * Color scheme of education view:
     *
     * @property container Color of the container of the education.
     * @property text Text color of the [TextView] of education promo.
     */
    data class EducationColorScheme(
        @ColorInt val container: Int,
        @ColorInt val text: Int,
    )
}
