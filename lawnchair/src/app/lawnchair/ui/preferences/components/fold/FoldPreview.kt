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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.lawnchair.fold.FoldDock
import app.lawnchair.fold.FoldDrawerLayout
import app.lawnchair.fold.FoldHomeLayout
import app.lawnchair.fold.FoldOption
import app.lawnchair.fold.FoldTransitionStyle

/** One loop of a preview: folded (0–0.2), unfolding (0.2–0.4), open (0.4–0.8), folded again. */
private const val LOOP_MILLIS = 4000

/** The loop time shown, still, with animations off: open. */
private const val STILL_TIME = 0.6f

/** Whether [option] has a [FoldPreview]. */
fun hasFoldPreview(option: FoldOption): Boolean = option is FoldHomeLayout || option is FoldDrawerLayout ||
    option is FoldTransitionStyle || option is FoldDock

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
 * A looping picture of a foldable [option] (a home screen layout, an animation, an app drawer layout
 * or a dock): the cover screen, the unfold, the large screen open. The fixed half is on the right,
 * as on a book-style foldable in its natural rotation; it is a picture, not the device's real side.
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
    // Window blurs the moving half, which is then a layer of its own (API 31+, a fade below)
    val style = option as? FoldTransitionStyle ?: animation.takeIf { option is FoldHomeLayout }
    val blurred = style == FoldTransitionStyle.WINDOW && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.clearAndSetSemantics {}) {
        Canvas(Modifier.matchParentSize()) {
            with(painter) { drawPreview(option, animation, time.value, withMoving = !blurred) }
        }
        if (blurred) {
            val blur = remember { BlurLevels() }
            Canvas(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = true
                        shape = MovingHalfShape
                        renderEffect = blur.at(size.width, windowBlur(time.value))
                    },
            ) {
                with(painter) { drawMoving(option, time.value) }
            }
        }
    }
}

/** The moving (left) half, where Window's blur stays. */
private object MovingHalfShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pad = with(density) { Pad.toPx() }
        val width = halfWidth(size.width, pad, with(density) { Gap.toPx() })
        return Outline.Rounded(RoundRect(pad, pad, pad + width, size.height - pad, CornerRadius(halfRadius(size.width, density))))
    }
}

/** Window's blurs for one preview width, built when the width changes. */
private class BlurLevels {
    private var width = 0f
    private val levels = arrayOfNulls<RenderEffect>(8)

    fun at(width: Float, amount: Float): RenderEffect? {
        if (amount <= 0f || width <= 0f) return null
        if (width != this.width) {
            this.width = width
            for (i in levels.indices) {
                // At most 0.6 em, as in the mock
                val radius = 0.6f * EM * width * (i + 1) / levels.size
                levels[i] = BlurEffect(radius, radius, TileMode.Decal)
            }
        }
        return levels[(amount * levels.size).toInt().coerceIn(0, levels.size - 1)]
    }
}
