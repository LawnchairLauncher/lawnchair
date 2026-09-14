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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import com.android.launcher3.folder.FolderIcon

/**
 * Captures what is actually behind a glass surface, so the glass can refract it.
 *
 * The wallpaper alone is not enough. A launcher draws icons, folders, widgets and
 * the search bar over it, and those are exactly what should bend through a pane
 * of glass sitting on top -- refracting only the wallpaper gives a pane that
 * looks like a tinted blur, because everything the user actually sees through it
 * is missing.
 *
 * The system composites the wallpaper behind the (translucent) launcher window,
 * so it never appears in the view tree. The scene is therefore rebuilt here: the
 * wallpaper first, then the launcher's own hierarchy drawn over it.
 *
 * Capture happens once, before the popup or folder is added to the drag layer --
 * at that moment the hierarchy holds exactly the content behind it, and nothing
 * moves underneath while a menu is open.
 */
object LiquidGlassCapture {

    private const val TAG = "LiquidGlassCapture"

    /**
     * Glass shows a blurred, refracted version of this, so full resolution would
     * cost memory and a slower capture for detail that can never be seen.
     */
    private const val MAX_DIMENSION = 720

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint()

    /**
     * Renders [root]'s current contents over the wallpaper.
     *
     * Must be called on the UI thread, and before the surface that will refract it
     * is added to [root] -- otherwise the glass would be capturing itself.
     *
     * Returns `null` if there is nothing to capture, in which case callers should
     * fall back to the wallpaper on its own.
     */
    /** Where the last captured view sat on screen, so callers can line the scene up. */
    var lastCaptureScreenLeft: Int = 0
        private set
    var lastCaptureScreenTop: Int = 0
        private set

    /**
     * Size in screen pixels of the area the last capture covers.
     *
     * The bitmap itself is downscaled, so this is what the scene has to be drawn
     * at for one scene pixel to land on one screen pixel. Using the size of the
     * view that draws it instead stretches the scene whenever the two differ,
     * which shows up as a blur that drifts further out the further it gets from
     * the capture's origin.
     */
    var lastCaptureWidth: Int = 0
        private set
    var lastCaptureHeight: Int = 0
        private set

    @JvmStatic
    var isCapturing: Boolean = false
        private set

    fun captureBehind(
        root: View,
        excludeSelf: View? = null,
        excludeTint: Int = 0,
        excludeFrosted: Boolean = false,
    ): Bitmap? {
        val width = root.width
        val height = root.height
        if (width <= 0 || height <= 0) return null

        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        lastCaptureScreenLeft = rootLocation[0]
        lastCaptureScreenTop = rootLocation[1]
        lastCaptureWidth = width
        lastCaptureHeight = height

        val scale = (MAX_DIMENSION.toFloat() / maxOf(width, height).toFloat()).coerceAtMost(1f)
        val outWidth = (width * scale).toInt().coerceAtLeast(1)
        val outHeight = (height * scale).toInt().coerceAtLeast(1)

        isCapturing = true
        Log.i(TAG, "captureBehind started, excludeSelf=$excludeSelf")
        try {
            return render(outWidth, outHeight) { canvas ->
                drawWallpaperUnder(root, canvas, outWidth, outHeight)
                canvas.scale(scale, scale)
                withoutGlassPanes(root, excludeSelf) { root.draw(canvas) }
                excludeSelf?.let { patchWithWallpaper(it, root, canvas, excludeTint, excludeFrosted) }
            }
        } finally {
            isCapturing = false
            Log.i(TAG, "captureBehind finished")
        }
    }

    /**
     * Draws [block] into a bitmap, on the GPU where that is possible.
     *
     * A Canvas over a Bitmap is always a software canvas, and a launcher's view
     * tree holds things software cannot draw: an icon pack's themed icons are
     * backed by hardware bitmaps, and asking a software canvas for one throws.
     * One such icon anywhere in the tree used to lose the entire capture, and
     * every glass surface then fell back to refracting bare wallpaper -- which
     * is what it looks like when a themed icon pack is switched on.
     *
     * Rendering through a RenderNode gives a hardware canvas, which can draw
     * them, and the result is copied back into an ordinary bitmap. The software
     * path stays as the fallback for older releases and for the rare case where
     * the GPU path cannot be set up.
     */
    private inline fun render(width: Int, height: Int, crossinline block: (Canvas) -> Unit): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderOnGpu(width, height) { block(it) }?.let { return it }
        }
        return runCatching {
            val bitmap = createBitmap(width, height)
            block(Canvas(bitmap))
            bitmap
        }.onFailure { Log.w(TAG, "could not capture the content behind the glass", it) }
            .getOrNull()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private inline fun renderOnGpu(
        width: Int,
        height: Int,
        crossinline block: (Canvas) -> Unit,
    ): Bitmap? = runCatching {
        val node = RenderNode("sora-capture")
        node.setPosition(0, 0, width, height)
        val recording = node.beginRecording()
        try {
            block(recording)
        } finally {
            node.endRecording()
        }

        // The renderer and its surface are kept between captures. Building them
        // is most of what a capture costs, and every surface asks for the same
        // size, so rebuilding them each time was paying that over and over.
        val target = obtainTarget(width, height) ?: return@runCatching null
        try {
            target.renderer.setContentRoot(node)
            target.renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = target.reader.acquireLatestImage() ?: return@runCatching null
            try {
                val buffer = image.hardwareBuffer ?: return@runCatching null
                try {
                    val hardware = Bitmap.wrapHardwareBuffer(buffer, null)
                        ?: return@runCatching null
                    // Copied off the GPU: callers keep these for the life of a
                    // surface, and read them back as plain pixels.
                    hardware.copy(Bitmap.Config.ARGB_8888, false)
                } finally {
                    buffer.close()
                }
            } finally {
                image.close()
            }
        } finally {
            node.discardDisplayList()
        }
    }.onFailure { Log.w(TAG, "GPU capture unavailable, falling back to software", it) }
        .getOrNull()

    private class GpuTarget(val width: Int, val height: Int) {
        val reader: ImageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer: HardwareRenderer = HardwareRenderer().apply { setSurface(reader.surface) }

        fun destroy() {
            renderer.destroy()
            reader.close()
        }
    }

    private var gpuTarget: GpuTarget? = null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun obtainTarget(width: Int, height: Int): GpuTarget? {
        gpuTarget?.let { if (it.width == width && it.height == height) return it }
        gpuTarget?.destroy()
        gpuTarget = null
        return runCatching { GpuTarget(width, height) }.getOrNull()?.also { gpuTarget = it }
    }

    /**
     * Puts the wallpaper back over the patch of scene [self] occupies.
     *
     * A surface must not be part of the scene it refracts. A folder opening is
     * the clearest case: the capture is taken while its icon is still on screen,
     * so without this the folder's own glass shows that icon's preview icons
     * smeared through it -- the folder refracting itself.
     *
     * Cutting the shape out is not an option; glass has to have something to
     * bend. So the hole is filled with what genuinely lies behind [self], and
     * everything around it is left exactly as captured.
     *
     * [frosted] says which wallpaper that is. On the workspace the icon stands
     * on the wallpaper itself, so the sharp copy is what belongs there. In the
     * app drawer it stands on the drawer's frosted backdrop, which is this same
     * wallpaper already blurred and cached for the drawer to sit on -- so the
     * patch reuses that rather than punching a window of sharp wallpaper through
     * the middle of an otherwise frosted capture, which is what it looked like.
     */
    private fun patchWithWallpaper(
        self: View,
        root: View,
        canvas: Canvas,
        tint: Int,
        frosted: Boolean,
    ) {
        if (self.width <= 0 || self.height <= 0) return
        val picked = if (frosted) {
            LiquidGlassWallpaper.getBlurred(root.context)
        } else {
            LiquidGlassWallpaper.get(root.context)
        }
        val wallpaper = picked ?: return

        val rootLocation = IntArray(2)
        val selfLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        self.getLocationOnScreen(selfLocation)

        val display = root.context.resources.displayMetrics
        if (display.widthPixels <= 0 || display.heightPixels <= 0) return
        val wallpaperScaleX = wallpaper.width / display.widthPixels.toFloat()
        val wallpaperScaleY = wallpaper.height / display.heightPixels.toFloat()

        val source = Rect(
            (selfLocation[0] * wallpaperScaleX).toInt().coerceIn(0, wallpaper.width),
            (selfLocation[1] * wallpaperScaleY).toInt().coerceIn(0, wallpaper.height),
            ((selfLocation[0] + self.width) * wallpaperScaleX).toInt()
                .coerceIn(1, wallpaper.width),
            ((selfLocation[1] + self.height) * wallpaperScaleY).toInt()
                .coerceIn(1, wallpaper.height),
        )
        if (source.isEmpty) return

        // The canvas is already scaled to the capture, so this is drawn in the
        // root's own coordinates, the same ones the hierarchy was drawn in.
        val left = selfLocation[0] - rootLocation[0]
        val top = selfLocation[1] - rootLocation[1]
        val destination = Rect(left, top, left + self.width, top + self.height)
        canvas.drawBitmap(wallpaper, source, destination, bitmapPaint)

        // Whatever the surroundings are washed with goes on too. On a workspace
        // nothing lies over the wallpaper and this is skipped; in the app drawer
        // the scrim and the sheet both colour what is behind an icon, and a patch
        // of bare wallpaper there would read as a hole punched to the desktop.
        if (tint ushr 24 != 0) {
            tintPaint.color = tint
            canvas.drawRect(destination, tintPaint)
        }
    }

    /**
     * Runs [block] with any glass panes in [root] taken out of the hierarchy.
     *
     * Two reasons, and either alone would be enough. A pane draws through a
     * runtime shader, which exists only on a hardware canvas -- asking one to
     * draw into a bitmap throws, and the whole capture is lost, leaving the
     * surface refracting nothing but the wallpaper. And a pane shows the scene
     * behind it, so capturing one would fold that scene into the next capture,
     * each surface stacking on the last.
     *
     * Visibility is restored before returning, within the one synchronous call,
     * so no frame is ever produced with anything missing.
     */
    private inline fun <T> withoutGlassPanes(
        root: View,
        excludeSelf: View? = null,
        block: () -> T,
    ): T {
        val parent = root as? ViewGroup ?: return block()
        val excludedGlass: View? = when (excludeSelf) {
            is FolderIcon -> excludeSelf.iconGlass
            is LiquidGlassPanel -> excludeSelf
            else -> null
        }
        var hidden: MutableList<View>? = null
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child is LiquidGlassPanel && child.visibility == View.VISIBLE) {
                val shouldHide = (excludedGlass == null && child == excludeSelf) ||
                    (excludedGlass != null && child == excludedGlass)
                if (shouldHide) {
                    child.visibility = View.INVISIBLE
                    (hidden ?: ArrayList<View>(2).also { hidden = it }).add(child)
                }
            }
        }
        try {
            return block()
        } finally {
            hidden?.forEach { it.visibility = View.VISIBLE }
        }
    }

    /**
     * Renders everything [parent] draws *underneath* [stopAt], over the wallpaper.
     *
     * [captureBehind] draws a whole view, which is no use for a surface that is
     * itself one of the children: it would capture itself, and everything stacked
     * above it. The app drawer's scrim is exactly that -- a sibling sitting over
     * the workspace, with the drawer above it -- so the siblings before it are
     * drawn one by one instead, giving the home screen without the drawer.
     *
     * Children are drawn through [View.draw], which applies neither their alpha
     * nor their scale. That is deliberate: by the time the drawer is opening the
     * workspace has already begun to fade, and the backdrop wants the home screen
     * as the user last saw it, not as it looks part way through leaving.
     */
    fun captureBelow(parent: ViewGroup, stopAt: View, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        val width = parent.width
        val height = parent.height
        if (width <= 0 || height <= 0) return null

        // Nothing is below the first child, and a view that is not a child at all
        // gives -1 -- neither is something to capture.
        val stopIndex = parent.indexOfChild(stopAt)
        if (stopIndex <= 0) return null

        val scale = (maxDimension.toFloat() / maxOf(width, height).toFloat()).coerceAtMost(1f)
        val outWidth = (width * scale).toInt().coerceAtLeast(1)
        val outHeight = (height * scale).toInt().coerceAtLeast(1)

        isCapturing = true
        try {
            return render(outWidth, outHeight) { canvas ->
                drawWallpaperUnder(parent, canvas, outWidth, outHeight)
                canvas.scale(scale, scale)
                for (index in 0 until stopIndex) {
                    val child = parent.getChildAt(index) ?: continue
                    if (child.visibility != View.VISIBLE || child.width <= 0) continue
                    val save = canvas.save()
                    canvas.translate(child.left.toFloat(), child.top.toFloat())
                    child.draw(canvas)
                    canvas.restoreToCount(save)
                }
            }
        } finally {
            isCapturing = false
        }
    }

    /**
     * Lays the user's wallpaper down as the bottom of a captured scene.
     *
     * The wallpaper is behind the window rather than in the hierarchy, so it has
     * to be drawn explicitly for the scene to match what is on screen.
     *
     * It is cached cropped to the whole display, while [view] usually covers less
     * than that -- the system bars are not its to draw. So the slice under [view]
     * is what gets taken, rather than the whole image stretched to fit:
     * stretching leaves the icons (drawn from the view tree, and so correct)
     * sitting over a wallpaper that has slid.
     */
    private fun drawWallpaperUnder(view: View, canvas: Canvas, outWidth: Int, outHeight: Int) {
        // Something opaque has to go down first. Without it the scene is the
        // launcher's icons over nothing, and blurring transparency produces
        // transparency -- the pane stays but looks like the blur is gone.
        canvas.drawColor(LiquidGlassWallpaper.fallbackColor(view.context))

        val wallpaper = LiquidGlassWallpaper.get(view.context) ?: return
        val display = view.context.resources.displayMetrics
        val location = IntArray(2)
        view.getLocationOnScreen(location)

        val wallpaperScaleX = wallpaper.width / display.widthPixels.toFloat()
        val wallpaperScaleY = wallpaper.height / display.heightPixels.toFloat()
        val source = Rect(
            (location[0] * wallpaperScaleX).toInt().coerceIn(0, wallpaper.width),
            (location[1] * wallpaperScaleY).toInt().coerceIn(0, wallpaper.height),
            ((location[0] + view.width) * wallpaperScaleX).toInt().coerceIn(1, wallpaper.width),
            ((location[1] + view.height) * wallpaperScaleY).toInt().coerceIn(1, wallpaper.height),
        )
        if (source.isEmpty) return
        canvas.drawBitmap(wallpaper, source, Rect(0, 0, outWidth, outHeight), bitmapPaint)
    }
}
