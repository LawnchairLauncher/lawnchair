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

import androidx.annotation.FloatRange;
import androidx.annotation.RestrictTo;

/**
 * Spring Force defines the characteristics of the spring being used in the animation.
 * <p>
 * By configuring the stiffness and damping ratio, callers can create a spring with the look and
 * feel suits their use case. Stiffness corresponds to the spring constant. The stiffer the spring
 * is, the harder it is to stretch it, the faster it undergoes dampening.
 * <p>
 * Spring damping ratio describes how oscillations in a system decay after a disturbance.
 * When damping ratio > 1* (i.e. over-damped), the object will quickly return to the rest position
 * without overshooting. If damping ratio equals to 1 (i.e. critically damped), the object will
 * return to equilibrium within the shortest amount of time. When damping ratio is less than 1
 * (i.e. under-damped), the mass tends to overshoot, and return, and overshoot again. Without any
 * damping (i.e. damping ratio = 0), the mass will oscillate forever.
 */
public final class SpringForce implements Force {
    /**
     * Stiffness constant for extremely stiff spring.
     */
    public static final float STIFFNESS_HIGH = 10_000f;
    /**
     * Stiffness constant for medium stiff spring. This is the default stiffness for spring force.
     */
    public static final float STIFFNESS_MEDIUM = 1500f;
    /**
     * Stiffness constant for a spring with low stiffness.
     */
    public static final float STIFFNESS_LOW = 200f;
    /**
     * Stiffness constant for a spring with very low stiffness.
     */
    public static final float STIFFNESS_VERY_LOW = 50f;

    /**
     * Damping ratio for a very bouncy spring. Note for under-damped springs
     * (i.e. damping ratio < 1), the lower the damping ratio, the more bouncy the spring.
     */
    public static final float DAMPING_RATIO_HIGH_BOUNCY = 0.2f;
    /**
     * Damping ratio for a medium bouncy spring. This is also the default damping ratio for spring
     * force. Note for under-damped springs (i.e. damping ratio < 1), the lower the damping ratio,
     * the more bouncy the spring.
     */
    public static final float DAMPING_RATIO_MEDIUM_BOUNCY = 0.5f;
    /**
     * Damping ratio for a spring with low bounciness. Note for under-damped springs
     * (i.e. damping ratio < 1), the lower the damping ratio, the higher the bounciness.
     */
    public static final float DAMPING_RATIO_LOW_BOUNCY = 0.75f;
    /**
     * Damping ratio for a spring with no bounciness. This damping ratio will create a critically
     * damped spring that returns to equilibrium within the shortest amount of time without
     * oscillating.
     */
    public static final float DAMPING_RATIO_NO_BOUNCY = 1f;

    // This multiplier is used to calculate the velocity threshold given a certain value threshold.
    // The idea is that if it takes >= 1 frame to move the value threshold amount, then the velocity
    // is a reasonable threshold.
    private static final double VELOCITY_THRESHOLD_MULTIPLIER = 1000.0 / 16.0;

    // Natural frequency
    double mNaturalFreq = Math.sqrt(STIFFNESS_MEDIUM);
    // Damping ratio.
    double mDampingRatio = DAMPING_RATIO_MEDIUM_BOUNCY;

    // Value to indicate an unset state.
    private static final double UNSET = Double.MAX_VALUE;

    // Indicates whether the spring has been initialized
    private boolean mInitialized = false;

    // Threshold for velocity and value to determine when it's reasonable to assume that the spring
    // is approximately at rest.
    private double mValueThreshold;
    private double mVelocityThreshold;

    // Intermediate values to simplify the spring function calculation per frame.
    private double mGammaPlus;
    private double mGammaMinus;
    private double mDampedFreq;

    // Final position of the spring. This must be set before the start of the animation.
    private double mFinalPosition = UNSET;

    // Internal state to hold a value/velocity pair.
    private final DynamicAnimation.MassState mMassState = new DynamicAnimation.MassState();

    /**
     * Creates a spring force. Note that final position of the spring must be set through
     * {@link #setFinalPosition(float)} before the spring animation starts.
     */
    public SpringForce() {
        // No op.
    }

    /**
     * Creates a spring with a given final rest position.
     *
     * @param finalPosition final position of the spring when it reaches equilibrium
     */
    public SpringForce(float finalPosition) {
        mFinalPosition = finalPosition;
    }

    /**
     * Sets the stiffness of a spring. The more stiff a spring is, the more force it applies to
     * the object attached when the spring is not at the final position. Default stiffness is
     * {@link #STIFFNESS_MEDIUM}.
     *
     * @param stiffness non-negative stiffness constant of a spring
     * @return the spring force that the given stiffness is set on
     * @throws IllegalArgumentException if the given spring stiffness is not positive
     */
    public SpringForce setStiffness(
            @FloatRange(from = 0.0, fromInclusive = false) float stiffness) {
        if (stiffness <= 0) {
            throw new IllegalArgumentException("Spring stiffness constant must be positive.");
        }
        mNaturalFreq = Math.sqrt(stiffness);
        // All the intermediate values need to be recalculated.
        mInitialized = false;
        return this;
    }

    /**
     * Gets the stiffness of the spring.
     *
     * @return the stiffness of the spring
     */
    public float getStiffness() {
        return (float) (mNaturalFreq * mNaturalFreq);
    }

    /**
     * Spring damping ratio describes how oscillations in a system decay after a disturbance.
     * <p>
     * When damping ratio > 1 (over-damped), the object will quickly return to the rest position
     * without overshooting. If damping ratio equals to 1 (i.e. critically damped), the object will
     * return to equilibrium within the shortest amount of time. When damping ratio is less than 1
     * (i.e. under-damped), the mass tends to overshoot, and return, and overshoot again. Without
     * any damping (i.e. damping ratio = 0), the mass will oscillate forever.
     * <p>
     * Default damping ratio is {@link #DAMPING_RATIO_MEDIUM_BOUNCY}.
     *
     * @param dampingRatio damping ratio of the spring, it should be non-negative
     * @return the spring force that the given damping ratio is set on
     * @throws IllegalArgumentException if the {@param dampingRatio} is negative.
     */
    public SpringForce setDampingRatio(@FloatRange(from = 0.0) float dampingRatio) {
        if (dampingRatio < 0) {
            throw new IllegalArgumentException("Damping ratio must be non-negative");
        }
        mDampingRatio = dampingRatio;
        // All the intermediate values need to be recalculated.
        mInitialized = false;
        return this;
    }

    /**
     * Returns the damping ratio of the spring.
     *
     * @return damping ratio of the spring
     */
    public float getDampingRatio() {
        return (float) mDampingRatio;
    }

    /**
     * Sets the rest position of the spring.
     *
     * @param finalPosition rest position of the spring
     * @return the spring force that the given final position is set on
     */
    public SpringForce setFinalPosition(float finalPosition) {
        mFinalPosition = finalPosition;
        return this;
    }

    /**
     * Returns the rest position of the spring.
     *
     * @return rest position of the spring
     */
    public float getFinalPosition() {
        return (float) mFinalPosition;
    }

    /*********************** Below are private APIs *********************/

    /**
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public float getAcceleration(float lastDisplacement, float lastVelocity) {

        lastDisplacement -= getFinalPosition();

        double k = mNaturalFreq * mNaturalFreq;
        double c = 2 * mNaturalFreq * mDampingRatio;

        return (float) (-k * lastDisplacement - c * lastVelocity);
    }

    /**
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public boolean isAtEquilibrium(float value, float velocity) {
        if (Math.abs(velocity) < mVelocityThreshold
                && Math.abs(value - getFinalPosition()) < mValueThreshold) {
            return true;
        }
        return false;
    }

    /**
     * Initialize the string by doing the necessary pre-calculation as well as some sanity check
     * on the setup.
     *
     * @throws IllegalStateException if the final position is not yet set by the time the spring
     *                               animation has started
     */
    private void init() {
        if (mInitialized) {
            return;
        }

        if (mFinalPosition == UNSET) {
            throw new IllegalStateException("Error: Final position of the spring must be"
                    + " set before the animation starts");
        }

        if (mDampingRatio > 1) {
            // Over damping
            mGammaPlus = -mDampingRatio * mNaturalFreq
                    + mNaturalFreq * Math.sqrt(mDampingRatio * mDampingRatio - 1);
            mGammaMinus = -mDampingRatio * mNaturalFreq
                    - mNaturalFreq * Math.sqrt(mDampingRatio * mDampingRatio - 1);
        } else if (mDampingRatio >= 0 && mDampingRatio < 1) {
            // Under damping
            mDampedFreq = mNaturalFreq * Math.sqrt(1 - mDampingRatio * mDampingRatio);
        }

        mInitialized = true;
    }

    /**
     * Internal only call for Spring to calculate the spring position/velocity using
     * an analytical approach.
     */
    DynamicAnimation.MassState updateValues(double lastDisplacement, double lastVelocity,
            long timeElapsed) {
        init();

        double deltaT = timeElapsed / 1000d; // unit: seconds
        lastDisplacement -= mFinalPosition;
        double displacement;
        double currentVelocity;
        if (mDampingRatio > 1) {
            // Overdamped
            double coeffA =  lastDisplacement - (mGammaMinus * lastDisplacement - lastVelocity)
                    / (mGammaMinus - mGammaPlus);
            double coeffB =  (mGammaMinus * lastDisplacement - lastVelocity)
                    / (mGammaMinus - mGammaPlus);
            displacement = coeffA * Math.pow(Math.E, mGammaMinus * deltaT)
                    + coeffB * Math.pow(Math.E, mGammaPlus * deltaT);
            currentVelocity = coeffA * mGammaMinus * Math.pow(Math.E, mGammaMinus * deltaT)
                    + coeffB * mGammaPlus * Math.pow(Math.E, mGammaPlus * deltaT);
        } else if (mDampingRatio == 1) {
            // Critically damped
            double coeffA = lastDisplacement;
            double coeffB = lastVelocity + mNaturalFreq * lastDisplacement;
            displacement = (coeffA + coeffB * deltaT) * Math.pow(Math.E, -mNaturalFreq * deltaT);
            currentVelocity = (coeffA + coeffB * deltaT) * Math.pow(Math.E, -mNaturalFreq * deltaT)
                    * -mNaturalFreq + coeffB * Math.pow(Math.E, -mNaturalFreq * deltaT);
        } else {
            // Underdamped
            double cosCoeff = lastDisplacement;
            double sinCoeff = (1 / mDampedFreq) * (mDampingRatio * mNaturalFreq
                    * lastDisplacement + lastVelocity);
            displacement = Math.pow(Math.E, -mDampingRatio * mNaturalFreq * deltaT)
                    * (cosCoeff * Math.cos(mDampedFreq * deltaT)
                    + sinCoeff * Math.sin(mDampedFreq * deltaT));
            currentVelocity = displacement * -mNaturalFreq * mDampingRatio
                    + Math.pow(Math.E, -mDampingRatio * mNaturalFreq * deltaT)
                    * (-mDampedFreq * cosCoeff * Math.sin(mDampedFreq * deltaT)
                    + mDampedFreq * sinCoeff * Math.cos(mDampedFreq * deltaT));
        }

        mMassState.mValue = (float) (displacement + mFinalPosition);
        mMassState.mVelocity = (float) currentVelocity;
        return mMassState;
    }

    /**
     * This threshold defines how close the animation value needs to be before the animation can
     * finish. This default value is based on the property being animated, e.g. animations on alpha,
     * scale, translation or rotation would have different thresholds. This value should be small
     * enough to avoid visual glitch of "jumping to the end". But it shouldn't be so small that
     * animations take seconds to finish.
     *
     * @param threshold the difference between the animation value and final spring position that
     *                  is allowed to end the animation when velocity is very low
     */
    void setValueThreshold(double threshold) {
        mValueThreshold = Math.abs(threshold);
        mVelocityThreshold = mValueThreshold * VELOCITY_THRESHOLD_MULTIPLIER;
    }
}
