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

package com.android.launcher3.apppairs

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.PlaceHolderIconDrawable
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Themes

/**
 * A FrameLayout marking the area on an [AppPairIcon] where the visual icon will be drawn. One of
 * two child UI elements on an [AppPairIcon], along with a BubbleTextView holding the text title.
 */
class AppPairIconGraphic @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    private val TAG = "AppPairIconGraphic"

    companion object {
        // Design specs -- the below ratios are in relation to the size of a standard app icon.
        private const val OUTER_PADDING_SCALE = 1 / 30f
        private const val INNER_PADDING_SCALE = 1 / 24f
        private const val MEMBER_ICON_SCALE = 11 / 30f
        private const val CENTER_CHANNEL_SCALE = 1 / 30f
        private const val BIG_RADIUS_SCALE = 1 / 5f
        private const val SMALL_RADIUS_SCALE = 1 / 15f
        // Disabled alpha is 38%, or 97/255
        private const val DISABLED_ALPHA = 97
        private const val ENABLED_ALPHA = 255
    }

    // App pair icons are slightly smaller than regular icons, so we pad the icon by this much on
    // each side.
    private var outerPadding = 0f
    // Inside of the icon, the two member apps are padded by this much.
    private var innerPadding = 0f
    // The colored background (two rectangles in a square area) is this big.
    private var backgroundSize = 0f
    // The two member apps have icons that are this big (in diameter).
    private var memberIconSize = 0f
    // The size of the center channel.
    var centerChannelSize = 0f
    // The large outer radius of the background rectangles.
    var bigRadius = 0f
    // The small inner radius of the background rectangles.
    var smallRadius = 0f
    // The app pairs icon appears differently in portrait and landscape.
    var isLeftRightSplit = false

    private lateinit var parentIcon: AppPairIcon
    private lateinit var appPairBackground: Drawable
    private var appIcon1: Drawable? = null
    private var appIcon2: Drawable? = null

    fun init(grid: DeviceProfile, icon: AppPairIcon) {
        // Calculate device-specific measurements
        val defaultIconSize = grid.iconSizePx
        outerPadding = OUTER_PADDING_SCALE * defaultIconSize
        innerPadding = INNER_PADDING_SCALE * defaultIconSize
        backgroundSize = defaultIconSize - outerPadding * 2
        memberIconSize = MEMBER_ICON_SCALE * defaultIconSize
        centerChannelSize = CENTER_CHANNEL_SCALE * defaultIconSize
        bigRadius = BIG_RADIUS_SCALE * defaultIconSize
        smallRadius = SMALL_RADIUS_SCALE * defaultIconSize
        isLeftRightSplit = grid.isLeftRightSplit
        parentIcon = icon

        appPairBackground = AppPairIconBackground(context, this)
        appPairBackground.setBounds(0, 0, backgroundSize.toInt(), backgroundSize.toInt())
        applyIcons(parentIcon.info.contents)

        // Center the drawable area in the larger icon canvas
        val lp: LayoutParams = layoutParams as LayoutParams
        lp.gravity = Gravity.CENTER_HORIZONTAL
        lp.topMargin = outerPadding.toInt()
        lp.height = backgroundSize.toInt()
        lp.width = backgroundSize.toInt()
        layoutParams = lp
    }

    /** Sets up app pair member icons for drawing. */
    private fun applyIcons(contents: ArrayList<WorkspaceItemInfo>) {
        // App pair should always contain 2 members; if not 2, return to avoid a crash loop
        if (contents.size != 2) {
            Log.wtf(TAG, "AppPair contents not 2, size: " + contents.size, Throwable())
            return
        }

        // Generate new icons, using themed flag if needed
        val flags = if (Themes.isThemedIconEnabled(context)) BitmapInfo.FLAG_THEMED else 0
        val newIcon1 = parentIcon.info.contents[0].newIcon(context, flags)
        val newIcon2 = parentIcon.info.contents[1].newIcon(context, flags)

        // If app icons did not draw fully last time, animate to full icon
        (appIcon1 as? PlaceHolderIconDrawable)?.animateIconUpdate(newIcon1)
        (appIcon2 as? PlaceHolderIconDrawable)?.animateIconUpdate(newIcon2)

        appIcon1 = newIcon1
        appIcon2 = newIcon2
        appIcon1?.setBounds(0, 0, memberIconSize.toInt(), memberIconSize.toInt())
        appIcon2?.setBounds(0, 0, memberIconSize.toInt(), memberIconSize.toInt())
    }

    /** Gets this icon graphic's bounds, with respect to the parent icon's coordinate system. */
    fun getIconBounds(outBounds: Rect) {
        outBounds.set(0, 0, backgroundSize.toInt(), backgroundSize.toInt())
        outBounds.offset(
            // x-coordinate in parent's coordinate system
            ((parentIcon.width - backgroundSize) / 2).toInt(),
            // y-coordinate in parent's coordinate system
            parentIcon.paddingTop + outerPadding.toInt()
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val drawAlpha =
            if (!parentIcon.isLaunchableAtScreenSize || parentIcon.info.isDisabled) DISABLED_ALPHA
            else ENABLED_ALPHA

        // Draw background
        appPairBackground.alpha = drawAlpha
        appPairBackground.draw(canvas)

        // Make sure icons are loaded and fresh
        applyIcons(parentIcon.info.contents)

        // Draw first icon
        canvas.save()
        // The app icons are placed differently depending on device orientation.
        if (isLeftRightSplit) {
            canvas.translate(innerPadding, height / 2f - memberIconSize / 2f)
        } else {
            canvas.translate(width / 2f - memberIconSize / 2f, innerPadding)
        }
        appIcon1?.alpha = drawAlpha
        appIcon1?.draw(canvas)
        canvas.restore()

        // Draw second icon
        canvas.save()
        // The app icons are placed differently depending on device orientation.
        if (isLeftRightSplit) {
            canvas.translate(
                width - (innerPadding + memberIconSize),
                height / 2f - memberIconSize / 2f
            )
        } else {
            canvas.translate(
                width / 2f - memberIconSize / 2f,
                height - (innerPadding + memberIconSize)
            )
        }
        appIcon2?.alpha = drawAlpha
        appIcon2?.draw(canvas)
        canvas.restore()
    }
}
