/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wm.shell.common.magnetictarget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringForce
import com.android.wm.shell.shared.animation.PhysicsAnimator
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Utility class for creating 'magnetized' objects that are attracted to one or more magnetic
 * targets. Magnetic targets attract objects that are dragged near them, and hold them there unless
 * they're moved away or released. Releasing objects inside a magnetic target typically performs an
 * action on the object.
 *
 * MagnetizedObject also supports flinging to targets, which will result in the object being pulled
 * into the target and released as if it was dragged into it.
 *
 * To use this class, either construct an instance with an object of arbitrary type, or use the
 * [MagnetizedObject.magnetizeView] shortcut method if you're magnetizing a view. Then, set
 * [magnetListener] to receive event callbacks. In your touch handler, pass all MotionEvents
 * that move this object to [maybeConsumeMotionEvent]. If that method returns true, consider the
 * event consumed by the MagnetizedObject and don't move the object unless it begins returning false
 * again.
 *
 * @param context Context, used to retrieve a Vibrator instance for vibration effects.
 * @param underlyingObject The actual object that we're magnetizing.
 * @param xProperty Property that sets the x value of the object's position.
 * @param yProperty Property that sets the y value of the object's position.
 */
abstract class MagnetizedObject<T : Any>(
    val context: Context,

    /** The actual object that is animated. */
    val underlyingObject: T,

    /** Property that gets/sets the object's X value. */
    val xProperty: FloatPropertyCompat<in T>,

    /** Property that gets/sets the object's Y value. */
    val yProperty: FloatPropertyCompat<in T>
) {

    /** Return the width of the object. */
    abstract fun getWidth(underlyingObject: T): Float

    /** Return the height of the object. */
    abstract fun getHeight(underlyingObject: T): Float

    /**
     * Fill the provided array with the location of the top-left of the object, relative to the
     * entire screen. Compare to [View.getLocationOnScreen].
     */
    abstract fun getLocationOnScreen(underlyingObject: T, loc: IntArray)

    /** Methods for listening to events involving a magnetized object.  */
    interface MagnetListener {

        /**
         * Called when touch events move within the magnetic field of a target, causing the
         * object to animate to the target and become 'stuck' there. The animation happens
         * automatically here - you should not move the object. You can, however, change its state
         * to indicate to the user that it's inside the target and releasing it will have an effect.
         *
         * [maybeConsumeMotionEvent] is now returning true and will continue to do so until a call
         * to [onUnstuckFromTarget] or [onReleasedInTarget].
         *
         * @param target The target that the object is now stuck to.
         * @param draggedObject The object that is stuck to the target.
         */
        fun onStuckToTarget(target: MagneticTarget, draggedObject: MagnetizedObject<*>)

        /**
         * Called when the object is no longer stuck to a target. This means that either touch
         * events moved outside of the magnetic field radius, or that a forceful fling out of the
         * target was detected.
         *
         * The object won't be automatically animated out of the target, since you're responsible
         * for moving the object again. You should move it (or animate it) using your own
         * movement/animation logic.
         *
         * Reverse any effects applied in [onStuckToTarget] here.
         *
         * If [wasFlungOut] is true, [maybeConsumeMotionEvent] returned true for the ACTION_UP event
         * that concluded the fling. If [wasFlungOut] is false, that means a drag gesture is ongoing
         * and [maybeConsumeMotionEvent] is now returning false.
         *
         * @param target The target that this object was just unstuck from.
         * @param draggedObject The object being unstuck from the target.
         * @param velX The X velocity of the touch gesture when it exited the magnetic field.
         * @param velY The Y velocity of the touch gesture when it exited the magnetic field.
         * @param wasFlungOut Whether the object was unstuck via a fling gesture. This means that
         * an ACTION_UP event was received, and that the gesture velocity was sufficient to conclude
         * that the user wants to un-stick the object despite no touch events occurring outside of
         * the magnetic field radius.
         */
        fun onUnstuckFromTarget(
            target: MagneticTarget,
            draggedObject: MagnetizedObject<*>,
            velX: Float,
            velY: Float,
            wasFlungOut: Boolean
        )

        /**
         * Called when the object is released inside a target, or flung towards it with enough
         * velocity to reach it.
         *
         * @param target The target that the object was released in.
         * @param draggedObject The object released in the target.
         */
        fun onReleasedInTarget(target: MagneticTarget, draggedObject: MagnetizedObject<*>)
    }

    private val animator: PhysicsAnimator<T> = PhysicsAnimator.getInstance(underlyingObject)
    private val objectLocationOnScreen = IntArray(2)

    /**
     * Targets that have been added to this object. These will all be considered when determining
     * magnetic fields and fling trajectories.
     */
    private val associatedTargets = ArrayList<MagneticTarget>()

    private val velocityTracker: VelocityTracker = VelocityTracker.obtain()
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val vibrationAttributes: VibrationAttributes = VibrationAttributes.createForUsage(
            VibrationAttributes.USAGE_TOUCH)

    private var touchDown = PointF()
    private var touchSlop = 0
    private var movedBeyondSlop = false

    /** Whether touch events are presently occurring within the magnetic field area of a target. */
    val objectStuckToTarget: Boolean
        get() = targetObjectIsStuckTo != null

    /** The target the object is stuck to, or null if the object is not stuck to any target. */
    private var targetObjectIsStuckTo: MagneticTarget? = null

    /**
     * Sets the listener to receive events. This must be set, or [maybeConsumeMotionEvent]
     * will always return false and no magnetic effects will occur.
     */
    lateinit var magnetListener: MagnetizedObject.MagnetListener

    /**
     * Optional update listener to provide to the PhysicsAnimator that is used to spring the object
     * into the target.
     */
    var physicsAnimatorUpdateListener: PhysicsAnimator.UpdateListener<T>? = null

    /**
     * Optional end listener to provide to the PhysicsAnimator that is used to spring the object
     * into the target.
     */
    var physicsAnimatorEndListener: PhysicsAnimator.EndListener<T>? = null

    /**
     * Method that is called when the object should be animated stuck to the target. The default
     * implementation uses the object's x and y properties to animate the object centered inside the
     * target. You can override this if you need custom animation.
     *
     * The method is invoked with the MagneticTarget that the object is sticking to, the X and Y
     * velocities of the gesture that brought the object into the magnetic radius, whether or not it
     * was flung, and a callback you must call after your animation completes.
     */
    var animateStuckToTarget: (MagneticTarget, Float, Float, Boolean, (() -> Unit)?) -> Unit =
            ::animateStuckToTargetInternal

    /**
     * Sets whether forcefully flinging the object vertically towards a target causes it to be
     * attracted to the target and then released immediately, despite never being dragged within the
     * magnetic field.
     */
    var flingToTargetEnabled = true

    /**
     * If fling to target is enabled, forcefully flinging the object towards a target will cause
     * it to be attracted to the target and then released immediately, despite never being dragged
     * within the magnetic field.
     *
     * This sets the width of the area considered 'near' enough a target to be considered a fling,
     * in terms of percent of the target view's width. For example, setting this to 3f means that
     * flings towards a 100px-wide target will be considered 'near' enough if they're towards the
     * 300px-wide area around the target.
     *
     * Flings whose trajectory intersects the area will be attracted and released - even if the
     * target view itself isn't intersected:
     *
     * |             |
     * |           0 |
     * |          /  |
     * |         /   |
     * |      X /    |
     * |.....###.....|
     *
     *
     * Flings towards the target whose trajectories do not intersect the area will be treated as
     * normal flings and the magnet will leave the object alone:
     *
     * |             |
     * |             |
     * |   0         |
     * |  /          |
     * | /    X      |
     * |.....###.....|
     *
     */
    var flingToTargetWidthPercent = 3f

    /**
     * Sets the minimum velocity (in pixels per second) required to fling an object to the target
     * without dragging it into the magnetic field.
     */
    var flingToTargetMinVelocity = 4000f

    /**
     * Sets the minimum velocity (in pixels per second) required to fling un-stuck an object stuck
     * to the target. If this velocity is reached, the object will be freed even if it wasn't moved
     * outside the magnetic field radius.
     */
    var flingUnstuckFromTargetMinVelocity = 4000f

    /**
     * Sets the maximum X velocity above which the object will not stick to the target. Even if the
     * object is dragged through the magnetic field, it will not stick to the target until the
     * horizontal velocity is below this value.
     */
    var stickToTargetMaxXVelocity = 2000f

    /**
     * Enable or disable haptic vibration effects when the object interacts with the magnetic field.
     *
     * If you're experiencing crashes when the object enters targets, ensure that you have the
     * android.permission.VIBRATE permission!
     */
    var hapticsEnabled = true

    /** Default spring configuration to use for animating the object into a target. */
    var springConfig = PhysicsAnimator.SpringConfig(
            SpringForce.STIFFNESS_MEDIUM, SpringForce.DAMPING_RATIO_NO_BOUNCY)

    /**
     * Spring configuration to use to spring the object into a target specifically when it's flung
     * towards (rather than dragged near) it.
     */
    var flungIntoTargetSpringConfig = springConfig

    /**
     * Adds the provided MagneticTarget to this object. The object will now be attracted to the
     * target if it strays within its magnetic field or is flung towards it.
     *
     * If this target (or its magnetic field) overlaps another target added to this object, the
     * prior target will take priority.
     */
    fun addTarget(target: MagneticTarget) {
        associatedTargets.add(target)
        target.updateLocationOnScreen()
    }

    /**
     * Shortcut that accepts a View and a magnetic field radius and adds it as a magnetic target.
     *
     * @return The MagneticTarget instance for the given View. This can be used to change the
     * target's magnetic field radius after it's been added. It can also be added to other
     * magnetized objects.
     */
    fun addTarget(target: View, magneticFieldRadiusPx: Int): MagneticTarget {
        return MagneticTarget(target, magneticFieldRadiusPx).also { addTarget(it) }
    }

    /**
     * Removes the given target from this object. The target will no longer attract the object.
     */
    fun removeTarget(target: MagneticTarget) {
        associatedTargets.remove(target)
    }

    /**
     * Removes all associated targets from this object.
     */
    fun clearAllTargets() {
        associatedTargets.clear()
    }

    /**
     * Provide this method with all motion events that move the magnetized object. If the
     * location of the motion events moves within the magnetic field of a target, or indicate a
     * fling-to-target gesture, this method will return true and you should not move the object
     * yourself until it returns false again.
     *
     * Note that even when this method returns true, you should continue to pass along new motion
     * events so that we know when the events move back outside the magnetic field area.
     *
     * This method will always return false if you haven't set a [magnetListener].
     */
    fun maybeConsumeMotionEvent(ev: MotionEvent): Boolean {
        // Short-circuit if we don't have a listener or any targets, since those are required.
        if (associatedTargets.size == 0) {
            return false
        }

        // When a gesture begins, recalculate target views' positions on the screen in case they
        // have changed. Also, clear state.
        if (ev.action == MotionEvent.ACTION_DOWN) {
            updateTargetViews()

            // Clear the velocity tracker and stuck target.
            velocityTracker.clear()
            targetObjectIsStuckTo = null

            // Set the touch down coordinates and reset movedBeyondSlop.
            touchDown.set(ev.rawX, ev.rawY)
            movedBeyondSlop = false
        }

        // Always pass events to the VelocityTracker.
        addMovement(ev)

        // If we haven't yet moved beyond the slop distance, check if we have.
        if (!movedBeyondSlop) {
            val dragDistance = hypot(ev.rawX - touchDown.x, ev.rawY - touchDown.y)
            if (dragDistance > touchSlop) {
                // If we're beyond the slop distance, save that and continue.
                movedBeyondSlop = true
            } else {
                // Otherwise, don't do anything yet.
                return false
            }
        }

        val targetObjectIsInMagneticFieldOf = associatedTargets.firstOrNull { target ->
            val distanceFromTargetCenter = hypot(
                    ev.rawX - target.centerOnDisplayX(),
                    ev.rawY - target.centerOnDisplayY())
            distanceFromTargetCenter < target.magneticFieldRadiusPx
        }

        // If we aren't currently stuck to a target, and we're in the magnetic field of a target,
        // we're newly stuck.
        val objectNewlyStuckToTarget =
                !objectStuckToTarget && targetObjectIsInMagneticFieldOf != null

        // If we are currently stuck to a target, we're in the magnetic field of a target, and that
        // target isn't the one we're currently stuck to, then touch events have moved into a
        // adjacent target's magnetic field.
        val objectMovedIntoDifferentTarget =
                objectStuckToTarget &&
                        targetObjectIsInMagneticFieldOf != null &&
                        targetObjectIsStuckTo != targetObjectIsInMagneticFieldOf

        if (objectNewlyStuckToTarget || objectMovedIntoDifferentTarget) {
            velocityTracker.computeCurrentVelocity(1000)
            val velX = velocityTracker.xVelocity
            val velY = velocityTracker.yVelocity

            // If the object is moving too quickly within the magnetic field, do not stick it. This
            // only applies to objects newly stuck to a target. If the object is moved into a new
            // target, it wasn't moving at all (since it was stuck to the previous one).
            if (objectNewlyStuckToTarget && abs(velX) > stickToTargetMaxXVelocity) {
                return false
            }

            // This touch event is newly within the magnetic field - let the listener know, and
            // animate sticking to the magnet.
            targetObjectIsStuckTo = targetObjectIsInMagneticFieldOf
            cancelAnimations()
            magnetListener.onStuckToTarget(targetObjectIsInMagneticFieldOf!!, this)
            animateStuckToTarget(targetObjectIsInMagneticFieldOf, velX, velY, false, null)

            vibrateIfEnabled(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else if (targetObjectIsInMagneticFieldOf == null && objectStuckToTarget) {
            velocityTracker.computeCurrentVelocity(1000)

            // This touch event is newly outside the magnetic field - let the listener know. It will
            // move the object out of the target using its own movement logic.
            cancelAnimations()
            magnetListener.onUnstuckFromTarget(
                    targetObjectIsStuckTo!!, this,
                    velocityTracker.xVelocity, velocityTracker.yVelocity,
                    wasFlungOut = false)
            targetObjectIsStuckTo = null

            vibrateIfEnabled(VibrationEffect.EFFECT_TICK)
        }

        // First, check for relevant gestures concluding with an ACTION_UP.
        if (ev.action == MotionEvent.ACTION_UP) {
            velocityTracker.computeCurrentVelocity(1000 /* units */)
            val velX = velocityTracker.xVelocity
            val velY = velocityTracker.yVelocity

            // Cancel the magnetic animation since we might still be springing into the magnetic
            // target, but we're about to fling away or release.
            cancelAnimations()

            if (objectStuckToTarget) {
                if (-velY > flingUnstuckFromTargetMinVelocity) {
                    // If the object is stuck, but it was forcefully flung away from the target in
                    // the upward direction, tell the listener so the object can be animated out of
                    // the target.
                    magnetListener.onUnstuckFromTarget(
                            targetObjectIsStuckTo!!, this,
                            velX, velY, wasFlungOut = true)
                } else {
                    // If the object is stuck and not flung away, it was released inside the target.
                    magnetListener.onReleasedInTarget(targetObjectIsStuckTo!!, this)
                    vibrateIfEnabled(VibrationEffect.EFFECT_HEAVY_CLICK)
                }

                // Either way, we're no longer stuck.
                targetObjectIsStuckTo = null
                return true
            }

            // The target we're flinging towards, or null if we're not flinging towards any target.
            val flungToTarget = associatedTargets.firstOrNull { target ->
                isForcefulFlingTowardsTarget(target, ev.rawX, ev.rawY, velX, velY)
            }

            if (flungToTarget != null) {
                // If this is a fling-to-target, animate the object to the magnet and then release
                // it.
                magnetListener.onStuckToTarget(flungToTarget, this)
                targetObjectIsStuckTo = flungToTarget

                animateStuckToTarget(flungToTarget, velX, velY, true) {
                    magnetListener.onReleasedInTarget(flungToTarget, this)
                    targetObjectIsStuckTo = null
                    vibrateIfEnabled(VibrationEffect.EFFECT_HEAVY_CLICK)
                }

                return true
            }

            // If it's not either of those things, we are not interested.
            return false
        }

        return objectStuckToTarget // Always consume touch events if the object is stuck.
    }

    /** Plays the given vibration effect if haptics are enabled. */
    @SuppressLint("MissingPermission")
    private fun vibrateIfEnabled(effectId: Int) {
        if (hapticsEnabled) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId), vibrationAttributes)
        }
    }

    /** Adds the movement to the velocity tracker using raw coordinates. */
    private fun addMovement(event: MotionEvent) {
        // Add movement to velocity tracker using raw screen X and Y coordinates instead
        // of window coordinates because the window frame may be moving at the same time.
        val deltaX = event.rawX - event.x
        val deltaY = event.rawY - event.y
        event.offsetLocation(deltaX, deltaY)
        velocityTracker.addMovement(event)
        event.offsetLocation(-deltaX, -deltaY)
    }

    /** Animates sticking the object to the provided target with the given start velocities.  */
    private fun animateStuckToTargetInternal(
        target: MagneticTarget,
        velX: Float,
        velY: Float,
        flung: Boolean,
        after: (() -> Unit)? = null
    ) {
        target.updateLocationOnScreen()
        getLocationOnScreen(underlyingObject, objectLocationOnScreen)

        // Calculate the difference between the target's center coordinates and the object's.
        // Animating the object's x/y properties by these values will center the object on top
        // of the magnetic target.
        val xDiff = target.centerOnScreen.x -
                getWidth(underlyingObject) / 2f - objectLocationOnScreen[0]
        val yDiff = target.centerOnScreen.y -
                getHeight(underlyingObject) / 2f - objectLocationOnScreen[1]

        val springConfig = if (flung) flungIntoTargetSpringConfig else springConfig

        cancelAnimations()

        // Animate to the center of the target.
        animator
                .spring(xProperty, xProperty.getValue(underlyingObject) + xDiff, velX,
                        springConfig)
                .spring(yProperty, yProperty.getValue(underlyingObject) + yDiff, velY,
                        springConfig)

        if (physicsAnimatorUpdateListener != null) {
            animator.addUpdateListener(physicsAnimatorUpdateListener!!)
        }

        if (physicsAnimatorEndListener != null) {
            animator.addEndListener(physicsAnimatorEndListener!!)
        }

        if (after != null) {
            animator.withEndActions(after)
        }

        animator.start()
    }

    /**
     * Whether or not the provided values match a 'fast fling' towards the provided target. If it
     * does, we consider it a fling-to-target gesture.
     */
    private fun isForcefulFlingTowardsTarget(
        target: MagneticTarget,
        rawX: Float,
        rawY: Float,
        velX: Float,
        velY: Float
    ): Boolean {
        if (!flingToTargetEnabled) {
            return false
        }

        // Whether velocity is sufficient, depending on whether we're flinging into a target at the
        // top or the bottom of the screen.
        val velocitySufficient =
                if (rawY < target.centerOnDisplayY()) velY > flingToTargetMinVelocity
                else velY < flingToTargetMinVelocity

        if (!velocitySufficient) {
            return false
        }

        // Whether the trajectory of the fling intersects the target area.
        var targetCenterXIntercept = rawX

        // Only do math if the X velocity is non-zero, otherwise X won't change.
        if (velX != 0f) {
            // Rise over run...
            val slope = velY / velX
            // ...y = mx + b, b = y / mx...
            val yIntercept = rawY - slope * rawX

            // ...calculate the x value when y = the target's y-coordinate.
            targetCenterXIntercept = (target.centerOnDisplayY() - yIntercept) / slope
        }

        // The width of the area we're looking for a fling towards.
        val targetAreaWidth = target.targetView.width * flingToTargetWidthPercent

        // Velocity was sufficient, so return true if the intercept is within the target area.
        return targetCenterXIntercept > target.centerOnDisplayX() - targetAreaWidth / 2 &&
                targetCenterXIntercept < target.centerOnDisplayX() + targetAreaWidth / 2
    }

    /** Cancel animations on this object's x/y properties. */
    internal fun cancelAnimations() {
        animator.cancel(xProperty, yProperty)
    }

    /** Updates the locations on screen of all of the [associatedTargets]. */
    internal fun updateTargetViews() {
        associatedTargets.forEach { it.updateLocationOnScreen() }

        // Update the touch slop, since the configuration may have changed.
        if (associatedTargets.size > 0) {
            touchSlop =
                    ViewConfiguration.get(associatedTargets[0].targetView.context).scaledTouchSlop
        }
    }

    /**
     * Represents a target view with a magnetic field radius and cached center-on-screen
     * coordinates.
     *
     * Instances of MagneticTarget are passed to a MagnetizedObject's [addTarget], and can then
     * attract the object if it's dragged near or flung towards it. MagneticTargets can be added to
     * multiple objects.
     */
    class MagneticTarget(
        val targetView: View,
        var magneticFieldRadiusPx: Int
    ) {
        val centerOnScreen = PointF()

        /**
         * Set screen vertical offset amount.
         *
         * Screen surface may be vertically shifted in some cases, for example when one-handed mode
         * is enabled. [MagneticTarget] and [MagnetizedObject] set their location in screen
         * coordinates (see [MagneticTarget.centerOnScreen] and
         * [MagnetizedObject.getLocationOnScreen] respectively).
         *
         * When a [MagnetizedObject] is dragged, the touch location is determined by
         * [MotionEvent.getRawX] and [MotionEvent.getRawY]. These work in display coordinates. When
         * screen is shifted due to one-handed mode, display coordinates and screen coordinates do
         * not match. To determine if a [MagnetizedObject] is dragged into a [MagneticTarget], view
         * location on screen is translated to display coordinates using this offset value.
         */
        var screenVerticalOffset: Int = 0

        private val tempLoc = IntArray(2)

        fun updateLocationOnScreen() {
            targetView.post {
                targetView.getLocationOnScreen(tempLoc)

                // Add half of the target size to get the center, and subtract translation since the
                // target could be animating in while we're doing this calculation.
                centerOnScreen.set(
                        tempLoc[0] + targetView.width / 2f - targetView.translationX,
                        tempLoc[1] + targetView.height / 2f - targetView.translationY)
            }
        }

        /**
         * Get target center coordinate on x-axis on display. [centerOnScreen] has to be up to date
         * by calling [updateLocationOnScreen] first.
         */
        fun centerOnDisplayX(): Float {
            return centerOnScreen.x
        }

        /**
         * Get target center coordinate on y-axis on display. [centerOnScreen] has to be up to date
         * by calling [updateLocationOnScreen] first. Use [screenVerticalOffset] to update the
         * screen offset compared to the display.
         */
        fun centerOnDisplayY(): Float {
            return centerOnScreen.y + screenVerticalOffset
        }
    }

    companion object {
        /**
         * Magnetizes the given view. Magnetized views are attracted to one or more magnetic
         * targets. Magnetic targets attract objects that are dragged near them, and hold them there
         * unless they're moved away or released. Releasing objects inside a magnetic target
         * typically performs an action on the object.
         *
         * Magnetized views can also be flung to targets, which will result in the view being pulled
         * into the target and released as if it was dragged into it.
         *
         * To use the returned MagnetizedObject<View> instance, first set [magnetListener] to
         * receive event callbacks. In your touch handler, pass all MotionEvents that move this view
         * to [maybeConsumeMotionEvent]. If that method returns true, consider the event consumed by
         * MagnetizedObject and don't move the view unless it begins returning false again.
         *
         * The view will be moved via translationX/Y properties, and its
         * width/height will be determined via getWidth()/getHeight(). If you are animating
         * something other than a view, or want to position your view using properties other than
         * translationX/Y, implement an instance of [MagnetizedObject].
         *
         * Note that the magnetic library can't re-order your view automatically. If the view
         * renders on top of the target views, it will obscure the target when it sticks to it.
         * You'll want to bring the view to the front in [MagnetListener.onStuckToTarget].
         */
        @JvmStatic
        fun <T : View> magnetizeView(view: T): MagnetizedObject<T> {
            return object : MagnetizedObject<T>(
                    view.context,
                    view,
                    DynamicAnimation.TRANSLATION_X,
                    DynamicAnimation.TRANSLATION_Y) {
                override fun getWidth(underlyingObject: T): Float {
                    return underlyingObject.width.toFloat()
                }

                override fun getHeight(underlyingObject: T): Float {
                    return underlyingObject.height.toFloat() }

                override fun getLocationOnScreen(underlyingObject: T, loc: IntArray) {
                    underlyingObject.getLocationOnScreen(loc)
                }
            }
        }
    }
}