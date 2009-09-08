/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff;
import android.os.Vibrator;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

/**
 * A ViewGroup that coordinated dragging across its dscendants
 */
public class DragLayer extends FrameLayout {
    private static final String TAG = "Launcher.DragLayer";

    private static final int DRAG = 1;
    private static final int SWIPE = 2;
    private static final int BOTH = DRAG | SWIPE;

    DragController mDragController;
    SwipeController mSwipeController;

    private int mAllowed = BOTH;


    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDragController(DragController controller) {
        mDragController = controller;
    }
    
    public void setSwipeController(SwipeController controller) {
        mSwipeController = controller;
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean result = false;

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mAllowed = BOTH;
        }

        if ((mAllowed & DRAG) != 0) {
            result = mDragController.onInterceptTouchEvent(ev);
            if (result) {
                mAllowed = DRAG;
            }
        }

        if ((mAllowed & SWIPE) != 0) {
            result = mSwipeController.onInterceptTouchEvent(ev);
            if (result) {
                mAllowed = SWIPE;
            }
        }

        return result;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = false;

        if ((mAllowed & DRAG) != 0) {
            result = mDragController.onTouchEvent(ev);
            if (result) {
                mAllowed = DRAG;
            }
        }

        if ((mAllowed & SWIPE) != 0) {
            result = mSwipeController.onTouchEvent(ev);
            if (result) {
                mAllowed = SWIPE;
            }
        }

        return result;
    }
}
