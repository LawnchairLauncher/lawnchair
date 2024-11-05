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
package com.android.wm.shell.windowdecor.viewholder

import android.annotation.ColorInt
import android.app.ActivityManager.RunningTaskInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.withStyledAttributes
import androidx.core.view.isVisible
import com.android.internal.R.attr.materialColorOnSecondaryContainer
import com.android.internal.R.attr.materialColorOnSurface
import com.android.internal.R.attr.materialColorSecondaryContainer
import com.android.internal.R.attr.materialColorSurfaceContainerHigh
import com.android.internal.R.attr.materialColorSurfaceContainerLow
import com.android.internal.R.attr.materialColorSurfaceDim
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.windowdecor.MaximizeButtonView
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.OPACITY_100
import com.android.wm.shell.windowdecor.common.OPACITY_11
import com.android.wm.shell.windowdecor.common.OPACITY_15
import com.android.wm.shell.windowdecor.common.OPACITY_55
import com.android.wm.shell.windowdecor.common.OPACITY_65
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.extension.isLightCaptionBarAppearance
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance

/**
 * A desktop mode window decoration used when the window is floating (i.e. freeform). It hosts
 * finer controls such as a close window button and an "app info" section to pull up additional
 * controls.
 */
internal class AppHeaderViewHolder(
        rootView: View,
        onCaptionTouchListener: View.OnTouchListener,
        onCaptionButtonClickListener: View.OnClickListener,
        onLongClickListener: OnLongClickListener,
        onCaptionGenericMotionListener: View.OnGenericMotionListener,
        appName: CharSequence,
        appIconBitmap: Bitmap,
        onMaximizeHoverAnimationFinishedListener: () -> Unit
) : WindowDecorationViewHolder(rootView) {

    private val decorThemeUtil = DecorThemeUtil(context)
    private val lightColors = dynamicLightColorScheme(context)
    private val darkColors = dynamicDarkColorScheme(context)

    /**
     * The corner radius to apply to the app chip, maximize and close button's background drawable.
     **/
    private val headerButtonsRippleRadius = context.resources
        .getDimensionPixelSize(R.dimen.desktop_mode_header_buttons_ripple_radius)

    /**
     * The app chip, maximize and close button's height extends to the top & bottom edges of the
     * header, and their width may be larger than their height. This is by design to increase the
     * clickable and hover-able bounds of the view as much as possible. However, to prevent the
     * ripple drawable from being as large as the views (and asymmetrical), insets are applied to
     * the background ripple drawable itself to give the appearance of a smaller button
     * (with padding between itself and the header edges / sibling buttons) but without affecting
     * its touchable region.
     */
    private val appChipDrawableInsets = DrawableInsets(
        vertical = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_app_chip_ripple_inset_vertical)
    )
    private val maximizeDrawableInsets = DrawableInsets(
        vertical = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_maximize_ripple_inset_vertical),
        horizontal = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_maximize_ripple_inset_horizontal)
    )
    private val closeDrawableInsets = DrawableInsets(
        vertical = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_close_ripple_inset_vertical),
        horizontal = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_close_ripple_inset_horizontal)
    )

    private val captionView: View = rootView.requireViewById(R.id.desktop_mode_caption)
    private val captionHandle: View = rootView.requireViewById(R.id.caption_handle)
    private val openMenuButton: View = rootView.requireViewById(R.id.open_menu_button)
    private val closeWindowButton: ImageButton = rootView.requireViewById(R.id.close_window)
    private val expandMenuButton: ImageButton = rootView.requireViewById(R.id.expand_menu_button)
    private val maximizeButtonView: MaximizeButtonView =
            rootView.requireViewById(R.id.maximize_button_view)
    private val maximizeWindowButton: ImageButton = rootView.requireViewById(R.id.maximize_window)
    private val appNameTextView: TextView = rootView.requireViewById(R.id.application_name)
    private val appIconImageView: ImageView = rootView.requireViewById(R.id.application_icon)
    val appNameTextWidth: Int
        get() = appNameTextView.width

    init {
        captionView.setOnTouchListener(onCaptionTouchListener)
        captionHandle.setOnTouchListener(onCaptionTouchListener)
        openMenuButton.setOnClickListener(onCaptionButtonClickListener)
        openMenuButton.setOnTouchListener(onCaptionTouchListener)
        closeWindowButton.setOnClickListener(onCaptionButtonClickListener)
        maximizeWindowButton.setOnClickListener(onCaptionButtonClickListener)
        maximizeWindowButton.setOnTouchListener(onCaptionTouchListener)
        maximizeWindowButton.setOnGenericMotionListener(onCaptionGenericMotionListener)
        maximizeWindowButton.onLongClickListener = onLongClickListener
        closeWindowButton.setOnTouchListener(onCaptionTouchListener)
        appNameTextView.text = appName
        appIconImageView.setImageBitmap(appIconBitmap)
        maximizeButtonView.onHoverAnimationFinishedListener =
                onMaximizeHoverAnimationFinishedListener
    }

    override fun bindData(taskInfo: RunningTaskInfo) {
        if (Flags.enableThemedAppHeaders()) {
            bindDataWithThemedHeaders(taskInfo)
        } else {
            bindDataLegacy(taskInfo)
        }
    }

    private fun bindDataLegacy(taskInfo: RunningTaskInfo) {
        captionView.setBackgroundColor(getCaptionBackgroundColor(taskInfo))
        val color = getAppNameAndButtonColor(taskInfo)
        val alpha = Color.alpha(color)
        closeWindowButton.imageTintList = ColorStateList.valueOf(color)
        maximizeWindowButton.imageTintList = ColorStateList.valueOf(color)
        expandMenuButton.imageTintList = ColorStateList.valueOf(color)
        appNameTextView.isVisible = !taskInfo.isTransparentCaptionBarAppearance
        appNameTextView.setTextColor(color)
        appIconImageView.imageAlpha = alpha
        maximizeWindowButton.imageAlpha = alpha
        closeWindowButton.imageAlpha = alpha
        expandMenuButton.imageAlpha = alpha
        context.withStyledAttributes(
            set = null,
            attrs = intArrayOf(
                android.R.attr.selectableItemBackground,
                android.R.attr.selectableItemBackgroundBorderless
            ),
            defStyleAttr = 0,
            defStyleRes = 0
        ) {
            openMenuButton.background = getDrawable(0)
            maximizeWindowButton.background = getDrawable(1)
            closeWindowButton.background = getDrawable(1)
        }
        maximizeButtonView.setAnimationTints(isDarkMode())
    }

    private fun bindDataWithThemedHeaders(taskInfo: RunningTaskInfo) {
        val header = fillHeaderInfo(taskInfo)
        val headerStyle = getHeaderStyle(header)

        // Caption Background
        when (headerStyle.background) {
            is HeaderStyle.Background.Opaque -> {
                captionView.setBackgroundColor(headerStyle.background.color)
            }
            HeaderStyle.Background.Transparent -> {
                captionView.setBackgroundColor(Color.TRANSPARENT)
            }
        }

        // Caption Foreground
        val foregroundColor = headerStyle.foreground.color
        val foregroundAlpha = headerStyle.foreground.opacity
        val colorStateList = ColorStateList.valueOf(foregroundColor).withAlpha(foregroundAlpha)
        // App chip.
        openMenuButton.apply {
            background = createRippleDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = appChipDrawableInsets,
            )
            expandMenuButton.imageTintList = colorStateList
            appNameTextView.apply {
                isVisible = header.type == Header.Type.DEFAULT
                setTextColor(colorStateList)
            }
            appIconImageView.imageAlpha = foregroundAlpha
        }
        // Maximize button.
        maximizeButtonView.setAnimationTints(
            darkMode = header.appTheme == Theme.DARK,
            iconForegroundColor = colorStateList,
            baseForegroundColor = foregroundColor,
            rippleDrawable = createRippleDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = maximizeDrawableInsets
            )
        )
        // Close button.
        closeWindowButton.apply {
            imageTintList = colorStateList
            background = createRippleDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = closeDrawableInsets
            )
        }
    }

    override fun onHandleMenuOpened() {}

    override fun onHandleMenuClosed() {}

    fun setAnimatingTaskResize(animatingTaskResize: Boolean) {
        // If animating a task resize, cancel any running hover animations
        if (animatingTaskResize) {
            maximizeButtonView.cancelHoverAnimation()
        }
        maximizeButtonView.hoverDisabled = animatingTaskResize
    }

    fun onMaximizeWindowHoverExit() {
        maximizeButtonView.cancelHoverAnimation()
    }

    fun onMaximizeWindowHoverEnter() {
        maximizeButtonView.startHoverAnimation()
    }

    private fun getHeaderStyle(header: Header): HeaderStyle {
        return HeaderStyle(
            background = getHeaderBackground(header),
            foreground = getHeaderForeground(header)
        )
    }

    private fun getHeaderBackground(header: Header): HeaderStyle.Background {
        return when (header.type) {
            Header.Type.DEFAULT -> {
                when (header.appTheme) {
                    Theme.LIGHT -> {
                        if (header.isFocused) {
                            HeaderStyle.Background.Opaque(lightColors.secondaryContainer.toArgb())
                        } else {
                            HeaderStyle.Background.Opaque(lightColors.surfaceContainerLow.toArgb())
                        }
                    }
                    Theme.DARK -> {
                        if (header.isFocused) {
                            HeaderStyle.Background.Opaque(darkColors.surfaceContainerHigh.toArgb())
                        } else {
                            HeaderStyle.Background.Opaque(darkColors.surfaceDim.toArgb())
                        }
                    }
                }
            }
            Header.Type.CUSTOM -> HeaderStyle.Background.Transparent
        }
    }

    private fun getHeaderForeground(header: Header): HeaderStyle.Foreground {
        return when (header.type) {
            Header.Type.DEFAULT -> {
                when (header.appTheme) {
                    Theme.LIGHT -> {
                        if (header.isFocused) {
                            HeaderStyle.Foreground(
                                color = lightColors.onSecondaryContainer.toArgb(),
                                opacity = OPACITY_100
                            )
                        } else {
                            HeaderStyle.Foreground(
                                color = lightColors.onSecondaryContainer.toArgb(),
                                opacity = OPACITY_65
                            )
                        }
                    }
                    Theme.DARK -> {
                        if (header.isFocused) {
                            HeaderStyle.Foreground(
                                color = darkColors.onSurface.toArgb(),
                                opacity = OPACITY_100
                            )
                        } else {
                            HeaderStyle.Foreground(
                                color = darkColors.onSurface.toArgb(),
                                opacity = OPACITY_55
                            )
                        }
                    }
                }
            }
            Header.Type.CUSTOM -> when {
                header.isAppearanceCaptionLight && header.isFocused -> {
                    HeaderStyle.Foreground(
                        color = lightColors.onSecondaryContainer.toArgb(),
                        opacity = OPACITY_100
                    )
                }
                header.isAppearanceCaptionLight && !header.isFocused -> {
                    HeaderStyle.Foreground(
                        color = lightColors.onSecondaryContainer.toArgb(),
                        opacity = OPACITY_65
                    )
                }
                !header.isAppearanceCaptionLight && header.isFocused -> {
                    HeaderStyle.Foreground(
                        color = darkColors.onSurface.toArgb(),
                        opacity = OPACITY_100
                    )
                }
                !header.isAppearanceCaptionLight && !header.isFocused -> {
                    HeaderStyle.Foreground(
                        color = darkColors.onSurface.toArgb(),
                        opacity = OPACITY_55
                    )
                }
                else -> error("No other combination expected header=$header")
            }
        }
    }

    private fun fillHeaderInfo(taskInfo: RunningTaskInfo): Header {
        return Header(
            type = if (taskInfo.isTransparentCaptionBarAppearance) {
                Header.Type.CUSTOM
            } else {
                Header.Type.DEFAULT
            },
            appTheme = decorThemeUtil.getAppTheme(taskInfo),
            isFocused = taskInfo.isFocused,
            isAppearanceCaptionLight = taskInfo.isLightCaptionBarAppearance
        )
    }

    @ColorInt
    private fun replaceColorAlpha(@ColorInt color: Int, alpha: Int): Int {
        return Color.argb(
            alpha,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun createRippleDrawable(
        @ColorInt color: Int,
        cornerRadius: Int,
        drawableInsets: DrawableInsets,
    ): RippleDrawable {
        return RippleDrawable(
            ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_hovered),
                    intArrayOf(android.R.attr.state_pressed),
                    intArrayOf(),
                ),
                intArrayOf(
                    replaceColorAlpha(color, OPACITY_11),
                    replaceColorAlpha(color, OPACITY_15),
                    Color.TRANSPARENT
                )
            ),
            null /* content */,
            LayerDrawable(arrayOf(
                ShapeDrawable().apply {
                    shape = RoundRectShape(
                        FloatArray(8) { cornerRadius.toFloat() },
                        null /* inset */,
                        null /* innerRadii */
                    )
                    paint.color = Color.WHITE
                }
            )).apply {
                require(numberOfLayers == 1) { "Must only contain one layer" }
                setLayerInset(0 /* index */,
                    drawableInsets.l, drawableInsets.t, drawableInsets.r, drawableInsets.b)
            }
        )
    }

    private data class DrawableInsets(val l: Int, val t: Int, val r: Int, val b: Int) {
        constructor(vertical: Int = 0, horizontal: Int = 0) :
                this(horizontal, vertical, horizontal, vertical)
    }

    private data class Header(
        val type: Type,
        val appTheme: Theme,
        val isFocused: Boolean,
        val isAppearanceCaptionLight: Boolean,
    ) {
        enum class Type { DEFAULT, CUSTOM }
    }

    private data class HeaderStyle(
        val background: Background,
        val foreground: Foreground
    ) {
        data class Foreground(
            @ColorInt val color: Int,
            val opacity: Int
        )

        sealed class Background {
            data object Transparent : Background()
            data class Opaque(@ColorInt val color: Int) : Background()
        }
    }

    @ColorInt
    private fun getCaptionBackgroundColor(taskInfo: RunningTaskInfo): Int {
        if (taskInfo.isTransparentCaptionBarAppearance) {
            return Color.TRANSPARENT
        }
        val materialColorAttr: Int =
            if (isDarkMode()) {
                if (!taskInfo.isFocused) {
                    materialColorSurfaceContainerHigh
                } else {
                    materialColorSurfaceDim
                }
            } else {
                if (!taskInfo.isFocused) {
                    materialColorSurfaceContainerLow
                } else {
                    materialColorSecondaryContainer
                }
        }
        context.withStyledAttributes(null, intArrayOf(materialColorAttr), 0, 0) {
            return getColor(0, 0)
        }
        return 0
    }

    @ColorInt
    private fun getAppNameAndButtonColor(taskInfo: RunningTaskInfo): Int {
        val materialColorAttr = when {
            taskInfo.isTransparentCaptionBarAppearance &&
                    taskInfo.isLightCaptionBarAppearance -> materialColorOnSecondaryContainer
            taskInfo.isTransparentCaptionBarAppearance &&
                    !taskInfo.isLightCaptionBarAppearance -> materialColorOnSurface
            isDarkMode() -> materialColorOnSurface
            else -> materialColorOnSecondaryContainer
        }
        val appDetailsOpacity = when {
            isDarkMode() && !taskInfo.isFocused -> DARK_THEME_UNFOCUSED_OPACITY
            !isDarkMode() && !taskInfo.isFocused -> LIGHT_THEME_UNFOCUSED_OPACITY
            else -> FOCUSED_OPACITY
        }
        context.withStyledAttributes(null, intArrayOf(materialColorAttr), 0, 0) {
            val color = getColor(0, 0)
            return if (appDetailsOpacity == FOCUSED_OPACITY) {
                color
            } else {
                Color.argb(
                    appDetailsOpacity,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )
            }
        }
        return 0
    }

    private fun isDarkMode(): Boolean {
        return context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val TAG = "DesktopModeAppControlsWindowDecorationViewHolder"

        private const val DARK_THEME_UNFOCUSED_OPACITY = 140 // 55%
        private const val LIGHT_THEME_UNFOCUSED_OPACITY = 166 // 65%
        private const val FOCUSED_OPACITY = 255
    }
}
