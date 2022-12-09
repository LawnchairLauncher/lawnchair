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

import static com.android.launcher3.LauncherAnimUtils.SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.LauncherAnimUtils.TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS;
import static com.android.launcher3.anim.Interpolators.scrollInterpolatorForVelocity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.touch.BaseSwipeDetector;
import com.android.launcher3.touch.SingleAxisSwipeDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extension of {@link AbstractFloatingView} with common methods for sliding in from bottom.
 *
 * @param <T> Type of ActivityContext inflating this view.
 */
public abstract class AbstractSlideInView<T extends Context & ActivityContext>
        extends AbstractFloatingView implements SingleAxisSwipeDetector.Listener {

    protected static final Property<AbstractSlideInView, Float> TRANSLATION_SHIFT =
            new Property<AbstractSlideInView, Float>(Float.class, "translationShift") {

                @Override
                public Float get(AbstractSlideInView view) {
                    return view.mTranslationShift;
                }

                @Override
                public void set(AbstractSlideInView view, Float value) {
                    view.setTranslationShift(value);
                }
            };
    protected static final float TRANSLATION_SHIFT_CLOSED = 1f;
    protected static final float TRANSLATION_SHIFT_OPENED = 0f;

    protected final T mActivityContext;

    protected final SingleAxisSwipeDetector mSwipeDetector;
    protected final ObjectAnimator mOpenCloseAnimator;

    protected ViewGroup mContent;
    protected final View mColorScrim;
    protected Interpolator mScrollInterpolator;

    // range [0, 1], 0=> completely open, 1=> completely closed
    protected float mTranslationShift = TRANSLATION_SHIFT_CLOSED;

    protected boolean mNoIntercept;
    protected @Nullable OnCloseListener mOnCloseBeginListener;
    protected List<OnCloseListener> mOnCloseListeners = new ArrayList<>();

    public AbstractSlideInView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);

        mScrollInterpolator = Interpolators.SCROLL_CUBIC;
        mSwipeDetector = new SingleAxisSwipeDetector(context, this,
                SingleAxisSwipeDetector.VERTICAL);

        mOpenCloseAnimator = ObjectAnimator.ofPropertyValuesHolder(this);
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mSwipeDetector.finishedScrolling();
                announceAccessibilityChanges();
            }
        });
        int scrimColor = getScrimColor(context);
        mColorScrim = scrimColor != -1 ? createColorScrim(context, scrimColor) : null;
    }

    protected void attachToContainer() {
        if (mColorScrim != null) {
            getPopupContainer().addView(mColorScrim);
        }
        getPopupContainer().addView(this);
    }

    /**
     * Returns a scrim color for a sliding view. if returned value is -1, no scrim is added.
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
        if (mColorScrim != null) {
            mColorScrim.setAlpha(1 - mTranslationShift);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mNoIntercept) {
            return false;
        }

        int directionsToDetectScroll = mSwipeDetector.isIdleState()
                ? SingleAxisSwipeDetector.DIRECTION_NEGATIVE : 0;
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

    /**
     * Returns {@code true} if the touch event is over the visible area of the bottom sheet.
     *
     * By default will check if the touch event is over {@code mContent}, subclasses should override
     * this method if the visible area of the bottom sheet is different from {@code mContent}.
     */
    protected boolean isEventOverContent(MotionEvent ev) {
        return getPopupContainer().isEventOverView(mContent, ev);
    }

    private boolean isOpeningAnimationRunning() {
        return mIsOpen && mOpenCloseAnimator.isRunning();
    }

    /* SingleAxisSwipeDetector.Listener */

    @Override
    public void onDragStart(boolean start, float startDisplacement) { }

    @Override
    public boolean onDrag(float displacement) {
        float range = getShiftRange();
        displacement = Utilities.boundToRange(displacement, 0, range);
        setTranslationShift(displacement / range);
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        float successfulShiftThreshold = mActivityContext.getDeviceProfile().isTablet
                ? TABLET_BOTTOM_SHEET_SUCCESS_TRANSITION_PROGRESS : SUCCESS_TRANSITION_PROGRESS;
        if ((mSwipeDetector.isFling(velocity) && velocity > 0)
                || mTranslationShift > successfulShiftThreshold) {
            mScrollInterpolator = scrollInterpolatorForVelocity(velocity);
            mOpenCloseAnimator.setDuration(BaseSwipeDetector.calculateDuration(
                    velocity, TRANSLATION_SHIFT_CLOSED - mTranslationShift));
            close(true);
        } else {
            mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(
                    TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
            mOpenCloseAnimator.setDuration(
                    BaseSwipeDetector.calculateDuration(velocity, mTranslationShift))
                    .setInterpolator(Interpolators.DEACCEL);
            mOpenCloseAnimator.start();
        }
    }

    /** Callback invoked when the view is beginning to close (e.g. close animation is started). */
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
            mOpenCloseAnimator.cancel();
            setTranslationShift(TRANSLATION_SHIFT_CLOSED);
            onCloseComplete();
            return;
        }
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_CLOSED));
        mOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator.removeListener(this);
                onCloseComplete();
            }
        });
        if (mSwipeDetector.isIdleState()) {
            mOpenCloseAnimator
                    .setDuration(defaultDuration)
                    .setInterpolator(getIdleInterpolator());
        } else {
            mOpenCloseAnimator.setInterpolator(mScrollInterpolator);
        }
        mOpenCloseAnimator.start();
    }

    protected Interpolator getIdleInterpolator() {
        return Interpolators.ACCEL;
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
