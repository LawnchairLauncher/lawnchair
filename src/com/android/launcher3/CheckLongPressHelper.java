/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.view.View;

import com.android.launcher3.util.Thunk;

public class CheckLongPressHelper {

    @Thunk View mView;
    @Thunk View.OnLongClickListener mListener;
    @Thunk boolean mHasPerformedLongPress;
    private int mLongPressTimeout = 300;
    private CheckForLongPress mPendingCheckForLongPress;

    class CheckForLongPress implements Runnable {
        public void run() {
            if ((mView.getParent() != null) && mView.hasWindowFocus()
                    && !mHasPerformedLongPress) {
                boolean handled;
                if (mListener != null) {
                    handled = mListener.onLongClick(mView);
                } else {
                    handled = mView.performLongClick();
                }
                if (handled) {
                    mView.setPressed(false);
                    mHasPerformedLongPress = true;
                }
            }
        }
    }

    public CheckLongPressHelper(View v) {
        mView = v;
    }

    public CheckLongPressHelper(View v, View.OnLongClickListener listener) {
        mView = v;
        mListener = listener;
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeout(int longPressTimeout) {
        mLongPressTimeout = longPressTimeout;
    }

    public void postCheckForLongPress() {
        mHasPerformedLongPress = false;

        if (mPendingCheckForLongPress == null) {
            mPendingCheckForLongPress = new CheckForLongPress();
        }
        mView.postDelayed(mPendingCheckForLongPress, mLongPressTimeout);
    }

    public void cancelLongPress() {
        mHasPerformedLongPress = false;
        if (mPendingCheckForLongPress != null) {
            mView.removeCallbacks(mPendingCheckForLongPress);
            mPendingCheckForLongPress = null;
        }
    }

    public boolean hasPerformedLongPress() {
        return mHasPerformedLongPress;
    }
}
