/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.icons.cache;

import android.os.Handler;

/**
 * A runnable that can be posted to a {@link Handler} which can be canceled.
 */
public abstract class HandlerRunnable implements Runnable {

    private final Handler mHandler;
    private final Runnable mEndRunnable;

    private boolean mEnded = false;
    private boolean mCanceled = false;

    public HandlerRunnable(Handler handler, Runnable endRunnable) {
        mHandler = handler;
        mEndRunnable = endRunnable;
    }

    /**
     * Cancels this runnable from being run, only if it has not already run.
     */
    public void cancel() {
        mHandler.removeCallbacks(this);
        // TODO: This can actually cause onEnd to be called twice if the handler is already running
        //       this runnable
        // NOTE: This is currently run on whichever thread the caller is run on.
        mCanceled = true;
        onEnd();
    }

    /**
     * @return whether this runnable was canceled.
     */
    protected boolean isCanceled() {
        return mCanceled;
    }

    /**
     * To be called by the implemention of this runnable. The end callback is done on whichever
     * thread the caller is calling from.
     */
    public void onEnd() {
        if (!mEnded) {
            mEnded = true;
            if (mEndRunnable != null) {
                mEndRunnable.run();
            }
        }
    }
}
