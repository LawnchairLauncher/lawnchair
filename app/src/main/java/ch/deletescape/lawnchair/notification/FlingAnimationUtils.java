package ch.deletescape.lawnchair.notification;

import android.animation.Animator;
import android.content.Context;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

public class FlingAnimationUtils {
    private AnimatorProperties mAnimatorProperties;
    private float mHighVelocityPxPerSecond;
    private float mMaxLengthSeconds;
    private float mMinVelocityPxPerSecond;

    class AnimatorProperties {
        long duration;
        Interpolator interpolator;

        private AnimatorProperties() {
        }
    }

    final class InterpolatorInterpolator implements Interpolator {
        private Interpolator mCrossfader;
        private Interpolator mInterpolator1;
        private Interpolator mInterpolator2;

        InterpolatorInterpolator(Interpolator interpolator, Interpolator interpolator2, Interpolator interpolator3) {
            mInterpolator1 = interpolator;
            mInterpolator2 = interpolator2;
            mCrossfader = interpolator3;
        }

        @Override
        public float getInterpolation(float f) {
            float interpolation = mCrossfader.getInterpolation(f);
            return (interpolation * mInterpolator2.getInterpolation(f)) + ((1.0f - interpolation) * mInterpolator1.getInterpolation(f));
        }
    }

    final class VelocityInterpolator implements Interpolator {
        private float mDiff;
        private float mDurationSeconds;
        private float mVelocity;

        private VelocityInterpolator(float f, float f2, float f3) {
            mDurationSeconds = f;
            mVelocity = f2;
            mDiff = f3;
        }

        @Override
        public float getInterpolation(float f) {
            return ((mDurationSeconds * f) * mVelocity) / mDiff;
        }
    }

    public FlingAnimationUtils(Context context, float f) {
        this(context, f, 0.0f);
    }

    public FlingAnimationUtils(Context context, float f, float f2) {
        this(context, f, f2, -1.0f, 1.0f);
    }

    public FlingAnimationUtils(Context context, float f, float f2, float f3, float f4) {
        mAnimatorProperties = new AnimatorProperties();
        mMaxLengthSeconds = f;
        if (f3 < 0.0f) {
        } else {
        }
        mMinVelocityPxPerSecond = context.getResources().getDisplayMetrics().density * 250.0f;
        mHighVelocityPxPerSecond = context.getResources().getDisplayMetrics().density * 3000.0f;
    }

    private static float interpolate(float f, float f2, float f3) {
        return ((1.0f - f3) * f) + (f2 * f3);
    }

    public void applyDismissing(Animator animator, float f, float f2, float f3, float f4) {
        AnimatorProperties dismissingProperties = getDismissingProperties(f, f2, f3, f4);
        animator.setDuration(dismissingProperties.duration);
        animator.setInterpolator(dismissingProperties.interpolator);
    }

    private AnimatorProperties getDismissingProperties(float f, float f2, float f3, float f4) {
        float pow = (float) (((double) mMaxLengthSeconds) * Math.pow((double) (Math.abs(f2 - f) / f4), 0.5d));
        float abs = Math.abs(f2 - f);
        float abs2 = Math.abs(f3);
        float calculateLinearOutFasterInY2 = calculateLinearOutFasterInY2(abs2);
        float f5 = calculateLinearOutFasterInY2 / 0.5f;
        Interpolator pathInterpolator = new PathInterpolator(0.0f, 0.0f, 0.5f, calculateLinearOutFasterInY2);
        calculateLinearOutFasterInY2 = (f5 * abs) / abs2;
        if (calculateLinearOutFasterInY2 <= pow) {
            mAnimatorProperties.interpolator = pathInterpolator;
        } else if (abs2 >= mMinVelocityPxPerSecond) {
            mAnimatorProperties.interpolator = new InterpolatorInterpolator(new VelocityInterpolator(pow, abs2, abs), pathInterpolator, Interpolators.LINEAR_OUT_SLOW_IN);
            calculateLinearOutFasterInY2 = pow;
        } else {
            mAnimatorProperties.interpolator = Interpolators.FAST_OUT_LINEAR_IN;
            calculateLinearOutFasterInY2 = pow;
        }
        mAnimatorProperties.duration = (long) (calculateLinearOutFasterInY2 * 1000.0f);
        return mAnimatorProperties;
    }

    private float calculateLinearOutFasterInY2(float f) {
        float max = Math.max(0.0f, Math.min(1.0f, (f - mMinVelocityPxPerSecond) / (mHighVelocityPxPerSecond - mMinVelocityPxPerSecond)));
        return (max * 0.5f) + ((1.0f - max) * 0.4f);
    }
}