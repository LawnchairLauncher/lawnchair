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
import android.graphics.PorterDuff;
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

    // The number of icons to display in the
    private static final int NUM_ITEMS_IN_PREVIEW = 4;
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;

    // The degree to which the inner ring grows when accepting drop
    private static final float INNER_RING_GROWTH_FACTOR = 0.1f;

    // The degree to which the inner ring is scaled in its natural state
    private static final float INNER_RING_BASELINE_SCALE = 1.0f;

    // The degree to which the outer ring grows when accepting drop
    private static final float OUTER_RING_BASELINE_SCALE = 0.7f;

    // The degree to which the outer ring is scaled in its natural state
    private static final float OUTER_RING_GROWTH_FACTOR = 0.3f;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.18f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.3f;

    // The percentage of the FolderIcons view that will be dedicated to the items preview
    private static final float SPACE_PERCENTAGE_FOR_ICONS = 0.8f;

    public static Drawable sFolderOuterRingDrawable = null;
    public static Drawable sFolderInnerRingDrawable = null;

    private int mOriginalWidth = -1;
    private int mOriginalHeight = -1;

    FolderRingAnimator mFolderRingAnimator = null;

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
        icon.mFolderRingAnimator = new FolderRingAnimator(launcher, icon);
        folderInfo.addListener(icon);

        return icon;
    }

    public static class FolderRingAnimator {
        public int mFolderLocX;
        public int mFolderLocY;
        public float mOuterRingScale;
        public float mInnerRingScale;
        public FolderIcon mFolderIcon = null;
        private Launcher mLauncher;

        public FolderRingAnimator(Launcher launcher, FolderIcon folderIcon) {
            mLauncher = launcher;
            mFolderIcon = folderIcon;
            if (sFolderOuterRingDrawable == null) {
                sFolderOuterRingDrawable =
                        launcher.getResources().getDrawable(R.drawable.portal_ring_outer_holo);
            }
            if (sFolderInnerRingDrawable == null) {
                sFolderInnerRingDrawable =
                        launcher.getResources().getDrawable(R.drawable.portal_ring_inner_holo);
            }
        }

        public void setLocation(int x, int y) {
            mFolderLocX = x;
            mFolderLocY = y;
        }

        public void animateToAcceptState() {
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(CONSUMPTION_ANIMATION_DURATION);
            va.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mOuterRingScale = OUTER_RING_BASELINE_SCALE + percent * OUTER_RING_GROWTH_FACTOR;
                    mInnerRingScale = INNER_RING_BASELINE_SCALE + percent * INNER_RING_GROWTH_FACTOR;
                    mLauncher.getWorkspace().invalidate();
                    if (mFolderIcon != null) {
                        mFolderIcon.invalidate();
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Instead of setting the background drawable to null, we set the color to
                    // transparent. Setting the background drawable to null results in onDraw
                    // not getting called.
                    if (mFolderIcon != null) {
                        mFolderIcon.setBackgroundColor(Color.TRANSPARENT);
                        mFolderIcon.requestLayout();
                    }
                }
            });
            va.start();
        }

        public void animateToNaturalState() {
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
                    if (mFolderIcon != null) {
                        mFolderIcon.invalidate();
                    }
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mFolderIcon != null) {
                        mFolderIcon.setBackgroundDrawable(sFolderInnerRingDrawable);
                    }
                    mLauncher.getWorkspace().hideFolderAccept(FolderRingAnimator.this);
                }
            });
            va.start();
        }

        public void getLocation(int[] loc) {
            loc[0] = mFolderLocX;
            loc[1] = mFolderLocY;
        }

        public float getOuterRingScale() {
            return mOuterRingScale;
        }

        public float getInnerRingScale() {
            return mInnerRingScale;
        }
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

    private void determineFolderLocationInWorkspace() {
        int tvLocation[] = new int[2];
        int wsLocation[] = new int[2];
        getLocationOnScreen(tvLocation);
        mLauncher.getWorkspace().getLocationOnScreen(wsLocation);

        int x = tvLocation[0] - wsLocation[0] + getMeasuredWidth() / 2;
        int y = tvLocation[1] - wsLocation[1] + getMeasuredHeight() / 2;
        mFolderRingAnimator.setLocation(x, y);
    }

    public void onDragEnter(DragObject d) {
        if (!willAcceptItem((ItemInfo) d.dragInfo)) return;
        determineFolderLocationInWorkspace();
        mLauncher.getWorkspace().showFolderAccept(mFolderRingAnimator);
        mFolderRingAnimator.animateToAcceptState();
    }

    public void onDragOver(DragObject d) {
    }

    public void onDragExit(DragObject d) {
        if (!willAcceptItem((ItemInfo) d.dragInfo)) return;
        mFolderRingAnimator.animateToNaturalState();
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
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

        int unscaledHeight = (int) (d.getIntrinsicHeight() * (1 + PERSPECTIVE_SHIFT_FACTOR));
        float baselineIconScale = SPACE_PERCENTAGE_FOR_ICONS / (unscaledHeight / (mOriginalHeight * 1.0f));

        int baselineHeight = (int) (d.getIntrinsicHeight() * baselineIconScale);
        int totalStackHeight = (int) (baselineHeight * (1 + PERSPECTIVE_SHIFT_FACTOR));
        int baselineWidth = (int) (d.getIntrinsicWidth() * baselineIconScale);
        float maxPerpectiveShift = baselineHeight * PERSPECTIVE_SHIFT_FACTOR;

        ArrayList<View> items = mFolder.getItemsInReadingOrder();
        int firstItemIndex = Math.max(0, items.size() - NUM_ITEMS_IN_PREVIEW);

        int xShift = (int) (mOriginalWidth - baselineWidth) / 2;
        int yShift = (int) (mOriginalHeight - totalStackHeight) / 2;
        canvas.translate(xShift, yShift);
        for (int i = firstItemIndex; i < items.size(); i++) {
            int index = i - firstItemIndex;
            index += Math.max(0, NUM_ITEMS_IN_PREVIEW - items.size());

            float r = (index * 1.0f) / (NUM_ITEMS_IN_PREVIEW - 1);
            float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));
            r = (float) Math.pow(r, 2);

            float transY = r * maxPerpectiveShift;
            float transX = (1 - scale) * baselineWidth / 2.0f;

            v = (TextView) items.get(i);
            d = v.getCompoundDrawables()[1];

            canvas.save();
            canvas.translate(transX, transY);
            canvas.scale(baselineIconScale * scale, baselineIconScale * scale);

            int overlayAlpha = (int) (80 * (1 - r));
            if (d != null) {
                d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                d.setColorFilter(Color.argb(overlayAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
                d.draw(canvas);
                d.clearColorFilter();
            }
            canvas.restore();
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
