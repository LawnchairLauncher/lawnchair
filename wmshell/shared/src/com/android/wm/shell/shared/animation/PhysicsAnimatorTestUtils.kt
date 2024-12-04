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

import android.os.Handler
import android.os.Looper
import android.util.ArrayMap
import androidx.dynamicanimation.animation.FloatPropertyCompat
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils.prepareForTest
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.Set
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.drop
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.toTypedArray

typealias UpdateMatcher = (PhysicsAnimator.AnimationUpdate) -> Boolean
typealias UpdateFramesPerProperty<T> =
        ArrayMap<FloatPropertyCompat<in T>, ArrayList<PhysicsAnimator.AnimationUpdate>>

/**
 * Utilities for testing code that uses [PhysicsAnimator].
 *
 * Start by calling [prepareForTest] at the beginning of each test - this will modify the behavior
 * of all PhysicsAnimator instances so that they post animations to the main thread (so they don't
 * crash). It'll also enable the use of the other static helper methods in this class, which you can
 * use to do things like block the test until animations complete (so you can test end states), or
 * verify keyframes.
 */
object PhysicsAnimatorTestUtils {
    var timeoutMs: Long = 2000
    private var startBlocksUntilAnimationsEnd = false
    private val animationThreadHandler = Handler(Looper.getMainLooper())
    private val allAnimatedObjects = HashSet<Any>()
    private val animatorTestHelpers = HashMap<PhysicsAnimator<*>, AnimatorTestHelper<*>>()

    /**
     * Modifies the behavior of all [PhysicsAnimator] instances so that they post animations to the
     * main thread, and report all of their
     */
    @JvmStatic
    fun prepareForTest() {
        PhysicsAnimator.onAnimatorCreated = { animator, target ->
            allAnimatedObjects.add(target)
            animatorTestHelpers[animator] = AnimatorTestHelper(animator)
        }

        timeoutMs = 2000
        startBlocksUntilAnimationsEnd = false
        allAnimatedObjects.clear()
    }

    @JvmStatic
    fun tearDown() {
        val latch = CountDownLatch(1)
        animationThreadHandler.post {
            animatorTestHelpers.keys.forEach { it.cancel() }
            latch.countDown()
        }

        latch.await()

        animatorTestHelpers.clear()
        animators.clear()
        allAnimatedObjects.clear()
    }

    /**
     * Sets the maximum time (in milliseconds) to block the test thread while waiting for animations
     * before throwing an exception.
     */
    @JvmStatic
    fun setBlockTimeout(timeoutMs: Long) {
        PhysicsAnimatorTestUtils.timeoutMs = timeoutMs
    }

    /**
     * Sets whether all animations should block the test thread until they end. This is typically
     * the desired behavior, since you can invoke code that runs an animation and then assert things
     * about its end state.
     */
    @JvmStatic
    fun setAllAnimationsBlock(block: Boolean) {
        startBlocksUntilAnimationsEnd = block
    }

    /**
     * Blocks the calling thread until animations of the given property on the target object end.
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun <T : Any> blockUntilAnimationsEnd(
        animator: PhysicsAnimator<T>,
        vararg properties: FloatPropertyCompat<in T>
    ) {
        val animatingProperties = HashSet<FloatPropertyCompat<in T>>()
        for (property in properties) {
            if (animator.isPropertyAnimating(property)) {
                animatingProperties.add(property)
            }
        }

        if (animatingProperties.size > 0) {
            val latch = CountDownLatch(animatingProperties.size)
            getAnimationTestHelper(animator).addTestEndListener(
                    object : PhysicsAnimator.EndListener<T> {
                override fun onAnimationEnd(
                    target: T,
                    property: FloatPropertyCompat<in T>,
                    wasFling: Boolean,
                    canceled: Boolean,
                    finalValue: Float,
                    finalVelocity: Float,
                    allRelevantPropertyAnimsEnded: Boolean
                ) {
                    if (animatingProperties.contains(property)) {
                        latch.countDown()
                    }
                }
            })

            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Blocks the calling thread until all animations of the given property (on all target objects)
     * have ended. Useful when you don't have access to the objects being animated, but still need
     * to wait for them to end so that other testable side effects occur (such as update/end
     * listeners).
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> blockUntilAnimationsEnd(
        vararg properties: FloatPropertyCompat<in T>
    ) {
        for (target in allAnimatedObjects) {
            try {
                blockUntilAnimationsEnd(
                        PhysicsAnimator.getInstance(target) as PhysicsAnimator<T>, *properties)
            } catch (e: ClassCastException) {
                // Keep checking the other objects for ones whose types match the provided
                // properties.
            }
        }
    }

    /**
     * Blocks the calling thread until the first animation frame in which predicate returns true. If
     * the given object isn't animating, returns without blocking.
     */
    @JvmStatic
    @Throws(InterruptedException::class)
    fun <T : Any> blockUntilFirstAnimationFrameWhereTrue(
        animator: PhysicsAnimator<T>,
        predicate: (T) -> Boolean
    ) {
        if (animator.isRunning()) {
            val latch = CountDownLatch(1)
            getAnimationTestHelper(animator).addTestUpdateListener(object : PhysicsAnimator
            .UpdateListener<T> {
                override fun onAnimationUpdateForProperty(
                    target: T,
                    values: UpdateMap<T>
                ) {
                    if (predicate(target)) {
                        latch.countDown()
                    }
                }
            })

            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * Verifies that the animator reported animation frame values to update listeners that satisfy
     * the given matchers, in order. Not all frames need to satisfy a matcher - we'll run through
     * all animation frames, and check them against the current predicate. If it returns false, we
     * continue through the frames until it returns true, and then move on to the next matcher.
     * Verification fails if we run out of frames while unsatisfied matchers remain.
     *
     * If verification is successful, all frames to this point are considered 'verified' and will be
     * cleared. Subsequent calls to this method will start verification at the next animation frame.
     *
     * Example: Verify that an animation surpassed x = 50f before going negative.
     * verifyAnimationUpdateFrames(
     *    animator, TRANSLATION_X,
     *    { u -> u.value > 50f },
     *    { u -> u.value < 0f })
     *
     * Example: verify that an animation went backwards at some point while still being on-screen.
     * verifyAnimationUpdateFrames(
     *    animator, TRANSLATION_X,
     *    { u -> u.velocity < 0f && u.value >= 0f })
     *
     * This method is intended to help you test longer, more complicated animations where it's
     * critical that certain values were reached. Using this method to test short animations can
     * fail due to the animation having fewer frames than provided matchers. For example, an
     * animation from x = 1f to x = 5f might only have two frames, at x = 3f and x = 5f. The
     * following would then fail despite it seeming logically sound:
     *
     * verifyAnimationUpdateFrames(
     *    animator, TRANSLATION_X,
     *    { u -> u.value > 1f },
     *    { u -> u.value > 2f },
     *    { u -> u.value > 3f })
     *
     * Tests might also fail if your matchers are too granular, such as this example test after an
     * animation from x = 0f to x = 100f. It's unlikely there was a frame specifically between 2f
     * and 3f.
     *
     * verifyAnimationUpdateFrames(
     *    animator, TRANSLATION_X,
     *    { u -> u.value > 2f && u.value < 3f },
     *    { u -> u.value >= 50f })
     *
     * Failures will print a helpful log of all animation frames so you can see what caused the test
     * to fail.
     */
    fun <T : Any> verifyAnimationUpdateFrames(
        animator: PhysicsAnimator<T>,
        property: FloatPropertyCompat<in T>,
        firstUpdateMatcher: UpdateMatcher,
        vararg additionalUpdateMatchers: UpdateMatcher
    ) {
        val updateFrames: UpdateFramesPerProperty<T> = getAnimationUpdateFrames(animator)

        if (!updateFrames.containsKey(property)) {
            error("No frames for given target object and property.")
        }

        // Copy the frames to avoid a ConcurrentModificationException if the animation update
        // listeners attempt to add a new frame while we're verifying these.
        val framesForProperty = ArrayList(updateFrames[property]!!)
        val matchers = ArrayDeque<UpdateMatcher>(
                additionalUpdateMatchers.toList())
        val frameTraceMessage = StringBuilder()

        var curMatcher = firstUpdateMatcher

        // Loop through the updates from the testable animator.
        for (update in framesForProperty) {
            // Check whether this frame satisfies the current matcher.
            if (curMatcher(update)) {
                // If that was the last unsatisfied matcher, we're good here. 'Verify' all remaining
                // frames and return without failing.
                if (matchers.size == 0) {
                    getAnimationUpdateFrames(animator).remove(property)
                    return
                }

                frameTraceMessage.append("$update\t(satisfied matcher)\n")
                curMatcher = matchers.pop() // Get the next matcher and keep going.
            } else {
                frameTraceMessage.append("${update}\n")
            }
        }

        val readablePropertyName = PhysicsAnimator.getReadablePropertyName(property)
        getAnimationUpdateFrames(animator).remove(property)

        throw RuntimeException(
                "Failed to verify animation frames for property $readablePropertyName: " +
                        "Provided ${additionalUpdateMatchers.size + 1} matchers, " +
                        "however ${matchers.size + 1} remained unsatisfied.\n\n" +
                        "All frames:\n$frameTraceMessage")
    }

    /**
     * Overload of [verifyAnimationUpdateFrames] that builds matchers for you, from given float
     * values. For example, to verify that an animations passed from 0f to 50f to 100f back to 50f:
     *
     * verifyAnimationUpdateFrames(animator, TRANSLATION_X, 0f, 50f, 100f, 50f)
     *
     * This verifies that update frames were received with values of >= 0f, >= 50f, >= 100f, and
     * <= 50f.
     *
     * The same caveats apply: short animations might not have enough frames to satisfy all of the
     * matchers, and overly specific calls (such as 0f, 1f, 2f, 3f, etc. for an animation from
     * x = 0f to x = 100f) might fail as the animation only had frames at 0f, 25f, 50f, 75f, and
     * 100f. As with [verifyAnimationUpdateFrames], failures will print a helpful log of all frames
     * so you can see what caused the test to fail.
     */
    fun <T : Any> verifyAnimationUpdateFrames(
        animator: PhysicsAnimator<T>,
        property: FloatPropertyCompat<in T>,
        startValue: Float,
        firstTargetValue: Float,
        vararg additionalTargetValues: Float
    ) {
        val matchers = ArrayList<UpdateMatcher>()

        val values = ArrayList<Float>().also {
            it.add(firstTargetValue)
            it.addAll(additionalTargetValues.toList())
        }

        var prevVal = startValue
        for (value in values) {
            if (value > prevVal) {
                matchers.add { update -> update.value >= value }
            } else {
                matchers.add { update -> update.value <= value }
            }

            prevVal = value
        }

        verifyAnimationUpdateFrames(
                animator, property, matchers[0], *matchers.drop(0).toTypedArray())
    }

    /**
     * Returns all of the values that have ever been reported to update listeners, per property.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getAnimationUpdateFrames(animator: PhysicsAnimator<T>):
            UpdateFramesPerProperty<T> {
        return animatorTestHelpers[animator]?.getUpdates() as UpdateFramesPerProperty<T>
    }

    /**
     * Clears animation frame updates from the given animator so they aren't used the next time its
     * passed to [verifyAnimationUpdateFrames].
     */
    fun <T : Any> clearAnimationUpdateFrames(animator: PhysicsAnimator<T>) {
        animatorTestHelpers[animator]?.clearUpdates()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> getAnimationTestHelper(animator: PhysicsAnimator<T>): AnimatorTestHelper<T> {
        return animatorTestHelpers[animator] as AnimatorTestHelper<T>
    }

    /**
     * Helper class for testing an animator. This replaces the animator's start action with
     * [startForTest] and adds test listeners to enable other test utility behaviors. We build one
     * these for each Animator and keep them around so we can access the updates.
     */
    class AnimatorTestHelper<T> (private val animator: PhysicsAnimator<T>) {

        /** All updates received for each property animation. */
        private val allUpdates =
                ArrayMap<FloatPropertyCompat<in T>, ArrayList<PhysicsAnimator.AnimationUpdate>>()

        private val testEndListeners = ArrayList<PhysicsAnimator.EndListener<T>>()
        private val testUpdateListeners = ArrayList<PhysicsAnimator.UpdateListener<T>>()

        /** Whether we're currently in the middle of executing startInternal(). */
        private var currentlyRunningStartInternal = false

        init {
            animator.startAction = ::startForTest
            animator.cancelAction = ::cancelForTest
        }

        internal fun addTestEndListener(listener: PhysicsAnimator.EndListener<T>) {
            testEndListeners.add(listener)
        }

        internal fun addTestUpdateListener(listener: PhysicsAnimator.UpdateListener<T>) {
            testUpdateListeners.add(listener)
        }

        internal fun getUpdates(): UpdateFramesPerProperty<T> {
            return allUpdates
        }

        internal fun clearUpdates() {
            allUpdates.clear()
        }

        private fun startForTest() {
            // The testable animator needs to block the main thread until super.start() has been
            // called, since callers expect .start() to be synchronous but we're posting it to a
            // handler here. We may also continue blocking until all animations end, if
            // startBlocksUntilAnimationsEnd = true.
            val unblockLatch = CountDownLatch(if (startBlocksUntilAnimationsEnd) 2 else 1)

            animationThreadHandler.post {
                // Add an update listener that dispatches to any test update listeners added by
                // tests.
                animator.addUpdateListener(object : PhysicsAnimator.UpdateListener<T> {
                    override fun onAnimationUpdateForProperty(
                        target: T,
                        values: ArrayMap<FloatPropertyCompat<in T>, PhysicsAnimator.AnimationUpdate>
                    ) {
                        values.forEach { (property, value) ->
                            allUpdates.getOrPut(property, { ArrayList() }).add(value)
                        }

                        for (listener in testUpdateListeners) {
                            listener.onAnimationUpdateForProperty(target, values)
                        }
                    }
                })

                // Add an end listener that dispatches to any test end listeners added by tests, and
                // unblocks the main thread if required.
                animator.addEndListener(object : PhysicsAnimator.EndListener<T> {
                    override fun onAnimationEnd(
                        target: T,
                        property: FloatPropertyCompat<in T>,
                        wasFling: Boolean,
                        canceled: Boolean,
                        finalValue: Float,
                        finalVelocity: Float,
                        allRelevantPropertyAnimsEnded: Boolean
                    ) {
                        for (listener in testEndListeners) {
                            listener.onAnimationEnd(
                                    target, property, wasFling, canceled, finalValue, finalVelocity,
                                    allRelevantPropertyAnimsEnded)
                        }

                        if (allRelevantPropertyAnimsEnded) {
                            testEndListeners.clear()
                            testUpdateListeners.clear()

                            if (startBlocksUntilAnimationsEnd) {
                                unblockLatch.countDown()
                            }
                        }
                    }
                })

                currentlyRunningStartInternal = true
                animator.startInternal()
                currentlyRunningStartInternal = false
                unblockLatch.countDown()
            }

            unblockLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }

        private fun cancelForTest(properties: Set<FloatPropertyCompat<in T>>) {
            // If this was called from startInternal, we are already on the animation thread, and
            // should just call cancelInternal rather than posting it. If we post it, the
            // cancellation will occur after the rest of startInternal() and we'll immediately
            // cancel the animation we worked so hard to start!
            if (currentlyRunningStartInternal) {
                animator.cancelInternal(properties)
                return
            }

            val unblockLatch = CountDownLatch(1)

            animationThreadHandler.post {
                animator.cancelInternal(properties)
                unblockLatch.countDown()
            }

            unblockLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }
}
