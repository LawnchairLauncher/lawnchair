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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.Gravity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.MarginLayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.annotation.IntDef
import androidx.annotation.LayoutRes
import androidx.core.text.HtmlCompat
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_EDU_OPEN
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.DisplayController
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_EDU_TOOLTIP_STEP
import com.android.launcher3.util.OnboardingPrefs.TASKBAR_SEARCH_EDU_SEEN
import com.android.launcher3.util.ResourceBasedOverride
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.util.LottieAnimationColorUtils
import java.io.PrintWriter

/** First EDU step for swiping up to show transient Taskbar. */
const val TOOLTIP_STEP_SWIPE = 0
/** Second EDU step for explaining Taskbar functionality when unstashed. */
const val TOOLTIP_STEP_FEATURES = 1
/** Third EDU step for explaining Taskbar pinning. */
const val TOOLTIP_STEP_PINNING = 2

/**
 * EDU is completed.
 *
 * This value should match the maximum count for [TASKBAR_EDU_TOOLTIP_STEP].
 */
const val TOOLTIP_STEP_NONE = 3
/** The base URL for the Privacy Policy that will later be localized. */
private const val PRIVACY_POLICY_BASE_URL = "https://policies.google.com/privacy/embedded?hl="
/** The base URL for the Terms of Service that will later be localized. */
private const val TOS_BASE_URL = "https://policies.google.com/terms?hl="

/** Current step in the tooltip EDU flow. */
@Retention(AnnotationRetention.SOURCE)
@IntDef(TOOLTIP_STEP_SWIPE, TOOLTIP_STEP_FEATURES, TOOLTIP_STEP_PINNING, TOOLTIP_STEP_NONE)
annotation class TaskbarEduTooltipStep

/** Controls stepping through the Taskbar tooltip EDU. */
open class TaskbarEduTooltipController(context: Context) :
    ResourceBasedOverride, LoggableTaskbarController {

    protected val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)
    open val shouldShowSearchEdu = false
    private val isTooltipEnabled: Boolean
        get() {
            return !Utilities.isRunningInTestHarness() &&
                !activityContext.isPhoneMode &&
                !activityContext.isTinyTaskbar
        }

    private val isOpen: Boolean
        get() = tooltip?.isOpen ?: false

    val isBeforeTooltipFeaturesStep: Boolean
        get() = isTooltipEnabled && tooltipStep <= TOOLTIP_STEP_FEATURES

    private lateinit var controllers: TaskbarControllers

    // Keep track of whether the user has seen the Search Edu
    private var userHasSeenSearchEdu: Boolean
        get() {
            return TASKBAR_SEARCH_EDU_SEEN.get(activityContext)
        }
        private set(seen) {
            LauncherPrefs.get(activityContext).put(TASKBAR_SEARCH_EDU_SEEN, seen)
        }

    @TaskbarEduTooltipStep
    var tooltipStep: Int
        get() {
            return TASKBAR_EDU_TOOLTIP_STEP.get(activityContext)
        }
        private set(step) {
            TASKBAR_EDU_TOOLTIP_STEP.set(step, activityContext)
        }

    private var tooltip: TaskbarEduTooltip? = null

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers
        // We want to show the Search Edu right after pinning the taskbar, so we post it here
        activityContext.dragLayer.post { maybeShowSearchEdu() }
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
        tooltip?.run {
            requireViewById<LottieAnimationView>(R.id.swipe_animation).supportLightTheme()
            show()
        }
    }

    /**
     * Shows feature EDU tooltip if this step has not been seen.
     *
     * If [TOOLTIP_STEP_SWIPE] has not been seen at this point, the first step is skipped because a
     * swipe up is necessary to show this step.
     */
    fun maybeShowFeaturesEdu() {
        if (!isTooltipEnabled || tooltipStep > TOOLTIP_STEP_FEATURES) {
            maybeShowPinningEdu()
            maybeShowSearchEdu()
            return
        }

        tooltipStep = TOOLTIP_STEP_NONE
        inflateTooltip(R.layout.taskbar_edu_features)
        tooltip?.run {
            allowTouchDismissal = false
            val splitscreenAnim = requireViewById<LottieAnimationView>(R.id.splitscreen_animation)
            val suggestionsAnim = requireViewById<LottieAnimationView>(R.id.suggestions_animation)
            val pinningAnim = requireViewById<LottieAnimationView>(R.id.pinning_animation)
            val pinningEdu = requireViewById<View>(R.id.pinning_edu)
            splitscreenAnim.supportLightTheme()
            suggestionsAnim.supportLightTheme()
            pinningAnim.supportLightTheme()
            if (DisplayController.isTransientTaskbar(activityContext)) {
                splitscreenAnim.setAnimation(R.raw.taskbar_edu_splitscreen_transient)
                suggestionsAnim.setAnimation(R.raw.taskbar_edu_suggestions_transient)
                pinningEdu.visibility = if (enableTaskbarPinning()) VISIBLE else GONE
            } else {
                splitscreenAnim.setAnimation(R.raw.taskbar_edu_splitscreen_persistent)
                suggestionsAnim.setAnimation(R.raw.taskbar_edu_suggestions_persistent)
                pinningEdu.visibility = GONE
            }

            // Set up layout parameters.
            content.updateLayoutParams { width = MATCH_PARENT }
            updateLayoutParams<MarginLayoutParams> {
                if (DisplayController.isTransientTaskbar(activityContext)) {
                    width =
                        resources.getDimensionPixelSize(
                            if (enableTaskbarPinning())
                                R.dimen.taskbar_edu_features_tooltip_width_with_three_features
                            else R.dimen.taskbar_edu_features_tooltip_width_with_two_features
                        )

                    bottomMargin += activityContext.deviceProfile.taskbarHeight
                } else {
                    width =
                        resources.getDimensionPixelSize(
                            R.dimen.taskbar_edu_features_tooltip_width_with_two_features
                        )
                }
            }

            findViewById<View>(R.id.done_button)?.setOnClickListener { hide() }
            show()
        }
    }

    /**
     * Shows standalone Pinning EDU tooltip if this EDU has not been seen.
     *
     * We show this standalone edu if users have seen the previous version of taskbar education,
     * which did not include the pinning feature.
     */
    private fun maybeShowPinningEdu() {
        // use old value of tooltipStep that was set to the previous value of TOOLTIP_STEP_NONE (2
        // for the original 2 edu steps) as a proxy to needing to show the separate pinning edu
        if (
            !enableTaskbarPinning() ||
                !DisplayController.isTransientTaskbar(activityContext) ||
                !isTooltipEnabled ||
                tooltipStep > TOOLTIP_STEP_PINNING ||
                tooltipStep < TOOLTIP_STEP_FEATURES
        ) {
            return
        }
        tooltipStep = TOOLTIP_STEP_NONE
        inflateTooltip(R.layout.taskbar_edu_pinning)

        tooltip?.run {
            allowTouchDismissal = true
            requireViewById<LottieAnimationView>(R.id.standalone_pinning_animation)
                .supportLightTheme()

            updateLayoutParams<BaseDragLayer.LayoutParams> {
                if (DisplayController.isTransientTaskbar(activityContext)) {
                    bottomMargin += activityContext.deviceProfile.taskbarHeight
                }
                // Unlike other tooltips, we want to align with taskbar divider rather than center.
                gravity = Gravity.BOTTOM
                marginStart = 0
                width =
                    resources.getDimensionPixelSize(
                        R.dimen.taskbar_edu_features_tooltip_width_with_one_feature
                    )
            }

            // Calculate the amount the tooltip must be shifted by to align with the taskbar divider
            val taskbarDividerView = controllers.taskbarViewController.taskbarDividerView
            val dividerLocation = taskbarDividerView.x + taskbarDividerView.width / 2
            x = dividerLocation - layoutParams.width / 2

            show()
        }
    }

    /**
     * Shows standalone Search EDU tooltip if this EDU has not been seen.
     *
     * We show this standalone edu for users to learn to how to trigger Search from the pinned
     * taskbar
     */
    fun maybeShowSearchEdu() {
        if (
            !enableTaskbarPinning() ||
                !DisplayController.isPinnedTaskbar(activityContext) ||
                !isTooltipEnabled ||
                !shouldShowSearchEdu ||
                userHasSeenSearchEdu
        ) {
            return
        }
        userHasSeenSearchEdu = true
        inflateTooltip(R.layout.taskbar_edu_search)
        tooltip?.run {
            allowTouchDismissal = true
            requireViewById<LottieAnimationView>(R.id.search_edu_animation).supportLightTheme()
            val eduSubtitle: TextView = requireViewById(R.id.search_edu_text)
            showDisclosureText(eduSubtitle)
            updateLayoutParams<BaseDragLayer.LayoutParams> {
                if (DisplayController.isTransientTaskbar(activityContext)) {
                    bottomMargin += activityContext.deviceProfile.taskbarHeight
                }
                // Unlike other tooltips, we want to align with the all apps button rather than
                // center.
                gravity = Gravity.BOTTOM
                marginStart = 0
                width =
                    resources.getDimensionPixelSize(
                        R.dimen.taskbar_edu_features_tooltip_width_with_one_feature
                    )
            }

            // Calculate the amount the tooltip must be shifted by to align with the action key
            val allAppsButtonView = controllers.taskbarViewController.allAppsButtonView
            if (allAppsButtonView != null) {
                val allAppsIconLocation = allAppsButtonView.x + allAppsButtonView.width / 2
                x = allAppsIconLocation - layoutParams.width / 2
            }

            show()
        }
    }

    /**
     * Set up the provided TextView to display legal disclosures. The method takes locale into
     * account to show the appropriate links to regional disclosures.
     */
    private fun TaskbarEduTooltip.showDisclosureText(
        textView: TextView,
        stringId: Int = R.string.taskbar_edu_search_disclosure,
    ) {
        val locale = resources.configuration.locales[0]
        val text =
            SpannableString(
                HtmlCompat.fromHtml(
                    resources.getString(
                        stringId,
                        PRIVACY_POLICY_BASE_URL + locale.language,
                        TOS_BASE_URL + locale.language,
                    ),
                    HtmlCompat.FROM_HTML_MODE_COMPACT,
                )
            )
        // Directly process URLSpan clicks
        text.getSpans(0, text.length, URLSpan::class.java).forEach { urlSpan ->
            val url: URLSpan =
                object : URLSpan(urlSpan.url) {
                    override fun onClick(widget: View) {
                        val uri = Uri.parse(urlSpan.url)
                        val context = widget.context
                        val intent =
                            Intent(Intent.ACTION_VIEW, uri).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }

            val spanStart = text.getSpanStart(urlSpan)
            val spanEnd = text.getSpanEnd(urlSpan)
            val spanFlags = text.getSpanFlags(urlSpan)
            text.removeSpan(urlSpan)
            text.setSpan(url, spanStart, spanEnd, spanFlags)
        }
        textView.text = text
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    /** Closes the current [tooltip]. */
    fun hide() {
        tooltip?.close(true)
    }

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
        tooltip.accessibilityDelegate = createAccessibilityDelegate()

        overlayContext.layoutInflater.inflate(contentResId, tooltip.content, true)
        this.tooltip = tooltip
    }

    private fun createAccessibilityDelegate() =
        object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
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
                    event.text.add(host.context?.getText(R.string.taskbar_edu_a11y_title))
                }
            }

            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        R.id.close,
                        host.context?.getText(R.string.taskbar_edu_close)
                    )
                )
            }
        }

    override fun dumpLogs(prefix: String?, pw: PrintWriter?) {
        pw?.println(prefix + "TaskbarEduTooltipController:")
        pw?.println("$prefix\tisTooltipEnabled=$isTooltipEnabled")
        pw?.println("$prefix\tisOpen=$isOpen")
        pw?.println("$prefix\ttooltipStep=$tooltipStep")
    }

    companion object {
        @JvmStatic
        fun newInstance(context: Context): TaskbarEduTooltipController {
            return ResourceBasedOverride.Overrides.getObject(
                TaskbarEduTooltipController::class.java,
                context,
                R.string.taskbar_edu_tooltip_controller_class
            )
        }
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
