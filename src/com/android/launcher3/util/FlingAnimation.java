package com.android.launcher3.util;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher3.DragLayer;
import com.android.launcher3.DragView;
import com.android.launcher3.DropTarget.DragObject;

public class FlingAnimation implements AnimatorUpdateListener {

    /**
     * Maximum acceleration in one dimension (pixels per milliseconds)
     */
    private static final float MAX_ACCELERATION = 0.5f;
    private static final int DRAG_END_DELAY = 300;

    protected final DragObject mDragObject;
    protected final Rect mIconRect;
    protected final DragLayer mDragLayer;
    protected final Rect mFrom;
    protected final int mDuration;
    protected final float mUX, mUY;
    protected final float mAnimationTimeFraction;
    protected final TimeInterpolator mAlphaInterpolator = new DecelerateInterpolator(0.75f);

    protected float mAX, mAY;

    /**
     * @param vel initial fling velocity in pixels per second.
     */
    public FlingAnimation(DragObject d, PointF vel, Rect iconRect, DragLayer dragLayer) {
        mDragObject = d;
        mUX = vel.x / 1000;
        mUY = vel.y / 1000;
        mIconRect = iconRect;

        mDragLayer = dragLayer;
        mFrom = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, mFrom);

        float scale = d.dragView.getScaleX();
        float xOffset = ((scale - 1f) * d.dragView.getMeasuredWidth()) / 2f;
        float yOffset = ((scale - 1f) * d.dragView.getMeasuredHeight()) / 2f;
        mFrom.left += xOffset;
        mFrom.right -= xOffset;
        mFrom.top += yOffset;
        mFrom.bottom -= yOffset;

        mDuration = initDuration();
        mAnimationTimeFraction = ((float) mDuration) / (mDuration + DRAG_END_DELAY);
    }

    /**
     * The fling animation is based on the following system
     *   - Apply a constant force in the y direction to causing the fling to decelerate.
     *   - The animation runs for the time taken by the object to go out of the screen.
     *   - Calculate a constant acceleration in x direction such that the object reaches
     *     {@link #mIconRect} in the given time.
     */
    protected int initDuration() {
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

    public final int getDuration() {
        return mDuration + DRAG_END_DELAY;
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
