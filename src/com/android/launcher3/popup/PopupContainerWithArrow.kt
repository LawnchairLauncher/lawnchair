/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.popup

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.LayoutRes
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Flags
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfoWithIcon
import com.android.launcher3.shortcuts.DeepShortcutTextView
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.util.Executors
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.ActivityContext
import java.util.Optional
import java.util.stream.Collectors
import kotlin.math.max

/**
 * A container for shortcuts to deep links associated with an app.
 *
 * @param <T> The activity on with the popup shows </T>
 */
class PopupContainerWithArrow<T>
private constructor(
    context: Context?,
    originalView: View,
    itemInfo: ItemInfo,
    private val updateIconUi: Boolean,
) :
    PopupContainer<T>(context, originalView, itemInfo),
    DragSource,
    DragController.DragListener,
    Popup where T : Context, T : ActivityContext {

    private val deepShortcuts: MutableList<DeepShortcutView> = ArrayList()
    private val interceptTouchDown = PointF()
    private val shortcutHeight: Float =
        resources.getDimension(R.dimen.system_shortcut_header_height)

    val itemClickListener: OnClickListener
        get() = OnClickListener { view: View? ->
            mActivityContext?.itemOnClickListener?.onClick(view)
        }

    private var containerWidth: Int = resources.getDimensionPixelSize(R.dimen.bg_popup_item_width)
    private var deepShortcutContainer: ViewGroup? = null
    private var currentHeight = 0f

    val originalIcon = originalView as BubbleTextView

    var itemDragHandler: PopupItemDragHandler? = null
        private set

    var widgetContainer: ViewGroup? = null

    override fun getAccessibilityInitialFocusView(): View {
        return systemShortcutContainer?.getChildAt(0) ?: super.getAccessibilityInitialFocusView()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            interceptTouchDown[ev.x] = ev.y
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
        return (Utilities.squaredHypot(interceptTouchDown.x - ev.x, interceptTouchDown.y - ev.y) >
            Utilities.squaredTouchSlop(context))
    }

    fun setPopupItemDragHandler(popupItemDragHandler: PopupItemDragHandler?) {
        itemDragHandler = popupItemDragHandler
    }

    fun configureForLauncher(launcher: Launcher, itemInfo: ItemInfo) {
        addOnAttachStateChangeListener(
            LauncherPopupLiveUpdateHandler(launcher, this as PopupContainerWithArrow<Launcher>)
        )
        if (
            !Flags.privateSpaceRestrictItemDrag() ||
                (itemInfo !is ItemInfoWithIcon) ||
                (itemInfo.runtimeStatusFlags and ItemInfoWithIcon.FLAG_NOT_PINNABLE) == 0
        ) {
            itemDragHandler = LauncherPopupItemDragHandler(launcher, this)
        }
        launcher.dragController.addDragListener(this)
    }

    /**
     * Populate and show shortcuts for the Launcher U app shortcut design. Will inflate the
     * container and shortcut View instances for the popup container.
     *
     * @param deepShortcutCount Number of DeepShortcutView instances to add to container
     * @param systemShortcuts List of SystemShortcuts to add to container
     */
    fun populateAndShowRows(deepShortcutCount: Int, systemShortcuts: List<SystemShortcut<*>>) {
        populateAndShowRows(itemInfo, deepShortcutCount, systemShortcuts)
    }

    /**
     * Populate and show shortcuts for the Launcher U app shortcut design. Will inflate the
     * container and shortcut View instances for the popup container.
     *
     * @param itemInfo The info that is used to load app shortcuts
     * @param deepShortcutCount Number of DeepShortcutView instances to add to container
     * @param systemShortcuts List of SystemShortcuts to add to container
     */
    private fun populateAndShowRows(
        itemInfo: ItemInfo,
        deepShortcutCount: Int,
        systemShortcuts: List<SystemShortcut<*>>,
    ) {
        containerWidth = resources.getDimensionPixelSize(R.dimen.bg_popup_item_width)

        if (deepShortcutCount > 0) {
            addAllShortcuts(deepShortcutCount, systemShortcuts)
        } else if (systemShortcuts.isNotEmpty()) {
            addSystemShortcuts(
                systemShortcuts,
                R.layout.system_shortcut_rows_container,
                R.layout.system_shortcut,
            )
        }
        show()
        loadAppShortcuts(itemInfo)
    }

    /** Animates and loads shortcuts on background thread for this popup container */
    private fun loadAppShortcuts(originalItemInfo: ItemInfo) {
        accessibilityPaneTitle = context.getString(R.string.action_deep_shortcut)
        originalIcon.forceHideDot = true
        // All views are added. Animate layout from now on.
        layoutTransition = LayoutTransition()
        // Load the shortcuts on a background thread and update the container as it animates.
        Executors.MODEL_EXECUTOR.handler.postAtFrontOfQueue(
            PopupPopulator.createUpdateRunnable(
                mActivityContext,
                originalItemInfo,
                Handler(Looper.getMainLooper()),
                this,
                deepShortcuts,
            )
        )
    }

    /**
     * Adds any Deep Shortcuts, System Shortcuts and the Widget Shortcut to their respective
     * containers
     *
     * @param deepShortcutCount number of DeepShortcutView instances
     * @param systemShortcuts List of SystemShortcuts
     */
    private fun addAllShortcuts(deepShortcutCount: Int, systemShortcuts: List<SystemShortcut<*>>) {
        if (deepShortcutCount + systemShortcuts.size <= SHORTCUT_COLLAPSE_THRESHOLD) {
            // add all system shortcuts including widgets shortcut to same container
            addSystemShortcuts(
                systemShortcuts,
                R.layout.system_shortcut_rows_container,
                R.layout.system_shortcut,
            )
            val startingHeight = ((shortcutHeight * systemShortcuts.size) + mChildContainerMargin)
            addDeepShortcuts(deepShortcutCount, startingHeight)
            return
        }

        currentHeight = shortcutHeight + mChildContainerMargin

        collapseEligibleSystemShortcutsIfOverThreshold(systemShortcuts)
        addDeepShortcuts(deepShortcutCount, currentHeight)
    }

    /**
     * If the total amount of shortcuts is over threshold, we collapse the shortcuts that are
     * eligible to be collapsible, and make sure the non-collapsible ones get their own container.
     *
     * @param systemShortcuts List of SystemShortcuts
     */
    private fun collapseEligibleSystemShortcutsIfOverThreshold(
        systemShortcuts: List<SystemShortcut<*>>
    ) {
        val collapsibleSystemShortcuts = getCollapsibleSystemShortcuts(systemShortcuts)
        // If total shortcuts over threshold, collapse system shortcuts to single row
        addSystemShortcutsIconsOnly(collapsibleSystemShortcuts)
        // May need to recalculate row width
        containerWidth =
            max(
                    containerWidth.toDouble(),
                    (collapsibleSystemShortcuts.size *
                            resources.getDimensionPixelSize(
                                R.dimen.system_shortcut_header_icon_touch_size
                            ))
                        .toDouble(),
                )
                .toInt()
        val nonCollapsibleSystemShortcuts =
            systemShortcuts
                .stream()
                .filter { shortcut: SystemShortcut<*> -> !shortcut.mIsCollapsible }
                .toList()
        if (nonCollapsibleSystemShortcuts.isNotEmpty()) {
            addSystemShortcuts(
                nonCollapsibleSystemShortcuts,
                R.layout.system_shortcut_rows_container,
                R.layout.system_shortcut,
            )
            currentHeight +=
                ((shortcutHeight * nonCollapsibleSystemShortcuts.size) + mChildContainerMargin)
        }
    }

    /**
     * Inflates the given systemShortcutContainerLayout as a container, and populates with the
     * systemShortcuts as views using the systemShortcutLayout
     *
     * @param systemShortcuts List of SystemShortcut to inflate as Views
     * @param systemShortcutContainerLayout Layout Resource for the Container of shortcut Views
     * @param systemShortcutLayout Layout Resource for the individual shortcut Views
     */
    private fun addSystemShortcuts(
        systemShortcuts: List<SystemShortcut<*>>,
        @LayoutRes systemShortcutContainerLayout: Int,
        @LayoutRes systemShortcutLayout: Int,
    ) {
        if (systemShortcuts.isEmpty()) {
            return
        }
        systemShortcutContainer = inflateAndAdd(systemShortcutContainerLayout, this)
        widgetContainer = systemShortcutContainer
        for (i in systemShortcuts.indices) {
            initializeSystemShortcut(
                systemShortcutLayout,
                systemShortcutContainer,
                systemShortcuts[i],
                i < systemShortcuts.size - 1,
            )
        }
    }

    private fun addSystemShortcutsIconsOnly(systemShortcuts: List<SystemShortcut<*>>) {
        if (systemShortcuts.isEmpty()) {
            return
        }

        systemShortcutContainer = inflateAndAdd(R.layout.system_shortcut_icons_container, this)

        for (i in systemShortcuts.indices) {
            @LayoutRes var shortcutIconLayout = R.layout.system_shortcut_icon_only
            var shouldAppendSpacer = true

            if (i == 0) {
                shortcutIconLayout = R.layout.system_shortcut_icon_only_start
            } else if (i == systemShortcuts.size - 1) {
                shortcutIconLayout = R.layout.system_shortcut_icon_only_end
                shouldAppendSpacer = false
            }
            initializeSystemShortcut(
                shortcutIconLayout,
                systemShortcutContainer,
                systemShortcuts[i],
                shouldAppendSpacer,
            )
        }
    }

    /**
     * Inflates and adds [deepShortcutCount] number of DeepShortcutView for the to a new container
     *
     * @param deepShortcutCount number of DeepShortcutView instances to add
     * @param startingHeight height of popup before adding deep shortcuts
     */
    private fun addDeepShortcuts(deepShortcutCount: Int, startingHeight: Float) {
        var height = startingHeight
        deepShortcutContainer = inflateAndAdd(R.layout.deep_shortcut_container, this)
        for (i in deepShortcutCount downTo 1) {
            height += shortcutHeight
            // when there is limited vertical screen space, limit total popup rows to fit
            if (
                height >=
                    (mActivityContext?.deviceProfile?.deviceProperties?.availableHeightPx ?: 0)
            )
                break
            val v = inflateAndAdd<DeepShortcutView>(R.layout.deep_shortcut, deepShortcutContainer)
            v.layoutParams.width = containerWidth
            deepShortcuts.add(v)
        }
        updateHiddenShortcuts()
    }

    override fun getTargetObjectLocation(outPos: Rect) {
        super.getTargetObjectLocation(outPos)
        outPos.bottom = outPos.top + (originalIcon.icon?.bounds?.height() ?: originalView.height)
    }

    private fun updateHiddenShortcuts() {
        val total = deepShortcuts.size
        for (i in 0..<total) {
            val view = deepShortcuts[i]
            view.visibility = if (i >= PopupPopulator.MAX_SHORTCUTS) GONE else VISIBLE
        }
    }

    fun initializeWidgetShortcut(container: ViewGroup?, info: SystemShortcut<*>) {
        val view = initializeSystemShortcut(R.layout.system_shortcut, container, info, false)
        view.layoutParams.width = containerWidth
    }

    /**
     * Initializes and adds View for given SystemShortcut to a container.
     *
     * @param resId Resource id to use for SystemShortcut View.
     * @param container ViewGroup to add the shortcut View to as a parent
     * @param info The SystemShortcut instance to create a View for.
     * @param shouldAppendSpacer If True, will add a spacer after the shortcut, when showing the
     *   SystemShortcut as an icon only. Used to space the shortcut icons evenly.
     * @return The view inflated for the SystemShortcut
     */
    private fun initializeSystemShortcut(
        resId: Int,
        container: ViewGroup?,
        info: SystemShortcut<*>,
        shouldAppendSpacer: Boolean,
    ): View {
        val view = inflateAndAdd<View>(resId, container)
        if (view is DeepShortcutView) {
            // System shortcut takes entire row with icon and text
            val shortcutView = view
            if (com.android.wm.shell.Flags.enableGsf()) {
                shortcutView.bubbleText.typeface =
                    Typeface.create(
                        DeepShortcutTextView.GOOGLE_SANS_FLEX_LABEL_LARGE,
                        Typeface.NORMAL,
                    )
            }
            info.setIconAndLabelFor(shortcutView.iconView, shortcutView.bubbleText)
        } else if (view is ImageView) {
            // System shortcut is just an icon
            info.setIconAndContentDescriptionFor(view)
            if (shouldAppendSpacer) inflateAndAdd<View>(R.layout.system_shortcut_spacer, container)
            view.setTooltipText(view.getContentDescription())
        }
        view.tag = info
        view.setOnClickListener(info)
        return view
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    override fun createPreDragCondition(): PreDragCondition {
        return object : PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double): Boolean {
                return distanceDragged > startDragThreshold
            }

            override fun onPreDragStart(dragObject: DragObject) {
                if (!updateIconUi) {
                    return
                }
                if (mIsAboveIcon) {
                    // Hide only the icon, keep the text visible.
                    originalIcon.setIconVisible(false)
                    originalIcon.visibility = VISIBLE
                } else {
                    // Hide both the icon and text.
                    originalIcon.visibility = INVISIBLE
                }
            }

            override fun onPreDragEnd(dragObject: DragObject, dragStarted: Boolean) {
                if (!updateIconUi) {
                    return
                }
                originalIcon.setIconVisible(true)
                if (dragStarted) {
                    // Make sure we keep the original icon hidden while it is being dragged.
                    originalIcon.visibility = INVISIBLE
                } else {
                    // TODO: add WW logging if want to add logging for long press on popup
                    //  container.
                    //  mLauncher.getUserEventDispatcher().logDeepShortcutsOpen(mOriginalIcon);
                    if (!mIsAboveIcon) {
                        // Show the icon but keep the text hidden.
                        originalIcon.visibility = VISIBLE
                        originalIcon.setTextVisibility(false)
                    }
                }
            }
        }
    }

    override fun onCreateCloseAnimation(anim: AnimatorSet) {
        // Animate original icon's text back in.
        anim.play(originalIcon.createTextAlphaAnimator(true /* fadeIn */))
        originalIcon.forceHideDot = false
    }

    override fun closeComplete() {
        super.closeComplete()
        mActivityContext?.getDragController<DragController<*>>()?.removeDragListener(this)
        val openPopup = getOpen<T>(mActivityContext)
        if (openPopup == null || openPopup.originalView !== originalIcon) {
            originalIcon.setTextVisibility(originalIcon.shouldTextBeVisible())
            originalIcon.forceHideDot = false
        }
    }

    companion object {
        private const val SHORTCUT_COLLAPSE_THRESHOLD = 6

        /** Returns true if we can show the container. */
        @Deprecated("Left here since some dependent projects are using this method")
        fun canShow(icon: View?, item: ItemInfo?): Boolean {
            return icon is BubbleTextView && ShortcutUtil.supportsShortcuts(item)
        }

        /**
         * Shows the popup specifically for the Private Space app. This is specifically special in
         * which no system shortcuts are shown for this icon.
         *
         * @param icon the app icon to show the popup for
         */
        @JvmStatic
        fun showForPrivateSpaceApp(icon: BubbleTextView) {
            val activityContext: ActivityContext = ActivityContext.lookupContext(icon.context)
            if (getOpen(ActivityContext.lookupContext(icon.context)) != null) {
                // There is already an items container open, so don't open this one.
                icon.clearFocus()
                return
            }
            val item = icon.tag as ItemInfo
            val deepShortcutCount =
                activityContext.activityComponent.popupDataProvider.getShortcutCountForItem(item)

            val container =
                create<Launcher>(context = icon.context, originalView = icon, itemInfo = item)
            container.populateAndShowRows(deepShortcutCount, emptyList())
            container.requestFocus()
        }

        /**
         * Finds the first instance of the Widgets Shortcut from the SystemShortcut List
         *
         * @param systemShortcuts List of SystemShortcut instances to search
         * @return Optional Widgets SystemShortcut
         */
        private fun getWidgetShortcut(
            systemShortcuts: List<SystemShortcut<*>>
        ): Optional<SystemShortcut.Widgets<*>> {
            return systemShortcuts
                .stream()
                .filter { shortcut: SystemShortcut<*>? -> shortcut is SystemShortcut.Widgets<*> }
                .map { it as SystemShortcut.Widgets }
                .findFirst()
        }

        /**
         * Returns list of [systemShortcuts] without the non-collapsible shortcuts.
         *
         * @param systemShortcuts list of SystemShortcuts to filter from.
         * @return systemShortcuts without the Widgets Shortcut.
         */
        private fun getCollapsibleSystemShortcuts(
            systemShortcuts: List<SystemShortcut<*>>
        ): List<SystemShortcut<*>> {
            return systemShortcuts
                .stream()
                .filter { shortcut: SystemShortcut<*> -> shortcut.mIsCollapsible }
                .collect(Collectors.toList())
        }

        /**
         * Returns list of [systemShortcuts] without the non-collapsible shortcuts.
         *
         * @param systemShortcuts list of SystemShortcuts to filter from.
         * @return systemShortcuts without the Widgets Shortcut.
         */
        private fun getNonWidgetSystemShortcuts(
            systemShortcuts: List<SystemShortcut<*>>
        ): List<SystemShortcut<*>> {
            return systemShortcuts
                .stream()
                .filter { shortcut: SystemShortcut<*>? -> shortcut !is SystemShortcut.Widgets<*> }
                .collect(Collectors.toList())
        }

        /**
         * Creates a new instance of [PopupContainerWithArrow].
         *
         * @param context The context in which the popup will be created.
         * @param originalView The view that the popup is associated with.
         * @param updateIconUi Whether to update the icon UI during drag and drop.
         * @return A new instance of [PopupContainerWithArrow].
         */
        @JvmStatic
        fun <T> create(
            context: Context,
            originalView: View,
            itemInfo: ItemInfo,
            updateIconUi: Boolean = true,
        ): PopupContainerWithArrow<T> where T : Context, T : ActivityContext {
            val container =
                PopupContainerWithArrow<T>(context, originalView, itemInfo, updateIconUi)
            container.id = R.id.popup_container
            container.clipChildren = false
            container.clipToPadding = false
            container.orientation = VERTICAL
            container.layoutParams =
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            return container
        }
    }
}
