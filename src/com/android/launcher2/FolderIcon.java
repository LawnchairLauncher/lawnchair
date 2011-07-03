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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher.R;
import com.android.launcher2.DropTarget.DragObject;
import com.android.launcher2.FolderInfo.FolderListener;

import java.util.ArrayList;

/**
 * An icon that can appear on in the workspace representing an {@link UserFolder}.
 */
public class FolderIcon extends LinearLayout implements FolderListener {
    private Launcher mLauncher;
    Folder mFolder;
    FolderInfo mInfo;

    // The number of icons to display in the
    private static final int NUM_ITEMS_IN_PREVIEW = 3;
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;

    // The degree to which the inner ring grows when accepting drop
    private static final float INNER_RING_GROWTH_FACTOR = 0.1f;

    // The degree to which the outer ring is scaled in its natural state
    private static final float OUTER_RING_GROWTH_FACTOR = 0.4f;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.24f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;

    private int mOriginalWidth = -1;
    private int mOriginalHeight = -1;
    private ImageView mPreviewBackground;
    private BubbleTextView mFolderName;

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

        icon.mFolderName = (BubbleTextView) icon.findViewById(R.id.folder_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mPreviewBackground = (ImageView) icon.findViewById(R.id.preview_background);
        icon.mPreviewBackground.setContentDescription(folderInfo.title);

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
        public float mOuterRingSize;
        public float mInnerRingSize;
        public FolderIcon mFolderIcon = null;
        private Launcher mLauncher;
        public Drawable mOuterRingDrawable = null;
        public Drawable mInnerRingDrawable = null;
        public static Drawable sSharedOuterRingDrawable = null;
        public static Drawable sSharedInnerRingDrawable = null;
        public static int sPreviewSize = -1;
        public static int sPreviewPadding = -1;

        private ValueAnimator mAcceptAnimator;
        private ValueAnimator mNeutralAnimator;

        public FolderRingAnimator(Launcher launcher, FolderIcon folderIcon) {
            mLauncher = launcher;
            mFolderIcon = folderIcon;
            Resources res = launcher.getResources();
            mOuterRingDrawable = res.getDrawable(R.drawable.portal_ring_outer_holo);
            mInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);

            if (sPreviewSize < 0 || sPreviewPadding < 0) {
                sPreviewSize = res.getDimensionPixelSize(R.dimen.folder_preview_size);
                sPreviewPadding = res.getDimensionPixelSize(R.dimen.folder_preview_padding);
            }
            if (sSharedOuterRingDrawable == null) {
                sSharedOuterRingDrawable = res.getDrawable(R.drawable.portal_ring_outer_holo);
            }
            if (sSharedInnerRingDrawable == null) {
                sSharedInnerRingDrawable = res.getDrawable(R.drawable.portal_ring_inner_holo);
            }
        }

        // Location is expressed in window coordinates
        public void setLocation(int x, int y) {
            mFolderLocX = x;
            mFolderLocY = y;
        }

        public void animateToAcceptState() {
            if (mNeutralAnimator != null) {
                mNeutralAnimator.cancel();
            }
            mAcceptAnimator = ValueAnimator.ofFloat(0f, 1f);
            mAcceptAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);
            mAcceptAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mOuterRingSize = (1 + percent * OUTER_RING_GROWTH_FACTOR) * sPreviewSize;
                    mInnerRingSize = (1 + percent * INNER_RING_GROWTH_FACTOR) * sPreviewSize;
                    mLauncher.getWorkspace().invalidate();
                    if (mFolderIcon != null) {
                        mFolderIcon.invalidate();
                    }
                }
            });
            mAcceptAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (mFolderIcon != null) {
                        mFolderIcon.mPreviewBackground.setVisibility(INVISIBLE);
                    }
                }
            });
            mAcceptAnimator.start();
        }

        public void animateToNaturalState() {
            if (mAcceptAnimator != null) {
                mAcceptAnimator.cancel();
            }
            mNeutralAnimator = ValueAnimator.ofFloat(0f, 1f);
            mNeutralAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);
            mNeutralAnimator.addUpdateListener(new AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    final float percent = (Float) animation.getAnimatedValue();
                    mOuterRingSize = (1 + (1 - percent) * OUTER_RING_GROWTH_FACTOR) * sPreviewSize;
                    mInnerRingSize = (1 + (1 - percent) * INNER_RING_GROWTH_FACTOR) * sPreviewSize;
                    mLauncher.getWorkspace().invalidate();
                    if (mFolderIcon != null) {
                        mFolderIcon.invalidate();
                    }
                }
            });
            mNeutralAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mFolderIcon != null) {
                        mFolderIcon.mPreviewBackground.setVisibility(VISIBLE);
                    }
                    mLauncher.getWorkspace().hideFolderAccept(FolderRingAnimator.this);
                }
            });
            mNeutralAnimator.start();
        }

        // Location is expressed in window coordinates
        public void getLocation(int[] loc) {
            loc[0] = mFolderLocX;
            loc[1] = mFolderLocY;
        }

        public float getOuterRingSize() {
            return mOuterRingSize;
        }

        public float getInnerRingSize() {
            return mInnerRingSize;
        }
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) &&
                !mFolder.isFull() && item != mInfo);
    }

    public boolean acceptDrop(Object dragInfo) {
        final ItemInfo item = (ItemInfo) dragInfo;
        return willAcceptItem(item);
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item);
        LauncherModel.addOrMoveItemInDatabase(mLauncher, item, mInfo.id, 0, item.cellX, item.cellY);
    }

    void saveState(CellLayout.LayoutParams lp) {
        mOriginalWidth = lp.width;
        mOriginalHeight = lp.height;
    }

    private void determineFolderLocationInWorkspace() {
        int tvLocation[] = new int[2];
        int wsLocation[] = new int[2];
        getLocationInWindow(tvLocation);
        mLauncher.getWorkspace().getLocationInWindow(wsLocation);

        int x = tvLocation[0] - wsLocation[0] + getMeasuredWidth() / 2;
        int y = tvLocation[1] - wsLocation[1] + FolderRingAnimator.sPreviewSize / 2;
        mFolderRingAnimator.setLocation(x, y);
    }

    public void onDragEnter(Object dragInfo) {
        if (!willAcceptItem((ItemInfo) dragInfo)) return;
        determineFolderLocationInWorkspace();
        mLauncher.getWorkspace().showFolderAccept(mFolderRingAnimator);
        mFolderRingAnimator.animateToAcceptState();
    }

    public void onDragOver(Object dragInfo) {
    }

    public void onDragExit(Object dragInfo) {
        if (!willAcceptItem((ItemInfo) dragInfo)) return;
        mFolderRingAnimator.animateToNaturalState();
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
        DragLayer dragLayer = mLauncher.getDragLayer();
        Rect from = new Rect();
        dragLayer.getViewRectRelativeToSelf(d.dragView, from);
        Rect to = new Rect();
        dragLayer.getDescendantRectRelativeToSelf(this, to);

        int previewSize = FolderRingAnimator.sPreviewSize;
        int vanishingPointX = (int) (previewSize * 0.7);
        int vanishingPointY = (int) (previewSize * (1 - 0.88f));
        to.offset(vanishingPointX - previewSize / 2 , vanishingPointY - previewSize / 2);

        dragLayer.animateView(d.dragView, from, to, 0f, 0.2f, 400, new DecelerateInterpolator(2),
                new AccelerateInterpolator(2));
    }

    public DropTarget getDropTargetDelegate(DragObject d) {
        return null;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0) return;

        canvas.save();
        TextView v = (TextView) mFolder.getItemAt(0);
        Drawable d = v.getCompoundDrawables()[1];
        int intrinsicIconSize = d.getIntrinsicHeight();

        if (mOriginalWidth < 0 || mOriginalHeight < 0) {
            mOriginalWidth = getMeasuredWidth();
            mOriginalHeight = getMeasuredHeight();
        }
        final int previewSize = FolderRingAnimator.sPreviewSize;
        final int previewPadding = FolderRingAnimator.sPreviewPadding;

        int halfAvailableSpace = (previewSize - 2 * previewPadding) / 2;
        // cos(45) = 0.707  + ~= 0.1)
        int availableSpace = (int) (halfAvailableSpace * (1 + 0.8f));

        int unscaledHeight = (int) (intrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));
        float baselineIconScale = (1.0f * availableSpace / unscaledHeight);

        int baselineSize = (int) (intrinsicIconSize * baselineIconScale);
        float maxPerspectiveShift = baselineSize * PERSPECTIVE_SHIFT_FACTOR;

        ArrayList<View> items = mFolder.getItemsInReadingOrder(false);

        int xShift = (mOriginalWidth - 2 * halfAvailableSpace) / 2;
        int yShift = previewPadding;
        canvas.translate(xShift, yShift);
        int nItemsInPreview = Math.min(items.size(), NUM_ITEMS_IN_PREVIEW);
        for (int i = nItemsInPreview - 1; i >= 0; i--) {
            int index = NUM_ITEMS_IN_PREVIEW - i - 1;

            float r = (index * 1.0f) / (NUM_ITEMS_IN_PREVIEW - 1);
            float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

            float offset = (1 - r) * maxPerspectiveShift;
            float scaledSize = scale * baselineSize;
            float scaleOffsetCorrection = (1 - scale) * baselineSize;

            // We want to imagine our coordinates from the bottom left, growing up and to the
            // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
            float transY = 2 * halfAvailableSpace - (offset + scaledSize + scaleOffsetCorrection);
            float transX = offset + scaleOffsetCorrection;

            v = (TextView) items.get(i);
            d = v.getCompoundDrawables()[1];

            canvas.save();
            canvas.translate(transX, transY);
            canvas.scale(baselineIconScale * scale, baselineIconScale * scale);

            int overlayAlpha = (int) (80 * (1 - r));
            if (d != null) {
                d.setBounds(0, 0, intrinsicIconSize, intrinsicIconSize);
                d.setFilterBitmap(true);
                d.setColorFilter(Color.argb(overlayAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
                d.draw(canvas);
                d.clearColorFilter();
                d.setFilterBitmap(false);
            }
            canvas.restore();
        }
        canvas.restore();
    }

    public void onItemsChanged() {
        invalidate();
        requestLayout();
    }

    public void onAdd(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }

    public void onRemove(ShortcutInfo item) {
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title.toString());
        mPreviewBackground.setContentDescription(title.toString());
    }
}
