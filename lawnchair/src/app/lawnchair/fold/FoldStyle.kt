package app.lawnchair.fold

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * The look of a fold transition, selected by [FoldTransitionStyle].
 *
 * A style only draws; [FoldTransitionController] owns the progress (hinge or timer), the listener
 * events and the lifecycle. On the main thread, the controller calls:
 * 1. [onStart] once, synchronously from the DeviceProfile change (or the debug replay), before the
 *    new layout is drawn. Return false if the style can't run here: the final layout is then shown
 *    as it is.
 * 2. [onPreDraw] before every frame while running, after layout, so [FoldScene.geometry] matches the
 *    views. Set view properties here.
 * 3. [onDrawOverlay] while the frame is drawn, if [usesOverlay] is true. It draws above the whole
 *    launcher (the root view overlay), never under the wallpaper.
 * 4. [onEnd] exactly once, also after a failure or a cancellation. It must restore every view
 *    property the style changed, so the final layout shows as is.
 *
 * Progress is 0..1, 1 = unfolded. On the large layout ([FoldScene.largeLayout]) the rest state is
 * progress 1; on the cover layout after a fold it is progress 0.
 *
 * Rules: no allocations in [onPreDraw] and [onDrawOverlay]; never hide content without drawing it
 * somewhere else in the same frame; prefer drawing live views ([android.graphics.RenderNode]
 * copies) over bitmaps.
 *
 * FoldStyle itself draws nothing: it stands in for a style that can't run, so the dock still gets
 * its events over the transition.
 */
open class FoldStyle {

    open fun usesOverlay(scene: FoldScene): Boolean = false

    open fun onStart(scene: FoldScene): Boolean = true

    open fun onPreDraw(scene: FoldScene, progress: Float) = Unit

    open fun onDrawOverlay(scene: FoldScene, canvas: Canvas, progress: Float) = Unit

    open fun onEnd(scene: FoldScene) = Unit

    companion object {
        @RequiresApi(Build.VERSION_CODES.Q)
        fun create(style: FoldTransitionStyle): FoldStyle {
            return when (style) {
                FoldTransitionStyle.WINDOW -> WindowFoldStyle()
                FoldTransitionStyle.SIMPLE -> SimpleFoldStyle()
                FoldTransitionStyle.WAVE -> WaveFoldStyle()
                FoldTransitionStyle.BOOK -> BookFoldStyle()
            }
        }
    }
}
