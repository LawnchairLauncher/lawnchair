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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Point
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_DOWN
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.ImageButton
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import com.android.internal.policy.SystemBarUtils
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_HANDLE_MENU_OPENED
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.windowdecor.AppHandleAnimator
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer

/**
 * A desktop mode window decoration used when the window is in full "focus" (i.e. fullscreen/split).
 * It hosts a simple handle bar from which to initiate a drag motion to enter desktop mode.
 */
class AppHandleViewHolder(
    appHandleView: View?,
    private val context: Context,
    onCaptionTouchListener: View.OnTouchListener,
    onCaptionButtonClickListener: OnClickListener,
    private val windowManagerWrapper: WindowManagerWrapper,
    private val handler: Handler,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
) : WindowDecorationViewHolder<AppHandleViewHolder.HandleData>() {

    data class HandleData(
        val taskInfo: RunningTaskInfo,
        val position: Point,
        val width: Int,
        val height: Int,
        val showInputLayer: Boolean,
        val isCaptionVisible: Boolean,
    ) : Data()

    private lateinit var taskInfo: RunningTaskInfo
    override val rootView =
        appHandleView ?: if (DesktopExperienceFlags.ENABLE_WINDOW_DECORATION_REFACTOR.isTrue) {
            LayoutInflater.from(context)
                .inflate(R.layout.desktop_mode_app_handle, null) as WindowDecorLinearLayout
        } else {
            error("App Handle root view should not be null")
        }
    private val captionView: View = rootView.requireViewById(R.id.desktop_mode_caption)
    private val captionHandle: ImageButton = rootView.requireViewById(R.id.caption_handle)
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val animator: AppHandleAnimator = AppHandleAnimator(rootView, captionHandle)
    private var statusBarInputLayerExists = false

    // An invisible View that takes up the same coordinates as captionHandle but is layered
    // above the status bar. The purpose of this View is to receive input intended for
    // captionHandle.
    private var statusBarInputLayer: AdditionalSystemViewContainer? = null

    init {
        captionView.setOnTouchListener(onCaptionTouchListener)
        captionHandle.setOnTouchListener(onCaptionTouchListener)
        captionHandle.setOnClickListener(onCaptionButtonClickListener)
        captionHandle.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                when (eventType) {
                    AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
                    AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> {
                        // Caption Handle itself can't get a11y focus because it's under the status
                        // bar, so pass through TYPE_VIEW_HOVER a11y events to the status bar
                        // input layer, so that it can get a11y focus on the caption handle's behalf
                        statusBarInputLayer?.view?.sendAccessibilityEvent(eventType)
                    }
                    else -> super.sendAccessibilityEvent(host, eventType)
                }
            }
        }
    }

    override fun bindData(data: HandleData) {
        bindData(
            data.taskInfo,
            data.position,
            data.width,
            data.height,
            data.showInputLayer,
            data.isCaptionVisible
        )
    }

    private fun bindData(
        taskInfo: RunningTaskInfo,
        position: Point,
        width: Int,
        height: Int,
        showInputLayer: Boolean,
        isCaptionVisible: Boolean
    ) {
        setVisibility(isCaptionVisible)
        captionHandle.imageTintList = ColorStateList.valueOf(getCaptionHandleBarColor(taskInfo))
        this.taskInfo = taskInfo
        // If handle is not in status bar region(i.e., bottom stage in vertical split),
        // do not create an input layer
        if (position.y >= SystemBarUtils.getStatusBarHeight(context) || !showInputLayer) {
            disposeStatusBarInputLayer()
            return
        }
        // Input layer view creation / modification takes a significant amount of time;
        // post them so we don't hold up DesktopModeWindowDecoration#relayout.
        if (statusBarInputLayerExists) {
            handler.post { updateStatusBarInputLayer(position) }
        } else {
            // Input layer is created on a delay; prevent multiple from being created.
            statusBarInputLayerExists = true
            handler.post { createStatusBarInputLayer(position, width, height) }
        }
    }

    override fun onHandleMenuOpened() {
        animator.animateCaptionHandleAlpha(startValue = 1f, endValue = 0f)
    }

    override fun onHandleMenuClosed() {
        animator.animateCaptionHandleAlpha(startValue = 0f, endValue = 1f)
    }

    private fun createStatusBarInputLayer(handlePosition: Point,
                                          handleWidth: Int,
                                          handleHeight: Int) {
        if (!DesktopModeFlags.ENABLE_HANDLE_INPUT_FIX.isTrue()) return
        statusBarInputLayer = AdditionalSystemViewContainer(context, windowManagerWrapper,
            taskInfo.taskId, handlePosition.x, handlePosition.y, handleWidth, handleHeight,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            ignoreCutouts = Flags.showAppHandleLargeScreens()
                    || BubbleAnythingFlagHelper.enableBubbleToFullscreen()
        )
        val view = statusBarInputLayer?.view ?: error("Unable to find statusBarInputLayer View")
        val lp = statusBarInputLayer?.lp ?: error("Unable to find statusBarInputLayer " +
                "LayoutParams")
        lp.title = "Handle Input Layer of task " + taskInfo.taskId
        lp.setTrustedOverlay()
        // Make this window a spy window to enable it to pilfer pointers from the system-wide
        // gesture listener that receives events before window. This is to prevent notification
        // shade gesture when we swipe down to enter desktop.
        lp.inputFeatures = WindowManager.LayoutParams.INPUT_FEATURE_SPY
        view.setOnHoverListener { _, event ->
            captionHandle.onHoverEvent(event)
        }
        // Caption handle is located within the status bar region, meaning the
        // DisplayPolicy will attempt to transfer this input to status bar if it's
        // a swipe down. Pilfer here to keep the gesture in handle alone.
        view.setOnTouchListener { v, event ->
            if (event.actionMasked == ACTION_DOWN) {
                inputManager.pilferPointers(v.viewRootImpl.inputToken)
            }
            captionHandle.dispatchTouchEvent(event)
            return@setOnTouchListener true
        }
        setupAppHandleA11y(view)
        windowManagerWrapper.updateViewLayout(view, lp)
    }

    private fun setupAppHandleA11y(view: View) {
        view.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                // Allow the status bar input layer to be a11y clickable so it can interact with
                // a11y services on behalf of caption handle (due to being under status bar)
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityAction.ACTION_CLICK)
                host.isClickable = true
            }

            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
            ): Boolean {
                // Passthrough the a11y click action so the caption handle, so that app handle menu
                // is opened on a11y click, similar to a real click
                if (action == AccessibilityAction.ACTION_CLICK.id) {
                    desktopModeUiEventLogger.log(taskInfo, A11Y_APP_HANDLE_MENU_OPENED)
                    captionHandle.performClick()
                }
                return super.performAccessibilityAction(host, action, args)
            }

            override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {
                super.onPopulateAccessibilityEvent(host, event)
                // When the status bar input layer is focused, use the content description of the
                // caption handle so that it appears as "App handle" and not "Unlabelled view"
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    event.text.add(captionHandle.contentDescription)
                }
            }
        }

        // Update a11y action text so that Talkback announces "Press double tap to open menu"
        // while focused on status bar input layer
        ViewCompat.replaceAccessibilityAction(
            view,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_handle_chip_accessibility_announce),
            null
        )
    }

    private fun updateStatusBarInputLayer(globalPosition: Point) {
        statusBarInputLayer?.setPosition(
            SurfaceControl.Transaction(),
            globalPosition.x.toFloat(),
            globalPosition.y.toFloat()
        ) ?: return
    }

    /**
     * Remove the input layer from [WindowManager]. Should be used when caption handle
     * is not visible.
     */
    fun disposeStatusBarInputLayer() {
        if (!statusBarInputLayerExists) return
        statusBarInputLayerExists = false
        statusBarInputLayer?.view?.setOnTouchListener(null)
        handler.post {
            statusBarInputLayer?.releaseView()
            statusBarInputLayer = null
        }
    }

    private fun setVisibility(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        if (
            captionView.visibility == v ||
                !DesktopModeFlags.ENABLE_DESKTOP_APP_HANDLE_ANIMATION.isTrue()
        ) {
            return
        }
        // TODO(b/405251465): animate app handle visibility change after creation and animation are
        //  moved to a background thread.
        captionView.visibility = v
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
                    taskInfo.windowingMode == WINDOWING_MODE_FREEFORM
                ) {
                    Color.valueOf(taskDescription.statusBarColor).luminance() < 0.5
                } else {
                    taskDescription.systemBarsAppearance and APPEARANCE_LIGHT_STATUS_BARS == 0
                }
            } ?: false
    }

    override fun setTaskFocusState(taskFocusState: Boolean) {
        (rootView as WindowDecorLinearLayout).setTaskFocusState(taskFocusState)
    }

    override fun close() {
        animator.cancel()
    }

    /** Factory class for creating [AppHandleViewHolder] objects. */
    class Factory {
        /**
         * Create a [AppHandleViewHolder] object to handle caption view and status bar
         * input layer logic.
         */
        fun create(
            rootView: View?,
            context: Context,
            onCaptionTouchListener: View.OnTouchListener,
            onCaptionButtonClickListener: OnClickListener,
            windowManagerWrapper: WindowManagerWrapper,
            handler: Handler,
            desktopModeUiEventLogger: DesktopModeUiEventLogger,
        ): AppHandleViewHolder = AppHandleViewHolder(
            rootView,
            context,
            onCaptionTouchListener,
            onCaptionButtonClickListener,
            windowManagerWrapper,
            handler,
            desktopModeUiEventLogger,
        )
    }
}
