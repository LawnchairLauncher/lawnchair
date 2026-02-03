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
package com.android.launcher3.taskbar.growth

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_GROWTH_NUDGE_OPEN
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.LottieAnimationColorUtils
import java.io.PrintWriter

/**
 * Controls nudge lifecycles.
 *
 * TODO: b/413718172 - Refactor to reduce code duplication with [TaskbarEduTooltipController].
 */
class NudgeController(context: Context) : LoggableTaskbarController {

    protected val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)

    private val isNudgeEnabled: Boolean
        get() {
            return !Utilities.isRunningInTestHarness() &&
                !activityContext.isPhoneMode &&
                !activityContext.isTinyTaskbar
        }

    val isNudgeOpen: Boolean
        get() = nudgeView?.isOpen == true

    private lateinit var controllers: TaskbarControllers

    private var nudgeView: NudgeView? = null

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
    }

    fun maybeShow(model: NudgePayload) {
        if (!isNudgeEnabled || !activityContext.isTransientTaskbar) {
            return
        }

        inflateNudgeContent(R.layout.growth_nudge)
        nudgeView?.run {
            allowTouchDismissal = false

            fun updateButton(button: Button, buttonPayload: ButtonPayload?) {
                if (buttonPayload != null) {
                    button.apply {
                        text = buttonPayload.label
                        setOnClickListener {
                            ActionPerformers.performActions(
                                /*actions=*/ buttonPayload.actions,
                                /*context=*/ activityContext,
                                /*dismissCallback=*/ ::hide,
                            )
                        }
                    }
                } else {
                    button.visibility = GONE
                }
            }

            fun updateImage(image: Image?) {
                val imageView = requireViewById<ImageView>(R.id.image_view)
                when (image) {
                    is Image.ResourceId -> {
                        imageView.setImageDrawable(context.getDrawable(image.resId))
                    }
                    null -> imageView.visibility = GONE
                }
            }

            fun updateContent() {
                // Update content.
                val title = requireViewById<TextView>(R.id.title)
                title.text = model.titleText
                val body = requireViewById<TextView>(R.id.body)
                body.text = model.bodyText
                updateButton(requireViewById(R.id.primary_button), model.primaryButton)
                updateButton(requireViewById(R.id.secondary_button), model.secondaryButton)
                updateImage(model.image)
            }

            fun updateLayout() {
                content.updateLayoutParams { width = MATCH_PARENT }
                val sideSpacing =
                    resources.getDimensionPixelSize(R.dimen.nudge_default_position_side_spacing)
                updateLayoutParams<MarginLayoutParams> {
                    if (Utilities.isRtl(context.getResources())) {
                        rightMargin = sideSpacing
                    } else {
                        leftMargin = sideSpacing
                    }
                    width = resources.getDimensionPixelSize(R.dimen.nudge_width)
                }
            }

            updateContent()
            updateLayout()
            show()
        }
    }

    /** Closes the current [nudgeView]. */
    fun hide() {
        nudgeView?.close(true)
    }

    /** Initializes [nudgeView] with content from [contentResId]. */
    private fun inflateNudgeContent(@LayoutRes contentResId: Int) {
        val overlayContext = controllers.taskbarOverlayController.requestWindow()
        val nudgeView =
            overlayContext.layoutInflater.inflate(
                R.layout.taskbar_nudge_container,
                overlayContext.dragLayer,
                false,
            ) as NudgeView

        controllers.taskbarAutohideSuspendController.updateFlag(
            FLAG_AUTOHIDE_SUSPEND_GROWTH_NUDGE_OPEN,
            true,
        )

        nudgeView.onCloseCallback = {
            this.nudgeView = null
            controllers.taskbarAutohideSuspendController.updateFlag(
                FLAG_AUTOHIDE_SUSPEND_GROWTH_NUDGE_OPEN,
                false,
            )
            controllers.taskbarStashController.updateAndAnimateTransientTaskbar(true)
        }
        nudgeView.accessibilityDelegate = createAccessibilityDelegate()

        overlayContext.layoutInflater.inflate(contentResId, nudgeView.content, true)
        this.nudgeView = nudgeView
    }

    private fun createAccessibilityDelegate() =
        object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?,
            ): Boolean {
                if (action == R.id.close) {
                    hide()
                    return true
                }
                return super.performAccessibilityAction(host, action, args)
            }

            override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {
                super.onPopulateAccessibilityEvent(host, event)
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    event.text.add(host.context?.getText(R.string.nudge_a11y_title))
                }
            }

            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo,
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        R.id.close,
                        host.context?.getText(R.string.nudge_a11y_close),
                    )
                )
            }
        }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println(prefix + "NudgeController:")
        pw?.println("$prefix\tisNudgeEnabled=$isNudgeEnabled")
        pw?.println("$prefix\tisOpen=$isNudgeOpen")
    }
}

/**
 * Maps colors in the dark-themed Lottie assets to their light-themed equivalents.
 *
 * For instance, `".blue100" to R.color.lottie_blue400` means objects that are material blue100 in
 * dark theme should be changed to material blue400 in light theme.
 */
private val DARK_TO_LIGHT_COLORS =
    mapOf(
        ".blue100" to R.color.lottie_blue400,
        ".blue400" to R.color.lottie_blue600,
        ".green100" to R.color.lottie_green400,
        ".green400" to R.color.lottie_green600,
        ".grey300" to R.color.lottie_grey600,
        ".grey400" to R.color.lottie_grey700,
        ".grey800" to R.color.lottie_grey200,
        ".red400" to R.color.lottie_red600,
        ".yellow100" to R.color.lottie_yellow400,
        ".yellow400" to R.color.lottie_yellow600,
    )

private fun LottieAnimationView.supportLightTheme() {
    if (Utilities.isDarkTheme(context)) {
        return
    }

    LottieAnimationColorUtils.updateToColorResources(this, DARK_TO_LIGHT_COLORS, context.theme)
}
