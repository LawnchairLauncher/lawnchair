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
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher.R;
import com.android.launcher2.FolderInfo.FolderListener;

/**
 * An icon that can appear on in the workspace representing an {@link UserFolder}.
 */
public class FolderIcon extends FrameLayout implements DropTarget, FolderListener {
    private Launcher mLauncher;
    Folder mFolder;
    FolderInfo mInfo;

    private static final int NUM_ITEMS_IN_PREVIEW = 4;
    private static final float ICON_ANGLE = 15f;

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FolderIcon(Context context) {
        super(context);
    }

    public boolean isDropEnabled() {
        final ViewGroup cellLayoutChildren = (ViewGroup) getParent();
        final ViewGroup cellLayout = (ViewGroup) cellLayoutChildren.getParent();
        final Workspace workspace = (Workspace) cellLayout.getParent();
        return !workspace.isSmall();
    }

    static FolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
            FolderInfo folderInfo, IconCache iconCache) {

        FolderIcon icon = (FolderIcon) LayoutInflater.from(launcher).inflate(resId, group, false);

        final Resources resources = launcher.getResources();
        Drawable d = iconCache.getFullResIcon(resources, R.drawable.folder_bg);
        icon.setBackgroundDrawable(d);
        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;

        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setLauncher(launcher);
        folder.bind(folderInfo);
        icon.mFolder = folder;

        folderInfo.addListener(icon);

        return icon;
    }

    public boolean acceptDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        final int itemType = item.itemType;
        return (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT)
                && item.container != mInfo.id;
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, item.cellX, item.cellY);
    }

    public void onDrop(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        ShortcutInfo item;
        if (dragInfo instanceof ApplicationInfo) {
            // Came from all apps -- make a copy
            item = ((ApplicationInfo)dragInfo).makeShortcut();
        } else {
            item = (ShortcutInfo)dragInfo;
        }
        item.cellX = -1;
        item.cellY = -1;
        addItem(item);
    }

    public void onDragEnter(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragOver(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    public void onDragExit(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
    }

    @Override
    public DropTarget getDropTargetDelegate(DragSource source, int x, int y, int xOffset, int yOffset,
            DragView dragView, Object dragInfo) {
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0) return;

        canvas.save();
        TextView v = (TextView) mFolder.getItemAt(0);
        Drawable d = v.getCompoundDrawables()[1];

        canvas.translate( (getMeasuredWidth() - d.getIntrinsicWidth()) / 2,
                (getMeasuredHeight() - d.getIntrinsicHeight()) / 2);

        canvas.translate(d.getIntrinsicWidth() / 2, d.getIntrinsicHeight() / 2);
        canvas.rotate(ICON_ANGLE);

        canvas.translate(-d.getIntrinsicWidth() / 2, -d.getIntrinsicHeight() / 2);

        for (int i = Math.max(0, mFolder.getItemCount() - NUM_ITEMS_IN_PREVIEW);
                i < mFolder.getItemCount(); i++) {
            v = (TextView) mFolder.getItemAt(i);
            d = v.getCompoundDrawables()[1];

            if (d != null) {
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                d.draw(canvas);
            }

            canvas.translate(d.getIntrinsicWidth() / 2, d.getIntrinsicHeight() / 2);
            canvas.rotate(-ICON_ANGLE);
            canvas.translate(-d.getIntrinsicWidth() / 2, -d.getIntrinsicHeight() / 2);
        }

        canvas.restore();
    }

    public void onAdd(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }

    public void onRemove(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }
}
