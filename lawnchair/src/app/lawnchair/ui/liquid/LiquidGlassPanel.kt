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

package app.lawnchair.ui.liquid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.icons.shape.PathShapeDelegate
import app.lawnchair.icons.shape.IconShape
import com.android.launcher3.graphics.ThemeManager
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/**
 * A fixed, full-screen overlay that a glass pane is revealed through.
 *
 * This view covers its whole parent and is never moved, scaled or faded itself.
 * The only thing that animates is [setPaneBounds] -- the rectangle the glass
 * shows through.
 *
 * That split is the entire point. The blurred, refracted scene is rendered
 * across the full screen and stays pinned to it, so a pane growing over it
 * reveals more of a still image. Transforming the view instead scales the
 * rendered scene along with it, which reads as a photo being zoomed rather than
 * a piece of glass moving over a desk.
 *
 * The scene comes from [LiquidGlassCapture]: the wallpaper with the launcher's
 * own icons and widgets drawn over it, because those are what should bend
 * through the glass and the wallpaper on its own is not what the user sees.
 */
class LiquidGlassPanel(context: Context) : FrameLayout(context) {

    // ComposeView is final in this Compose version, so it is held rather than
    // subclassed.
    private val composeView = ComposeView(context)

    private var paneLeft by mutableFloatStateOf(0f)
    private var paneTop by mutableFloatStateOf(0f)
    private var paneWidth by mutableFloatStateOf(0f)
    private var paneHeight by mutableFloatStateOf(0f)
    private var paneAlpha by mutableFloatStateOf(1f)
    private var cornerRadiusPx by mutableFloatStateOf(0f)
    private var scene by mutableStateOf<ImageBitmap?>(null)
    private var sceneOffsetX by mutableFloatStateOf(0f)
    private var sceneOffsetY by mutableFloatStateOf(0f)
    private var sceneWidth by mutableFloatStateOf(0f)
    private var sceneHeight by mutableFloatStateOf(0f)
    private var tintEnabled by mutableStateOf(false)

    /**
     * Blur, in dp, applied before the lens bends the scene. Zero by default.
     *
     * Backdrop's own catalog reaches for this far less than seems natural: the
     * Control Center uses none at all, and the playground starts at zero. The
     * reason shows up the moment you use it -- refraction is only visible in
     * what it bends, and a scene already smoothed to a haze has nothing left to
     * bend. Blur first and the glass stops being glass and becomes frosting.
     */
    private var paneBlurDp by mutableFloatStateOf(0f)
    private var blurOnlyEnabled by mutableStateOf(false)
    private var rimLightEnabled by mutableStateOf(true)

    /**
     * An exact wash to use instead of the default light/dark one.
     *
     * A pane floating over the wallpaper only needs enough wash to keep its text
     * legible. A pane sitting *inside* an already-tinted surface has a harder
     * job: the drawer lays a themed colour over its frosted backdrop, and glass
     * that refracts the backdrop alone comes out the wrong colour against
     * everything around it -- a blue window in a pink field. Handing it that
     * same colour puts the two back in the same family.
     */
    private var tintOverride by mutableStateOf<Color?>(null)

    /**
     * Called once per frame while this panel is attached, to refresh the pane rect.
     *
     * Driven from a Choreographer callback rather than the host's draw pass:
     * Compose state written during a draw is not picked up for recomposition, so
     * the pane stayed at its first value and the glass silently stopped drawing.
     * The animation phase is where such updates belong.
     */
    // Runnable rather than a Kotlin lambda type so Java callers can pass a
    // plain method reference.
    var onSyncFrame: Runnable? = null

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            onSyncFrame?.run()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        addView(composeView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Choreographer.getInstance().postFrameCallback(frameCallback)
            }

            override fun onViewDetachedFromWindow(v: View) {
                Choreographer.getInstance().removeFrameCallback(frameCallback)
            }
        })
        refreshScene()
        val dynamicOffset: () -> Offset = {
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            Offset((captureScreenLeft - loc[0]).toFloat(), (captureScreenTop - loc[1]).toFloat())
        }
        composeView.setContent {
            GlassSurface(
                scene = scene,
                paneLeft = { paneLeft },
                paneTop = { paneTop },
                paneWidth = { paneWidth },
                paneHeight = { paneHeight },
                paneAlpha = { paneAlpha },
                cornerRadiusPx = { cornerRadiusPx },
                sceneOffsetX = sceneOffsetX,
                sceneOffsetY = sceneOffsetY,
                sceneWidth = sceneWidth,
                sceneHeight = sceneHeight,
                tinted = tintEnabled,
                blurRadiusDp = { paneBlurDp },
                tintOverride = tintOverride,
                blurOnly = blurOnlyEnabled,
                dynamicOffsetProvider = dynamicOffset,
            )
        }
    }

    private val shadowPath = Path()
    private val shadowRect = RectF()

    /**
     * A line above which this pane draws nothing, in the panel's own coordinates.
     *
     * A pane in the app drawer belongs to a tile in a scrolling list, but the
     * pane itself is a sibling of that list rather than a child of it, so
     * nothing stops it drawing where the list would have been clipped. Scrolled
     * up, the tiles' glass carried on over the header and sat on top of the
     * search bar. NaN means no limit, which is every pane that is not in a list.
     */
    private var clipTop = Float.NaN

    /** Sets the line from [setClipTop] onwards; NaN removes it. */
    fun setClipTop(y: Float) {
        if (clipTop != y) {
            clipTop = y
            invalidate()
        }
    }

    var touchThrough: Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (touchThrough) return false
        return super.dispatchTouchEvent(ev)
    }

    /**
     * The pane's cast shadow, drawn before the glass so the glass sits on it.
     *
     * Here rather than inside the Compose backdrop because it belongs outside
     * the pane, and the backdrop only ever paints within its own shape. Glass
     * gets a shadow; the icons next to it do not.
     */
    override fun dispatchDraw(canvas: Canvas) {
        val limited = !clipTop.isNaN()
        val save = if (limited) {
            val c = canvas.save()
            canvas.clipRect(0f, clipTop, width.toFloat(), height.toFloat())
            c
        } else {
            -1
        }
        super.dispatchDraw(canvas)
        if (paneWidth <= 0f || paneHeight <= 0f || paneAlpha <= 0f) {
            if (limited) canvas.restoreToCount(save)
            return
        }

        // The rim, drawn by the same code that rims a folder plate and bakes
        // into an icon -- not by the backdrop's own highlight shader.
        //
        // Two engines drawing "the same" light will never agree: the shader
        // takes a dot product against a light direction while this traces a
        // gradient down the shape, and the two differ most exactly where the eye
        // compares them -- a folder icon against the open folder it becomes, a
        // search bar against the tiles under it. One drawing of it, over the
        // glass, is the only way there is nothing to notice.
        //
        // Outside the pane it is a shadow and inside it is light, so this sits
        // after the glass: the shadow is clipped to what lies beyond the shape
        // and lands on the background regardless of order.
        if (rimLightEnabled && !blurOnlyEnabled) {
            shadowRect.set(paneLeft, paneTop, paneLeft + paneWidth, paneTop + paneHeight)
            shadowPath.reset()
            shadowPath.addRoundRect(shadowRect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            LiquidRimLight.drawPath(
                canvas,
                shadowPath,
                resources.displayMetrics.density * LiquidRimLight.WIDTH_DP,
                paneAlpha,
            )
        }
        if (limited) canvas.restoreToCount(save)
    }

    fun setCornerRadius(radiusPx: Float) {
        cornerRadiusPx = radiusPx
    }

    /**
     * Lays a thin wash over the glass, light on a light theme and grey on a dark
     * one, so menu text keeps its contrast against whatever it happens to cover.
     */
    fun setTinted(value: Boolean) {
        tintEnabled = value
    }

    /**
     * Softens the scene before it is refracted. Leave at zero for clear glass;
     * a few dp helps where text has to stay legible over a busy wallpaper.
     */
    fun setBlurRadiusDp(radius: Float) {
        if (paneBlurDp != radius) {
            paneBlurDp = radius
            Snapshot.sendApplyNotifications()
            composeView.invalidate()
        }
    }

    fun getBlurRadiusDp(): Float = paneBlurDp

    fun setBlurOnly(value: Boolean) {
        blurOnlyEnabled = value
    }

    fun setRimEnabled(value: Boolean) {
        if (rimLightEnabled != value) {
            rimLightEnabled = value
            invalidate()
        }
    }

    /**
     * Sets the exact wash, or clears it with a fully transparent colour so that
     * [setTinted] decides again.
     */
    fun setTintColor(color: Int) {
        tintOverride = if (color ushr 24 == 0) null else Color(color)
    }

    /**
     * Where on screen, and at what size, this panel's scene was taken.
     *
     * Held per panel rather than read back from [LiquidGlassCapture] each time.
     * Those fields describe whichever capture happened most recently, so a panel
     * that outlives another surface's capture would silently start lining its
     * scene up against someone else's rectangle.
     */
    private var captureScreenLeft = 0
    private var captureScreenTop = 0
    private var captureWidth = 0
    private var captureHeight = 0

    /**
     * Re-reads where this overlay sits on screen relative to the captured scene.
     *
     * The overlay's own origin is not necessarily the drag layer's: insets and
     * padding can move it. Rather than assume they line up, the difference
     * between the two screen positions is measured and applied.
     */
    private fun refreshSceneOffset() {
        val location = IntArray(2)
        getLocationOnScreen(location)
        sceneOffsetX = (captureScreenLeft - location[0]).toFloat()
        sceneOffsetY = (captureScreenTop - location[1]).toFloat()
        sceneWidth = captureWidth.toFloat()
        sceneHeight = captureHeight.toFloat()
    }

    /**
     * Uses a scene somebody else already captured, rather than taking one.
     *
     * A capture is a software draw of the whole hierarchy and costs tens of
     * milliseconds. Where a surface can be handed an image that is already both
     * correct and paid for -- the drawer's frosted backdrop, for its search pill
     * -- that is strictly better than repeating the work, and it guarantees the
     * two show the same thing.
     *
     * [screenLeft] and [screenTop] are where the scene's top-left corner sits on
     * screen; [width] and [height] are the area it covers there, which is not
     * the bitmap's own size when it was captured downscaled.
     */
    fun setScene(bitmap: Bitmap?, screenLeft: Int, screenTop: Int, width: Int, height: Int) {
        val next = bitmap?.takeUnless(Bitmap::isRecycled)?.asImageBitmap()
        val changed = next !== scene ||
            captureScreenLeft != screenLeft || captureScreenTop != screenTop ||
            captureWidth != width || captureHeight != height
        scene = next
        captureScreenLeft = screenLeft
        captureScreenTop = screenTop
        captureWidth = width
        captureHeight = height
        refreshSceneOffset()

        // Pushed through the same way the pane rectangle is. The scene is read
        // in composition, and the recomposer for this view can be stopped --
        // after a configuration change, or simply because nothing else in it
        // ever changes -- in which case a scene set after the first composition
        // never reaches what is drawn, and the pane goes on showing whatever it
        // was built with.
        if (changed) {
            Snapshot.sendApplyNotifications()
            composeView.requestLayout()
            composeView.invalidate()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        refreshSceneOffset()
    }

    /** This overlay's position on screen, for callers placing panes inside it. */
    fun screenLocation(out: IntArray) {
        getLocationOnScreen(out)
    }

    /**
     * The rectangle, in this view's own coordinates, that the glass shows through.
     *
     * Callers pass their current *visual* rectangle: layout bounds with any
     * open/close scale already folded in. The reveal then animates by changing
     * this rectangle while the scene underneath stays exactly where it is.
     */
    fun setPaneBounds(left: Float, top: Float, width: Float, height: Float, alpha: Float) {
        val changed = paneLeft != left || paneTop != top ||
            paneWidth != width || paneHeight != height || paneAlpha != alpha
        paneLeft = left
        paneTop = top
        paneWidth = width
        paneHeight = height
        paneAlpha = alpha

        if (changed) {
            Snapshot.sendApplyNotifications()
            // Geometry is read in the layout phase, so a layout pass -- not a
            // recomposition -- is what actually moves the pane. This keeps working
            // even when the recomposer for this view has stopped.
            composeView.requestLayout()
            composeView.invalidate()
        }
    }

    /**
     * The wallpaper, laid over the screen, until the owner says otherwise.
     *
     * The rectangle matters as much as the bitmap. Setting the scene alone left
     * the capture rect at zero, and a scene with no size is drawn at the
     * overlay's size instead -- a different part of the wallpaper from the one
     * every other pane is showing.
     */
    fun refreshScene() {
        val display = context.resources.displayMetrics
        setScene(LiquidGlassWallpaper.get(context), 0, 0, display.widthPixels, display.heightPixels)
    }

    /**
     * Capture what [root] currently shows, so the glass refracts the real icons
     * rather than only the wallpaper.
     *
     * Call before this panel is added to [root]: at that point the hierarchy holds
     * exactly what will sit behind the glass. Keeps the wallpaper-only scene if
     * the capture fails.
     *
     * [excludeSelf] is the view this pane is standing in for, if any. Its patch
     * of the scene is filled with plain wallpaper rather than captured, so the
     * pane cannot end up refracting itself. [excludeFrosted] asks for the
     * blurred copy of that wallpaper instead, for a pane whose surroundings are
     * themselves frosted -- the app drawer.
     */
    @JvmOverloads
    fun captureBehind(
        root: View,
        excludeSelf: View? = null,
        excludeTint: Int = 0,
        excludeFrosted: Boolean = false,
    ) {
        LiquidGlassCapture.captureBehind(root, excludeSelf, excludeTint, excludeFrosted)
            ?.takeUnless(Bitmap::isRecycled)
            ?.let { scene = it.asImageBitmap() }
        captureScreenLeft = LiquidGlassCapture.lastCaptureScreenLeft
        captureScreenTop = LiquidGlassCapture.lastCaptureScreenTop
        captureWidth = LiquidGlassCapture.lastCaptureWidth
        captureHeight = LiquidGlassCapture.lastCaptureHeight
        refreshSceneOffset()
    }

    /**
     * Copies the captured scene and bounds from another panel without re-rendering.
     */
    fun copySceneFrom(source: LiquidGlassPanel) {
        scene = source.scene
        captureScreenLeft = source.captureScreenLeft
        captureScreenTop = source.captureScreenTop
        captureWidth = source.captureWidth
        captureHeight = source.captureHeight
        refreshSceneOffset()
        Snapshot.sendApplyNotifications()
        composeView.requestLayout()
        composeView.invalidate()
    }
}

@Composable
private fun GlassSurface(
    scene: ImageBitmap?,
    paneLeft: () -> Float,
    paneTop: () -> Float,
    paneWidth: () -> Float,
    paneHeight: () -> Float,
    paneAlpha: () -> Float,
    cornerRadiusPx: () -> Float,
    sceneOffsetX: Float,
    sceneOffsetY: Float,
    sceneWidth: Float,
    sceneHeight: Float,
    tinted: Boolean,
    tintOverride: Color?,
    blurRadiusDp: () -> Float,
    blurOnly: Boolean = false,
    dynamicOffsetProvider: (() -> Offset)? = null,
) {

    val density = LocalDensity.current
    val context = LocalContext.current
    // Read here rather than passed in: every pane in the launcher answers to the
    // one icon shape, and reading it where it is used means none of them can be
    // left holding a stale one.
    val cornerStyle = remember(context) { iconCornerStyle(context) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The scene was captured from the drag layer, and this overlay fills that
        // same drag layer, so the two share one coordinate space: drawing the
        // scene at this overlay's own size maps it 1:1 onto the screen. Deriving
        // the size from display metrics instead would be off by the system bars,
        // which is exactly what threw the blur out of place.
        val overlayWidth = constraints.maxWidth
        val overlayHeight = constraints.maxHeight

        // Drawn at the size of the area that was captured, not this overlay's:
        // the two are not always equal, and stretching the scene to fit shows up
        // as blur that slides further off the further it is from the origin.
        val sceneDrawWidth = if (sceneWidth > 0f) sceneWidth.toInt() else overlayWidth
        val sceneDrawHeight = if (sceneHeight > 0f) sceneHeight.toInt() else overlayHeight

        val fallback = if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
        val backdrop: Backdrop = remember(
            scene, sceneDrawWidth, sceneDrawHeight, sceneOffsetX, sceneOffsetY, fallback, dynamicOffsetProvider,
        ) {
            ScreenAnchoredBackdrop(
                scene = scene,
                sceneSize = IntSize(sceneDrawWidth, sceneDrawHeight),
                sceneOffset = Offset(sceneOffsetX, sceneOffsetY),
                dynamicOffsetProvider = dynamicOffsetProvider,
                fallbackColor = fallback,
            )
        }

        val isDark = isSystemInDarkTheme()
        // Resolved in composition, not in the draw lambda. drawBackdrop records
        // its surface into a graphics layer of its own, and invalidating the
        // view does not make it re-record -- a colour read in there stays at
        // whatever it was when the layer was first built.
        // The thin wash a menu needs to keep its text legible. Panes with no
        // text to protect set a far lighter colour of their own: a heavy wash
        // buries the refraction that makes a pane read as glass at all.
        val tint = tintOverride ?: when {
            !tinted -> Color.Transparent
            isDark -> Color(0xFF8A8A8E).copy(alpha = 0.18f)
            else -> Color.White.copy(alpha = 0.28f)
        }

        Box(
            Modifier
                // Size and position are read in the layout phase, not composition.
                // The recomposer for this view can stop running after a
                // configuration change, and anything read at composition time then
                // freezes at its first value -- which left the pane at zero and the
                // glass permanently blank. Layout reads refresh on requestLayout().
                .layout { measurable, constraints ->
                    val w = paneWidth().toInt().coerceAtLeast(0)
                    val h = paneHeight().toInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    // The node keeps the parent's full size and the pane is placed
                    // inside it. Sizing the node to the pane instead would leave it
                    // at the parent's origin while its content sat at the pane's
                    // coordinates -- drawn outside its own bounds, so misplaced and
                    // clipped at the edges.
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(paneLeft().toInt(), paneTop().toInt())
                    }
                }
                .graphicsLayer { alpha = paneAlpha() }
                .drawBackdrop(
                    backdrop = backdrop,
                    // Read here rather than in composition: like the geometry
                    // above, a value captured at composition time freezes once the
                    // recomposer stops, which flattened the corners to square.
                    // The corner follows whatever icon shape is set: iOS asks
                    // for a continuous curvature, everything else for a plain
                    // circular one. Stated as a corner *style* rather than by
                    // handing over the icon's own outline, which only works on a
                    // square -- and the open folder and the search pill are not
                    // square. This holds on any shape of pane.
                    shape = {
                        RoundedRectangle(
                            with(density) { cornerRadiusPx().toDp() },
                            cornerStyle,
                        )
                    },
                    effects = {
                        // The Control Center's stack, which is vibrancy and a lens
                        // and nothing else. Its proportions too: the lens is scaled
                        // off the pane's smaller side, capped at the 24/48dp it
                        // uses, because a menu is far larger than a control tile
                        // and an uncapped bend would swallow the whole sheet
                        // instead of sitting at its edge.
                        // The pane's smaller side, not the node's.
                        //
                        // The node is deliberately the size of the whole overlay
                        // -- the pane is placed inside it -- so `size` here is the
                        // drag layer, and every pane came out asking for the same
                        // bend: the cap, 24 and 48dp. That is right for a folder
                        // plate and far too much for the search pill, which is
                        // only about 110px tall. Its lens reached further than the
                        // pill was deep, the bends from its top and bottom edges
                        // met in the middle, and what was left read as flat glass
                        // with no bend at all.
                        val radius = blurRadiusDp()
                        if (radius > 0f) blur(radius.dp.toPx())
                        if (!blurOnly) {
                            val minDimension = minOf(paneWidth(), paneHeight())
                            vibrancy()
                            // Floors as well as caps. A tenth of the search pill's
                            // height is barely two pixels of bend -- technically
                            // present, invisible in practice -- so a small pane is
                            // held to a minimum that still reads as glass, while the
                            // cap keeps a large one from swallowing itself.
                            lens(
                                refractionHeight = (minDimension * 0.1f)
                                    .coerceIn(8f.dp.toPx(), 24f.dp.toPx()),
                                refractionAmount = (minDimension * 0.2f)
                                    .coerceIn(16f.dp.toPx(), 48f.dp.toPx()),
                                depthEffect = true,
                            )
                        }
                    },
                    // A lit edge, not an outline. Highlight.Plain traces the
                    // whole rim at one brightness, which reads as a white border
                    // drawn on rather than as light landing on something.
                    //
                    // The shader takes the magnitude of the dot product between
                    // the surface normal and the light, so one direction lights
                    // both the side facing it and the side opposite. Straight up
                    // therefore gives a bright arc along the top and another
                    // along the bottom, both fading out towards the left and
                    // right ends -- which is how a rounded pane of glass sitting
                    // under an overhead light actually catches it.
                    // None: the rim is drawn over the pane in dispatchDraw, by
                    // the same code every other surface uses. Leaving this on as
                    // well would put two different lights on the one edge.
                    highlight = null,
                    shadow = null,
                    onDrawSurface = { if (tint.alpha > 0f) drawRect(tint) },
                ),
        )
    }
}

/**
 * Draws [scene] pinned to the window, whatever moves over it.
 *
 * Backdrop hands every backdrop the drawing element's [LayoutCoordinates], but
 * only when the backdrop declares that it needs them: a
 * `CanvasBackdrop` reports `isCoordinatesDependent = false`, and the modifier
 * then drops the coordinates entirely. Positioning the scene by hand against
 * that -- shifting it by a pane rectangle tracked separately -- means the shift
 * and the pane can disagree for a frame, and the scene visibly slides along with
 * the pane instead of staying put.
 *
 * Declaring the dependency and using the coordinates Backdrop supplies removes
 * that whole class of drift: the offset always comes from where the element
 * actually is, in the same pass that draws it.
 */
private class ScreenAnchoredBackdrop(
    private val scene: ImageBitmap?,
    private val sceneSize: IntSize,
    private val sceneOffset: Offset,
    private val dynamicOffsetProvider: (() -> Offset)? = null,
    private val fallbackColor: Color,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        // A scene can be missing -- reading the wallpaper needs storage access,
        // and the cache is dropped when the wallpaper changes. Falling back to a
        // flat colour keeps a pane of glass on screen; returning here would make
        // it vanish outright, which is what happened on a theme switch.
        val image = scene
        if (image == null) {
            drawRect(fallbackColor)
            return
        }
        val origin = coordinates?.positionInRoot() ?: Offset.Zero
        val offset = dynamicOffsetProvider?.invoke() ?: sceneOffset
        // sceneOffset carries the gap between where the scene was captured and
        // where this overlay actually sits, measured rather than assumed.
        translate(offset.x - origin.x, offset.y - origin.y) {
            drawImage(
                image = image,
                dstOffset = IntOffset.Zero,
                dstSize = sceneSize,
            )
        }
    }
}

/**
 * The corner style the chosen icon shape asks for.
 *
 * iOS draws a superellipse -- curvature easing continuously into the edge --
 * and the shapes library models that as [RoundedCornerStyle.Continuous]. Every
 * other shape here is built from circular arcs, so it gets the circular corner.
 *
 * A style rather than the icon's own outline on purpose: the outline is defined
 * on a square and stretching it onto the open folder or the search pill would
 * distort exactly the corners it was chosen for. A corner style is well defined
 * at any proportion.
 */
private fun iconCornerStyle(context: android.content.Context): RoundedCornerStyle {
    val shape = runCatching {
        (ThemeManager.INSTANCE.get(context).folderShape as? PathShapeDelegate)?.iconShape
    }.getOrNull()
    return if (shape === IconShape.Cupertino) {
        RoundedCornerStyle.Continuous
    } else {
        RoundedCornerStyle.Circular
    }
}
