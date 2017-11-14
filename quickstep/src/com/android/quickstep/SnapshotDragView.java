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
package com.android.quickstep;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.systemui.shared.recents.view.AnimateableViewBounds;

/**
 * Floating view which shows the task snapshot allowing it to be dragged and placed.
 */
public class SnapshotDragView extends AbstractFloatingView implements Insettable {

    private final Launcher mLauncher;
    private final Bitmap mSnapshot;
    private final AnimateableViewBounds mViewBounds;

    public SnapshotDragView(Launcher launcher, Bitmap snapshot) {
        super(launcher, null);
        mLauncher = launcher;
        mSnapshot = snapshot;
        mViewBounds = new AnimateableViewBounds(this, 0);
        setWillNotDraw(false);
        setClipToOutline(true);
        setOutlineProvider(mViewBounds);
    }

    AnimateableViewBounds getViewBounds() {
        return mViewBounds;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mSnapshot != null) {
            setMeasuredDimension(mSnapshot.getWidth(), mSnapshot.getHeight());
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    public void setInsets(Rect insets) {

    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mSnapshot != null) {
            canvas.drawBitmap(mSnapshot, 0, 0, null);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        // We dont suupport animate.
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    public void logActionCommand(int command) {
        // We should probably log the weather
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_QUICKSTEP_PREVIEW) != 0;
    }
}
