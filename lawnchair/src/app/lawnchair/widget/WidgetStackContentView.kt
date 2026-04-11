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
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import app.lawnchair.smartspace.PageIndicator
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.widget.LauncherAppWidgetHostView
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.NavigableAppWidgetHostView
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetInflater
import com.android.launcher3.widget.WidgetManagerHelper

/**
 * Inner view that displays and manages a stack of widgets inside a [ViewPager].
 * Handles widget inflation, live-update registration, pending→real transitions,
 * and auto-rotation.
 */
class WidgetStackContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    companion object {
        private const val TAG = "WidgetStackContent"
        private const val AUTO_ROTATE_INTERVAL_MS = 20_000L
        private const val MAX_REFRESH_ATTEMPTS = 5
    }

    private lateinit var viewPager: InterceptingWidgetPager
    private lateinit var indicator: PageIndicator
    private val adapter = WidgetStackAdapter()

    /** Ordered list of widget views matching the stack's widget-id order. */
    private val widgetViews = mutableListOf<LauncherAppWidgetHostView>()

    /** Local cache for widget infos not yet in BgDataModel (e.g. freshly drag-dropped). */
    private val knownWidgetInfos = mutableMapOf<Int, LauncherAppWidgetInfo>()

    private var stackInfo: WidgetStackInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoRotateRunnable: Runnable? = null
    private var refreshRunnable: Runnable? = null
    private var isRefreshing = false
    private var refreshAttempts = 0
    private var stackChangeListener: WidgetStackChangeListener? = null

    private val launcher: Launcher? = try {
        Launcher.getLauncher(context)
    } catch (_: Exception) {
        null
    }
    private val widgetHolder: LauncherWidgetHolder? = launcher?.appWidgetHolder
    private val widgetInflater: WidgetInflater? = launcher?.let { WidgetInflater(it) }

    // ──────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────

    override fun onFinishInflate() {
        super.onFinishInflate()

        viewPager = findViewById(R.id.widget_stack_pager)!!
        indicator = findViewById(R.id.widget_stack_page_indicator)!!

        viewPager.isSaveEnabled = false
        viewPager.isLongClickable = false
        viewPager.isClickable = true
        viewPager.offscreenPageLimit = 20

        isLongClickable = false
        isClickable = false

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, offset: Float, offsetPx: Int) {
                indicator.setPageOffset(position, offset)
            }

            override fun onPageSelected(position: Int) {
                stackInfo?.currentIndex = position
                if (stackInfo?.autoRotate == true) {
                    restartAutoRotate()
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })

        viewPager.adapter = adapter
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshAttempts = 0
        if (stackInfo?.autoRotate == true) startAutoRotate()
        scheduleRefreshIfNeeded()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoRotate()
        cancelRefresh()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        applyWidgetScaling()
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        super.setOnLongClickListener(null)
    }

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    fun setStackChangeListener(listener: WidgetStackChangeListener?) {
        stackChangeListener = listener
    }

    fun getStackInfo(): WidgetStackInfo? = stackInfo

    @JvmOverloads
    fun setStackInfo(info: WidgetStackInfo, knownWidgets: List<LauncherAppWidgetInfo> = emptyList()) {
        for (w in knownWidgets) knownWidgetInfos[w.appWidgetId] = w
        val oldInfo = stackInfo
        stackInfo = info

        if (::viewPager.isInitialized) {
            viewPager.offscreenPageLimit = info.widgetIds.size.coerceAtLeast(1)
        }

        rebuildWidgetViews(info)

        adapter.notifyDataSetChanged()
        indicator.setNumPages(info.size())

        val page = if (oldInfo != null && viewPager.currentItem < widgetViews.size) {
            viewPager.currentItem
        } else {
            info.currentIndex.coerceIn(0, (widgetViews.size - 1).coerceAtLeast(0))
        }
        if (::viewPager.isInitialized) viewPager.setCurrentItem(page, false)

        if (info.autoRotate) startAutoRotate() else stopAutoRotate()

        handler.post { applyWidgetScaling() }
        scheduleRefreshIfNeeded()
        notifyStackChanged()
    }

    fun addWidget(widgetInfo: LauncherAppWidgetInfo) {
        knownWidgetInfos[widgetInfo.appWidgetId] = widgetInfo
        addWidget(widgetInfo.appWidgetId)
    }

    fun addWidget(widgetId: Int) {
        val view = createWidgetView(widgetId) ?: return
        widgetViews.add(view)
        adapter.notifyDataSetChanged()

        stackInfo?.let { info ->
            val newIds = info.widgetIds + widgetId
            val updated = info.copy(widgetIds = newIds)
            stackInfo = updated
            indicator.setNumPages(newIds.size)
            saveStackToDb(updated)
            notifyStackChanged()
        }

        if (view is PendingAppWidgetHostView) scheduleRefreshIfNeeded()
    }

    fun removeWidget(widgetId: Int) {
        val idx = widgetViews.indexOfFirst { it.appWidgetId == widgetId }
        if (idx == -1) return

        widgetViews.removeAt(idx)
        adapter.notifyDataSetChanged()

        stackInfo?.let { info ->
            val newIds = info.widgetIds.filter { it != widgetId }

            if (newIds.size == 1) {
                getParentStackView()?.let { parent ->
                    stackChangeListener?.onStackShouldCollapse(parent, newIds.first())
                }
                return
            }

            val updated = info.copy(
                widgetIds = newIds,
                currentIndex = info.currentIndex.coerceIn(0, (newIds.size - 1).coerceAtLeast(0)),
            )
            stackInfo = updated
            indicator.setNumPages(newIds.size)
            saveStackToDb(updated)
            notifyStackChanged()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // View rebuilding
    // ──────────────────────────────────────────────────────────────

    private fun rebuildWidgetViews(info: WidgetStackInfo) {
        val existing = widgetViews.associateBy { it.appWidgetId }
        val ordered = mutableListOf<LauncherAppWidgetHostView>()

        for (widgetId in info.widgetIds) {
            val reused = existing[widgetId]
            if (reused != null) {
                ordered.add(reused)
                continue
            }
            val created = createWidgetView(widgetId)
            if (created != null) {
                ordered.add(created)
                continue
            }
            val placeholder = createPlaceholder(widgetId, info)
            if (placeholder != null) {
                ordered.add(placeholder)
            }
        }

        widgetViews.clear()
        widgetViews.addAll(ordered)
    }

    // ──────────────────────────────────────────────────────────────
    // Widget creation (single path)
    // ──────────────────────────────────────────────────────────────

    private fun createWidgetView(widgetId: Int): LauncherAppWidgetHostView? {
        val launcherInstance = launcher ?: return null
        val holder = widgetHolder ?: return null
        val inflater = widgetInflater ?: return null

        val widgetInfo = findWidgetInfo(widgetId) ?: return null

        var result = inflater.inflateAppWidget(widgetInfo)

        if (result.type == WidgetInflater.TYPE_DELETE) {
            result = tryRestoreWidget(widgetInfo, inflater) ?: return null
        }

        syncPositionToStack(widgetInfo, launcherInstance)

        return when (result.type) {
            WidgetInflater.TYPE_REAL -> {
                val provider = result.widgetInfo ?: return null
                // The holder can only create a fully-working widget view when it
                // is listening AND we are on the main thread.  Outside of those
                // conditions createView returns an empty placeholder that never
                // receives RemoteViews updates.  Create a PendingAppWidgetHostView
                // instead so that refreshPendingWidgets upgrades it later.
                if (!holder.isListening || Looper.myLooper() != Looper.getMainLooper()) {
                    val pendingView = PendingAppWidgetHostView(context, holder, widgetInfo, provider)
                    configureWidgetView(pendingView, widgetInfo)
                    return pendingView
                }
                val hostView = holder.createView(widgetId, provider) as? LauncherAppWidgetHostView
                    ?: return null
                hostView.setAppWidget(widgetId, provider)
                configureWidgetView(hostView, widgetInfo)
                hostView
            }

            WidgetInflater.TYPE_PENDING -> {
                val wmHelper = WidgetManagerHelper(context)
                val provider = result.widgetInfo
                    ?: wmHelper.findProvider(widgetInfo.providerName, widgetInfo.user)
                val pendingView = PendingAppWidgetHostView(context, holder, widgetInfo, provider)
                configureWidgetView(pendingView, widgetInfo)
                pendingView
            }

            else -> null
        }
    }

    private fun createPlaceholder(widgetId: Int, info: WidgetStackInfo): LauncherAppWidgetHostView? {
        val holder = widgetHolder ?: return null
        val widgetInfo = findWidgetInfo(widgetId) ?: return null
        val placeholder = PendingAppWidgetHostView(context, holder, widgetInfo, null)
        configureWidgetView(placeholder, widgetInfo)
        return placeholder
    }

    private fun configureWidgetView(view: LauncherAppWidgetHostView, widgetInfo: LauncherAppWidgetInfo) {
        view.tag = widgetInfo
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        view.isClickable = false
        view.isFocusable = false
        if (view is NavigableAppWidgetHostView) {
            view.post { applyScalingToWidget(view) }
        }
    }

    private fun findWidgetInfo(widgetId: Int): LauncherAppWidgetInfo? {
        knownWidgetInfos[widgetId]?.let { return it }
        val bgDataModel = launcher?.model?.getBgDataModel() ?: return null
        return synchronized(bgDataModel) {
            for (item in bgDataModel.itemsIdMap) {
                if (item is LauncherAppWidgetInfo && item.appWidgetId == widgetId) {
                    return@synchronized item
                }
            }
            null
        }
    }

    /**
     * Attempts to restore a widget whose providerName / targetComponent is missing
     * so that [WidgetInflater] no longer returns [WidgetInflater.TYPE_DELETE].
     */
    private fun tryRestoreWidget(
        widgetInfo: LauncherAppWidgetInfo,
        inflater: WidgetInflater,
    ): WidgetInflater.InflationResult? {
        if (widgetInfo.providerName != null && widgetInfo.targetComponent != null) return null

        val wmHelper = WidgetManagerHelper(context)
        return try {
            val provider = wmHelper.getLauncherAppWidgetInfo(
                widgetInfo.appWidgetId,
                widgetInfo.providerName,
            ) ?: return null
            widgetInfo.providerName = provider.getComponent()
            launcher?.modelWriter?.updateItemInDatabase(widgetInfo)
            val retry = inflater.inflateAppWidget(widgetInfo)
            if (retry.type == WidgetInflater.TYPE_DELETE) null else retry
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore widget ${widgetInfo.appWidgetId}", e)
            null
        }
    }

    /** Aligns a widget's persisted position to the current stack bounds. */
    private fun syncPositionToStack(
        widgetInfo: LauncherAppWidgetInfo,
        launcherInstance: Launcher,
    ) {
        val info = stackInfo ?: return
        val needsUpdate = widgetInfo.screenId != info.screenId ||
            widgetInfo.cellX != info.cellX ||
            widgetInfo.cellY != info.cellY ||
            widgetInfo.container != info.container ||
            widgetInfo.widgetStackId != info.stackId

        if (!needsUpdate) return

        widgetInfo.screenId = info.screenId
        widgetInfo.cellX = info.cellX
        widgetInfo.cellY = info.cellY
        widgetInfo.container = info.container
        widgetInfo.widgetStackId = info.stackId
        widgetInfo.sourceContainer = info.container

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

    // ──────────────────────────────────────────────────────────────
    // Scaling
    // ──────────────────────────────────────────────────────────────

    private fun applyWidgetScaling() {
        for (view in widgetViews) {
            if (view is NavigableAppWidgetHostView) applyScalingToWidget(view)
        }
    }

    private fun applyScalingToWidget(widgetView: NavigableAppWidgetHostView) {
        val profile = launcher?.deviceProfile ?: return
        val itemInfo = widgetView.tag as? com.android.launcher3.model.data.ItemInfo ?: return

        val scale = profile.getAppWidgetScale(itemInfo)
        widgetView.setScaleToFit(minOf(scale.x, scale.y))

        val w = widgetView.width.takeIf { it > 0 } ?: widgetView.measuredWidth
        val h = widgetView.height.takeIf { it > 0 } ?: widgetView.measuredHeight
        if (w > 0 && h > 0) {
            widgetView.translateDelegate.setTranslation(
                MultiTranslateDelegate.INDEX_WIDGET_CENTERING,
                -(w - w * scale.x) / 2f,
                -(h - h * scale.y) / 2f,
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Refresh pending → real
    // ──────────────────────────────────────────────────────────────

    private fun scheduleRefreshIfNeeded() {
        if (!isAttachedToWindow) return
        val info = stackInfo ?: return
        val hasPending = widgetViews.any { it is PendingAppWidgetHostView }
        val hasMissing = info.widgetIds.size > widgetViews.size
        if (!hasPending && !hasMissing) return
        handler.post { refreshPendingWidgets() }
    }

    private fun cancelRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
        isRefreshing = false
        refreshAttempts = 0
    }

    private fun refreshPendingWidgets() {
        if (!isAttachedToWindow || isRefreshing) return
        if (refreshAttempts >= MAX_REFRESH_ATTEMPTS) {
            cancelRefresh()
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
        val currentInfo = stackInfo ?: run {
            isRefreshing = false
            return
        }

        // Can't create real widget views until the host is listening on the main thread.
        if (!holder.isListening) {
            isRefreshing = false
            scheduleRetry(currentInfo)
            return
        }

        try {
            var changed = false

            // 1) Fill missing views
            val existingIds = widgetViews.map { it.appWidgetId }.toSet()
            for (widgetId in currentInfo.widgetIds) {
                if (widgetId in existingIds) continue
                val view = createWidgetView(widgetId) ?: createPlaceholder(widgetId, currentInfo)
                    ?: continue
                val pos = currentInfo.widgetIds.indexOf(widgetId)
                if (pos in widgetViews.indices) {
                    widgetViews[pos] = view
                } else {
                    while (widgetViews.size < pos) widgetViews.add(view)
                    widgetViews.add(view)
                }
                changed = true
            }

            // 2) Upgrade pending views that are now ready
            val snapshot = widgetViews.toList()
            for ((i, view) in snapshot.withIndex()) {
                if (view !is PendingAppWidgetHostView) continue
                val widgetInfo = view.tag as? LauncherAppWidgetInfo ?: continue

                val result = inflater.inflateAppWidget(widgetInfo)
                if (result.isUpdate) {
                    launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)
                }

                if (result.type == WidgetInflater.TYPE_REAL) {
                    val wmHelper = WidgetManagerHelper(context)
                    val provider = result.widgetInfo
                        ?: wmHelper.findProvider(widgetInfo.providerName, widgetInfo.user)
                        ?: continue
                    try {
                        val real = holder.createView(widgetInfo.appWidgetId, provider)
                            as? LauncherAppWidgetHostView ?: continue
                        real.setAppWidget(widgetInfo.appWidgetId, provider)
                        configureWidgetView(real, widgetInfo)
                        widgetViews[i] = real
                        changed = true
                        if (widgetInfo.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED) {
                            widgetInfo.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED
                            launcherInstance.modelWriter?.updateItemInDatabase(widgetInfo)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create real view for ${widgetInfo.appWidgetId}", e)
                    }
                }
            }

            if (changed) {
                adapter.notifyDataSetChanged()
                refreshAttempts = 0
            }

            // 3) Schedule retry if still needed
            val stillPending = widgetViews.any { it is PendingAppWidgetHostView }
            val stillMissing = currentInfo.widgetIds.size > widgetViews.size
            if (stillPending || stillMissing) {
                scheduleRetry(currentInfo)
            } else {
                refreshRunnable?.let { handler.removeCallbacks(it) }
                refreshRunnable = null
                refreshAttempts = 0
            }
        } finally {
            isRefreshing = false
        }
    }

    private fun scheduleRetry(currentInfo: WidgetStackInfo) {
        if (!isAttachedToWindow || refreshAttempts >= MAX_REFRESH_ATTEMPTS) return
        val delay = (500L * refreshAttempts).coerceAtMost(2000L)
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = Runnable { if (isAttachedToWindow) refreshPendingWidgets() }
        handler.postDelayed(refreshRunnable!!, delay)
    }

    // ──────────────────────────────────────────────────────────────
    // Auto-rotate
    // ──────────────────────────────────────────────────────────────

    private fun startAutoRotate() {
        stopAutoRotate()
        scheduleNextAutoRotate()
    }

    private fun stopAutoRotate() {
        autoRotateRunnable?.let { handler.removeCallbacks(it) }
        autoRotateRunnable = null
    }

    private fun restartAutoRotate() {
        stopAutoRotate()
        scheduleNextAutoRotate()
    }

    private fun scheduleNextAutoRotate() {
        val info = stackInfo ?: return
        if (!info.autoRotate || info.size() <= 1) return
        autoRotateRunnable = Runnable {
            if (!::viewPager.isInitialized) return@Runnable
            val next = (viewPager.currentItem + 1) % info.size()
            viewPager.setCurrentItem(next, true)
            scheduleNextAutoRotate()
        }.also { handler.postDelayed(it, AUTO_ROTATE_INTERVAL_MS) }
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private fun getParentStackView(): WidgetStackView? {
        var p = parent
        while (p != null) {
            if (p is WidgetStackView) return p
            p = p.parent
        }
        return null
    }

    private fun notifyStackChanged() {
        getParentStackView()?.let { stackChangeListener?.onStackChanged(it) }
    }

    private fun saveStackToDb(info: WidgetStackInfo) {
        launcher?.modelWriter?.saveWidgetStack(info)
    }

    // ──────────────────────────────────────────────────────────────
    // PagerAdapter
    // ──────────────────────────────────────────────────────────────

    private inner class WidgetStackAdapter : PagerAdapter() {

        override fun getCount(): Int = widgetViews.size

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun getItemPosition(obj: Any): Int {
            val idx = widgetViews.indexOf(obj)
            return if (idx >= 0) idx else POSITION_NONE
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val view: View = widgetViews.getOrNull(position) ?: run {
                View(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            }
            // Detach from previous parent if still attached (prevents IllegalStateException)
            (view.parent as? ViewGroup)?.removeView(view)
            container.addView(view)

            if (view is NavigableAppWidgetHostView) {
                applyScalingToWidget(view)
            }
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            (obj as? View)?.let { container.removeView(it) }
        }
    }
}
