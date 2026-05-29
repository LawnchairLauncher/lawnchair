package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.app.animation.Interpolators
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.OnboardingPrefs
import com.android.launcher3.views.ArrowTipView

class EditModePageStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {

    private val pageStripAdapter = PageStripAdapter()
    private var workspace: Workspace<*>? = null
    private var educationTip: ArrowTipView? = null
    private val showEducationTipRunnable = Runnable { showEducationTipIfNeeded() }

    init {
        layoutManager = LinearLayoutManager(context, HORIZONTAL, false)
        overScrollMode = OVER_SCROLL_NEVER
        itemAnimator = DefaultItemAnimator().apply {
            moveDuration = 200
            changeDuration = 150
            supportsChangeAnimations = true
        }
        adapter = pageStripAdapter
        ItemTouchHelper(DragCallback()).attachToRecyclerView(this)
    }

    fun bindWorkspace(workspace: Workspace<*>) {
        this.workspace = workspace
        refreshItems()
    }

    fun showForEditMode(workspace: Workspace<*>, duration: Long) {
        cancelVisibilityAnimation()
        bindWorkspace(workspace)
        if (duration <= 0) {
            applyVisibleState()
            return
        }
        val offset = stripSlideOffset()
        visibility = VISIBLE
        alpha = 0f
        translationY = offset
        animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(Interpolators.DECELERATE_2)
            .start()
    }

    fun maybeShowEducationTip(launcher: Launcher) {
        if (OnboardingPrefs.EDIT_MODE_PAGE_STRIP_TIP_SEEN.get(launcher)) {
            return
        }
        if (visibility != VISIBLE) {
            return
        }
        removeCallbacks(showEducationTipRunnable)
        postDelayed(showEducationTipRunnable, 150)
    }

    fun dismissEducationTip() {
        removeCallbacks(showEducationTipRunnable)
        educationTip?.close(true)
        educationTip = null
    }

    private fun showEducationTipIfNeeded() {
        if (visibility != VISIBLE || width == 0) {
            return
        }
        val launcher = context as? Launcher ?: return
        if (OnboardingPrefs.EDIT_MODE_PAGE_STRIP_TIP_SEEN.get(launcher)) {
            return
        }
        val bounds = Utilities.getViewBounds(this)
        val margin = resources.getDimensionPixelSize(R.dimen.edit_mode_page_strip_tip_margin)
        val tip =
            ArrowTipView(launcher, false).showAtLocation(
                resources.getString(R.string.edit_mode_page_strip_tip),
                bounds.centerX(),
                bounds.top - margin,
            ) ?: return
        educationTip = tip
        LauncherPrefs.get(launcher).put(OnboardingPrefs.EDIT_MODE_PAGE_STRIP_TIP_SEEN, true)
    }

    fun hideForEditMode(duration: Long) {
        dismissEducationTip()
        cancelVisibilityAnimation()
        if (duration <= 0) {
            applyHiddenState()
            return
        }
        val offset = stripSlideOffset()
        animate()
            .alpha(0f)
            .translationY(offset)
            .setDuration(duration)
            .setInterpolator(Interpolators.ACCELERATE)
            .withEndAction { applyHiddenState() }
            .start()
    }

    private fun cancelVisibilityAnimation() {
        animate().cancel()
    }

    private fun stripSlideOffset(): Float {
        if (height > 0) {
            return height.toFloat()
        }
        return resources.getDimension(R.dimen.edit_mode_page_strip_height)
    }

    private fun applyVisibleState() {
        visibility = VISIBLE
        alpha = 1f
        translationY = 0f
    }

    private fun applyHiddenState() {
        visibility = GONE
        alpha = 1f
        translationY = 0f
    }

    fun refreshItems() {
        val ws = workspace
        if (ws == null) {
            pageStripAdapter.setItems(emptyList())
            return
        }
        val starts = ws.reorderablePageGroupStarts
        val defaultGroupIndex = ws.defaultPageGroupIndex
        val items = (0 until starts.size()).map { i ->
            PageItem(isDefault = i == defaultGroupIndex)
        }
        pageStripAdapter.setItems(items)
    }

    private fun moveGroup(from: Int, to: Int): Boolean {
        val ws = workspace ?: return false
        if (!ws.moveReorderablePageGroup(from, to)) {
            return false
        }
        pageStripAdapter.moveItem(from, to)
        return true
    }

    private fun setHomeGroup(position: Int): Boolean {
        val ws = workspace ?: return false
        val oldDefaultIndex = pageStripAdapter.indexOfDefault()
        val set = ws.setDefaultPageForReorderableGroup(position)
        if (!set) {
            return false
        }
        pageStripAdapter.updateDefaultHome(oldDefaultIndex, position)
        dismissEducationTip()
        return true
    }

    private fun resetDragView(view: View) {
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationZ(0f)
            .setDuration(150)
            .start()
    }

    private inner class DragCallback :
        ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0,
        ) {
        override fun isLongPressDragEnabled(): Boolean = true

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: ViewHolder,
            target: ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == NO_POSITION || to == NO_POSITION) {
                return false
            }
            return moveGroup(from, to)
        }

        override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
            // No-op.
        }

        override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
            val itemView = viewHolder?.itemView ?: return
            when (actionState) {
                ItemTouchHelper.ACTION_STATE_DRAG -> {
                    dismissEducationTip()
                    itemView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .translationZ(8f)
                        .setDuration(150)
                        .start()
                }

                else -> resetDragView(itemView)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            resetDragView(viewHolder.itemView)
        }
    }

    private inner class PageStripAdapter : Adapter<PageStripViewHolder>() {
        private val items = mutableListOf<PageItem>()

        fun setItems(newItems: List<PageItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        fun moveItem(from: Int, to: Int) {
            if (from == to || from !in items.indices || to !in items.indices) {
                return
            }
            val item = items.removeAt(from)
            items.add(to, item)
            notifyItemMoved(from, to)
            val start = minOf(from, to)
            notifyItemRangeChanged(start, maxOf(from, to) - start + 1)
        }

        fun indexOfDefault(): Int = items.indexOfFirst { it.isDefault }

        fun updateDefaultHome(oldIndex: Int, newIndex: Int) {
            if (oldIndex in items.indices) {
                items[oldIndex] = items[oldIndex].copy(isDefault = false)
                notifyItemChanged(oldIndex)
            }
            if (newIndex in items.indices) {
                items[newIndex] = items[newIndex].copy(isDefault = true)
                notifyItemChanged(newIndex)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageStripViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.edit_mode_page_strip_item, parent, false)
            return PageStripViewHolder(view)
        }

        override fun onBindViewHolder(holder: PageStripViewHolder, position: Int) {
            val item = items[position]
            holder.itemView.isSelected = item.isDefault
            holder.title.text = if (item.isDefault) {
                resources.getString(R.string.edit_mode_page_strip_default)
            } else {
                resources.getString(R.string.edit_mode_page_strip_page, position + 1)
            }
            holder.homeButton.isSelected = item.isDefault
            holder.homeButton.contentDescription = if (item.isDefault) {
                resources.getString(R.string.edit_mode_page_strip_home_selected)
            } else {
                resources.getString(R.string.edit_mode_page_strip_set_home)
            }
            holder.homeButton.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != NO_POSITION) {
                    setHomeGroup(index)
                }
            }
            holder.itemView.setOnClickListener {
                val ws = workspace ?: return@setOnClickListener
                val starts = ws.reorderablePageGroupStarts
                val index = holder.bindingAdapterPosition
                if (index in 0 until starts.size()) {
                    ws.snapToPage(starts[index])
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class PageStripViewHolder(itemView: View) : ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.page_strip_title)
        val homeButton: ImageButton = itemView.findViewById(R.id.page_strip_home_button)
    }

    private data class PageItem(
        val isDefault: Boolean,
    )
}
