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
package com.android.launcher3.graphics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnDrawListener;

import com.android.launcher3.Utilities;
import com.android.launcher3.icons.GraphicsUtils;

/**
 * Utility class to check bitmap creation during draw pass.
 */
public class BitmapCreationCheck {

    private static final String TAG = "BitmapCreationCheck";

    public static final boolean ENABLED = false;

    /**
     * Starts tracking bitmap creations during {@link View#draw(Canvas)} calls
     */
    public static void startTracking(Context context) {
        MyTracker tracker = new MyTracker();
        ((Application) context.getApplicationContext()).registerActivityLifecycleCallbacks(tracker);
        GraphicsUtils.sOnNewBitmapRunnable = tracker::onBitmapCreated;
    }

    @TargetApi(VERSION_CODES.Q)
    private static class MyTracker
            implements ActivityLifecycleCallbacks, OnAttachStateChangeListener {

        private final ThreadLocal<Boolean> mCurrentThreadDrawing =
                ThreadLocal.withInitial(() -> false);

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            activity.getWindow().getDecorView().addOnAttachStateChangeListener(this);
        }

        @Override
        public void onActivityStarted(Activity activity) { }

        @Override
        public void onActivityResumed(Activity activity) { }

        @Override
        public void onActivityPaused(Activity activity) { }

        @Override
        public void onActivityStopped(Activity activity) { }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

        @Override
        public void onActivityDestroyed(Activity activity) { }

        @Override
        public void onViewAttachedToWindow(View view) {
            view.getViewTreeObserver().addOnDrawListener(new MyViewDrawListener(view.getHandler()));
        }

        @Override
        public void onViewDetachedFromWindow(View view) { }

        private class MyViewDrawListener implements OnDrawListener, Runnable {

            private final Handler mHandler;

            MyViewDrawListener(Handler handler) {
                mHandler = handler;
            }

            @Override
            public void onDraw() {
                mCurrentThreadDrawing.set(true);
                Utilities.postAsyncCallback(mHandler, this);
            }

            @Override
            public void run() {
                mCurrentThreadDrawing.set(false);
            }
        }

        private void onBitmapCreated() {
            if (mCurrentThreadDrawing.get()) {
                Log.e(TAG, "Bitmap created during draw pass", new Exception());
            }
        }
    }

}
