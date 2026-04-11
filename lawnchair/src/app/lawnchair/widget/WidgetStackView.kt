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
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.CheckLongPressHelper
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.util.MultiTranslateDelegate

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

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return longPressHelper.hasPerformedLongPress()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        longPressHelper.onTouchEvent(ev)
        return true
    }

    override fun onLongClick(view: View): Boolean = ItemLongClickListener.INSTANCE_WORKSPACE.onLongClick(view)

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
}
