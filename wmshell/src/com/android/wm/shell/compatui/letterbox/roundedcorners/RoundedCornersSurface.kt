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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Binder
import android.view.Gravity
import android.view.IWindow
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import android.view.WindowlessWindowManager
import android.widget.FrameLayout
import android.window.TaskConstants
import com.android.wm.shell.R
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.suppliers.SurfaceBuilderSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxConfiguration
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersDrawableFactory.Position.BOTTOM_LEFT
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersDrawableFactory.Position.BOTTOM_RIGHT
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersDrawableFactory.Position.TOP_LEFT
import com.android.wm.shell.compatui.letterbox.roundedcorners.RoundedCornersDrawableFactory.Position.TOP_RIGHT

/** Overlay responsible for handling letterbox rounded corners in Shell. */
class RoundedCornersSurface(
    private val context: Context,
    config: Configuration?,
    private val syncQueue: SyncTransactionQueue,
    private val parentSurface: SurfaceControl,
    private val cornersFactory: LetterboxRoundedCornersDrawableFactory,
    private val letterboxConfiguration: LetterboxConfiguration,
    private val surfaceBuilderSupplier: SurfaceBuilderSupplier,
) : WindowlessWindowManager(config, parentSurface, null) {

    private var viewHost: SurfaceControlViewHost? = null

    private var leftTopRoundedCorner: View? = null
    private var rightTopRoundedCorner: View? = null
    private var leftBottomRoundedCorner: View? = null
    private var rightBottomRoundedCorner: View? = null

    private val letterboxBounds = Rect()

    /**
     * A surface leash to position the layout relative to the task, since we can't set position for
     * the `mViewHost` directly.
     */
    protected var leash: SurfaceControl? = null

    private var roundedCornersLayout: View

    init {
        viewHost = SurfaceControlViewHost(context, context.display, this, javaClass.simpleName)
        roundedCornersLayout = inflateLayout()
        viewHost?.setView(roundedCornersLayout, getRoundedCornersViewWindowLayoutParams())
    }

    override fun getParentSurface(
        window: IWindow,
        attrs: WindowManager.LayoutParams,
    ): SurfaceControl {
        val className = javaClass.simpleName
        val builder =
            surfaceBuilderSupplier
                .get()
                .setContainerLayer()
                .setName("LetterboxRoundedCornersParentSurface")
                .setHidden(false)
                .setParent(parentSurface)
                .setCallsite("$className#attachToParentSurface")
        return builder.build().apply {
            initSurface(this)
            leash = this
        }
    }

    /**
     * Updates the position of the surface with respect to the given `positionX` and `positionY`.
     */
    fun updateSurfaceBounds(bounds: Rect) {
        if (leash == null) {
            return
        }
        letterboxBounds.set(bounds)
        syncQueue.runInSync { t: SurfaceControl.Transaction ->
            // If leash is null or invalid, return. Otherwise, assign it to 'validLeash'.
            val validLeash = leash?.takeIf { it.isValid } ?: return@runInSync
            t.setPosition(validLeash, bounds.left.toFloat(), bounds.top.toFloat())
                .setWindowCrop(validLeash, bounds.width(), bounds.height())
        }
        viewHost?.relayout(getRoundedCornersViewWindowLayoutParams())
    }

    /** Updates the position of the surface with respect to the given positions [x] and [y]. */
    fun updatePosition(t: SurfaceControl.Transaction, x: Int, y: Int) {
        // Chain of checks:
        // 1. Is 'leash' not null?
        // 2. If so, is it valid?
        // 3. If so, execute the 'let' block with the valid leash.
        leash
            ?.takeIf { it.isValid }
            ?.let { validLeash -> t.setPosition(validLeash, x.toFloat(), y.toFloat()) }
    }

    /** Releases the surface control and tears down the view hierarchy. */
    fun release() {
        // If viewHost is not null, run this block. 'it' is the non-null viewHost.
        viewHost?.let {
            it.release()
            viewHost = null
        }

        // If leash is not null, run this block. 'currentLeash' is the non-null leash.
        leash?.let { currentLeash ->
            syncQueue.runInSync { t: SurfaceControl.Transaction -> t.remove(currentLeash) }
            leash = null
        }
    }

    fun setCornersVisibility(
        executor: ShellExecutor,
        visible: Boolean,
        immediate: Boolean = false,
    ) {
        leftTopRoundedCorner?.setCornersVisibility(executor, visible, immediate)
        rightTopRoundedCorner?.setCornersVisibility(executor, visible, immediate)
        leftBottomRoundedCorner?.setCornersVisibility(executor, visible, immediate)
        rightBottomRoundedCorner?.setCornersVisibility(executor, visible, immediate)
    }

    private fun View.setCornersVisibility(
        executor: ShellExecutor,
        visible: Boolean,
        immediate: Boolean = false,
    ) {
        val drawable = background as? LetterboxRoundedCornersDrawable
        drawable?.let {
            if (visible) it.show(executor, immediate) else it.hide(executor, immediate)
        }
    }

    private fun getRoundedCornersViewWindowLayoutParams(): WindowManager.LayoutParams {
        return getWindowLayoutParams(letterboxBounds.width(), letterboxBounds.height())
    }

    /** Gets the layout params given the width and height of the layout. */
    private fun getWindowLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        // Cannot be wrap_content as this determines the actual window size
        val winParams =
            WindowManager.LayoutParams(
                width,
                height,
                TYPE_APPLICATION_OVERLAY,
                getWindowManagerLayoutParamsFlags(),
                PixelFormat.TRANSLUCENT,
            )
        winParams.token = Binder()
        winParams.title = javaClass.simpleName
        winParams.privateFlags =
            winParams.privateFlags or
                (WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION or
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
        return winParams
    }

    /** @return Flags to use for the [WindowManager] layout */
    private fun getWindowManagerLayoutParamsFlags(): Int {
        return FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
    }

    /** Inits the z-order of the surface. */
    private fun initSurface(leash: SurfaceControl?) {
        syncQueue.runInSync { t: SurfaceControl.Transaction ->
            val validLeash = leash?.takeIf { it.isValid } ?: return@runInSync
            t.setLayer(validLeash, TaskConstants.TASK_CHILD_SHELL_LAYER_LETTERBOX_ROUNDED_CORNERS)
        }
    }

    @SuppressLint("InflateParams")
    private fun inflateLayout(): View {
        val color = letterboxConfiguration.getLetterboxBackgroundColor()
        val radius = letterboxConfiguration.getLetterboxActivityCornersRadius().toFloat()
        // The parent here must be [null] because this is a View that will be set as the [View] of
        // a [SurfaceControlViewHost].
        return LayoutInflater.from(context)
            .inflate(R.layout.letterbox_shell_rounded_corners, null)
            .apply {
                leftTopRoundedCorner =
                    findViewById<View>(R.id.roundedCornerTopLeft)
                        .applyCornerAttrs(TOP_LEFT, radius, Gravity.TOP or Gravity.START, color)
                rightTopRoundedCorner =
                    findViewById<View>(R.id.roundedCornerTopRight)
                        .applyCornerAttrs(TOP_RIGHT, radius, Gravity.TOP or Gravity.END, color)
                leftBottomRoundedCorner =
                    findViewById<View>(R.id.roundedCornerBottomLeft)
                        .applyCornerAttrs(
                            BOTTOM_LEFT,
                            radius,
                            Gravity.BOTTOM or Gravity.START,
                            color,
                        )
                rightBottomRoundedCorner =
                    findViewById<View>(R.id.roundedCornerBottomRight)
                        .applyCornerAttrs(
                            BOTTOM_RIGHT,
                            radius,
                            Gravity.BOTTOM or Gravity.END,
                            color,
                        )
            }
    }

    private fun View.applyCornerAttrs(
        position: RoundedCornersDrawableFactory.Position,
        radius: Float,
        cornerGravity: Int,
        color: Color,
    ): View {
        layoutParams =
            FrameLayout.LayoutParams(radius.toInt(), radius.toInt()).apply {
                gravity = cornerGravity
            }
        background = cornersFactory.getRoundedCornerDrawable(color, position, radius)
        return this
    }
}
