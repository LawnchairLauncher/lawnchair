package app.lawnchair.hotseat

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.ViewTreeObserver
import app.lawnchair.fold.FoldDockLayout
import app.lawnchair.fold.FoldProgressListener
import com.android.app.animation.Interpolators
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
 * It only moves existing views (one translation channel per icon) and draws the pills from
 * [Hotseat.dispatchDraw], with no allocation per frame. The work is done in a pre-draw listener
 * so that icons bound after the DeviceProfile change, and the layout of the new pages, are picked
 * up on the frame they first show.
 *
 * Until the fold transition engine (Phase 3) drives it, a DeviceProfile change between the cover
 * and the large screen plays a short animation: 0 to 1 on unfold, 1 to 0 on fold (on the cover
 * there is no large dock left to split, so folding shows nothing; the engine reports the
 * progress before the screens switch).
 */
class HotseatFoldController(
    private val hotseat: Hotseat,
    private val activity: ActivityContext,
) : FoldProgressListener,
    DeviceProfile.OnDeviceProfileChangeListener,
    ViewTreeObserver.OnPreDrawListener {

    /**
     * Whether a DeviceProfile change between the cover and the large screen plays the merge with
     * this controller's own animator. The fold transition engine sets it to false and calls the
     * [FoldProgressListener] methods itself.
     */
    var animateOnDeviceProfileChange = true

    private var isUnfolded = false
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

    private var animatorOpening = true
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = ANIMATION_DURATION
        interpolator = Interpolators.EMPHASIZED
        addUpdateListener {
            val fraction = it.animatedFraction
            onFoldProgress(if (animatorOpening) fraction else 1f - fraction, animatorOpening)
        }
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    onFoldStart(animatorOpening)
                }

                override fun onAnimationEnd(animation: Animator) {
                    onFoldEnd(animatorOpening)
                }
            },
        )
    }

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

    fun onAttached(dp: DeviceProfile) {
        isUnfolded = dp.deviceProperties.isTwoPanels
    }

    fun onDetached() {
        animator.cancel()
        onFoldEnd(animatorOpening)
    }

    override fun onDeviceProfileChanged(dp: DeviceProfile) {
        // Drop the old layout's offsets before the hotseat applies the new profile (setInsets
        // decides the search bar's visibility after this)
        clearTransforms()
        preparedPage = null
        foldDock = null
        val unfolded = dp.deviceProperties.isTwoPanels
        if (unfolded == isUnfolded) return
        isUnfolded = unfolded
        if (!animateOnDeviceProfileChange || !hotseat.isAttachedToWindow) return
        animator.cancel()
        animatorOpening = unfolded
        animator.start()
    }

    override fun onFoldStart(opening: Boolean) {
        progress = if (opening) 0f else 1f
        if (!isActive) {
            isActive = true
            hotseat.viewTreeObserver.addOnPreDrawListener(this)
        }
        hotseat.invalidate()
    }

    override fun onFoldProgress(progress: Float, opening: Boolean) {
        if (!isActive) onFoldStart(opening)
        this.progress = progress.coerceIn(0f, 1f)
        hotseat.invalidate()
    }

    override fun onFoldEnd(opening: Boolean) {
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
            if (animator.isRunning) animator.end() else onFoldEnd(true)
            return true
        }
        if (prepare()) applyTransforms() else clearTransforms()
        return true
    }

    /** Draws the background pills under the icons while the dock merges or splits. */
    fun drawBackground(canvas: Canvas) {
        if (!hasTransforms || !drawsPills) return
        val layout = foldDock ?: return
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
        val layout = hotseat.foldDockLayout?.takeIf { page.countX == it.dockIcons }
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
        }
        cellCount = count
        val container = pagedView.parent as View
        val pageX = container.left + pagedView.left + page.left - pagedView.scrollX
        for (x in 0 until count) {
            page.cellToRect(x, 0, 1, 1, tmpRect)
            finalCenters[x] = pageX + tmpRect.exactCenterX()
        }

        // Where the cover dock's icons sit, with the cover screen over the fixed half
        val width = hotseat.width.toFloat()
        val coverIcons = layout.coverIcons
        val cover = findCoverProfile(activity.deviceProfile)
        val coverWidth: Float
        val coverFirstCenter: Float
        val coverStep: Float
        if (cover != null) {
            val padding = cover.getHotseatLayoutPadding(hotseat.context)
            val coverWidthPx = cover.deviceProperties.widthPx
            val contentWidth = coverWidthPx - padding.left - padding.right
            val border = cover.hotseatBorderSpace
            val cellWidth = DeviceProfile.calculateCellWidth(contentWidth, border, coverIcons)
            val unused = contentWidth - coverIcons * cellWidth - (coverIcons - 1) * border
            coverWidth = coverWidthPx.toFloat()
            coverStep = (cellWidth + border).toFloat()
            coverFirstCenter = padding.left + ceil(unused / 2f) + cellWidth / 2f
        } else {
            coverWidth = width / 2f
            coverStep = coverWidth / coverIcons
            coverFirstCenter = coverStep / 2f
        }
        val movingLeft = layout.isMovingHalfLeft
        val fixedOrigin = if (movingLeft) width - coverWidth else 0f
        // The extra icons start outside the screen, with the moving half
        extraShift = if (movingLeft) -width / 2f else width / 2f
        for (x in 0 until count) {
            val slot = layout.coverSlotOf(x)
            cellOffsets[x] = if (slot >= 0) {
                fixedOrigin + coverFirstCenter + slot * coverStep - finalCenters[x]
            } else {
                extraShift
            }
        }

        drawsPills = hasBackgroundStyle && hotseat.background != null
        if (!drawsPills) return
        pillTop = pillInsets.top.toFloat()
        pillBottom = (hotseat.height - pillInsets.bottom).toFloat()
        val backgroundLeft = pillInsets.left.toFloat()
        val backgroundRight = width - pillInsets.right
        if (movingLeft) {
            fixedStartLeft = width - coverWidth + pillInsets.left
            fixedStartRight = width - pillInsets.right
        } else {
            fixedStartLeft = pillInsets.left.toFloat()
            fixedStartRight = coverWidth - pillInsets.right
        }
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

    private fun applyTransforms() {
        hasTransforms = true
        val remaining = 1f - progress
        val pagedView = hotseat.pagedView
        for (i in 0 until pagedView.childCount) {
            val icons = (pagedView.getChildAt(i) as? CellLayout)?.shortcutsAndWidgets ?: continue
            for (j in 0 until icons.childCount) {
                val icon = icons.getChildAt(j)
                val cellX = (icon.layoutParams as? CellLayoutLayoutParams)?.cellX ?: continue
                setOffset(icon, if (cellX in 0 until cellCount) cellOffsets[cellX] * remaining else 0f)
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
                setOffset(icons.getChildAt(j), 0f)
            }
        }
        hotseat.getQsbAlpha(Hotseat.ALPHA_CHANNEL_FOLD_DOCK).value = 1f
        hotseat.background?.alpha = 255
    }

    private fun setOffset(icon: View, translationX: Float) {
        (icon as? Reorderable)?.translateDelegate?.getTranslationX(INDEX_FOLD_DOCK_ANIM)?.value = translationX
    }

    /** Returns the cover screen's profile: the portrait one-panel profile of this device. */
    private fun findCoverProfile(dp: DeviceProfile): DeviceProfile? = dp.inv.supportedProfiles.firstOrNull {
        !it.deviceProperties.isTwoPanels && !it.deviceProperties.isLandscape && !it.isVerticalBarLayout
    }

    private fun lerp(start: Float, end: Float, fraction: Float) = start + (end - start) * fraction

    companion object {
        private const val ANIMATION_DURATION = 450L

        /** The dock search bar fades in over the second half, once the pills are close. */
        private const val QSB_FADE_START = 0.5f
    }
}
