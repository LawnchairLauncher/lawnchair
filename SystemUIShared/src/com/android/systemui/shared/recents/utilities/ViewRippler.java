/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.systemui.shared.recents.utilities;

import android.view.View;

/**
 * Shows view ripples by toggling the provided Views "pressed" state.
 * Ripples 4 times.
 */
public class ViewRippler {
    private static final int RIPPLE_OFFSET_MS = 50;
    private static final int RIPPLE_INTERVAL_MS = 2000;
    private View mRoot;

    public void start(View root) {
        stop(); // Stop any pending ripple animations

        mRoot = root;

        // Schedule pending ripples, offset the 1st to avoid problems with visibility change
        mRoot.postOnAnimationDelayed(mRipple, RIPPLE_OFFSET_MS);
        mRoot.postOnAnimationDelayed(mRipple, RIPPLE_INTERVAL_MS);
        mRoot.postOnAnimationDelayed(mRipple, 2 * RIPPLE_INTERVAL_MS);
        mRoot.postOnAnimationDelayed(mRipple, 3 * RIPPLE_INTERVAL_MS);
        mRoot.postOnAnimationDelayed(mRipple, 4 * RIPPLE_INTERVAL_MS);
    }

    public void stop() {
        if (mRoot != null) mRoot.removeCallbacks(mRipple);
    }

    private final Runnable mRipple = new Runnable() {
        @Override
        public void run() { // Cause the ripple to fire via false presses
            if (!mRoot.isAttachedToWindow()) return;
            mRoot.setPressed(true /* pressed */);
            mRoot.setPressed(false /* pressed */);
        }
    };
}