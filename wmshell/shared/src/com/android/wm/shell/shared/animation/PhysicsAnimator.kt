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

package com.android.wm.shell.shared.animation

import android.util.ArrayMap
import android.util.Log
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

import com.android.wm.shell.shared.animation.PhysicsAnimator.Companion.getInstance
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Extension function for all objects which will return a PhysicsAnimator instance for that object.
 */
val <T : View> T.physicsAnimator: PhysicsAnimator<T> get() { return getInstance(this) }

private const val TAG = "PhysicsAnimator"

private val UNSET = -Float.MAX_VALUE

/**
 * [FlingAnimation] multiplies the friction set via [FlingAnimation.setFriction] by 4.2f, which is
 * where this number comes from. We use it in [PhysicsAnimator.flingThenSpring] to calculate the
 * minimum velocity for a fling to reach a certain value, given the fling's friction.
 */
private const val FLING_FRICTION_SCALAR_MULTIPLIER = 4.2f

typealias EndAction = () -> Unit

/** A map of Property -> AnimationUpdate, which is provided to update listeners on each frame. */
typealias UpdateMap<T> =
        ArrayMap<FloatPropertyCompat<in T>, PhysicsAnimator.AnimationUpdate>

/**
 * Map of the animators associated with a given object. This ensures that only one animator
 * per object exists.
 */
internal val animators = WeakHashMap<Any, PhysicsAnimator<*>>()

/**
 * Default spring configuration to use for animations where stiffness and/or damping ratio
 * were not provided, and a default spring was not set via [PhysicsAnimator.setDefaultSpringConfig].
 */
private val globalDefaultSpring = PhysicsAnimator.SpringConfig(
        SpringForce.STIFFNESS_MEDIUM,
        SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)

/**
 * Default fling configuration to use for animations where friction was not provided, and a default
 * fling config was not set via [PhysicsAnimator.setDefaultFlingConfig].
 */
private val globalDefaultFling = PhysicsAnimator.FlingConfig(
        friction = 1f, min = -Float.MAX_VALUE, max = Float.MAX_VALUE)

/** Whether to log helpful debug information about animations. */
private var verboseLogging = false

/**
 * Animator that uses physics-based animations to animate properties on views and objects. Physics
 * animations use real-world physical concepts, such as momentum and mass, to realistically simulate
 * motion. PhysicsAnimator is heavily inspired by [android.view.ViewPropertyAnimator], and
 * also uses the builder pattern to configure and start animations.
 *
 * The physics animations are backed by [DynamicAnimation].
 *
 * @param T The type of the object being animated.
 */
class PhysicsAnimator<T> private constructor (target: T) {
    /** Weak reference to the animation target. */
    val weakTarget = WeakReference(target)

    /** Data class for representing animation frame updates. */
    data class AnimationUpdate(val value: Float, val velocity: Float)

    /** [DynamicAnimation] instances for the given properties. */
    private val springAnimations = ArrayMap<FloatPropertyCompat<in T>, SpringAnimation>()
    private val flingAnimations = ArrayMap<FloatPropertyCompat<in T>, FlingAnimation>()

    /**
     * Spring and fling configurations for the properties to be animated on the target. We'll
     * configure and start the DynamicAnimations for these properties according to the provided
     * configurations.
     */
    private val springConfigs = ArrayMap<FloatPropertyCompat<in T>, SpringConfig>()
    private val flingConfigs = ArrayMap<FloatPropertyCompat<in T>, FlingConfig>()

    /**
     * Animation listeners for the animation. These will be notified when each property animation
     * updates or ends.
     */
    private val updateListeners = ArrayList<UpdateListener<T>>()
    private val endListeners = ArrayList<EndListener<T>>()

    /** End actions to run when all animations have completed.  */
    private val endActions = ArrayList<EndAction>()

    /** SpringConfig to use by default for properties whose springs were not provided. */
    private var defaultSpring: SpringConfig = globalDefaultSpring

    /** FlingConfig to use by default for properties whose fling configs were not provided. */
    private var defaultFling: FlingConfig = globalDefaultFling

    /**
     * Internal listeners that respond to DynamicAnimations updating and ending, and dispatch to
     * the listeners provided via [addUpdateListener] and [addEndListener]. This allows us to add
     * just one permanent update and end listener to the DynamicAnimations.
     */
    internal var internalListeners = ArrayList<InternalListener>()

    /**
     * Action to run when [start] is called. This can be changed by
     * [PhysicsAnimatorTestUtils.prepareForTest] to enable animators to run under test and provide
     * helpful test utilities.
     */
    internal var startAction: () -> Unit = ::startInternal

    /**
     * Action to run when [cancel] is called. This can be changed by
     * [PhysicsAnimatorTestUtils.prepareForTest] to cancel animations from the main thread, which
     * is required.
     */
    internal var cancelAction: (Set<FloatPropertyCompat<in T>>) -> Unit = ::cancelInternal

    /**
     * Springs a property to the given value, using the provided configuration settings.
     *
     * Springs are used when you know the exact value to which you want to animate. They can be
     * configured with a start velocity (typically used when the spring is initiated by a touch
     * event), but this velocity will be realistically attenuated as forces are applied to move the
     * property towards the end value.
     *
     * If you find yourself repeating the same stiffness and damping ratios many times, consider
     * storing a single [SpringConfig] instance and passing that in instead of individual values.
     *
     * @param property The property to spring to the given value. The property must be an instance
     * of FloatPropertyCompat&lt;? super T&gt;. For example, if this is a
     * PhysicsAnimator&lt;FrameLayout&gt;, you can use a FloatPropertyCompat&lt;FrameLayout&gt;, as
     * well as a FloatPropertyCompat&lt;ViewGroup&gt;, and so on.
     * @param toPosition The value to spring the given property to.
     * @param startVelocity The initial velocity to use for the animation.
     * @param stiffness The stiffness to use for the spring. Higher stiffness values result in
     * faster animations, while lower stiffness means a slower animation. Reasonable values for
     * low, medium, and high stiffness can be found as constants in [SpringForce].
     * @param dampingRatio The damping ratio (bounciness) to use for the spring. Higher values
     * result in a less 'springy' animation, while lower values allow the animation to bounce
     * back and forth for a longer time after reaching the final position. Reasonable values for
     * low, medium, and high damping can be found in [SpringForce].
     */
    fun spring(
        property: FloatPropertyCompat<in T>,
        toPosition: Float,
        startVelocity: Float = 0f,
        stiffness: Float = defaultSpring.stiffness,
        dampingRatio: Float = defaultSpring.dampingRatio
    ): PhysicsAnimator<T> {
        if (verboseLogging) {
            Log.d(TAG, "Springing ${getReadablePropertyName(property)} to $toPosition.")
        }

        springConfigs[property] =
                SpringConfig(stiffness, dampingRatio, startVelocity, toPosition)
        return this
    }

    /**
     * Springs a property to a given value using the provided start velocity and configuration
     * options.
     *
     * @see spring
     */
    fun spring(
        property: FloatPropertyCompat<in T>,
        toPosition: Float,
        startVelocity: Float,
        config: SpringConfig = defaultSpring
    ): PhysicsAnimator<T> {
        return spring(
                property, toPosition, startVelocity, config.stiffness, config.dampingRatio)
    }

    /**
     * Springs a property to a given value using the provided configuration options, and a start
     * velocity of 0f.
     *
     * @see spring
     */
    fun spring(
        property: FloatPropertyCompat<in T>,
        toPosition: Float,
        config: SpringConfig = defaultSpring
    ): PhysicsAnimator<T> {
        return spring(property, toPosition, 0f, config)
    }

    /**
     * Springs a property to a given value using the provided configuration options, and a start
     * velocity of 0f.
     *
     * @see spring
     */
    fun spring(
        property: FloatPropertyCompat<in T>,
        toPosition: Float
    ): PhysicsAnimator<T> {
        return spring(property, toPosition, 0f)
    }

    /**
     * Flings a property using the given start velocity, using a [FlingAnimation] configured using
     * the provided configuration settings.
     *
     * Flings are used when you have a start velocity, and want the property value to realistically
     * decrease as friction is applied until the velocity reaches zero. Flings do not have a
     * deterministic end value. If you are attempting to animate to a specific end value, use
     * [spring].
     *
     * If you find yourself repeating the same friction/min/max values, consider storing a single
     * [FlingConfig] and passing that in instead.
     *
     * @param property The property to fling using the given start velocity.
     * @param startVelocity The start velocity (in pixels per second) with which to start the fling.
     * @param friction Friction value applied to slow down the animation over time. Higher values
     * will more quickly slow the animation. Typical friction values range from 1f to 10f.
     * @param min The minimum value allowed for the animation. If this value is reached, the
     * animation will end abruptly.
     * @param max The maximum value allowed for the animation. If this value is reached, the
     * animation will end abruptly.
     */
    fun fling(
        property: FloatPropertyCompat<in T>,
        startVelocity: Float,
        friction: Float = defaultFling.friction,
        min: Float = defaultFling.min,
        max: Float = defaultFling.max
    ): PhysicsAnimator<T> {
        if (verboseLogging) {
            Log.d(TAG, "Flinging ${getReadablePropertyName(property)} " +
                    "with velocity $startVelocity.")
        }

        flingConfigs[property] = FlingConfig(friction, min, max, startVelocity)
        return this
    }

    /**
     * Flings a property using the given start velocity, using a [FlingAnimation] configured using
     * the provided configuration settings.
     *
     * @see fling
     */
    fun fling(
        property: FloatPropertyCompat<in T>,
        startVelocity: Float,
        config: FlingConfig = defaultFling
    ): PhysicsAnimator<T> {
        return fling(property, startVelocity, config.friction, config.min, config.max)
    }

    /**
     * Flings a property using the given start velocity. If the fling animation reaches the min/max
     * bounds (from the [flingConfig]) with velocity remaining, it'll overshoot it and spring back.
     *
     * If the object is already out of the fling bounds, it will immediately spring back within
     * bounds.
     *
     * This is useful for animating objects that are bounded by constraints such as screen edges,
     * since otherwise the fling animation would end abruptly upon reaching the min/max bounds.
     *
     * @param property The property to animate.
     * @param startVelocity The velocity, in pixels/second, with which to start the fling. If the
     * object is already outside the fling bounds, this velocity will be used as the start velocity
     * of the spring that will spring it back within bounds.
     * @param flingMustReachMinOrMax If true, the fling animation is guaranteed to reach either its
     * minimum bound (if [startVelocity] is negative) or maximum bound (if it's positive). The
     * animator will use startVelocity if it's sufficient, or add more velocity if necessary. This
     * is useful when fling's deceleration-based physics are preferable to the acceleration-based
     * forces used by springs - typically, when you're allowing the user to move an object somewhere
     * on the screen, but it needs to be along an edge.
     * @param flingConfig The configuration to use for the fling portion of the animation.
     * @param springConfig The configuration to use for the spring portion of the animation.
     */
    @JvmOverloads
    fun flingThenSpring(
        property: FloatPropertyCompat<in T>,
        startVelocity: Float,
        flingConfig: FlingConfig,
        springConfig: SpringConfig,
        flingMustReachMinOrMax: Boolean = false
    ): PhysicsAnimator<T> {
        val target = weakTarget.get()
        if (target == null) {
            Log.w(TAG, "Trying to animate a GC-ed target.")
            return this
        }
        val flingConfigCopy = flingConfig.copy()
        val springConfigCopy = springConfig.copy()
        val toAtLeast = if (startVelocity < 0) flingConfig.min else flingConfig.max

        if (flingMustReachMinOrMax && isValidValue(toAtLeast)) {
            val currentValue = property.getValue(target)
            val flingTravelDistance =
                    startVelocity / (flingConfig.friction * FLING_FRICTION_SCALAR_MULTIPLIER)
            val projectedFlingEndValue = currentValue + flingTravelDistance
            val midpoint = (flingConfig.min + flingConfig.max) / 2

            // If fling velocity is too low to push the target past the midpoint between min and
            // max, then spring back towards the nearest edge, starting with the current velocity.
            if ((startVelocity < 0 && projectedFlingEndValue > midpoint) ||
                    (startVelocity > 0 && projectedFlingEndValue < midpoint)) {
                val toPosition =
                        if (projectedFlingEndValue < midpoint) flingConfig.min else flingConfig.max
                if (isValidValue(toPosition)) {
                    return spring(property, toPosition, startVelocity, springConfig)
                }
            }

            // Projected fling end value is past the midpoint, so fling forward.
            val distanceToDestination = toAtLeast - property.getValue(target)

            // The minimum velocity required for the fling to end up at the given destination,
            // taking the provided fling friction value.
            val velocityToReachDestination = distanceToDestination *
                    (flingConfig.friction * FLING_FRICTION_SCALAR_MULTIPLIER)

            // If there's distance to cover, and the provided velocity is moving in the correct
            // direction, ensure that the velocity is high enough to reach the destination.
            // Otherwise, just use startVelocity - this means that the fling is at or out of bounds.
            // The fling will immediately end and a spring will bring the object back into bounds
            // with this startVelocity.
            flingConfigCopy.startVelocity = when {
                distanceToDestination > 0f && startVelocity >= 0f ->
                    max(velocityToReachDestination, startVelocity)
                distanceToDestination < 0f && startVelocity <= 0f ->
                    min(velocityToReachDestination, startVelocity)
                else -> startVelocity
            }

            springConfigCopy.finalPosition = toAtLeast
        } else {
            flingConfigCopy.startVelocity = startVelocity
        }

        flingConfigs[property] = flingConfigCopy
        springConfigs[property] = springConfigCopy
        return this
    }

    private fun isValidValue(value: Float) = value < Float.MAX_VALUE && value > -Float.MAX_VALUE

    /**
     * Adds a listener that will be called whenever any property on the animated object is updated.
     * This will be called on every animation frame, with the current value of the animated object
     * and the new property values.
     */
    fun addUpdateListener(listener: UpdateListener<T>): PhysicsAnimator<T> {
        updateListeners.add(listener)
        return this
    }

    /**
     * Adds a listener that will be called when a property stops animating. This is useful if
     * you care about a specific property ending, or want to use the end value/end velocity from a
     * particular property's animation. If you just want to run an action when all property
     * animations have ended, use [withEndActions].
     */
    fun addEndListener(listener: EndListener<T>): PhysicsAnimator<T> {
        endListeners.add(listener)
        return this
    }

    /**
     * Adds end actions that will be run sequentially when animations for every property involved in
     * this specific animation have ended (unless they were explicitly canceled). For example, if
     * you call:
     *
     * animator
     *   .spring(TRANSLATION_X, ...)
     *   .spring(TRANSLATION_Y, ...)
     *   .withEndAction(action)
     *   .start()
     *
     * 'action' will be run when both TRANSLATION_X and TRANSLATION_Y end.
     *
     * Other properties may still be animating, if those animations were not started in the same
     * call. For example:
     *
     * animator
     *   .spring(ALPHA, ...)
     *   .start()
     *
     * animator
     *   .spring(TRANSLATION_X, ...)
     *   .spring(TRANSLATION_Y, ...)
     *   .withEndAction(action)
     *   .start()
     *
     * 'action' will still be run as soon as TRANSLATION_X and TRANSLATION_Y end, even if ALPHA is
     * still animating.
     *
     * If you want to run actions as soon as a subset of property animations have ended, you want
     * access to the animation's end value/velocity, or you want to run these actions even if the
     * animation is explicitly canceled, use [addEndListener]. End listeners have an allEnded param,
     * which indicates that all relevant animations have ended.
     */
    fun withEndActions(vararg endActions: EndAction?): PhysicsAnimator<T> {
        this.endActions.addAll(endActions.filterNotNull())
        return this
    }

    /**
     * Helper overload so that callers from Java can use Runnables or method references as end
     * actions without having to explicitly return Unit.
     */
    fun withEndActions(vararg endActions: Runnable?): PhysicsAnimator<T> {
        this.endActions.addAll(endActions.filterNotNull().map { it::run })
        return this
    }

    fun setDefaultSpringConfig(defaultSpring: SpringConfig) {
        this.defaultSpring = defaultSpring
    }

    fun setDefaultFlingConfig(defaultFling: FlingConfig) {
        this.defaultFling = defaultFling
    }

    /** Starts the animations! */
    fun start() {
        startAction()
    }

    /**
     * Starts the animations for real! This is typically called immediately by [start] unless this
     * animator is under test.
     */
    internal fun startInternal() {
        val target = weakTarget.get()
        if (target == null) {
            Log.w(TAG, "Trying to animate a GC-ed object.")
            return
        }

        // Functions that will actually start the animations. These are run after we build and add
        // the InternalListener, since some animations might update/end immediately and we don't
        // want to miss those updates.
        val animationStartActions = ArrayList<() -> Unit>()

        for (animatedProperty in getAnimatedProperties()) {
            val flingConfig = flingConfigs[animatedProperty]
            val springConfig = springConfigs[animatedProperty]

            // The property's current value on the object.
            val currentValue = animatedProperty.getValue(target)

            // Start by checking for a fling configuration. If one is present, we're either flinging
            // or flinging-then-springing. Either way, we'll want to start the fling first.
            if (flingConfig != null) {
                animationStartActions.add {
                    // When the animation is starting, adjust the min/max bounds to include the
                    // current value of the property, if necessary. This is required to allow a
                    // fling to bring an out-of-bounds object back into bounds. For example, if an
                    // object was dragged halfway off the left side of the screen, but then flung to
                    // the right, we don't want the animation to end instantly just because the
                    // object started out of bounds. If the fling is in the direction that would
                    // take it farther out of bounds, it will end instantly as expected.
                    flingConfig.apply {
                        min = min(currentValue, this.min)
                        max = max(currentValue, this.max)
                    }

                    // Flings can't be updated to a new position while maintaining velocity, because
                    // we're using the explicitly provided start velocity. Cancel any flings (or
                    // springs) on this property before flinging.
                    cancel(animatedProperty)

                    // Apply the configuration and start the animation.
                    getFlingAnimation(animatedProperty, target)
                        .also { flingConfig.applyToAnimation(it) }.start()
                }
            }

            // Check for a spring configuration. If one is present, we're either springing, or
            // flinging-then-springing.
            if (springConfig != null) {
                // If there is no corresponding fling config, we're only springing.
                if (flingConfig == null) {
                    // Apply the configuration and start the animation.
                    val springAnim = getSpringAnimation(animatedProperty, target)

                    // Apply the configuration and start the animation.
                    springConfig.applyToAnimation(springAnim)
                    animationStartActions.add(springAnim::start)
                } else {
                    // If there's a corresponding fling config, we're flinging-then-springing. Save
                    // the fling's original bounds so we can spring to them when the fling ends.
                    val flingMin = flingConfig.min
                    val flingMax = flingConfig.max

                    // Add an end listener that will start the spring when the fling ends.
                    endListeners.add(0, object : EndListener<T> {
                        override fun onAnimationEnd(
                            target: T,
                            property: FloatPropertyCompat<in T>,
                            wasFling: Boolean,
                            canceled: Boolean,
                            finalValue: Float,
                            finalVelocity: Float,
                            allRelevantPropertyAnimsEnded: Boolean
                        ) {
                            // If this isn't the relevant property, it wasn't a fling, or the fling
                            // was explicitly cancelled, don't spring.
                            if (property != animatedProperty || !wasFling || canceled) {
                                return
                            }

                            val endedWithVelocity = abs(finalVelocity) > 0

                            // If the object was out of bounds when the fling animation started, it
                            // will immediately end. In that case, we'll spring it back in bounds.
                            val endedOutOfBounds = finalValue !in flingMin..flingMax

                            // If the fling ended either out of bounds or with remaining velocity,
                            // it's time to spring.
                            if (endedWithVelocity || endedOutOfBounds) {
                                springConfig.startVelocity = finalVelocity

                                // If the spring's final position isn't set, this is a
                                // flingThenSpring where flingMustReachMinOrMax was false. We'll
                                // need to set the spring's final position here.
                                if (springConfig.finalPosition == UNSET) {
                                    if (endedWithVelocity) {
                                        // If the fling ended with negative velocity, that means it
                                        // hit the min bound, so spring to that bound (and vice
                                        // versa).
                                        springConfig.finalPosition =
                                                if (finalVelocity < 0) flingMin else flingMax
                                    } else if (endedOutOfBounds) {
                                        // If the fling ended out of bounds, spring it to the
                                        // nearest bound.
                                        springConfig.finalPosition =
                                                if (finalValue < flingMin) flingMin else flingMax
                                    }
                                }

                                // Apply the configuration and start the spring animation.
                                getSpringAnimation(animatedProperty, target)
                                    .also { springConfig.applyToAnimation(it) }.start()
                            }
                        }
                    })
                }
            }
        }

        // Add an internal listener that will dispatch animation events to the provided listeners.
        internalListeners.add(InternalListener(
                target,
                getAnimatedProperties(),
                ArrayList(updateListeners),
                ArrayList(endListeners),
                ArrayList(endActions)))

        // Actually start the DynamicAnimations. This is delayed until after the InternalListener is
        // constructed and added so that we don't miss the end listener firing for any animations
        // that immediately end.
        animationStartActions.forEach { it.invoke() }

        clearAnimator()
    }

    /** Clear the animator's builder variables. */
    private fun clearAnimator() {
        springConfigs.clear()
        flingConfigs.clear()

        updateListeners.clear()
        endListeners.clear()
        endActions.clear()
    }

    /** Retrieves a spring animation for the given property, building one if needed. */
    private fun getSpringAnimation(
        property: FloatPropertyCompat<in T>,
        target: T
    ): SpringAnimation {
        return springAnimations.getOrPut(
                property,
                { configureDynamicAnimation(SpringAnimation(target, property), property)
                        as SpringAnimation })
    }

    /** Retrieves a fling animation for the given property, building one if needed. */
    private fun getFlingAnimation(property: FloatPropertyCompat<in T>, target: T): FlingAnimation {
        return flingAnimations.getOrPut(
                property,
                { configureDynamicAnimation(FlingAnimation(target, property), property)
                        as FlingAnimation })
    }

    /**
     * Adds update and end listeners to the DynamicAnimation which will dispatch to the internal
     * listeners.
     */
    private fun configureDynamicAnimation(
        anim: DynamicAnimation<*>,
        property: FloatPropertyCompat<in T>
    ): DynamicAnimation<*> {
        anim.addUpdateListener { _, value, velocity ->
            for (i in 0 until internalListeners.size) {
                internalListeners[i].onInternalAnimationUpdate(property, value, velocity)
            }
        }
        anim.addEndListener { _, canceled, value, velocity ->
            internalListeners.removeAll {
                it.onInternalAnimationEnd(
                        property, canceled, value, velocity, anim is FlingAnimation)
            }
            if (springAnimations[property] == anim) {
                springAnimations.remove(property)
            }
            if (flingAnimations[property] == anim) {
                flingAnimations.remove(property)
            }
        }
        return anim
    }

    /**
     * Internal listener class that receives updates from DynamicAnimation listeners, and dispatches
     * them to the appropriate update/end listeners. This class is also aware of which properties
     * were being animated when the end listeners were passed in, so that we can provide the
     * appropriate value for allEnded to [EndListener.onAnimationEnd].
     */
    internal inner class InternalListener constructor(
        private val target: T,
        private var properties: Set<FloatPropertyCompat<in T>>,
        private var updateListeners: List<UpdateListener<T>>,
        private var endListeners: List<EndListener<T>>,
        private var endActions: List<EndAction>
    ) {

        /** The number of properties whose animations haven't ended. */
        private var numPropertiesAnimating = properties.size

        /**
         * Update values that haven't yet been dispatched because not all property animations have
         * updated yet.
         */
        private val undispatchedUpdates =
                ArrayMap<FloatPropertyCompat<in T>, AnimationUpdate>()

        /** Called when a DynamicAnimation updates.  */
        internal fun onInternalAnimationUpdate(
            property: FloatPropertyCompat<in T>,
            value: Float,
            velocity: Float
        ) {
            // If this property animation isn't relevant to this listener, ignore it.
            if (!properties.contains(property)) {
                return
            }

            undispatchedUpdates[property] = AnimationUpdate(value, velocity)
            maybeDispatchUpdates()
        }

        /**
         * Called when a DynamicAnimation ends.
         *
         * @return True if this listener should be removed from the list of internal listeners, so
         * it no longer receives updates from DynamicAnimations.
         */
        internal fun onInternalAnimationEnd(
            property: FloatPropertyCompat<in T>,
            canceled: Boolean,
            finalValue: Float,
            finalVelocity: Float,
            isFling: Boolean
        ): Boolean {
            // If this property animation isn't relevant to this listener, ignore it.
            if (!properties.contains(property)) {
                return false
            }

            // Dispatch updates if we have one for each property.
            numPropertiesAnimating--
            maybeDispatchUpdates()

            // If we didn't have an update for each property, dispatch the update for the ending
            // property. This guarantees that an update isn't sent for this property *after* we call
            // onAnimationEnd for that property.
            if (undispatchedUpdates.contains(property)) {
                updateListeners.forEach { updateListener ->
                    updateListener.onAnimationUpdateForProperty(
                            target,
                            UpdateMap<T>().also { it[property] = undispatchedUpdates[property] })
                }

                undispatchedUpdates.remove(property)
            }

            val allEnded = !arePropertiesAnimating(properties)
            endListeners.forEach {
                it.onAnimationEnd(
                        target, property, isFling, canceled, finalValue, finalVelocity,
                        allEnded)

                // Check that the end listener didn't restart this property's animation.
                if (isPropertyAnimating(property)) {
                    return false
                }
            }

            // If all of the animations that this listener cares about have ended, run the end
            // actions unless the animation was canceled.
            if (allEnded && !canceled) {
                endActions.forEach { it() }
            }

            return allEnded
        }

        /**
         * Dispatch undispatched values if we've received an update from each of the animating
         * properties.
         */
        private fun maybeDispatchUpdates() {
            if (undispatchedUpdates.size >= numPropertiesAnimating &&
                    undispatchedUpdates.size > 0) {
                updateListeners.forEach {
                    it.onAnimationUpdateForProperty(target, ArrayMap(undispatchedUpdates))
                }

                undispatchedUpdates.clear()
            }
        }
    }

    /** Return true if any animations are running on the object.  */
    fun isRunning(): Boolean {
        return arePropertiesAnimating(springAnimations.keys.union(flingAnimations.keys))
    }

    /** Returns whether the given property is animating.  */
    fun isPropertyAnimating(property: FloatPropertyCompat<in T>): Boolean {
        return springAnimations[property]?.isRunning ?: false ||
                flingAnimations[property]?.isRunning ?: false
    }

    /** Returns whether any of the given properties are animating.  */
    fun arePropertiesAnimating(properties: Set<FloatPropertyCompat<in T>>): Boolean {
        return properties.any { isPropertyAnimating(it) }
    }

    /** Return the set of properties that will begin animating upon calling [start]. */
    internal fun getAnimatedProperties(): Set<FloatPropertyCompat<in T>> {
        return springConfigs.keys.union(flingConfigs.keys)
    }

    /**
     * Cancels the given properties. This is typically called immediately by [cancel], unless this
     * animator is under test.
     */
    internal fun cancelInternal(properties: Set<FloatPropertyCompat<in T>>) {
        for (property in properties) {
            flingAnimations[property]?.cancel()
            springAnimations[property]?.cancel()
        }
    }

    /** Cancels all in progress animations on all properties. */
    fun cancel() {
        if (flingAnimations.size > 0) {
            cancelAction(flingAnimations.keys)
        }
        if (springAnimations.size > 0) {
            cancelAction(springAnimations.keys)
        }
    }

    /** Cancels in progress animations on the provided properties only. */
    fun cancel(vararg properties: FloatPropertyCompat<in T>) {
        cancelAction(properties.toSet())
    }

    /**
     * Container object for spring animation configuration settings. This allows you to store
     * default stiffness and damping ratio values in a single configuration object, which you can
     * pass to [spring].
     */
    data class SpringConfig internal constructor(
        var stiffness: Float,
        internal var dampingRatio: Float,
        internal var startVelocity: Float = 0f,
        internal var finalPosition: Float = UNSET
    ) {

        constructor() :
                this(globalDefaultSpring.stiffness, globalDefaultSpring.dampingRatio)

        constructor(stiffness: Float, dampingRatio: Float) :
                this(stiffness = stiffness, dampingRatio = dampingRatio, startVelocity = 0f)

        /** Apply these configuration settings to the given SpringAnimation. */
        internal fun applyToAnimation(anim: SpringAnimation) {
            val springForce = anim.spring ?: SpringForce()
            anim.spring = springForce.apply {
                stiffness = this@SpringConfig.stiffness
                dampingRatio = this@SpringConfig.dampingRatio
                finalPosition = this@SpringConfig.finalPosition
            }

            if (startVelocity != 0f) anim.setStartVelocity(startVelocity)
        }
    }

    /**
     * Container object for fling animation configuration settings. This allows you to store default
     * friction values (as well as optional min/max values) in a single configuration object, which
     * you can pass to [fling] and related methods.
     */
    data class FlingConfig internal constructor(
        internal var friction: Float,
        var min: Float,
        var max: Float,
        internal var startVelocity: Float
    ) {

        constructor() : this(globalDefaultFling.friction)

        constructor(friction: Float) :
                this(friction, globalDefaultFling.min, globalDefaultFling.max)

        constructor(friction: Float, min: Float, max: Float) :
                this(friction, min, max, startVelocity = 0f)

        /** Apply these configuration settings to the given FlingAnimation. */
        internal fun applyToAnimation(anim: FlingAnimation) {
            anim.apply {
                friction = this@FlingConfig.friction
                setMinValue(min)
                setMaxValue(max)
                setStartVelocity(startVelocity)
            }
        }
    }

    /**
     * Listener for receiving values from in progress animations. Used with
     * [PhysicsAnimator.addUpdateListener].
     *
     * @param <T> The type of the object being animated.
    </T> */
    fun interface UpdateListener<T> {

        /**
         * Called on each animation frame with the target object, and a map of FloatPropertyCompat
         * -> AnimationUpdate, containing the latest value and velocity for that property. When
         * multiple properties are animating together, the map will typically contain one entry for
         * each property. However, you should never assume that this is the case - when a property
         * animation ends earlier than the others, you'll receive an UpdateMap containing only that
         * property's final update. Subsequently, you'll only receive updates for the properties
         * that are still animating.
         *
         * Always check that the map contains an update for the property you're interested in before
         * accessing it.
         *
         * @param target The animated object itself.
         * @param values Map of property to AnimationUpdate, which contains that property
         * animation's latest value and velocity. You should never assume that a particular property
         * is present in this map.
         */
        fun onAnimationUpdateForProperty(
            target: T,
            values: UpdateMap<T>
        )
    }

    /**
     * Listener for receiving callbacks when animations end.
     *
     * @param <T> The type of the object being animated.
    </T> */
    fun interface EndListener<T> {

        /**
         * Called with the final animation values as each property animation ends. This can be used
         * to respond to specific property animations concluding (such as hiding a view when ALPHA
         * ends, even if the corresponding TRANSLATION animations have not ended).
         *
         * If you just want to run an action when all of the property animations have ended, you can
         * use [PhysicsAnimator.withEndActions].
         *
         * @param target The animated object itself.
         * @param property The property whose animation has just ended.
         * @param wasFling Whether this property ended after a fling animation (as opposed to a
         * spring animation). If this property was animated via [flingThenSpring], this will be true
         * if the fling animation did not reach the min/max bounds, decelerating to a stop
         * naturally. It will be false if it hit the bounds and was sprung back.
         * @param canceled Whether the animation was explicitly canceled before it naturally ended.
         * @param finalValue The final value of the animated property.
         * @param finalVelocity The final velocity (in pixels per second) of the ended animation.
         * This is typically zero, unless this was a fling animation which ended abruptly due to
         * reaching its configured min/max values.
         * @param allRelevantPropertyAnimsEnded Whether all properties relevant to this end listener
         * have ended. Relevant properties are those which were animated alongside the
         * [addEndListener] call where this animator was passed in. For example:
         *
         * animator
         *    .spring(TRANSLATION_X, 100f)
         *    .spring(TRANSLATION_Y, 200f)
         *    .withEndListener(firstEndListener)
         *    .start()
         *
         * firstEndListener will be called first for TRANSLATION_X, with allEnded = false,
         * because TRANSLATION_Y is still running. When TRANSLATION_Y ends, it'll be called with
         * allEnded = true.
         *
         * If a subsequent call to start() is made with other properties, those properties are not
         * considered relevant and allEnded will still equal true when only TRANSLATION_X and
         * TRANSLATION_Y end. For example, if immediately after the prior example, while
         * TRANSLATION_X and TRANSLATION_Y are still animating, we called:
         *
         * animator.
         *    .spring(SCALE_X, 2f, stiffness = 10f) // That will take awhile...
         *    .withEndListener(secondEndListener)
         *    .start()
         *
         * firstEndListener will still be called with allEnded = true when TRANSLATION_X/Y end, even
         * though SCALE_X is still animating. Similarly, secondEndListener will be called with
         * allEnded = true as soon as SCALE_X ends, even if the translation animations are still
         * running.
         */
        fun onAnimationEnd(
            target: T,
            property: FloatPropertyCompat<in T>,
            wasFling: Boolean,
            canceled: Boolean,
            finalValue: Float,
            finalVelocity: Float,
            allRelevantPropertyAnimsEnded: Boolean
        )
    }

    companion object {

        /**
         * Callback to notify that a new animator was created. Used in [PhysicsAnimatorTestUtils]
         * to be able to keep track of animators and wait for them to finish.
         */
        internal var onAnimatorCreated: (PhysicsAnimator<*>, Any) -> Unit = { _, _ -> }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getInstance(target: T): PhysicsAnimator<T> {
            if (!animators.containsKey(target)) {
                val animator = PhysicsAnimator(target)
                onAnimatorCreated(animator, target)
                animators[target] = animator
            }

            return animators[target] as PhysicsAnimator<T>
        }

        /**
         * Set whether all physics animators should log a lot of information about animations.
         * Useful for debugging!
         */
        @JvmStatic
        fun setVerboseLogging(debug: Boolean) {
            verboseLogging = debug
        }

        /**
         * Estimates the end value of a fling that starts at the given value using the provided
         * start velocity and fling configuration.
         *
         * This is only an estimate. Fling animations use a timing-based physics simulation that is
         * non-deterministic, so this exact value may not be reached.
         */
        @JvmStatic
        fun estimateFlingEndValue(
            startValue: Float,
            startVelocity: Float,
            flingConfig: FlingConfig
        ): Float {
            val distance = startVelocity / (flingConfig.friction * FLING_FRICTION_SCALAR_MULTIPLIER)
            return Math.min(flingConfig.max, Math.max(flingConfig.min, startValue + distance))
        }

        @JvmStatic
        fun getReadablePropertyName(property: FloatPropertyCompat<*>): String {
            return when (property) {
                DynamicAnimation.TRANSLATION_X -> "translationX"
                DynamicAnimation.TRANSLATION_Y -> "translationY"
                DynamicAnimation.TRANSLATION_Z -> "translationZ"
                DynamicAnimation.SCALE_X -> "scaleX"
                DynamicAnimation.SCALE_Y -> "scaleY"
                DynamicAnimation.ROTATION -> "rotation"
                DynamicAnimation.ROTATION_X -> "rotationX"
                DynamicAnimation.ROTATION_Y -> "rotationY"
                DynamicAnimation.SCROLL_X -> "scrollX"
                DynamicAnimation.SCROLL_Y -> "scrollY"
                DynamicAnimation.ALPHA -> "alpha"
                else -> "Custom FloatPropertyCompat instance"
            }
        }
    }
}
