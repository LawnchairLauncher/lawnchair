/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;


/**
 * Implements a DropTarget which allows applications to be dropped on it,
 * in order to launch the application info for that app.
 */
public class IconDropTarget extends TextView implements DropTarget, DragController.DragListener {
    protected Launcher mLauncher;

    /**
     * If true, this View responsible for managing its own visibility, and that of its overlapping
     *  views. This is generally the case, but it will be set to false when this is part of the
     * Contextual Action Bar.
     */
    protected boolean mDragAndDropEnabled;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;

    /** The views that this view should appear in the place of. */
    protected View[] mOverlappingViews = null;

    /** The paint applied to the drag view on hover */
    protected final Paint mHoverPaint = new Paint();

    /** Drag zone padding [T, R, B, L] */
    protected final int mDragPadding[] = new int[4];

    public IconDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDragAndDropEnabled = true;
    }

    protected void setDragPadding(int t, int r, int b, int l) {
        mDragPadding[0] = t;
        mDragPadding[1] = r;
        mDragPadding[2] = b;
        mDragPadding[3] = l;
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    void setOverlappingView(View view) {
        mOverlappingViews = new View[] { view };
    }
    
    void setOverlappingViews(View[] views) {
        mOverlappingViews = views;
    }

    void setDragAndDropEnabled(boolean enabled) {
        mDragAndDropEnabled = enabled;
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return false;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        // Do nothing
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (mDragAndDropEnabled) {
            dragView.setPaint(mHoverPaint);
        }
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        // Do nothing
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        if (mDragAndDropEnabled) {
            dragView.setPaint(null);
        }
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        // Do nothing
    }

    public boolean isDropEnabled() {
        return mDragAndDropEnabled && mActive;
    }

    public void onDragEnd() {
        // Do nothing
    }

    @Override
    public void getHitRect(Rect outRect) {
        super.getHitRect(outRect);
        if (LauncherApplication.isScreenLarge()) {
            outRect.top -= mDragPadding[0];
            outRect.right += mDragPadding[1];
            outRect.bottom += mDragPadding[2];
            outRect.left -= mDragPadding[3];
        }
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        return null;
    }
}
