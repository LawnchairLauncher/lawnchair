/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.common.split;

import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;

import static com.android.wm.shell.common.split.SplitLayout.RESTING_TOUCH_LAYER;

import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.view.DragEvent;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;

import com.android.wm.shell.common.SyncTransactionQueue;

/**
 * Holds and manages a single touchable surface. These are used in offscreen split layouts, where
 * we use them as a signal that the user wants to bring an offscreen app back onscreen.
 * <br>
 *                       Split root
 *                    /      |       \
 *         Stage root      Divider      Stage root
 *           /   \
 *      Task       *this class*
 *
 */
public class OffscreenTouchZone {
    private static final String TAG = "OffscreenTouchZone";

    /**
     * Whether this touch zone is on the top/left or the bottom/right screen edge.
     */
    private final boolean mIsTopLeft;
    /** The function that will be run when this zone is tapped. */
    private final Runnable mOnClickRunnable;

    private TouchInterceptLayer mInterceptLayer;
    private GestureDetector mGestureDetector;

    private final GestureDetector.SimpleOnGestureListener mTapDetector =
            new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    mOnClickRunnable.run();
                    return true;
                }
            };

    private final View.OnDragListener mDragListener = new View.OnDragListener() {
        @Override
        public boolean onDrag(View view, DragEvent dragEvent) {
            if (dragEvent.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                mOnClickRunnable.run();
            }
            return false;
        }
    };

    private final View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return mGestureDetector.onTouchEvent(motionEvent);
        }
    };

    /**
     * @param isTopLeft Whether the desired touch zone will be on the top/left or the bottom/right
     *                  screen edge.
     * @param runnable The function to run when the touch zone is tapped.
     */
    OffscreenTouchZone(boolean isTopLeft, Runnable runnable) {
        mIsTopLeft = isTopLeft;
        mOnClickRunnable = runnable;
    }

    /** Sets up a touch zone. */
    public void inflate(Context context, SurfaceControl rootLeash, TaskInfo rootTaskInfo) {
        mGestureDetector = new GestureDetector(context, mTapDetector);
        mInterceptLayer = new TouchInterceptLayer(TAG + (mIsTopLeft ? "TopLeft" : "BottomRight"));
        mInterceptLayer.inflate(rootLeash, rootTaskInfo);
        mInterceptLayer.setTouchListener(mTouchListener);
        mInterceptLayer.setDragListener(mDragListener);
    }

    /** Releases the touch zone when it's no longer needed. */
    void release(SurfaceControl.Transaction t) {
        if (mInterceptLayer != null) {
            mInterceptLayer.release();
            mInterceptLayer = null;
        }
    }

    /**
     * Returns {@code true} if this touch zone represents an offscreen app on the top/left edge of
     * the display, {@code false} for bottom/right.
     */
    public boolean isTopLeft() {
        return mIsTopLeft;
    }
}
