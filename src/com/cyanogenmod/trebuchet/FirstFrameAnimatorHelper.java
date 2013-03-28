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

package com.cyanogenmod.trebuchet;

import android.animation.ValueAnimator;
import android.animation.Animator.AnimatorListener;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.view.View;

/*
 *  This is a helper class that listens to updates from the corresponding animation.
 *  For the first two frames, it adjusts the current play time of the animation to
 *  prevent jank at the beginning of the animation
 */
public class FirstFrameAnimatorHelper implements ValueAnimator.AnimatorUpdateListener {
    private static final boolean DEBUG = false;
    private static final int MAX_FIRST_FRAME_DELAY = 200;
    private static final int IDEAL_FRAME_DURATION = 16;
    private View mTarget;
    private long mStartFrame;
    private long mStartTime = -1;
    private boolean mHandlingOnAnimationUpdate;
    private boolean mAdjustedSecondFrameTime;

    private static ViewTreeObserver.OnDrawListener sGlobalDrawListener;
    private static long sGlobalFrameCounter;

    public FirstFrameAnimatorHelper(ValueAnimator animator, View target) {
        mTarget = target;
        animator.addUpdateListener(this);
    }

    public static void initializeDrawListener(View view) {
        sGlobalDrawListener = new ViewTreeObserver.OnDrawListener() {
                private long mTime = System.currentTimeMillis();
                public void onDraw() {
                    sGlobalFrameCounter++;
                    long newTime = System.currentTimeMillis();
                    if (DEBUG) {
                        Log.d("FirstFrameAnimatorHelper", "TICK " + (newTime - mTime));
                    }
                    mTime = newTime;
                }
            };
        view.getViewTreeObserver().addOnDrawListener(sGlobalDrawListener);
    }

    public void onAnimationUpdate(final ValueAnimator animation) {
        if (mStartTime == -1) {
            mStartFrame = sGlobalFrameCounter;
            mStartTime = System.currentTimeMillis();
        }

        if (!mHandlingOnAnimationUpdate) {
            mHandlingOnAnimationUpdate = true;
            long frameNum = sGlobalFrameCounter - mStartFrame;

            // If we haven't drawn our first frame, reset the time to t = 0
            // (give up after 200ms of waiting though - might happen, for example, if we are no
            // longer in the foreground and no frames are being rendered ever)
            if (frameNum == 0 && System.currentTimeMillis() < mStartTime + MAX_FIRST_FRAME_DELAY) {
                mTarget.getRootView().invalidate(); // make sure we'll do a draw
                animation.setCurrentPlayTime(0);

            // For the second frame, if the first frame took more than 16ms,
            // adjust the start time and pretend it took only 16ms anyway. This
            // prevents a large jump in the animation due to an expensive first frame
            } else if (frameNum == 1 && !mAdjustedSecondFrameTime &&
                       System.currentTimeMillis() > mStartTime + 16) {
                animation.setCurrentPlayTime(IDEAL_FRAME_DURATION);
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
        Log.d("FirstFrameAnimatorHelper", sGlobalFrameCounter +
              "(" + (sGlobalFrameCounter - mStartFrame) + ") " + mTarget + " dirty? " +
              mTarget.isDirty() + " " + flatFraction + " " + this + " " + animation);
    }
}
