/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.bubbles

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.util.PathParser
import android.view.LayoutInflater
import android.view.View.VISIBLE
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView

class BubbleOverflow(private val context: Context, private val positioner: BubblePositioner) :
    BubbleViewProvider {

    private lateinit var bitmap: Bitmap
    private lateinit var dotPath: Path

    private var dotColor = 0
    private var showDot = false
    private var overflowIconInset = 0

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var expandedView: BubbleExpandedView?
    private var bubbleBarExpandedView: BubbleBarExpandedView? = null
    private var overflowBtn: BadgedImageView?

    init {
        updateResources()
        expandedView = null
        overflowBtn = null
    }

    /** Call before use and again if cleanUpExpandedState was called. */
    fun initialize(
        expandedViewManager: BubbleExpandedViewManager,
        stackView: BubbleStackView,
        positioner: BubblePositioner
    ) {
        createExpandedView()
                .initialize(
                        expandedViewManager,
                    stackView,
                    positioner,
                    /* isOverflow= */ true,
                    /* bubbleTaskView= */ null
                )
    }

    fun initializeForBubbleBar(
        expandedViewManager: BubbleExpandedViewManager,
        positioner: BubblePositioner
    ) {
        createBubbleBarExpandedView()
            .initialize(
                expandedViewManager,
                positioner,
                /* isOverflow= */ true,
                /* bubbleTaskView= */ null
            )
    }

    fun cleanUpExpandedState() {
        expandedView?.cleanUpExpandedState()
        expandedView = null
        bubbleBarExpandedView?.cleanUpExpandedState()
        bubbleBarExpandedView = null
    }

    fun update() {
        updateResources()
        getExpandedView()?.applyThemeAttrs()
        getBubbleBarExpandedView()?.applyThemeAttrs()
        // Apply inset and new style to fresh icon drawable.
        getIconView()?.setIconImageResource(R.drawable.bubble_ic_overflow_button)
        updateBtnTheme()
    }

    fun updateResources() {
        overflowIconInset =
            context.resources.getDimensionPixelSize(R.dimen.bubble_overflow_icon_inset)
        overflowBtn?.layoutParams =
            FrameLayout.LayoutParams(positioner.bubbleSize, positioner.bubbleSize)
        expandedView?.updateDimensions()
    }

    private fun updateBtnTheme() {
        val res = context.resources

        // Set overflow button accent color, dot color

        val typedArray =
            context.obtainStyledAttributes(
                intArrayOf(
                    com.android.internal.R.attr.materialColorPrimaryFixed,
                    com.android.internal.R.attr.materialColorOnPrimaryFixed
                )
            )

        val colorAccent = typedArray.getColor(0, Color.WHITE)
        val shapeColor = typedArray.getColor(1, Color.BLACK)
        typedArray.recycle()

        dotColor = colorAccent
        overflowBtn?.iconDrawable?.setTint(shapeColor)

        val iconFactory =
            BubbleIconFactory(
                context,
                res.getDimensionPixelSize(R.dimen.bubble_size),
                res.getDimensionPixelSize(R.dimen.bubble_badge_size),
                ContextCompat.getColor(
                    context,
                    com.android.launcher3.icons.R.color.important_conversation
                ),
                res.getDimensionPixelSize(com.android.internal.R.dimen.importance_ring_stroke_width)
            )

        // Update bitmap
        val fg = InsetDrawable(overflowBtn?.iconDrawable, overflowIconInset)
        bitmap =
            iconFactory
                .createBadgedIconBitmap(AdaptiveIconDrawable(ColorDrawable(colorAccent), fg))
                .icon

        // Update dot path
        dotPath =
            PathParser.createPathFromPathData(
                res.getString(com.android.internal.R.string.config_icon_mask)
            )
        val scale =
            iconFactory.normalizer.getScale(
                iconView!!.iconDrawable,
                null /* outBounds */,
                null /* path */,
                null /* outMaskShape */
            )
        val radius = BadgedImageView.DEFAULT_PATH_SIZE / 2f
        val matrix = Matrix()
        matrix.setScale(
            scale /* x scale */,
            scale /* y scale */,
            radius /* pivot x */,
            radius /* pivot y */
        )
        dotPath.transform(matrix)

        // Attach BubbleOverflow to BadgedImageView
        overflowBtn?.setRenderedBubble(this)
        overflowBtn?.removeDotSuppressionFlag(BadgedImageView.SuppressionFlag.FLYOUT_VISIBLE)
    }

    fun setVisible(visible: Int) {
        overflowBtn?.visibility = visible
    }

    fun setShowDot(show: Boolean) {
        showDot = show
        if (overflowBtn?.visibility == VISIBLE) {
            overflowBtn?.updateDotVisibility(true /* animate */)
        }
    }

    /** Creates the expanded view for bubbles showing in the stack view. */
    private fun createExpandedView(): BubbleExpandedView {
        val view =
            inflater.inflate(
                R.layout.bubble_expanded_view,
                null /* root */,
                false /* attachToRoot */
            ) as BubbleExpandedView
        view.applyThemeAttrs()
        expandedView = view
        updateResources()
        return view
    }

    override fun getExpandedView(): BubbleExpandedView? {
        return expandedView
    }

    /** Creates the expanded view for bubbles showing in the bubble bar. */
    private fun createBubbleBarExpandedView(): BubbleBarExpandedView {
        val view =
            inflater.inflate(
                R.layout.bubble_bar_expanded_view,
                null, /* root */
                false /* attachToRoot*/
            ) as BubbleBarExpandedView
        view.applyThemeAttrs()
        bubbleBarExpandedView = view
        return view
    }

    override fun getBubbleBarExpandedView(): BubbleBarExpandedView? = bubbleBarExpandedView

    override fun getDotColor(): Int {
        return dotColor
    }

    override fun getAppBadge(): Bitmap? {
        return null
    }

    override fun getRawAppBadge(): Bitmap? {
        return null
    }

    override fun getBubbleIcon(): Bitmap {
        return bitmap
    }

    override fun showDot(): Boolean {
        return showDot
    }

    override fun getDotPath(): Path? {
        return dotPath
    }

    override fun setTaskViewVisibility(visible: Boolean) {
        // Overflow does not have a TaskView.
    }

    override fun getIconView(): BadgedImageView? {
        if (overflowBtn == null) {
            overflowBtn =
                inflater.inflate(
                    R.layout.bubble_overflow_button,
                    null /* root */,
                    false /* attachToRoot */
                ) as BadgedImageView
            overflowBtn?.initialize(positioner)
            overflowBtn?.contentDescription =
                context.resources.getString(R.string.bubble_overflow_button_content_description)
            val bubbleSize = positioner.bubbleSize
            overflowBtn?.layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize)
            updateBtnTheme()
        }
        return overflowBtn
    }

    override fun getKey(): String {
        return KEY
    }

    override fun getTaskId(): Int {
        return if (expandedView != null) expandedView!!.taskId else INVALID_TASK_ID
    }

    companion object {
        const val KEY = "Overflow"
    }
}
