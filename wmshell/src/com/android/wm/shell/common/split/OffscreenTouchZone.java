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
    private SurfaceControlViewHost mViewHost;
    private SurfaceControl mLeash;
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
    public void inflate(Context context, Configuration config, SyncTransactionQueue syncQueue,
            SurfaceControl stageRoot) {
        View touchableView = new View(context);
        mGestureDetector = new GestureDetector(context, mTapDetector);
        touchableView.setOnTouchListener(new OffscreenTouchListener());

        // Set WM flags, tokens, and sizing on the touchable view. It will be the same size as its
        // parent, the stage root.
        // TODO (b/349828130): It's a bit wasteful to have the touch zone cover the whole app
        //  surface, even extending offscreen (keeps buffer active in memory), so can trim it down
        //  to the visible onscreen area in a future patch.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_INPUT_CONSUMER,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.token = new Binder();
        lp.setTitle(TAG);
        lp.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        touchableView.setLayoutParams(lp);
        touchableView.setOnDragListener(mDragListener);

        // Create a new leash under our stage leash.
        final SurfaceControl.Builder builder = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName(TAG + (mIsTopLeft ? "TopLeft" : "BottomRight"))
                .setCallsite("OffscreenTouchZone::init");
        builder.setParent(stageRoot);
        SurfaceControl leash = builder.build();
        mLeash = leash;

        // Create a ViewHost that will hold our view.
        WindowlessWindowManager wwm = new WindowlessWindowManager(config, leash, null);
        mViewHost = new SurfaceControlViewHost(context, context.getDisplay(), wwm,
                "SplitTouchZones");
        mViewHost.setView(touchableView, lp);

        // Create a transaction so that we can activate and reposition our surface.
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        // Set layer to maximum. We want this surface to be above the app layer, or else touches
        // will be blocked.
        t.setLayer(leash, RESTING_TOUCH_LAYER);
        // Leash starts off hidden, show it.
        t.show(leash);
        syncQueue.runInSync(transaction -> {
            transaction.merge(t);
            t.close();
        });
    }

    /** Releases the touch zone when it's no longer needed. */
    void release(SurfaceControl.Transaction t) {
        if (mViewHost != null) {
            mViewHost.release();
        }
        if (mLeash != null) {
            t.remove(mLeash);
            mLeash = null;
        }
    }

    /**
     * Listens for touch events.
     */
    private class OffscreenTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            return mGestureDetector.onTouchEvent(motionEvent);
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
