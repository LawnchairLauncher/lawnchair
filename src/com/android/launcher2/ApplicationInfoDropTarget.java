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

/**
 * Implements a DropTarget which allows applications to be dropped on it,
 * in order to launch the application info for that app.
 */
public class ApplicationInfoDropTarget extends ImageView implements DropTarget, DragController.DragListener {
    private Launcher mLauncher;
    private boolean mActive = false;

    /**
     * If true, this View responsible for managing its own visibility, and that of its handle.
     * This is generally the case, but it will be set to false when this is part of the
     * Contextual Action Bar.
     */
    private boolean mManageVisibility = true;

    /** The view that this view should appear in the place of. */
    private View mHandle = null;

    private final Paint mPaint = new Paint();

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ApplicationInfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
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

        ComponentName componentName = null;
        if (dragInfo instanceof ApplicationInfo) {
            componentName = ((ApplicationInfo)dragInfo).componentName;
        } else if (dragInfo instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo)dragInfo).intent.getComponent();
        }
        mLauncher.startApplicationDetailsActivity(componentName);
        return false;
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
        dragView.setPaint(null);
    }

    public void onDragStart(DragSource source, Object info, int dragAction) {
        if (info != null) {
            final int itemType = ((ItemInfo)info).itemType;
            mActive = (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION);
            if (mManageVisibility) {
                // Only show the info icon when an application is selected
                if (mActive) {
                    setVisibility(VISIBLE);
                }
                mHandle.setVisibility(INVISIBLE);
            }
        }
    }

    public boolean isDropEnabled() {
        return mActive;
    }

    public void onDragEnd() {
        if (mActive) {
            mActive = false;
            if (mManageVisibility) {
                setVisibility(GONE);
                mHandle.setVisibility(VISIBLE);
            }
        }
    }

    @Override
    public void getHitRect(Rect outRect) {
        super.getHitRect(outRect);
        if (LauncherApplication.isScreenXLarge()) {
            // TODO: This is a temporary hack. mManageVisiblity = false when you're in CAB mode.
            // In that case, this icon is more tightly spaced next to the delete icon so we want
            // it to have a smaller drag region. When the new drag&drop system comes in, we'll
            // dispatch the drag/drop by calculating what target you're overlapping
            final int dragPadding = mManageVisibility ? 50 : 10;
            outRect.top -= dragPadding;
            outRect.left -= dragPadding;
            outRect.bottom += dragPadding;
            outRect.right += dragPadding;
        }
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    void setHandle(View view) {
        mHandle = view;
    }

    void setManageVisibility(boolean value) {
        mManageVisibility = value;
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return null;
    }
}
