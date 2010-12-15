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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.launcher.R;

/**
 * Implements a DropTarget which allows applications to be dropped on it,
 * in order to launch the application info for that app.
 */
public class IconDropTarget extends ImageView implements DropTarget, DragController.DragListener {
    protected Launcher mLauncher;

    /**
     * If true, this View responsible for managing its own visibility, and that of its handle.
     * This is generally the case, but it will be set to false when this is part of the
     * Contextual Action Bar.
     */
    protected boolean mDragAndDropEnabled;

    /** Whether this drop target is active for the current drag */
    protected boolean mActive;

    /** The view that this view should appear in the place of. */
    protected View mHandle = null;

    /** The paint applied to the drag view on hover */
    protected final Paint mHoverPaint = new Paint();

    /** Drag zone padding */
    protected int mInnerDragPadding;
    protected int mOuterDragPadding;

    public IconDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDragAndDropEnabled = true;
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    void setHandle(View view) {
        mHandle = view;
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
        if (LauncherApplication.isScreenXLarge()) {
            outRect.top -= mOuterDragPadding;
            outRect.left -= mInnerDragPadding;
            outRect.bottom += mOuterDragPadding;
            outRect.right += mOuterDragPadding;
        }
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset,
            int yOffset, DragView dragView, Object dragInfo) {
        return null;
    }
}
