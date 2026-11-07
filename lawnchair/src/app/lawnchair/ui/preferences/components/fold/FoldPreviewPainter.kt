package app.lawnchair.ui.preferences.components.fold

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldPageOrder
import app.lawnchair.fold.FoldTransitionStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** The mock's type size: 1 em is this share of the preview's width. */
internal const val EM = 1f / 18.3f

/** The mock's frame around the halves, and the hinge between them. */
internal val Pad = 3.dp
internal val Gap = 2.dp

private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Open book's curve in the mock. */
private val BookCurve = CubicBezierEasing(0.3f, 0f, 0f, 1f)

/**
 * Draws the schematic device of the mock, sized in its em ([EM]). Holds the frame being drawn, so
 * nothing is allocated per frame.
 */
internal class FoldPreviewPainter(scheme: ColorScheme) {
    // A dark screen with light icons, in light and dark mode
    private val dark = scheme.surface.luminance() < 0.5f
    private val frame = scheme.inverseSurface
    private val screen = if (dark) scheme.primaryContainer else lerp(scheme.primary, Color.Black, 0.45f)
    private val offScreen = lerp(screen, Color.Black, 0.75f)
    private val icons = if (dark) {
        listOf(scheme.primary, scheme.onSecondaryContainer, scheme.tertiary, scheme.onPrimaryContainer, scheme.secondary, lerp(scheme.tertiary, Color.White, 0.4f))
    } else {
        listOf(scheme.inversePrimary, scheme.secondaryContainer, scheme.tertiaryContainer, scheme.primaryContainer, lerp(scheme.secondary, Color.White, 0.6f), lerp(scheme.tertiary, Color.White, 0.6f))
    }
    private val widget = lerp(screen, Color.White, 0.2f)
    private val homeBar = if (dark) scheme.onSurface else scheme.surfaceContainerHighest
    private val drawerColor = scheme.surfaceContainerHigh
    private val drawerBar = scheme.surfaceContainerLowest
    private val drawerIcons = listOf(scheme.primary, scheme.tertiary, scheme.secondary)

    // 0..1 wide gradients, scaled where they are drawn
    private val edgeBrush = Brush.horizontalGradient(listOf(screen, screen.copy(alpha = 0f)), 0f, 1f)
    private val light = lerp(if (dark) scheme.primary else scheme.inversePrimary, Color.White, 0.5f)
    private val bandBrush = Brush.horizontalGradient(listOf(Color.Transparent, light.copy(alpha = 0.6f), Color.Transparent), 0f, 1f)

    // Open book: each page dark at the hinge
    private val movingShadeBrush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), 0f, 1f)
    private val fixedShadeBrush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), 0f, 1f)
    private val turn = Matrix()
    private val flatCorners = FloatArray(8)
    private val turnedCorners = FloatArray(8)

    // Page order: the pages' numbers, in the language's digits
    private val pageNumbers = Array(3) { String.format(Locale.getDefault(), "%d", it + 1) }
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = light.toArgb()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var t = 0f
    private var em = 0f
    private var pad = 0f
    private var hw = 0f
    private var hh = 0f
    private var fixedLeft = 0f
    private var radius = 0f

    /** Opacity of what is drawn now. */
    private var alpha = 1f

    /** Light wave: icons pop as the band passes them, in on the moving half, a pulse on the fixed one. */
    private var pop = false

    /** The preview at loop time [time]; with [layered], the halves' content is left to [drawHalf] (Window's blurred layers). */
    fun DrawScope.drawPreview(option: FoldOption, animation: FoldTransitionStyle?, time: Float, layered: Boolean) {
        measure(time)
        drawRoundRect(frame, cornerRadius = CornerRadius(radius + pad))
        when (option) {
            is FoldTransitionStyle -> split(FoldHomeLayout.PAGES, option, docks = true, layered)

            is FoldHomeLayout -> if (animation != null || option == FoldHomeLayout.PAGES) {
                split(option, animation, docks = animation != null && option == FoldHomeLayout.PAGES, layered)
            } else {
                halves()
                grow(option)
            }

            is FoldDrawerLayout -> {
                halves()
                clipRect(pad, pad, size.width - pad, size.height - pad) { drawer(option) }
            }

            is FoldDock -> {
                halves()
                dock(option)
            }

            is FoldPageOrder -> {
                halves()
                pageOrder(option)
            }
        }
    }

    /** One half's content alone, for Window's blurred layer of that half (the layer zooms it). */
    fun DrawScope.drawHalf(option: FoldOption, time: Float, moving: Boolean) {
        measure(time)
        val home = option as? FoldHomeLayout ?: FoldHomeLayout.PAGES
        val docks = home == FoldHomeLayout.PAGES
        if (moving) moving(home, FoldTransitionStyle.WINDOW, docks) else fixed(home, FoldTransitionStyle.WINDOW, docks)
    }

    private fun DrawScope.measure(time: Float) {
        t = time
        em = size.width * EM
        pad = Pad.toPx()
        hw = halfWidth(size.width, pad, Gap.toPx())
        hh = size.height - 2 * pad
        fixedLeft = size.width - pad - hw
        radius = halfRadius(size.width, this)
    }

    private fun DrawScope.halves() {
        half(pad, screen)
        half(fixedLeft, screen)
    }

    /**
     * The halves apart, the whole screen taking part: the fixed one shows the cover page until the
     * unfold, the moving one opens with [style] (null: a layout's own fade), in the opening direction.
     */
    private fun DrawScope.split(home: FoldHomeLayout, style: FoldTransitionStyle?, docks: Boolean, layered: Boolean) {
        // Window: the whole screen settles from a slight zoom about the fixed half's outer edge
        scale(if (style == FoldTransitionStyle.WINDOW) windowZoom(t) else 1f, Offset(size.width - pad, size.height / 2f)) {
            // Open book: the pages turn over the dark screen
            if (style == FoldTransitionStyle.BOOK) {
                half(pad, offScreen)
                half(fixedLeft, offScreen)
            }
            turned(moving = false, degrees = if (style == FoldTransitionStyle.BOOK) 30f * bookTurn(t) else 0f) {
                half(fixedLeft, screen)
                if (!layered) fixed(home, style, docks)
            }
            turned(moving = true, degrees = if (style == FoldTransitionStyle.BOOK) 78f * bookTurn(t) else 0f) {
                half(pad, screen)
                if (!layered) moving(home, style, docks)
            }
        }
    }

    /** The fixed half: the page you were on, taking part in [style]. */
    private fun DrawScope.fixed(home: FoldHomeLayout, style: FoldTransitionStyle?, docks: Boolean) {
        clipRect(fixedLeft, pad, fixedLeft + hw, pad + hh) {
            val base = when {
                // A short dip as the new half fades in
                style == FoldTransitionStyle.SIMPLE -> 1f - 0.45f * seg(t, 0.19f, 0.2f) * (1f - seg(t, 0.2f, 0.3f))

                // Window's blur, a fade where there is none
                style == FoldTransitionStyle.WINDOW && Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> 1f - 0.5f * windowFixedBlur(t)

                else -> 1f
            }
            pop = style == FoldTransitionStyle.WAVE
            if (home == FoldHomeLayout.PAGES) {
                alpha = base
                page(fixedLeft, 0)
                if (docks) dock4()
            } else {
                val folded = foldedAt(t)
                alpha = base * folded
                page(fixedLeft, 0)
                alpha = base * (1f - folded)
                openHome(home)
            }
            alpha = 1f
            pop = false
            when (style) {
                // The band enters at the outer edge and crosses to the hinge
                FoldTransitionStyle.WAVE -> gradient(bandBrush, fixedLeft + hw * (0.96f - 1.41f * seg(t, 0.21f, 0.32f)), 0.45f * hw, seg(t, 0.2f, 0.21f) * (1f - seg(t, 0.32f, 0.33f)))

                FoldTransitionStyle.BOOK -> gradient(fixedShadeBrush, fixedLeft, hw, 0.8f * seg(t, 0.19f, 0.2f) * bookTurn(t))

                else -> Unit
            }
        }
    }

    private fun DrawScope.moving(home: FoldHomeLayout, style: FoldTransitionStyle?, docks: Boolean) {
        val hingeX = pad + hw
        clipRect(pad, pad, hingeX, pad + hh) {
            when (style) {
                // Revealed from the hinge to the free edge behind a soft edge, sharpening as it opens
                FoldTransitionStyle.WINDOW -> {
                    val edge = hingeX - hw * ease(t, 0.2f, 0.4f)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) alpha = 1f - windowBlur(t)
                    clipRect(left = edge) { openMoving(home, docks) }
                    alpha = 1f
                    gradient(edgeBrush, edge, 0.32f * hw, seg(t, 0.19f, 0.21f) * (1f - seg(t, 0.4f, 0.46f)))
                }

                // The band comes over the hinge and leaves at the free edge; icons pop in as it passes
                FoldTransitionStyle.WAVE -> {
                    pop = true
                    openMoving(home, docks)
                    pop = false
                    gradient(bandBrush, pad + hw * (0.96f - 1.41f * seg(t, 0.33f, 0.46f)), 0.45f * hw, seg(t, 0.32f, 0.33f) * (1f - seg(t, 0.46f, 0.47f)))
                }

                // The page turns flat, its shade at the hinge fading
                FoldTransitionStyle.BOOK -> {
                    openMoving(home, docks)
                    gradient(movingShadeBrush, pad, hw, 0.9f * bookTurn(t))
                }

                // The new half fades in
                FoldTransitionStyle.SIMPLE, null -> {
                    alpha = seg(t, 0.2f, 0.3f)
                    openMoving(home, docks)
                    alpha = 1f
                }
            }
            off()
        }
    }

    /**
     * Draws [block] with the moving or the fixed half turned [degrees] about its hinge edge, its
     * outer edge going away, in the mock's perspective (26 em, from the middle of the hinge edge).
     */
    private inline fun DrawScope.turned(moving: Boolean, degrees: Float, block: DrawScope.() -> Unit) {
        if (degrees <= 0f) return block()
        val hingeX = if (moving) pad + hw else fixedLeft
        val outerX = if (moving) pad else fixedLeft + hw
        val radians = degrees * (PI.toFloat() / 180f)
        val distance = 26f * em
        val depth = distance / (distance + hw * sin(radians))
        val middle = pad + hh / 2f
        setCorners(flatCorners, hingeX, outerX, pad, pad + hh)
        setCorners(turnedCorners, hingeX, hingeX + (outerX - hingeX) * cos(radians) * depth, middle - hh / 2f * depth, middle + hh / 2f * depth)
        if (!turn.setPolyToPoly(flatCorners, 0, turnedCorners, 0, 4)) return block()
        val canvas = drawContext.canvas
        canvas.save()
        canvas.nativeCanvas.concat(turn)
        block()
        canvas.restore()
    }

    /** The hinge edge at [hingeX] from [pad] to the bottom, then the outer edge at [outerX] from [top] to [bottom]. */
    private fun setCorners(points: FloatArray, hingeX: Float, outerX: Float, top: Float, bottom: Float) {
        points[0] = hingeX
        points[1] = pad
        points[2] = hingeX
        points[3] = pad + hh
        points[4] = outerX
        points[5] = top
        points[6] = outerX
        points[7] = bottom
    }

    private fun DrawScope.openMoving(home: FoldHomeLayout, docks: Boolean) {
        if (home != FoldHomeLayout.PAGES) return openHome(home)
        page(pad, 3)
        if (docks) dock2(0f)
    }

    /** The large layout grows out of the fixed half over both halves (Same as cover, Expand, Independent). */
    private fun DrawScope.grow(home: FoldHomeLayout) {
        alpha = 1f - seg(t, 0.19f, 0.24f) + seg(t, 0.79f, 0.8f)
        page(fixedLeft, 0)
        alpha = 1f
        off()
        val shown = shown(t, 0.2f, 0.34f)
        if (shown > 0f) {
            scale(0.55f + 0.45f * shown, Offset(size.width * 0.75f, size.height / 2f)) {
                drawRoundRect(screen, Offset(pad, pad), Size(size.width - 2 * pad, hh), CornerRadius(radius), alpha = shown)
                alpha = shown
                openHome(home)
                alpha = 1f
            }
        }
        if (home != FoldHomeLayout.MIRROR) hinge()
    }

    /** The open large screen of [home], over both halves. */
    private fun DrawScope.openHome(home: FoldHomeLayout) {
        val width = size.width - 2 * pad
        when (home) {
            FoldHomeLayout.PAGES -> {
                page(pad, 3)
                page(fixedLeft, 0)
            }

            // The cover page, larger
            FoldHomeLayout.MIRROR -> grid(pad + 1.4f * em, pad + 2.6f * em, width - 2.8f * em, 3, 9, 1.6f * em, 1.6f * em, 0)

            // One wide page
            FoldHomeLayout.EXPAND -> grid(pad + 0.9f * em, pad + 2.4f * em, width - 1.8f * em, 6, 9, 0.75f * em, 0.9f * em, 0)

            // An arrangement of its own, with a widget
            FoldHomeLayout.INDEPENDENT -> {
                val left = pad + 0.9f * em
                val top = pad + 2.4f * em
                val gap = 0.75f * em
                val cell = (width - 1.8f * em - 5 * gap) / 6
                val widgetWidth = 3 * cell + 2 * gap
                val widgetHeight = widgetWidth * 1.3f / 3f
                drawRoundRect(widget, Offset(left, top), Size(widgetWidth, widgetHeight), CornerRadius(0.6f * em), alpha = alpha)
                for (i in 3 until 9) {
                    icon(left + i % 6 * (cell + gap), top + i / 6 * (widgetHeight + 0.9f * em), cell, icons[(i + 2) % icons.size], round = true)
                }
            }
        }
    }

    private fun DrawScope.drawer(layout: FoldDrawerLayout) {
        val shown = shown(t, 0.22f, 0.38f)
        val width = size.width - 2 * pad
        when (layout) {
            // More columns: the new half's fade in beside the cover's
            FoldDrawerLayout.EXPAND -> {
                sheet()
                val gap = 0.75f * em
                val cell = (width - 1.8f * em - 5 * gap) / 6
                val columns = 3 * cell + 2 * gap
                grid(pad + 0.9f * em + columns + gap, pad + 2.4f * em, columns, 3, 12, gap, 0.9f * em, 0, drawerIcons)
                alpha = shown(t, 0.22f, 0.34f)
                grid(pad + 0.9f * em, pad + 2.4f * em, columns, 3, 12, gap, 0.9f * em, 1, drawerIcons)
                alpha = 1f
            }

            // The right page is the cover's drawer; the left one continues it, rising in
            FoldDrawerLayout.PAGES -> {
                half(pad, drawerColor)
                half(fixedLeft, drawerColor)
                bar(fixedLeft + 0.6f * em, hw - 1.2f * em)
                grid(fixedLeft + 0.9f * em, pad + 2.6f * em, hw - 1.8f * em, 3, 12, 0.9f * em, em, 0, drawerIcons)
                alpha = shown
                translate(top = (1f - shown) * 0.55f * (hw - 3.6f * em) / 3f) {
                    grid(pad + 0.9f * em, pad + 1.2f * em, hw - 1.8f * em, 3, 12, 0.9f * em, em, 1, drawerIcons)
                }
                alpha = 1f
            }

            // The cover's columns, larger
            FoldDrawerLayout.BIG_ICONS -> {
                sheet()
                scale(0.55f + 0.45f * shown, Offset(fixedLeft + hw / 2f, pad + 2.8f * em)) {
                    grid(pad + 1.6f * em, pad + 2.8f * em, width - 3.2f * em, 3, 12, 1.8f * em, 1.6f * em, 0, drawerIcons)
                }
            }
        }
        off()
        if (layout != FoldDrawerLayout.PAGES) hinge()
    }

    private fun DrawScope.dock(layout: FoldDock) {
        val shown = shown(t, 0.22f, 0.38f)
        val s = 1.5f * em
        when (layout) {
            // The cover's dock stays; the extra icons slide in from the hinge
            FoldDock.MERGE -> {
                dock4()
                clipRect(pad, pad, pad + hw, pad + hh) {
                    alpha = shown
                    dock2((1f - shown) * 1.6f * s)
                    alpha = 1f
                }
                off()
            }

            // The same icons, spread across both halves
            FoldDock.SAME -> {
                off()
                val free = size.width - 2.8f * em - 4 * s
                for (i in 0 until 4) {
                    val cover = fixedLeft + 0.8f * em + i * (hw - 1.6f * em - s) / 3f
                    val wide = 1.4f * em + free / 8f + i * (s + free / 4f)
                    icon(cover + (wide - cover) * shown, dockY(), s, icons[(i + 1) % icons.size])
                }
            }

            // A column on the fixed half's outer edge, above a search bar across both halves
            FoldDock.SIDE -> {
                drawRoundRect(homeBar, Offset(pad + 0.8f * em, dockY()), Size(size.width - 2 * pad - 3.8f * em, s), CornerRadius(s / 2f), alpha = 0.85f)
                hinge()
                val top = pad + 2.2f * em
                val count = ((dockY() - top + 0.05f * em) / (s + 0.55f * em)).toInt().coerceIn(1, 6)
                alpha = shown
                for (i in 0 until count) {
                    icon(fixedLeft + hw - 0.7f * em - s, top + i * (s + 0.55f * em) + (1f - shown) * 0.4f * s, s, icons[i % icons.size])
                }
                alpha = 1f
                off()
            }
        }
    }

    /**
     * The pages as the large screen opens, numbered: cover page 2 stays on the fixed half and the
     * moving half opens beside it on page 3, or with [FoldPageOrder.READING] in a left-to-right
     * language on page 1, which comes before it. Right to left, reading order is the opening one.
     */
    private fun DrawScope.pageOrder(order: FoldPageOrder) {
        numberedPage(fixedLeft, 2)
        val before = order == FoldPageOrder.READING && layoutDirection == LayoutDirection.Ltr
        val shown = shown(t, 0.2f, 0.34f)
        clipRect(pad, pad, pad + hw, pad + hh) {
            // The new page comes out from the hinge as the half opens
            alpha = shown
            translate(left = (1f - shown) * 1.6f * em) { numberedPage(pad, if (before) 1 else 3) }
            alpha = 1f
        }
        off()
    }

    /** A page of the mock numbered [number] (1 to 3), on the half at [left]: the number above two rows of icons. */
    private fun DrawScope.numberedPage(left: Float, number: Int) {
        if (alpha > 0f) {
            numberPaint.textSize = 5f * em
            numberPaint.alpha = (alpha * 255).roundToInt()
            val baseline = pad + 0.36f * hh - (numberPaint.ascent() + numberPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(pageNumbers[number - 1], left + hw / 2f, baseline, numberPaint)
        }
        grid(left + 0.9f * em, pad + 0.62f * hh, hw - 1.8f * em, 3, 6, 0.9f * em, em, 2 * number)
    }

    /** The mock's cover page: 9 icons in 3 columns, on the half at [left]. */
    private fun DrawScope.page(left: Float, shift: Int) = grid(left + 0.9f * em, pad + 2.2f * em, hw - 1.8f * em, 3, 9, 0.9f * em, em, shift)

    /** The cover's dock on the fixed half. */
    private fun DrawScope.dock4() {
        val s = 1.5f * em
        for (i in 0 until 4) icon(fixedLeft + 0.8f * em + i * (hw - 1.6f * em - s) / 3f, dockY(), s, icons[(i + 1) % icons.size])
    }

    /** The large screen's extra dock icons, beside the hinge on the moving half, [shift] toward it. */
    private fun DrawScope.dock2(shift: Float) {
        val s = 1.5f * em
        for (i in 0 until 2) icon(pad + hw - 0.8f * em - s - i * (s + 0.55f * em) + shift, dockY(), s, icons[(i + 4) % icons.size])
    }

    private fun dockY() = pad + hh - 2.4f * em

    private fun DrawScope.grid(
        left: Float,
        top: Float,
        width: Float,
        columns: Int,
        count: Int,
        columnGap: Float,
        rowGap: Float,
        shift: Int,
        palette: List<Color> = icons,
    ) {
        val cell = (width - (columns - 1) * columnGap) / columns
        for (i in 0 until count) {
            icon(left + i % columns * (cell + columnGap), top + i / columns * (cell + rowGap), cell, palette[(i + shift) % palette.size])
        }
    }

    private fun DrawScope.icon(x: Float, y: Float, s: Float, color: Color, round: Boolean = false) {
        var a = alpha
        var scale = 1f
        val center = x + s / 2f
        if (pop && center < pad + hw) {
            // The moving half's icons appear as the band reaches them, from the hinge out
            val start = 0.32f + 0.4f / 3f * ((pad + hw - center) / hw - 0.2f)
            val grow = seg(t, start, start + 0.04f)
            a *= grow
            scale = if (grow < 1f) 0.6f + 0.58f * grow else 1.18f - 0.18f * seg(t, start + 0.04f, start + 0.08f)
        } else if (pop) {
            // The page you were on pulses as the band crosses it, from its outer edge to the hinge
            val start = 0.21f + 0.1f * ((fixedLeft + hw - center) / hw - 0.2f)
            scale = 1f + 0.2f * (seg(t, start, start + 0.03f) - seg(t, start + 0.03f, start + 0.07f))
        }
        if (a <= 0f) return
        val d = s * scale
        drawRoundRect(color, Offset(center - d / 2f, y + (s - d) / 2f), Size(d, d), CornerRadius(d * if (round) 0.5f else 0.32f), alpha = a)
    }

    /** The moving half off, while folded. */
    private fun DrawScope.off() {
        val a = foldedAt(t)
        if (a > 0f) half(pad, offScreen, a)
    }

    private fun DrawScope.half(left: Float, color: Color, a: Float = 1f) {
        drawRoundRect(color, Offset(left, pad), Size(hw, hh), CornerRadius(radius), alpha = a)
    }

    private fun DrawScope.hinge() = drawRect(frame, Offset(pad + hw, pad), Size(fixedLeft - pad - hw, hh))

    /** The drawer over both halves, with its search bar. */
    private fun DrawScope.sheet() {
        drawRoundRect(drawerColor, Offset(pad, pad), Size(size.width - 2 * pad, hh), CornerRadius(radius))
        bar(pad + 0.9f * em, size.width - 2 * pad - 1.8f * em)
    }

    private fun DrawScope.bar(left: Float, width: Float) {
        drawRoundRect(drawerBar, Offset(left, pad + 0.8f * em), Size(width, 1.1f * em), CornerRadius(0.55f * em))
    }

    /** [brush], a 0..1 horizontal gradient, stretched over [width] from [left] and the halves' height. */
    private fun DrawScope.gradient(brush: Brush, left: Float, width: Float, opacity: Float) {
        if (opacity <= 0f) return
        withTransform({
            translate(left, pad)
            scale(width, hh, Offset.Zero)
        }) {
            drawRect(brush, size = Size(1f, 1f), alpha = opacity)
        }
    }
}

internal fun halfWidth(width: Float, pad: Float, gap: Float) = (width - 2 * pad - gap) / 2f

internal fun halfRadius(width: Float, density: Density) = max(width * 0.027f, with(density) { 3.5.dp.toPx() })

/** 0 before [from], 1 after [to], linear between. */
internal fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** [seg] on the mock's curve, Material's standard easing. */
private fun ease(t: Float, from: Float, to: Float) = Standard.transform(seg(t, from, to))

/** How much of the open state shows: it eases in between [from] and [to] and goes when the phone folds again. */
private fun shown(t: Float, from: Float, to: Float) = ease(t, from, to) * (1f - seg(t, 0.79f, 0.8f))

/** Window's blur of the moving half: 1 as the unfold starts, 0 once open. */
internal fun windowBlur(t: Float) = if (t < 0.2f) 0f else 1f - ease(t, 0.2f, 0.42f)

/** Window's blur of the fixed half: from the unfold's start, gone before the moving half's. */
internal fun windowFixedBlur(t: Float) = seg(t, 0.19f, 0.2f) * (1f - ease(t, 0.2f, 0.38f))

/** Window's settle: the whole screen from a slight zoom back to 1 as it unfolds. */
internal fun windowZoom(t: Float) = 1f + 0.08f * seg(t, 0.19f, 0.2f) * (1f - ease(t, 0.2f, 0.42f))

/** Open book: how far the pages are still turned, 1 as the unfold starts (from just before it), 0 once open. */
private fun bookTurn(t: Float) = if (t < 0.19f) 0f else 1f - BookCurve.transform(seg(t, 0.2f, 0.46f))
