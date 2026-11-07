package app.lawnchair.fold

import android.graphics.Rect
import android.view.Gravity
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Reorderable
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.util.MultiTranslateDelegate
import com.android.launcher3.widget.NavigableAppWidgetHostView
import kotlin.math.abs
import kotlin.math.min

/**
 * A home screen item (icon, folder or widget) on a visible workspace page, seen by the per-item
 * style (WAVE).
 *
 * Styles move items only through the [TRANSLATE_INDEX] channel of their [MultiTranslateDelegate],
 * which adds to every other translation (reorder, the dock merge, page pairing) instead of replacing it.
 * That channel belongs to AOSP's unfold animation, which never runs while this engine does (the
 * engine is off when Lawnchair is the recents provider).
 *
 * @property state the style's own state for this item (Wave's glow), created when the item is
 * first seen.
 */
class FoldItem<S> internal constructor(
    val view: View,
    val state: S,
) {
    val isWidget = view is NavigableAppWidgetHostView
    private val delegate: MultiTranslateDelegate? = (view as? Reorderable)?.translateDelegate

    /** Center of the item, in root coordinates, without the style's own translation. */
    var centerX = 0f
        internal set
    var centerY = 0f
        internal set

    /** Center of the icon itself (the label excluded), relative to [centerX]/[centerY]. */
    var iconOffsetX = 0f
        internal set
    var iconOffsetY = 0f
        internal set

    /** Half the icon size, for glows and shadows. */
    var iconRadius = 0f
        internal set

    /** Whether the item is on the newly revealed half. Always false on the cover layout. */
    var onMovingHalf = false
        internal set

    /** Distance from the hinge, 0 at the hinge and 1 at the free edge of the item's half. */
    var distance = 0f
        internal set

    internal var seen = false

    private var appliedX = 0f
    private var appliedY = 0f

    fun setTranslation(x: Float, y: Float) {
        val delegate = delegate ?: return
        if (x == appliedX && y == appliedY) return
        appliedX = x
        appliedY = y
        delegate.setTranslation(TRANSLATE_INDEX, x, y)
    }

    fun resetTranslation() = setTranslation(0f, 0f)

    companion object {
        const val TRANSLATE_INDEX = MultiTranslateDelegate.INDEX_MOVE_FROM_CENTER_ANIM
    }
}

/**
 * Keeps the list of items on the visible workspace pages, rescanned before every frame so icons
 * bound after the DeviceProfile change are picked up on the frame they first show. Only the pages
 * on screen are scanned, and each view's [FoldItem] is found by the view. Allocates only when a new
 * view appears (its state comes from [newState]); removed views are handed to [onRemoved] so the
 * style can reset them.
 */
class FoldItemTracker<S>(
    private val newState: () -> S,
    private val onRemoved: (FoldItem<S>) -> Unit,
) {
    val items = ArrayList<FoldItem<S>>()

    private val byView = HashMap<View, FoldItem<S>>()
    private val pages = ArrayList<CellLayout>()
    private val tmpRect = Rect()
    private val position = FloatArray(2)

    fun update(scene: FoldScene) {
        val root = scene.rootView
        val geometry = scene.geometry
        if (!geometry.isValid) return
        for (i in items.indices) items[i].seen = false
        scene.getVisiblePages(pages)
        for (i in pages.indices) {
            val container = pages[i].shortcutsAndWidgets
            for (j in 0 until container.childCount) {
                val child = container.getChildAt(j)
                if (child.visibility != View.VISIBLE || !centerInRoot(child, root)) continue
                val x = position[0]
                val y = position[1]
                if (x < 0f || y < 0f || x > geometry.width || y > geometry.height) continue
                val item = byView[child] ?: FoldItem(child, newState()).also {
                    items.add(it)
                    byView[child] = it
                }
                item.seen = true
                item.centerX = x
                item.centerY = y
                measureIcon(item)
                place(item, scene)
            }
        }
        for (i in items.size - 1 downTo 0) {
            val item = items[i]
            if (!item.seen) remove(i)
        }
    }

    /** Hands every item to [onRemoved] and forgets them. */
    fun clear() {
        for (i in items.size - 1 downTo 0) remove(i)
    }

    private fun remove(index: Int) {
        val item = items.removeAt(index)
        byView.remove(item.view)
        onRemoved(item)
    }

    /** Layout position of [view]'s center in [root], ignoring the view's own translation and every scale. */
    private fun centerInRoot(view: View, root: View): Boolean {
        var x = view.left + view.width / 2f
        var y = view.top + view.height / 2f
        var parent = view.parent as? View
        while (parent != null && parent !== root) {
            x += parent.left - parent.scrollX + parent.translationX
            y += parent.top - parent.scrollY + parent.translationY
            parent = parent.parent as? View
        }
        position[0] = x
        position[1] = y
        return parent === root
    }

    private fun measureIcon(item: FoldItem<S>) {
        val view = item.view
        when (view) {
            is BubbleTextView -> view.getIconBounds(tmpRect)
            is FolderIcon -> view.getPreviewBounds(tmpRect)
            else -> tmpRect.set(0, 0, view.width, view.height)
        }
        if (tmpRect.isEmpty) tmpRect.set(0, 0, view.width, view.height)
        item.iconOffsetX = tmpRect.exactCenterX() - view.width / 2f
        item.iconOffsetY = tmpRect.exactCenterY() - view.height / 2f
        item.iconRadius = min(tmpRect.width(), tmpRect.height()) / 2f
    }

    private fun place(item: FoldItem<S>, scene: FoldScene) {
        val geometry = scene.geometry
        val along = if (geometry.isVerticalHinge) item.centerX else item.centerY
        if (scene.largeLayout) {
            item.onMovingHalf = geometry.movingRect.contains(item.centerX.toInt(), item.centerY.toInt())
            item.distance = (abs(along - geometry.hingePosition) / geometry.halfSpan).coerceIn(0f, 1f)
        } else {
            // Cover layout: the hinge is the screen edge on the moving side.
            val extent = if (geometry.isVerticalHinge) geometry.width.toFloat() else geometry.height.toFloat()
            val fromHinge = when (geometry.movingEdge) {
                Gravity.LEFT, Gravity.TOP -> along
                else -> extent - along
            }
            item.onMovingHalf = false
            item.distance = (fromHinge / extent).coerceIn(0f, 1f)
        }
    }
}
