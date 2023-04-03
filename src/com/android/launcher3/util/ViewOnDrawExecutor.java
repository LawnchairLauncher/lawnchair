/**
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.util;

import android.util.Log;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnDrawListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.testing.shared.TestProtocol;

import java.util.function.Consumer;

/**
 * An executor which runs all the tasks after the first onDraw is called on the target view.
 */
public class ViewOnDrawExecutor implements OnDrawListener, Runnable,
        OnAttachStateChangeListener {

    private final RunnableList mTasks;

    private Consumer<ViewOnDrawExecutor> mOnClearCallback;
    private View mAttachedView;
    private boolean mCompleted;

    private boolean mLoadAnimationCompleted;
    private boolean mFirstDrawCompleted;

    private boolean mCancelled;

    public ViewOnDrawExecutor(RunnableList tasks) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING, "Initialize ViewOnDrawExecutor");
        }
        mTasks = tasks;
    }

    public void attachTo(Launcher launcher) {
        mOnClearCallback = launcher::clearPendingExecutor;
        mAttachedView = launcher.getWorkspace();

        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING, "ViewOnDrawExecutor.attachTo: launcher=" + launcher
                    + ", isAttachedToWindow=" + mAttachedView.isAttachedToWindow());
        }

        mAttachedView.addOnAttachStateChangeListener(this);

        if (mAttachedView.isAttachedToWindow()) {
            attachObserver();
        }
    }

    private void attachObserver() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING,
                    "ViewOnDrawExecutor.attachObserver: mCompleted=" + mCompleted);
        }
        if (!mCompleted) {
            mAttachedView.getViewTreeObserver().addOnDrawListener(this);
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING, "ViewOnDrawExecutor.onViewAttachedToWindow");
        }
        attachObserver();
    }

    @Override
    public void onViewDetachedFromWindow(View v) {}

    @Override
    public void onDraw() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING, "ViewOnDrawExecutor.onDraw");
        }
        mFirstDrawCompleted = true;
        mAttachedView.post(this);
    }

    public void onLoadAnimationCompleted() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING,
                    "ViewOnDrawExecutor.onLoadAnimationCompleted: mAttachedView != null="
                            + (mAttachedView != null));
        }
        mLoadAnimationCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.post(this);
        }
    }

    @Override
    public void run() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING,
                    "ViewOnDrawExecutor.run: mLoadAnimationCompleted=" + mLoadAnimationCompleted
                            + ", mFirstDrawCompleted=" + mFirstDrawCompleted
                            + ", mCompleted=" + mCompleted);
        }
        // Post the pending tasks after both onDraw and onLoadAnimationCompleted have been called.
        if (mLoadAnimationCompleted && mFirstDrawCompleted && !mCompleted) {
            markCompleted();
        }
    }

    /**
     * Executes all tasks immediately
     */
    public void markCompleted() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING,
                    "ViewOnDrawExecutor.markCompleted: mCancelled=" + mCancelled
                            + ", mOnClearCallback != null=" + (mOnClearCallback != null)
                            + ", mAttachedView != null=" + (mAttachedView != null));
        }
        if (!mCancelled) {
            mTasks.executeAllAndDestroy();
        }
        mCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.getViewTreeObserver().removeOnDrawListener(this);
            mAttachedView.removeOnAttachStateChangeListener(this);
        }
        if (mOnClearCallback != null) {
            mOnClearCallback.accept(this);
        }
    }

    public void cancel() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.FLAKY_BINDING, "ViewOnDrawExecutor.cancel");
        }
        mCancelled = true;
        markCompleted();
    }
}
