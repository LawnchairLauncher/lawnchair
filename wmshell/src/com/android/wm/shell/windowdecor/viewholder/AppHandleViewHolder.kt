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
package com.android.wm.shell.windowdecor.viewholder

import android.animation.ObjectAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.widget.ImageButton
import com.android.wm.shell.R
import com.android.wm.shell.animation.Interpolators

/**
 * A desktop mode window decoration used when the window is in full "focus" (i.e. fullscreen/split).
 * It hosts a simple handle bar from which to initiate a drag motion to enter desktop mode.
 */
internal class AppHandleViewHolder(
        rootView: View,
        onCaptionTouchListener: View.OnTouchListener,
        onCaptionButtonClickListener: View.OnClickListener
) : WindowDecorationViewHolder(rootView) {

    companion object {
        private const val CAPTION_HANDLE_ANIMATION_DURATION: Long = 100
    }

    private val captionView: View = rootView.requireViewById(R.id.desktop_mode_caption)
    private val captionHandle: ImageButton = rootView.requireViewById(R.id.caption_handle)

    init {
        captionView.setOnTouchListener(onCaptionTouchListener)
        captionHandle.setOnTouchListener(onCaptionTouchListener)
        captionHandle.setOnClickListener(onCaptionButtonClickListener)
    }

    override fun bindData(taskInfo: RunningTaskInfo) {
        captionHandle.imageTintList = ColorStateList.valueOf(getCaptionHandleBarColor(taskInfo))
    }

    override fun onHandleMenuOpened() {
        animateCaptionHandleAlpha(startValue = 1f, endValue = 0f)
    }

    override fun onHandleMenuClosed() {
        animateCaptionHandleAlpha(startValue = 0f, endValue = 1f)
    }

    private fun getCaptionHandleBarColor(taskInfo: RunningTaskInfo): Int {
        return if (shouldUseLightCaptionColors(taskInfo)) {
            context.getColor(R.color.desktop_mode_caption_handle_bar_light)
        } else {
            context.getColor(R.color.desktop_mode_caption_handle_bar_dark)
        }
    }

    /**
     * Whether the caption items should use the 'light' color variant so that there's good contrast
     * with the caption background color.
     */
    private fun shouldUseLightCaptionColors(taskInfo: RunningTaskInfo): Boolean {
        return taskInfo.taskDescription
            ?.let { taskDescription ->
                if (Color.alpha(taskDescription.statusBarColor) != 0 &&
                    taskInfo.windowingMode == WINDOWING_MODE_FREEFORM) {
                    Color.valueOf(taskDescription.statusBarColor).luminance() < 0.5
                } else {
                    taskDescription.systemBarsAppearance and APPEARANCE_LIGHT_STATUS_BARS == 0
                }
            } ?: false
    }

    /** Animate appearance/disappearance of caption handle as the handle menu is animated. */
    private fun animateCaptionHandleAlpha(startValue: Float, endValue: Float) {
        val animator =
            ObjectAnimator.ofFloat(captionHandle, View.ALPHA, startValue, endValue).apply {
                duration = CAPTION_HANDLE_ANIMATION_DURATION
                interpolator = Interpolators.FAST_OUT_SLOW_IN
            }
        animator.start()
    }
}
