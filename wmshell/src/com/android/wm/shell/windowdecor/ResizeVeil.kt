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
package com.android.wm.shell.windowdecor

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.os.Trace
import android.view.Choreographer
import android.view.Display
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL
import android.view.WindowlessWindowManager
import android.widget.ImageView
import android.window.TaskConstants
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayController.OnDisplaysChangedListener
import com.android.wm.shell.shared.annotations.ShellBackgroundThread
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.windowdecor.WindowDecoration.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.Theme
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Creates and updates a veil that covers task contents on resize.
 */
public class ResizeVeil @JvmOverloads constructor(
        private val context: Context,
        private val displayController: DisplayController,
        private val taskResourceLoader: WindowDecorTaskResourceLoader,
        @ShellMainThread private val mainDispatcher: CoroutineDispatcher,
        @ShellBackgroundThread private val bgScope: CoroutineScope,
        private var parentSurface: SurfaceControl,
        private val surfaceControlTransactionSupplier: Supplier<SurfaceControl.Transaction>,
        private val surfaceControlBuilderFactory: SurfaceControlBuilderFactory =
                object : SurfaceControlBuilderFactory {},
        private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
                object : SurfaceControlViewHostFactory {},
        taskInfo: RunningTaskInfo,
) {
    private val decorThemeUtil = DecorThemeUtil(context)
    private val lightColors = dynamicLightColorScheme(context)
    private val darkColors = dynamicDarkColorScheme(context)

    @VisibleForTesting
    lateinit var iconView: ImageView
    private var iconSize = 0

    /** A container surface to host the veil background and icon child surfaces.  */
    private var veilSurface: SurfaceControl? = null
    /** A color surface for the veil background.  */
    private var backgroundSurface: SurfaceControl? = null
    /** A surface that hosts a windowless window with the app icon.  */
    private var iconSurface: SurfaceControl? = null
    private var viewHost: SurfaceControlViewHost? = null
    private var display: Display? = null
    private var veilAnimator: ValueAnimator? = null
    private var iconAnimator: ValueAnimator? = null
    private var loadAppInfoJob: Job? = null

    /**
     * Whether the resize veil is currently visible.
     *
     * Note: when animating a [ResizeVeil.hideVeil], the veil is considered visible as soon
     * as the animation starts.
     */
    private var isVisible = false

    private val onDisplaysChangedListener: OnDisplaysChangedListener =
            object : OnDisplaysChangedListener {
                override fun onDisplayAdded(displayId: Int) {
                    if (taskInfo.displayId != displayId) {
                        return
                    }
                    displayController.removeDisplayWindowListener(this)
                    setupResizeVeil(taskInfo)
                }
            }

    /**
     * Whether the resize veil is ready to be shown.
     */
    private val isReady: Boolean
        get() = viewHost != null

    init {
        setupResizeVeil(taskInfo)
    }

    /**
     * Create the veil in its default invisible state.
     */
    private fun setupResizeVeil(taskInfo: RunningTaskInfo) {
        if (!obtainDisplayOrRegisterListener(taskInfo.displayId)) {
            // Display may not be available yet, skip this until then.
            return
        }
        Trace.beginSection("ResizeVeil#setupResizeVeil")
        veilSurface = surfaceControlBuilderFactory
                .create("Resize veil of Task=" + taskInfo.taskId)
                .setContainerLayer()
                .setHidden(true)
                .setParent(parentSurface)
                .setCallsite("ResizeVeil#setupResizeVeil")
                .build()
        backgroundSurface = surfaceControlBuilderFactory
                .create("Resize veil background of Task=" + taskInfo.taskId)
                .setColorLayer()
                .setHidden(true)
                .setParent(veilSurface)
                .setCallsite("ResizeVeil#setupResizeVeil")
                .build()
        iconSurface = surfaceControlBuilderFactory
                .create("Resize veil icon of Task=" + taskInfo.taskId)
                .setContainerLayer()
                .setHidden(true)
                .setParent(veilSurface)
                .setCallsite("ResizeVeil#setupResizeVeil")
                .build()
        iconSize = context.resources
                .getDimensionPixelSize(R.dimen.desktop_mode_resize_veil_icon_size)
        val root = LayoutInflater.from(context)
                .inflate(R.layout.desktop_mode_resize_veil, null /* root */)
        iconView = root.requireViewById(R.id.veil_application_icon)
        val lp = WindowManager.LayoutParams(
                iconSize,
                iconSize,
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT)
        lp.title = "Resize veil icon window of Task=" + taskInfo.taskId
        lp.inputFeatures = INPUT_FEATURE_NO_INPUT_CHANNEL
        lp.setTrustedOverlay()
        val wwm = WindowlessWindowManager(taskInfo.configuration,
                iconSurface, null /* hostInputToken */)
        viewHost = surfaceControlViewHostFactory.create(context, display, wwm, "ResizeVeil")
        viewHost?.setView(root, lp)
        loadAppInfoJob = bgScope.launch {
            if (!isActive) return@launch
            val icon = taskResourceLoader.getVeilIcon(taskInfo)
            withContext(mainDispatcher) {
                if (!isActive) return@withContext
                iconView.setImageBitmap(icon)
            }
        }
        Trace.endSection()
    }

    private fun obtainDisplayOrRegisterListener(displayId: Int): Boolean {
        display = displayController.getDisplay(displayId)
        if (display == null) {
            displayController.addDisplayWindowListener(onDisplaysChangedListener)
            return false
        }
        return true
    }

    /**
     * Shows the veil surface/view.
     *
     * @param t the transaction to apply in sync with the veil draw
     * @param parent the surface that the veil should be a child of
     * @param taskBounds the bounds of the task that owns the veil
     * @param fadeIn if true, the veil will fade-in with an animation, if false, it will be shown
     * immediately
     */
    fun showVeil(
            t: SurfaceControl.Transaction,
            parent: SurfaceControl,
            taskBounds: Rect,
            taskInfo: RunningTaskInfo,
            fadeIn: Boolean,
    ) {
        if (!isReady || isVisible) {
            t.apply()
            return
        }
        val background = backgroundSurface
        val icon = iconSurface
        if (background == null || icon == null) return
        updateTransactionWithShowVeil(
            t,
            parent,
            taskBounds,
            taskInfo,
            fadeIn,
        )
        if (fadeIn) {
            cancelAnimation()
            val veilAnimT = surfaceControlTransactionSupplier.get()
            val iconAnimT = surfaceControlTransactionSupplier.get()
            veilAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = VEIL_ENTRY_ALPHA_ANIMATION_DURATION
                addUpdateListener {
                    veilAnimT.setAlpha(background, animatedValue as Float)
                            .apply()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        veilAnimT.show(background)
                                .setAlpha(background, 0f)
                                .apply()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        veilAnimT.setAlpha(background, 1f).apply()
                    }
                })
            }
            iconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ICON_ALPHA_ANIMATION_DURATION
                startDelay = ICON_ENTRY_DELAY
                addUpdateListener {
                    iconAnimT.setAlpha(icon, animatedValue as Float)
                            .apply()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        iconAnimT.show(icon)
                                .setAlpha(icon, 0f)
                                .apply()
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        iconAnimT.setAlpha(icon, 1f).apply()
                    }
                })
            }

            // Let the animators show it with the correct alpha value once the animation starts.
            t.hide(icon)
                    .hide(background)
                    .apply()
            veilAnimator?.start()
            iconAnimator?.start()
        } else {
            // Show the veil immediately.
            t.apply()
        }
    }

    fun updateTransactionWithShowVeil(
        t: SurfaceControl.Transaction,
        parent: SurfaceControl,
        taskBounds: Rect,
        taskInfo: RunningTaskInfo,
        fadeIn: Boolean = false,
    ) {
        if (!isReady || isVisible) return
        isVisible = true
        val background = backgroundSurface
        val icon = iconSurface
        val veil = veilSurface
        if (background == null || icon == null || veil == null) return
        // Parent surface can change, ensure it is up to date.
        if (parent != parentSurface) {
            t.reparent(veil, parent)
            parentSurface = parent
        }
        val backgroundColor = when (decorThemeUtil.getAppTheme(taskInfo)) {
            Theme.LIGHT -> lightColors.surfaceContainer
            Theme.DARK -> darkColors.surfaceContainer
        }
        t.show(veil)
            .setLayer(veil, VEIL_CONTAINER_LAYER)
            .setLayer(icon, VEIL_ICON_LAYER)
            .setLayer(background, VEIL_BACKGROUND_LAYER)
            .setColor(background, Color.valueOf(backgroundColor.toArgb()).components)
        relayout(taskBounds, t)
        if (!fadeIn) {
            t.show(icon)
                .show(background)
                .setAlpha(icon, 1f)
                .setAlpha(background, 1f)
        }
    }

    /**
     * Animate veil's alpha to 1, fading it in.
     */
    fun showVeil(parentSurface: SurfaceControl, taskBounds: Rect, taskInfo: RunningTaskInfo) {
        if (!isReady || isVisible) {
            return
        }
        val t = surfaceControlTransactionSupplier.get()
        showVeil(t, parentSurface, taskBounds, taskInfo, true /* fadeIn */)
    }

    /**
     * Update veil bounds to match bounds changes.
     * @param newBounds bounds to update veil to.
     */
    private fun relayout(newBounds: Rect, t: SurfaceControl.Transaction) {
        val iconPosition = calculateAppIconPosition(newBounds)
        val veil = veilSurface
        val icon = iconSurface
        if (veil == null || icon == null) return
        t.setWindowCrop(veil, newBounds.width(), newBounds.height())
                .setPosition(icon, iconPosition.x, iconPosition.y)
                .setPosition(parentSurface, newBounds.left.toFloat(), newBounds.top.toFloat())
                .setWindowCrop(parentSurface, newBounds.width(), newBounds.height())
                .setFrameTimeline(Choreographer.getInstance().vsyncId)
    }

    /**
     * Calls relayout to update task and veil bounds.
     * @param newBounds bounds to update veil to.
     */
    fun updateResizeVeil(newBounds: Rect) {
        if (!isVisible) {
            return
        }
        val t = surfaceControlTransactionSupplier.get()
        updateResizeVeil(t, newBounds)
    }

    /**
     * Calls relayout to update task and veil bounds.
     * Finishes veil fade in if animation is currently running; this is to prevent empty space
     * being visible behind the transparent veil during a fast resize.
     *
     * @param t a transaction to be applied in sync with the veil draw.
     * @param newBounds bounds to update veil to.
     */
    fun updateResizeVeil(t: SurfaceControl.Transaction, newBounds: Rect) {
        updateTransactionWithResizeVeil(t, newBounds)
        t.apply()
    }

    fun updateTransactionWithResizeVeil(t: SurfaceControl.Transaction, newBounds: Rect) {
        if (!isVisible) {
            return
        }
        veilAnimator?.let { animator ->
            if (animator.isStarted) {
                animator.removeAllUpdateListeners()
                animator.end()
            }
        }
        relayout(newBounds, t)
    }

    /**
     * Animate veil's alpha to 0, fading it out.
     */
    fun hideVeil() {
        if (!isVisible) {
            return
        }
        cancelAnimation()
        val background = backgroundSurface
        val icon = iconSurface
        if (background == null || icon == null) return

        veilAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = VEIL_EXIT_ALPHA_ANIMATION_DURATION
            startDelay = VEIL_EXIT_DELAY
            addUpdateListener {
                surfaceControlTransactionSupplier.get()
                        .setAlpha(background, animatedValue as Float)
                        .apply()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    surfaceControlTransactionSupplier.get()
                            .hide(background)
                            .apply()
                }
            })
        }
        iconAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = ICON_ALPHA_ANIMATION_DURATION
            addUpdateListener {
                surfaceControlTransactionSupplier.get()
                    .setAlpha(icon, animatedValue as Float)
                    .apply()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    surfaceControlTransactionSupplier.get()
                        .hide(icon)
                        .apply()
                }
            })
        }
        veilAnimator?.start()
        iconAnimator?.start()
        isVisible = false
    }

    private fun calculateAppIconPosition(parentBounds: Rect): PointF {
        return PointF(parentBounds.width().toFloat() / 2 - iconSize.toFloat() / 2,
                parentBounds.height().toFloat() / 2 - iconSize.toFloat() / 2)
    }

    private fun cancelAnimation() {
        veilAnimator?.removeAllUpdateListeners()
        veilAnimator?.cancel()
        veilAnimator = null
        iconAnimator?.removeAllUpdateListeners()
        iconAnimator?.cancel()
        iconAnimator = null
    }

    /**
     * Dispose of veil when it is no longer needed, likely on close of its container decor.
     */
    fun dispose() {
        cancelAnimation()
        isVisible = false
        loadAppInfoJob?.cancel()

        viewHost?.release()
        viewHost = null

        val t: SurfaceControl.Transaction = surfaceControlTransactionSupplier.get()
        backgroundSurface?.let { background -> t.remove(background) }
        backgroundSurface = null
        iconSurface?.let { icon -> t.remove(icon) }
        iconSurface = null
        veilSurface?.let { veil -> t.remove(veil) }
        veilSurface = null
        t.apply()
        displayController.removeDisplayWindowListener(onDisplaysChangedListener)
    }

    interface SurfaceControlBuilderFactory {
        fun create(name: String): SurfaceControl.Builder {
            return SurfaceControl.Builder().setName(name)
        }
    }

    companion object {
        private const val TAG = "ResizeVeil"
        private const val ICON_ALPHA_ANIMATION_DURATION = 50L
        private const val VEIL_ENTRY_ALPHA_ANIMATION_DURATION = 50L
        private const val VEIL_EXIT_ALPHA_ANIMATION_DURATION = 200L
        private const val ICON_ENTRY_DELAY = 33L
        private const val VEIL_EXIT_DELAY = 33L
        private const val VEIL_CONTAINER_LAYER = TaskConstants.TASK_CHILD_LAYER_RESIZE_VEIL

        /** The background is a child of the veil container layer and goes at the bottom.  */
        private const val VEIL_BACKGROUND_LAYER = 0

        /** The icon is a child of the veil container layer and goes in front of the background.  */
        private const val VEIL_ICON_LAYER = 1
    }
}
