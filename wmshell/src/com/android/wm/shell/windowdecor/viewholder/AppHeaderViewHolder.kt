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
import android.annotation.DrawableRes
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.android.internal.R.color.materialColorOnSecondaryContainer
import com.android.internal.R.color.materialColorOnSurface
import com.android.internal.R.color.materialColorSecondaryContainer
import com.android.internal.R.color.materialColorSurfaceContainerHigh
import com.android.internal.R.color.materialColorSurfaceContainerLow
import com.android.internal.R.color.materialColorSurfaceDim
import com.android.wm.shell.R
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_MAXIMIZE_RESTORE
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_RESIZE_LEFT
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_ACTION_RESIZE_RIGHT
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_WINDOW_CLOSE_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_WINDOW_MAXIMIZE_RESTORE_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.A11Y_APP_WINDOW_MINIMIZE_BUTTON
import com.android.wm.shell.windowdecor.MaximizeButtonView
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.DrawableInsets
import com.android.wm.shell.windowdecor.common.OPACITY_100
import com.android.wm.shell.windowdecor.common.OPACITY_55
import com.android.wm.shell.windowdecor.common.OPACITY_65
import com.android.wm.shell.windowdecor.common.Theme
import com.android.wm.shell.windowdecor.common.createBackgroundDrawable
import com.android.wm.shell.windowdecor.extension.isLightCaptionBarAppearance
import com.android.wm.shell.windowdecor.extension.isTransparentCaptionBarAppearance

/**
 * A desktop mode window decoration used when the window is floating (i.e. freeform). It hosts
 * finer controls such as a close window button and an "app info" section to pull up additional
 * controls.
 */
class AppHeaderViewHolder(
    appHeaderView: View?,
    private val context: Context,
    windowDecorationActions: WindowDecorationActions,
    onCaptionTouchListener: View.OnTouchListener,
    onCaptionButtonClickListener: View.OnClickListener,
    private val onLongClickListener: OnLongClickListener,
    onCaptionGenericMotionListener: View.OnGenericMotionListener,
    onMaximizeHoverAnimationFinishedListener: () -> Unit,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
) : WindowDecorationViewHolder<AppHeaderViewHolder.HeaderData>() {

    data class HeaderData(
        val taskInfo: RunningTaskInfo,
        val isTaskMaximized: Boolean,
        val inFullImmersiveState: Boolean,
        val hasGlobalFocus: Boolean,
        val enableMaximizeLongClick: Boolean,
        val isCaptionVisible: Boolean,
    ) : Data()

    private val decorThemeUtil = DecorThemeUtil(context)
    private val lightColors = dynamicLightColorScheme(context)
    private val darkColors = dynamicDarkColorScheme(context)

    /**
     * The corner radius to apply to the app chip, maximize and close button's background drawable.
     **/
    private val headerButtonsRippleRadius = context.resources
        .getDimensionPixelSize(R.dimen.desktop_mode_header_buttons_ripple_radius)

    /**
     * The max width of the app name shown on the app header.
     **/
    private val appNameMaxWidth = context.resources
        .getDimensionPixelSize(R.dimen.desktop_mode_header_app_name_max_width)

    /**
     * The width of the expand menu error image on the app header.
     **/
    private val expandMenuErrorImageWidth = context.resources
        .getDimensionPixelSize(R.dimen.desktop_mode_header_expand_menu_error_image_width)

    /**
     * The margin added between app name and expand menu error image on the app header.
     **/
    private val expandMenuErrorImageMargin = context.resources
        .getDimensionPixelSize(R.dimen.desktop_mode_header_expand_menu_error_image_margin)

    /**
     * The app chip, minimize, maximize and close button's height extends to the top & bottom edges
     * of the header, and their width may be larger than their height. This is by design to increase
     * the clickable and hover-able bounds of the view as much as possible. However, to prevent the
     * ripple drawable from being as large as the views (and asymmetrical), insets are applied to
     * the background ripple drawable itself to give the appearance of a smaller button
     * (with padding between itself and the header edges / sibling buttons) but without affecting
     * its touchable region.
     */
    private val appChipDrawableInsets = DrawableInsets(
        vertical = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_app_chip_ripple_inset_vertical)
    )
    private val minimizeDrawableInsets = DrawableInsets(
        vertical = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_minimize_ripple_inset_vertical),
        horizontal = context.resources
            .getDimensionPixelSize(R.dimen.desktop_mode_header_minimize_ripple_inset_horizontal)
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

    override val rootView =
        appHeaderView ?: if (DesktopExperienceFlags.ENABLE_WINDOW_DECORATION_REFACTOR.isTrue) {
            LayoutInflater.from(context)
            .inflate(R.layout.desktop_mode_app_header, null) as WindowDecorLinearLayout
    } else {
        error("App Header root view should not be null")
    }
    private val captionView: View = rootView.requireViewById(R.id.desktop_mode_caption)
    private val captionHandle: View = rootView.requireViewById(R.id.caption_handle)
    private val openMenuButton: View = rootView.requireViewById(R.id.open_menu_button)
    private val closeWindowButton: ImageButton = rootView.requireViewById(R.id.close_window)
    private val expandMenuButton: ImageButton = rootView.requireViewById(R.id.expand_menu_button)
    private val maximizeButtonView: MaximizeButtonView =
        rootView.requireViewById(R.id.maximize_button_view)
    private val maximizeWindowButton: ImageButton = rootView.requireViewById(R.id.maximize_window)
    private val minimizeWindowButton: ImageButton = rootView.requireViewById(R.id.minimize_window)
    private val appNameTextView: TextView = rootView.requireViewById(R.id.application_name)
    private val appIconImageView: ImageView = rootView.requireViewById(R.id.application_icon)
    private val expandMenuErrorImageView: ImageView =
        rootView.requireViewById(R.id.expand_menu_error)

    val appNameTextWidth: Int
        get() = appNameTextView.width

    private val a11yAnnounceTextMaximize: String =
        context.getString(R.string.app_header_talkback_action_maximize_button_text)
    private val a11yAnnounceTextRestore: String =
        context.getString(R.string.app_header_talkback_action_restore_button_text)

    private val a11yAnnounceTextOpening: String =
        context.getString(R.string.desktop_mode_talkback_state_opening)
    private val a11yAnnounceTextMinimizing: String =
        context.getString(R.string.desktop_mode_talkback_state_minimizing)
    private val a11yAnnounceTextClosing: String =
        context.getString(R.string.desktop_mode_talkback_state_closing)
    private lateinit var a11yAnnounceTextFocused: String
    private lateinit var a11yAnnounceTextNotFocused: String

    private lateinit var sizeToggleDirection: SizeToggleDirection
    private lateinit var a11yTextMaximize: String
    private lateinit var a11yTextRestore: String
    private lateinit var currentTaskInfo: RunningTaskInfo

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
        minimizeWindowButton.setOnClickListener(onCaptionButtonClickListener)
        minimizeWindowButton.setOnTouchListener(onCaptionTouchListener)
        maximizeButtonView.onHoverAnimationFinishedListener =
                onMaximizeHoverAnimationFinishedListener

        val a11yActionSnapLeft = AccessibilityAction(
            R.id.action_snap_left,
            context.getString(R.string.desktop_mode_a11y_action_snap_left)
        )
        val a11yActionSnapRight = AccessibilityAction(
            R.id.action_snap_right,
            context.getString(R.string.desktop_mode_a11y_action_snap_right)
        )
        val a11yActionMaximizeRestore = AccessibilityAction(
            R.id.action_maximize_restore,
            context.getString(R.string.desktop_mode_a11y_action_maximize_restore)
        )

        captionHandle.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(a11yActionSnapLeft)
                info.addAction(a11yActionSnapRight)
                info.addAction(a11yActionMaximizeRestore)
                info.liveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                info.isScreenReaderFocusable = false
            }

            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
            ): Boolean {
                when (action) {
                    R.id.action_snap_left -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_LEFT)
                        windowDecorationActions.onLeftSnap(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                    R.id.action_snap_right -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_RIGHT)
                        windowDecorationActions.onRightSnap(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                    R.id.action_maximize_restore -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_MAXIMIZE_RESTORE)
                        windowDecorationActions.onMaximizeOrRestore(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                }

                return super.performAccessibilityAction(host, action, args)
            }
        }
        maximizeWindowButton.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(AccessibilityAction.ACTION_CLICK)
                info.addAction(a11yActionSnapLeft)
                info.addAction(a11yActionSnapRight)
                info.addAction(a11yActionMaximizeRestore)
                host.isClickable = true
            }

            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
            ): Boolean {
                when (action) {
                    AccessibilityAction.ACTION_CLICK.id -> {
                        desktopModeUiEventLogger.log(
                            currentTaskInfo, A11Y_APP_WINDOW_MAXIMIZE_RESTORE_BUTTON
                        )
                        host.performClick()
                    }
                    R.id.action_snap_left -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_LEFT)
                        windowDecorationActions.onLeftSnap(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                    R.id.action_snap_right -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_RESIZE_RIGHT)
                        windowDecorationActions.onRightSnap(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                    R.id.action_maximize_restore -> {
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_ACTION_MAXIMIZE_RESTORE)
                        windowDecorationActions.onMaximizeOrRestore(
                            currentTaskInfo.taskId,
                            InputMethod.ACCESSIBILITY
                        )
                    }
                }

                return super.performAccessibilityAction(host, action, args)
            }
        }

        closeWindowButton.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
            ): Boolean {
                when (action) {
                    AccessibilityAction.ACTION_CLICK.id -> {
                        captionHandle.stateDescription = a11yAnnounceTextClosing
                        desktopModeUiEventLogger.log(currentTaskInfo, A11Y_APP_WINDOW_CLOSE_BUTTON)
                    }
                }

                return super.performAccessibilityAction(host, action, args)
            }
        }

        minimizeWindowButton.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: Bundle?
            ): Boolean {
                when (action) {
                    AccessibilityAction.ACTION_CLICK.id -> {
                        captionHandle.stateDescription = a11yAnnounceTextMinimizing
                        desktopModeUiEventLogger.log(
                            currentTaskInfo, A11Y_APP_WINDOW_MINIMIZE_BUTTON
                        )
                    }
                }

                return super.performAccessibilityAction(host, action, args)
            }
        }

        // Update a11y announcement to say "double tap to open menu"
        ViewCompat.replaceAccessibilityAction(
            openMenuButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_handle_chip_accessibility_announce),
            null
        )

        // Update a11y announcement to say "double tap to minimize app window"
        ViewCompat.replaceAccessibilityAction(
            minimizeWindowButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_header_talkback_action_minimize_button_text),
            null
        )

        // Update a11y announcement to say "double tap to close app window"
        ViewCompat.replaceAccessibilityAction(
            closeWindowButton,
            AccessibilityActionCompat.ACTION_CLICK,
            context.getString(R.string.app_header_talkback_action_close_button_text),
            null
        )
    }

    override fun bindData(data: HeaderData) {
        bindData(
            data.taskInfo,
            data.isTaskMaximized,
            data.inFullImmersiveState,
            data.hasGlobalFocus,
            data.enableMaximizeLongClick,
            data.isCaptionVisible,
        )
    }

    /** Announces app window name as "focused" via Talkback */
    fun a11yAnnounceFocused() {
        captionHandle.stateDescription = a11yAnnounceTextFocused
    }

    /** Sets the app's name in the header. */
    fun setAppName(name: CharSequence) {
        appNameTextView.text = name
        populateA11yStrings(name)

        updateMaximizeButtonContentDescription()
    }

    /** Populates string variables from string templates which rely on app name */
    private fun populateA11yStrings(name: CharSequence) {
        openMenuButton.contentDescription =
            context.getString(R.string.desktop_mode_app_header_chip_text, name)

        closeWindowButton.contentDescription = context.getString(R.string.close_button_text, name)
        minimizeWindowButton.contentDescription =
            context.getString(R.string.minimize_button_text, name)

        a11yTextMaximize = context.getString(R.string.maximize_button_text, name)
        a11yTextRestore = context.getString(R.string.restore_button_text, name)
        a11yAnnounceTextFocused =
            context.getString(R.string.desktop_mode_talkback_state_focused, name)
        a11yAnnounceTextNotFocused =
            context.getString(R.string.desktop_mode_talkback_state_not_focused, name)
    }

    private fun updateMaximizeButtonContentDescription() {
        if (this::a11yTextRestore.isInitialized &&
            this::a11yTextMaximize.isInitialized &&
            this::sizeToggleDirection.isInitialized) {
            maximizeWindowButton.contentDescription = when (sizeToggleDirection) {
                SizeToggleDirection.MAXIMIZE -> a11yTextMaximize
                SizeToggleDirection.RESTORE -> a11yTextRestore
            }
        }
    }

    /** Sets the app's icon in the header. */
    fun setAppIcon(icon: Bitmap) {
        appIconImageView.setImageBitmap(icon)
    }

    private fun bindData(
        taskInfo: RunningTaskInfo,
        isTaskMaximized: Boolean,
        inFullImmersiveState: Boolean,
        hasGlobalFocus: Boolean,
        enableMaximizeLongClick: Boolean,
        isCaptionVisible: Boolean,
    ) {
        currentTaskInfo = taskInfo
        if (DesktopModeFlags.ENABLE_THEMED_APP_HEADERS.isTrue) {
            bindDataWithThemedHeaders(
                taskInfo,
                isTaskMaximized,
                inFullImmersiveState,
                hasGlobalFocus,
                enableMaximizeLongClick,
                isCaptionVisible,
            )
        } else {
            bindDataLegacy(taskInfo, hasGlobalFocus, isCaptionVisible)
        }
    }

    private fun bindDataLegacy(
        taskInfo: RunningTaskInfo,
        hasGlobalFocus: Boolean,
        isCaptionVisible: Boolean,
    ) {
        if (DesktopModeFlags.ENABLE_DESKTOP_APP_HANDLE_ANIMATION.isTrue()) {
            setCaptionVisibility(isCaptionVisible)
        }
        captionView.setBackgroundColor(getCaptionBackgroundColor(taskInfo, hasGlobalFocus))
        val color = getAppNameAndButtonColor(taskInfo, hasGlobalFocus)
        val alpha = Color.alpha(color)
        closeWindowButton.imageTintList = ColorStateList.valueOf(color)
        maximizeWindowButton.imageTintList = ColorStateList.valueOf(color)
        minimizeWindowButton.imageTintList = ColorStateList.valueOf(color)
        expandMenuButton.imageTintList = ColorStateList.valueOf(color)
        appNameTextView.isVisible = !taskInfo.isTransparentCaptionBarAppearance
        appNameTextView.setTextColor(color)
        appIconImageView.imageAlpha = alpha
        maximizeWindowButton.imageAlpha = alpha
        minimizeWindowButton.imageAlpha = alpha
        closeWindowButton.imageAlpha = alpha
        expandMenuButton.imageAlpha = alpha
        expandMenuErrorImageView.imageAlpha = alpha
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
            minimizeWindowButton.background = getDrawable(1)
        }
        maximizeButtonView.setAnimationTints(isDarkMode())
        minimizeWindowButton.isGone = !DesktopModeFlags.ENABLE_MINIMIZE_BUTTON.isTrue
    }

    private fun bindDataWithThemedHeaders(
        taskInfo: RunningTaskInfo,
        isTaskMaximized: Boolean,
        inFullImmersiveState: Boolean,
        hasGlobalFocus: Boolean,
        enableMaximizeLongClick: Boolean,
        isCaptionVisible: Boolean,
    ) {
        val header = fillHeaderInfo(taskInfo, hasGlobalFocus)
        val headerStyle = getHeaderStyle(header)

        if (DesktopModeFlags.ENABLE_DESKTOP_APP_HANDLE_ANIMATION.isTrue()) {
            setCaptionVisibility(isCaptionVisible)
        }

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
            background = createBackgroundDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = appChipDrawableInsets,
            )
            expandMenuButton.imageTintList = colorStateList
            expandMenuErrorImageView.visibility =
                if (currentTaskInfo.appCompatTaskInfo.isRestartMenuEnabledForDisplayMove)
                    View.VISIBLE else View.GONE
            appNameTextView.apply {
                isVisible = header.type == Header.Type.DEFAULT
                setTextColor(colorStateList)
                maxWidth = if (currentTaskInfo.appCompatTaskInfo.isRestartMenuEnabledForDisplayMove)
                    appNameMaxWidth - expandMenuErrorImageWidth - expandMenuErrorImageMargin else appNameMaxWidth
            }
            appIconImageView.imageAlpha = foregroundAlpha
            defaultFocusHighlightEnabled = false
        }
        // Minimize button.
        minimizeWindowButton.apply {
            imageTintList = colorStateList
            background = createBackgroundDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = minimizeDrawableInsets
            )
        }
        minimizeWindowButton.isGone = !DesktopModeFlags.ENABLE_MINIMIZE_BUTTON.isTrue
        // Maximize button.
        maximizeButtonView.apply {
            setAnimationTints(
                darkMode = header.appTheme == Theme.DARK,
                iconForegroundColor = colorStateList,
                baseForegroundColor = foregroundColor,
                backgroundDrawable = createBackgroundDrawable(
                    color = foregroundColor,
                    cornerRadius = headerButtonsRippleRadius,
                    drawableInsets = maximizeDrawableInsets
                )
            )
            val icon = getMaximizeButtonIcon(isTaskMaximized, inFullImmersiveState)
            setIcon(icon)

            when (icon) {
                R.drawable.decor_desktop_mode_immersive_or_maximize_exit_button_dark -> {
                    sizeToggleDirection = SizeToggleDirection.RESTORE

                    // Update a11y announcement to say "double tap to maximize app window size"
                    ViewCompat.replaceAccessibilityAction(
                        maximizeWindowButton,
                        AccessibilityActionCompat.ACTION_CLICK,
                        a11yAnnounceTextRestore,
                        null
                    )
                }
                R.drawable.decor_desktop_mode_maximize_button_dark -> {
                    sizeToggleDirection = SizeToggleDirection.MAXIMIZE

                    // Update a11y announcement to say "double tap to restore app window size"
                    ViewCompat.replaceAccessibilityAction(
                        maximizeWindowButton,
                        AccessibilityActionCompat.ACTION_CLICK,
                        a11yAnnounceTextMaximize,
                        null
                    )
                }
            }
            updateMaximizeButtonContentDescription()
        }
        // Close button.
        closeWindowButton.apply {
            imageTintList = colorStateList
            background = createBackgroundDrawable(
                color = foregroundColor,
                cornerRadius = headerButtonsRippleRadius,
                drawableInsets = closeDrawableInsets
            )
        }
        if (!enableMaximizeLongClick) {
            maximizeButtonView.cancelHoverAnimation()
        }
        maximizeButtonView.hoverDisabled = !enableMaximizeLongClick
        maximizeWindowButton.onLongClickListener = if (enableMaximizeLongClick) {
            onLongClickListener
        } else {
            // Disable long-click to open maximize menu when in immersive.
            null
        }
    }

    private fun setCaptionVisibility(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        captionView.visibility = v
    }

    override fun onHandleMenuOpened() {}

    override fun onHandleMenuClosed() {}

    fun onMaximizeWindowHoverExit() {
        maximizeButtonView.cancelHoverAnimation()
    }

    fun onMaximizeWindowHoverEnter() {
        maximizeButtonView.startHoverAnimation()
    }

    fun runOnAppChipGlobalLayout(runnable: () -> Unit) {
        // Wait for app chip to be inflated before notifying repository.
        openMenuButton.viewTreeObserver.addOnGlobalLayoutListener(object :
            OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                runnable()
                openMenuButton.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    fun getAppChipLocationInWindow(): Rect {
        val appChipBoundsInWindow = IntArray(2)
        openMenuButton.getLocationInWindow(appChipBoundsInWindow)

        return Rect(
            /* left = */ appChipBoundsInWindow[0],
            /* top = */ appChipBoundsInWindow[1],
            /* right = */ appChipBoundsInWindow[0] + openMenuButton.width,
            /* bottom = */ appChipBoundsInWindow[1] + openMenuButton.height
        )
    }

    fun requestAccessibilityFocus() {
        maximizeWindowButton.post {
            maximizeWindowButton.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        }
    }

    @DrawableRes
    private fun getMaximizeButtonIcon(
        isTaskMaximized: Boolean,
        inFullImmersiveState: Boolean
    ): Int = when {
        shouldShowExitFullImmersiveOrMaximizeIcon(isTaskMaximized, inFullImmersiveState) -> {
            R.drawable.decor_desktop_mode_immersive_or_maximize_exit_button_dark
        }
        else -> R.drawable.decor_desktop_mode_maximize_button_dark
    }

    private fun shouldShowExitFullImmersiveOrMaximizeIcon(
        isTaskMaximized: Boolean,
        inFullImmersiveState: Boolean
    ): Boolean = (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue && inFullImmersiveState)
            || isTaskMaximized

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

    private fun fillHeaderInfo(taskInfo: RunningTaskInfo, hasGlobalFocus: Boolean): Header {
        return Header(
            type = if (taskInfo.isTransparentCaptionBarAppearance) {
                Header.Type.CUSTOM
            } else {
                Header.Type.DEFAULT
            },
            appTheme = decorThemeUtil.getAppTheme(taskInfo),
            isFocused = hasGlobalFocus,
            isAppearanceCaptionLight = taskInfo.isLightCaptionBarAppearance
        )
    }

    private enum class SizeToggleDirection {
        MAXIMIZE, RESTORE
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
    private fun getCaptionBackgroundColor(taskInfo: RunningTaskInfo, hasGlobalFocus: Boolean): Int {
        if (taskInfo.isTransparentCaptionBarAppearance) {
            return Color.TRANSPARENT
        }
        val materialColorAttr: Int =
            if (isDarkMode()) {
                if (!hasGlobalFocus) {
                    materialColorSurfaceContainerHigh
                } else {
                    materialColorSurfaceDim
                }
            } else {
                if (!hasGlobalFocus) {
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
    private fun getAppNameAndButtonColor(taskInfo: RunningTaskInfo, hasGlobalFocus: Boolean): Int {
        val materialColor = context.getColor(when {
            taskInfo.isTransparentCaptionBarAppearance &&
                    taskInfo.isLightCaptionBarAppearance -> materialColorOnSecondaryContainer
            taskInfo.isTransparentCaptionBarAppearance &&
                    !taskInfo.isLightCaptionBarAppearance -> materialColorOnSurface
            isDarkMode() -> materialColorOnSurface
            else -> materialColorOnSecondaryContainer
        })
        val appDetailsOpacity = when {
            isDarkMode() && !hasGlobalFocus -> DARK_THEME_UNFOCUSED_OPACITY
            !isDarkMode() && !hasGlobalFocus -> LIGHT_THEME_UNFOCUSED_OPACITY
            else -> FOCUSED_OPACITY
        }


        return if (appDetailsOpacity == FOCUSED_OPACITY) {
            materialColor
        } else {
            Color.argb(
                appDetailsOpacity,
                Color.red(materialColor),
                Color.green(materialColor),
                Color.blue(materialColor)
            )
        }
    }

    private fun isDarkMode(): Boolean {
        return context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
    }

    override fun setTaskFocusState(taskFocusState: Boolean) {
        (rootView as WindowDecorLinearLayout).setTaskFocusState(taskFocusState)
    }

    override fun close() {
        // Should not fire long press events after closing the window decoration.
        maximizeWindowButton.cancelLongPress()
    }

    companion object {
        private const val TAG = "DesktopModeAppControlsWindowDecorationViewHolder"

        private const val DARK_THEME_UNFOCUSED_OPACITY = 140 // 55%
        private const val LIGHT_THEME_UNFOCUSED_OPACITY = 166 // 65%
        private const val FOCUSED_OPACITY = 255
    }

    class Factory {
        fun create(
            rootView: View?,
            context: Context,
            windowDecorationActions: WindowDecorationActions,
            onCaptionTouchListener: View.OnTouchListener,
            onCaptionButtonClickListener: View.OnClickListener,
            onLongClickListener: OnLongClickListener,
            onCaptionGenericMotionListener: View.OnGenericMotionListener,
            onMaximizeHoverAnimationFinishedListener: () -> Unit,
            desktopModeUiEventLogger: DesktopModeUiEventLogger
        ): AppHeaderViewHolder = AppHeaderViewHolder(
            rootView,
            context,
            windowDecorationActions,
            onCaptionTouchListener,
            onCaptionButtonClickListener,
            onLongClickListener,
            onCaptionGenericMotionListener,
            onMaximizeHoverAnimationFinishedListener,
            desktopModeUiEventLogger,
        )
    }
}
