/*
  Copyright (C) 2015 The Android Open Source Project

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package ch.deletescape.lawnchair.util;

import android.support.annotation.NonNull;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.ViewTreeObserver.OnDrawListener;

import java.util.ArrayList;
import java.util.concurrent.Executor;

import ch.deletescape.lawnchair.DeferredHandler;
import ch.deletescape.lawnchair.Launcher;

/**
 * An executor which runs all the tasks after the first onDraw is called on the target view.
 */
public class ViewOnDrawExecutor implements Executor, OnDrawListener, Runnable,
        OnAttachStateChangeListener {

    private final ArrayList<Runnable> mTasks = new ArrayList<>();
    private final DeferredHandler mHandler;

    private Launcher mLauncher;
    private View mAttachedView;
    private boolean mCompleted;

    private boolean mLoadAnimationCompleted;
    private boolean mFirstDrawCompleted;

    public ViewOnDrawExecutor(DeferredHandler handler) {
        mHandler = handler;
    }

    public void attachTo(Launcher launcher) {
        mLauncher = launcher;
        mAttachedView = launcher.getWorkspace();
        mAttachedView.addOnAttachStateChangeListener(this);

        attachObserver();
    }

    private void attachObserver() {
        if (!mCompleted) {
            mAttachedView.getViewTreeObserver().addOnDrawListener(this);
        }
    }

    @Override
    public void execute(@NonNull Runnable command) {
        mTasks.add(command);
    }

    @Override
    public void onViewAttachedToWindow(View v) {
        attachObserver();
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
    }

    @Override
    public void onDraw() {
        mFirstDrawCompleted = true;
        mAttachedView.post(this);
    }

    public void onLoadAnimationCompleted() {
        mLoadAnimationCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.post(this);
        }
    }

    @Override
    public void run() {
        // Post the pending tasks after both onDraw and onLoadAnimationCompleted have been called.
        if (mLoadAnimationCompleted && mFirstDrawCompleted && !mCompleted) {
            for (final Runnable r : mTasks) {
                mHandler.post(r);
            }
            markCompleted();
        }
    }

    public void markCompleted() {
        mTasks.clear();
        mCompleted = true;
        if (mAttachedView != null) {
            mAttachedView.getViewTreeObserver().removeOnDrawListener(this);
            mAttachedView.removeOnAttachStateChangeListener(this);
        }
        if (mLauncher != null) {
            mLauncher.clearPendingExecutor(this);
        }
    }
}
