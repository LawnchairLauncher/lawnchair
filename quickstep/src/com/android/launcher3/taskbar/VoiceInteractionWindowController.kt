package com.android.launcher3.taskbar

import android.graphics.Canvas
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import com.android.launcher3.views.BaseDragLayer
import com.android.systemui.animation.ViewRootSync
import java.io.PrintWriter

private const val TASKBAR_ICONS_FADE_DURATION = 300L
private const val STASHED_HANDLE_FADE_DURATION = 180L

/**
 * Controls Taskbar behavior while Voice Interaction Window (assistant) is showing.
 */
class VoiceInteractionWindowController(val context: TaskbarActivityContext)
    : TaskbarControllers.LoggableTaskbarController,
        TaskbarControllers.BackgroundRendererController {

    private val taskbarBackgroundRenderer = TaskbarBackgroundRenderer(context)

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers
    private lateinit var separateWindowForTaskbarBackground: BaseDragLayer<TaskbarActivityContext>
    private lateinit var separateWindowLayoutParams: WindowManager.LayoutParams

    private var isVoiceInteractionWindowVisible: Boolean = false

    fun init(controllers: TaskbarControllers) {
        this.controllers = controllers

        separateWindowForTaskbarBackground =
            object : BaseDragLayer<TaskbarActivityContext>(context, null, 0) {
                override fun recreateControllers() {
                    mControllers = emptyArray()
                }

                override fun draw(canvas: Canvas) {
                    super.draw(canvas)
                    if (this@VoiceInteractionWindowController.context.isGestureNav
                        && controllers.taskbarStashController.isInAppAndNotStashed) {
                        taskbarBackgroundRenderer.draw(canvas)
                    }
                }
            }
        separateWindowForTaskbarBackground.recreateControllers()
        separateWindowForTaskbarBackground.setWillNotDraw(false)

        separateWindowLayoutParams = context.createDefaultWindowLayoutParams(
            TYPE_APPLICATION_OVERLAY)
        separateWindowLayoutParams.isSystemApplicationOverlay = true
    }

    fun onDestroy() {
        setIsVoiceInteractionWindowVisible(visible = false, skipAnim = true)
    }

    fun setIsVoiceInteractionWindowVisible(visible: Boolean, skipAnim: Boolean) {
        if (isVoiceInteractionWindowVisible == visible) {
            return
        }
        isVoiceInteractionWindowVisible = visible

        // Fade out taskbar icons and stashed handle.
        val taskbarIconAlpha = if (isVoiceInteractionWindowVisible) 0f else 1f
        val fadeTaskbarIcons = controllers.taskbarViewController.taskbarIconAlpha
            .get(TaskbarViewController.ALPHA_INDEX_ASSISTANT_INVOKED)
            .animateToValue(taskbarIconAlpha)
            .setDuration(TASKBAR_ICONS_FADE_DURATION)
        val fadeStashedHandle = controllers.stashedHandleViewController.stashedHandleAlpha
            .get(StashedHandleViewController.ALPHA_INDEX_ASSISTANT_INVOKED)
            .animateToValue(taskbarIconAlpha)
            .setDuration(STASHED_HANDLE_FADE_DURATION)
        fadeTaskbarIcons.start()
        fadeStashedHandle.start()
        if (skipAnim) {
            fadeTaskbarIcons.end()
            fadeStashedHandle.end()
        }

        moveTaskbarBackgroundToAppropriateLayer(skipAnim)
    }

    /**
     * Either:
     * Hides the TaskbarDragLayer background and creates a new window to draw just that background.
     * OR
     * Removes the temporary window and show the TaskbarDragLayer background again.
     */
    private fun moveTaskbarBackgroundToAppropriateLayer(skipAnim: Boolean) {
        val taskbarBackgroundOverride = controllers.taskbarDragLayerController
            .overrideBackgroundAlpha
        val moveToLowerLayer = isVoiceInteractionWindowVisible
        val onWindowsSynchronized = if (moveToLowerLayer) {
            // First add the temporary window, then hide the overlapping taskbar background.
            context.addWindowView(separateWindowForTaskbarBackground, separateWindowLayoutParams);
            { taskbarBackgroundOverride.updateValue(0f) }
        } else {
            // First reapply the original taskbar background, then remove the temporary window.
            taskbarBackgroundOverride.updateValue(1f);
            { context.removeWindowView(separateWindowForTaskbarBackground) }
        }

        if (skipAnim) {
            onWindowsSynchronized()
        } else {
            ViewRootSync.synchronizeNextDraw(
                separateWindowForTaskbarBackground,
                context.dragLayer,
                onWindowsSynchronized
            )
        }
    }

    override fun setCornerRoundness(cornerRoundness: Float) {
        taskbarBackgroundRenderer.setCornerRoundness(cornerRoundness)
        separateWindowForTaskbarBackground.invalidate()
    }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println(prefix + "VoiceInteractionWindowController:")
        pw.println("$prefix\tisVoiceInteractionWindowVisible=$isVoiceInteractionWindowVisible")
    }
}