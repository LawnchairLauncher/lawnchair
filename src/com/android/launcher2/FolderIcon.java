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

import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
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
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;
    private static final float INNER_RING_GROWTH_FACTOR = 0.1f;
    private static final float OUTER_RING_BASELINE_SCALE = 0.7f;
    private static final float OUTER_RING_GROWTH_FACTOR = 0.3f;
    private static final float INNER_RING_BASELINE_SCALE = 1.0f;

    public static Drawable sFolderOuterRingDrawable = null;
    public static Drawable sFolderInnerRingDrawable = null;

    private int mOriginalWidth = -1;
    private int mOriginalHeight = -1;

    private int mFolderLocX;
    private int mFolderLocY;
    private float mOuterRingScale;
    private float mInnerRingScale;

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
        Drawable d = iconCache.getFullResIcon(resources, R.drawable.portal_ring_inner_holo);
        icon.setBackgroundDrawable(d);
        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;

        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setLauncher(launcher);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.mFolder = folder;

        folderInfo.addListener(icon);
        if (sFolderOuterRingDrawable == null) {
            sFolderOuterRingDrawable =
                    launcher.getResources().getDrawable(R.drawable.portal_ring_outer_holo);
        }

        if (sFolderInnerRingDrawable == null) {
            sFolderInnerRingDrawable =
                    launcher.getResources().getDrawable(R.drawable.portal_ring_inner_holo);
        }
        return icon;
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) &&
                !mFolder.isFull() && item != mInfo);
    }

    public boolean acceptDrop(DragObject d) {
        final ItemInfo item = (ItemInfo) d.dragInfo;
        return willAcceptItem(item);
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, item.cellX, item.cellY);
    }

    public void onDrop(DragObject d) {
        ShortcutInfo item;
        if (d.dragInfo instanceof ApplicationInfo) {
            // Came from all apps -- make a copy
            item = ((ApplicationInfo) d.dragInfo).makeShortcut();
        } else {
            item = (ShortcutInfo) d.dragInfo;
        }
        item.cellX = -1;
        item.cellY = -1;
        addItem(item);
    }

    void saveState(CellLayout.LayoutParams lp) {
        mOriginalWidth = lp.width;
        mOriginalHeight = lp.height;
    }

    private void animateToAcceptState() {
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();

        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(CONSUMPTION_ANIMATION_DURATION);
        va.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                mOuterRingScale = OUTER_RING_BASELINE_SCALE + percent * OUTER_RING_GROWTH_FACTOR;
                mInnerRingScale = INNER_RING_BASELINE_SCALE + percent * INNER_RING_GROWTH_FACTOR;
                mLauncher.getWorkspace().invalidate();
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Instead of setting the background drawable to null, we set the color to
                // transparent. Setting the background drawable to null results in onDraw
                // not getting called.
                setBackgroundColor(Color.TRANSPARENT);
                requestLayout();
            }
        });
        va.start();
    }

    private void animateToNaturalState() {
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(CONSUMPTION_ANIMATION_DURATION);
        va.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = (Float) animation.getAnimatedValue();
                mOuterRingScale = OUTER_RING_BASELINE_SCALE + OUTER_RING_GROWTH_FACTOR
                        - percent * OUTER_RING_GROWTH_FACTOR;
                mInnerRingScale = INNER_RING_BASELINE_SCALE + INNER_RING_GROWTH_FACTOR
                        - percent * INNER_RING_GROWTH_FACTOR;
                mLauncher.getWorkspace().invalidate();
                invalidate();
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setBackgroundDrawable(sFolderInnerRingDrawable);
                mLauncher.getWorkspace().hideFolderAccept(FolderIcon.this);
            }
        });
        va.start();
    }

    private void determineFolderLocationInWorkspace() {
        int tvLocation[] = new int[2];
        int wsLocation[] = new int[2];
        getLocationOnScreen(tvLocation);
        mLauncher.getWorkspace().getLocationOnScreen(wsLocation);
        mFolderLocX = tvLocation[0] - wsLocation[0] + getMeasuredWidth() / 2;
        mFolderLocY = tvLocation[1] - wsLocation[1] + getMeasuredHeight() / 2;
    }

    public void onDragEnter(DragObject d) {
        if (!willAcceptItem((ItemInfo) d.dragInfo)) return;
        determineFolderLocationInWorkspace();
        mLauncher.getWorkspace().showFolderAccept(this);
        animateToAcceptState();
    }

    public void onDragOver(DragObject d) {
    }

    public void onDragExit(DragObject d) {
        if (!willAcceptItem((ItemInfo) d.dragInfo)) return;
        animateToNaturalState();
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
    }

    public void getFolderLocation(int[] loc) {
        loc[0] = mFolderLocX;
        loc[1] = mFolderLocY;
    }

    public float getOuterRingScale() {
        return mOuterRingScale;
    }

    public float getInnerRingScale() {
        return mInnerRingScale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0) return;

        canvas.save();
        TextView v = (TextView) mFolder.getItemAt(0);
        Drawable d = v.getCompoundDrawables()[1];

        if (mOriginalWidth < 0 || mOriginalHeight < 0) {
            mOriginalWidth = getMeasuredWidth();
            mOriginalHeight = getMeasuredHeight();
        }

        int xShift = (mOriginalWidth - d.getIntrinsicWidth()) / 2;
        int yShift = (mOriginalHeight - d.getIntrinsicHeight()) / 2;
        canvas.translate(xShift, yShift);

        ArrayList<View> items = mFolder.getItemsInReadingOrder();
        int firstItemIndex = Math.max(0, items.size() - NUM_ITEMS_IN_PREVIEW);
        for (int i = firstItemIndex; i < items.size(); i++) {
            v = (TextView) items.get(i);
            d = v.getCompoundDrawables()[1];

            canvas.translate(d.getIntrinsicWidth() / 2, d.getIntrinsicHeight() / 2);
            canvas.rotate(i == firstItemIndex ? ICON_ANGLE : -ICON_ANGLE);
            canvas.translate(-d.getIntrinsicWidth() / 2, -d.getIntrinsicHeight() / 2);

            if (d != null) {
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                d.draw(canvas);
            }
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
