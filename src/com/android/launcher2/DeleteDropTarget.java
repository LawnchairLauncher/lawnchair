/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher.R;

public class DeleteDropTarget extends ButtonDropTarget {

    private static int DELETE_ANIMATION_DURATION = 250;
    private ColorStateList mOriginalTextColor;
    private TransitionDrawable mDrawable;
    private int mHoverColor = 0xFFFF0000;

    public DeleteDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeleteDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // Get the drawable
        mOriginalTextColor = getTextColors();

        // Get the hover color
        Resources r = getResources();
        mHoverColor = r.getColor(R.color.delete_target_hover_tint);
        mHoverPaint.setColorFilter(new PorterDuffColorFilter(
                mHoverColor, PorterDuff.Mode.SRC_ATOP));
        mDrawable = (TransitionDrawable) getCompoundDrawables()[0];
        mDrawable.setCrossFadeEnabled(true);

        // Remove the text in the Phone UI in landscape
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!LauncherApplication.isScreenLarge()) {
                setText("");
            }
        }
    }

    private boolean isAllAppsApplication(DragSource source, Object info) {
        return (source instanceof AppsCustomizePagedView) && (info instanceof ApplicationInfo);
    }
    private boolean isAllAppsWidget(DragSource source, Object info) {
        return (source instanceof AppsCustomizePagedView) && (info instanceof PendingAddWidgetInfo);
    }
    private boolean isDragSourceWorkspaceOrFolder(DragObject d) {
        return (d.dragSource instanceof Workspace) || (d.dragSource instanceof Folder);
    }
    private boolean isWorkspaceOrFolderApplication(DragObject d) {
        return isDragSourceWorkspaceOrFolder(d) && (d.dragInfo instanceof ShortcutInfo);
    }
    private boolean isWorkspaceOrFolderWidget(DragObject d) {
        return isDragSourceWorkspaceOrFolder(d) && (d.dragInfo instanceof LauncherAppWidgetInfo);
    }
    private boolean isWorkspaceFolder(DragObject d) {
        return (d.dragSource instanceof Workspace) && (d.dragInfo instanceof FolderInfo);
    }

    @Override
    public boolean acceptDrop(DragObject d) {
        // We can remove everything including App shortcuts, folders, widgets, etc.
        return true;
    }

    @Override
    public void onDragStart(DragSource source, Object info, int dragAction) {
        boolean isVisible = true;
        boolean isUninstall = false;

        // If we are dragging a widget from AppsCustomize, hide the delete target
        if (isAllAppsWidget(source, info)) {
            isVisible = false;
        }

        // If we are dragging an application from AppsCustomize, only show the control if we can
        // delete the app (it was downloaded), and rename the string to "uninstall" in such a case
        if (isAllAppsApplication(source, info)) {
            ApplicationInfo appInfo = (ApplicationInfo) info;
            if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) != 0) {
                isUninstall = true;
            } else {
                isVisible = false;
            }
        }

        mActive = isVisible;
        mDrawable.resetTransition();
        setTextColor(mOriginalTextColor);
        ((ViewGroup) getParent()).setVisibility(isVisible ? View.VISIBLE : View.GONE);
        if (getText().length() > 0) {
            setText(isUninstall ? R.string.delete_target_uninstall_label
                : R.string.delete_target_label);
        }
    }

    @Override
    public void onDragEnd() {
        super.onDragEnd();
        mActive = false;
    }

    public void onDragEnter(DragObject d) {
        super.onDragEnter(d);

        mDrawable.startTransition(mTransitionDuration);
        setTextColor(mHoverColor);
    }

    public void onDragExit(DragObject d) {
        super.onDragExit(d);

        if (!d.dragComplete) {
            mDrawable.resetTransition();
            setTextColor(mOriginalTextColor);
        }
    }

    private void animateToTrashAndCompleteDrop(final DragObject d) {
        DragLayer dragLayer = mLauncher.getDragLayer();
        Rect from = new Rect();
        Rect to = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);
        dragLayer.getViewRectRelativeToSelf(this, to);

        int width = mDrawable.getIntrinsicWidth();
        int height = mDrawable.getIntrinsicHeight();
        to.set(to.left + getPaddingLeft(), to.top + getPaddingTop(),
                to.left + getPaddingLeft() + width, to.bottom);

        // Center the destination rect about the trash icon
        int xOffset = (int) -(d.dragView.getMeasuredWidth() - width) / 2;
        int yOffset = (int) -(d.dragView.getMeasuredHeight() - height) / 2;
        to.offset(xOffset, yOffset);

        mSearchDropTargetBar.deferOnDragEnd();
        Runnable onAnimationEndRunnable = new Runnable() {
            @Override
            public void run() {
                mSearchDropTargetBar.onDragEnd();
                mLauncher.exitSpringLoadedDragMode();
                completeDrop(d);
            }
        };
        dragLayer.animateView(d.dragView, from, to, 0.1f, 0.1f,
                DELETE_ANIMATION_DURATION, new DecelerateInterpolator(2),
                new DecelerateInterpolator(1.5f), onAnimationEndRunnable, false);
    }

    private void completeDrop(DragObject d) {
        ItemInfo item = (ItemInfo) d.dragInfo;

        if (isAllAppsApplication(d.dragSource, item)) {
            // Uninstall the application if it is being dragged from AppsCustomize
            mLauncher.startApplicationUninstallActivity((ApplicationInfo) item);
        } else if (isWorkspaceOrFolderApplication(d)) {
            LauncherModel.deleteItemFromDatabase(mLauncher, item);
        } else if (isWorkspaceFolder(d)) {
            // Remove the folder from the workspace and delete the contents from launcher model
            FolderInfo folderInfo = (FolderInfo) item;
            mLauncher.removeFolder(folderInfo);
            LauncherModel.deleteFolderContentsFromDatabase(mLauncher, folderInfo);
        } else if (isWorkspaceOrFolderWidget(d)) {
            // Remove the widget from the workspace
            mLauncher.removeAppWidget((LauncherAppWidgetInfo) item);
            LauncherModel.deleteItemFromDatabase(mLauncher, item);

            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = mLauncher.getAppWidgetHost();
            if (appWidgetHost != null) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        appWidgetHost.deleteAppWidgetId(launcherAppWidgetInfo.appWidgetId);
                    }
                }.start();
            }
        }
    }

    public void onDrop(DragObject d) {
        animateToTrashAndCompleteDrop(d);
    }
}
