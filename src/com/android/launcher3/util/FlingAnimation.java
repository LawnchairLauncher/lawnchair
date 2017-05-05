package com.android.launcher3.util;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.ButtonDropTarget;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;

public class FlingAnimation implements AnimatorUpdateListener, Runnable {

    /**
     * Maximum acceleration in one dimension (pixels per milliseconds)
     */
    private static final float MAX_ACCELERATION = 0.5f;
    private static final int DRAG_END_DELAY = 300;

    private final ButtonDropTarget mDropTarget;
    private final Launcher mLauncher;

    protected final DragObject mDragObject;
    protected final DragLayer mDragLayer;
    protected final TimeInterpolator mAlphaInterpolator = new DecelerateInterpolator(0.75f);
    protected final float mUX, mUY;

    protected Rect mIconRect;
    protected Rect mFrom;
    protected int mDuration;
    protected float mAnimationTimeFraction;

    protected float mAX, mAY;

    public FlingAnimation(DragObject d, PointF vel, ButtonDropTarget dropTarget, Launcher launcher) {
        mDropTarget = dropTarget;
        mLauncher = launcher;
        mDragObject = d;
        mUX = vel.x / 1000;
        mUY = vel.y / 1000;
        mDragLayer = mLauncher.getDragLayer();
    }

    @Override
    public void run() {
        mIconRect = mDropTarget.getIconRect(mDragObject);

        // Initiate from
        mFrom = new Rect();
        mDragLayer.getViewRectRelativeToSelf(mDragObject.dragView, mFrom);
        float scale = mDragObject.dragView.getScaleX();
        float xOffset = ((scale - 1f) * mDragObject.dragView.getMeasuredWidth()) / 2f;
        float yOffset = ((scale - 1f) * mDragObject.dragView.getMeasuredHeight()) / 2f;
        mFrom.left += xOffset;
        mFrom.right -= xOffset;
        mFrom.top += yOffset;
        mFrom.bottom -= yOffset;
        mDuration = Math.abs(mUY) > Math.abs(mUX) ? initFlingUpDuration() : initFlingLeftDuration();

        mAnimationTimeFraction = ((float) mDuration) / (mDuration + DRAG_END_DELAY);

        // Don't highlight the icon as it's animating
        mDragObject.dragView.setColor(0);

        final int duration = mDuration + DRAG_END_DELAY;
        final long startTime = AnimationUtils.currentAnimationTimeMillis();

        // NOTE: Because it takes time for the first frame of animation to actually be
        // called and we expect the animation to be a continuation of the fling, we have
        // to account for the time that has elapsed since the fling finished.  And since
        // we don't have a startDelay, we will always get call to update when we call
        // start() (which we want to ignore).
        final TimeInterpolator tInterpolator = new TimeInterpolator() {
            private int mCount = -1;
            private float mOffset = 0f;

            @Override
            public float getInterpolation(float t) {
                if (mCount < 0) {
                    mCount++;
                } else if (mCount == 0) {
                    mOffset = Math.min(0.5f, (float) (AnimationUtils.currentAnimationTimeMillis() -
                            startTime) / duration);
                    mCount++;
                }
                return Math.min(1f, mOffset + t);
            }
        };

        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mLauncher.exitSpringLoadedDragMode();
                mDropTarget.completeDrop(mDragObject);
            }
        };

        mDragLayer.animateView(mDragObject.dragView, this, duration, tInterpolator,
                onAnimationEndRunnable, DragLayer.ANIMATION_END_DISAPPEAR, null);
    }

    /**
     * The fling animation is based on the following system
     *   - Apply a constant force in the y direction to causing the fling to decelerate.
     *   - The animation runs for the time taken by the object to go out of the screen.
     *   - Calculate a constant acceleration in x direction such that the object reaches
     *     {@link #mIconRect} in the given time.
     */
    protected int initFlingUpDuration() {
        float sY = -mFrom.bottom;

        float d = mUY * mUY + 2 * sY * MAX_ACCELERATION;
        if (d >= 0) {
            // sY can be reached under the MAX_ACCELERATION. Use MAX_ACCELERATION for y direction.
            mAY = MAX_ACCELERATION;
        } else {
            // sY is not reachable, decrease the acceleration so that sY is almost reached.
            d = 0;
            mAY = mUY * mUY / (2 * -sY);
        }
        double t = (-mUY - Math.sqrt(d)) / mAY;

        float sX = -mFrom.exactCenterX() + mIconRect.exactCenterX();

        // Find horizontal acceleration such that: u*t + a*t*t/2 = s
        mAX = (float) ((sX - t * mUX) * 2 / (t * t));
        return (int) Math.round(t);
    }

    /**
     * The fling animation is based on the following system
     *   - Apply a constant force in the x direction to causing the fling to decelerate.
     *   - The animation runs for the time taken by the object to go out of the screen.
     *   - Calculate a constant acceleration in y direction such that the object reaches
     *     {@link #mIconRect} in the given time.
     */
    protected int initFlingLeftDuration() {
        float sX = -mFrom.right;

        float d = mUX * mUX + 2 * sX * MAX_ACCELERATION;
        if (d >= 0) {
            // sX can be reached under the MAX_ACCELERATION. Use MAX_ACCELERATION for x direction.
            mAX = MAX_ACCELERATION;
        } else {
            // sX is not reachable, decrease the acceleration so that sX is almost reached.
            d = 0;
            mAX = mUX * mUX / (2 * -sX);
        }
        double t = (-mUX - Math.sqrt(d)) / mAX;

        float sY = -mFrom.exactCenterY() + mIconRect.exactCenterY();

        // Find vertical acceleration such that: u*t + a*t*t/2 = s
        mAY = (float) ((sY - t * mUY) * 2 / (t * t));
        return (int) Math.round(t);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        float t = animation.getAnimatedFraction();
        if (t > mAnimationTimeFraction) {
            t = 1;
        } else {
            t = t / mAnimationTimeFraction;
        }
        final DragView dragView = (DragView) mDragLayer.getAnimatedView();
        final float time = t * mDuration;
        dragView.setTranslationX(time * mUX + mFrom.left + mAX * time * time / 2);
        dragView.setTranslationY(time * mUY + mFrom.top + mAY * time * time / 2);
        dragView.setAlpha(1f - mAlphaInterpolator.getInterpolation(t));
    }
}
