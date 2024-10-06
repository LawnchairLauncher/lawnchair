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

package androidx.dynamicanimation.animation;

import android.util.AndroidRuntimeException;

import androidx.annotation.MainThread;

/**
 * SpringAnimation is an animation that is driven by a {@link SpringForce}. The spring force defines
 * the spring's stiffness, damping ratio, as well as the rest position. Once the SpringAnimation is
 * started, on each frame the spring force will update the animation's value and velocity.
 * The animation will continue to run until the spring force reaches equilibrium. If the spring used
 * in the animation is undamped, the animation will never reach equilibrium. Instead, it will
 * oscillate forever.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * </div>
 *
 * <p>To create a simple {@link SpringAnimation} that uses the default {@link SpringForce}:</p>
 * <pre class="prettyprint">
 * // Create an animation to animate view's X property, set the rest position of the
 * // default spring to 0, and start the animation with a starting velocity of 5000 (pixel/s).
 * final SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.X, 0)
 *         .setStartVelocity(5000);
 * anim.start();
 * </pre>
 *
 * <p>Alternatively, a {@link SpringAnimation} can take a pre-configured {@link SpringForce}, and
 * use that to drive the animation. </p>
 * <pre class="prettyprint">
 * // Create a low stiffness, low bounce spring at position 0.
 * SpringForce spring = new SpringForce(0)
 *         .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
 *         .setStiffness(SpringForce.STIFFNESS_LOW);
 * // Create an animation to animate view's scaleY property, and start the animation using
 * // the spring above and a starting value of 0.5. Additionally, constrain the range of value for
 * // the animation to be non-negative, effectively preventing any spring overshoot.
 * final SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.SCALE_Y)
 *         .setMinValue(0).setSpring(spring).setStartValue(1);
 * anim.start();
 * </pre>
 */
public final class SpringAnimation extends DynamicAnimation<SpringAnimation> {

    private SpringForce mSpring = null;
    private float mPendingPosition = UNSET;
    private static final float UNSET = Float.MAX_VALUE;
    private boolean mEndRequested = false;

    /**
     * <p>This creates a SpringAnimation that animates a {@link FloatValueHolder} instance. During
     * the animation, the {@link FloatValueHolder} instance will be updated via
     * {@link FloatValueHolder#setValue(float)} each frame. The caller can obtain the up-to-date
     * animation value via {@link FloatValueHolder#getValue()}.
     *
     * <p><strong>Note:</strong> changing the value in the {@link FloatValueHolder} via
     * {@link FloatValueHolder#setValue(float)} outside of the animation during an
     * animation run will not have any effect on the on-going animation.
     *
     * @param floatValueHolder the property to be animated
     */
    public SpringAnimation(FloatValueHolder floatValueHolder) {
        super(floatValueHolder);
    }

    /**
     * <p>This creates a SpringAnimation that animates a {@link FloatValueHolder} instance. During
     * the animation, the {@link FloatValueHolder} instance will be updated via
     * {@link FloatValueHolder#setValue(float)} each frame. The caller can obtain the up-to-date
     * animation value via {@link FloatValueHolder#getValue()}.
     *
     * A Spring will be created with the given final position and default stiffness and damping
     * ratio. This spring can be accessed and reconfigured through {@link #setSpring(SpringForce)}.
     *
     * <p><strong>Note:</strong> changing the value in the {@link FloatValueHolder} via
     * {@link FloatValueHolder#setValue(float)} outside of the animation during an
     * animation run will not have any effect on the on-going animation.
     *
     * @param floatValueHolder the property to be animated
     * @param finalPosition the final position of the spring to be created.
     */
    public SpringAnimation(FloatValueHolder floatValueHolder, float finalPosition) {
        super(floatValueHolder);
        mSpring = new SpringForce(finalPosition);
    }

    /**
     * This creates a SpringAnimation that animates the property of the given object.
     * Note, a spring will need to setup through {@link #setSpring(SpringForce)} before
     * the animation starts.
     *
     * @param object the Object whose property will be animated
     * @param property the property to be animated
     * @param <K> the class on which the Property is declared
     */
    public <K> SpringAnimation(K object, FloatPropertyCompat<K> property) {
        super(object, property);
    }

    /**
     * This creates a SpringAnimation that animates the property of the given object. A Spring will
     * be created with the given final position and default stiffness and damping ratio.
     * This spring can be accessed and reconfigured through {@link #setSpring(SpringForce)}.
     *
     * @param object the Object whose property will be animated
     * @param property the property to be animated
     * @param finalPosition the final position of the spring to be created.
     * @param <K> the class on which the Property is declared
     */
    public <K> SpringAnimation(K object, FloatPropertyCompat<K> property,
            float finalPosition) {
        super(object, property);
        mSpring = new SpringForce(finalPosition);
    }

    /**
     * Returns the spring that the animation uses for animations.
     *
     * @return the spring that the animation uses for animations
     */
    public SpringForce getSpring() {
        return mSpring;
    }

    /**
     * Uses the given spring as the force that drives this animation. If this spring force has its
     * parameters re-configured during the animation, the new configuration will be reflected in the
     * animation immediately.
     *
     * @param force a pre-defined spring force that drives the animation
     * @return the animation that the spring force is set on
     */
    public SpringAnimation setSpring(SpringForce force) {
        mSpring = force;
        return this;
    }

    @MainThread
    @Override
    public void start() {
        sanityCheck();
        mSpring.setValueThreshold(getValueThreshold());
        super.start();
    }

    /**
     * Updates the final position of the spring.
     * <p/>
     * When the animation is running, calling this method would assume the position change of the
     * spring as a continuous movement since last frame, which yields more accurate results than
     * changing the spring position directly through {@link SpringForce#setFinalPosition(float)}.
     * <p/>
     * If the animation hasn't started, calling this method will change the spring position, and
     * immediately start the animation.
     *
     * @param finalPosition rest position of the spring
     */
    public void animateToFinalPosition(float finalPosition) {
        if (isRunning()) {
            mPendingPosition = finalPosition;
        } else {
            if (mSpring == null) {
                mSpring = new SpringForce(finalPosition);
            }
            mSpring.setFinalPosition(finalPosition);
            start();
        }
    }

    /**
     * Cancels the on-going animation. If the animation hasn't started, no op. Note that this method
     * should only be called on main thread.
     *
     * @throws AndroidRuntimeException if this method is not called on the main thread
     */
    @MainThread
    @Override
    public void cancel() {
        super.cancel();
        if (mPendingPosition != UNSET) {
            if (mSpring == null) {
                mSpring = new SpringForce(mPendingPosition);
            } else {
                mSpring.setFinalPosition(mPendingPosition);
            }
            mPendingPosition = UNSET;
        }
    }

    /**
     * Skips to the end of the animation. If the spring is undamped, an
     * {@link IllegalStateException} will be thrown, as the animation would never reach to an end.
     * It is recommended to check {@link #canSkipToEnd()} before calling this method. If animation
     * is not running, no-op.
     *
     * Unless a AnimationHandler is provided via setAnimationHandler, a default AnimationHandler
     * is created on the same thread as the first call to start/cancel an animation. All the
     * subsequent animation lifecycle manipulations need to be on that same thread, until the
     * AnimationHandler is reset (using [setAnimationHandler]).
     *
     * @throws IllegalStateException if the spring is undamped (i.e. damping ratio = 0)
     * @throws AndroidRuntimeException if this method is not called on the same thread as the
     * animation handler
     */
    public void skipToEnd() {
        if (!canSkipToEnd()) {
            throw new UnsupportedOperationException("Spring animations can only come to an end"
                    + " when there is damping");
        }
        if (!getAnimationHandler().isCurrentThread()) {
            throw new AndroidRuntimeException("Animations may only be started on the same thread "
                    + "as the animation handler");
        }
        if (mRunning) {
            mEndRequested = true;
        }
    }

    /**
     * Queries whether the spring can eventually come to the rest position.
     *
     * @return {@code true} if the spring is damped, otherwise {@code false}
     */
    public boolean canSkipToEnd() {
        return mSpring.mDampingRatio > 0;
    }

    /************************ Below are private APIs *************************/

    private void sanityCheck() {
        if (mSpring == null) {
            throw new UnsupportedOperationException("Incomplete SpringAnimation: Either final"
                    + " position or a spring force needs to be set.");
        }
        double finalPosition = mSpring.getFinalPosition();
        if (finalPosition > mMaxValue) {
            throw new UnsupportedOperationException("Final position of the spring cannot be greater"
                    + " than the max value.");
        } else if (finalPosition < mMinValue) {
            throw new UnsupportedOperationException("Final position of the spring cannot be less"
                    + " than the min value.");
        }
    }

    @Override
    boolean updateValueAndVelocity(long deltaT) {
        // If user had requested end, then update the value and velocity to end state and consider
        // animation done.
        if (mEndRequested) {
            if (mPendingPosition != UNSET) {
                mSpring.setFinalPosition(mPendingPosition);
                mPendingPosition = UNSET;
            }
            mValue = mSpring.getFinalPosition();
            mVelocity = 0;
            mEndRequested = false;
            return true;
        }

        if (mPendingPosition != UNSET) {
            // Approximate by considering half of the time spring position stayed at the old
            // position, half of the time it's at the new position.
            MassState massState = mSpring.updateValues(mValue, mVelocity, deltaT / 2);
            mSpring.setFinalPosition(mPendingPosition);
            mPendingPosition = UNSET;

            massState = mSpring.updateValues(massState.mValue, massState.mVelocity, deltaT / 2);
            mValue = massState.mValue;
            mVelocity = massState.mVelocity;

        } else {
            MassState massState = mSpring.updateValues(mValue, mVelocity, deltaT);
            mValue = massState.mValue;
            mVelocity = massState.mVelocity;
        }

        mValue = Math.max(mValue, mMinValue);
        mValue = Math.min(mValue, mMaxValue);

        if (isAtEquilibrium(mValue, mVelocity)) {
            mValue = mSpring.getFinalPosition();
            mVelocity = 0f;
            return true;
        }
        return false;
    }

    @Override
    float getAcceleration(float value, float velocity) {
        return mSpring.getAcceleration(value, velocity);
    }

    @Override
    boolean isAtEquilibrium(float value, float velocity) {
        return mSpring.isAtEquilibrium(value, velocity);
    }

    @Override
    void setValueThreshold(float threshold) {
    }
}
