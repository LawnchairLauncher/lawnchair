package app.lawnchair.ui.preferences.components.fold

import android.animation.ValueAnimator
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldPageOrder
import app.lawnchair.fold.FoldTransitionStyle

/** One loop of a preview: folded (0–0.2), unfolding (0.2–0.4), open (0.4–0.8), folded again. */
private const val LOOP_MILLIS = 4000

/** The loop time shown, still, with animations off: open. */
private const val STILL_TIME = 0.6f

/** Window's largest blurs in the mock, in em: the moving half, the fixed half. */
private const val MOVING_BLUR_EM = 0.6f
private const val FIXED_BLUR_EM = 0.4f

/** Whether [option] has a [FoldPreview]. */
fun hasFoldPreview(option: FoldOption): Boolean = option is FoldHomeLayout || option is FoldPageOrder ||
    option is FoldDrawerLayout || option is FoldTransitionStyle || option is FoldDock

/**
 * The loop time of a [FoldPreview], 0..1 over [LOOP_MILLIS]; a new [restartKey] starts it over.
 * With animations off (animator duration scale 0, which "Remove animations" sets) it stays on the
 * open frame.
 */
@Composable
fun rememberFoldPreviewTime(restartKey: Any? = null): State<Float> {
    val animated = remember { ValueAnimator.areAnimatorsEnabled() }
    if (!animated) return remember { mutableFloatStateOf(STILL_TIME) }
    return key(restartKey) {
        rememberInfiniteTransition(label = "FoldPreview").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing)),
            label = "FoldPreviewTime",
        )
    }
}

/** 1 while a preview shows the folded phone (its moving half off), 0 while unfolded. */
fun foldedAt(time: Float): Float = 1f - seg(time, 0.19f, 0.2f) + seg(time, 0.79f, 0.8f)

/**
 * A looping picture of a foldable [option] (a home screen layout or its page order, an animation,
 * an app drawer layout or a dock): the cover screen, the unfold, the large screen open. The fixed
 * half is on the right, as on a book-style foldable in its natural rotation; it is a picture, not
 * the device's real side. The animations use the whole screen and run the way the phone opens:
 * from the fixed half across the hinge to the moving half's free edge.
 * With [animation], a home screen layout unfolds with it; without, the layout's own change shows.
 * Decorative: the row or card around it says what it shows.
 */
@Composable
fun FoldPreview(
    option: FoldOption,
    modifier: Modifier = Modifier,
    animation: FoldTransitionStyle? = null,
    time: State<Float> = rememberFoldPreviewTime(),
) {
    val scheme = MaterialTheme.colorScheme
    val painter = remember(scheme) { FoldPreviewPainter(scheme) }
    // Window blurs each half's content, which is then a layer of its own (API 31+, a fade below)
    val style = option as? FoldTransitionStyle ?: animation.takeIf { option is FoldHomeLayout }
    val layered = style == FoldTransitionStyle.WINDOW && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.clip(FrameShape).clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize()) {
            with(painter) { drawPreview(option, animation, time.value, layered) }
        }
        if (layered) {
            val fixedBlur = remember { BlurLevels(FIXED_BLUR_EM) }
            val movingBlur = remember { BlurLevels(MOVING_BLUR_EM) }
            Canvas(Modifier.matchParentSize().halfLayer(FixedHalfShape, fixedBlur, time, ::windowFixedBlur)) {
                with(painter) { drawHalf(option, time.value, moving = false) }
            }
            Canvas(Modifier.matchParentSize().halfLayer(MovingHalfShape, movingBlur, time, ::windowBlur)) {
                with(painter) { drawHalf(option, time.value, moving = true) }
            }
        }
    }
}

/** Window's layer of one half: clipped to [shape], blurred by [amount] at [time], zoomed with the whole screen. */
private fun Modifier.halfLayer(shape: Shape, blur: BlurLevels, time: State<Float>, amount: (Float) -> Float) = graphicsLayer {
    clip = true
    this.shape = shape
    renderEffect = blur.at(size.width, amount(time.value))
    scaleX = windowZoom(time.value)
    scaleY = scaleX
    // The fixed half's outer edge, as in the painter
    transformOrigin = TransformOrigin(1f - Pad.toPx() / size.width, 0.5f)
}

/** The device frame, which the whole screen's zoom stays inside. */
private object FrameShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = halfRadius(size.width, density) + with(density) { Pad.toPx() }
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}

/** The moving (left) or the fixed (right) half, where Window's blur stays. */
private class HalfShape(private val moving: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pad = with(density) { Pad.toPx() }
        val width = halfWidth(size.width, pad, with(density) { Gap.toPx() })
        val left = if (moving) pad else size.width - pad - width
        return Outline.Rounded(RoundRect(left, pad, left + width, size.height - pad, CornerRadius(halfRadius(size.width, density))))
    }
}

private val MovingHalfShape = HalfShape(moving = true)
private val FixedHalfShape = HalfShape(moving = false)

/** Window's blurs for one preview width, up to [maxEm], built when the width changes. */
private class BlurLevels(private val maxEm: Float) {
    private var width = 0f
    private val levels = arrayOfNulls<RenderEffect>(8)

    fun at(width: Float, amount: Float): RenderEffect? {
        if (amount <= 0f || width <= 0f) return null
        if (width != this.width) {
            this.width = width
            for (i in levels.indices) {
                val radius = maxEm * EM * width * (i + 1) / levels.size
                levels[i] = BlurEffect(radius, radius, TileMode.Decal)
            }
        }
        return levels[(amount * levels.size).toInt().coerceIn(0, levels.size - 1)]
    }
}
