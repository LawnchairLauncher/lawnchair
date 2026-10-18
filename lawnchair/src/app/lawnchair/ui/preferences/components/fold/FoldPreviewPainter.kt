package app.lawnchair.ui.preferences.components.fold

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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldTransitionStyle
import kotlin.math.max

/** The mock's type size: 1 em is this share of the preview's width. */
internal const val EM = 1f / 18.3f

/** The mock's frame around the halves, and the hinge between them. */
internal val Pad = 3.dp
internal val Gap = 2.dp

private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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

    private var t = 0f
    private var em = 0f
    private var pad = 0f
    private var hw = 0f
    private var hh = 0f
    private var fixedLeft = 0f
    private var radius = 0f

    /** Opacity of what is drawn now. */
    private var alpha = 1f

    /** Light wave: icons on the moving half pop in as the band passes them. */
    private var pop = false

    /** The preview at loop time [time]; without [withMoving], Window's moving half is left to [drawMoving]. */
    fun DrawScope.drawPreview(option: FoldOption, animation: FoldTransitionStyle?, time: Float, withMoving: Boolean) {
        measure(time)
        drawRoundRect(frame, cornerRadius = CornerRadius(radius + pad))
        half(pad, screen)
        half(fixedLeft, screen)
        when (option) {
            is FoldTransitionStyle -> split(FoldHomeLayout.PAGES, option, docks = true, withMoving)

            is FoldHomeLayout -> when {
                animation != null -> split(option, animation, docks = option == FoldHomeLayout.PAGES, withMoving)
                option == FoldHomeLayout.PAGES -> split(option, FoldTransitionStyle.SIMPLE, docks = false, withMoving)
                else -> grow(option)
            }

            is FoldDrawerLayout -> clipRect(pad, pad, size.width - pad, size.height - pad) { drawer(option) }

            is FoldDock -> dock(option)
        }
    }

    /** Window's moving half alone, for its blurred layer. */
    fun DrawScope.drawMoving(option: FoldOption, time: Float) {
        measure(time)
        val home = option as? FoldHomeLayout ?: FoldHomeLayout.PAGES
        moving(home, FoldTransitionStyle.WINDOW, docks = home == FoldHomeLayout.PAGES)
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

    /** The halves apart: the fixed one shows the cover page until the unfold, the moving one enters with [style]. */
    private fun DrawScope.split(home: FoldHomeLayout, style: FoldTransitionStyle, docks: Boolean, withMoving: Boolean) {
        if (home == FoldHomeLayout.PAGES) {
            page(fixedLeft, 0)
            if (docks) dock4()
        } else {
            clipRect(fixedLeft, pad, fixedLeft + hw, pad + hh) {
                val folded = foldedAt(t)
                alpha = folded
                page(fixedLeft, 0)
                alpha = 1f - folded
                openHome(home)
                alpha = 1f
            }
        }
        if (withMoving) moving(home, style, docks)
    }

    private fun DrawScope.moving(home: FoldHomeLayout, style: FoldTransitionStyle, docks: Boolean) {
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

                // A band of light sweeps out from the hinge; icons pop in as it passes
                FoldTransitionStyle.WAVE -> {
                    pop = true
                    openMoving(home, docks)
                    pop = false
                    gradient(bandBrush, pad + hw * (0.96f - 1.41f * seg(t, 0.22f, 0.46f)), 0.45f * hw, seg(t, 0.2f, 0.22f) * (1f - seg(t, 0.46f, 0.47f)))
                }

                FoldTransitionStyle.SIMPLE, FoldTransitionStyle.BOOK -> {
                    alpha = seg(t, 0.2f, 0.3f)
                    openMoving(home, docks)
                    alpha = 1f
                }

                FoldTransitionStyle.NONE -> if (t >= 0.2f) openMoving(home, docks)
            }
            off()
        }
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
            // The nearer the hinge, the sooner the band reaches it
            val start = 0.17f + 0.24f * (pad + hw - center) / hw
            val grow = seg(t, start, start + 0.06f)
            a *= grow
            scale = if (grow < 1f) 0.6f + 0.58f * grow else 1.18f - 0.18f * seg(t, start + 0.06f, start + 0.12f)
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

/** Window's blur: 1 as the unfold starts, 0 once open. */
internal fun windowBlur(t: Float) = if (t < 0.2f) 0f else 1f - ease(t, 0.2f, 0.42f)
