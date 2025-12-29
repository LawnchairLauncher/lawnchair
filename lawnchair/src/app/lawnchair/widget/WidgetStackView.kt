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
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Reorderable
import com.android.launcher3.dragndrop.DraggableView
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.MultiTranslateDelegate

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
    Reorderable {

    // Required for Reorderable interface
    private val translateDelegate = MultiTranslateDelegate(this)
    private var reorderBounceScale = 1f

    private val contentView: WidgetStackContentView

    init {
        // Inflate the content view from XML (same pattern as smartspace)
        val inflater = LayoutInflater.from(context)
        contentView = inflater.inflate(
            R.layout.widget_stack_content,
            this,
            false,
        ) as WidgetStackContentView

        addView(contentView)

        // Set focusable and long-clickable to allow workspace to handle long press and drag
        // This enables both context menu and drag operations
        isFocusable = true
        isLongClickable = true
        // Allow this view to receive focus even when children are present
        // This ensures long-press on children bubbles up to this view
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        // Must be clickable for workspace to detect touch events for drag operations
        // Without this, long-press won't trigger drag-and-drop
        isClickable = true
    }

    /**
     * Sets the stack information and loads widgets
     */
    fun setStackInfo(info: WidgetStackInfo) {
        contentView.setStackInfo(info)
    }

    /**
     * Gets the current stack info
     */
    fun getStackInfo(): WidgetStackInfo? = contentView.getStackInfo()

    /**
     * Adds a widget to the stack
     */
    fun addWidget(widgetId: Int) {
        contentView.addWidget(widgetId)
    }

    /**
     * Removes a widget from the stack
     */
    fun removeWidget(widgetId: Int) {
        contentView.removeWidget(widgetId)
    }

    /**
     * Opens the edit dialog for this widget stack
     */
    private fun openEditDialog() {
        val launcher = Launcher.getLauncher(context)
        val currentStackInfo = getStackInfo() ?: return

        // Find the first widget info in the stack to use as reference
        val bgDataModel = launcher.model.getBgDataModel()
        val firstWidgetInfo = synchronized(bgDataModel) {
            var found: LauncherAppWidgetInfo? = null
            for (itemInfo in bgDataModel.itemsIdMap) {
                if (itemInfo is LauncherAppWidgetInfo &&
                    itemInfo.appWidgetId == currentStackInfo.widgetIds.firstOrNull()
                ) {
                    found = itemInfo
                    break
                }
            }
            found
        }

        if (firstWidgetInfo != null) {
            showWidgetStackDialog(launcher, firstWidgetInfo)
        }
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
