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
package com.android.launcher3.views;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.allapps.AllAppsTransitionController.REVERT_SWIPE_ALL_APPS_TO_HOME_ANIMATION_DURATION_MS;
import static com.android.launcher3.util.ScrollableLayoutManager.PREDICTIVE_BACK_MIN_SCALE;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.window.BackEvent;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RequiresApi;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.SingleAxisSwipeDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extension of {@link AbstractFloatingView} with common methods for sliding in
 * from bottom.
 *
 * @param <T> Type of ActivityContext inflating this view.
 */
public abstract class AbstractSlideInView<T extends Context & ActivityContext>
        extends AbstractFloatingView implements SingleAxisSwipeDetector.Listener {

    protected static final FloatProperty<AbstractSlideInView<?>> TRANSLATION_SHIFT = new FloatProperty<>(
            "translationShift") {

        @Override
        public Float get(AbstractSlideInView view) {
            return view.mTranslationShift;
        }

        @Override
        public void setValue(AbstractSlideInView view, float value) {
            view.setTranslationShift(value);
        }
    };
    protected static final float TRANSLATION_SHIFT_CLOSED = 1f;
    protected static final float TRANSLATION_SHIFT_OPENED = 0f;
    private static final int DEFAULT_DURATION = 300;

    protected final T mActivityContext;

    protected final SingleAxisSwipeDetector mSwipeDetector;
    protected @NonNull AnimatorPlaybackController mOpenCloseAnimation;

    protected ViewGroup mContent;
    protected final @Nullable View mColorScrim;

    /**
     * Interpolator for {@link #mOpenCloseAnimation} when we are closing due to
     * dragging downwards.
     */
    private Interpolator mScrollInterpolator;
    private long mScrollDuration;
    /**
     * End progress for {@link #mOpenCloseAnimation} when we are closing due to
     * dragging downloads.
     * <p>
     * There are two cases that determine this value:
     * <ol>
     * <li>
     * If the drag interrupts the opening transition (i.e.
     * {@link #mToTranslationShift}
     * is {@link #TRANSLATION_SHIFT_OPENED}), we need to animate back to {@code 0}
     * to
     * reverse the animation that was paused at
     * {@link #onDragStart(boolean, float)}.
     * </li>
     * <li>
     * If the drag started after the view is fully opened (i.e.
     * {@link #mToTranslationShift} is {@link #TRANSLATION_SHIFT_CLOSED}), the
     * animation
     * that was set up at {@link #onDragStart(boolean, float)} for closing the view
     * should go forward to {@code 1}.
     * </li>
     * </ol>
     */
    private float mScrollEndProgress;

    // range [0, 1], 0=> completely open, 1=> completely closed
    protected float mTranslationShift = TRANSLATION_SHIFT_CLOSED;
    protected float mFromTranslationShift;
    protected float mToTranslationShift;
    /**
     * {@link #mOpenCloseAnimation} progress at
     * {@link #onDragStart(boolean, float)}.
     */
    private float mDragStartProgress;

    protected boolean mNoIntercept;
    protected @Nullable OnCloseListener mOnCloseBeginListener;
    protected List<OnCloseListener> mOnCloseListeners = new ArrayList<>();

    /**
     * How far through a "user initiated dismissal" the UI is. e.g. Predictive back,
     * swipe to home,
     * 0 is regular state, 1 is fully dismissed.
     */
    protected final AnimatedFloat mSwipeToDismissProgress = new AnimatedFloat(this::onUserSwipeToDismissProgressChanged,
            0f);
    protected boolean mIsDismissInProgress;
    private View mViewToAnimateInSwipeToDismiss = this;
    private @Nullable Drawable mContentBackground;
    private @Nullable View mContentBackgroundParentView;

    protected final ViewOutlineProvider mViewOutlineProvider = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            outline.setRect(
                    0,
                    0,
                    view.getMeasuredWidth(),
                    view.getMeasuredHeight() + getBottomOffsetPx());
        }
    };

    public AbstractSlideInView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);

        mScrollInterpolator = Interpolators.SCROLL_CUBIC;
        mScrollDuration = DEFAULT_DURATION;
        mSwipeDetector = new SingleAxisSwipeDetector(context, this,
                SingleAxisSwipeDetector.VERTICAL);

        mOpenCloseAnimation = new PendingAnimation(0).createPlaybackController();

        int scrimColor = getScrimColor(context);
        mColorScrim = scrimColor != -1 ? createColorScrim(context, scrimColor) : null;
    }

    /**
     * Sets up a {@link #mOpenCloseAnimation} for opening with default parameters.
     *
     * @see #setUpOpenCloseAnimation(float, float, long)
     */
    protected final AnimatorPlaybackController setUpDefaultOpenAnimation() {
        AnimatorPlaybackController animation = setUpOpenCloseAnimation(
                TRANSLATION_SHIFT_CLOSED, TRANSLATION_SHIFT_OPENED, DEFAULT_DURATION);
        animation.getAnimationPlayer().setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        return animation;
    }

    /**
     * Sets up a {@link #mOpenCloseAnimation} for opening with a given duration.
     *
     * @see #setUpOpenCloseAnimation(float, float, long)
     */
    protected final AnimatorPlaybackController setUpOpenAnimation(long duration) {
        return setUpOpenCloseAnimation(
                TRANSLATION_SHIFT_CLOSED, TRANSLATION_SHIFT_OPENED, duration);
    }

    private AnimatorPlaybackController setUpCloseAnimation(long duration) {
        return setUpOpenCloseAnimation(
                TRANSLATION_SHIFT_OPENED, TRANSLATION_SHIFT_CLOSED, duration);
    }

    /**
     * Initializes a new {@link #mOpenCloseAnimation}.
     *
     * @param fromTranslationShift translation shift to animate from.
     * @param toTranslationShift   translation shift to animate to.
     * @param duration             animation duration.
     * @return {@link #mOpenCloseAnimation}
     */
    private AnimatorPlaybackController setUpOpenCloseAnimation(
            float fromTranslationShift, float toTranslationShift, long duration) {
        mFromTranslationShift = fromTranslationShift;
        mToTranslationShift = toTranslationShift;

        PendingAnimation animation = new PendingAnimation(duration);
        animation.addEndListener(b -> {
            mSwipeDetector.finishedScrolling();
            announceAccessibilityChanges();
        });

        animation.addFloat(
                this, TRANSLATION_SHIFT, fromTranslationShift, toTranslationShift, LINEAR);
        if (mColorScrim != null) {
            animation.setViewAlpha(mColorScrim, 1 - toTranslationShift, getScrimInterpolator());
        }
        onOpenCloseAnimationPending(animation);

        mOpenCloseAnimation = animation.createPlaybackController();
        return mOpenCloseAnimation;
    }

    /**
     * Invoked when a {@link #mOpenCloseAnimation} is being set up.
     * <p>
     * Subclasses can override this method to modify the animation before it's used
     * to create a
     * {@link AnimatorPlaybackController}.
     */
    protected void onOpenCloseAnimationPending(PendingAnimation animation) {
    }

    protected void attachToContainer() {
        if (mColorScrim != null) {
            getPopupContainer().addView(mColorScrim);
        }
        getPopupContainer().addView(this);
    }

    /**
     * Returns a scrim color for a sliding view. if returned value is -1, no scrim
     * is added.
     */
    protected int getScrimColor(Context context) {
        return -1;
    }

    /**
     * Returns the range in height that the slide in view can be dragged.
     */
    protected float getShiftRange() {
        return mContent.getHeight();
    }

    protected void setTranslationShift(float translationShift) {
        mTranslationShift = translationShift;
        mContent.setTranslationY(mTranslationShift * getShiftRange());
        invalidate();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mNoIntercept) {
            return false;
        }

        int directionsToDetectScroll = mSwipeDetector.isIdleState()
                ? SingleAxisSwipeDetector.DIRECTION_NEGATIVE
                : 0;
        mSwipeDetector.setDetectableScrollConditions(
                directionsToDetectScroll, false);
        mSwipeDetector.onTouchEvent(ev);
        return mSwipeDetector.isDraggingOrSettling() || !isEventOverContent(ev);
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        mSwipeDetector.onTouchEvent(ev);
        if (ev.getAction() == MotionEvent.ACTION_UP && mSwipeDetector.isIdleState()
                && !isOpeningAnimationRunning()) {
            // If we got ACTION_UP without ever starting swipe, close the panel.
            if (!isEventOverContent(ev)) {
                close(true);
            }
        }
        return true;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackStarted(BackEvent backEvent) {
        super.onBackStarted(backEvent);
        mViewToAnimateInSwipeToDismiss = shouldAnimateContentViewInBackSwipe() ? mContent : this;
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void onBackProgressed(BackEvent backEvent) {
        final float progress = backEvent.getProgress();
        float deceleratedProgress = Interpolators.BACK_GESTURE.getInterpolation(progress);
        mSwipeToDismissProgress.updateValue(deceleratedProgress);
    }

    /**
     * During predictive back swipe, the default behavior is to scale
     * {@link AbstractSlideInView}
     * during back swipe. This method allow subclass to scale {@link #mContent},
     * typically to exit
     * search mode.
     *
     * <p>
     * Note that this method can be expensive, and should only be called from
     * {@link #onBackStarted(BackEvent)}, not from
     * {@link #onBackProgressed(BackEvent)}.
     */
    protected boolean shouldAnimateContentViewInBackSwipe() {
        return false;
    }

    protected void onUserSwipeToDismissProgressChanged() {
        float progress = mSwipeToDismissProgress.value;
        mIsDismissInProgress = progress > 0f;

        float scale = PREDICTIVE_BACK_MIN_SCALE + (1 - PREDICTIVE_BACK_MIN_SCALE) * (1f - progress);
        SCALE_PROPERTY.set(mViewToAnimateInSwipeToDismiss, scale);
        setClipChildren(!mIsDismissInProgress);
        setClipToPadding(!mIsDismissInProgress);
        mContent.setClipChildren(!mIsDismissInProgress);
        mContent.setClipToPadding(!mIsDismissInProgress);
        invalidate();
    }

    @Override
    public void onBackCancelled() {
        super.onBackCancelled();
        animateSwipeToDismissProgressToStart();
    }

    protected void animateSwipeToDismissProgressToStart() {
        ObjectAnimator objectAnimator = mSwipeToDismissProgress.animateToValue(0f)
                .setDuration(REVERT_SWIPE_ALL_APPS_TO_HOME_ANIMATION_DURATION_MS);

        // If we are animating a different view, we should reset the animating view back
        // to
        // AbstractSlideInView as it is the default view to animate.
        if (this != mViewToAnimateInSwipeToDismiss) {
            objectAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationCancel(Animator animator) {
                    mViewToAnimateInSwipeToDismiss = AbstractSlideInView.this;
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    mViewToAnimateInSwipeToDismiss = AbstractSlideInView.this;
                }

                @Override
                public void onAnimationRepeat(Animator animator) {
                }

                @Override
                public void onAnimationStart(Animator animator) {
                }
            });
        }

        objectAnimator.start();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawScaledBackground(canvas);
        super.dispatchDraw(canvas);
    }

    /**
     * Set slide in view's background {@link Drawable} which will be draw onto a
     * parent view in
     * {@link #dispatchDraw(Canvas)}
     */
    protected void setContentBackgroundWithParent(
            @NonNull Drawable drawable, @NonNull View parentView) {
        mContentBackground = drawable;
        mContentBackgroundParentView = parentView;
    }

    /** Draw scaled background during predictive back animation. */
    private void drawScaledBackground(Canvas canvas) {
        if (mContentBackground == null || mContentBackgroundParentView == null) {
            return;
        }
        mContentBackground.setBounds(
                mContentBackgroundParentView.getLeft(),
                mContentBackgroundParentView.getTop() + (int) mContent.getTranslationY(),
                mContentBackgroundParentView.getRight(),
                mContentBackgroundParentView.getBottom()
                        + (mIsDismissInProgress ? getBottomOffsetPx() : 0));
        mContentBackground.draw(canvas);
    }

    /** Return extra space revealed during predictive back animation. */
    @Px
    protected int getBottomOffsetPx() {
        return (int) (getMeasuredHeight() * (1 - PREDICTIVE_BACK_MIN_SCALE));
    }

    /**
     * Returns {@code true} if the touch event is over the visible area of the
     * bottom sheet.
     *
     * By default will check if the touch event is over {@code mContent}, subclasses
     * should override
     * this method if the visible area of the bottom sheet is different from
     * {@code mContent}.
     */
    protected boolean isEventOverContent(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mContent, ev);
    }

    private boolean isOpeningAnimationRunning() {
        return mIsOpen && mOpenCloseAnimation.getAnimationPlayer().isRunning();
    }

    /* SingleAxisSwipeDetector.Listener */

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        if (mOpenCloseAnimation.getAnimationPlayer().isRunning()) {
            mOpenCloseAnimation.pause();
            mDragStartProgress = mOpenCloseAnimation.getProgressFraction();
        } else {
            setUpCloseAnimation(DEFAULT_DURATION);
            mDragStartProgress = 0;
        }
    }

    @Override
    public boolean onDrag(float displacement) {
        float progress = mDragStartProgress
                + Math.signum(mToTranslationShift - mFromTranslationShift)
                        * (displacement / getShiftRange());
        mOpenCloseAnimation.setPlayFraction(Utilities.boundToRange(progress, 0, 1));
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        float successfulShiftThreshold = mActivityContext.getDeviceProfile().isTablet
                ? TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS
                : SUCCESS_TRANSITION_PROGRESS;
        if ((mSwipeDetector.isFling(velocity) && velocity > 0)
                || mTranslationShift > successfulShiftThreshold) {
            mScrollInterpolator = scrollInterpolatorForVelocity(velocity);
            mScrollDuration = BaseSwipeDetector.calculateDuration(
                    velocity, TRANSLATION_SHIFT_CLOSED - mTranslationShift);
            mScrollEndProgress = mToTranslationShift == TRANSLATION_SHIFT_OPENED ? 0 : 1;
            close(true);
        } else {
            ValueAnimator animator = mOpenCloseAnimation.getAnimationPlayer();
            animator.setInterpolator(Interpolators.DECELERATE);
            animator.setFloatValues(
                    mOpenCloseAnimation.getProgressFraction(),
                    mToTranslationShift == TRANSLATION_SHIFT_OPENED ? 1 : 0);
            animator.setDuration(BaseSwipeDetector.calculateDuration(velocity, mTranslationShift))
                    .start();
        }
    }

    /**
     * Callback invoked when the view is beginning to close (e.g. close animation is
     * started).
     */
    public void setOnCloseBeginListener(@Nullable OnCloseListener onCloseBeginListener) {
        mOnCloseBeginListener = onCloseBeginListener;
    }

    /** Registers an {@link OnCloseListener}. */
    public void addOnCloseListener(OnCloseListener listener) {
        mOnCloseListeners.add(listener);
    }

    protected void handleClose(boolean animate, long defaultDuration) {
        if (!mIsOpen) {
            return;
        }
        Optional.ofNullable(mOnCloseBeginListener).ifPresent(OnCloseListener::onSlideInViewClosed);

        if (!animate) {
            mOpenCloseAnimation.pause();
            setTranslationShift(TRANSLATION_SHIFT_CLOSED);
            onCloseComplete();
            return;
        }

        final ValueAnimator animator;
        if (mSwipeDetector.isIdleState()) {
            setUpCloseAnimation(defaultDuration);
            animator = mOpenCloseAnimation.getAnimationPlayer();
            animator.setInterpolator(getIdleInterpolator());
        } else {
            animator = mOpenCloseAnimation.getAnimationPlayer();
            animator.setInterpolator(mScrollInterpolator);
            animator.setDuration(mScrollDuration);
            mOpenCloseAnimation.getAnimationPlayer().setFloatValues(
                    mOpenCloseAnimation.getProgressFraction(), mScrollEndProgress);
        }

        animator.addListener(AnimatorListeners.forEndCallback(this::onCloseComplete));
        animator.start();
    }

    protected Interpolator getIdleInterpolator() {
        return Interpolators.ACCELERATE;
    }

    protected Interpolator getScrimInterpolator() {
        return LINEAR;
    }

    protected void onCloseComplete() {
        mIsOpen = false;
        getPopupContainer().removeView(this);
        if (mColorScrim != null) {
            getPopupContainer().removeView(mColorScrim);
        }
        mOnCloseListeners.forEach(OnCloseListener::onSlideInViewClosed);
    }

    protected BaseDragLayer getPopupContainer() {
        return mActivityContext.getDragLayer();
    }

    protected View createColorScrim(Context context, int bgColor) {
        View view = new View(context);
        view.forceHasOverlappingRendering(false);
        view.setBackgroundColor(bgColor);

        BaseDragLayer.LayoutParams lp = new BaseDragLayer.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        lp.ignoreInsets = true;
        view.setLayoutParams(lp);

        return view;
    }

    /**
     * Interface to report that the {@link AbstractSlideInView} has closed.
     */
    public interface OnCloseListener {

        /**
         * Called when {@link AbstractSlideInView} closes.
         */
        void onSlideInViewClosed();
    }
}
