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

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.PageIndicator
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetInflater
import com.android.launcher3.widget.WidgetManagerHelper

/**
 * Inner view that displays and manages a stack of widgets.
 * Similar to BcSmartspaceView - handles ViewPager and content.
 */
class WidgetStackContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private lateinit var viewPager: InterceptingWidgetPager
    private lateinit var indicator: PageIndicator
    private val adapter = WidgetStackAdapter()
    private val widgetViews = mutableListOf<LauncherAppWidgetHostView>()

    private var stackInfo: WidgetStackInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoRotateRunnable: Runnable? = null
    private val autoRotateIntervalMs = 20000L
    private var refreshRunnable: Runnable? = null
    private var isRefreshing = false // Prevent concurrent refresh operations
    private var refreshAttempts = 0 // Track refresh attempts to prevent infinite loops
    private val maxRefreshAttempts = 5 // Maximum number of refresh attempts

    private val launcher: Launcher? = Launcher.getLauncher(context)
    private val widgetHolder: LauncherWidgetHolder? = launcher?.getAppWidgetHolder()
    private val widgetInflater: WidgetInflater? = launcher?.let { WidgetInflater(context) }

    override fun onFinishInflate() {
        super.onFinishInflate()

        // Find views from XML (same pattern as BcSmartspaceView)
        viewPager = findViewById<InterceptingWidgetPager>(R.id.widget_stack_pager)!!
        indicator = findViewById<PageIndicator>(R.id.widget_stack_page_indicator)!!

        // Configure ViewPager
        viewPager.isSaveEnabled = false
        // Make ViewPager NOT long-clickable so parent WidgetStackView can handle long-press for drag
        viewPager.isLongClickable = false
        // Ensure ViewPager doesn't block touch events from parent
        viewPager.isClickable = true

        // Set offscreen page limit to keep all widgets attached
        // This ensures widgets receive live updates even when not visible
        // Using a large value to keep all widgets in memory (for small stacks)
        viewPager.offscreenPageLimit = 10

        // Make this view NOT long-clickable so events bubble up to parent WidgetStackView
        isLongClickable = false
        // Don't block touch events - let them bubble up to parent for drag
        isClickable = false

        // Set up page change listener
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int,
            ) {
                indicator.setPageOffset(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                stackInfo?.currentIndex = position

                // Trigger widget update when page becomes visible
                // This ensures widgets receive updates when they become visible
                updateVisibleWidget(position)

                // Reset auto-rotate timer when user manually swipes
                if (stackInfo?.autoRotate == true) {
                    handler.removeCallbacks(autoRotateRunnable ?: return)
                    scheduleNextAutoRotate()
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                // When scrolling ends, update all visible widgets
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    val currentPosition = viewPager.currentItem
                    updateVisibleWidget(currentPosition)
                }
            }
        })

        // Set adapter
        viewPager.adapter = adapter
    }

    /**
     * Sets the stack information and loads widgets
     * Preserves existing views to prevent widgets from disappearing during editing
     * Never removes widgets that are still in the stack, even if temporarily not in model
     */
    fun setStackInfo(info: WidgetStackInfo) {
        val oldStackInfo = stackInfo
        stackInfo = info

        // Build new widget views list while preserving existing views
        // This prevents widgets from disappearing when updating stack info during editing
        val existingViewsMap = widgetViews.associateBy { it.appWidgetId }
        val newWidgetViews = mutableListOf<LauncherAppWidgetHostView>()
        val preservedWidgetIds = mutableSetOf<Int>()

        // Process each widget in the new stack - preserve order from stack info
        info.widgetIds.forEach { widgetId ->
            // First, try to reuse existing view if it exists
            val existingView = existingViewsMap[widgetId]
            if (existingView != null) {
                // View exists - reuse it (ViewPager will handle reattaching if needed)
                newWidgetViews.add(existingView)
                preservedWidgetIds.add(widgetId)
                android.util.Log.d("WidgetStackContentView", "Preserved existing view for widget $widgetId")
            } else {
                // Try to create new view for widget not in existing views
                createWidgetView(widgetId)?.let { view ->
                    newWidgetViews.add(view)
                    preservedWidgetIds.add(widgetId)
                    android.util.Log.d("WidgetStackContentView", "Created new view for widget $widgetId")
                } ?: run {
                    // Widget not found in model - might be async database write or still loading
                    // Check if we have an existing view for this widget ID (even if not in map)
                    // This can happen if the view was created but the map wasn't updated
                    val existingViewByWidgetId = widgetViews.firstOrNull { it.appWidgetId == widgetId }
                    if (existingViewByWidgetId != null) {
                        // Found existing view by widget ID - preserve it
                        newWidgetViews.add(existingViewByWidgetId)
                        preservedWidgetIds.add(widgetId)
                        android.util.Log.d("WidgetStackContentView", "Preserved existing view for widget $widgetId (found by ID search)")
                    } else {
                        // Widget not found in model and no existing view
                        // Create a placeholder PendingAppWidgetHostView to maintain stack structure
                        // This prevents the stack from disappearing and allows the widget to be loaded later
                        android.util.Log.w("WidgetStackContentView", "Widget $widgetId not found in model when updating stack ${info.stackId}, creating placeholder")

                        // Try to get widget info from database/model to create a proper placeholder
                        val bgDataModel = launcher?.model?.getBgDataModel()
                        val widgetInfo = synchronized(bgDataModel ?: return@run) {
                            bgDataModel.itemsIdMap.firstOrNull {
                                it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                            } as? LauncherAppWidgetInfo
                        }

                        if (widgetInfo != null) {
                            // Widget exists in model but createWidgetView failed - create placeholder
                            val placeholder = PendingAppWidgetHostView(
                                context,
                                widgetHolder ?: return@run,
                                widgetInfo,
                                null, // No provider info yet
                            )
                            placeholder.tag = widgetInfo
                            placeholder.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            placeholder.isClickable = false
                            placeholder.isFocusable = false
                            newWidgetViews.add(placeholder)
                            preservedWidgetIds.add(widgetId)
                            android.util.Log.d("WidgetStackContentView", "Created placeholder for widget $widgetId")
                        } else {
                            // Widget truly not in model - will be loaded via refreshPendingWidgets
                            // But we MUST still add a placeholder to maintain stack structure
                            // Otherwise the ViewPager will have wrong count and crash
                            android.util.Log.w("WidgetStackContentView", "Widget $widgetId not in model, stack structure may be incomplete")
                        }
                    }
                }
            }
        }

        // Ensure all widgets in stack info have views
        // If any widgets are missing, we need to preserve them or create placeholders
        val missingWidgetIds = info.widgetIds.filter { it !in preservedWidgetIds }
        if (missingWidgetIds.isNotEmpty()) {
            android.util.Log.w("WidgetStackContentView", "Missing views for ${missingWidgetIds.size} widgets in stack ${info.stackId}: $missingWidgetIds")

            // Try one more time to find or create views for missing widgets
            missingWidgetIds.forEach { widgetId ->
                // Check if widget exists in AppWidgetManager (might be valid but not in model yet)
                val widgetManagerHelper = WidgetManagerHelper(context)
                val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(context)
                try {
                    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (appWidgetInfo != null) {
                        // Widget exists in AppWidgetManager - create a placeholder that will be refreshed
                        android.util.Log.d("WidgetStackContentView", "Widget $widgetId exists in AppWidgetManager, creating placeholder")
                        // Try to get widget info from model one more time
                        val bgDataModel = launcher?.model?.getBgDataModel()
                        val widgetInfo = synchronized(bgDataModel ?: return@forEach) {
                            bgDataModel.itemsIdMap.firstOrNull {
                                it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                            } as? LauncherAppWidgetInfo
                        } ?: run {
                            // Create a minimal widget info for the placeholder
                            LauncherAppWidgetInfo(widgetId, appWidgetInfo.provider).apply {
                                this.widgetStackId = info.stackId
                                this.container = info.container
                                this.screenId = info.screenId
                                this.cellX = info.cellX
                                this.cellY = info.cellY
                                this.spanX = info.spanX
                                this.spanY = info.spanY
                            }
                        }

                        // Convert AppWidgetProviderInfo to LauncherAppWidgetProviderInfo
                        val launcherAppWidgetInfo = widgetManagerHelper.getLauncherAppWidgetInfo(
                            widgetId,
                            appWidgetInfo.provider,
                        )

                        val holder = widgetHolder ?: return@forEach
                        val placeholder = PendingAppWidgetHostView(
                            context,
                            holder,
                            widgetInfo,
                            launcherAppWidgetInfo,
                        )
                        placeholder.tag = widgetInfo
                        placeholder.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        placeholder.isClickable = false
                        placeholder.isFocusable = false
                        newWidgetViews.add(placeholder)
                        android.util.Log.d("WidgetStackContentView", "Created placeholder for widget $widgetId from AppWidgetManager")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("WidgetStackContentView", "Error checking widget $widgetId in AppWidgetManager", e)
                }
            }
        }

        // Save current page before updating (if we have views)
        val currentPageBeforeUpdate = if (widgetViews.isNotEmpty()) viewPager.currentItem else 0

        // Update widget views list WITHOUT clearing first
        // This ensures widgets don't disappear during the update
        // Build ordered list matching stack info order
        val orderedViews = mutableListOf<LauncherAppWidgetHostView>()
        val usedViews = mutableSetOf<LauncherAppWidgetHostView>()

        // First, add views in the order specified by stack info
        info.widgetIds.forEach { widgetId ->
            val view = newWidgetViews.firstOrNull { it.appWidgetId == widgetId }
                ?: widgetViews.firstOrNull { it.appWidgetId == widgetId }

            if (view != null) {
                orderedViews.add(view)
                usedViews.add(view)
            } else {
                // Widget doesn't have a view yet - create placeholder to maintain structure
                android.util.Log.w("WidgetStackContentView", "Widget $widgetId in stack ${info.stackId} has no view, creating placeholder")

                // Try to get widget info for placeholder
                val bgDataModel = launcher?.model?.getBgDataModel()
                val widgetInfo = synchronized(bgDataModel ?: return@forEach) {
                    bgDataModel.itemsIdMap.firstOrNull {
                        it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                    } as? LauncherAppWidgetInfo
                }

                if (widgetInfo != null && widgetHolder != null) {
                    val placeholder = PendingAppWidgetHostView(
                        context,
                        widgetHolder,
                        widgetInfo,
                        null,
                    )
                    placeholder.tag = widgetInfo
                    placeholder.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    placeholder.isClickable = false
                    placeholder.isFocusable = false
                    orderedViews.add(placeholder)
                    usedViews.add(placeholder)
                } else {
                    // Can't create placeholder - add null to maintain structure (will be handled by adapter)
                    android.util.Log.e("WidgetStackContentView", "Cannot create placeholder for widget $widgetId - stack structure may be broken")
                }
            }
        }

        // Update widgetViews list with ordered views
        widgetViews.clear()
        widgetViews.addAll(orderedViews)

        // Log final state
        if (widgetViews.size < info.widgetIds.size) {
            val stillMissing = info.widgetIds.filter { widgetId ->
                widgetViews.none { it.appWidgetId == widgetId }
            }
            android.util.Log.w("WidgetStackContentView", "Stack ${info.stackId} still missing ${stillMissing.size} widget views after update: $stillMissing")
        } else {
            android.util.Log.d("WidgetStackContentView", "Stack ${info.stackId} has ${widgetViews.size} views for ${info.widgetIds.size} widgets")
        }

        // Don't aggressively remove widgets during editing
        // Only remove widgets that are truly invalid (not in AppWidgetManager)
        // Widgets that are temporarily not in model should be preserved as placeholders

        // Notify adapter (ViewPager will handle view lifecycle)
        adapter.notifyDataSetChanged()

        // Update indicator - use actual widget count from stack info, not just loaded views
        indicator.setNumPages(info.size())

        // Set current page (preserve current page if still valid, otherwise use stack info)
        val currentIndex = when {
            // If we had views before and the old index is still valid, try to preserve it
            oldStackInfo != null &&
                currentPageBeforeUpdate < widgetViews.size &&
                currentPageBeforeUpdate < info.widgetIds.size -> {
                currentPageBeforeUpdate
            }

            // Otherwise use the index from stack info
            else -> {
                info.currentIndex.coerceIn(0, (info.widgetIds.size - 1).coerceAtLeast(0))
            }
        }

        // Always set current page, even if we have no views yet (will be updated when views load)
        viewPager.setCurrentItem(currentIndex, false)

        // Update the visible widget after setting current page
        // This ensures the widget receives updates when the stack is loaded
        handler.post {
            if (isAttachedToWindow && currentIndex < widgetViews.size) {
                updateVisibleWidget(currentIndex)
            }
        }

        // If we have widget IDs but couldn't load all views, schedule a single refresh
        if (info.widgetIds.isNotEmpty() && widgetViews.size < info.widgetIds.size) {
            android.util.Log.d("WidgetStackContentView", "Stack ${info.stackId} has ${info.widgetIds.size} widgets but only ${widgetViews.size} views loaded, will refresh")
            // Update indicator to show correct count
            indicator.setNumPages(info.size())
            // Schedule a single refresh attempt (refreshPendingWidgets will handle retries if needed)
            if (isAttachedToWindow) {
                handler.post {
                    refreshPendingWidgets()
                }
            }
        }

        // Start auto-rotate if enabled
        if (info.autoRotate) {
            startAutoRotate()
        } else {
            stopAutoRotate()
        }
    }

    /**
     * Gets the current stack info
     */
    fun getStackInfo(): WidgetStackInfo? = stackInfo

    /**
     * Adds a widget to the stack
     * Also saves the updated stack info to database for persistence
     */
    fun addWidget(widgetId: Int) {
        createWidgetView(widgetId)?.let { view ->
            widgetViews.add(view)
            adapter.notifyDataSetChanged()

            // Update stack info
            stackInfo?.let { info ->
                val newIds = info.widgetIds + widgetId
                val updatedInfo = info.copy(
                    widgetIds = newIds,
                    container = info.container,
                    screenId = info.screenId,
                    cellX = info.cellX,
                    cellY = info.cellY,
                    spanX = info.spanX,
                    spanY = info.spanY,
                )
                stackInfo = updatedInfo
                indicator.setNumPages(newIds.size)

                // Save updated stack to database for persistence
                launcher?.let { launcherInstance ->
                    val db = launcherInstance.model.modelDbController.db
                    WidgetStackManager.saveStack(db, updatedInfo)
                }
            }

            // If the widget is pending, schedule a single refresh check
            // refreshPendingWidgets will handle retries if needed
            if (view is PendingAppWidgetHostView && isAttachedToWindow) {
                handler.post {
                    refreshPendingWidgets()
                }
            }
        }
    }

    /**
     * Removes a widget from the stack
     * Also saves the updated stack info to database for persistence
     */
    fun removeWidget(widgetId: Int) {
        val index = widgetViews.indexOfFirst { view ->
            view.appWidgetId == widgetId
        }

        if (index != -1) {
            widgetViews.removeAt(index)
            adapter.notifyDataSetChanged()

            // Update stack info
            stackInfo?.let { info ->
                val newIds = info.widgetIds.filter { it != widgetId }
                val updatedInfo = info.copy(
                    widgetIds = newIds,
                    currentIndex = info.currentIndex.coerceIn(0, (newIds.size - 1).coerceAtLeast(0)),
                    container = info.container,
                    screenId = info.screenId,
                    cellX = info.cellX,
                    cellY = info.cellY,
                    spanX = info.spanX,
                    spanY = info.spanY,
                )
                stackInfo = updatedInfo
                indicator.setNumPages(newIds.size)

                // Save updated stack to database for persistence
                launcher?.let { launcherInstance ->
                    val db = launcherInstance.model.modelDbController.db
                    WidgetStackManager.saveStack(db, updatedInfo)
                }
            }
        }
    }

    /**
     * Creates a widget view for the given widget ID
     * Uses proper widget inflation logic to handle pending/real widgets correctly
     * Ensures widget position matches stack info position
     */
    private fun createWidgetView(widgetId: Int): LauncherAppWidgetHostView? {
        val launcherInstance = launcher ?: return null
        val holder = widgetHolder ?: return null
        val inflater = widgetInflater ?: return null
        val widgetManagerHelper = WidgetManagerHelper(context)

        // Find the widget info by appWidgetId
        val bgDataModel = launcherInstance.model.getBgDataModel()
        val widgetInfo = synchronized(bgDataModel) {
            var found: LauncherAppWidgetInfo? = null
            for (itemInfo in bgDataModel.itemsIdMap) {
                if (itemInfo is LauncherAppWidgetInfo && itemInfo.appWidgetId == widgetId) {
                    found = itemInfo
                    break
                }
            }
            found
        } ?: return null

        // Ensure widget position matches stack info position
        // This fixes issues where widgets have stale positions after stack is moved
        // IMPORTANT: Do NOT modify widgetInfo before calling inflater.inflateAppWidget()
        // WidgetInflater needs the original widget info to find the provider correctly
        // Only update position AFTER successful inflation
        stackInfo?.let { info ->
            val needsPositionUpdate = widgetInfo.screenId != info.screenId ||
                widgetInfo.cellX != info.cellX ||
                widgetInfo.cellY != info.cellY ||
                widgetInfo.container != info.container ||
                widgetInfo.widgetStackId != info.stackId

            // Don't modify widgetInfo before inflation - it needs original data
            // to find provider via targetComponent and providerName
            // Position will be updated after successful inflation if needed
        }

        // Use WidgetInflater to properly inflate the widget (handles pending/real states)
        val inflationResult = inflater.inflateAppWidget(widgetInfo)

        when (inflationResult.type) {
            WidgetInflater.TYPE_DELETE -> {
                // ROOT CAUSE FIX: TYPE_DELETE usually means targetComponent/providerName is missing
                // Try to restore these fields from AppWidgetManager before giving up

                val hasRequiredFields = widgetInfo.targetComponent != null &&
                    widgetInfo.providerName != null

                if (!hasRequiredFields) {
                    // Missing required fields - try to restore from AppWidgetManager
                    // Use WidgetManagerHelper to get provider info properly
                    try {
                        val providerInfo = widgetManagerHelper.getLauncherAppWidgetInfo(
                            widgetId,
                            widgetInfo.getTargetComponent(),
                        )
                        if (providerInfo != null) {
                            // Restore missing fields
                            // targetComponent is a getter that returns providerName, so we only need to set providerName
                            // Use getComponent() which returns the provider ComponentName
                            widgetInfo.providerName = providerInfo.getComponent()

                            // Update in database
                            launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)

                            // Retry inflation with restored fields
                            val retryResult = inflater.inflateAppWidget(widgetInfo)
                            if (retryResult.type != WidgetInflater.TYPE_DELETE) {
                                // Success! Handle the retry result
                                android.util.Log.d("WidgetStackContentView", "Widget $widgetId restored successfully in createWidgetView")

                                // Handle retry result based on type
                                when (retryResult.type) {
                                    WidgetInflater.TYPE_PENDING -> {
                                        val providerInfo = retryResult.widgetInfo
                                            ?: widgetManagerHelper.findProvider(widgetInfo.providerName, widgetInfo.user)

                                        val pendingView = PendingAppWidgetHostView(
                                            context,
                                            holder,
                                            widgetInfo,
                                            providerInfo,
                                        )
                                        pendingView.tag = widgetInfo
                                        pendingView.layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        pendingView.isClickable = false
                                        pendingView.isFocusable = false

                                        // Update position after successful restore
                                        stackInfo?.let { info ->
                                            val needsPositionUpdate = widgetInfo.screenId != info.screenId ||
                                                widgetInfo.cellX != info.cellX ||
                                                widgetInfo.cellY != info.cellY ||
                                                widgetInfo.container != info.container ||
                                                widgetInfo.widgetStackId != info.stackId

                                            if (needsPositionUpdate) {
                                                widgetInfo.screenId = info.screenId
                                                widgetInfo.cellX = info.cellX
                                                widgetInfo.cellY = info.cellY
                                                widgetInfo.container = info.container
                                                widgetInfo.widgetStackId = info.stackId
                                                if (widgetInfo.sourceContainer != info.container) {
                                                    widgetInfo.sourceContainer = info.container
                                                }
                                                launcherInstance.modelWriter?.modifyItemInDatabase(
                                                    widgetInfo,
                                                    info.container,
                                                    info.screenId,
                                                    info.cellX,
                                                    info.cellY,
                                                    widgetInfo.spanX,
                                                    widgetInfo.spanY,
                                                )
                                            }
                                        }

                                        return pendingView as? LauncherAppWidgetHostView
                                    }

                                    WidgetInflater.TYPE_REAL -> {
                                        val providerInfo = retryResult.widgetInfo ?: return null

                                        // Update position after successful restore
                                        stackInfo?.let { info ->
                                            val needsPositionUpdate = widgetInfo.screenId != info.screenId ||
                                                widgetInfo.cellX != info.cellX ||
                                                widgetInfo.cellY != info.cellY ||
                                                widgetInfo.container != info.container ||
                                                widgetInfo.widgetStackId != info.stackId

                                            if (needsPositionUpdate) {
                                                widgetInfo.screenId = info.screenId
                                                widgetInfo.cellX = info.cellX
                                                widgetInfo.cellY = info.cellY
                                                widgetInfo.container = info.container
                                                widgetInfo.widgetStackId = info.stackId
                                                if (widgetInfo.sourceContainer != info.container) {
                                                    widgetInfo.sourceContainer = info.container
                                                }
                                                launcherInstance.modelWriter?.modifyItemInDatabase(
                                                    widgetInfo,
                                                    info.container,
                                                    info.screenId,
                                                    info.cellX,
                                                    info.cellY,
                                                    widgetInfo.spanX,
                                                    widgetInfo.spanY,
                                                )
                                            }
                                        }

                                        val hostView = holder.createView(widgetId, providerInfo) as? LauncherAppWidgetHostView
                                        hostView?.setAppWidget(widgetId, providerInfo)
                                        hostView?.tag = widgetInfo
                                        hostView?.layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        hostView?.isClickable = false
                                        hostView?.isFocusable = false
                                        return hostView
                                    }

                                    else -> return null
                                }
                            } else {
                                // Still failed after restore - widget is truly gone
                                android.util.Log.w("WidgetStackContentView", "Widget $widgetId still invalid after restore, skipping")
                                return null
                            }
                        } else {
                            // Widget not in AppWidgetManager - truly gone
                            android.util.Log.w("WidgetStackContentView", "Widget $widgetId not in AppWidgetManager, skipping")
                            return null
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WidgetStackContentView", "Failed to restore widget $widgetId", e)
                        return null
                    }
                } else {
                    // Has required fields but still TYPE_DELETE - widget is truly gone
                    android.util.Log.w("WidgetStackContentView", "Widget $widgetId has required fields but still TYPE_DELETE, skipping")
                    return null
                }
            }

            WidgetInflater.TYPE_PENDING -> {
                // Widget is pending, create PendingAppWidgetHostView
                // Try to get provider info even if widget is pending (might be available)
                val providerInfo = inflationResult.widgetInfo
                    ?: widgetManagerHelper.findProvider(widgetInfo.providerName, widgetInfo.user)

                // Update position after successful inflation check
                stackInfo?.let { info ->
                    val needsPositionUpdate = widgetInfo.screenId != info.screenId ||
                        widgetInfo.cellX != info.cellX ||
                        widgetInfo.cellY != info.cellY ||
                        widgetInfo.container != info.container ||
                        widgetInfo.widgetStackId != info.stackId

                    if (needsPositionUpdate) {
                        widgetInfo.screenId = info.screenId
                        widgetInfo.cellX = info.cellX
                        widgetInfo.cellY = info.cellY
                        widgetInfo.container = info.container
                        widgetInfo.widgetStackId = info.stackId
                        if (widgetInfo.sourceContainer != info.container) {
                            widgetInfo.sourceContainer = info.container
                        }
                        launcherInstance.modelWriter?.modifyItemInDatabase(
                            widgetInfo,
                            info.container,
                            info.screenId,
                            info.cellX,
                            info.cellY,
                            widgetInfo.spanX,
                            widgetInfo.spanY,
                        )
                    }
                }

                val pendingView = PendingAppWidgetHostView(
                    context,
                    holder,
                    widgetInfo,
                    providerInfo, // Pass provider info if available (enables restoration listener)
                )
                // Set the tag so PendingAppWidgetHostView can reinflate correctly
                pendingView.tag = widgetInfo
                pendingView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                pendingView.isClickable = false
                pendingView.isFocusable = false
                return pendingView as? LauncherAppWidgetHostView
            }

            WidgetInflater.TYPE_REAL -> {
                // Widget is ready, create real widget view
                val providerInfo = inflationResult.widgetInfo ?: return null

                // Update position after successful inflation
                stackInfo?.let { info ->
                    val needsPositionUpdate = widgetInfo.screenId != info.screenId ||
                        widgetInfo.cellX != info.cellX ||
                        widgetInfo.cellY != info.cellY ||
                        widgetInfo.container != info.container ||
                        widgetInfo.widgetStackId != info.stackId

                    if (needsPositionUpdate) {
                        widgetInfo.screenId = info.screenId
                        widgetInfo.cellX = info.cellX
                        widgetInfo.cellY = info.cellY
                        widgetInfo.container = info.container
                        widgetInfo.widgetStackId = info.stackId
                        if (widgetInfo.sourceContainer != info.container) {
                            widgetInfo.sourceContainer = info.container
                        }
                        launcherInstance.modelWriter?.modifyItemInDatabase(
                            widgetInfo,
                            info.container,
                            info.screenId,
                            info.cellX,
                            info.cellY,
                            widgetInfo.spanX,
                            widgetInfo.spanY,
                        )
                    }
                }

                val hostView = holder.createView(widgetId, providerInfo) as? LauncherAppWidgetHostView
                hostView?.setAppWidget(widgetId, providerInfo)

                // Set the tag so the widget can be identified
                hostView?.tag = widgetInfo

                // Ensure proper layout parameters
                hostView?.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )

                // Allow the widget to be interactive
                hostView?.isClickable = false
                hostView?.isFocusable = false

                return hostView
            }

            else -> return null
        }
    }

    /**
     * Starts auto-rotation of widgets
     */
    private fun startAutoRotate() {
        stopAutoRotate()
        scheduleNextAutoRotate()
    }

    /**
     * Stops auto-rotation of widgets
     */
    private fun stopAutoRotate() {
        autoRotateRunnable?.let { handler.removeCallbacks(it) }
        autoRotateRunnable = null
    }

    /**
     * Schedules the next auto-rotation
     */
    private fun scheduleNextAutoRotate() {
        val info = stackInfo ?: return
        if (!info.autoRotate || info.size() <= 1) return

        autoRotateRunnable = Runnable {
            val nextIndex = (viewPager.currentItem + 1) % info.size()
            viewPager.setCurrentItem(nextIndex, true)
            scheduleNextAutoRotate()
        }.also {
            handler.postDelayed(it, autoRotateIntervalMs)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Reset refresh attempts when reattached
        refreshAttempts = 0

        // Resume auto-rotate if needed
        if (stackInfo?.autoRotate == true) {
            startAutoRotate()
        }

        // Ensure all widgets are properly attached for live updates
        // Update the currently visible widget to ensure it receives updates
        val currentPosition = if (::viewPager.isInitialized) viewPager.currentItem else 0
        handler.post {
            updateVisibleWidget(currentPosition)
        }

        // Refresh any pending widgets that might have become ready
        // Single refresh attempt - refreshPendingWidgets will handle retries if needed
        handler.post {
            refreshPendingWidgets()
        }
    }

    /**
     * Refreshes widgets that were pending but are now ready
     * This ensures widgets transition from "Loading..." to actual content
     * Also checks for widgets in stack info that don't have views yet
     * This fixes the issue where widgets aren't loaded on restart
     * Thread-safe: Uses synchronized blocks to prevent race conditions
     * Prevents infinite loops by tracking refresh attempts
     */
    private fun refreshPendingWidgets() {
        if (!isAttachedToWindow) return

        // Prevent concurrent refresh operations
        if (isRefreshing) {
            android.util.Log.d("WidgetStackContentView", "Refresh already in progress, skipping")
            return
        }

        // Prevent infinite refresh loops
        if (refreshAttempts >= maxRefreshAttempts) {
            android.util.Log.w("WidgetStackContentView", "Max refresh attempts ($maxRefreshAttempts) reached, stopping refresh")
            refreshRunnable?.let { handler.removeCallbacks(it) }
            refreshRunnable = null
            return
        }

        isRefreshing = true
        refreshAttempts++

        val launcherInstance = launcher ?: run {
            isRefreshing = false
            return
        }
        val inflater = widgetInflater ?: run {
            isRefreshing = false
            return
        }
        val holder = widgetHolder ?: run {
            isRefreshing = false
            return
        }
        val widgetManagerHelper = WidgetManagerHelper(context)

        val bgDataModel = launcherInstance.model.getBgDataModel()
        var needsRefresh = false
        val currentStackInfo = synchronized(this) { stackInfo } ?: run {
            isRefreshing = false
            return
        }

        try {
            // First, check for widgets in stack info that don't have views yet
            // This ensures all widgets in the stack are loaded, even if they weren't in model initially
            // This is especially important for widgets on secondary pages that might not have been loaded yet
            val widgetIdsWithViews = widgetViews.map { it.appWidgetId }.toSet()
            val missingWidgetIds = currentStackInfo.widgetIds.filter { it !in widgetIdsWithViews }

            if (missingWidgetIds.isNotEmpty()) {
                android.util.Log.d("WidgetStackContentView", "Found ${missingWidgetIds.size} missing widgets in stack ${currentStackInfo.stackId}: $missingWidgetIds")
            }

            // Try to create views for widgets that are in stack info but don't have views
            // Use synchronized to prevent concurrent modifications
            synchronized(widgetViews) {
                missingWidgetIds.forEach { widgetId ->
                    // Try to create view for this widget
                    val view = createWidgetView(widgetId)
                    if (view != null) {
                        // Find the correct position in the list based on stack info order
                        val positionInStack = currentStackInfo.widgetIds.indexOf(widgetId)
                        if (positionInStack >= 0) {
                            // Ensure list is large enough
                            while (widgetViews.size <= positionInStack) {
                                // Add temporary placeholder to maintain structure
                                val tempPlaceholder = view // Use the actual view as placeholder
                                widgetViews.add(tempPlaceholder)
                            }
                            // Insert at correct position
                            if (positionInStack < widgetViews.size) {
                                widgetViews[positionInStack] = view
                            } else {
                                widgetViews.add(view)
                            }
                            needsRefresh = true
                            android.util.Log.d("WidgetStackContentView", "Loaded missing widget $widgetId at position $positionInStack for stack ${currentStackInfo.stackId}")
                        } else {
                            // Widget not in stack info - shouldn't happen, but add to end
                            widgetViews.add(view)
                            needsRefresh = true
                            android.util.Log.w("WidgetStackContentView", "Widget $widgetId not found in stack info, added to end")
                        }
                    } else {
                        // Couldn't create view - try to create a placeholder
                        android.util.Log.w("WidgetStackContentView", "Could not create view for widget $widgetId, will create placeholder")

                        // Get widget info to create placeholder
                        val bgDataModel = launcherInstance.model.getBgDataModel()
                        val widgetInfo = synchronized(bgDataModel) {
                            bgDataModel.itemsIdMap.firstOrNull {
                                it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                            } as? LauncherAppWidgetInfo
                        }

                        if (widgetInfo != null && widgetHolder != null) {
                            // Create placeholder
                            val placeholder = PendingAppWidgetHostView(
                                context,
                                widgetHolder,
                                widgetInfo,
                                null,
                            )
                            placeholder.tag = widgetInfo
                            placeholder.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            placeholder.isClickable = false
                            placeholder.isFocusable = false

                            // Add placeholder at correct position
                            val positionInStack = currentStackInfo.widgetIds.indexOf(widgetId)
                            if (positionInStack >= 0) {
                                while (widgetViews.size <= positionInStack) {
                                    widgetViews.add(placeholder)
                                }
                                widgetViews[positionInStack] = placeholder
                                needsRefresh = true
                                android.util.Log.d("WidgetStackContentView", "Created placeholder for widget $widgetId at position $positionInStack")
                            }
                        }
                    }
                }
            }

            // Check each widget view and refresh if it's pending but now ready
            // Create a snapshot of widget views to avoid concurrent modification
            val viewsToCheck = synchronized(widgetViews) {
                widgetViews.mapIndexed { index, view -> index to view }
            }

            // Process in reverse order to avoid index shifting issues
            for ((originalIndex, view) in viewsToCheck.reversed()) {
                if (view !is PendingAppWidgetHostView) continue
                val widgetInfo = view.tag as? LauncherAppWidgetInfo ?: continue

                // Re-check widget state using WidgetInflater
                val inflationResult = inflater.inflateAppWidget(widgetInfo)

                // Update widget info in database if needed
                if (inflationResult.isUpdate) {
                    launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)
                }

                when (inflationResult.type) {
                    WidgetInflater.TYPE_REAL -> {
                        // Widget is now ready, create real widget view
                        val providerInfo = inflationResult.widgetInfo
                            ?: widgetManagerHelper.findProvider(widgetInfo.providerName, widgetInfo.user)
                            ?: continue

                        try {
                            val realView = holder.createView(widgetInfo.appWidgetId, providerInfo) as? LauncherAppWidgetHostView
                            realView?.setAppWidget(widgetInfo.appWidgetId, providerInfo)
                            realView?.tag = widgetInfo
                            realView?.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            realView?.isClickable = false
                            realView?.isFocusable = false

                            if (realView != null) {
                                // Replace pending view with real widget view
                                synchronized(widgetViews) {
                                    val actualIndex = widgetViews.indexOfFirst { it.appWidgetId == widgetInfo.appWidgetId }
                                    if (actualIndex != -1 && actualIndex < widgetViews.size) {
                                        widgetViews[actualIndex] = realView
                                        needsRefresh = true
                                    }
                                }

                                // Update widget info in database if it was pending
                                // This ensures the restoreStatus is persisted correctly
                                if (widgetInfo.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                                    widgetInfo.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                                    launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)
                                }
                            }
                        } catch (e: Exception) {
                            // Widget creation failed, keep as pending and try again later
                            android.util.Log.w("WidgetStackContentView", "Failed to create widget view for ${widgetInfo.appWidgetId}", e)
                        }
                    }

                    WidgetInflater.TYPE_DELETE -> {
                        // TYPE_DELETE means WidgetInflater couldn't find the provider
                        // This is usually because targetComponent or providerName is missing/null
                        // OR the widget was actually deleted from the system
                        // Check if widget info has required fields - if not, that's the root cause!

                        val hasRequiredFields = widgetInfo.targetComponent != null &&
                            widgetInfo.providerName != null

                        if (!hasRequiredFields) {
                            // ROOT CAUSE: Widget info is missing required fields!
                            // Try to restore them from AppWidgetManager
                            android.util.Log.w("WidgetStackContentView", "Widget ${widgetInfo.appWidgetId} missing targetComponent/providerName, attempting to restore")

                            // Use WidgetManagerHelper to get provider info properly
                            try {
                                val providerInfo = widgetManagerHelper.getLauncherAppWidgetInfo(
                                    widgetInfo.appWidgetId,
                                    widgetInfo.getTargetComponent(),
                                )
                                if (providerInfo != null) {
                                    // Restore missing fields
                                    // targetComponent is a getter that returns providerName, so we only need to set providerName
                                    // Use getComponent() which returns the provider ComponentName
                                    widgetInfo.providerName = providerInfo.getComponent()

                                    // Update in database
                                    launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)

                                    // Retry inflation with restored fields
                                    val retryResult = inflater.inflateAppWidget(widgetInfo)
                                    if (retryResult.type != WidgetInflater.TYPE_DELETE) {
                                        // Success! Widget is now valid
                                        android.util.Log.d("WidgetStackContentView", "Widget ${widgetInfo.appWidgetId} restored successfully")
                                        // Handle as TYPE_REAL or TYPE_PENDING
                                        if (retryResult.type == WidgetInflater.TYPE_REAL) {
                                            val providerInfo = retryResult.widgetInfo
                                                ?: widgetManagerHelper.findProvider(widgetInfo.providerName, widgetInfo.user)

                                            if (providerInfo != null) {
                                                try {
                                                    val realView = holder.createView(widgetInfo.appWidgetId, providerInfo) as? LauncherAppWidgetHostView
                                                    realView?.setAppWidget(widgetInfo.appWidgetId, providerInfo)
                                                    realView?.tag = widgetInfo
                                                    realView?.layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                    realView?.isClickable = false
                                                    realView?.isFocusable = false

                                                    if (realView != null) {
                                                        synchronized(widgetViews) {
                                                            val actualIndex = widgetViews.indexOfFirst { it.appWidgetId == widgetInfo.appWidgetId }
                                                            if (actualIndex != -1 && actualIndex < widgetViews.size) {
                                                                widgetViews[actualIndex] = realView
                                                                needsRefresh = true
                                                            }
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.w("WidgetStackContentView", "Failed to create widget view after restore", e)
                                                }
                                            }
                                        }
                                        // If TYPE_PENDING, keep as pending view and continue to next iteration
                                        continue
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("WidgetStackContentView", "Failed to restore widget ${widgetInfo.appWidgetId}", e)
                            }
                        }

                        // Widget is truly deleted or couldn't be restored - only remove if explicitly removed by user
                        val widgetStillInStack = currentStackInfo.widgetIds.contains(widgetInfo.appWidgetId)
                        if (!widgetStillInStack) {
                            // Widget was already removed from stack by user - just remove the view
                            synchronized(widgetViews) {
                                val actualIndex = widgetViews.indexOfFirst { it.appWidgetId == widgetInfo.appWidgetId }
                                if (actualIndex != -1 && actualIndex < widgetViews.size) {
                                    widgetViews.removeAt(actualIndex)
                                    needsRefresh = true
                                }
                            }
                            continue
                        }

                        // Widget still in stack but marked as DELETE - keep as pending
                        // Don't delete automatically - let user remove it explicitly
                        android.util.Log.d("WidgetStackContentView", "Widget ${widgetInfo.appWidgetId} marked as DELETE but still in stack, keeping as pending")
                    }
                    // TYPE_PENDING: Widget still pending, keep checking
                }
            }

            if (needsRefresh) {
                adapter.notifyDataSetChanged()
                // Reset refresh attempts on successful refresh
                refreshAttempts = 0
            }

            // Check if there are still pending widgets OR missing widgets
            val hasPendingWidgets = widgetViews.any { it is PendingAppWidgetHostView }
            val hasMissingWidgets = currentStackInfo.widgetIds.any { widgetId ->
                widgetViews.none { it.appWidgetId == widgetId }
            }

            if ((hasPendingWidgets || hasMissingWidgets) && isAttachedToWindow && refreshAttempts < maxRefreshAttempts) {
                // Schedule another check in case more widgets become ready
                // Use exponential backoff: 500ms, 1000ms, 2000ms, etc.
                val delay = (500L * refreshAttempts).coerceAtMost(2000L)
                refreshRunnable?.let { handler.removeCallbacks(it) }
                refreshRunnable = Runnable {
                    if (isAttachedToWindow) {
                        refreshPendingWidgets()
                    }
                }
                handler.postDelayed(refreshRunnable!!, delay)
                android.util.Log.d("WidgetStackContentView", "Scheduled refresh attempt ${refreshAttempts + 1} in ${delay}ms")
            } else {
                // All widgets are ready OR max attempts reached, stop checking
                refreshRunnable?.let { handler.removeCallbacks(it) }
                refreshRunnable = null
                if (hasPendingWidgets || hasMissingWidgets) {
                    android.util.Log.w("WidgetStackContentView", "Stopped refreshing: max attempts reached or view detached")
                } else {
                    android.util.Log.d("WidgetStackContentView", "All widgets loaded successfully")
                    refreshAttempts = 0 // Reset on success
                }
            }
        } finally {
            isRefreshing = false
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Stop auto-rotate when detached
        stopAutoRotate()
        // Stop refresh polling when detached
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
        // Reset refresh state
        isRefreshing = false
        refreshAttempts = 0
        // Clear any pending callbacks to prevent memory leaks
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Delegate long click listener to parent WidgetStackView for drag operations
     * Don't set it on ViewPager - let parent handle it
     */
    override fun setOnLongClickListener(l: OnLongClickListener?) {
        // Don't set on ViewPager - let parent WidgetStackView handle long-press for drag
        // This ensures drag-and-drop works correctly
        super.setOnLongClickListener(null)
    }

    /**
     * Updates the widget at the given position to ensure it receives live updates
     * This is called when a page becomes visible to refresh widget content
     * Widgets must be attached to the window to receive updates from AppWidgetManager
     *
     * Widgets receive updates automatically when attached, but we can also manually
     * trigger updates for widgets that support it (like list-based widgets)
     */
    private fun updateVisibleWidget(position: Int) {
        if (position < 0 || position >= widgetViews.size) return

        synchronized(widgetViews) {
            val widgetView = widgetViews.getOrNull(position) ?: return
            if (widgetView is LauncherAppWidgetHostView) {
                val widgetId = widgetView.appWidgetId
                if (widgetId > 0) {
                    // Ensure widget is attached to receive updates
                    // If widget is not attached, it won't receive live updates from AppWidgetManager
                    if (widgetView.isAttachedToWindow) {
                        // Widget is attached - it will receive updates automatically from the system
                        // The AppWidgetHost will call updateAppWidget() when the provider sends updates
                        android.util.Log.d("WidgetStackContentView", "Widget $widgetId at position $position is attached and will receive live updates")

                        // For list-based widgets (like AdapterView widgets), we can manually trigger
                        // a data refresh. This is optional - most widgets update automatically.
                        try {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            // This only works for widgets with AdapterViews (like ListView, GridView)
                            // Regular widgets will ignore this call, which is fine
                            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, android.R.id.list)
                        } catch (e: Exception) {
                            // Some widgets don't support this - that's perfectly normal
                            // They'll update automatically when the provider sends updates
                            android.util.Log.d("WidgetStackContentView", "Widget $widgetId doesn't support manual data refresh (this is normal)", e)
                        }
                    } else {
                        // Widget is not attached - this shouldn't happen with offscreenPageLimit set
                        // But if it does, the widget will be attached when instantiateItem is called
                        android.util.Log.d("WidgetStackContentView", "Widget $widgetId at position $position is not yet attached - will attach when page becomes visible")
                    }
                }
            }
        }
    }

    /**
     * Adapter for the ViewPager to display widget views
     * Widgets must remain attached to receive live updates
     */
    private inner class WidgetStackAdapter : PagerAdapter() {
        override fun getCount(): Int = widgetViews.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            // Handle out-of-bounds positions gracefully
            // This can happen if widgetViews hasn't been fully populated yet
            if (position < 0 || position >= widgetViews.size) {
                android.util.Log.w("WidgetStackContentView", "Invalid position $position (widgetViews.size=${widgetViews.size}), attempting to load widget")

                // Try to load the widget for this position if we have stack info
                val widgetView = stackInfo?.let { info ->
                    if (position < info.widgetIds.size) {
                        val widgetId = info.widgetIds[position]
                        android.util.Log.d("WidgetStackContentView", "Attempting to load widget $widgetId for position $position")
                        // Try to create view for this widget immediately
                        createWidgetView(widgetId)
                    } else {
                        null
                    }
                }

                if (widgetView != null) {
                    // Successfully created widget view - add it to container and list
                    container.addView(widgetView)
                    synchronized(widgetViews) {
                        // Ensure list is large enough
                        while (widgetViews.size <= position) {
                            // Add a temporary placeholder to maintain list structure
                            // We'll replace it with the actual view below
                            val tempPlaceholder = widgetView // Use the actual view as placeholder
                            widgetViews.add(tempPlaceholder)
                        }
                        widgetViews[position] = widgetView
                    }
                    adapter.notifyDataSetChanged()
                    return widgetView
                } else {
                    // Couldn't create widget view - create a proper PendingAppWidgetHostView placeholder
                    val placeholderView = stackInfo?.let { info ->
                        if (position < info.widgetIds.size) {
                            val widgetId = info.widgetIds[position]
                            val bgDataModel = launcher?.model?.getBgDataModel()
                            val widgetInfo = synchronized(bgDataModel ?: return@let null) {
                                bgDataModel.itemsIdMap.firstOrNull {
                                    it is LauncherAppWidgetInfo && it.appWidgetId == widgetId
                                } as? LauncherAppWidgetInfo
                            }

                            if (widgetInfo != null && widgetHolder != null) {
                                // Create a proper pending view placeholder
                                PendingAppWidgetHostView(
                                    context,
                                    widgetHolder,
                                    widgetInfo,
                                    null, // No provider info yet
                                ).apply {
                                    tag = widgetInfo
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                    isClickable = false
                                    isFocusable = false
                                }
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } ?: run {
                        // Create a minimal placeholder view if we can't create a proper widget view
                        android.view.View(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                    }

                    container.addView(placeholderView)

                    // If we created a proper LauncherAppWidgetHostView placeholder, add it to list
                    if (placeholderView is LauncherAppWidgetHostView) {
                        synchronized(widgetViews) {
                            while (widgetViews.size <= position) {
                                widgetViews.add(placeholderView)
                            }
                            widgetViews[position] = placeholderView
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        // For plain View placeholders, try to load the widget asynchronously
                        stackInfo?.let { info ->
                            if (position < info.widgetIds.size) {
                                val widgetId = info.widgetIds[position]
                                handler.post {
                                    createWidgetView(widgetId)?.let { view ->
                                        // Replace placeholder with actual widget view
                                        val placeholderIndex = container.indexOfChild(placeholderView)
                                        if (placeholderIndex >= 0) {
                                            container.removeView(placeholderView)
                                            container.addView(view, placeholderIndex)
                                            // Update widgetViews list - ensure it's large enough
                                            synchronized(widgetViews) {
                                                while (widgetViews.size <= position) {
                                                    // Use the actual view as temporary placeholder
                                                    widgetViews.add(view)
                                                }
                                                widgetViews[position] = view
                                            }
                                            adapter.notifyDataSetChanged()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return placeholderView
                }
            }

            val view = widgetViews[position]
            // Add view to container - this attaches it to the window
            // Widgets must be attached to receive live updates from AppWidgetManager
            container.addView(view)

            // Ensure widget is properly set up for updates
            if (view is LauncherAppWidgetHostView) {
                val widgetId = view.appWidgetId
                // Post a runnable to check attachment after the view is laid out
                view.post {
                    if (view.isAttachedToWindow) {
                        // Widget is now attached and will receive updates automatically
                        android.util.Log.d("WidgetStackContentView", "Widget $widgetId attached at position $position - will receive live updates")

                        // Request an immediate update to ensure widget is fresh
                        try {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, android.R.id.list)
                        } catch (e: Exception) {
                            // Some widgets don't support this - that's okay
                            android.util.Log.d("WidgetStackContentView", "Could not request immediate update for widget $widgetId", e)
                        }
                    } else {
                        android.util.Log.w("WidgetStackContentView", "Widget $widgetId at position $position is not attached after instantiateItem")
                    }
                }
            } else if (view is PendingAppWidgetHostView) {
                // Widget is pending - ensure it will be refreshed when ready
                val widgetId = view.appWidgetId
                android.util.Log.d("WidgetStackContentView", "Pending widget $widgetId at position $position, will refresh when ready")
                // Schedule refresh for pending widgets
                handler.post {
                    refreshPendingWidgets()
                }
            }

            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            val view = obj as? View ?: return
            // Only remove view if we're not keeping it attached
            // With offscreenPageLimit set, this may not be called for visible pages
            // But if it is, the widget will be reattached when instantiateItem is called again
            container.removeView(view)
            android.util.Log.d("WidgetStackContentView", "Widget removed from container at position $position")
        }
    }
}
