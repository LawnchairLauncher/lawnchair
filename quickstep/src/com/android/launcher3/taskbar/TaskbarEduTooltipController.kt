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
package com.android.launcher3.taskbar

import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntDef
import androidx.annotation.LayoutRes
import com.android.launcher3.R
import com.android.launcher3.Utilities.IS_RUNNING_IN_TEST_HARNESS
import com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_EDU_TOOLTIP
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_EDU_OPEN
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP
import java.io.PrintWriter

/** First EDU step for swiping up to show transient Taskbar. */
const val TOOLTIP_STEP_SWIPE = 0
/** Second EDU step for explaining Taskbar functionality when unstashed. */
const val TOOLTIP_STEP_FEATURES = 1
/**
 * EDU is completed.
 *
 * This value should match the maximum count for [TASKBAR_EDU_TOOLTIP_STEP].
 */
const val TOOLTIP_STEP_NONE = 2

/** Current step in the tooltip EDU flow. */
@Retention(AnnotationRetention.SOURCE)
@IntDef(TOOLTIP_STEP_SWIPE, TOOLTIP_STEP_FEATURES, TOOLTIP_STEP_NONE)
annotation class TaskbarEduTooltipStep

/** Controls stepping through the Taskbar tooltip EDU. */
class TaskbarEduTooltipController(val activityContext: TaskbarActivityContext) :
    LoggableTaskbarController {

    private val isTooltipEnabled = !IS_RUNNING_IN_TEST_HARNESS && ENABLE_TASKBAR_EDU_TOOLTIP.get()
    private val isOpen: Boolean
        get() = tooltip?.isOpen ?: false

    private lateinit var controllers: TaskbarControllers

    @TaskbarEduTooltipStep
    var tooltipStep: Int
        get() {
            return activityContext.onboardingPrefs?.getCount(TASKBAR_EDU_TOOLTIP_STEP)
                ?: TOOLTIP_STEP_NONE
        }
        private set(step) {
            activityContext.onboardingPrefs?.setEventCount(step, TASKBAR_EDU_TOOLTIP_STEP)
        }

    private var tooltip: TaskbarEduTooltip? = null

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
    }

    /** Shows swipe EDU tooltip if it is the current [tooltipStep]. */
    fun maybeShowSwipeEdu() {
        if (
            !isTooltipEnabled ||
                !DisplayController.isTransientTaskbar(activityContext) ||
                tooltipStep > TOOLTIP_STEP_SWIPE
        ) {
            return
        }

        tooltipStep = TOOLTIP_STEP_FEATURES
        inflateTooltip(R.layout.taskbar_edu_swipe)
        tooltip?.show()
    }

    /**
     * Shows feature EDU tooltip if this step has not been seen.
     *
     * If [TOOLTIP_STEP_SWIPE] has not been seen at this point, the first step is skipped because a
     * swipe up is necessary to show this step.
     */
    fun maybeShowFeaturesEdu() {
        if (!isTooltipEnabled || tooltipStep > TOOLTIP_STEP_FEATURES) {
            return
        }

        tooltipStep = TOOLTIP_STEP_NONE
        inflateTooltip(R.layout.taskbar_edu_features)
        tooltip?.apply {
            findViewById<View>(R.id.done_button)?.setOnClickListener { hide() }
            if (DisplayController.isTransientTaskbar(activityContext)) {
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin +=
                    activityContext.deviceProfile.taskbarSize
            }
            show()
        }
    }

    /** Closes the current [tooltip]. */
    fun hide() = tooltip?.close(true)

    /** Initializes [tooltip] with content from [contentResId]. */
    private fun inflateTooltip(@LayoutRes contentResId: Int) {
        val overlayContext = controllers.taskbarOverlayController.requestWindow()
        val tooltip =
            overlayContext.layoutInflater.inflate(
                R.layout.taskbar_edu_tooltip,
                overlayContext.dragLayer,
                false
            ) as TaskbarEduTooltip

        controllers.taskbarAutohideSuspendController.updateFlag(
            FLAG_AUTOHIDE_SUSPEND_EDU_OPEN,
            true
        )
        tooltip.onCloseCallback = {
            this.tooltip = null
            controllers.taskbarAutohideSuspendController.updateFlag(
                FLAG_AUTOHIDE_SUSPEND_EDU_OPEN,
                false
            )
            controllers.taskbarStashController.updateAndAnimateTransientTaskbar(true)
        }

        overlayContext.layoutInflater.inflate(contentResId, tooltip.content, true)
        this.tooltip = tooltip
    }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println("$(prefix)TaskbarEduController:")
        pw?.println("$prefix\tisTooltipEnabled=$isTooltipEnabled")
        pw?.println("$prefix\tisOpen=$isOpen")
        pw?.println("$prefix\ttooltipStep=$tooltipStep")
    }
}
