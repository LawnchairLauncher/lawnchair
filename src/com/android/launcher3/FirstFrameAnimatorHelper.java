/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import com.android.launcher3.util.Thunk;

import static com.android.launcher3.Utilities.SINGLE_FRAME_MS;

/*
 *  This is a helper class that listens to updates from the corresponding animation.
 *  For the first two frames, it adjusts the current play time of the animation to
 *  prevent jank at the beginning of the animation
 */
public class FirstFrameAnimatorHelper extends AnimatorListenerAdapter
    implements ValueAnimator.AnimatorUpdateListener {
    private static final String TAG = "FirstFrameAnimatorHlpr";
    private static final boolean DEBUG = false;
    private static final int MAX_DELAY = 1000;
    private final View mTarget;
    private long mStartFrame;
    private long mStartTime = -1;
    private boolean mHandlingOnAnimationUpdate;
    private boolean mAdjustedSecondFrameTime;

    private static ViewTreeObserver.OnDrawListener sGlobalDrawListener;
    @Thunk static long sGlobalFrameCounter;
    private static boolean sVisible;

    public FirstFrameAnimatorHelper(ValueAnimator animator, View target) {
        mTarget = target;
        animator.addUpdateListener(this);
    }

    public FirstFrameAnimatorHelper(ViewPropertyAnimator vpa, View target) {
        mTarget = target;
        vpa.setListener(this);
    }

    // only used for ViewPropertyAnimators
    public void onAnimationStart(Animator animation) {
        final ValueAnimator va = (ValueAnimator) animation;
        va.addUpdateListener(FirstFrameAnimatorHelper.this);
        onAnimationUpdate(va);
    }

    public static void setIsVisible(boolean visible) {
        sVisible = visible;
    }

    public static void initializeDrawListener(View view) {
        if (sGlobalDrawListener != null) {
            view.getViewTreeObserver().removeOnDrawListener(sGlobalDrawListener);
        }

        sGlobalDrawListener = () -> sGlobalFrameCounter++;
        view.getViewTreeObserver().addOnDrawListener(sGlobalDrawListener);
        sVisible = true;
    }

    public void onAnimationUpdate(final ValueAnimator animation) {
        final long currentTime = System.currentTimeMillis();
        if (mStartTime == -1) {
            mStartFrame = sGlobalFrameCounter;
            mStartTime = currentTime;
        }

        final long currentPlayTime = animation.getCurrentPlayTime();
        boolean isFinalFrame = Float.compare(1f, animation.getAnimatedFraction()) == 0;

        if (!mHandlingOnAnimationUpdate &&
            sVisible &&
            // If the current play time exceeds the duration, or the animated fraction is 1,
            // the animation will get finished, even if we call setCurrentPlayTime -- therefore
            // don't adjust the animation in that case
            currentPlayTime < animation.getDuration() && !isFinalFrame) {
            mHandlingOnAnimationUpdate = true;
            long frameNum = sGlobalFrameCounter - mStartFrame;
            // If we haven't drawn our first frame, reset the time to t = 0
            // (give up after MAX_DELAY ms of waiting though - might happen, for example, if we
            // are no longer in the foreground and no frames are being rendered ever)
            if (frameNum == 0 && currentTime < mStartTime + MAX_DELAY && currentPlayTime > 0) {
                // The first frame on animations doesn't always trigger an invalidate...
                // force an invalidate here to make sure the animation continues to advance
                mTarget.getRootView().invalidate();
                animation.setCurrentPlayTime(0);
            // For the second frame, if the first frame took more than 16ms,
            // adjust the start time and pretend it took only 16ms anyway. This
            // prevents a large jump in the animation due to an expensive first frame
            } else if (frameNum == 1 && currentTime < mStartTime + MAX_DELAY &&
                       !mAdjustedSecondFrameTime &&
                       currentTime > mStartTime + SINGLE_FRAME_MS &&
                       currentPlayTime > SINGLE_FRAME_MS) {
                animation.setCurrentPlayTime(SINGLE_FRAME_MS);
                mAdjustedSecondFrameTime = true;
            } else {
                if (frameNum > 1) {
                    mTarget.post(new Runnable() {
                            public void run() {
                                animation.removeUpdateListener(FirstFrameAnimatorHelper.this);
                            }
                        });
                }
                if (DEBUG) print(animation);
            }
            mHandlingOnAnimationUpdate = false;
        } else {
            if (DEBUG) print(animation);
        }
    }

    public void print(ValueAnimator animation) {
        float flatFraction = animation.getCurrentPlayTime() / (float) animation.getDuration();
        Log.d(TAG, sGlobalFrameCounter +
              "(" + (sGlobalFrameCounter - mStartFrame) + ") " + mTarget + " dirty? " +
              mTarget.isDirty() + " " + flatFraction + " " + this + " " + animation);
    }
}
