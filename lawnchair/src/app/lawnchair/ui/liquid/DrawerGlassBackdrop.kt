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
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R
import com.android.launcher3.util.Executors

/**
 * The frosted home screen that shows through behind the app drawer.
 *
 * The drawer used to open over a flat colour, which hides the home screen rather
 * than suggesting it is still there underneath. This puts the workspace back:
 * the wallpaper and the icons standing on it are captured together and blurred,
 * and the scrim's own colour is then laid over the result as a tint.
 *
 * The capture is deliberately taken while the drawer is *closed* and reused for
 * the whole gesture. Blurring is a software pass costing tens of milliseconds --
 * far too slow to run on the frame the drawer starts opening, which is the one
 * frame the user is most likely to notice. Taken while nothing is moving, it
 * costs nothing anyone can see, and by the time it is needed the bitmap is
 * already sitting there.
 *
 * Because the image is fixed to the screen rather than to the drawer, the
 * backdrop stays put while the drawer travels over it, which is what keeps a
 * still image reading as glass.
 *
 * @param canCapture whether now is a moment when a capture may be taken. Checked
 *   again when the capture finally runs, not only when it is scheduled: by then
 *   the user may have started opening the drawer, and a capture is both wrong
 *   (it would include the drawer) and expensive at that point.
 */
class DrawerGlassBackdrop(
    private val scrim: View,
    private val canCapture: () -> Boolean,
) {

    private var blurred: Bitmap? = null

    /**
     * Workspace scroll and wallpaper the cached capture was taken with, so a
     * page change or a new wallpaper is noticed rather than shown stale.
     */
    private var capturedScroll = NO_CAPTURE
    private var capturedGeneration = -1

    private var scheduledScroll = NO_CAPTURE
    private var captureScheduled = false

    private var workspace: View? = null

    /** Distinguishes a blur still in flight from the one that superseded it. */
    private var captureToken = 0

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = Rect()

    private val captureRunnable = Runnable {
        captureScheduled = false
        if (scrim.isAttachedToWindow && canCapture()) capture()
    }

    /**
     * The frosted home screen, for surfaces sitting inside the drawer that want
     * to refract the very thing drawn behind them.
     *
     * The drawer's search pill is one: giving it this rather than a capture of
     * its own means the two agree by construction, and costs nothing -- the
     * expensive part is the capture, and it has already happened.
     */
    /**
     * The blurred wallpaper, cached.
     *
     * Not a capture of the home screen any more. Capturing it meant blurring a
     * fresh bitmap every time the workspace settled, downscaling it to afford
     * that, and still ending up with a picture of the icons smeared into the
     * background. The wallpaper on its own is what the drawer should be sitting
     * on, it is blurred once for the whole launcher, and every glass surface
     * already reads it -- so the drawer and the panes floating on it are looking
     * at the same image with nothing to keep in step.
     */
    val frosted: Bitmap? get() = LiquidGlassWallpaper.getBlurred(scrim.context)

    /**
     * The same scene before it was blurred.
     *
     * A pane that is meant to *read* as glass inside the drawer wants this one,
     * not [frosted]. Refraction is only visible in what it bends, and [frosted]
     * is deliberately smoothed until a whole screen of it reads as haze -- a
     * pane handed that has nothing left to bend and comes out as plain blur.
     *
     * Not every pane in the drawer wants it. A folder tile is meant to vanish
     * into its surroundings and so refracts [frosted]; the search pill is meant
     * to be seen as a pane set into them, and so takes this.
     */
    val sharp: Bitmap? get() = LiquidGlassWallpaper.get(scrim.context)

    /**
     * Draws the backdrop, [openness] being how far the drawer has travelled --
     * 0 on the workspace, 1 with the drawer fully open.
     *
     * Must be called before the scrim's own colour, so the colour tints it.
     */
    fun draw(canvas: Canvas, openness: Float, isMerged: Boolean = false, overScrollY: Int = 0) {
        if (openness <= 0f || isMerged) return
        val bitmap = frosted ?: return
        val width = scrim.width
        val height = scrim.height
        if (width <= 0 || height <= 0) return

        source.set(0, 0, bitmap.width, bitmap.height)
        val edge = height * (1f - openness.coerceIn(0f, 1f))
        val top = edge + overScrollY
        if (top >= height) return

        destination.set(0, top.toInt(), width, (top + height).toInt())

        paint.alpha = 255
        val save = canvas.save()
        canvas.clipRect(0f, top.coerceAtLeast(0f), width.toFloat(), height.toFloat())
        canvas.drawBitmap(bitmap, source, destination, paint)
        canvas.restoreToCount(save)
    }

    /**
     * Call on every frame the drawer is closed; takes a capture once the home
     * screen has settled.
     *
     * The wait is the point. This runs during a home screen scroll too, when the
     * workspace is moving and every frame has 8ms to make its deadline -- long
     * enough for a bitmap draw to be exactly the thing that misses it.
     * Rescheduling on each change means the capture only ever happens once the
     * scroll has come to a stop.
     *
     * A capture is kept until the home screen it was taken from actually
     * changes. Retaking one each time the drawer closed was the obvious thing to
     * do and cost 30ms of main thread every time, to redraw a screen that cannot
     * change while the drawer is covering it.
     */
    fun refreshWhileClosed() {
        val scroll = workspaceScroll()
        val generation = LiquidGlassWallpaper.generation
        if (blurred != null && capturedScroll == scroll && capturedGeneration == generation) return
        if (captureScheduled && scheduledScroll == scroll) return

        scheduledScroll = scroll
        captureScheduled = true
        scrim.removeCallbacks(captureRunnable)
        scrim.postDelayed(captureRunnable, SETTLE_DELAY_MS)
    }

    fun discard() {
        scrim.removeCallbacks(captureRunnable)
        captureScheduled = false
        blurred = null
        workspace = null
        capturedScroll = NO_CAPTURE
        capturedGeneration = -1
    }

    /**
     * Takes the scene on the main thread and blurs it off it.
     *
     * Drawing views has to happen on the thread that owns them, but resampling
     * is just arithmetic over a bitmap nobody else can see yet, and it is the
     * more expensive half. Splitting the two keeps the main thread holding only
     * the part that genuinely needs it.
     */
    private fun capture() {
        val parent = scrim.parent as? ViewGroup ?: return

        val startedAt = SystemClock.elapsedRealtimeNanos()
        val scene = LiquidGlassCapture.captureBelow(parent, scrim, CAPTURE_MAX_DIMENSION) ?: return
        val drawnAt = SystemClock.elapsedRealtimeNanos()

        val scroll = workspaceScroll()
        val generation = LiquidGlassWallpaper.generation
        val token = ++captureToken

        Executors.THREAD_POOL_EXECUTOR.execute {
            val result = LiquidGlassBlur.resample(scene, DOWNSCALE)
            val blurredAt = SystemClock.elapsedRealtimeNanos()
            Executors.MAIN_EXECUTOR.execute {
                // A newer capture may have overtaken this one while it blurred;
                // its scene is the current one, so this result is now junk.
                if (token != captureToken) {
                    result?.takeIf { it !== scene }?.recycle()
                    scene.recycle()
                    return@execute
                }
                // The unblurred scene was never handed out, so it is safe to
                // free. The previous backdrop is only dropped, never recycled: a
                // frame holding it may still be in flight on the render thread.
                if (result != null && result !== scene) scene.recycle()

                blurred = result ?: scene
                capturedScroll = scroll
                capturedGeneration = generation
                if (LOG_TIMING) {
                    Log.w(
                        TAG,
                        "backdrop ready: draw ${(drawnAt - startedAt) / 1_000_000f}ms on the main " +
                            "thread, blur ${(blurredAt - drawnAt) / 1_000_000f}ms off it",
                    )
                }
            }
        }
    }

    /**
     * Which part of the workspace is showing, used only to tell one resting
     * position from another. Resolved once -- this is read on every frame.
     */
    private fun workspaceScroll(): Int {
        val view = workspace ?: (scrim.parent as? ViewGroup)
            ?.findViewById<View>(R.id.workspace)
            ?.also { workspace = it }
        return view?.scrollX ?: NO_CAPTURE
    }

    private companion object {
        const val TAG = "DrawerGlassBackdrop"

        /** Timings for tuning the capture; off outside deliberate measurement. */
        const val LOG_TIMING = false

        /**
         * A scroll position no workspace can be at, standing in for "nothing
         * cached" so that a genuine scroll of 0 still counts as captured.
         */
        const val NO_CAPTURE = Int.MIN_VALUE

        /** How long the home screen has to hold still before it is captured. */
        const val SETTLE_DELAY_MS = 120L

        /**
         * The scene is only ever shown blurred, so it is captured small: less to
         * draw in software, less to shrink, and no detail lost that could have
         * survived the blur anyway.
         */
        /**
         * The scene is captured at 720p, not smaller.
         *
         * At 512 the home screen behind the drawer had lost its icons to mush
         * before the blur even ran: the capture was already throwing away more
         * detail than the blur was meant to. Blur should be the thing that
         * softens the image, not the resolution it was stored at.
         */
        const val CAPTURE_MAX_DIMENSION = 720

        /**
         * Chosen so the blur works out at roughly 40px measured on screen -- deep
         * enough that icons read as colour rather than as shapes, which is what
         * separates a frosted backdrop from a merely out-of-focus one.
         */
        /**
         * How hard the scene is blurred, as a fraction of the most this blur
         * will do.
         *
         * The blur works by shrinking the image and growing it back, so its
         * strength *is* the factor it shrinks by, and stating the factor
         * directly says nothing about how blurred that leaves things. Stated as
         * a fraction it can be read and changed as what it is.
         */
        const val BLUR_STRENGTH = 0.70f
        private const val MAX_DOWNSCALE = 10
        val DOWNSCALE = Math.max(1, Math.round(MAX_DOWNSCALE * BLUR_STRENGTH))
    }
}
