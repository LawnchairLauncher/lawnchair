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

import android.animation.AnimatorSet
import android.graphics.Canvas
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import com.android.launcher3.util.DisplayController
import com.android.launcher3.views.BaseDragLayer
import com.android.systemui.animation.ViewRootSync
import java.io.PrintWriter

private const val TASKBAR_ICONS_FADE_DURATION = 300L
private const val STASHED_HANDLE_FADE_DURATION = 180L
private const val TEMP_BACKGROUND_WINDOW_TITLE = "VoiceInteractionTaskbarBackground"

/**
 * Controls Taskbar behavior while Voice Interaction Window (assistant) is showing. Specifically:
 * - We always hide the taskbar icons or stashed handle, whichever is currently showing.
 * - For persistent taskbar, we also move the taskbar background to a new window/layer
 *   (TYPE_APPLICATION_OVERLAY) which is behind the assistant.
 * - For transient taskbar, we hide the real taskbar background (if it's showing).
 */
class VoiceInteractionWindowController(val context: TaskbarActivityContext) :
    TaskbarControllers.LoggableTaskbarController, TaskbarControllers.BackgroundRendererController {

    private val isSeparateBackgroundEnabled = !DisplayController.isTransientTaskbar(context)
    private val taskbarBackgroundRenderer = TaskbarBackgroundRenderer(context)
    private val nonTouchableInsetsComputer =
        ViewTreeObserver.OnComputeInternalInsetsListener {
            it.touchableRegion.setEmpty()
            it.setTouchableInsets(TOUCHABLE_INSETS_REGION)
        }

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers
    // Only initialized if isSeparateBackgroundEnabled
    private var separateWindowForTaskbarBackground: BaseDragLayer<TaskbarActivityContext>? = null
    private var separateWindowLayoutParams: WindowManager.LayoutParams? = null

    private var isVoiceInteractionWindowVisible: Boolean = false
    private var pendingAttachedToWindowListener: View.OnAttachStateChangeListener? = null

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers

        if (!isSeparateBackgroundEnabled) {
            return
        }

        separateWindowForTaskbarBackground =
            object : BaseDragLayer<TaskbarActivityContext>(context, null, 0) {
                override fun recreateControllers() {
                    mControllers = emptyArray()
                }

                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    if (controllers.taskbarStashController.isTaskbarVisibleAndNotStashing) {
                        taskbarBackgroundRenderer.draw(canvas)
                    }
                }

                override fun onAttachedToWindow() {
                    super.onAttachedToWindow()
                    viewTreeObserver.addOnComputeInternalInsetsListener(nonTouchableInsetsComputer)
                }

                override fun onDetachedFromWindow() {
                    super.onDetachedFromWindow()
                    viewTreeObserver.removeOnComputeInternalInsetsListener(
                        nonTouchableInsetsComputer
                    )
                }
            }
        separateWindowForTaskbarBackground?.recreateControllers()
        separateWindowForTaskbarBackground?.setWillNotDraw(false)

        separateWindowLayoutParams =
            context.createDefaultWindowLayoutParams(
                TYPE_APPLICATION_OVERLAY,
                TEMP_BACKGROUND_WINDOW_TITLE
            )
        separateWindowLayoutParams?.isSystemApplicationOverlay = true
    }

    fun onDestroy() {
        setIsVoiceInteractionWindowVisible(visible = false, skipAnim = true)
        separateWindowForTaskbarBackground?.removeOnAttachStateChangeListener(
            pendingAttachedToWindowListener
        )
    }

    fun setIsVoiceInteractionWindowVisible(visible: Boolean, skipAnim: Boolean) {
        if (isVoiceInteractionWindowVisible == visible) {
            return
        }
        isVoiceInteractionWindowVisible = visible

        // Fade out taskbar icons and stashed handle.
        val taskbarIconAlpha = if (isVoiceInteractionWindowVisible) 0f else 1f
        val fadeTaskbarIcons =
            controllers.taskbarViewController.taskbarIconAlpha
                .get(TaskbarViewController.ALPHA_INDEX_ASSISTANT_INVOKED)
                .animateToValue(taskbarIconAlpha)
                .setDuration(TASKBAR_ICONS_FADE_DURATION)
        val fadeStashedHandle =
            controllers.stashedHandleViewController.stashedHandleAlpha
                .get(StashedHandleViewController.ALPHA_INDEX_ASSISTANT_INVOKED)
                .animateToValue(taskbarIconAlpha)
                .setDuration(STASHED_HANDLE_FADE_DURATION)
        val animSet = AnimatorSet()
        animSet.play(fadeTaskbarIcons)
        animSet.play(fadeStashedHandle)
        if (!isSeparateBackgroundEnabled) {
            val fadeTaskbarBackground =
                controllers.taskbarDragLayerController.assistantBgTaskbar
                    .animateToValue(taskbarIconAlpha)
                    .setDuration(TASKBAR_ICONS_FADE_DURATION)
            animSet.play(fadeTaskbarBackground)
        }
        animSet.start()
        if (skipAnim) {
            animSet.end()
        }

        if (isSeparateBackgroundEnabled) {
            moveTaskbarBackgroundToAppropriateLayer(skipAnim)
        }
    }

    /**
     * Either:
     *
     * Hides the TaskbarDragLayer background and creates a new window to draw just that background.
     *
     * OR
     *
     * Removes the temporary window and show the TaskbarDragLayer background again.
     */
    private fun moveTaskbarBackgroundToAppropriateLayer(skipAnim: Boolean) {
        val moveToLowerLayer = isVoiceInteractionWindowVisible
        val onWindowsSynchronized =
            if (moveToLowerLayer) {
                // First add the temporary window, then hide the overlapping taskbar background.
                context.addWindowView(
                    separateWindowForTaskbarBackground,
                    separateWindowLayoutParams
                );
                { controllers.taskbarDragLayerController.setIsBackgroundDrawnElsewhere(true) }
            } else {
                // First reapply the original taskbar background, then remove the temporary window.
                controllers.taskbarDragLayerController.setIsBackgroundDrawnElsewhere(false);
                { context.removeWindowView(separateWindowForTaskbarBackground) }
            }

        if (skipAnim) {
            onWindowsSynchronized()
        } else {
            separateWindowForTaskbarBackground?.runWhenAttachedToWindow {
                ViewRootSync.synchronizeNextDraw(
                    separateWindowForTaskbarBackground!!,
                    context.dragLayer,
                    onWindowsSynchronized
                )
            }
        }
    }

    private fun View.runWhenAttachedToWindow(onAttachedToWindow: () -> Unit) {
        if (isAttachedToWindow) {
            onAttachedToWindow()
            return
        }
        removeOnAttachStateChangeListener(pendingAttachedToWindowListener)
        pendingAttachedToWindowListener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    onAttachedToWindow()
                    removeOnAttachStateChangeListener(this)
                    pendingAttachedToWindowListener = null
                }

                override fun onViewDetachedFromWindow(v: View) {}
            }
        addOnAttachStateChangeListener(pendingAttachedToWindowListener)
    }

    override fun setCornerRoundness(cornerRoundness: Float) {
        if (!isSeparateBackgroundEnabled) {
            return
        }
        taskbarBackgroundRenderer.setCornerRoundness(cornerRoundness)
        separateWindowForTaskbarBackground?.invalidate()
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "VoiceInteractionWindowController:")
        pw.println("$prefix\tisSeparateBackgroundEnabled=$isSeparateBackgroundEnabled")
        pw.println("$prefix\tisVoiceInteractionWindowVisible=$isVoiceInteractionWindowVisible")
        pw.println(
            "$prefix\tisSeparateTaskbarBackgroundAttachedToWindow=" +
                "${separateWindowForTaskbarBackground?.isAttachedToWindow}"
        )
    }
}
