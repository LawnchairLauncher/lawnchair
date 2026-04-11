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

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import app.lawnchair.smartspace.PageIndicator
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings
import com.android.launcher3.R
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.widget.WidgetCell
import com.android.launcher3.widget.WidgetManagerHelper
import kotlin.math.abs

/**
 * [ViewPager2] + [PageIndicator] for editing stack members: swipe between widgets, trailing **add**
 * page, long-press drag to reorder, and delete on each card. A touch listener on the pager calls
 * [android.view.ViewParent.requestDisallowInterceptTouchEvent] for horizontal drags so the bottom
 * sheet does not steal paging; [ViewPager2] is final so it cannot be subclassed like [InterceptingWidgetPager].
 */
class WidgetStackEditMembersHost(
    private val launcher: Launcher,
) : android.widget.LinearLayout(launcher) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var viewPager: ViewPager2

    private var pagerTouchInitialX = 0f
    private var pagerTouchInitialY = 0f
    private val pagerTouchSlop = ViewConfiguration.get(launcher).scaledTouchSlop
    private lateinit var indicator: PageIndicator

    /** Backing list for the adapter; must stay in sync with Compose after [onReorder]. */
    private val widgetIds = mutableListOf<Int>()

    private val adapter = EditAdapter()

    private val itemTouchHelper = ItemTouchHelper(
        object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0,
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
            ): Int {
                if (viewHolder.itemViewType == TYPE_ADD_PAGE) return makeMovementFlags(0, 0)
                if (widgetIds.isEmpty()) return makeMovementFlags(0, 0)
                return makeMovementFlags(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
            }

            override fun canDropOver(
                recyclerView: RecyclerView,
                current: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                if (target.itemViewType == TYPE_ADD_PAGE || current.itemViewType == TYPE_ADD_PAGE) {
                    return false
                }
                val to = target.bindingAdapterPosition
                val from = current.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                return to < widgetIds.size && from < widgetIds.size
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from >= widgetIds.size || to >= widgetIds.size) return false
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                val id = widgetIds.removeAt(from)
                widgetIds.add(to, id)
                adapter.notifyItemMoved(from, to)
                onReorder?.invoke(widgetIds.toList())
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        },
    )

    private var onRemove: ((Int) -> Unit)? = null
    private var onReorder: ((List<Int>) -> Unit)? = null
    private var onAddWidget: (() -> Unit)? = null
    private var onPageSelected: ((Int) -> Unit)? = null

    private var touchHelperAttached = false

    private data class PageTag(val widgetId: Int)

    init {
        orientation = VERTICAL
        LayoutInflater.from(launcher).inflate(R.layout.widget_stack_edit_members_inner, this, true)
        viewPager = requireNotNull(findViewById<ViewPager2>(R.id.stack_edit_pager))
        indicator = requireNotNull(findViewById<PageIndicator>(R.id.stack_edit_indicator))

        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        viewPager.isSaveEnabled = false
        viewPager.offscreenPageLimit = 20
        viewPager.adapter = adapter

        installPagerParentScrollHandoff()

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                    indicator.setPageOffset(position, positionOffset)
                }

                override fun onPageSelected(position: Int) {
                    if (position < widgetIds.size) {
                        onPageSelected?.invoke(position)
                    }
                }
            },
        )

        viewPager.post {
            if (touchHelperAttached) return@post
            val rv = viewPager.getChildAt(0) as? RecyclerView ?: return@post
            itemTouchHelper.attachToRecyclerView(rv)
            touchHelperAttached = true
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installPagerParentScrollHandoff() {
        viewPager.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pagerTouchInitialX = ev.x
                    pagerTouchInitialY = ev.y
                    viewPager.parent?.requestDisallowInterceptTouchEvent(false)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(ev.x - pagerTouchInitialX)
                    val dy = abs(ev.y - pagerTouchInitialY)
                    if (dx > pagerTouchSlop && dx > dy) {
                        viewPager.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    viewPager.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
    }

    fun bind(
        widgetIds: List<Int>,
        currentIndex: Int,
        onRemove: (Int) -> Unit,
        onReorder: (List<Int>) -> Unit,
        onAddWidget: () -> Unit,
        onPageSelected: (Int) -> Unit,
    ) {
        this.onRemove = onRemove
        this.onReorder = onReorder
        this.onAddWidget = onAddWidget
        this.onPageSelected = onPageSelected

        val newList = widgetIds.toList()
        val idsChanged = newList.size != this.widgetIds.size ||
            newList.indices.any { i -> newList[i] != this.widgetIds.getOrNull(i) }

        if (idsChanged) {
            this.widgetIds.clear()
            this.widgetIds.addAll(newList)
            adapter.notifyDataSetChanged()
        }

        indicator.setNumPages(adapter.pageCount())

        if (idsChanged) {
            when {
                widgetIds.isEmpty() -> {
                    viewPager.setCurrentItem(0, false)
                }

                else -> {
                    val idx = currentIndex.coerceIn(0, widgetIds.lastIndex)
                    viewPager.setCurrentItem(idx, false)
                }
            }
            indicator.setPageOffset(viewPager.currentItem, 0f)
        } else if (widgetIds.isNotEmpty()) {
            val want = currentIndex.coerceIn(0, widgetIds.lastIndex)
            if (viewPager.currentItem < widgetIds.size && viewPager.currentItem != want) {
                viewPager.setCurrentItem(want, false)
                indicator.setPageOffset(want, 0f)
            }
        }

        if (!touchHelperAttached) {
            viewPager.post {
                if (touchHelperAttached) return@post
                val rv = viewPager.getChildAt(0) as? RecyclerView ?: return@post
                itemTouchHelper.attachToRecyclerView(rv)
                touchHelperAttached = true
            }
        }
    }

    private fun widgetCellLayoutInflater(): LayoutInflater {
        val tv = TypedValue()
        return if (launcher.theme.resolveAttribute(R.attr.widgetsTheme, tv, true)) {
            LayoutInflater.from(ContextThemeWrapper(launcher, tv.resourceId))
        } else {
            LayoutInflater.from(launcher)
        }
    }

    private fun widgetItemForMember(widgetId: Int): WidgetItem? {
        val wi = synchronized(launcher.model.bgDataModel) {
            launcher.model.bgDataModel.itemsIdMap
                .firstOrNull { it is LauncherAppWidgetInfo && it.appWidgetId == widgetId } as? LauncherAppWidgetInfo
        } ?: return null
        val helper = WidgetManagerHelper(launcher)
        val providerInfo = helper.getLauncherAppWidgetInfo(wi.appWidgetId, wi.providerName) ?: return null
        val idp = LauncherAppState.getIDP(launcher)
        val iconCache = LauncherAppState.getInstance(launcher).iconCache
        return WidgetItem(providerInfo, idp, iconCache, launcher, helper)
    }

    private fun loadCell(widgetId: Int, root: View, container: FrameLayout) {
        container.removeAllViews()
        val progress = ProgressBar(launcher, null, android.R.attr.progressBarStyle).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
        }
        container.addView(progress)

        MODEL_EXECUTOR.execute {
            val item = widgetItemForMember(widgetId)
            mainHandler.post {
                if (!isAttachedToWindow) return@post
                if (widgetIds.indexOf(widgetId) < 0) return@post
                if ((root.tag as? PageTag)?.widgetId != widgetId) return@post

                container.removeAllViews()

                if (item != null) {
                    val cell = widgetCellLayoutInflater().inflate(R.layout.widget_cell, container, false) as WidgetCell
                    cell.setSourceContainer(LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY)
                    cell.isClickable = false
                    cell.isFocusable = false
                    cell.isLongClickable = false
                    cell.applyFromCellItem(item)
                    cell.hideAddButton(false)
                    container.addView(
                        cell,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                } else {
                    val scroll = ScrollView(launcher).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                    val tv = TextView(launcher).apply {
                        TextViewCompat.setTextAppearance(this, android.R.style.TextAppearance_Small)
                        setPadding(
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                            (12 * resources.displayMetrics.density).toInt(),
                        )
                        text = buildFallbackLabel(widgetId)
                    }
                    scroll.addView(tv)
                    container.addView(scroll)
                }
            }
        }
    }

    private fun buildFallbackLabel(widgetId: Int): String {
        val wi = synchronized(launcher.model.bgDataModel) {
            launcher.model.bgDataModel.itemsIdMap
                .firstOrNull { it is LauncherAppWidgetInfo && it.appWidgetId == widgetId } as? LauncherAppWidgetInfo
        }
        val label = if (wi != null) {
            val wmHelper = WidgetManagerHelper(launcher)
            wmHelper.getLauncherAppWidgetInfo(widgetId, wi.providerName)?.label
                ?: wi.providerName?.className?.substringAfterLast('.')
                ?: "Widget"
        } else {
            try {
                android.appwidget.AppWidgetManager.getInstance(launcher)
                    .getAppWidgetInfo(widgetId)?.loadLabel(launcher.packageManager)
            } catch (_: Exception) {
                null
            } ?: "Widget"
        }
        return "$label\n" + launcher.getString(R.string.widget_stack_member_id, widgetId)
    }

    private inner class WidgetPageVH(
        root: View,
    ) : RecyclerView.ViewHolder(root) {
        val cellContainer: FrameLayout = requireNotNull(root.findViewById(R.id.stack_edit_cell_container))
        val delete: ImageButton = requireNotNull(root.findViewById(R.id.stack_edit_delete))
    }

    private inner class AddPageVH(
        root: View,
    ) : RecyclerView.ViewHolder(root)

    private inner class EditAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        init {
            setHasStableIds(true)
        }

        fun pageCount(): Int = if (widgetIds.isEmpty()) 1 else widgetIds.size + 1

        override fun getItemCount(): Int = pageCount()

        override fun getItemId(position: Int): Long {
            if (widgetIds.isEmpty()) return ADD_PAGE_STABLE_ID
            return if (position < widgetIds.size) {
                widgetIds[position].toLong()
            } else {
                ADD_PAGE_STABLE_ID
            }
        }

        override fun getItemViewType(position: Int): Int {
            if (widgetIds.isEmpty()) return TYPE_ADD_PAGE
            return if (position == widgetIds.size) TYPE_ADD_PAGE else TYPE_WIDGET_PAGE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(launcher)
            return when (viewType) {
                TYPE_WIDGET_PAGE -> {
                    val v = inflater.inflate(R.layout.widget_stack_edit_member_page, parent, false)
                    WidgetPageVH(v)
                }

                else -> {
                    val v = inflater.inflate(R.layout.widget_stack_edit_add_page, parent, false)
                    val label = v.findViewById<TextView>(R.id.stack_edit_add_label)
                    if (label != null) {
                        TextViewCompat.setTextAppearance(label, android.R.style.TextAppearance_Small)
                    }
                    AddPageVH(v)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is WidgetPageVH -> {
                    if (position >= widgetIds.size) return
                    val widgetId = widgetIds[position]
                    holder.itemView.tag = PageTag(widgetId)
                    holder.delete.isVisible = widgetIds.size > 1
                    holder.delete.setOnClickListener { onRemove?.invoke(widgetId) }
                    loadCell(widgetId, holder.itemView, holder.cellContainer)
                }

                is AddPageVH -> {
                    holder.itemView.setOnClickListener { onAddWidget?.invoke() }
                }
            }
        }
    }

    private companion object {
        const val TYPE_WIDGET_PAGE = 0
        const val TYPE_ADD_PAGE = 1
        const val ADD_PAGE_STABLE_ID = Long.MAX_VALUE
    }
}
