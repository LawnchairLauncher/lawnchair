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
package com.android.systemui.shared.recents.view;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.AppTransitionAnimationSpec;
import android.view.IAppTransitionAnimationSpecsFuture;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * To be implemented by a particular animation to asynchronously provide the animation specs for a
 * particular transition.
 */
public abstract class AppTransitionAnimationSpecsFuture {

    private final Handler mHandler;
    private FutureTask<List<AppTransitionAnimationSpecCompat>> mComposeTask = new FutureTask<>(
            new Callable<List<AppTransitionAnimationSpecCompat>>() {
                @Override
                public List<AppTransitionAnimationSpecCompat> call() throws Exception {
                    return composeSpecs();
                }
            });

    private final IAppTransitionAnimationSpecsFuture mFuture =
            new IAppTransitionAnimationSpecsFuture.Stub() {
        @Override
        public AppTransitionAnimationSpec[] get() throws RemoteException {
            try {
                if (!mComposeTask.isDone()) {
                    mHandler.post(mComposeTask);
                }
                List<AppTransitionAnimationSpecCompat> specs = mComposeTask.get();
                // Clear reference to the compose task this future holds onto the reference to it's
                // implementation (which can leak references to the bitmap it creates for the
                // transition)
                mComposeTask = null;
                if (specs == null) {
                    return null;
                }

                AppTransitionAnimationSpec[] arr = new AppTransitionAnimationSpec[specs.size()];
                for (int i = 0; i < specs.size(); i++) {
                    arr[i] = specs.get(i).toAppTransitionAnimationSpec();
                }
                return arr;
            } catch (Exception e) {
                return null;
            }
        }
    };

    public AppTransitionAnimationSpecsFuture(Handler handler) {
        mHandler = handler;
    }

    /**
     * Returns the future to handle the call from window manager.
     */
    public final IAppTransitionAnimationSpecsFuture getFuture() {
        return mFuture;
    }

    /**
     * Called ahead of the future callback to compose the specs to be returned in the future.
     */
    public final void composeSpecsSynchronous() {
        if (Looper.myLooper() != mHandler.getLooper()) {
            throw new RuntimeException("composeSpecsSynchronous() called from wrong looper");
        }
        mComposeTask.run();
    }

    public abstract List<AppTransitionAnimationSpecCompat> composeSpecs();
}
