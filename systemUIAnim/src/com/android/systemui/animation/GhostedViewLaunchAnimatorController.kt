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

package com.android.systemui.animation

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Insets
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.util.Log
import android.view.GhostView
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
import android.widget.FrameLayout
import com.android.internal.jank.InteractionJankMonitor
import java.lang.IllegalArgumentException
import java.util.LinkedList
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "GhostedViewLaunchAnimatorController"

/**
 * A base implementation of [ActivityLaunchAnimator.Controller] which creates a [ghost][GhostView]
 * of [ghostedView] as well as an expandable background view, which are drawn and animated instead
 * of the ghosted view.
 *
 * Important: [ghostedView] must be attached to a [ViewGroup] when calling this function and during
 * the animation. It must also implement [LaunchableView], otherwise an exception will be thrown
 * during this controller instantiation.
 *
 * Note: Avoid instantiating this directly and call [ActivityLaunchAnimator.Controller.fromView]
 * whenever possible instead.
 */
open class GhostedViewLaunchAnimatorController
@JvmOverloads
constructor(
    /** The view that will be ghosted and from which the background will be extracted. */
    private val ghostedView: View,

    /** The [InteractionJankMonitor.CujType] associated to this animation. */
    private val cujType: Int? = null,
    private var interactionJankMonitor: InteractionJankMonitor =
        InteractionJankMonitor.getInstance(),
) : ActivityLaunchAnimator.Controller {

    /** The container to which we will add the ghost view and expanding background. */
    override var launchContainer = ghostedView.rootView as ViewGroup
    private val launchContainerOverlay: ViewGroupOverlay
        get() = launchContainer.overlay
    private val launchContainerLocation = IntArray(2)

    /** The ghost view that is drawn and animated instead of the ghosted view. */
    private var ghostView: GhostView? = null
    private val initialGhostViewMatrixValues = FloatArray(9) { 0f }
    private val ghostViewMatrix = Matrix()

    /**
     * The expanding background view that will be added to [launchContainer] (below [ghostView]) and
     * animate.
     */
    private var backgroundView: FrameLayout? = null

    /**
     * The drawable wrapping the [ghostedView] background and used as background for
     * [backgroundView].
     */
    private var backgroundDrawable: WrappedDrawable? = null
    private val backgroundInsets by lazy { background?.opticalInsets ?: Insets.NONE }
    private var startBackgroundAlpha: Int = 0xFF

    private val ghostedViewLocation = IntArray(2)
    private val ghostedViewState = LaunchAnimator.State()

    /**
     * The background of the [ghostedView]. This background will be used to draw the background of
     * the background view that is expanding up to the final animation position.
     *
     * Note that during the animation, the alpha value value of this background will be set to 0,
     * then set back to its initial value at the end of the animation.
     */
    private val background: Drawable?

    init {
        // Make sure the View we launch from implements LaunchableView to avoid visibility issues.
        if (ghostedView !is LaunchableView) {
            throw IllegalArgumentException(
                "A GhostedViewLaunchAnimatorController was created from a View that does not " +
                    "implement LaunchableView. This can lead to subtle bugs where the visibility " +
                    "of the View we are launching from is not what we expected."
            )
        }

        /** Find the first view with a background in [view] and its children. */
        fun findBackground(view: View): Drawable? {
            if (view.background != null) {
                return view.background
            }

            // Perform a BFS to find the largest View with background.
            val views = LinkedList<View>().apply { add(view) }

            while (views.isNotEmpty()) {
                val v = views.removeFirst()
                if (v.background != null) {
                    return v.background
                }

                if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        views.add(v.getChildAt(i))
                    }
                }
            }

            return null
        }

        background = findBackground(ghostedView)
    }

    /**
     * Set the corner radius of [background]. The background is the one that was returned by
     * [getBackground].
     */
    protected open fun setBackgroundCornerRadius(
        background: Drawable,
        topCornerRadius: Float,
        bottomCornerRadius: Float
    ) {
        // By default, we rely on WrappedDrawable to set/restore the background radii before/after
        // each draw.
        backgroundDrawable?.setBackgroundRadius(topCornerRadius, bottomCornerRadius)
    }

    /** Return the current top corner radius of the background. */
    protected open fun getCurrentTopCornerRadius(): Float {
        val drawable = background ?: return 0f
        val gradient = findGradientDrawable(drawable) ?: return 0f

        // TODO(b/184121838): Support more than symmetric top & bottom radius.
        val radius = gradient.cornerRadii?.get(CORNER_RADIUS_TOP_INDEX) ?: gradient.cornerRadius
        return radius * ghostedView.scaleX
    }

    /** Return the current bottom corner radius of the background. */
    protected open fun getCurrentBottomCornerRadius(): Float {
        val drawable = background ?: return 0f
        val gradient = findGradientDrawable(drawable) ?: return 0f

        // TODO(b/184121838): Support more than symmetric top & bottom radius.
        val radius = gradient.cornerRadii?.get(CORNER_RADIUS_BOTTOM_INDEX) ?: gradient.cornerRadius
        return radius * ghostedView.scaleX
    }

    override fun createAnimatorState(): LaunchAnimator.State {
        val state =
            LaunchAnimator.State(
                topCornerRadius = getCurrentTopCornerRadius(),
                bottomCornerRadius = getCurrentBottomCornerRadius()
            )
        fillGhostedViewState(state)
        return state
    }

    fun fillGhostedViewState(state: LaunchAnimator.State) {
        // For the animation we are interested in the area that has a non transparent background,
        // so we have to take the optical insets into account.
        ghostedView.getLocationOnScreen(ghostedViewLocation)
        val insets = backgroundInsets
        state.top = ghostedViewLocation[1] + insets.top
        state.bottom =
            ghostedViewLocation[1] + (ghostedView.height * ghostedView.scaleY).roundToInt() -
                insets.bottom
        state.left = ghostedViewLocation[0] + insets.left
        state.right =
            ghostedViewLocation[0] + (ghostedView.width * ghostedView.scaleX).roundToInt() -
                insets.right
    }

    override fun onLaunchAnimationStart(isExpandingFullyAbove: Boolean) {
        if (ghostedView.parent !is ViewGroup) {
            // This should usually not happen, but let's make sure we don't crash if the view was
            // detached right before we started the animation.
            Log.w(TAG, "Skipping animation as ghostedView is not attached to a ViewGroup")
            return
        }

        backgroundView = FrameLayout(launchContainer.context)
        launchContainerOverlay.add(backgroundView)

        // We wrap the ghosted view background and use it to draw the expandable background. Its
        // alpha will be set to 0 as soon as we start drawing the expanding background.
        startBackgroundAlpha = background?.alpha ?: 0xFF
        backgroundDrawable = WrappedDrawable(background)
        backgroundView?.background = backgroundDrawable

        // Delay the calls to `ghostedView.setVisibility()` during the animation. This must be
        // called before `GhostView.addGhost()` is called because the latter will change the
        // *transition* visibility, which won't be blocked and will affect the normal View
        // visibility that is saved by `setShouldBlockVisibilityChanges()` for a later restoration.
        (ghostedView as? LaunchableView)?.setShouldBlockVisibilityChanges(true)

        // Create a ghost of the view that will be moving and fading out. This allows to fade out
        // the content before fading out the background.
        ghostView = GhostView.addGhost(ghostedView, launchContainer)

        val matrix = ghostView?.animationMatrix ?: Matrix.IDENTITY_MATRIX
        matrix.getValues(initialGhostViewMatrixValues)

        cujType?.let { interactionJankMonitor.begin(ghostedView, it) }
    }

    override fun onLaunchAnimationProgress(
        state: LaunchAnimator.State,
        progress: Float,
        linearProgress: Float
    ) {
        val ghostView = this.ghostView ?: return
        val backgroundView = this.backgroundView!!

        if (!state.visible) {
            if (ghostView.visibility == View.VISIBLE) {
                // Making the ghost view invisible will make the ghosted view visible, so order is
                // important here.
                ghostView.visibility = View.INVISIBLE

                // Make the ghosted view invisible again. We use the transition visibility like
                // GhostView does so that we don't mess up with the accessibility tree (see
                // b/204944038#comment17).
                ghostedView.setTransitionVisibility(View.INVISIBLE)
                backgroundView.visibility = View.INVISIBLE
            }
            return
        }

        // The ghost and backgrounds views were made invisible earlier. That can for instance happen
        // when animating a dialog into a view.
        if (ghostView.visibility == View.INVISIBLE) {
            ghostView.visibility = View.VISIBLE
            backgroundView.visibility = View.VISIBLE
        }

        fillGhostedViewState(ghostedViewState)
        val leftChange = state.left - ghostedViewState.left
        val rightChange = state.right - ghostedViewState.right
        val topChange = state.top - ghostedViewState.top
        val bottomChange = state.bottom - ghostedViewState.bottom

        val widthRatio = state.width.toFloat() / ghostedViewState.width
        val heightRatio = state.height.toFloat() / ghostedViewState.height
        val scale = min(widthRatio, heightRatio)

        if (ghostedView.parent is ViewGroup) {
            // Recalculate the matrix in case the ghosted view moved. We ensure that the ghosted
            // view is still attached to a ViewGroup, otherwise calculateMatrix will throw.
            GhostView.calculateMatrix(ghostedView, launchContainer, ghostViewMatrix)
        }

        launchContainer.getLocationOnScreen(launchContainerLocation)
        ghostViewMatrix.postScale(
            scale,
            scale,
            ghostedViewState.centerX - launchContainerLocation[0],
            ghostedViewState.centerY - launchContainerLocation[1]
        )
        ghostViewMatrix.postTranslate(
            (leftChange + rightChange) / 2f,
            (topChange + bottomChange) / 2f
        )
        ghostView.animationMatrix = ghostViewMatrix

        // We need to take into account the background insets for the background position.
        val insets = backgroundInsets
        val topWithInsets = state.top - insets.top
        val leftWithInsets = state.left - insets.left
        val rightWithInsets = state.right + insets.right
        val bottomWithInsets = state.bottom + insets.bottom

        backgroundView.top = topWithInsets - launchContainerLocation[1]
        backgroundView.bottom = bottomWithInsets - launchContainerLocation[1]
        backgroundView.left = leftWithInsets - launchContainerLocation[0]
        backgroundView.right = rightWithInsets - launchContainerLocation[0]

        val backgroundDrawable = backgroundDrawable!!
        backgroundDrawable.wrapped?.let {
            setBackgroundCornerRadius(it, state.topCornerRadius, state.bottomCornerRadius)
        }
    }

    override fun onLaunchAnimationEnd(isExpandingFullyAbove: Boolean) {
        if (ghostView == null) {
            // We didn't actually run the animation.
            return
        }

        cujType?.let { interactionJankMonitor.end(it) }

        backgroundDrawable?.wrapped?.alpha = startBackgroundAlpha

        GhostView.removeGhost(ghostedView)
        launchContainerOverlay.remove(backgroundView)

        if (ghostedView is LaunchableView) {
            // Restore the ghosted view visibility.
            ghostedView.setShouldBlockVisibilityChanges(false)
        } else {
            // Make the ghosted view visible. We ensure that the view is considered VISIBLE by
            // accessibility by first making it INVISIBLE then VISIBLE (see b/204944038#comment17
            // for more info).
            ghostedView.visibility = View.INVISIBLE
            ghostedView.visibility = View.VISIBLE
            ghostedView.invalidate()
        }
    }

    companion object {
        private const val CORNER_RADIUS_TOP_INDEX = 0
        private const val CORNER_RADIUS_BOTTOM_INDEX = 4

        /**
         * Return the first [GradientDrawable] found in [drawable], or null if none is found. If
         * [drawable] is a [LayerDrawable], this will return the first layer that is a
         * [GradientDrawable].
         */
        fun findGradientDrawable(drawable: Drawable): GradientDrawable? {
            if (drawable is GradientDrawable) {
                return drawable
            }

            if (drawable is InsetDrawable) {
                return drawable.drawable?.let { findGradientDrawable(it) }
            }

            if (drawable is LayerDrawable) {
                for (i in 0 until drawable.numberOfLayers) {
                    val maybeGradient = drawable.getDrawable(i)
                    if (maybeGradient is GradientDrawable) {
                        return maybeGradient
                    }
                }
            }

            if (drawable is StateListDrawable) {
                return findGradientDrawable(drawable.current)
            }

            return null
        }
    }

    private class WrappedDrawable(val wrapped: Drawable?) : Drawable() {
        private var currentAlpha = 0xFF
        private var previousBounds = Rect()

        private var cornerRadii = FloatArray(8) { -1f }
        private var previousCornerRadii = FloatArray(8)

        override fun draw(canvas: Canvas) {
            val wrapped = this.wrapped ?: return

            wrapped.copyBounds(previousBounds)

            wrapped.alpha = currentAlpha
            wrapped.bounds = bounds
            applyBackgroundRadii()

            wrapped.draw(canvas)

            // The background view (and therefore this drawable) is drawn before the ghost view, so
            // the ghosted view background alpha should always be 0 when it is drawn above the
            // background.
            wrapped.alpha = 0
            wrapped.bounds = previousBounds
            restoreBackgroundRadii()
        }

        override fun setAlpha(alpha: Int) {
            if (alpha != currentAlpha) {
                currentAlpha = alpha
                invalidateSelf()
            }
        }

        override fun getAlpha() = currentAlpha

        override fun getOpacity(): Int {
            val wrapped = this.wrapped ?: return PixelFormat.TRANSPARENT

            val previousAlpha = wrapped.alpha
            wrapped.alpha = currentAlpha
            val opacity = wrapped.opacity
            wrapped.alpha = previousAlpha
            return opacity
        }

        override fun setColorFilter(filter: ColorFilter?) {
            wrapped?.colorFilter = filter
        }

        fun setBackgroundRadius(topCornerRadius: Float, bottomCornerRadius: Float) {
            updateRadii(cornerRadii, topCornerRadius, bottomCornerRadius)
            invalidateSelf()
        }

        private fun updateRadii(
            radii: FloatArray,
            topCornerRadius: Float,
            bottomCornerRadius: Float
        ) {
            radii[0] = topCornerRadius
            radii[1] = topCornerRadius
            radii[2] = topCornerRadius
            radii[3] = topCornerRadius

            radii[4] = bottomCornerRadius
            radii[5] = bottomCornerRadius
            radii[6] = bottomCornerRadius
            radii[7] = bottomCornerRadius
        }

        private fun applyBackgroundRadii() {
            if (cornerRadii[0] < 0 || wrapped == null) {
                return
            }

            savePreviousBackgroundRadii(wrapped)
            applyBackgroundRadii(wrapped, cornerRadii)
        }

        private fun savePreviousBackgroundRadii(background: Drawable) {
            // TODO(b/184121838): This method assumes that all GradientDrawable in background will
            // have the same radius. Should we save/restore the radii for each layer instead?
            val gradient = findGradientDrawable(background) ?: return

            // TODO(b/184121838): GradientDrawable#getCornerRadii clones its radii array. Should we
            // try to avoid that?
            val radii = gradient.cornerRadii
            if (radii != null) {
                radii.copyInto(previousCornerRadii)
            } else {
                // Copy the cornerRadius into previousCornerRadii.
                val radius = gradient.cornerRadius
                updateRadii(previousCornerRadii, radius, radius)
            }
        }

        private fun applyBackgroundRadii(drawable: Drawable, radii: FloatArray) {
            if (drawable is GradientDrawable) {
                drawable.cornerRadii = radii
                return
            }

            if (drawable is InsetDrawable) {
                drawable.drawable?.let { applyBackgroundRadii(it, radii) }
                return
            }

            if (drawable !is LayerDrawable) {
                return
            }

            for (i in 0 until drawable.numberOfLayers) {
                (drawable.getDrawable(i) as? GradientDrawable)?.cornerRadii = radii
            }
        }

        private fun restoreBackgroundRadii() {
            if (cornerRadii[0] < 0 || wrapped == null) {
                return
            }

            applyBackgroundRadii(wrapped, previousCornerRadii)
        }
    }
}
