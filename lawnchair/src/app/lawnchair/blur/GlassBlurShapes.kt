package app.lawnchair.blur

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.View
import androidx.annotation.UiThread
import com.android.launcher3.graphics.ShapeDelegate
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Shapes of the blur regions. SurfaceFlinger only supports rounded rects, with a radius per
 * corner, so these are the closest fit to the actual shapes.
 */
internal object GlassBlurShapes {
    // Icon shape pixels are at least this opaque, translucent icon packs go down to ~0.2 alpha...
    private const val ICON_ALPHA_THRESHOLD = 32

    // ...and not black, to leave out the shadow drawn around icon pack icons
    private const val ICON_MIN_CHANNEL = 24

    // On a corner rounded with radius r, the first point of the diagonal inside the shape is
    // r * (1 - 1/sqrt(2)) away from the corner along each axis
    private val DIAGONAL_TO_RADIUS = 1f / (1f - sqrt(0.5f))

    /**
     * Blur region of an icon: the bounding box of its opaque part and the radius of each
     * corner, as fractions of the bitmap width.
     */
    class IconShape(val bounds: RectF, val radiusTL: Float, val radiusTR: Float, val radiusBL: Float, val radiusBR: Float)

    // Confined to the UI thread
    private val iconShapes = WeakHashMap<Bitmap, IconShape>()
    private val viewsWaitingForShape = WeakHashMap<Bitmap, MutableList<WeakReference<View>>>()

    private var folderShape: ShapeDelegate? = null
    private var folderCornerRatio = 0f

    /**
     * Returns the blur region of an icon. The first time, it returns null and reads the pixels
     * in the background, then redraws the view.
     */
    @UiThread
    fun getIconShape(icon: Bitmap, view: View): IconShape? {
        iconShapes[icon]?.let { return it }
        viewsWaitingForShape[icon]?.let {
            it.add(WeakReference(view))
            return null
        }
        viewsWaitingForShape[icon] = mutableListOf(WeakReference(view))
        THREAD_POOL_EXECUTOR.execute {
            val shape = computeIconShape(icon)
            MAIN_EXECUTOR.execute {
                iconShapes[icon] = shape
                viewsWaitingForShape.remove(icon)?.forEach { it.get()?.invalidate() }
            }
        }
        return null
    }

    private fun computeIconShape(icon: Bitmap): IconShape {
        // Hardware bitmaps cannot be read directly
        val readable = if (icon.config == Bitmap.Config.HARDWARE) {
            icon.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            icon
        }
        val w = readable.width
        val h = readable.height
        val pixels = IntArray(w * h)
        readable.getPixels(pixels, 0, w, 0, 0, w, h)
        if (readable !== icon) {
            readable.recycle()
        }

        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (isShapePixel(pixels[y * w + x])) {
                    left = min(left, x)
                    right = max(right, x)
                    top = min(top, y)
                    bottom = max(bottom, y)
                }
            }
        }
        if (right < 0) {
            return IconShape(RectF(), 0f, 0f, 0f, 0f)
        }
        val maxRadius = min(right - left + 1, bottom - top + 1) / 2
        fun cornerRadius(x: Int, y: Int, dx: Int, dy: Int): Float {
            for (k in 0 until maxRadius) {
                if (isShapePixel(pixels[(y + dy * k) * w + x + dx * k])) {
                    return min(k * DIAGONAL_TO_RADIUS, maxRadius.toFloat()) / w
                }
            }
            return maxRadius.toFloat() / w
        }
        return IconShape(
            RectF(left.toFloat() / w, top.toFloat() / h, (right + 1).toFloat() / w, (bottom + 1).toFloat() / h),
            radiusTL = cornerRadius(left, top, 1, 1),
            radiusTR = cornerRadius(right, top, -1, 1),
            radiusBL = cornerRadius(left, bottom, 1, -1),
            radiusBR = cornerRadius(right, bottom, -1, -1),
        )
    }

    private fun isShapePixel(argb: Int) = Color.alpha(argb) >= ICON_ALPHA_THRESHOLD &&
        max(Color.red(argb), max(Color.green(argb), Color.blue(argb))) >= ICON_MIN_CHANNEL

    /**
     * Returns the corner radius of the folder shape, as a fraction of half its edge.
     */
    @UiThread
    fun getFolderCornerRatio(shape: ShapeDelegate): Float {
        if (shape == folderShape) {
            return folderCornerRatio
        }
        val ratio = if (shape is ShapeDelegate.RoundedSquare) {
            shape.radiusRatio
        } else {
            val path = Path().also { shape.addToPath(it, 0f, 0f, 50f) }
            val region = Region().apply { setPath(path, Region(0, 0, 100, 100)) }
            var k = 0
            while (k < 50 && !region.contains(k, k)) {
                k++
            }
            min(1f, k * DIAGONAL_TO_RADIUS / 50f)
        }
        folderShape = shape
        folderCornerRatio = ratio
        return ratio
    }
}
