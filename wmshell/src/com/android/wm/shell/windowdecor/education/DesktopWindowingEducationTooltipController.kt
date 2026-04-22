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

package com.android.wm.shell.windowdecor.education

import android.annotation.ColorInt
import android.annotation.DimenRes
import android.annotation.LayoutRes
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.util.Size
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.View.MeasureSpec.UNSPECIFIED
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.window.DisplayAreaInfo
import android.window.WindowContainerTransaction
import androidx.core.graphics.drawable.DrawableCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayChangeController.OnDisplayChangingListener
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer

/**
 * Controls the lifecycle of an education tooltip, including showing and hiding it. Ensures that
 * only one tooltip is displayed at a time.
 */
class DesktopWindowingEducationTooltipController(
    private val context: Context,
    private val additionalSystemViewContainerFactory: AdditionalSystemViewContainer.Factory,
    private val displayController: DisplayController,
) : OnDisplayChangingListener {
  private var tooltipView: View? = null
  private var animator: PhysicsAnimator<View>? = null
  private val springConfig by lazy {
    PhysicsAnimator.SpringConfig(SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_LOW_BOUNCY)
  }
  private var popupWindow: AdditionalSystemViewContainer? = null

  override fun onDisplayChange(
      displayId: Int,
      fromRotation: Int,
      toRotation: Int,
      newDisplayAreaInfo: DisplayAreaInfo?,
      t: WindowContainerTransaction?
  ) {
    // Exit if the rotation hasn't changed or is changed by 180 degrees. [fromRotation] and
    // [toRotation] can be one of the [@Surface.Rotation] values.
    if ((fromRotation % 2 == toRotation % 2)) return
    hideEducationTooltip()
    // TODO: b/370820018 - Update tooltip position on orientation change instead of dismissing
  }

  /**
   * Shows education tooltip.
   *
   * @param tooltipViewConfig features of tooltip.
   * @param taskId is used in the title of popup window created for the tooltip view.
   */
  fun showEducationTooltip(tooltipViewConfig: TooltipEducationViewConfig, taskId: Int) {
    hideEducationTooltip()
    tooltipView = createEducationTooltipView(tooltipViewConfig, taskId)
    animator = createAnimator()
    animateShowTooltipTransition()
    displayController.addDisplayChangingController(this)
  }

  /** Hide the current education view if visible */
  fun hideEducationTooltip() = animateHideTooltipTransition { cleanUp() }

  /** Create education view by inflating layout provided. */
  private fun createEducationTooltipView(
      tooltipViewConfig: TooltipEducationViewConfig,
      taskId: Int,
  ): View {
    val tooltipView =
        LayoutInflater.from(context)
            .inflate(
                tooltipViewConfig.tooltipViewLayout, /* root= */ null, /* attachToRoot= */ false)
            .apply {
              alpha = 0f
              scaleX = 0f
              scaleY = 0f

              requireViewById<TextView>(R.id.tooltip_text).apply {
                text = tooltipViewConfig.tooltipText
              }

              setOnTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_OUTSIDE) {
                  hideEducationTooltip()
                  tooltipViewConfig.onDismissAction()
                  true
                } else {
                  false
                }
              }
              setOnClickListener {
                hideEducationTooltip()
                tooltipViewConfig.onEducationClickAction()
              }
              setTooltipColorScheme(tooltipViewConfig.tooltipColorScheme)
            }

    val tooltipDimens = tooltipDimens(tooltipView = tooltipView, tooltipViewConfig.arrowDirection)
    val tooltipViewGlobalCoordinates =
        tooltipViewGlobalCoordinates(
            tooltipViewGlobalCoordinates = tooltipViewConfig.tooltipViewGlobalCoordinates,
            arrowDirection = tooltipViewConfig.arrowDirection,
            tooltipDimen = tooltipDimens)
    createTooltipPopupWindow(
        taskId, tooltipViewGlobalCoordinates, tooltipDimens, tooltipView = tooltipView)

    return tooltipView
  }

  /** Create animator for education transitions */
  private fun createAnimator(): PhysicsAnimator<View>? =
      tooltipView?.let {
        PhysicsAnimator.getInstance(it).apply { setDefaultSpringConfig(springConfig) }
      }

  /** Animate show transition for the education view */
  private fun animateShowTooltipTransition() {
    animator
        ?.spring(DynamicAnimation.ALPHA, 1f)
        ?.spring(DynamicAnimation.SCALE_X, 1f)
        ?.spring(DynamicAnimation.SCALE_Y, 1f)
        ?.start()
  }

  /** Animate hide transition for the education view */
  private fun animateHideTooltipTransition(endActions: () -> Unit) {
    animator
        ?.spring(DynamicAnimation.ALPHA, 0f)
        ?.spring(DynamicAnimation.SCALE_X, 0f)
        ?.spring(DynamicAnimation.SCALE_Y, 0f)
        ?.start()
    endActions()
  }

  /** Remove education tooltip and clean up all relative properties */
  private fun cleanUp() {
    tooltipView = null
    animator = null
    popupWindow?.releaseView()
    popupWindow = null
    displayController.removeDisplayChangingController(this)
  }

  private fun createTooltipPopupWindow(
      taskId: Int,
      tooltipViewGlobalCoordinates: Point,
      tooltipDimen: Size,
      tooltipView: View,
  ) {
    popupWindow =
        additionalSystemViewContainerFactory.create(
            windowManagerWrapper =
                WindowManagerWrapper(context.getSystemService(WindowManager::class.java)),
            taskId = taskId,
            x = tooltipViewGlobalCoordinates.x,
            y = tooltipViewGlobalCoordinates.y,
            width = tooltipDimen.width,
            height = tooltipDimen.height,
            flags =
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            view = tooltipView)
  }

  private fun View.setTooltipColorScheme(tooltipColorScheme: TooltipColorScheme) {
    requireViewById<LinearLayout>(R.id.tooltip_container).apply {
      background.setTint(tooltipColorScheme.container)
    }
    requireViewById<ImageView>(R.id.arrow_icon).apply {
        val wrappedDrawable = DrawableCompat.wrap(this.drawable)
        DrawableCompat.setTint(wrappedDrawable, tooltipColorScheme.container)
        if (isRtl()) scaleX = -1f
    }
    requireViewById<TextView>(R.id.tooltip_text).apply { setTextColor(tooltipColorScheme.text) }
    requireViewById<ImageView>(R.id.tooltip_icon).apply {
      val wrappedDrawable = DrawableCompat.wrap(this.drawable)
      DrawableCompat.setTint(wrappedDrawable, tooltipColorScheme.icon)
    }
  }

  private fun tooltipViewGlobalCoordinates(
      tooltipViewGlobalCoordinates: Point,
      arrowDirection: TooltipArrowDirection,
      tooltipDimen: Size,
  ): Point {
    var tooltipX = tooltipViewGlobalCoordinates.x
    var tooltipY = tooltipViewGlobalCoordinates.y

    // Current values of [tooltipX]/[tooltipY] are the coordinates of tip of the arrow.
    // Parameter x and y passed to [AdditionalSystemViewContainer] is the top left position of
    // the window to be created. Hence we will need to move the coordinates left/up in order
    // to position the tooltip correctly.
    if (arrowDirection == TooltipArrowDirection.UP) {
      // Arrow is placed at horizontal center on top edge of the tooltip. Hence decrement
      // half of tooltip width from [tooltipX] to horizontally position the tooltip.
      tooltipX -= tooltipDimen.width / 2
    } else {
      // Arrow is placed at vertical center on the left edge of the tooltip. Hence decrement
      // half of tooltip height from [tooltipY] to vertically position the tooltip.
      tooltipY -= tooltipDimen.height / 2
      if (isRtl()) {
          tooltipX -= tooltipDimen.width
      }
    }
    return Point(tooltipX, tooltipY)
  }

  private fun tooltipDimens(tooltipView: View, arrowDirection: TooltipArrowDirection): Size {
    val tooltipBackground = tooltipView.requireViewById<LinearLayout>(R.id.tooltip_container)
    val arrowView = tooltipView.requireViewById<ImageView>(R.id.arrow_icon)
    tooltipBackground.measure(
        /* widthMeasureSpec= */ UNSPECIFIED, /* heightMeasureSpec= */ UNSPECIFIED)
    arrowView.measure(/* widthMeasureSpec= */ UNSPECIFIED, /* heightMeasureSpec= */ UNSPECIFIED)

    var desiredWidth =
        tooltipBackground.measuredWidth +
            2 * loadDimensionPixelSize(R.dimen.desktop_windowing_education_tooltip_padding)
    var desiredHeight =
        tooltipBackground.measuredHeight +
            2 * loadDimensionPixelSize(R.dimen.desktop_windowing_education_tooltip_padding)
    if (arrowDirection == TooltipArrowDirection.UP) {
      // desiredHeight currently does not account for the height of arrow, hence adding it.
      desiredHeight += arrowView.height
    } else {
      // desiredWidth currently does not account for the width of arrow, hence adding it.
      desiredWidth += arrowView.width
    }

    return Size(desiredWidth, desiredHeight)
  }

  private fun loadDimensionPixelSize(@DimenRes resourceId: Int): Int {
    if (resourceId == Resources.ID_NULL) return 0
    return context.resources.getDimensionPixelSize(resourceId)
  }

    private fun isRtl() = context.resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL

    /**
   * The configuration for education view features:
   *
   * @property tooltipViewLayout Layout resource ID of the view to be used for education tooltip.
   * @property tooltipViewGlobalCoordinates Global (screen) coordinates of the tip of the tooltip
   *   arrow.
   * @property tooltipText Text to be added to the TextView of tooltip.
   * @property arrowDirection Direction of arrow of the tooltip.
   * @property onEducationClickAction Lambda to be executed when the tooltip is clicked.
   * @property onDismissAction Lambda to be executed when the tooltip is dismissed.
   */
  data class TooltipEducationViewConfig(
      @LayoutRes val tooltipViewLayout: Int,
      val tooltipColorScheme: TooltipColorScheme,
      val tooltipViewGlobalCoordinates: Point,
      val tooltipText: String,
      val arrowDirection: TooltipArrowDirection,
      val onEducationClickAction: () -> Unit,
      val onDismissAction: () -> Unit,
  )

  /**
   * Color scheme of education view:
   *
   * @property container Color of the container of the tooltip.
   * @property text Text color of the [TextView] of education tooltip.
   * @property icon Color to be filled in tooltip's icon.
   */
  data class TooltipColorScheme(
      @ColorInt val container: Int,
      @ColorInt val text: Int,
      @ColorInt val icon: Int,
  )

  /** Direction of arrow of the tooltip */
  enum class TooltipArrowDirection {
    UP,
    HORIZONTAL
  }
}