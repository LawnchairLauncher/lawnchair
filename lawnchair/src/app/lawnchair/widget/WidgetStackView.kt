/*
 * Copyright (C) 2025 Lawnchair
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

package app.lawnchair.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.MultiTranslateDelegate
import kotlin.math.abs

/**
 * Listener interface for widget stack changes
 */
interface WidgetStackChangeListener {
    /**
     * Called when a stack should collapse to a single widget
     * @param stackView The WidgetStackView that should be collapsed
     * @param remainingWidgetId The widget ID that should remain as a single widget
     */
    fun onStackShouldCollapse(stackView: WidgetStackView, remainingWidgetId: Int)

    /**
     * Called when a stack is created or modified
     * @param stackView The WidgetStackView that was created or modified
     */
    fun onStackChanged(stackView: WidgetStackView)
}

/**
 * Outer container for widget stacks.
 * Similar to SmartspaceViewContainer - handles touch events and inflates inner content view.
 * Implements DraggableView and Reorderable to support drag/resize functionality.
 */
class WidgetStackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr),
    DraggableView,
    Reorderable,
    View.OnLongClickListener {

    // Required for Reorderable interface
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = 1f

    private val contentView: WidgetStackContentView
    private var stackChangeListener: WidgetStackChangeListener? = null
    private val longPressHelper = CheckLongPressHelper(this, this)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var longPressDownX = 0f
    private var longPressDownY = 0f
    private var paging = false

    init {
        val inflater = LayoutInflater.from(context)
        contentView = inflater.inflate(
            R.layout.widget_stack_content,
            this,
            false,
        ) as WidgetStackContentView

        addView(contentView)

        isFocusable = true
        isLongClickable = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        isClickable = true
    }

    // ==================== Touch / Long-press ====================

    /**
     * [CheckLongPressHelper] only cancels on MOVE when the finger leaves the view. Paging the
     * stack keeps the finger inside bounds, so we cancel the pending long-press once movement
     * exceeds touch slop (same idea as scrolling vs long-press elsewhere).
     */
    private fun cancelLongPressIfScrolled(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressDownX = ev.x
                longPressDownY = ev.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - longPressDownX) > touchSlop || abs(ev.y - longPressDownY) > touchSlop) {
                    longPressHelper.cancelLongPress()
                }
            }
        }
    }

    /**
     * Nested widgets (lists, host views) call this to keep their own scrolling. Ignore it so
     * stack paging still starts.
     */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (!disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(false)
        }
    }

    /**
     * Handle paging in [dispatchTouchEvent] so it cannot be skipped by child
     * `requestDisallowInterceptTouchEvent`. Only the axis selected for this stack
     * (horizontal or vertical) pages.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (handleStackPaging(ev)) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun usesVerticalSwipe(): Boolean = contentView.getStackInfo()?.verticalSwipe == true

    private fun handleStackPaging(ev: MotionEvent): Boolean {
        val pageCount = contentView.pageCount()
        val vertical = usesVerticalSwipe()
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressDownX = ev.x
                longPressDownY = ev.y
                paging = false
                // Vertical stacks must claim the stream now or All Apps / swipe-up
                // steals MOVE. Horizontal stacks leave vertical gestures to the launcher.
                if (vertical) {
                    disallowAncestorIntercept(true)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (longPressHelper.hasPerformedLongPress()) {
                    return false
                }
                if (paging) return true
                if (pageCount <= 1) return false
                val dx = ev.x - longPressDownX
                val dy = ev.y - longPressDownY
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (absDx <= touchSlop && absDy <= touchSlop) return false
                val alongAxis = if (vertical) absDy else absDx
                val acrossAxis = if (vertical) absDx else absDy
                if (alongAxis <= touchSlop || acrossAxis > alongAxis) {
                    if (vertical) disallowAncestorIntercept(false)
                    return false
                }
                paging = true
                longPressHelper.cancelLongPress()
                ItemLongClickListener.cancelScheduledWidgetMoveMode(this)
                disallowAncestorIntercept(true)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ItemLongClickListener.cancelScheduledWidgetMoveMode(this)
                if (!paging) {
                    disallowAncestorIntercept(false)
                    return false
                }
                val delta = if (vertical) {
                    ev.y - longPressDownY
                } else {
                    ev.x - longPressDownX
                }
                val threshold = touchSlop * 2f
                val page = contentView.currentPage()
                val target = when {
                    delta < -threshold -> page + 1
                    delta > threshold -> page - 1
                    else -> page
                }
                contentView.setPage(target, true, vertical)
                paging = false
                disallowAncestorIntercept(false)
                return true
            }
        }
        return paging
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        cancelLongPressIfScrolled(ev)
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress() || paging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        cancelLongPressIfScrolled(ev)
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun onLongClick(view: View): Boolean =
        ItemLongClickListener.INSTANCE_WORKSPACE.onLongClick(view)

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }

    /**
     * Sets the stack information and loads widgets.
     * @param knownWidgets widget infos to cache locally so they can be found
     *        even before [BgDataModel] has been updated asynchronously.
     */
    @JvmOverloads
    fun setStackInfo(
        info: WidgetStackInfo,
        knownWidgets: List<LauncherAppWidgetInfo> = emptyList(),
    ) {
        contentView.setStackInfo(info, knownWidgets)
        stackChangeListener?.onStackChanged(this)
    }

    /**
     * Sets the listener for stack changes
     */
    fun setStackChangeListener(listener: WidgetStackChangeListener?) {
        stackChangeListener = listener
        contentView.setStackChangeListener(object : WidgetStackChangeListener {
            override fun onStackShouldCollapse(stackView: WidgetStackView, remainingWidgetId: Int) {
                listener?.onStackShouldCollapse(stackView, remainingWidgetId)
            }

            override fun onStackChanged(stackView: WidgetStackView) {
                listener?.onStackChanged(stackView)
            }
        })
    }

    /**
     * Gets the current stack info
     */
    fun getStackInfo(): WidgetStackInfo? = contentView.getStackInfo()

    /** @see WidgetStackContentView.getWidgetInfoForMember */
    fun getWidgetInfoForMember(appWidgetId: Int): LauncherAppWidgetInfo? = contentView.getWidgetInfoForMember(appWidgetId)

    /** @see WidgetStackContentView.updateMemberWidgetSizeRangesForResize */
    fun updateMemberWidgetSizeRangesForResize(spanX: Int, spanY: Int) {
        contentView.updateMemberWidgetSizeRangesForResize(spanX, spanY)
    }

    /** @see WidgetStackContentView.getCurrentMemberScaleToFit */
    fun getCurrentMemberScaleToFit(): Float = contentView.getCurrentMemberScaleToFit()

    /**
     * Clears "tap to finish setup" / pending UI for a member after its configuration activity
     * completes. Required when the launcher skips the default workspace completion path for
     * stacked widgets.
     */
    fun onAppWidgetConfigureCompleted(appWidgetId: Int) {
        contentView.onAppWidgetConfigureCompleted(appWidgetId)
    }

    /**
     * Adds a widget to the stack by ID (looked up from BgDataModel).
     */
    fun addWidget(widgetId: Int) {
        contentView.addWidget(widgetId)
    }

    /**
     * Adds a widget to the stack using the provided info directly,
     * bypassing the BgDataModel lookup (useful when the model hasn't been updated yet).
     */
    fun addWidget(widgetInfo: LauncherAppWidgetInfo) {
        contentView.addWidget(widgetInfo)
    }

    /**
     * Removes a widget from the stack
     */
    fun removeWidget(widgetId: Int) {
        contentView.removeWidget(widgetId)
    }

    // Touch handling removed - let workspace handle everything normally
    // This allows both drag operations and context menu to work
    // The ViewPager inside contentView handles scrolling

    // ==================== DraggableView Implementation ====================

    /**
     * Returns the view type for drag operations.
     * Widget stacks are treated as widgets.
     */
    override fun getViewType(): Int = DraggableView.DRAGGABLE_WIDGET

    /**
     * Defines the visual bounds for dragging.
     * The entire widget stack is draggable.
     */
    override fun getWorkspaceVisualDragBounds(bounds: Rect) {
        bounds.set(0, 0, measuredWidth, measuredHeight)
    }

    // ==================== Reorderable Implementation ====================

    /**
     * Returns the translate delegate for reorder animations.
     */
    override fun getTranslateDelegate(): MultiTranslateDelegate = translateDelegate

    /**
     * Sets the scale for reorder bounce animations.
     */
    override fun setReorderBounceScale(scale: Float) {
        reorderBounceScale = scale
        updateScale()
    }

    /**
     * Gets the current reorder bounce scale.
     */
    override fun getReorderBounceScale(): Float = reorderBounceScale

    /**
     * Updates the view scale based on reorder bounce scale.
     */
    private fun updateScale() {
        scaleX = reorderBounceScale
        scaleY = reorderBounceScale
    }

    private fun disallowAncestorIntercept(disallow: Boolean) {
        var p = parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = (p as? ViewGroup)?.parent
        }
    }

    companion object {
        /**
         * True when [ev] (in DragLayer coordinates) hits a vertically paging [WidgetStackView].
         * Used so All Apps / swipe-up controllers do not steal that stack's up/down paging.
         * Horizontal stacks do not block those controllers.
         */
        @JvmStatic
        fun isEventOverStack(launcher: Launcher, ev: MotionEvent): Boolean {
            val dragLayer = launcher.dragLayer ?: return false
            return hitWidgetStack(dragLayer, ev.x, ev.y)
        }

        private fun hitWidgetStack(view: View, x: Float, y: Float): Boolean {
            if (view !is ViewGroup) return false
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                if (child.visibility != VISIBLE) continue
                if (x < child.left || x >= child.right || y < child.top || y >= child.bottom) {
                    continue
                }
                if (child is WidgetStackView) {
                    return child.usesVerticalSwipe()
                }
                val cx = x - child.left + child.scrollX
                val cy = y - child.top + child.scrollY
                if (hitWidgetStack(child, cx, cy)) return true
            }
            return false
        }
    }
}
