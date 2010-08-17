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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

/**
 * Implements a DropTarget which allows applications to be dropped on it,
 * in order to launch the application info for that app.
 */
public class ApplicationInfoDropTarget extends ImageView implements DropTarget, DragController.DragListener {
    private Launcher mLauncher;
    private DragController mDragController;
    private boolean mActive = false;

    private View mHandle;

    private final Paint mPaint = new Paint();

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }

    /**
     * Set the color that will be used as a filter over objects dragged over this object.
     */
    public void setDragColor(int color) {
        mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

        // acceptDrop is called just before onDrop. We do the work here, rather than
        // in onDrop, because it allows us to reject the drop (by returning false)
        // so that the object being dragged isn't removed from the home screen.

        String packageName = null;
        if (dragInfo instanceof ApplicationInfo) {
            packageName = ((ApplicationInfo)dragInfo).componentName.getPackageName();
        } else if (dragInfo instanceof ShortcutInfo) {
            packageName = ((ShortcutInfo)dragInfo).intent.getComponent().getPackageName();
        }
        mLauncher.startApplicationDetailsActivity(packageName);
        return false;
    }

    public Rect estimateDropLocation(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo, Rect recycle) {
        return null;
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {

    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        dragView.setPaint(mPaint);
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        // TODO: Animate out
        dragView.setPaint(null);
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        if (info != null) {
            mActive = true;

            // TODO: Animate these in and out

            // Only show the info icon when an application is selected
            if (((ItemInfo)info).itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                setVisibility(VISIBLE);
            }
            mHandle.setVisibility(INVISIBLE);
        }
    }

    public void onDragEnd() {
        if (mActive) {
            mActive = false;
            // TODO: Animate these in and out
            setVisibility(GONE);
            mHandle.setVisibility(VISIBLE);
        }
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    void setDragController(DragController dragController) {
        mDragController = dragController;
    }

    void setHandle(View view) {
        mHandle = view;
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return null;
    }
}
