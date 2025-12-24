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

package com.android.quickstep.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.app.animation.Interpolators
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.views.BaseDragLayer
import com.android.quickstep.util.MultiValueUpdateListener
import com.android.quickstep.util.SplitAnimationTimings
import com.android.systemui.shared.system.QuickStepContract

/**
 * View representing a Desktop task by showing its thumbnail.
 *
 * Use [Companion.create] to create an instance of this view, and [addClosingAnimation] to animate
 * the closing of the Desktop task.
 */
class FloatingDesktopTaskView(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    FrameLayout(context, attrs, defStyleAttr) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    private val isRtl = Utilities.isRtl(resources)
    private val cornerRadius = QuickStepContract.getWindowCornerRadius(context)
    private lateinit var thumbnailView: FloatingTaskThumbnailView
    private lateinit var startBounds: RectF

    override fun onFinishInflate() {
        super.onFinishInflate()
        thumbnailView = findViewById(R.id.thumbnail)
    }

    private fun init(
        recentsViewContainer: RecentsViewContainer,
        startBounds: RectF,
        thumbnail: Bitmap?,
    ) {
        this.startBounds = startBounds
        val lp: BaseDragLayer.LayoutParams =
            BaseDragLayer.LayoutParams(
                Math.round(startBounds.width()),
                Math.round(startBounds.height()),
            )
        initPosition(recentsViewContainer, startBounds, lp)
        layoutParams = lp

        pivotX = 0f
        pivotY = 0f

        // Copy bounds of exiting thumbnail into ImageView
        thumbnailView.setThumbnail(thumbnail)
        thumbnailView.setDrawCallback { canvas: Canvas, paint: Paint ->
            canvas.drawRoundRect(
                /* left= */ 0f,
                /* top= */ 0f,
                measuredWidth.toFloat(),
                measuredHeight.toFloat(),
                cornerRadius,
                cornerRadius,
                paint,
            )
        }
        thumbnailView.visibility = VISIBLE
    }

    private fun initPosition(
        recentsViewContainer: RecentsViewContainer,
        pos: RectF,
        lp: InsettableFrameLayout.LayoutParams,
    ) {
        lp.ignoreInsets = true
        // Position the floating view exactly on top of the original
        lp.topMargin = Math.round(pos.top)
        if (isRtl) {
            val deviceProperties = recentsViewContainer.getDeviceProfile().deviceProperties
            lp.marginStart = deviceProperties.widthPx - Math.round(pos.right)
        } else {
            lp.marginStart = Math.round(pos.left)
        }

        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        val left = pos.left.toInt()
        layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height)
    }

    /** Animates the FloatingTaskThumbnailView to fade out and scale down. */
    fun addClosingAnimation(
        recentsViewContainer: RecentsViewContainer,
        animation: PendingAnimation,
    ) {
        // Use the Workspace pivot point, so the Desktop task view scales together with Workspace.
        if (recentsViewContainer is QuickstepLauncher) {
            recentsViewContainer.workspace.setPivotToScaleWithSelf(this)
        }

        val timings =
            if (recentsViewContainer.deviceProfile.deviceProperties.isTablet) {
                SplitAnimationTimings.TABLET_OVERVIEW_TO_SPLIT
            } else {
                SplitAnimationTimings.PHONE_OVERVIEW_TO_SPLIT
            }
        addFadeAnimation(animation, timings)
        addScaleAnimation(animation, timings)
    }

    private fun addFadeAnimation(animation: PendingAnimation, timings: SplitAnimationTimings) {
        // FloatingTaskThumbnailView: thumbnail fades out to transparent
        animation.setViewAlpha(
            thumbnailView,
            0f,
            Interpolators.clampToProgress(
                Interpolators.LINEAR,
                timings.placeholderFadeInStartOffset,
                timings.placeholderFadeInEndOffset,
            ),
        )
    }

    private fun addScaleAnimation(animation: PendingAnimation, timings: SplitAnimationTimings) {
        val transitionAnimator = ValueAnimator.ofFloat(0f, 1f)
        animation.add(transitionAnimator)
        val floatingTaskViewBounds = RectF()
        val listener: MultiValueUpdateListener =
            object : MultiValueUpdateListener() {
                val scaleX: FloatProp =
                    FloatProp(
                        1f,
                        TASK_VIEW_END_SCALE,
                        Interpolators.clampToProgress(
                            timings.stagedRectScaleXInterpolator,
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset,
                        ),
                    )
                val scaleY: FloatProp =
                    FloatProp(
                        1f,
                        TASK_VIEW_END_SCALE,
                        Interpolators.clampToProgress(
                            timings.stagedRectScaleYInterpolator,
                            timings.stagedRectSlideStartOffset,
                            timings.stagedRectSlideEndOffset,
                        ),
                    )

                override fun onUpdate(percent: Float, initOnly: Boolean) {
                    floatingTaskViewBounds.set(startBounds)
                    update(scaleX.value, scaleY.value)
                }
            }

        transitionAnimator.addUpdateListener(listener)
    }

    private fun update(scaleX: Float, scaleY: Float) {
        setScaleX(scaleX)
        setScaleY(scaleY)
        thumbnailView.invalidate()
    }

    companion object {
        private const val TASK_VIEW_END_SCALE = 0.95f

        /**
         * Configures and returns an instance of [FloatingDesktopTaskView] showing [thumbnail] with
         * an initial position of [startBounds].
         */
        fun create(
            recentsViewContainer: RecentsViewContainer,
            startBounds: RectF,
            thumbnail: Bitmap,
        ): FloatingDesktopTaskView {
            val dragLayer: ViewGroup = recentsViewContainer.dragLayer
            val floatingView =
                recentsViewContainer.layoutInflater.inflate(
                    R.layout.floating_desktop_view,
                    dragLayer,
                    /* attachToRoot= */ false,
                ) as FloatingDesktopTaskView

            floatingView.init(recentsViewContainer, startBounds, thumbnail)
            FloatingTaskView.addViewBelowTaskMenu(recentsViewContainer, floatingView)
            return floatingView
        }
    }
}
