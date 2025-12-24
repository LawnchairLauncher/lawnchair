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
package com.android.wm.shell.windowdecor.tiling

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.provider.DeviceConfig
import android.util.AttributeSet
import android.util.Size
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.android.internal.R as InternalR
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags
import com.android.wm.shell.R
import com.android.wm.shell.common.split.DividerHandleView
import com.android.wm.shell.common.split.DividerRoundedCorner
import com.android.wm.shell.shared.animation.Interpolators
import com.android.wm.shell.windowdecor.DragDetector

/** Divider for tiling split screen, currently mostly a copy of [DividerView]. */
class TilingDividerView : FrameLayout, View.OnTouchListener, DragDetector.MotionEventHandler {
    private val paint = Paint()
    private val backgroundRect = Rect()

    private lateinit var callback: DividerMoveCallback
    private lateinit var handle: DividerHandleView
    private lateinit var corners: DividerRoundedCorner
    private var touchElevation = 0

    private var moving = false
    private var startPos = 0
    var handleRegionWidth: Int = 0
    private var handleRegionHeight = 0
    private var lastAcceptedPos = 0
    @VisibleForTesting var handleY: IntRange = 0..0
    private var canResize = false
    private var resized = false
    private var isDarkMode = false

    /**
     * Tracks divider bar visible bounds in screen-based coordination. Used to calculate with
     * insets.
     */
    private val dividerBounds = Rect()
    private var dividerBar: FrameLayout? = null
    private lateinit var dragDetector: DragDetector

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    /** Sets up essential dependencies of the divider bar. */
    fun setup(
        dividerMoveCallback: DividerMoveCallback,
        dividerBounds: Rect,
        handleRegionSize: Size,
        isDarkMode: Boolean,
    ) {
        callback = dividerMoveCallback
        this.dividerBounds.set(dividerBounds)
        this.isDarkMode = isDarkMode
        paint.color = getDividerColor()
        handle.setIsLeftRightSplit(true)
        handle.setColor(getTilingHandleColor(isDarkMode), /* invalidateView= */ false)
        handle.alpha = HANDLE_ALPHA
        corners.setIsLeftRightSplit(true)
        corners.setRadiusResource(
            com.android.wm.shell.shared.R.dimen.desktop_windowing_freeform_rounded_corner_radius
        )
        corners.setRoundCornerColor(paint.color, /* invalidateView= */ false)
        handleRegionHeight = handleRegionSize.height
        handleRegionWidth = handleRegionSize.width
        initHandleYCoordinates()
        dragDetector =
            DragDetector(
                this,
                /* holdToDragMinDurationMs= */ 0,
                ViewConfiguration.get(mContext).scaledTouchSlop,
            )
    }

    fun onUiModeChange(isDarkMode: Boolean) {
        this.isDarkMode = isDarkMode
        handle.setColor(getTilingHandleColor(isDarkMode), /* invalidateView= */ true)
        paint.color = getDividerColor()
        corners.setRoundCornerColor(paint.color, /* invalidateView= */ true)
        invalidate()
    }

    fun onThemeChanged() {
        val newColor = getDividerColor()
        if (paint.color != newColor) {
            paint.color = newColor
            corners.setRoundCornerColor(paint.color, /* invalidateView= */ true)
            invalidate()
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        dividerBar = requireViewById(R.id.divider_bar)
        handle = requireViewById(R.id.docked_divider_handle)
        corners = requireViewById(R.id.docked_divider_rounded_corner)
        touchElevation =
            resources.getDimensionPixelSize(R.dimen.docked_stack_divider_lift_elevation)
        setOnTouchListener(this)
        setWillNotDraw(false)
        paint.color = getDividerColor()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            val dividerSize = resources.getDimensionPixelSize(R.dimen.split_divider_bar_width)
            val backgroundLeft = (width - dividerSize) / 2
            val backgroundTop = 0
            val backgroundRight = backgroundLeft + dividerSize
            val backgroundBottom = height
            backgroundRect.set(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom)
        }
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon {
        if (isWithinHandleRegion(event.y.toInt())) {
            return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)
        }
        return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean =
        dragDetector.onMotionEvent(v, event)

    private fun setTouching() {
        handle.setTouching(true, true)
        // Lift handle as well so it doesn't get behind the background, even though it doesn't
        // cast shadow.
        handle
            .animate()
            .setInterpolator(Interpolators.TOUCH_RESPONSE)
            .setDuration(TOUCH_ANIMATION_DURATION)
            .translationZ(touchElevation.toFloat())
            .start()
    }

    private fun getDividerColor(): Int =
        context.resources.getColor(InternalR.color.customColorOverviewBackground, context.theme)

    /**
     * Retrieves the tiling handle background color based on the current dark mode status.
     *
     * @param isDarkMode A boolean indicating whether dark mode is currently active.
     * @return The integer color value for the tiling handle background.
     */
    private fun getTilingHandleColor(isDarkMode: Boolean): Int {
        val colorResId =
            if (isDarkMode) {
                R.color.tiling_handle_background_dark
            } else {
                R.color.tiling_handle_background_light
            }
        return resources.getColor(colorResId, /* theme= */ null)
    }

    private fun releaseTouching() {
        handle.setTouching(false, true)
        handle
            .animate()
            .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
            .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
            .translationZ(0f)
            .start()
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (
            !DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                SystemUiDeviceConfigFlags.CURSOR_HOVER_STATES_ENABLED,
                /* defaultValue = */ false,
            )
        ) {
            return false
        }

        if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
            setHovering()
            return true
        }
        if (event.action == MotionEvent.ACTION_HOVER_EXIT) {
            releaseHovering()
            return true
        }
        return false
    }

    @VisibleForTesting
    fun setHovering() {
        handle.setHovering(true, true)
        handle
            .animate()
            .setInterpolator(Interpolators.TOUCH_RESPONSE)
            .setDuration(TOUCH_ANIMATION_DURATION)
            .translationZ(touchElevation.toFloat())
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(backgroundRect, paint)
    }

    @VisibleForTesting
    fun releaseHovering() {
        handle.setHovering(false, true)
        handle
            .animate()
            .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
            .setDuration(TOUCH_RELEASE_ANIMATION_DURATION)
            .translationZ(0f)
            .start()
    }

    override fun handleMotionEvent(v: View?, event: MotionEvent): Boolean {
        val touchPos = event.rawX.toInt()
        val yTouchPosInDivider = event.y.toInt()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isWithinHandleRegion(yTouchPosInDivider)) return true
                callback.onDividerMoveStart(touchPos, event)
                setTouching()
                canResize = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!canResize) return true
                if (!moving) {
                    startPos = touchPos
                    moving = true
                }

                val pos = dividerBounds.left + touchPos - startPos
                if (callback.onDividerMove(pos)) {
                    lastAcceptedPos = touchPos
                    resized = true
                }
            }

            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                if (!canResize) return true
                if (moving && resized) {
                    dividerBounds.left = dividerBounds.left + lastAcceptedPos - startPos
                    callback.onDividerMovedEnd(dividerBounds.left, event)
                }
                moving = false
                canResize = false
                resized = false
                releaseTouching()
            }
        }
        return true
    }

    private fun isWithinHandleRegion(touchYPos: Int): Boolean = touchYPos in handleY

    private fun initHandleYCoordinates() {
        val handleStartY = (dividerBounds.height() - handleRegionHeight) / 2
        val handleEndY = handleStartY + handleRegionHeight
        handleY = handleStartY..handleEndY
    }

    companion object {
        const val TOUCH_ANIMATION_DURATION: Long = 150
        const val TOUCH_RELEASE_ANIMATION_DURATION: Long = 200
        const val HANDLE_ALPHA = 0.9f
        private val TAG = TilingDividerView::class.java.simpleName
    }
}
