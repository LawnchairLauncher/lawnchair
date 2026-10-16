package app.lawnchair.hotseat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewTreeObserver
import app.lawnchair.fold.FoldDockLayout
import app.lawnchair.fold.FoldLayout
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Hotseat
import com.android.launcher3.Reorderable
import com.android.launcher3.celllayout.CellLayoutLayoutParams
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.util.MultiTranslateDelegate.INDEX_FOLD_DOCK_ANIM
import com.android.launcher3.views.ActivityContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Merges the dock of an unfolded foldable ([FoldDockLayout]) as the device opens, and splits it
 * as it closes.
 *
 * At progress 0 the dock looks like the cover dock on the fixed half: its icons at their cover
 * positions with the cover's background pill, and the extra icons outside the screen with their
 * own pill. Towards 1 the cover icons settle into their cells, the extra icons arrive with the
 * moving half, both pills stretch to the hinge and meet, and the dock search bar fades in. At 1
 * nothing is left over: the dock is exactly its normal layout.
 *
 * With the side dock ([FoldDockLayout.isSide]) the cover's bottom dock goes around the bottom
 * outer corner into the column on the fixed half's edge, and its pill follows (see
 * computeSideGeometry).
 *
 * It only moves existing views (one translation channel per icon) and draws the pills from
 * [Hotseat.dispatchDraw], with no allocation per frame. The work is done in a pre-draw listener
 * so that icons bound after the DeviceProfile change, and the layout of the new pages, are picked
 * up on the frame they first show.
 *
 * The progress comes from the fold transition engine (FoldTransitionController), which reports
 * a fold while the large screen is still shown.
 */
class HotseatFoldController(
    private val hotseat: Hotseat,
    private val activity: ActivityContext,
) : DeviceProfile.OnDeviceProfileChangeListener,
    ViewTreeObserver.OnPreDrawListener {

    private var isActive = false
    private var progress = 1f
    private var hasTransforms = false

    // Geometry of the current dock, computed once per page layout
    private var preparedPage: CellLayout? = null
    private var preparedWidth = 0
    private var foldDock: FoldDockLayout? = null
    private var cellCount = 0
    private var finalCenters = FloatArray(0)
    private var cellOffsets = FloatArray(0)
    private var cellOffsetsY = FloatArray(0)
    private val tmpRect = Rect()

    // Background pills, in hotseat coordinates
    private var hasBackgroundStyle = false
    private var drawsPills = false
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pillRadius = 0f
    private val pillInsets = Rect()
    private val pillPath = Path()
    private val pillRect = RectF()
    private val pillRadii = FloatArray(8)
    private var pillTop = 0f
    private var pillBottom = 0f
    private var fixedStartLeft = 0f
    private var fixedStartRight = 0f
    private var fixedEndLeft = 0f
    private var fixedEndRight = 0f
    private var extraLeft = 0f
    private var extraRight = 0f
    private var extraShift = 0f
    private var sideStartTop = 0f
    private var sideStartBottom = 0f
    private val sideEndRect = RectF()

    /** Sets the dock background drawn as pills during the transition (see Hotseat#setUpBackground). */
    fun setBackgroundStyle(
        color: Int,
        radius: Float,
        insetLeft: Int,
        insetTop: Int,
        insetRight: Int,
        insetBottom: Int,
    ) {
        pillPaint.color = color
        pillRadius = radius
        pillInsets.set(insetLeft, insetTop, insetRight, insetBottom)
        hasBackgroundStyle = true
    }

    fun onDetached() {
        onFoldEnd(true)
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        // Drop the old layout's offsets before the hotseat applies the new profile (setInsets
        // decides the search bar's visibility after this)
        clearTransforms()
        preparedPage = null
        foldDock = null
    }

    fun onFoldStart(opening: Boolean) {
        progress = if (opening) 0f else 1f
        if (!isActive) {
            isActive = true
            hotseat.viewTreeObserver.addOnPreDrawListener(this)
        }
        hotseat.invalidate()
    }

    fun onFoldProgress(progress: Float, opening: Boolean) {
        if (!isActive) onFoldStart(opening)
        this.progress = progress.coerceIn(0f, 1f)
        hotseat.invalidate()
    }

    fun onFoldEnd(opening: Boolean) {
        if (!isActive) return
        isActive = false
        hotseat.viewTreeObserver.removeOnPreDrawListener(this)
        clearTransforms()
        preparedPage = null
        foldDock = null
        hotseat.invalidate()
    }

    override fun onPreDraw(): Boolean {
        if (!isActive) return true
        if (activity.getDragController<DragController<*>>()?.isDragging == true) {
            // An icon picked up mid-transition must not keep its offset
            onFoldEnd(true)
            return true
        }
        if (prepare()) applyTransforms() else clearTransforms()
        return true
    }

    /**
     * Draws the background pills under the icons while the dock merges or splits. Returns whether
     * it drew the dock background (the side dock's resting background is drawn by the hotseat).
     */
    fun drawBackground(canvas: Canvas): Boolean {
        if (!hasTransforms || !drawsPills) return false
        val layout = foldDock ?: return false
        if (layout.isSide) {
            drawSidePill(canvas)
            return true
        }
        val p = progress
        val hasExtras = layout.extraIcons > 0
        val innerRadius = if (hasExtras) pillRadius * (1f - p) else pillRadius
        var fixedLeft = lerp(fixedStartLeft, fixedEndLeft, p)
        var fixedRight = lerp(fixedStartRight, fixedEndRight, p)
        pillPath.rewind()
        if (!hasExtras) {
            addPill(fixedLeft, fixedRight, pillRadius, pillRadius)
        } else {
            val shift = (1f - p) * extraShift
            val left = extraLeft + shift
            val right = extraRight + shift
            if (layout.isMovingHalfLeft) {
                fixedLeft = max(fixedLeft, right)
                addPill(left, right, pillRadius, innerRadius)
                addPill(fixedLeft, fixedRight, innerRadius, pillRadius)
            } else {
                fixedRight = min(fixedRight, left)
                addPill(fixedLeft, fixedRight, pillRadius, innerRadius)
                addPill(left, right, innerRadius, pillRadius)
            }
        }
        canvas.drawPath(pillPath, pillPaint)
        return true
    }

    private fun addPill(left: Float, right: Float, leftRadius: Float, rightRadius: Float) {
        if (right <= left) return
        pillRect.set(left, pillTop, right, pillBottom)
        pillRadii[0] = leftRadius
        pillRadii[1] = leftRadius
        pillRadii[2] = rightRadius
        pillRadii[3] = rightRadius
        pillRadii[4] = rightRadius
        pillRadii[5] = rightRadius
        pillRadii[6] = leftRadius
        pillRadii[7] = leftRadius
        pillPath.addRoundRect(pillRect, pillRadii, Path.Direction.CW)
    }

    /** Computes the geometry of the current dock once its pages are laid out. */
    private fun prepare(): Boolean {
        val page = hotseat.pagedView.getCurrentCellLayout() ?: return false
        if (page === preparedPage && hotseat.width == preparedWidth) return foldDock != null
        if (hotseat.isLayoutRequested || hotseat.width == 0 || page.width == 0) return false
        preparedPage = page
        preparedWidth = hotseat.width
        // Before the rebind the pages still have the previous screen's grid
        val layout = hotseat.foldDockLayout?.takeIf {
            if (it.isSide) page.countX == 1 && page.countY == it.dockIcons else page.countX == it.dockIcons
        }
        foldDock = layout
        if (layout == null) return false
        computeGeometry(layout, page)
        return true
    }

    private fun computeGeometry(layout: FoldDockLayout, page: CellLayout) {
        val pagedView = hotseat.pagedView
        val count = layout.dockIcons
        if (finalCenters.size < count) {
            finalCenters = FloatArray(count)
            cellOffsets = FloatArray(count)
            cellOffsetsY = FloatArray(count)
        }
        cellCount = count
        val container = pagedView.parent as View
        val pageX = container.left + pagedView.left + page.left - pagedView.scrollX
        val pageY = container.top + pagedView.top + page.top

        // Where the cover dock's icons sit, with the cover screen scaled to fill the fixed half
        // as the drawer places it (FoldLayout.coverScaleOnLarge)
        val width = hotseat.width.toFloat()
        val coverIcons = layout.coverIcons
        val cover = FoldLayout.coverProfileOf(activity.deviceProfile)
        val coverWidth = width / 2f
        val coverFirstCenter: Float
        val coverStep: Float
        if (cover != null) {
            val scale = FoldLayout.coverScaleOnLarge(cover, activity.deviceProfile)
            val padding = cover.getHotseatLayoutPadding(hotseat.context)
            val contentWidth = cover.deviceProperties.widthPx - padding.left - padding.right
            val border = cover.hotseatBorderSpace
            val cellWidth = DeviceProfile.calculateCellWidth(contentWidth, border, coverIcons)
            val unused = contentWidth - coverIcons * cellWidth - (coverIcons - 1) * border
            coverStep = (cellWidth + border) * scale
            coverFirstCenter = (padding.left + ceil(unused / 2f) + cellWidth / 2f) * scale
        } else {
            coverStep = coverWidth / coverIcons
            coverFirstCenter = coverStep / 2f
        }
        val movingLeft = layout.isMovingHalfLeft
        val fixedOrigin = if (movingLeft) width - coverWidth else 0f
        drawsPills = hasBackgroundStyle && (layout.isSide || hotseat.background != null)
        if (movingLeft) {
            fixedStartLeft = width - coverWidth + pillInsets.left
            fixedStartRight = width - pillInsets.right
        } else {
            fixedStartLeft = pillInsets.left.toFloat()
            fixedStartRight = coverWidth - pillInsets.right
        }

        if (layout.isSide) {
            computeSideGeometry(layout, page, cover, fixedOrigin + coverFirstCenter, coverStep, pageX, pageY)
            return
        }

        for (x in 0 until count) {
            page.cellToRect(x, 0, 1, 1, tmpRect)
            finalCenters[x] = pageX + tmpRect.exactCenterX()
        }
        // The extra icons start outside the screen, with the moving half
        extraShift = if (movingLeft) -width / 2f else width / 2f
        for (x in 0 until count) {
            val slot = layout.coverSlotOf(x)
            cellOffsets[x] = if (slot >= 0) {
                fixedOrigin + coverFirstCenter + slot * coverStep - finalCenters[x]
            } else {
                extraShift
            }
            cellOffsetsY[x] = 0f
        }

        if (!drawsPills) return
        pillTop = pillInsets.top.toFloat()
        pillBottom = (hotseat.height - pillInsets.bottom).toFloat()
        val backgroundLeft = pillInsets.left.toFloat()
        val backgroundRight = width - pillInsets.right
        val extras = layout.extraIcons
        if (extras <= 0) {
            fixedEndLeft = backgroundLeft
            fixedEndRight = backgroundRight
            return
        }
        // The pills meet between the last extra cell and the first cover cell, on a whole
        // pixel so the seam doesn't show when they touch
        val (before, after) = if (movingLeft) extras - 1 to extras else coverIcons - 1 to coverIcons
        val mergeX = ((finalCenters[before] + finalCenters[after]) / 2f).roundToInt().toFloat()
        if (movingLeft) {
            fixedEndLeft = mergeX
            fixedEndRight = backgroundRight
            extraLeft = backgroundLeft
            extraRight = mergeX
        } else {
            fixedEndLeft = backgroundLeft
            fixedEndRight = mergeX
            extraLeft = mergeX
            extraRight = backgroundRight
        }
    }

    /**
     * The side dock ([FoldDockLayout.isSide]): at progress 0 its icons sit in the cover's bottom
     * dock on the fixed half, the extra ones continuing that row towards the hinge. Each icon goes
     * around the bottom outer corner on a quadratic curve whose control point is the corner below
     * the column: along the bottom first, then up the edge. The x and y orders are kept at every
     * moment, so no two icons ever cross.
     */
    private fun computeSideGeometry(
        layout: FoldDockLayout,
        page: CellLayout,
        cover: DeviceProfile?,
        coverFirstX: Float,
        coverStep: Float,
        pageX: Int,
        pageY: Int,
    ) {
        val height = hotseat.height.toFloat()
        val dp = activity.deviceProfile
        val coverBarHeight = (cover ?: dp).hotseatBarSizePx.toFloat()
        val startY = if (cover != null) {
            height - coverBarHeight + cover.getHotseatLayoutPadding(hotseat.context).top +
                cover.hotseatCellHeightPx / 2f
        } else {
            height - dp.hotseatBarSizePx - dp.hotseatCellHeightPx / 2f
        }
        // Virtual cover slot of a rank: the merge order, with the cover's slots from 0
        val slotShift = if (layout.isMovingHalfLeft) layout.extraIcons else 0
        for (y in 0 until layout.dockIcons) {
            page.cellToRect(0, y, 1, 1, tmpRect)
            val rank = layout.cellYToRank(y)
            val startX = coverFirstX + (layout.rankToCellX(rank) - slotShift) * coverStep
            cellOffsets[y] = startX - (pageX + tmpRect.exactCenterX())
            cellOffsetsY[y] = startY - (pageY + tmpRect.exactCenterY())
        }
        if (!drawsPills) return
        sideStartTop = height - coverBarHeight + pillInsets.top
        sideStartBottom = height - pillInsets.bottom
        hotseat.getSideDockBackgroundBounds(tmpRect)
        sideEndRect.set(tmpRect)
    }

    private fun drawSidePill(canvas: Canvas) {
        // Narrow first, tall last, like the icons going around the corner
        val p = progress
        val alongBottom = 1f - (1f - p) * (1f - p)
        val upTheEdge = p * p
        pillRect.set(
            lerp(fixedStartLeft, sideEndRect.left, alongBottom),
            lerp(sideStartTop, sideEndRect.top, upTheEdge),
            lerp(fixedStartRight, sideEndRect.right, alongBottom),
            lerp(sideStartBottom, sideEndRect.bottom, upTheEdge),
        )
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillPaint)
    }

    private fun applyTransforms() {
        hasTransforms = true
        val side = foldDock?.isSide == true
        val remaining = 1f - progress
        // The side dock's curve: x = (1 - t)², y = 1 - t² (see computeSideGeometry)
        val factorX = if (side) remaining * remaining else remaining
        val factorY = if (side) 1f - progress * progress else 0f
        val pagedView = hotseat.pagedView
        for (i in 0 until pagedView.childCount) {
            val icons = (pagedView.getChildAt(i) as? CellLayout)?.shortcutsAndWidgets ?: continue
            for (j in 0 until icons.childCount) {
                val icon = icons.getChildAt(j)
                val lp = icon.layoutParams as? CellLayoutLayoutParams ?: continue
                val cell = if (side) lp.cellY else lp.cellX
                if (cell in 0 until cellCount) {
                    setOffset(icon, cellOffsets[cell] * factorX, cellOffsetsY[cell] * factorY)
                } else {
                    setOffset(icon, 0f, 0f)
                }
            }
        }
        hotseat.getQsbAlpha(Hotseat.ALPHA_CHANNEL_FOLD_DOCK).value =
            ((progress - QSB_FADE_START) / (1f - QSB_FADE_START)).coerceIn(0f, 1f)
        if (drawsPills) hotseat.background?.alpha = 0
    }

    private fun clearTransforms() {
        if (!hasTransforms) return
        hasTransforms = false
        val pagedView = hotseat.pagedView
        for (i in 0 until pagedView.childCount) {
            val icons = (pagedView.getChildAt(i) as? CellLayout)?.shortcutsAndWidgets ?: continue
            for (j in 0 until icons.childCount) {
                setOffset(icons.getChildAt(j), 0f, 0f)
            }
        }
        hotseat.getQsbAlpha(Hotseat.ALPHA_CHANNEL_FOLD_DOCK).value = 1f
        hotseat.background?.alpha = 255
    }

    private fun setOffset(icon: View, translationX: Float, translationY: Float) {
        val delegate = (icon as? Reorderable)?.translateDelegate ?: return
        delegate.getTranslationX(INDEX_FOLD_DOCK_ANIM).value = translationX
        delegate.getTranslationY(INDEX_FOLD_DOCK_ANIM).value = translationY
    }

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

    companion object {
        /** The dock search bar fades in over the second half, once the pills are close. */
        private const val QSB_FADE_START = 0.5f
    }
}
