/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.back;

import static android.view.WindowManager.TRANSIT_OLD_UNSET;

import android.annotation.NonNull;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.IBackAnimationRunner;
import android.window.IOnBackInvokedCallback;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.Cuj.CujType;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.lang.ref.WeakReference;

/**
 * Used to register the animation callback and runner, it will trigger result if gesture was finish
 * before it received IBackAnimationRunner#onAnimationStart, so the controller could continue
 * trigger the real back behavior.
 */
public class BackAnimationRunner {
    private static final int NO_CUJ = -1;
    private static final String TAG = "ShellBackPreview";

    private final IOnBackInvokedCallback mCallback;
    private final IRemoteAnimationRunner mRunner;
    private final @CujType int mCujType;
    private final Context mContext;
    @ShellMainThread
    private final Handler mHandler;

    // Whether we are waiting to receive onAnimationStart
    private boolean mWaitingAnimation;

    /** True when the back animation is cancelled */
    private boolean mAnimationCancelled;

    public BackAnimationRunner(
            @NonNull IOnBackInvokedCallback callback,
            @NonNull IRemoteAnimationRunner runner,
            @NonNull Context context,
            @CujType int cujType,
            @ShellMainThread Handler handler) {
        mCallback = callback;
        mRunner = runner;
        mCujType = cujType;
        mContext = context;
        mHandler = handler;
    }

    public BackAnimationRunner(
            @NonNull IOnBackInvokedCallback callback,
            @NonNull IRemoteAnimationRunner runner,
            @NonNull Context context,
            @ShellMainThread Handler handler
    ) {
        this(callback, runner, context, NO_CUJ, handler);
    }

    /**
     * @deprecated Use {@link BackAnimationRunner} constructor providing an handler for the ui
     * thread of the animation.
     */
    @Deprecated
    public BackAnimationRunner(
            @NonNull IOnBackInvokedCallback callback,
            @NonNull IRemoteAnimationRunner runner,
            @NonNull Context context
    ) {
        this(callback, runner, context, NO_CUJ, context.getMainThreadHandler());
    }

    /** Returns the registered animation runner */
    IRemoteAnimationRunner getRunner() {
        return mRunner;
    }

    /** Returns the registered animation callback */
    IOnBackInvokedCallback getCallback() {
        return mCallback;
    }

    private Runnable mFinishedCallback;
    private RemoteAnimationTarget[] mApps;
    private RemoteAnimationFinishedStub mRemoteCallback;

    private static class RemoteAnimationFinishedStub extends IRemoteAnimationFinishedCallback.Stub {
        //the binder callback should not hold strong reference to it to avoid memory leak.
        private final WeakReference<BackAnimationRunner> mRunnerRef;
        private boolean mAbandoned;

        private RemoteAnimationFinishedStub(BackAnimationRunner runner) {
            mRunnerRef = new WeakReference<>(runner);
        }

        @Override
        public void onAnimationFinished() {
            synchronized (this) {
                if (mAbandoned) {
                    return;
                }
            }
            final BackAnimationRunner runner = mRunnerRef.get();
            if (runner == null) {
                return;
            }
            runner.onAnimationFinish(this);
        }

        void abandon() {
            synchronized (this) {
                mAbandoned = true;
                final BackAnimationRunner runner = mRunnerRef.get();
                if (runner == null) {
                    return;
                }
                if (runner.shouldMonitorCUJ(runner.mApps)) {
                    InteractionJankMonitor.getInstance().end(runner.mCujType);
                }
            }
        }
    }

    /**
     * Called from {@link IBackAnimationRunner}, it will deliver these
     * {@link RemoteAnimationTarget}s to the corresponding runner.
     */
    void startAnimation(RemoteAnimationTarget[] apps, RemoteAnimationTarget[] wallpapers,
            RemoteAnimationTarget[] nonApps, Runnable finishedCallback) {
        if (mRemoteCallback != null) {
            mRemoteCallback.abandon();
            mRemoteCallback = null;
        }
        mRemoteCallback = new RemoteAnimationFinishedStub(this);
        mFinishedCallback = finishedCallback;
        mApps = apps;
        mWaitingAnimation = false;
        if (shouldMonitorCUJ(apps)) {
            InteractionJankMonitor.getInstance().begin(
                    apps[0].leash, mContext, mHandler, mCujType);
        }
        try {
            getRunner().onAnimationStart(TRANSIT_OLD_UNSET, apps, wallpapers,
                    nonApps, mRemoteCallback);
        } catch (RemoteException e) {
            Log.w(TAG, "Failed call onAnimationStart", e);
        }
    }

    void onAnimationFinish(RemoteAnimationFinishedStub finished) {
        mHandler.post(() -> {
            if (mRemoteCallback != null && finished != mRemoteCallback) {
                return;
            }
            if (shouldMonitorCUJ(mApps)) {
                InteractionJankMonitor.getInstance().end(mCujType);
            }

            mFinishedCallback.run();
            for (int i = mApps.length - 1; i >= 0; --i) {
                final SurfaceControl sc = mApps[i].leash;
                if (sc != null && sc.isValid()) {
                    sc.release();
                }
            }
            mApps = null;
            mFinishedCallback = null;
            mRemoteCallback = null;
        });
    }

    @VisibleForTesting
    boolean shouldMonitorCUJ(RemoteAnimationTarget[] apps) {
        return apps.length > 0 && mCujType != NO_CUJ;
    }

    void startGesture() {
        mWaitingAnimation = true;
        mAnimationCancelled = false;
    }

    boolean isWaitingAnimation() {
        return mWaitingAnimation;
    }

    void cancelAnimation() {
        mWaitingAnimation = false;
        mAnimationCancelled = true;
    }

    boolean isAnimationCancelled() {
        return mAnimationCancelled;
    }

    void resetWaitingAnimation() {
        mWaitingAnimation = false;
    }
}
