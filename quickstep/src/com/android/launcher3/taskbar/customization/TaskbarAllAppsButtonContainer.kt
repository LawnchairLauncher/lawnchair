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

package com.android.launcher3.taskbar.customization

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color.TRANSPARENT
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.view.setPadding
import com.android.launcher3.R
import com.android.launcher3.Utilities.dpToPx
import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarViewCallbacks
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.IconButtonView
import com.android.quickstep.DeviceConfigWrapper
import com.android.quickstep.util.ContextualSearchStateManager
import com.android.wm.shell.Flags

/** Taskbar all apps button container for customizable taskbar. */
class TaskbarAllAppsButtonContainer
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    IconButtonView(context, attrs), TaskbarContainer {

    private val activityContext: TaskbarActivityContext = ActivityContext.lookupContext(context)
    private var allAppsTouchTriggered = false
    private var allAppsTouchRunnable: Runnable? = null
    private var allAppsButtonTouchDelayMs: Long = ViewConfiguration.getLongPressTimeout().toLong()
    private var isTaskbarInMinimalState = false
    private lateinit var taskbarViewCallbacks: TaskbarViewCallbacks

    override val spaceNeeded: Int
        get() {
            return dpToPx(
                activityContext.taskbarSpecsEvaluator.taskbarIconSize.size.toFloat(),
                activityContext,
            )
        }

    init {
        contentDescription = context.getString(R.string.all_apps_button_label)
        setUpIcon()
    }

    @SuppressLint("UseCompatLoadingForDrawables", "ResourceAsColor")
    private fun setUpIcon() {
        val drawable =
            resources.getDrawable(
                getAllAppsButton(activityContext.taskbarFeatureEvaluator.isTransient)
            )
        backgroundTintList = ColorStateList.valueOf(TRANSPARENT)
        setIconDrawable(drawable)
        if (!activityContext.isTransientTaskbar) {
            setPadding(
                dpToPx(
                    (activityContext.taskbarSpecsEvaluator.taskbarIconPadding).toFloat(),
                    activityContext,
                )
            )
        }
        setForegroundTint(activityContext.getColor(R.color.all_apps_button_color))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setUpCallbacks(callbacks: TaskbarViewCallbacks) {
        taskbarViewCallbacks = callbacks
        setOnClickListener(this::onAllAppsButtonClick)
        setOnLongClickListener(this::onAllAppsButtonLongClick)
        setOnTouchListener(this::onAllAppsButtonTouch)
        isHapticFeedbackEnabled =
            taskbarViewCallbacks.isAllAppsButtonHapticFeedbackEnabled(mContext)
        allAppsTouchRunnable = Runnable {
            taskbarViewCallbacks.triggerAllAppsButtonLongClick()
            allAppsTouchTriggered = true
        }
        val contextualSearchStateManager = ContextualSearchStateManager.INSTANCE[mContext]
        if (
            DeviceConfigWrapper.get().customLpaaThresholds &&
                contextualSearchStateManager.lpnhDurationMillis.isPresent
        ) {
            allAppsButtonTouchDelayMs = contextualSearchStateManager.lpnhDurationMillis.get()
        }
    }

    @DrawableRes
    private fun getAllAppsButton(isTransientTaskbar: Boolean): Int {
        if (Flags.enableGsf()) {
            return getAllAppsButtonForExpressiveTheme()
        }
        val shouldSelectTransientIcon =
            isTransientTaskbar ||
                (enableTaskbarPinning() &&
                    activityContext.taskbarFeatureEvaluator.supportsTransitionToTransientTaskbar)
        return if (shouldSelectTransientIcon) R.drawable.ic_transient_taskbar_all_apps_search_button
        else R.drawable.ic_taskbar_all_apps_search_button
    }

    @DrawableRes
    private fun getAllAppsButtonForExpressiveTheme(): Int {
        return if (isTaskbarInMinimalState) {
            R.drawable.ic_taskbar_minimal_state_all_apps_search_button_expressive_theme
        } else {
            R.drawable.ic_taskbar_all_apps_search_button_expressive_theme
        }
    }

    @DimenRes
    fun getAllAppsButtonTranslationXOffset(isTransientTaskbar: Boolean): Int {
        if (Flags.enableGsf()) {
            return R.dimen.taskbar_all_apps_search_button_translation_x_offset_for_expressive_theme
        }
        return if (isTransientTaskbar) {
            R.dimen.transient_taskbar_all_apps_button_translation_x_offset
        } else {
            R.dimen.taskbar_all_apps_search_button_translation_x_offset
        }
    }

    /** Taskbar minimal state is that taskbar does not host anything other than all apps button. */
    fun updateTaskbarMinimalState(isInMinimalState: Boolean) {
        if (isTaskbarInMinimalState != isInMinimalState) {
            isTaskbarInMinimalState = isInMinimalState
            setUpIcon()
        }
    }

    private fun onAllAppsButtonTouch(view: View, ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                allAppsTouchTriggered = false
                MAIN_EXECUTOR.handler.postDelayed(allAppsTouchRunnable!!, allAppsButtonTouchDelayMs)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> cancelAllAppsButtonTouch()
        }
        return false
    }

    private fun cancelAllAppsButtonTouch() {
        MAIN_EXECUTOR.handler.removeCallbacks(allAppsTouchRunnable!!)
        // ACTION_UP is first triggered, then click listener / long-click listener is triggered on
        // the next frame, so we need to post twice and delay the reset.
        this.post { this.post { allAppsTouchTriggered = false } }
    }

    private fun onAllAppsButtonClick(view: View) {
        if (!allAppsTouchTriggered) {
            taskbarViewCallbacks.triggerAllAppsButtonClick(view)
        }
    }

    // Handle long click from Switch Access and Voice Access
    private fun onAllAppsButtonLongClick(view: View): Boolean {
        if (!MAIN_EXECUTOR.handler.hasCallbacks(allAppsTouchRunnable!!) && !allAppsTouchTriggered) {
            taskbarViewCallbacks.triggerAllAppsButtonLongClick()
        }
        return true
    }
}
