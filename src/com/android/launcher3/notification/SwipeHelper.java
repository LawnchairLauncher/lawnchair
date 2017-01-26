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

package com.android.launcher3.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.RectF;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import com.android.launcher3.R;

import java.util.HashMap;

/**
 * This class was copied from com.android.systemui.
 */
public class SwipeHelper {
    static final String TAG = "SwipeHelper";
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_INVALIDATE = false;
    private static final boolean SLOW_ANIMATIONS = false; // DEBUG;
    private static final boolean CONSTRAIN_SWIPE = true;
    private static final boolean FADE_OUT_DURING_SWIPE = true;
    private static final boolean DISMISS_IF_SWIPED_FAR_ENOUGH = true;

    public static final int X = 0;
    public static final int Y = 1;

    private float SWIPE_ESCAPE_VELOCITY = 100f; // dp/sec
    private int DEFAULT_ESCAPE_ANIMATION_DURATION = 200; // ms
    private int MAX_ESCAPE_ANIMATION_DURATION = 400; // ms
    private int MAX_DISMISS_VELOCITY = 4000; // dp/sec
    private static final int SNAP_ANIM_LEN = SLOW_ANIMATIONS ? 1000 : 150; // ms

    static final float SWIPE_PROGRESS_FADE_END = 0.5f; // fraction of thumbnail width
                                                // beyond which swipe progress->0
    private float mMinSwipeProgress = 0f;
    private float mMaxSwipeProgress = 1f;

    private FlingAnimationUtils mFlingAnimationUtils;
    private float mPagingTouchSlop;
    private Callback mCallback;
    private Handler mHandler;
    private int mSwipeDirection;
    private VelocityTracker mVelocityTracker;

    private float mInitialTouchPos;
    private float mPerpendicularInitialTouchPos;
    private boolean mDragging;
    private boolean mSnappingChild;
    private View mCurrView;
    private boolean mCanCurrViewBeDimissed;
    private float mDensityScale;
    private float mTranslation = 0;

    private boolean mLongPressSent;
    private LongPressListener mLongPressListener;
    private Runnable mWatchLongPress;
    private long mLongPressTimeout;

    final private int[] mTmpPos = new int[2];
    private int mFalsingThreshold;
    private boolean mTouchAboveFalsingThreshold;
    private boolean mDisableHwLayers;

    private HashMap<View, Animator> mDismissPendingMap = new HashMap<>();

    public SwipeHelper(int swipeDirection, Callback callback, Context context) {
        mCallback = callback;
        mHandler = new Handler();
        mSwipeDirection = swipeDirection;
        mVelocityTracker = VelocityTracker.obtain();
        mDensityScale =  context.getResources().getDisplayMetrics().density;
        mPagingTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();

        mLongPressTimeout = (long) (ViewConfiguration.getLongPressTimeout() * 1.5f); // extra long-press!
        mFalsingThreshold = context.getResources().getDimensionPixelSize(
                R.dimen.swipe_helper_falsing_threshold);
        mFlingAnimationUtils = new FlingAnimationUtils(context, getMaxEscapeAnimDuration() / 1000f);
    }

    public void setLongPressListener(LongPressListener listener) {
        mLongPressListener = listener;
    }

    public void setDensityScale(float densityScale) {
        mDensityScale = densityScale;
    }

    public void setPagingTouchSlop(float pagingTouchSlop) {
        mPagingTouchSlop = pagingTouchSlop;
    }

    public void setDisableHardwareLayers(boolean disableHwLayers) {
        mDisableHwLayers = disableHwLayers;
    }

    private float getPos(MotionEvent ev) {
        return mSwipeDirection == X ? ev.getX() : ev.getY();
    }

    private float getPerpendicularPos(MotionEvent ev) {
        return mSwipeDirection == X ? ev.getY() : ev.getX();
    }

    protected float getTranslation(View v) {
        return mSwipeDirection == X ? v.getTranslationX() : v.getTranslationY();
    }

    private float getVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getXVelocity() :
                vt.getYVelocity();
    }

    protected ObjectAnimator createTranslationAnimation(View v, float newPos) {
        ObjectAnimator anim = ObjectAnimator.ofFloat(v,
                mSwipeDirection == X ? View.TRANSLATION_X : View.TRANSLATION_Y, newPos);
        return anim;
    }

    private float getPerpendicularVelocity(VelocityTracker vt) {
        return mSwipeDirection == X ? vt.getYVelocity() :
                vt.getXVelocity();
    }

    protected Animator getViewTranslationAnimator(View v, float target,
            AnimatorUpdateListener listener) {
        ObjectAnimator anim = createTranslationAnimation(v, target);
        if (listener != null) {
            anim.addUpdateListener(listener);
        }
        return anim;
    }

    protected void setTranslation(View v, float translate) {
        if (v == null) {
            return;
        }
        if (mSwipeDirection == X) {
            v.setTranslationX(translate);
        } else {
            v.setTranslationY(translate);
        }
    }

    protected float getSize(View v) {
        return mSwipeDirection == X ? v.getMeasuredWidth() :
                v.getMeasuredHeight();
    }

    public void setMinSwipeProgress(float minSwipeProgress) {
        mMinSwipeProgress = minSwipeProgress;
    }

    public void setMaxSwipeProgress(float maxSwipeProgress) {
        mMaxSwipeProgress = maxSwipeProgress;
    }

    private float getSwipeProgressForOffset(View view, float translation) {
        float viewSize = getSize(view);
        float result = Math.abs(translation / viewSize);
        return Math.min(Math.max(mMinSwipeProgress, result), mMaxSwipeProgress);
    }

    private float getSwipeAlpha(float progress) {
        return Math.min(0, Math.max(1, progress / SWIPE_PROGRESS_FADE_END));
    }

    private void updateSwipeProgressFromOffset(View animView, boolean dismissable) {
        updateSwipeProgressFromOffset(animView, dismissable, getTranslation(animView));
    }

    private void updateSwipeProgressFromOffset(View animView, boolean dismissable,
            float translation) {
        float swipeProgress = getSwipeProgressForOffset(animView, translation);
        if (!mCallback.updateSwipeProgress(animView, dismissable, swipeProgress)) {
            if (FADE_OUT_DURING_SWIPE && dismissable) {
                float alpha = swipeProgress;
                if (!mDisableHwLayers) {
                    if (alpha != 0f && alpha != 1f) {
                        animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    } else {
                        animView.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }
                animView.setAlpha(getSwipeAlpha(swipeProgress));
            }
        }
        invalidateGlobalRegion(animView);
    }

    // invalidate the view's own bounds all the way up the view hierarchy
    public static void invalidateGlobalRegion(View view) {
        invalidateGlobalRegion(
                view,
                new RectF(view.getLeft(), view.getTop(), view.getRight(), view.getBottom()));
    }

    // invalidate a rectangle relative to the view's coordinate system all the way up the view
    // hierarchy
    public static void invalidateGlobalRegion(View view, RectF childBounds) {
        //childBounds.offset(view.getTranslationX(), view.getTranslationY());
        if (DEBUG_INVALIDATE)
            Log.v(TAG, "-------------");
        while (view.getParent() != null && view.getParent() instanceof View) {
            view = (View) view.getParent();
            view.getMatrix().mapRect(childBounds);
            view.invalidate((int) Math.floor(childBounds.left),
                    (int) Math.floor(childBounds.top),
                    (int) Math.ceil(childBounds.right),
                    (int) Math.ceil(childBounds.bottom));
            if (DEBUG_INVALIDATE) {
                Log.v(TAG, "INVALIDATE(" + (int) Math.floor(childBounds.left)
                        + "," + (int) Math.floor(childBounds.top)
                        + "," + (int) Math.ceil(childBounds.right)
                        + "," + (int) Math.ceil(childBounds.bottom));
            }
        }
    }

    public void removeLongPressCallback() {
        if (mWatchLongPress != null) {
            mHandler.removeCallbacks(mWatchLongPress);
            mWatchLongPress = null;
        }
    }

    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        final int action = ev.getAction();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTouchAboveFalsingThreshold = false;
                mDragging = false;
                mSnappingChild = false;
                mLongPressSent = false;
                mVelocityTracker.clear();
                mCurrView = mCallback.getChildAtPosition(ev);

                if (mCurrView != null) {
                    onDownUpdate(mCurrView);
                    mCanCurrViewBeDimissed = mCallback.canChildBeDismissed(mCurrView);
                    mVelocityTracker.addMovement(ev);
                    mInitialTouchPos = getPos(ev);
                    mPerpendicularInitialTouchPos = getPerpendicularPos(ev);
                    mTranslation = getTranslation(mCurrView);
                    if (mLongPressListener != null) {
                        if (mWatchLongPress == null) {
                            mWatchLongPress = new Runnable() {
                                @Override
                                public void run() {
                                    if (mCurrView != null && !mLongPressSent) {
                                        mLongPressSent = true;
                                        mCurrView.sendAccessibilityEvent(
                                                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                                        mCurrView.getLocationOnScreen(mTmpPos);
                                        final int x = (int) ev.getRawX() - mTmpPos[0];
                                        final int y = (int) ev.getRawY() - mTmpPos[1];
                                        mLongPressListener.onLongPress(mCurrView, x, y);
                                    }
                                }
                            };
                        }
                        mHandler.postDelayed(mWatchLongPress, mLongPressTimeout);
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null && !mLongPressSent) {
                    mVelocityTracker.addMovement(ev);
                    float pos = getPos(ev);
                    float perpendicularPos = getPerpendicularPos(ev);
                    float delta = pos - mInitialTouchPos;
                    float deltaPerpendicular = perpendicularPos - mPerpendicularInitialTouchPos;
                    if (Math.abs(delta) > mPagingTouchSlop
                            && Math.abs(delta) > Math.abs(deltaPerpendicular)) {
                        mCallback.onBeginDrag(mCurrView);
                        mDragging = true;
                        mInitialTouchPos = getPos(ev);
                        mTranslation = getTranslation(mCurrView);
                        removeLongPressCallback();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                final boolean captured = (mDragging || mLongPressSent);
                mDragging = false;
                mCurrView = null;
                mLongPressSent = false;
                removeLongPressCallback();
                if (captured) return true;
                break;
        }
        return mDragging || mLongPressSent;
    }

    /**
     * @param view The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should move
     * @param useAccelerateInterpolator Should an accelerating Interpolator be used
     */
    public void dismissChild(final View view, float velocity, boolean useAccelerateInterpolator) {
        dismissChild(view, velocity, null /* endAction */, 0 /* delay */,
                useAccelerateInterpolator, 0 /* fixedDuration */, false /* isDismissAll */);
    }

    /**
     * @param animView The view to be dismissed
     * @param velocity The desired pixels/second speed at which the view should move
     * @param endAction The action to perform at the end
     * @param delay The delay after which we should start
     * @param useAccelerateInterpolator Should an accelerating Interpolator be used
     * @param fixedDuration If not 0, this exact duration will be taken
     */
    public void dismissChild(final View animView, float velocity, final Runnable endAction,
            long delay, boolean useAccelerateInterpolator, long fixedDuration,
            boolean isDismissAll) {
        final boolean canBeDismissed = mCallback.canChildBeDismissed(animView);
        float newPos;
        boolean isLayoutRtl = animView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        // if we use the Menu to dismiss an item in landscape, animate up
        boolean animateUpForMenu = velocity == 0 && (getTranslation(animView) == 0 || isDismissAll)
                && mSwipeDirection == Y;
        // if the language is rtl we prefer swiping to the left
        boolean animateLeftForRtl = velocity == 0 && (getTranslation(animView) == 0 || isDismissAll)
                && isLayoutRtl;
        boolean animateLeft = velocity < 0
                || (velocity == 0 && getTranslation(animView) < 0 && !isDismissAll);

        if (animateLeft || animateLeftForRtl || animateUpForMenu) {
            newPos = -getSize(animView);
        } else {
            newPos = getSize(animView);
        }
        long duration;
        if (fixedDuration == 0) {
            duration = MAX_ESCAPE_ANIMATION_DURATION;
            if (velocity != 0) {
                duration = Math.min(duration,
                        (int) (Math.abs(newPos - getTranslation(animView)) * 1000f / Math
                                .abs(velocity))
                );
            } else {
                duration = DEFAULT_ESCAPE_ANIMATION_DURATION;
            }
        } else {
            duration = fixedDuration;
        }

        if (!mDisableHwLayers) {
            animView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        AnimatorUpdateListener updateListener = new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                onTranslationUpdate(animView, (float) animation.getAnimatedValue(), canBeDismissed);
            }
        };

        Animator anim = getViewTranslationAnimator(animView, newPos, updateListener);
        if (anim == null) {
            return;
        }
        if (useAccelerateInterpolator) {
            anim.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
            anim.setDuration(duration);
        } else {
            mFlingAnimationUtils.applyDismissing(anim, getTranslation(animView),
                    newPos, velocity, getSize(animView));
        }
        if (delay > 0) {
            anim.setStartDelay(delay);
        }
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled;

            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
            }

            public void onAnimationEnd(Animator animation) {
                updateSwipeProgressFromOffset(animView, canBeDismissed);
                mDismissPendingMap.remove(animView);
                if (!mCancelled) {
                    mCallback.onChildDismissed(animView);
                }
                if (endAction != null) {
                    endAction.run();
                }
                if (!mDisableHwLayers) {
                    animView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            }
        });

        prepareDismissAnimation(animView, anim);
        mDismissPendingMap.put(animView, anim);
        anim.start();
    }

    /**
     * Called to update the dismiss animation.
     */
    protected void prepareDismissAnimation(View view, Animator anim) {
        // Do nothing
    }

    public void snapChild(final View animView, final float targetLeft, float velocity) {
        final boolean canBeDismissed = mCallback.canChildBeDismissed(animView);
        AnimatorUpdateListener updateListener = new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                onTranslationUpdate(animView, (float) animation.getAnimatedValue(), canBeDismissed);
            }
        };

        Animator anim = getViewTranslationAnimator(animView, targetLeft, updateListener);
        if (anim == null) {
            return;
        }
        int duration = SNAP_ANIM_LEN;
        anim.setDuration(duration);
        anim.addListener(new AnimatorListenerAdapter() {
            public void onAnimationEnd(Animator animator) {
                mSnappingChild = false;
                updateSwipeProgressFromOffset(animView, canBeDismissed);
                mCallback.onChildSnappedBack(animView, targetLeft);
            }
        });
        prepareSnapBackAnimation(animView, anim);
        mSnappingChild = true;
        anim.start();
    }

    /**
     * Called to update the snap back animation.
     */
    protected void prepareSnapBackAnimation(View view, Animator anim) {
        // Do nothing
    }

    /**
     * Called when there's a down event.
     */
    public void onDownUpdate(View currView) {
        // Do nothing
    }

    /**
     * Called on a move event.
     */
    protected void onMoveUpdate(View view, float totalTranslation, float delta) {
        // Do nothing
    }

    /**
     * Called in {@link AnimatorUpdateListener#onAnimationUpdate(ValueAnimator)} when the current
     * view is being animated to dismiss or snap.
     */
    public void onTranslationUpdate(View animView, float value, boolean canBeDismissed) {
        updateSwipeProgressFromOffset(animView, canBeDismissed, value);
    }

    private void snapChildInstantly(final View view) {
        final boolean canAnimViewBeDismissed = mCallback.canChildBeDismissed(view);
        setTranslation(view, 0);
        updateSwipeProgressFromOffset(view, canAnimViewBeDismissed);
    }

    /**
     * Called when a view is updated to be non-dismissable, if the view was being dismissed before
     * the update this will handle snapping it back into place.
     *
     * @param view the view to snap if necessary.
     * @param animate whether to animate the snap or not.
     * @param targetLeft the target to snap to.
     */
    public void snapChildIfNeeded(final View view, boolean animate, float targetLeft) {
        if ((mDragging && mCurrView == view) || mSnappingChild) {
            return;
        }
        boolean needToSnap = false;
        Animator dismissPendingAnim = mDismissPendingMap.get(view);
        if (dismissPendingAnim != null) {
            needToSnap = true;
            dismissPendingAnim.cancel();
        } else if (getTranslation(view) != 0) {
            needToSnap = true;
        }
        if (needToSnap) {
            if (animate) {
                snapChild(view, targetLeft, 0.0f /* velocity */);
            } else {
                snapChildInstantly(view);
            }
        }
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (mLongPressSent) {
            return true;
        }

        if (!mDragging) {
            if (mCallback.getChildAtPosition(ev) != null) {

                // We are dragging directly over a card, make sure that we also catch the gesture
                // even if nobody else wants the touch event.
                onInterceptTouchEvent(ev);
                 return true;
            } else {

                // We are not doing anything, make sure the long press callback
                // is not still ticking like a bomb waiting to go off.
                removeLongPressCallback();
                return false;
            }
        }

        mVelocityTracker.addMovement(ev);
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (mCurrView != null) {
                    float delta = getPos(ev) - mInitialTouchPos;
                    float absDelta = Math.abs(delta);
                    if (absDelta >= getFalsingThreshold()) {
                        mTouchAboveFalsingThreshold = true;
                    }
                    // don't let items that can't be dismissed be dragged more than
                    // maxScrollDistance
                    if (CONSTRAIN_SWIPE && !mCallback.canChildBeDismissed(mCurrView)) {
                        float size = getSize(mCurrView);
                        float maxScrollDistance = 0.25f * size;
                        if (absDelta >= size) {
                            delta = delta > 0 ? maxScrollDistance : -maxScrollDistance;
                        } else {
                            delta = maxScrollDistance * (float) Math.sin((delta/size)*(Math.PI/2));
                        }
                    }

                    setTranslation(mCurrView, mTranslation + delta);
                    updateSwipeProgressFromOffset(mCurrView, mCanCurrViewBeDimissed);
                    onMoveUpdate(mCurrView, mTranslation + delta, delta);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mCurrView == null) {
                    break;
                }
                mVelocityTracker.computeCurrentVelocity(1000 /* px/sec */, getMaxVelocity());
                float velocity = getVelocity(mVelocityTracker);

                if (!handleUpEvent(ev, mCurrView, velocity, getTranslation(mCurrView))) {
                    if (isDismissGesture(ev)) {
                        // flingadingy
                        dismissChild(mCurrView, velocity,
                                !swipedFastEnough() /* useAccelerateInterpolator */);
                    } else {
                        // snappity
                        mCallback.onDragCancelled(mCurrView);
                        snapChild(mCurrView, 0 /* leftTarget */, velocity);
                    }
                    mCurrView = null;
                }
                mDragging = false;
                break;
        }
        return true;
    }

    private int getFalsingThreshold() {
        float factor = mCallback.getFalsingThresholdFactor();
        return (int) (mFalsingThreshold * factor);
    }

    private float getMaxVelocity() {
        return MAX_DISMISS_VELOCITY * mDensityScale;
    }

    protected float getEscapeVelocity() {
        return getUnscaledEscapeVelocity() * mDensityScale;
    }

    protected float getUnscaledEscapeVelocity() {
        return SWIPE_ESCAPE_VELOCITY;
    }

    protected long getMaxEscapeAnimDuration() {
        return MAX_ESCAPE_ANIMATION_DURATION;
    }

    protected boolean swipedFarEnough() {
        float translation = getTranslation(mCurrView);
        return DISMISS_IF_SWIPED_FAR_ENOUGH && Math.abs(translation) > 0.4 * getSize(mCurrView);
    }

    protected boolean isDismissGesture(MotionEvent ev) {
        boolean falsingDetected = mCallback.isAntiFalsingNeeded() && !mTouchAboveFalsingThreshold;
        return !falsingDetected && (swipedFastEnough() || swipedFarEnough())
                && ev.getActionMasked() == MotionEvent.ACTION_UP
                && mCallback.canChildBeDismissed(mCurrView);
    }

    protected boolean swipedFastEnough() {
        float velocity = getVelocity(mVelocityTracker);
        float translation = getTranslation(mCurrView);
        boolean ret = (Math.abs(velocity) > getEscapeVelocity())
                && (velocity > 0) == (translation > 0);
        return ret;
    }

    protected boolean handleUpEvent(MotionEvent ev, View animView, float velocity,
            float translation) {
        return false;
    }

    public interface Callback {
        View getChildAtPosition(MotionEvent ev);

        boolean canChildBeDismissed(View v);

        boolean isAntiFalsingNeeded();

        void onBeginDrag(View v);

        void onChildDismissed(View v);

        void onDragCancelled(View v);

        /**
         * Called when the child is snapped to a position.
         *
         * @param animView the view that was snapped.
         * @param targetLeft the left position the view was snapped to.
         */
        void onChildSnappedBack(View animView, float targetLeft);

        /**
         * Updates the swipe progress on a child.
         *
         * @return if true, prevents the default alpha fading.
         */
        boolean updateSwipeProgress(View animView, boolean dismissable, float swipeProgress);

        /**
         * @return The factor the falsing threshold should be multiplied with
         */
        float getFalsingThresholdFactor();
    }

    /**
     * Equivalent to View.OnLongClickListener with coordinates
     */
    public interface LongPressListener {
        /**
         * Equivalent to {@link View.OnLongClickListener#onLongClick(View)} with coordinates
         * @return whether the longpress was handled
         */
        boolean onLongPress(View v, int x, int y);
    }
}