/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.launcher3.touch;

import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.TouchController;

/**
 * TouchController that intercepts horizontal swipes when the merged app drawer is displayed,
 * delegating the swipe to Workspace so the user can swipe back to the previous home screen page.
 */
public class MergedAppDrawerTouchController implements TouchController {

    private final Launcher mLauncher;
    private final float mTouchSlop;
    private float mDownX;
    private float mDownY;
    private boolean mIsDragging;

    public MergedAppDrawerTouchController(Launcher launcher) {
        mLauncher = launcher;
        mTouchSlop = ViewConfiguration.get(launcher).getScaledTouchSlop();
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (!mLauncher.isMergeAppDrawerToWorkspace() || !mLauncher.isInState(LauncherState.NORMAL)) {
            mIsDragging = false;
            return false;
        }

        Workspace<?> workspace = mLauncher.getWorkspace();
        if (workspace == null) {
            mIsDragging = false;
            return false;
        }

        int mergedIndex = workspace.getMergedAppDrawerPageIndex();
        if (mergedIndex < 0 || workspace.getNextPage() != mergedIndex) {
            mIsDragging = false;
            return false;
        }

        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mDownX = ev.getX();
            mDownY = ev.getY();
            mIsDragging = false;
            return false;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (mIsDragging) {
                return true;
            }
            float dx = ev.getX() - mDownX;
            float dy = ev.getY() - mDownY;
            boolean isRtl = Utilities.isRtl(mLauncher.getResources());
            boolean isSwipeToPrev = isRtl ? (dx < -mTouchSlop) : (dx > mTouchSlop);

            if (isSwipeToPrev && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                mIsDragging = true;

                MotionEvent downEvent = MotionEvent.obtain(ev);
                downEvent.setAction(MotionEvent.ACTION_DOWN);
                downEvent.setLocation(mDownX, mDownY);
                workspace.onTouchEvent(downEvent);
                downEvent.recycle();

                workspace.onTouchEvent(ev);
                return true;
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mIsDragging = false;
        }

        return false;
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        if (!mIsDragging) {
            return false;
        }
        Workspace<?> workspace = mLauncher.getWorkspace();
        if (workspace == null) {
            mIsDragging = false;
            return false;
        }

        boolean handled = workspace.onTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mIsDragging = false;
        }
        return handled;
    }
}
