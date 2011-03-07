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

import com.android.launcher.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * A ViewGroup that coordinates dragging across its descendants
 */
public class DragLayer extends FrameLayout {
    private DragController mDragController;
    private int[] mTmpXY = new int[2];

    /**
     * Used to create a new DragLayer from XML.
     *
     * @param context The application's context.
     * @param attrs The attributes set containing the Workspace's customization values.
     */
    public DragLayer(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Disable multitouch across the workspace/all apps/customize tray
        setMotionEventSplittingEnabled(false);
    }

    public void setDragController(DragController controller) {
        mDragController = controller;
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragController.dispatchKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // If the current CellLayoutChildren has a resize frame, we need to detect if any touch
        // event has occurred which doesn't result in resizing a widget. In this case, we
        // dismiss any visible resize frames.
        final Workspace w = (Workspace) findViewById(R.id.workspace);
        if (w != null) {
            final CellLayout currentPage = (CellLayout) w.getChildAt(w.getCurrentPage());
            final CellLayoutChildren childrenLayout = currentPage.getChildrenLayout();

            if (childrenLayout.hasResizeFrames() && !childrenLayout.isWidgetBeingResized()) {
                post(new Runnable() {
                    public void run() {
                        if (!childrenLayout.isWidgetBeingResized()) {
                            childrenLayout.clearAllResizeFrames();
                        }
                    }
                });
            }
        }
        return mDragController.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mDragController.onTouchEvent(ev);
    }

    @Override
    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mDragController.dispatchUnhandledMove(focused, direction);
    }

    public View createDragView(Bitmap b, int xPos, int yPos) {
        ImageView imageView = new ImageView(mContext);
        imageView.setImageBitmap(b);
        imageView.setX(xPos);
        imageView.setY(yPos);
        addView(imageView, b.getWidth(), b.getHeight());

        return imageView;
    }

    public View createDragView(View v) {
        v.getLocationOnScreen(mTmpXY);
        return createDragView(mDragController.getViewBitmap(v), mTmpXY[0], mTmpXY[1]);
    }
}
