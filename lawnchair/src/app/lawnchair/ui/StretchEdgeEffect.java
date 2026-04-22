/*
 * Copyright (C) 2010 The Android Open Source Project
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

package app.lawnchair.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RenderNode;
import android.view.animation.AnimationUtils;

import androidx.annotation.IntDef;

import com.android.launcher3.util.EdgeEffectCompat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class performs the graphical effect used at the edges of scrollable widgets
 * when the user scrolls beyond the content bounds in 2D space.
 *
 * <p>EdgeEffect is stateful. Custom widgets using EdgeEffect should create an
 * instance for each edge that should show the effect, feed it input data using
 * the methods {@link #onAbsorb(int)}, {@link #onPull(float)}, and {@link #onRelease()},
 * and draw the effect using {@link #draw(Canvas)} in the widget's overridden
 * {@link android.view.View#draw(Canvas)} method. If {@link #isFinished()} returns
 * false after drawing, the edge effect's animation is not yet complete and the widget
 * should schedule another drawing pass to continue the animation.</p>
 *
 * <p>When drawing, widgets should draw their main content and child views first,
 * usually by invoking <code>super.draw(canvas)</code> from an overridden <code>draw</code>
 * method. (This will invoke onDraw and dispatch drawing to child views as needed.)
 * The edge effect may then be drawn on top of the view's content using the
 * {@link #draw(Canvas)} method.</p>
 */
public class StretchEdgeEffect extends EdgeEffectCompat {
    /**
     * Completely disable edge effect
     */
    private static final int TYPE_NONE = -1;

    /**
     * Use a stretch for the edge effect.
     */
    private static final int TYPE_STRETCH = 1;

    /**
     * The velocity threshold before the spring animation is considered settled.
     * The idea here is that velocity should be less than 0.1 pixel per second.
     */
    private static final double VELOCITY_THRESHOLD = 0.01;

    /**
     * The speed at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final float LINEAR_VELOCITY_TAKE_OVER = 200f;

    /**
     * The value threshold before the spring animation is considered close enough to
     * the destination to be settled. This should be around 0.01 pixel.
     */
    private static final double VALUE_THRESHOLD = 0.001;

    /**
     * The maximum distance at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final double LINEAR_DISTANCE_TAKE_OVER = 8.0;

    /**
     * The natural frequency of the stretch spring.
     */
    private static final double NATURAL_FREQUENCY = 24.657;

    /**
     * The damping ratio of the stretch spring.
     */
    private static final double DAMPING_RATIO = 0.98;

    /**
     * The variation of the velocity for the stretch effect when it meets the bound.
     * if value is > 1, it will accentuate the absorption of the movement.
     */
    private static final float ON_ABSORB_VELOCITY_ADJUSTMENT = 13f;

    @IntDef({TYPE_NONE, TYPE_STRETCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EdgeEffectType {
    }

    private static final float LINEAR_STRETCH_INTENSITY = 0.016f;

    private static final float EXP_STRETCH_INTENSITY = 0.016f;

    private static final float SCROLL_DIST_AFFECTED_BY_EXP_STRETCH = 0.33f;

    @SuppressWarnings("UnusedDeclaration")
    private static final String TAG = "EdgeEffect";

    private float mDistance;
    private float mVelocity; // only for stretch animations

    private long mStartTime;

    private static final int STATE_IDLE = 0;
    private static final int STATE_PULL = 1;
    private static final int STATE_RECEDE = 3;
    private static final int STATE_PULL_DECAY = 4;

    private int mState = STATE_IDLE;

    private float mPullDistance;

    private float mWidth;
    private float mHeight;

    public static final int POSITION_TOP = 0;
    public static final int POSITION_BOTTOM = 1;
    public static final int POSITION_LEFT = 2;
    public static final int POSITION_RIGHT = 3;

    @IntDef({POSITION_TOP, POSITION_BOTTOM, POSITION_LEFT, POSITION_RIGHT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EdgeEffectPosition {
    }

    private Runnable mInvalidate = EMPTY_RUNNABLE;
    private Runnable mPostInvalidateOnAnimation = EMPTY_RUNNABLE;
    private final float[] mTmpOut = new float[5];

    private static final Runnable EMPTY_RUNNABLE = () -> {};

    /**
     * Construct a new EdgeEffect with a theme appropriate for the provided context.
     * @param context Context used to provide theming and resource information for the EdgeEffect
     */
    public StretchEdgeEffect(Context context) {
        super(context);
    }

    public StretchEdgeEffect(Context context, Runnable invalidate, Runnable postInvalidateOnAnimation) {
        this(context);

        mInvalidate = invalidate;
        mPostInvalidateOnAnimation = postInvalidateOnAnimation;
    }

    public void setOnInvalidate(Runnable invalidate) {
        mInvalidate = invalidate;
    }

    public void setPostInvalidateOnAnimation(Runnable postInvalidateOnAnimation) {
        mPostInvalidateOnAnimation = postInvalidateOnAnimation;
    }

    @EdgeEffectType
    private int getCurrentEdgeEffectBehavior() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return TYPE_NONE;
        } else {
            return TYPE_STRETCH;
        }
    }

    /**
     * Set the size of this edge effect in pixels.
     *
     * @param width Effect width in pixels
     * @param height Effect height in pixels
     */
    @Override
    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    /**
     * Reports if this EdgeEffect's animation is finished. If this method returns false
     * after a call to {@link #draw(Canvas)} the host widget should schedule another
     * drawing pass to continue the animation.
     *
     * @return true if animation is finished, false if drawing should continue on the next frame.
     */
    @Override
    public boolean isFinished() {
        return mState == STATE_IDLE;
    }

    /**
     * Immediately finish the current animation.
     * After this call {@link #isFinished()} will return true.
     */
    @Override
    public void finish() {
        mState = STATE_IDLE;
        mDistance = 0;
        mVelocity = 0;
    }

    private void invalidateIfNotFinished() {
        if (!isFinished()) {
            mInvalidate.run();
        }
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly.
     *
     * <p>Views using EdgeEffect should favor {@link #onPull(float, float)} when the displacement
     * of the pull point is known.</p>
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     */
    @Override
    public void onPull(float deltaDistance) {
        onPull(deltaDistance, 0.5f);
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly.
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     * @param displacement The displacement from the starting side of the effect of the point
     *                     initiating the pull. In the case of touch this is the finger position.
     *                     Values may be from 0-1.
     */
    @Override
    public void onPull(float deltaDistance, float displacement) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            finish();
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (mState != STATE_PULL) {
            // Restore the mPullDistance to the fraction it is currently showing -- we want
            // to "catch" the current stretch value.
            mPullDistance = mDistance;
        }
        mState = STATE_PULL;

        mStartTime = now;

        mPullDistance += deltaDistance;
        // Don't allow stretch beyond 1
        mPullDistance = Math.min(1f, mPullDistance);
        mDistance = Math.max(0f, mPullDistance);
        mVelocity = 0;

        if (mDistance == 0) {
            mState = STATE_IDLE;
        }

        invalidateIfNotFinished();
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly. This works similarly to {@link #onPull(float, float)},
     * but returns the amount of <code>deltaDistance</code> that has been consumed. If the
     * {@link #getDistance()} is currently 0 and <code>deltaDistance</code> is negative, this
     * function will return 0 and the drawn value will remain unchanged.
     * <p>
     * This method can be used to reverse the effect from a pull or absorb and partially consume
     * some of a motion:
     *
     * <pre class="prettyprint">
     *     if (deltaY < 0) {
     *         float consumed = edgeEffect.onPullDistance(deltaY / getHeight(), x / getWidth());
     *         deltaY -= consumed * getHeight();
     *         if (edgeEffect.getDistance() == 0f) edgeEffect.onRelease();
     *     }
     * </pre>
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     * @param displacement The displacement from the starting side of the effect of the point
     *                     initiating the pull. In the case of touch this is the finger position.
     *                     Values may be from 0-1.
     * @return The amount of <code>deltaDistance</code> that was consumed, a number between
     * 0 and <code>deltaDistance</code>.
     */
    @Override
    public float onPullDistance(float deltaDistance, float displacement) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            return 0f;
        }
        float finalDistance = Math.max(0f, deltaDistance + mDistance);
        float delta = finalDistance - mDistance;
        if (delta == 0f && mDistance == 0f) {
            return 0f; // No pull, don't do anything.
        }

        onPull(delta, displacement);
        return delta;
    }

    /**
     * Returns the pull distance needed to be released to remove the showing effect.
     * It is determined by the {@link #onPull(float, float)} <code>deltaDistance</code> and
     * any animating values, including from {@link #onAbsorb(int)} and {@link #onRelease()}.
     * <p>
     * This can be used in conjunction with {@link #onPullDistance(float, float)} to
     * release the currently showing effect.
     *
     * @return The pull distance that must be released to remove the showing effect.
     */
    @Override
    public float getDistance() {
        return mDistance;
    }

    /**
     * Call when the object is released after being pulled.
     * This will begin the "decay" phase of the effect. After calling this method
     * the host view should {@link android.view.View#invalidate()} and thereby
     * draw the results accordingly.
     */
    @Override
    public void onRelease() {
        mPullDistance = 0;

        if (mState != STATE_PULL && mState != STATE_PULL_DECAY) {
            return;
        }

        mState = STATE_RECEDE;

        mVelocity = 0.f;

        mStartTime = AnimationUtils.currentAnimationTimeMillis();

        invalidateIfNotFinished();
    }

    /**
     * Call when the effect absorbs an impact at the given velocity.
     * Used when a fling reaches the scroll boundary.
     *
     * <p>When using a {@link android.widget.Scroller} or {@link android.widget.OverScroller},
     * the method <code>getCurrVelocity</code> will provide a reasonable approximation
     * to use here.</p>
     *
     * @param velocity Velocity at impact in pixels per second.
     */
    @Override
    public void onAbsorb(int velocity) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mState = STATE_RECEDE;
            mVelocity = velocity * ON_ABSORB_VELOCITY_ADJUSTMENT;
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            invalidateIfNotFinished();
        } else {
            finish();
        }
    }

    public void applyStretch(Canvas canvas, @EdgeEffectPosition int position) {
        applyStretch(canvas, position, 0, 0);
    }

    public void applyStretch(Canvas canvas, @EdgeEffectPosition int position, int translationX, int translationY) {
        mTmpOut[0] = 0f;
        getScale(mTmpOut, position);
        if (mTmpOut[0] == 1f) {
            canvas.scale(mTmpOut[1], mTmpOut[2], mTmpOut[3] - translationX, mTmpOut[4] - translationY);
        }
    }

    public void getScale(float[] out, @EdgeEffectPosition int position) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_STRETCH) {
            if (mState == STATE_RECEDE) {
                updateSpring();
            }
            if (mDistance != 0) {
                float vec = dampStretchVector(Math.max(-1f, Math.min(1f, mDistance)));
                float scale = 1f + vec;
                /* apply, scaleX, scaleY, pivotX, pivotY */
                out[0] = 1f;
                switch (position) {
                    case POSITION_TOP:
                        out[1] = 1f;
                        out[2] = scale;
                        out[3] = 0f;
                        out[4] = 0f;
                        break;
                    case POSITION_BOTTOM:
                        out[1] = 1f;
                        out[2] = scale;
                        out[3] = 0f;
                        out[4] = mHeight;
                        break;
                    case POSITION_LEFT:
                        out[1] = scale;
                        out[2] = 1f;
                        out[3] = 0f;
                        out[4] = 0f;
                        break;
                    case POSITION_RIGHT:
                        out[1] = scale;
                        out[2] = 1f;
                        out[3] = mWidth;
                        out[4] = 0f;
                        break;
                }
            }
        } else {
            // Animations have been disabled or this is TYPE_STRETCH and drawing into a Canvas
            // that isn't a Recording Canvas, so no effect can be shown. Just end the effect.
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
        }

        boolean oneLastFrame = false;
        if (mState == STATE_RECEDE && mDistance == 0 && mVelocity == 0) {
            mState = STATE_IDLE;
            oneLastFrame = true;
        }

        if (mState != STATE_IDLE || oneLastFrame) {
            mPostInvalidateOnAnimation.run();
        }
    }

    /**
     * Draw into the provided canvas. Assumes that the canvas has been rotated
     * accordingly and the size has been set. The effect will be drawn the full
     * width of X=0 to X=width, beginning from Y=0 and extending to some factor <
     * 1.f of height. The effect will only be visible on a
     * hardware canvas, e.g. {@link RenderNode#beginRecording()}.
     *
     * @param canvas Canvas to draw into
     * @return true if drawing should continue beyond this frame to continue the
     *         animation
     */
    @Override
    public boolean draw(Canvas canvas) {
        return false;
    }

    /**
     * Return the maximum height that the edge effect will be drawn at given the original
     * {@link #setSize(int, int) input size}.
     * @return The maximum height of the edge effect
     */
    @Override
    public int getMaxHeight() {
        return (int) mHeight;
    }

    private void updateSpring() {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        final float deltaT = (time - mStartTime) / 1000f; // Convert from millis to seconds
        if (deltaT < 0.001f) {
            return; // Must have at least 1 ms difference
        }
        mStartTime = time;

        if (Math.abs(mVelocity) <= LINEAR_VELOCITY_TAKE_OVER
                && Math.abs(mDistance * mHeight) < LINEAR_DISTANCE_TAKE_OVER
                && Math.signum(mVelocity) == -Math.signum(mDistance)
        ) {
            // This is close. The spring will slowly reach the destination. Instead, we
            // will interpolate linearly so that it arrives at its destination quicker.
            mVelocity = Math.signum(mVelocity) * LINEAR_VELOCITY_TAKE_OVER;

            float targetDistance = mDistance + (mVelocity * deltaT / mHeight);
            if (Math.signum(targetDistance) != Math.signum(mDistance)) {
                // We have arrived
                mDistance = 0;
                mVelocity = 0;
            } else {
                mDistance = targetDistance;
            }
            return;
        }
        final double mDampedFreq = NATURAL_FREQUENCY * Math.sqrt(1 - DAMPING_RATIO * DAMPING_RATIO);

        // We're always underdamped, so we can use only those equations:
        double cosCoeff = mDistance * mHeight;
        double sinCoeff = (1 / mDampedFreq) * (DAMPING_RATIO * NATURAL_FREQUENCY
                * mDistance * mHeight + mVelocity);
        double distance = Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (cosCoeff * Math.cos(mDampedFreq * deltaT)
                + sinCoeff * Math.sin(mDampedFreq * deltaT));
        double velocity = distance * (-NATURAL_FREQUENCY) * DAMPING_RATIO
                + Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (-mDampedFreq * cosCoeff * Math.sin(mDampedFreq * deltaT)
                + mDampedFreq * sinCoeff * Math.cos(mDampedFreq * deltaT));
        mDistance = (float) distance / mHeight;
        mVelocity = (float) velocity;
        if (mDistance > 1f) {
            mDistance = 1f;
            mVelocity = 0f;
        }
        if (isAtEquilibrium()) {
            mDistance = 0;
            mVelocity = 0;
        }
    }

    /**
     * @return true if the spring used for calculating the stretch animation is
     * considered at rest or false if it is still animating.
     */
    private boolean isAtEquilibrium() {
        double displacement = mDistance * mHeight; // in pixels
        double velocity = mVelocity;

        // Don't allow displacement to drop below 0. We don't want it stretching the opposite
        // direction if it is flung that way. We also want to stop the animation as soon as
        // it gets very close to its destination.
        return displacement < 0 || (Math.abs(velocity) < VELOCITY_THRESHOLD
                && displacement < VALUE_THRESHOLD);
    }

    private float dampStretchVector(float normalizedVec) {
        float sign = normalizedVec > 0 ? 1f : -1f;
        float overscroll = Math.abs(normalizedVec);
        float linearIntensity = LINEAR_STRETCH_INTENSITY * overscroll;
        double scalar = Math.E / SCROLL_DIST_AFFECTED_BY_EXP_STRETCH;
        double expIntensity = EXP_STRETCH_INTENSITY * (1 - Math.exp(-overscroll * scalar));
        return sign * (float) (linearIntensity + expIntensity);
    }
}
