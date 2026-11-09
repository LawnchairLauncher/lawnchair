package app.lawnchair.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.BubbleTextView
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.views.ActivityContext
import kotlin.math.hypot

/**
 * Draws an icon-shaped outline in every empty cell of a dock page and turns a tap on one into
 * the slot picker. Dragging onto an empty cell is untouched, it is still a normal drop target.
 */
class DockPlaceholders(private val layout: CellLayout) {

    private val context: Context = layout.context
    private val prefs = PreferenceManager.getInstance(context)
    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(0x26, 0xFF, 0xFF, 0xFF)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.argb(0x80, 0xFF, 0xFF, 0xFF)
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val tapTimeout = ViewConfiguration.getLongPressTimeout()
    private val cell = Rect()
    private val icon = Rect()
    private val downCell = IntArray(2)
    private var downX = 0f
    private var downY = 0f
    private var tracking = false

    private val enabled get() = prefs.dockPlaceholders.get()

    /** Draws placeholders; failures are logged so drawing can never crash the launcher. */
    fun draw(canvas: Canvas) {
        try {
            drawPlaceholders(canvas)
        } catch (e: RuntimeException) {
            Log.e(TAG, "placeholder draw failed", e)
        }
    }

    /** Draws an icon-shaped fill and outline in every empty cell. */
    private fun drawPlaceholders(canvas: Canvas) {
        if (!enabled) return
        val shape = ThemeManager.INSTANCE.get(context).iconShape
        val offset = iconOffset()
        for (x in 0 until layout.countX) {
            for (y in 0 until layout.countY) {
                if (layout.isCellOccupied(x, y)) continue
                iconRect(x, y, offset)
                val path = shape.getPath(icon)
                canvas.drawPath(path, fill)
                canvas.drawPath(path, stroke)
            }
        }
    }

    /** Handles a touch on an empty cell; failures are logged and the touch is released. */
    fun onTouchEvent(ev: MotionEvent): Boolean = try {
        handleTouch(ev)
    } catch (e: RuntimeException) {
        Log.e(TAG, "placeholder touch failed", e)
        tracking = false
        false
    }

    /** Tracks a tap that starts and ends on the same empty cell and opens the slot picker for it. */
    private fun handleTouch(ev: MotionEvent): Boolean {
        if (!enabled) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layout.pointToCellExact(ev.x.toInt(), ev.y.toInt(), downCell)
                tracking = downCell[0] in 0 until layout.countX && downCell[1] in 0 until layout.countY &&
                    !layout.isCellOccupied(downCell[0], downCell[1])
                downX = ev.x
                downY = ev.y
                return tracking
            }
            MotionEvent.ACTION_MOVE -> if (tracking && hypot(ev.x - downX, ev.y - downY) > touchSlop) {
                tracking = false
            }
            MotionEvent.ACTION_UP -> if (tracking) {
                tracking = false
                if (ev.eventTime - ev.downTime < tapTimeout) {
                    val launcher = ActivityContext.lookupContextNoThrow(context) as? Launcher
                    launcher?.let { DockSlotPicker.show(it, layout.hotseatPageIndex, downCell[0], downCell[1]) }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return tracking
    }

    /** Where the icon sits inside its cell, taken from a real dock icon so labels or padding match. */
    private fun iconOffset(): Rect? {
        val container = layout.shortcutsAndWidgets
        for (i in 0 until container.childCount) {
            val btv = container.getChildAt(i) as? BubbleTextView ?: continue
            val lp = btv.layoutParams as? CellLayoutLayoutParams ?: continue
            layout.cellToRect(lp.cellX, lp.cellY, 1, 1, cell)
            btv.getIconBounds(icon)
            icon.offset(container.left + btv.left - cell.left, container.top + btv.top - cell.top)
            return Rect(icon)
        }
        return null
    }

    /** Sets [icon] to where an icon would sit in cell [x], [y]. */
    private fun iconRect(x: Int, y: Int, offset: Rect?) {
        layout.cellToRect(x, y, 1, 1, cell)
        if (offset != null) {
            icon.set(offset)
            icon.offset(cell.left, cell.top)
        } else {
            val size = (ActivityContext.lookupContext(context) as ActivityContext).deviceProfile.iconSizePx
            icon.set(0, 0, size, size)
            icon.offset(cell.centerX() - size / 2, cell.centerY() - size / 2)
        }
    }

    private companion object {
        const val TAG = "DockPlaceholders"
    }
}
