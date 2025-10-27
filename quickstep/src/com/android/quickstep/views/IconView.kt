/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.Utilities
import com.android.launcher3.util.MSDLPlayerWrapper
import com.android.launcher3.util.MultiValueAlpha
import com.android.launcher3.views.ActivityContext
import com.android.quickstep.util.RecentsOrientedState
import com.google.android.msdl.data.model.MSDLToken

/**
 * A view which draws a drawable stretched to fit its size. Unlike ImageView, it avoids relayout
 * when the drawable changes.
 */
class IconView : View, TaskViewIcon {
    private val multiValueAlpha: MultiValueAlpha = MultiValueAlpha(this, NUM_ALPHA_CHANNELS)
    private var drawable: Drawable? = null
    private var drawableWidth = 0
    private var drawableHeight = 0
    private var msdlPlayerWrapper: MSDLPlayerWrapper? = null

    constructor(context: Context) : super(context) {
        setUpHaptics()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        setUpHaptics()
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr) {
        setUpHaptics()
    }

    init {
        multiValueAlpha.setUpdateVisibility(true)
    }

    private fun setUpHaptics() {
        msdlPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
        // Haptics are handled by the MSDLPlayerWrapper
        isHapticFeedbackEnabled = !Flags.msdlFeedback() || msdlPlayerWrapper == null
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        super.setOnLongClickListener(l?.withFeedback())
    }

    /** Sets a [Drawable] to be displayed. */
    override fun setDrawable(d: Drawable?) {
        drawable?.callback = null

        // Copy drawable so that mutations below do not affect other users of the drawable
        drawable = d?.constantState?.newDrawable()?.mutate()
        drawable?.let {
            it.callback = this
            setDrawableSizeInternal(width, height)
        }
        invalidate()
    }

    /** Sets the size of the icon drawable. */
    override fun setDrawableSize(iconWidth: Int, iconHeight: Int) {
        drawableWidth = iconWidth
        drawableHeight = iconHeight
        drawable?.let { setDrawableSizeInternal(width, height) }
    }

    private fun setDrawableSizeInternal(selfWidth: Int, selfHeight: Int) {
        val selfRect = Rect(0, 0, selfWidth, selfHeight)
        val drawableRect = Rect()
        Gravity.apply(Gravity.CENTER, drawableWidth, drawableHeight, selfRect, drawableRect)
        drawable?.bounds = drawableRect
    }

    override fun getDrawable(): Drawable? = drawable

    override fun getDrawableWidth(): Int = drawableWidth

    override fun getDrawableHeight(): Int = drawableHeight

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawable?.let { setDrawableSizeInternal(w, h) }
    }

    override fun verifyDrawable(who: Drawable): Boolean =
        super.verifyDrawable(who) || who === drawable

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        drawable?.let {
            if (it.isStateful && it.setState(drawableState)) {
                invalidateDrawable(it)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawable?.draw(canvas)
    }

    override fun hasOverlappingRendering(): Boolean = false

    override fun setContentAlpha(alpha: Float) {
        multiValueAlpha[INDEX_CONTENT_ALPHA].setValue(alpha)
    }

    override fun setModalAlpha(alpha: Float) {
        multiValueAlpha[INDEX_MODAL_ALPHA].setValue(alpha)
    }

    override fun setFlexSplitAlpha(alpha: Float) {
        multiValueAlpha[INDEX_FLEX_SPLIT_ALPHA].setValue(alpha)
    }

    /**
     * Set the tint color of the icon, useful for scrimming or dimming.
     *
     * @param color to blend in.
     * @param amount [0,1] 0 no tint, 1 full tint
     */
    override fun setIconColorTint(color: Int, amount: Float) {
        drawable?.colorFilter = Utilities.makeColorTintingColorFilter(color, amount)
    }

    override fun setIconOrientation(orientationState: RecentsOrientedState, isGridTask: Boolean) {
        val orientationHandler = orientationState.orientationHandler
        val deviceProfile: DeviceProfile =
            (ActivityContext.lookupContext(context) as ActivityContext).getDeviceProfile()
        orientationHandler.setTaskIconParams(
            iconParams = getLayoutParams() as FrameLayout.LayoutParams,
            taskIconMargin = deviceProfile.overviewTaskMarginPx,
            taskIconHeight = deviceProfile.overviewTaskIconSizePx,
            thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx,
            isRtl = layoutDirection == LAYOUT_DIRECTION_RTL,
        )
        updateLayoutParams<FrameLayout.LayoutParams> {
            height = deviceProfile.overviewTaskIconSizePx
            width = height
        }
        setRotation(orientationHandler.degreesRotated)
        val iconDrawableSize =
            if (isGridTask) deviceProfile.overviewTaskIconDrawableSizeGridPx
            else deviceProfile.overviewTaskIconDrawableSizePx
        setDrawableSize(iconDrawableSize, iconDrawableSize)
    }

    override fun asView(): View = this

    private fun OnLongClickListener.withFeedback(): OnLongClickListener {
        val delegate = this
        return OnLongClickListener { v: View ->
            if (Flags.msdlFeedback()) {
                msdlPlayerWrapper?.playToken(MSDLToken.LONG_PRESS)
            }
            delegate.onLongClick(v)
        }
    }

    companion object {
        private const val NUM_ALPHA_CHANNELS = 3
        private const val INDEX_CONTENT_ALPHA = 0
        private const val INDEX_MODAL_ALPHA = 1
        private const val INDEX_FLEX_SPLIT_ALPHA = 2
    }
}
