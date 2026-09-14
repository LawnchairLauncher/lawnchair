/*
 * Copyright 2026, Renns Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.DropTarget
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.views.BaseDragLayer

/**
 * The handle a folder shows when it is held, and the size it is dragged into.
 *
 * A folder can occupy one cell, two side by side, two stacked, or a block of
 * four. The handle sits at the folder's outer corner: pulled sideways it makes
 * the folder wide, pulled down it makes it tall, pulled into the diagonal it
 * makes it square. What is being asked for is drawn as an outline over the
 * cells it would take, so the choice is visible before it is committed.
 *
 * It appears during the pre-drag: long-pressing a folder holds the drag back
 * until the finger travels, which is the same mechanism the app icon popup uses.
 * Carry on moving and the folder is dragged as it always was; let go without
 * moving and this stays up.
 */
class FolderResizeHint(context: Context, attrs: AttributeSet?) :
    AbstractFloatingView(context, attrs) {

    constructor(context: Context) : this(context, null)

    private val launcher = Launcher.getLauncher(context)

    private var folderIcon: FolderIcon? = null
    private var cellLayout: CellLayout? = null

    /** The folder's own cell, in the layer's coordinates, and one cell's size. */
    private val origin = RectF()
    private var cellWidth = 0f
    private var cellHeight = 0f

    private var spanX = 1
    private var spanY = 1
    private var cellX = 0
    private var cellY = 0
    private var containerLeft = 0f
    private var containerTop = 0f
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var startSpanX = 1
    private var startSpanY = 1

    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x66FFFFFF
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }

    private val density = context.resources.displayMetrics.density
    private val handleRadius = 9f * density
    private val corner = 18f * density

    init {
        outline.strokeWidth = 2f * density
        setWillNotDraw(false)
        // Without these the view is not a touch target at all, and every press
        // passes straight through to the workspace beneath it.
        isClickable = true
        isFocusable = true
    }

    private fun setup(icon: FolderIcon): Boolean {
        val layout = icon.parent?.parent as? CellLayout ?: return false
        val params = icon.layoutParams as? CellLayoutLayoutParams ?: return false

        folderIcon = icon
        cellLayout = layout
        spanX = params.cellHSpan.coerceAtLeast(1)
        spanY = params.cellVSpan.coerceAtLeast(1)

        // Taken from the cell layout's own idea of where a cell is, not derived
        // by dividing the folder's view. The view carries padding and a label,
        // so dividing it lands the outline off the grid it is meant to describe.
        cellX = params.cellX
        cellY = params.cellY
        val container = layout.shortcutsAndWidgets
        val containerRect = android.graphics.Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(container, containerRect)
        containerLeft = containerRect.left.toFloat()
        containerTop = containerRect.top.toFloat()

        val one = android.graphics.Rect()
        layout.cellToRect(cellX, cellY, 1, 1, one)
        cellWidth = one.width().toFloat()
        cellHeight = one.height().toFloat()
        if (cellWidth <= 0f || cellHeight <= 0f) return false
        origin.set(
            containerLeft + one.left,
            containerTop + one.top,
            containerLeft + one.right,
            containerTop + one.bottom,
        )
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val layout = cellLayout ?: return
        val cells = android.graphics.Rect()
        layout.cellToRect(cellX, cellY, spanX, spanY, cells)
        val rect = RectF(
            containerLeft + cells.left,
            containerTop + cells.top,
            containerLeft + cells.right,
            containerTop + cells.bottom,
        )
        canvas.drawRoundRect(rect, corner, corner, fill)
        canvas.drawRoundRect(rect, corner, corner, outline)
        canvas.drawCircle(rect.right, rect.bottom, handleRadius, handle)
    }

    /**
     * Nothing is claimed through the drag layer's controller chain; the touches
     * arrive at this view directly, which is what [onTouchEvent] handles.
     */
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    /**
     * Takes the whole gesture itself.
     *
     * This covers the drag layer, so every touch has to be answered here: a
     * grab on the handle starts a resize, and anything else dismisses. Letting
     * a touch fall through instead leaves a full-screen view sitting over the
     * workspace with no way to get rid of it, which is what it did before --
     * the press reached the folder underneath and the handle simply stayed.
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val right = origin.left + cellWidth * spanX
                val bottom = origin.top + cellHeight * spanY
                val reach = handleRadius * 3f
                val grabbed = Math.hypot((ev.x - right).toDouble(), (ev.y - bottom).toDouble()) < reach
                if (!grabbed) {
                    close(true)
                    return true
                }
                dragging = true
                downX = ev.x
                downY = ev.y
                startSpanX = spanX
                startSpanY = spanY
                // Nobody above gets to reinterpret this as a page swipe or a
                // pull from the shade once the handle has been taken hold of.
                parent?.requestDisallowInterceptTouchEvent(true)
                // Off the occupancy map for the length of the gesture. A folder
                // is always in its own way otherwise, and could never grow.
                folderIcon?.let { cellLayout?.markCellsAsUnoccupiedForView(it) }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true
                // Half a cell of travel is enough to ask for the next one.
                val across = startSpanX + ((ev.x - downX) / cellWidth + 0.5f).toInt()
                val down = startSpanY + ((ev.y - downY) / cellHeight + 0.5f).toInt()
                val nextX = across.coerceIn(1, 2)
                val nextY = down.coerceIn(1, 2)
                if ((nextX != spanX || nextY != spanY) && fits(nextX, nextY)) {
                    spanX = nextX
                    spanY = nextY
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    apply()
                }
                close(true)
            }
            else -> Unit
        }
        return true
    }

    /**
     * Whether the folder could actually take that much room.
     *
     * Checked while the finger is still moving rather than when it lifts. A
     * size that is refused on release is indistinguishable from a gesture that
     * did not work: the outline follows the finger, nothing happens, and the
     * direction looks broken. Refusing to grow in the first place says plainly
     * that the cells are taken.
     */
    private fun fits(candidateX: Int, candidateY: Int): Boolean {
        val layout = cellLayout ?: return false
        val params = folderIcon?.layoutParams as? CellLayoutLayoutParams ?: return false
        return layout.isRegionVacant(params.cellX, params.cellY, candidateX, candidateY)
    }

    private fun apply() {
        val icon = folderIcon ?: return
        val layout = cellLayout ?: return
        val params = icon.layoutParams as? CellLayoutLayoutParams ?: return
        val info = icon.mInfo ?: return
        if (params.cellHSpan == spanX && params.cellVSpan == spanY) {
            layout.markCellsAsOccupiedForView(icon)
            return
        }

        // Captured before the layout params change, so the plate has somewhere
        // to grow from once the cell layout has already moved the folder.
        val fromWidth = icon.folderBackground.plateWidth
        val fromHeight = icon.folderBackground.plateHeight

        params.cellHSpan = spanX
        params.cellVSpan = spanY
        info.spanX = spanX
        info.spanY = spanY
        layout.markCellsAsOccupiedForView(icon)

        launcher.modelWriter.modifyItemInDatabase(
            info, info.container, info.screenId, params.cellX, params.cellY, spanX, spanY,
        )

        // The plate's size is worked out while the preview is laid out, so the
        // preview is what has to be told the folder changed shape.
        icon.requestLayout()
        icon.onItemsChanged(false)
        // After the relayout, so the plate is asked to grow into a size it
        // already knows.
        icon.post { icon.folderBackground.animateResizeFrom(fromWidth, fromHeight) }
    }

    /**
     * Holds the drag back until the finger actually travels.
     *
     * Long-pressing a folder has to mean two things at once: show this, and stay
     * ready to move the folder. The pre-drag is how Launcher3 already reconciles
     * that for app icons -- the drag is armed but does not begin, so a press that
     * goes nowhere leaves the handle up and a press that turns into a swipe
     * becomes an ordinary drag.
     */
    fun preDragCondition(): DragOptions.PreDragCondition = object : DragOptions.PreDragCondition {

        override fun shouldStartDrag(distanceDragged: Double): Boolean =
            distanceDragged > ViewConfiguration.get(context).scaledTouchSlop

        override fun onPreDragStart(dragObject: DropTarget.DragObject) = Unit

        override fun onPreDragEnd(dragObject: DropTarget.DragObject, dragStarted: Boolean) {
            if (dragStarted) close(false)
        }
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        mIsOpen = false
        (parent as? ViewGroup)?.removeView(this)
        folderIcon = null
        cellLayout = null
    }

    override fun isOfType(type: Int): Boolean = (type and TYPE_FOLDER_RESIZE_HINT) != 0

    companion object {

        /**
         * Shows the handle over [icon], or does nothing if the folder is not
         * somewhere it can be resized.
         */
        @JvmStatic
        fun show(launcher: Launcher, icon: FolderIcon): FolderResizeHint? {
            if (icon.isInAppDrawer) return null
            val hint = FolderResizeHint(launcher)
            if (!hint.setup(icon)) return null
            hint.mIsOpen = true
            launcher.dragLayer.addView(
                hint,
                BaseDragLayer.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply { ignoreInsets = true },
            )
            return hint
        }
    }
}

/** Type bit for [AbstractFloatingView], picked clear of the ones Launcher3 uses. */
const val TYPE_FOLDER_RESIZE_HINT: Int = 1 shl 27
