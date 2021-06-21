/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import android.os.Handler;
import android.view.View;

import com.android.launcher3.Utilities;
import com.android.systemui.shared.system.ViewRootImplCompat;

import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

/**
 * Utility class for helpful methods related to {@link View} objects.
 */
public class ViewUtils {

    /** See {@link #postFrameDrawn(View, Runnable, BooleanSupplier)}} */
    public static boolean postFrameDrawn(View view, Runnable onFinishRunnable) {
        return postFrameDrawn(view, onFinishRunnable, () -> false);
    }

    /**
     * Inject some addition logic in order to make sure that the view is updated smoothly post
     * draw, and allow addition task to be run after view update.
     *
     * @param onFinishRunnable runnable to be run right after the view finishes drawing.
     */
    public static boolean postFrameDrawn(
            View view, Runnable onFinishRunnable, BooleanSupplier canceled) {
        return new FrameHandler(view, onFinishRunnable, canceled).schedule();
    }

    private static class FrameHandler implements LongConsumer {

        final ViewRootImplCompat mViewRoot;
        final Runnable mFinishCallback;
        final BooleanSupplier mCancelled;
        final Handler mHandler;

        int mDeferFrameCount = 1;

        FrameHandler(View view, Runnable finishCallback, BooleanSupplier cancelled) {
            mViewRoot = new ViewRootImplCompat(view);
            mFinishCallback = finishCallback;
            mCancelled = cancelled;
            mHandler = new Handler();
        }

        @Override
        public void accept(long l) {
            Utilities.postAsyncCallback(mHandler, this::onFrame);
        }

        private void onFrame() {
            if (mCancelled.getAsBoolean()) {
                return;
            }

            if (mDeferFrameCount > 0) {
                mDeferFrameCount--;
                schedule();
                return;
            }

            if (mFinishCallback != null) {
                mFinishCallback.run();
            }
        }

        private boolean schedule() {
            if (mViewRoot.isValid()) {
                mViewRoot.registerRtFrameCallback(this);
                mViewRoot.getView().invalidate();
                return true;
            }
            return false;
        }
    }
}
