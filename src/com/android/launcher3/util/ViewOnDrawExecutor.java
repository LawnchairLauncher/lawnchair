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

import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnDrawListener;

import androidx.annotation.NonNull;

import com.android.launcher3.Launcher;

import java.util.function.Consumer;

/**
 * An executor which runs all the tasks after the first onDraw is called on the target view.
 */
public class ViewOnDrawExecutor implements OnDrawListener, Runnable,
        OnAttachStateChangeListener {

    private final RunnableList mTasks;
    private final Consumer<ViewOnDrawExecutor> mOnClearCallback;
    private View mAttachedView;
    private boolean mCompleted;

    private boolean mFirstDrawCompleted;

    private boolean mCancelled;

    public ViewOnDrawExecutor(RunnableList tasks,
            @NonNull Consumer<ViewOnDrawExecutor> onClearCallback) {
        mTasks = tasks;
        mOnClearCallback = onClearCallback;
    }

    public void attachTo(Launcher launcher) {
        mAttachedView = launcher.getWorkspace();
        mAttachedView.addOnAttachStateChangeListener(this);
        if (mAttachedView.isAttachedToWindow()) {
            attachObserver();
        }
    }

    private void attachObserver() {
        if (!mCompleted) {
            mAttachedView.getViewTreeObserver().addOnDrawListener(this);
            mAttachedView.getRootView().invalidate();
        }
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        attachObserver();
    }

    @Override
    public void onViewDetachedFromWindow(View v) {}

    @Override
    public void onDraw() {
        mFirstDrawCompleted = true;
        mAttachedView.post(this);
    }

    @Override
    public void run() {
        // Post the pending tasks after first draw
        if (mFirstDrawCompleted && !mCompleted) {
            markCompleted();
        }
    }

    /**
     * Executes all tasks immediately
     */
    public void markCompleted() {
        if (!mCancelled) {
            mTasks.executeAllAndDestroy();
        }
        mCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.getViewTreeObserver().removeOnDrawListener(this);
            mAttachedView.removeOnAttachStateChangeListener(this);
        }

        mOnClearCallback.accept(this);
    }

    public void cancel() {
        mCancelled = true;
        markCompleted();
    }
}
