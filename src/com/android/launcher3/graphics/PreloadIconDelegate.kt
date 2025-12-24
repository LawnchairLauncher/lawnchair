/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.graphics

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.Cap.ROUND
import android.graphics.Paint.Style.FILL
import android.graphics.Paint.Style.STROKE
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Rect
import android.util.FloatProperty
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.ColorUtils
import com.android.app.animation.Interpolators
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.AnimatorListeners
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.FastBitmapDrawableDelegate
import com.android.launcher3.icons.FastBitmapDrawableDelegate.DelegateFactory
import com.android.launcher3.icons.GraphicsUtils.resizeToContentSize
import com.android.launcher3.icons.GraphicsUtils.transformed
import com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR
import com.android.launcher3.icons.IconShape
import com.android.launcher3.model.data.ItemInfoWithIcon
import kotlin.math.max
import kotlin.math.min

/** Extension of [FastBitmapDrawable] which shows a progress bar around the icon. */
class PreloadIconDelegate(
    item: ItemInfoWithIcon,
    isDarkMode: Boolean,
    private val iconShape: IconShape,
    private val host: FastBitmapDrawable,
    private val parentDelegate: FastBitmapDrawableDelegate,
    private val themedSeedColor: Int,
    private val themedSeedColorDark: Int,
    private val themedProgressColor: Int,
    private val themedProgressColorDark: Int,
) : FastBitmapDrawableDelegate by parentDelegate {
    private val pathMeasure =
        PathMeasure().apply { setPath(iconShape.path, true /* force close */) }
    private var trackLength = pathMeasure.length
    private val progressPath = Path()
    private val progressPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            strokeCap = ROUND
            strokeJoin = Paint.Join.ROUND
        }
    private var progressColor: Int
    private var trackColor: Int
    private var plateColor: Int
    private var ranFinishAnimation = false
    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private var internalStateProgress = 0f
    // This multiplier is used to animate scale when going from 0 to non-zero and expanding
    private val invalidateRunnable = Runnable { host.invalidateSelf() }
    private val iconScaleMultiplier = AnimatedFloat(invalidateRunnable)
    private val fixedDelegateBounds =
        Rect(0, 0, iconShape.pathSize, iconShape.pathSize).also {
            parentDelegate.onBoundsChange(it)
        }
    @get:VisibleForTesting
    var activeAnimation: ObjectAnimator? = null
        private set

    init {
        val m3HCT = FloatArray(3)
        if (isThemed()) {
            // colorInverseSurface maps to the surface colors in light and dark
            // used by the themed icons
            val seedColor = if (isDarkMode) themedSeedColorDark else themedSeedColor
            ColorUtils.colorToM3HCT(seedColor, m3HCT)
            // progress color is primary token in light mode and secondary token in dark mode
            progressColor = if (isDarkMode) themedProgressColorDark else themedProgressColor
            plateColor =
                ColorUtils.M3HCTToColor(
                    m3HCT[0],
                    if (isDarkMode) 36f else 24f, // 36 chroma (dark) / 24 chroma (light)
                    if (isDarkMode) 10f else 80f, // 10 tone (dark) / 80 tone (light)
                )

            trackColor =
                ColorUtils.M3HCTToColor(
                    m3HCT[0],
                    16f, // 16 chroma for both
                    if (isDarkMode) 30f else 90f, // 30 tone (dark) / 90 tone (light)
                )
        } else {
            val m3HCT = FloatArray(3)
            ColorUtils.colorToM3HCT(item.bitmap.color, m3HCT)
            // Progress color
            progressColor =
                ColorUtils.M3HCTToColor(
                    m3HCT[0],
                    m3HCT[1],
                    if (isDarkMode) max(m3HCT[2].toDouble(), 55.0).toFloat()
                    else min(m3HCT[2].toDouble(), 40.0).toFloat(),
                )

            // Track color
            trackColor =
                ColorUtils.M3HCTToColor(m3HCT[0], 16f, (if (isDarkMode) 30 else 90).toFloat())

            // Plate color
            plateColor =
                ColorUtils.M3HCTToColor(
                    m3HCT[0],
                    (if (isDarkMode) 36 else 24).toFloat(),
                    (if (isDarkMode) 20 else 80).toFloat(),
                )
        }
        // If it's a pending app we will animate scale and alpha when it's no longer pending.
        iconScaleMultiplier.updateValue((if (item.progressLevel == 0) 0 else 1).toFloat())
    }

    override fun onBoundsChange(bounds: Rect) {
        // Do nothing
    }

    override fun drawContent(
        info: BitmapInfo,
        iconShape: IconShape,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {
        if (ranFinishAnimation) {
            parentDelegate.drawContent(info, iconShape, canvas, bounds, paint)
        } else if (Flags.enableLauncherIconShapes()) {
            drawShapedProgressIcon(info, canvas, bounds, paint)
        } else {
            drawDefaultProgressIcon(info, canvas, bounds, paint)
        }
    }

    private fun drawShapedProgressIcon(
        info: BitmapInfo,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {
        if (internalStateProgress > 0f) {
            if (internalStateProgress < 1f) {
                // Draw icon at scale UNDER the progress and background paths.
                // We do this to ensure that the gap stroke is drawn at a consistent width,
                // overlapping slightly with the icon delegate.
                drawIconAtScale(info, canvas, bounds, paint)
            }
            val size = iconShape.pathSize.toFloat()
            canvas.resizeToContentSize(bounds, size) {
                progressPaint.style = STROKE
                // Draw a "gap" stroke for between the icon and track
                canvas.setupStrokeWidthFactor(
                    PLATE_STROKE_SIZE,
                    PROGRESS_GAP_SIZE / 2,
                    size,
                    progressPaint,
                ) {
                    progressPaint.color = plateColor
                    drawPath(iconShape.path, progressPaint)
                }
                // Draw the track and progress.
                canvas.setupStrokeWidthFactor(PROGRESS_STROKE_SIZE, 0f, size, progressPaint) {
                    progressPaint.color = trackColor
                    drawPath(iconShape.path, progressPaint)
                    progressPaint.color = progressColor
                    drawPath(progressPath, progressPaint)
                }
            }
            if (internalStateProgress >= 1f) {
                // Draw icon at scale animating OVER the progress and background path.
                // This helps the animation scale the icon over the gap/track when install is done.
                drawIconAtScale(info, canvas, bounds, paint)
            }
        } else {
            // Just draw Icon when no progress
            drawIconAtScale(info, canvas, bounds, paint)
        }
    }

    /**
     * Sets up the canvas and the paint, such that the shape path when drawn has a stroke width of
     * [factor] * [canvasSize] and is at a distance of [distanceFromEdge] * [canvasSize] on all
     * edges
     */
    private inline fun Canvas.setupStrokeWidthFactor(
        factor: Float,
        distanceFromEdge: Float,
        canvasSize: Float,
        paint: Paint,
        block: () -> Unit,
    ) {
        val iconScale = 1 - iconScaleMultiplier.value * (1 - SMALL_ICON_SCALE)
        val finalStrokeSize = factor * canvasSize
        // Since the stroke is drawn with half its width on each side, scale down the canvas so that
        // each edge is half the stroke size away from the original edge
        // Account for icon being scaled down, which will move the icon away from the gap stroke.
        val finalDistanceFromEdge = distanceFromEdge * canvasSize / iconScale + finalStrokeSize / 2
        val scaleFactor = (canvasSize - 2 * finalDistanceFromEdge) / canvasSize
        transformed {
            scale(scaleFactor, scaleFactor, canvasSize / 2, canvasSize / 2)
            paint.strokeWidth = finalStrokeSize / scaleFactor
            block.invoke()
        }
    }

    private fun drawDefaultProgressIcon(
        info: BitmapInfo,
        canvas: Canvas,
        bounds: Rect,
        paint: Paint,
    ) {
        if (internalStateProgress > 0) {
            if (internalStateProgress < 1f) {
                // Draw icon at scale UNDER the progress and background paths.
                drawIconAtScale(info, canvas, bounds, paint)
            }
            val size = iconShape.pathSize.toFloat()
            canvas.resizeToContentSize(bounds, size) {
                val center = size / 2
                canvas.scale(1 - PROGRESS_BOUNDS_SCALE, 1 - PROGRESS_BOUNDS_SCALE, center, center)
                // Draw background.
                progressPaint.style = FILL
                progressPaint.color = plateColor
                canvas.drawPath(progressPath, progressPaint)
                progressPaint.style = STROKE
                progressPaint.strokeWidth = size * PROGRESS_STROKE_MULTIPLIER_SCALED
                progressPaint.color = trackColor
                canvas.drawPath(iconShape.path, progressPaint)
                progressPaint.color = progressColor
                canvas.drawPath(progressPath, progressPaint)
            }
            if (internalStateProgress >= 1f) {
                // Draw icon at scale animating OVER the progress and background path.
                drawIconAtScale(info, canvas, bounds, paint)
            }
        } else {
            // Just draw Icon when no progress
            drawIconAtScale(info, canvas, bounds, paint)
        }
    }

    /** Draws just the icon to scale */
    private fun drawIconAtScale(info: BitmapInfo, canvas: Canvas, bounds: Rect, paint: Paint) {
        canvas.transformed {
            // Bring it to fixed-delegate bounds
            translate(bounds.left.toFloat(), bounds.top.toFloat())
            scale(
                bounds.width().toFloat() / fixedDelegateBounds.width(),
                bounds.height().toFloat() / fixedDelegateBounds.height(),
            )
            val scale = 1 - iconScaleMultiplier.value * (1 - SMALL_ICON_SCALE)
            scale(
                scale,
                scale,
                fixedDelegateBounds.exactCenterX(),
                fixedDelegateBounds.exactCenterY(),
            )
            parentDelegate.drawContent(info, iconShape, canvas, fixedDelegateBounds, paint)
        }
    }

    /** Updates the install progress based on the level */
    override fun onLevelChange(level: Int): Boolean {
        // Run the animation if we have already been bound.
        updateInternalState(level * 0.01f, false, null)
        return true
    }

    /** Runs the finish animation if it is has not been run after last call to [.onLevelChange] */
    fun maybePerformFinishedAnimation(oldIcon: FastBitmapDrawable, onFinishCallback: Runnable?) {
        val oldDelegate = extractPreloadDelegate(oldIcon) ?: this
        progressColor = oldDelegate.progressColor
        trackColor = oldDelegate.trackColor
        plateColor = oldDelegate.plateColor
        if (oldDelegate.internalStateProgress >= 1) {
            internalStateProgress = oldDelegate.internalStateProgress
        }
        // If the drawable was recently initialized, skip the progress animation.
        if (internalStateProgress == 0f) {
            internalStateProgress = 1f
        }
        updateInternalState(1 + COMPLETE_ANIM_FRACTION, true, onFinishCallback)
    }

    private fun updateInternalState(
        finalProgress: Float,
        isFinish: Boolean,
        onFinishCallback: Runnable?,
    ) {
        activeAnimation?.cancel()
        activeAnimation = null
        val animateProgress = finalProgress >= internalStateProgress && host.bounds.width() > 0
        if (!animateProgress || ranFinishAnimation) {
            setInternalProgress(finalProgress)
            if (isFinish) onFinishCallback?.run()
        } else {
            activeAnimation =
                ObjectAnimator.ofFloat(this, INTERNAL_STATE, finalProgress).also {
                    it.duration =
                        ((finalProgress - internalStateProgress) * DURATION_SCALE).toLong()
                    it.interpolator = Interpolators.LINEAR
                    if (isFinish) {
                        it.addListener(
                            AnimatorListeners.forEndCallback(Runnable { ranFinishAnimation = true })
                        )
                        if (onFinishCallback != null)
                            it.addListener(AnimatorListeners.forEndCallback(onFinishCallback))
                    }
                    it.start()
                }
        }
    }

    /**
     * Sets the internal progress and updates the UI accordingly
     *
     * for progress <= 0:
     * - icon is pending
     * - progress track is not visible
     * - progress bar is not visible
     *
     * for progress < 1:
     * - icon without pending motion
     * - progress track is visible
     * - progress bar is visible. Progress bar is drawn as a fraction of [scaledTrackPath].
     *
     * @see PathMeasure.getSegment
     */
    private fun setInternalProgress(progress: Float) {
        // Animate scale and alpha from pending to downloading state.
        if (progress > 0 && internalStateProgress == 0f) {
            // Progress is changing for the first time, animate the icon scale
            iconScaleMultiplier.animateToValue(1f).apply {
                duration = SCALE_AND_ALPHA_ANIM_DURATION
                interpolator = Interpolators.EMPHASIZED
                start()
            }
        }
        internalStateProgress = progress
        if (progress <= 0) {
            iconScaleMultiplier.updateValue(0f)
        } else {
            pathMeasure.getSegment(
                0f,
                (min(progress.toDouble(), 1.0) * trackLength).toFloat(),
                progressPath,
                true,
            )
            if (progress > 1) {
                // map the scale back to original value
                iconScaleMultiplier.updateValue(
                    Utilities.mapBoundToRange(
                        progress - 1,
                        0f,
                        COMPLETE_ANIM_FRACTION,
                        1f,
                        0f,
                        Interpolators.EMPHASIZED,
                    )
                )
            }
        }
        host.invalidateSelf()
    }

    fun reapplyProgress(item: ItemInfoWithIcon) {
        host.level = item.progressLevel
        host.isDisabled = item.isDisabled || item.isPendingDownload
    }

    companion object {
        private val INTERNAL_STATE: FloatProperty<PreloadIconDelegate> =
            object : FloatProperty<PreloadIconDelegate>("internalStateProgress") {
                override fun get(obj: PreloadIconDelegate): Float = obj.internalStateProgress

                override fun setValue(obj: PreloadIconDelegate, value: Float) =
                    obj.setInternalProgress(value)
            }
        private const val DURATION_SCALE: Long = 500
        private const val SCALE_AND_ALPHA_ANIM_DURATION: Long = 500
        // The smaller the number, the faster the animation would be.
        // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
        private const val COMPLETE_ANIM_FRACTION = 1f
        private const val SMALL_ICON_SCALE = 24f / 30
        private const val PROGRESS_STROKE_SIZE = 2f / 30
        private const val PROGRESS_GAP_SIZE = 1f / 30
        private const val PLATE_STROKE_SIZE = PROGRESS_STROKE_SIZE + PROGRESS_GAP_SIZE
        private const val PROGRESS_STROKE_SCALE = 0.055f
        private const val PROGRESS_BOUNDS_SCALE = 0.075f
        // Final progress stroke multiplier, taking into account, any canvas scale performed as part
        // of drawing the path
        private val PROGRESS_STROKE_MULTIPLIER_SCALED =
            PROGRESS_STROKE_SCALE / (1 - PROGRESS_BOUNDS_SCALE) / ICON_VISIBLE_AREA_FACTOR

        /** Returns a FastBitmapDrawable with a pending icon delegate. */
        @JvmStatic
        @JvmOverloads
        fun ItemInfoWithIcon.newPendingIcon(
            context: Context,
            @DrawableCreationFlags creationFlags: Int = 0,
        ): FastBitmapDrawable {
            val originalState = newIcon(context, creationFlags).constantState
            val themedSeedColor = context.resources.getColor(R.color.materialColorInverseSurface)
            val themedProgressColor = context.resources.getColor(R.color.materialColorPrimary)
            val themedProgressColorDark = context.resources.getColor(R.color.materialColorSecondary)
            val newState =
                originalState.copy(
                    // Set a disabled icon color if the app is suspended or is pending download
                    isDisabled = isDisabled || isPendingDownload,
                    level = progressLevel,
                    delegateFactory =
                        PreloadIconFactory(
                            info = this,
                            isDarkTheme = Utilities.isDarkTheme(context),
                            parentFactory = originalState.delegateFactory,
                            themedSeedColor = themedSeedColor,
                            themedSeedColorDark = themedSeedColor,
                            themedProgressColor = themedProgressColor,
                            themedProgressColorDark = themedProgressColorDark,
                        ),
                )
            return newState.newDrawable()
        }

        @JvmStatic
        fun extractPreloadDelegate(icon: FastBitmapDrawable?): PreloadIconDelegate? =
            icon?.delegate as? PreloadIconDelegate

        @JvmStatic
        fun FastBitmapDrawable?.hasPendingAnimationCompleted(): Boolean =
            (this?.delegate as? PreloadIconDelegate)?.ranFinishAnimation ?: true
    }

    class PreloadIconFactory(
        private val info: ItemInfoWithIcon,
        private val isDarkTheme: Boolean,
        private val parentFactory: DelegateFactory,
        private val themedSeedColor: Int,
        private val themedSeedColorDark: Int,
        private val themedProgressColor: Int,
        private val themedProgressColorDark: Int,
    ) : DelegateFactory {
        override fun newDelegate(
            bitmapInfo: BitmapInfo,
            iconShape: IconShape,
            paint: Paint,
            host: FastBitmapDrawable,
        ): FastBitmapDrawableDelegate {
            return PreloadIconDelegate(
                info,
                isDarkTheme,
                iconShape,
                host,
                parentFactory.newDelegate(bitmapInfo, iconShape, paint, host),
                themedSeedColor,
                themedSeedColorDark,
                themedProgressColor,
                themedProgressColorDark,
            )
        }
    }
}
