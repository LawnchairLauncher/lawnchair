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

package com.android.launcher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;

/**
 * A convenience class for two-way animations, e.g. a fadeIn/fadeOut animation.
 * With a regular ValueAnimator, if you call reverse to show the 'out' animation, you'll get
 * a frame-by-frame mirror of the 'in' animation -- i.e., the interpolated values will
 * be exactly reversed. Using this class, both the 'in' and the 'out' animation use the
 * interpolator in the same direction.
 */
public class InterruptibleInOutAnimator extends ValueAnimator {
    private long mOriginalDuration;
    private Object mOriginalFromValue;
    private Object mOriginalToValue;

    private boolean mFirstRun = true;

    private Object mTag = null;

    public InterruptibleInOutAnimator(long duration, Object fromValue, Object toValue) {
        super(duration, fromValue, toValue);
        mOriginalDuration = duration;
        mOriginalFromValue = fromValue;
        mOriginalToValue = toValue;
    }

    private void animateTo(Object toValue) {
        // This only makes sense when it's running in the opposite direction, or stopped.
        setDuration(mOriginalDuration - getCurrentPlayTime());

        final Object startValue = mFirstRun ? mOriginalFromValue : getAnimatedValue();
        cancel();
        if (startValue != toValue) {
            setValues(startValue, toValue);
            start();
            mFirstRun = false;
        }
    }

    /**
     * This is the equivalent of calling Animator.start(), except that it can be called when
     * the animation is running in the opposite direction, in which case we reverse
     * direction and animate for a correspondingly shorter duration.
     */
    public void animateIn() {
        animateTo(mOriginalToValue);
    }

    /**
     * This is the roughly the equivalent of calling Animator.reverse(), except that it uses the
     * same interpolation curve as animateIn(), rather than mirroring it. Also, like animateIn(),
     * if the animation is currently running in the opposite direction, we reverse
     * direction and animate for a correspondingly shorter duration.
     */
    public void animateOut() {
        animateTo(mOriginalFromValue);
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    public Object getTag() {
        return mTag;
    }
}
