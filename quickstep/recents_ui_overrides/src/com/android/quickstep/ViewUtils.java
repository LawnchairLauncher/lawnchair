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

import android.graphics.Canvas;
import android.view.View;

import com.android.systemui.shared.system.WindowCallbacksCompat;

import java.util.function.BooleanSupplier;

/**
 * Utility class for helpful methods related to {@link View} objects.
 */
public class ViewUtils {

    /** See {@link #postDraw(View, Runnable, BooleanSupplier)}} */
    public static boolean postDraw(View view, Runnable onFinishRunnable) {
        return postDraw(view, onFinishRunnable, () -> false);
    }

    /**
     * Inject some addition logic in order to make sure that the view is updated smoothly post
     * draw, and allow addition task to be run after view update.
     *
     * @param onFinishRunnable runnable to be run right after the view finishes drawing.
     */
    public static boolean postDraw(View view, Runnable onFinishRunnable, BooleanSupplier canceled) {
        // Defer finishing the animation until the next launcher frame with the
        // new thumbnail
        return new WindowCallbacksCompat(view) {
            // The number of frames to defer until we actually finish the animation
            private int mDeferFrameCount = 2;

            @Override
            public void onPostDraw(Canvas canvas) {
                // If we were cancelled after this was attached, do not update
                // the state.
                if (canceled.getAsBoolean()) {
                    detach();
                    return;
                }

                if (mDeferFrameCount > 0) {
                    mDeferFrameCount--;
                    // Workaround, detach and reattach to invalidate the root node for
                    // another draw
                    detach();
                    attach();
                    view.invalidate();
                    return;
                }

                if (onFinishRunnable != null) {
                    onFinishRunnable.run();
                }
                detach();
            }
        }.attach();
    }
}
