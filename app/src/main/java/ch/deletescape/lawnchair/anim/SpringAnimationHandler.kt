package ch.deletescape.lawnchair.anim

import android.support.animation.FloatPropertyCompat
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import ch.deletescape.lawnchair.R

class SpringAnimationHandler<T>(val velocityDirection: Int, val animationFactory: AnimationFactory<T>) {
    val animations = ArrayList<SpringAnimation>()
    var velocityTracker: VelocityTracker? = null
    val isVerticalDirection: Boolean get() = velocityDirection == 0
    var currentVelocity = 0f
    var shouldComputeVelocity = false

    fun add(view: View, obj: T) {
        var springAnimation = view.getTag(R.id.spring_animation_key) as SpringAnimation?
        if (springAnimation == null) {
            springAnimation = animationFactory.initialize(obj)
            view.setTag(R.id.spring_animation_key, springAnimation)
        }
        animationFactory.update(springAnimation, obj)
        springAnimation.setStartVelocity(currentVelocity)
        animations.add(springAnimation)
    }

    fun remove(view: View) {
        val springAnimation = view.getTag(R.id.spring_animation_key) as SpringAnimation
        if (springAnimation.canSkipToEnd()) {
            springAnimation.skipToEnd()
        }
        animations.remove(springAnimation)
    }

    fun addMovement(ev: MotionEvent) {
        when (ev.actionMasked) {
            0 -> reset()
            3 -> reset()
        }
        velocityTracker().addMovement(ev)
        shouldComputeVelocity = true
    }

    fun animateToFinalPosition(f: Float, i: Int) {
        animateToFinalPosition(f, i, shouldComputeVelocity)
    }

    fun animateToFinalPosition(f: Float, i: Int, z: Boolean) {
        if (shouldComputeVelocity)
            currentVelocity = computeVelocity()
        animations.forEach {
            it.setStartValue(i.toFloat())
            if (z)
                it.setStartVelocity(currentVelocity)
            it.animateToFinalPosition(f)
        }
    }

    fun animateToPositionWithVelocity(f: Float, i: Int, f2: Float) {
        currentVelocity = f2
        shouldComputeVelocity = false
        animateToFinalPosition(f, i, true)
    }

    fun skipToEnd() {
        animations
                .filter { it.canSkipToEnd() }
                .forEach { it.skipToEnd() }
    }

    fun reset() {
        if (velocityTracker != null) {
            velocityTracker!!.recycle()
            velocityTracker = null
        }
        currentVelocity = 0f
        shouldComputeVelocity = false
    }

    fun computeVelocity(): Float {
        velocityTracker().computeCurrentVelocity(1000)
        val yVelocity = if (isVerticalDirection)
            velocityTracker().yVelocity
        else
            velocityTracker().xVelocity
        return yVelocity * 0.175f
    }

    fun velocityTracker() : VelocityTracker {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        return velocityTracker!!
    }

    interface AnimationFactory<T> {
        fun initialize(obj: T) : SpringAnimation
        fun update(springAnimation: SpringAnimation, obj: T)
    }

    companion object {

        const val TAG = "SpringAnimationHandler"

        fun <K> forView(obj: K, property: FloatPropertyCompat<K>, finalPosition: Float) =
            SpringAnimation(obj, property, finalPosition).apply {
                spring = SpringForce(finalPosition)
            }
    }

}