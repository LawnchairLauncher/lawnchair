/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.blur

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RenderNode
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.ArraySet
import android.util.LongSparseArray
import android.view.SurfaceControl
import android.view.View
import android.view.ViewRootImpl
import android.view.ViewTreeObserver
import androidx.annotation.UiThread
import java.util.WeakHashMap

/**
 * Copy of the framework BackgroundBlurDrawable that sends its blur regions to an effect layer
 * created by root (see [GlassBlurRootLayer]), placed right below the window content.
 * SurfaceFlinger ignores all the blur of an app layer past 10 blur requests on screen
 * (b/399120953), but not the blur of layers created by privileged processes. Without root, the
 * regions go to the window layer, like with the framework drawable.
 */
class GlassBlurDrawable private constructor(private val aggregator: Aggregator) : Drawable() {

    private val renderNode = RenderNode("GlassBlurDrawable")
    private val paint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        color = Color.TRANSPARENT
        isAntiAlias = true
    }
    private val rectPath = Path()
    private val tmpRadii = FloatArray(8)

    // Confined to the UI thread. The values are copied into a BlurRegion, which lives on
    // RenderThread to avoid interference with UI thread updates.
    private var regionVisible = true
    private var blurRadius = 0
    private var cornerRadiusTL = 0f
    private var cornerRadiusTR = 0f
    private var cornerRadiusBL = 0f
    private var cornerRadiusBR = 0f
    private var regionAlpha = 255

    // Do not update from the UI thread. This holds the latest position for this drawable. It is
    // used by the Aggregator from RenderThread to get the final position of the region sent to SF
    private val rect = Rect()

    // HWUI only keeps a weak reference to the listener. It is called from a thread pool, and the
    // callbacks might come out of order w.r.t. the frame number, so the Aggregator applies the
    // updates on RenderThread when processing that same frame.
    private val positionUpdateListener = object : RenderNode.PositionUpdateListener {
        override fun positionChanged(frameNumber: Long, left: Int, top: Int, right: Int, bottom: Int) {
            aggregator.onRenderNodePositionChanged(frameNumber) { rect.set(left, top, right, bottom) }
        }

        override fun positionLost(frameNumber: Long) {
            aggregator.onRenderNodePositionChanged(frameNumber) { rect.setEmpty() }
        }
    }

    init {
        renderNode.addPositionUpdateListener(positionUpdateListener)
    }

    override fun draw(canvas: Canvas) {
        if (rectPath.isEmpty || !isVisible || regionAlpha == 0) {
            return
        }
        canvas.drawPath(rectPath, paint)
        canvas.drawRenderNode(renderNode)
    }

    /**
     * Color that will be alpha blended on top of the blur.
     */
    fun setColor(color: Int) {
        paint.color = color
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (changed) {
            regionVisible = visible
            aggregator.onBlurDrawableUpdated(this)
        }
        return changed
    }

    override fun setAlpha(alpha: Int) {
        if (regionAlpha != alpha) {
            regionAlpha = alpha
            invalidateSelf()
            aggregator.onBlurDrawableUpdated(this)
        }
    }

    override fun getAlpha() = regionAlpha

    /**
     * Blur radius in pixels.
     */
    fun setBlurRadius(radius: Int) {
        if (blurRadius != radius) {
            blurRadius = radius
            invalidateSelf()
            aggregator.onBlurDrawableUpdated(this)
        }
    }

    /**
     * Sets the radius of each corner, in pixels.
     */
    fun setCornerRadius(topLeft: Float, topRight: Float, bottomLeft: Float, bottomRight: Float) {
        if (cornerRadiusTL != topLeft || cornerRadiusTR != topRight ||
            cornerRadiusBL != bottomLeft || cornerRadiusBR != bottomRight
        ) {
            cornerRadiusTL = topLeft
            cornerRadiusTR = topRight
            cornerRadiusBL = bottomLeft
            cornerRadiusBR = bottomRight
            updatePath()
            invalidateSelf()
            aggregator.onBlurDrawableUpdated(this)
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        renderNode.setPosition(left, top, right, bottom)
        updatePath()
    }

    private fun updatePath() {
        // Clockwise from the top left corner
        tmpRadii.fill(cornerRadiusTL, 0, 2)
        tmpRadii.fill(cornerRadiusTR, 2, 4)
        tmpRadii.fill(cornerRadiusBR, 4, 6)
        tmpRadii.fill(cornerRadiusBL, 6, 8)
        rectPath.reset()
        if (regionAlpha == 0 || !isVisible) {
            return
        }
        val b = bounds
        rectPath.addRoundRect(
            b.left.toFloat(),
            b.top.toFloat(),
            b.right.toFloat(),
            b.bottom.toFloat(),
            tmpRadii,
            Path.Direction.CW,
        )
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        throw IllegalArgumentException("not implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    /**
     * Keeps track of all the blur regions of a [ViewRootImpl] and sends them to SurfaceFlinger
     * along with the frame they belong to.
     */
    private class Aggregator(private val viewRoot: ViewRootImpl) : ViewRootImpl.SurfaceChangedCallback {
        private val rtLock = Any()

        // All the visible blur drawables, confined to the UI thread
        private val drawables = ArraySet<GlassBlurDrawable>()

        // Guarded by rtLock
        private val frameRtUpdates = LongSparseArray<ArraySet<Runnable>>()
        private var lastFrameNumber = 0L
        private var lastFrameBlurRegions: Array<BlurRegion>? = null

        private var tmpBlurRegionsForFrame = emptyArray<BlurRegion>()
        private var hasUiUpdates = false
        private var onPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

        // Written on the UI thread, read on RenderThread
        @Volatile
        private var rootLayer: SurfaceControl? = null

        // Regions sent to the window layer before the root layer arrived have to be cleared
        @Volatile
        private var clearWindowRegions = false

        @UiThread
        private fun onRootLayerReady(layer: SurfaceControl) {
            val rootView = viewRoot.view
            if (rootView == null || !rootView.isAttachedToWindow) {
                SurfaceControl.Transaction().remove(layer).apply()
                return
            }
            rootLayer = layer
            clearWindowRegions = true
            viewRoot.addSurfaceChangedCallback(this)
            rootView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    viewRoot.removeSurfaceChangedCallback(this@Aggregator)
                    aggregators.remove(viewRoot)
                    rootLayer = null
                    SurfaceControl.Transaction().remove(layer).apply()
                }
            })
            val t = SurfaceControl.Transaction()
            attachRootLayer(t)
            t.apply()
            if (hasRegions()) {
                hasUiUpdates = true
                rootView.invalidate()
            }
        }

        private fun attachRootLayer(t: SurfaceControl.Transaction) {
            val layer = rootLayer ?: return
            val windowLayer = viewRoot.surfaceControl
            if (!windowLayer.isValid) {
                return
            }
            // Below the window content, so it blurs what is behind the window (the wallpaper)
            t.reparent(layer, windowLayer).setLayer(layer, -1).show(layer)
        }

        override fun surfaceCreated(t: SurfaceControl.Transaction) {
            attachRootLayer(t)
        }

        override fun surfaceReplaced(t: SurfaceControl.Transaction) {
            attachRootLayer(t)
        }

        override fun surfaceDestroyed() {}

        @UiThread
        fun onBlurDrawableUpdated(drawable: GlassBlurDrawable) {
            val shouldBeDrawn = drawable.regionAlpha > 0 && drawable.blurRadius > 0 &&
                drawable.regionVisible
            val isDrawn = drawables.contains(drawable)
            if (shouldBeDrawn) {
                hasUiUpdates = true
                if (!isDrawn) {
                    drawables.add(drawable)
                }
            } else if (isDrawn) {
                hasUiUpdates = true
                drawables.remove(drawable)
            }

            val rootView = viewRoot.view
            if (onPreDrawListener == null && rootView != null && hasRegions()) {
                registerPreDrawListener(rootView)
                if (hasUiUpdates) {
                    rootView.invalidate()
                }
            }
        }

        private fun registerPreDrawListener(rootView: View) {
            val listener = ViewTreeObserver.OnPreDrawListener {
                val uiUpdates = hasUiUpdates
                if (uiUpdates || hasRegions()) {
                    val blurRegionsForNextFrame = getBlurRegionsCopyForRT()
                    viewRoot.registerRtFrameCallback { frame ->
                        synchronized(rtLock) {
                            lastFrameNumber = frame
                            lastFrameBlurRegions = blurRegionsForNextFrame
                            handleDispatchBlurTransactionLocked(frame, blurRegionsForNextFrame, uiUpdates)
                        }
                    }
                }
                if (!hasRegions()) {
                    viewRoot.view?.viewTreeObserver?.removeOnPreDrawListener(onPreDrawListener)
                    onPreDrawListener = null
                }
                true
            }
            onPreDrawListener = listener
            rootView.viewTreeObserver.addOnPreDrawListener(listener)
        }

        // Called from a thread pool
        fun onRenderNodePositionChanged(frameNumber: Long, update: Runnable) {
            // One of the blur region's position has changed, so we have to send an updated list
            // of blur regions to SurfaceFlinger for this frame.
            synchronized(rtLock) {
                val updates = frameRtUpdates.get(frameNumber)
                    ?: ArraySet<Runnable>().also { frameRtUpdates.put(frameNumber, it) }
                updates.add(update)

                if (lastFrameNumber == frameNumber) {
                    // The transaction for this frame has already been sent, so we have to manually
                    // trigger sending a transaction here in order to apply this position update
                    lastFrameBlurRegions?.let {
                        handleDispatchBlurTransactionLocked(frameNumber, it, true)
                    }
                }
            }
        }

        @UiThread
        private fun hasRegions() = drawables.isNotEmpty()

        @UiThread
        private fun getBlurRegionsCopyForRT(): Array<BlurRegion> {
            if (hasUiUpdates) {
                tmpBlurRegionsForFrame = Array(drawables.size) { BlurRegion(drawables.valueAt(it)) }
                hasUiUpdates = false
            }
            return tmpBlurRegionsForFrame
        }

        /**
         * Called on RenderThread with rtLock held.
         *
         * @return the regions to send to SurfaceFlinger for this frame, or null if none changed
         */
        private fun getBlurRegionsForFrameLocked(
            frameNumber: Long,
            blurRegionsForFrame: Array<BlurRegion>,
            forceUpdate: Boolean,
        ): Array<FloatArray>? {
            if (!forceUpdate && (frameRtUpdates.size() == 0 || frameRtUpdates.keyAt(0) > frameNumber)) {
                return null
            }

            // frameRtUpdates holds position updates coming from a thread pool span from
            // RenderThread. At this point, all position updates for frame frameNumber should
            // have been added to frameRtUpdates.
            // Here, we apply all updates for frames <= frameNumber in case some previous update
            // has been missed. This also protects frameRtUpdates from memory leaks.
            while (frameRtUpdates.size() != 0 && frameRtUpdates.keyAt(0) <= frameNumber) {
                val frameUpdates = frameRtUpdates.valueAt(0)
                frameRtUpdates.removeAt(0)
                frameUpdates.forEach { it.run() }
            }

            return blurRegionsForFrame
                .filter { !it.rect.isEmpty }
                .map { it.toFloatArray() }
                .toTypedArray()
        }

        /**
         * Dispatches all blur regions if there are any UI or position updates for that frame.
         */
        private fun handleDispatchBlurTransactionLocked(
            frameNumber: Long,
            blurRegions: Array<BlurRegion>,
            forceUpdate: Boolean,
        ) {
            val regions = getBlurRegionsForFrameLocked(frameNumber, blurRegions, forceUpdate)
                ?: return
            val layer = rootLayer
            if (layer == null) {
                viewRoot.dispatchBlurRegions(regions, frameNumber)
                return
            }
            val t = SurfaceControl.Transaction()
            t.setBlurRegions(layer, regions)
            if (clearWindowRegions) {
                clearWindowRegions = false
                val windowLayer = viewRoot.surfaceControl
                if (windowLayer.isValid) {
                    t.setBlurRegions(windowLayer, emptyArray())
                }
            }
            viewRoot.mergeWithNextTransaction(t, frameNumber)
        }

        companion object {
            @UiThread
            fun get(viewRoot: ViewRootImpl, context: Context): Aggregator = aggregators.getOrPut(viewRoot) {
                Aggregator(viewRoot).also { aggregator ->
                    GlassBlurRootLayer.request(context, aggregator::onRootLayerReady)
                }
            }
        }
    }

    /**
     * Snapshot of a blur drawable, confined to RenderThread.
     */
    private class BlurRegion(drawable: GlassBlurDrawable) {
        private val blurRadius = drawable.blurRadius
        private val cornerRadiusTL = drawable.cornerRadiusTL
        private val cornerRadiusTR = drawable.cornerRadiusTR
        private val cornerRadiusBL = drawable.cornerRadiusBL
        private val cornerRadiusBR = drawable.cornerRadiusBR
        private val alpha = drawable.regionAlpha / 255f
        val rect = drawable.rect

        /**
         * Serializes this region into the layout expected by
         * SurfaceControl.Transaction#setBlurRegions, which has separate X/Y corner radii since
         * Android 17.
         */
        fun toFloatArray(): FloatArray {
            val position = floatArrayOf(
                blurRadius.toFloat(),
                alpha,
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat(),
            )
            val corners = if (Build.VERSION.SDK_INT >= 37) {
                floatArrayOf(
                    cornerRadiusTL,
                    cornerRadiusTL,
                    cornerRadiusTR,
                    cornerRadiusTR,
                    cornerRadiusBL,
                    cornerRadiusBL,
                    cornerRadiusBR,
                    cornerRadiusBR,
                )
            } else {
                floatArrayOf(cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)
            }
            return position + corners
        }
    }

    companion object {
        private val aggregators = WeakHashMap<ViewRootImpl, Aggregator>()

        /**
         * Creates a blur region for the window of the given view, which must be attached.
         */
        @UiThread
        fun create(view: View): GlassBlurDrawable = GlassBlurDrawable(Aggregator.get(view.viewRootImpl, view.context))
    }
}
