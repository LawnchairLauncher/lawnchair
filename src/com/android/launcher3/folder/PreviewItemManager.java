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

package com.android.launcher3.folder;

import static com.android.launcher3.BubbleTextView.DISPLAY_FOLDER;
import static com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ENTER_INDEX;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.EXIT_INDEX;
import static com.android.launcher3.folder.FolderIcon.DROP_IN_ANIMATION_DURATION;
import static com.android.launcher3.graphics.PreloadIconDrawable.newPendingIcon;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.apppairs.AppPairIconDrawingParams;
import com.android.launcher3.apppairs.AppPairIconGraphic;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import app.lawnchair.preferences.PreferenceManager;

/**
 * Manages the drawing and animations of {@link PreviewItemDrawingParams} for a
 * {@link FolderIcon}.
 */
public class PreviewItemManager {

    private final Context mContext;
    private final FolderIcon mIcon;
    @VisibleForTesting
    public final int mIconSize;

    // These variables are all associated with the drawing of the preview; they are
    // stored
    // as member variables for shared usage and to avoid computation on each frame
    private float mIntrinsicIconSize = -1;
    private int mTotalWidth = -1;
    private int mPrevTopPadding = -1;
    /**
     * The span the plate was last built for.
     *
     * Growing a folder sideways changes the view's width and growing it
     * downwards used to change its top padding, so the checks below caught both
     * without being told about spans at all. Now that a second row leaves the
     * padding alone -- which is what keeps the plate from dropping half a row --
     * a vertical resize changes none of them, and the plate would never be
     * rebuilt at its new height.
     */
    private int mPrevSpanX = -1;
    private int mPrevSpanY = -1;
    private Boolean mPrevDefaultMode = null;
    private Drawable mReferenceDrawable = null;

    private int mNumOfPrevItems = 0;

    // These hold the first page preview items
    private ArrayList<PreviewItemDrawingParams> mFirstPageParams = new ArrayList<>();

    static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    public PreviewItemManager(FolderIcon icon) {
        mContext = icon.getContext();
        mIcon = icon;
        mIconSize = ActivityContext.lookupContext(
                mContext).getDeviceProfile().folderChildIconSizePx;
    }

    /**
     * @param reverse If true, animates the final item in the preview to be full
     *                size. If false,
     *                animates the first item to its position in the preview.
     */
    public FolderPreviewItemAnim createFirstItemAnimation(final boolean reverse,
            final Runnable onCompleteRunnable) {
        return reverse
                ? new FolderPreviewItemAnim(this, mFirstPageParams.get(0), 0, 2, -1, -1,
                        FINAL_ITEM_ANIMATION_DURATION, onCompleteRunnable)
                : new FolderPreviewItemAnim(this, mFirstPageParams.get(0), -1, -1, 0, 2,
                        INITIAL_ITEM_ANIMATION_DURATION, onCompleteRunnable);
    }

    Drawable prepareCreateAnimation(final View destView) {
        Drawable animateDrawable = destView instanceof AppPairIcon
                ? ((AppPairIcon) destView).getIconDrawableArea().getDrawable()
                : ((BubbleTextView) destView).getIcon();
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth());
        mReferenceDrawable = animateDrawable;
        return animateDrawable;
    }

    public void recomputePreviewDrawingParams() {
        if (mReferenceDrawable != null) {
            computePreviewDrawingParams(mReferenceDrawable.getIntrinsicWidth(),
                    mIcon.getMeasuredWidth());
        }
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        int spanX = mIcon.mInfo == null ? 1 : Math.max(1, mIcon.mInfo.spanX);
        int spanY = mIcon.mInfo == null ? 1 : Math.max(1, mIcon.mInfo.spanY);
        boolean defaultMode = mIcon.isDefaultMode();
        // The plate not being resolved is a reason to ask again even when every
        // key below is unchanged: what went stale is something none of them
        // describe. It settles on the first draw after the icon is parented,
        // and the answer is memoised from then on.
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize ||
                mPrevTopPadding != mIcon.getPaddingTop()
                || mPrevSpanX != spanX || mPrevSpanY != spanY
                || mPrevDefaultMode == null || mPrevDefaultMode != defaultMode
                || !mIcon.mBackground.isPlateResolved()) {
            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;
            mPrevTopPadding = mIcon.getPaddingTop();
            mPrevSpanX = spanX;
            mPrevSpanY = spanY;
            mPrevDefaultMode = defaultMode;

            mIcon.mBackground.setup(mIcon.getContext(), mIcon.mActivity, mIcon, mTotalWidth,
                    mIcon.getPaddingTop());
            // After the plate is sized, never before: the title is placed
            // relative to the height the plate ended up with, and asking first
            // would place it against the height it had before the resize.
            mIcon.alignLabelBelowPlate();
            mIcon.mPreviewLayoutRule.init(
                    mIcon.mBackground.previewSize, mIntrinsicIconSize,
                    Utilities.isRtl(mIcon.getResources()),
                    mIcon.mActivity.getDeviceProfile().numFolderColumns
            );
            // init only ever hears about one cell. This is where the rule finds
            // out the plate is bigger than that and swaps the huddle for a grid.
            mIcon.mPreviewLayoutRule.initPlate(
                    mIcon.mBackground.previewWidth, mIcon.mBackground.previewHeight,
                    mIcon.mBackground.previewSize,
                    defaultMode);
            updatePreviewItems(false);
        }
    }

    PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        // We use an index of -1 to represent an icon on the workspace for the destroy
        // and
        // create animations
        if (index == -1) {
            return getFinalIconParams(params);
        }
        return mIcon.mPreviewLayoutRule.computePreviewItemDrawingParams(index, curNumItems, params);
    }

    private PreviewItemDrawingParams getFinalIconParams(PreviewItemDrawingParams params) {
        float iconSize = mIcon.mActivity.getDeviceProfile().iconSizePx;

        final float scale = iconSize / mReferenceDrawable.getIntrinsicWidth();
        final float trans = (mIcon.mBackground.previewSize - iconSize) / 2;

        params.update(trans, trans, scale);
        return params;
    }

    public void drawParams(Canvas canvas, ArrayList<PreviewItemDrawingParams> params,
            PointF offset, boolean shouldClipPath, Path clipPath) {
        // The first item should be drawn last (ie. on top of later items)
        for (int i = params.size() - 1; i >= 0; i--) {
            PreviewItemDrawingParams p = params.get(i);
            if (!p.hidden) {
                // Exiting param should always be clipped.
                boolean isExiting = p.index == EXIT_INDEX;
                drawPreviewItem(canvas, p, offset, isExiting | shouldClipPath, clipPath);
            }
        }
    }

    /**
     * Draws the preview items on {@param canvas}.
     */
    public void draw(Canvas canvas) {
        int saveCount = canvas.getSaveCount();
        // The items are drawn in coordinates relative to the preview offset
        PreviewBackground bg = mIcon.getFolderBackground();
        Path clipPath = bg.getClipPath();
        PointF firstPageOffset = new PointF(bg.basePreviewOffsetX, bg.basePreviewOffsetY);
        reportIfPreviewEscapedPlate(bg);
        drawParams(canvas, mFirstPageParams, firstPageOffset, /* shouldClipPath */ false, clipPath);
        canvas.restoreToCount(saveCount);
    }

    /**
     * Shouts when an app is about to be drawn outside the plate it belongs to.
     *
     * Sora: the preview and the plate are laid out from the same numbers, so an
     * app landing outside means those numbers disagree with each other -- and
     * the disagreement has so far only been caught by eye, at random, long after
     * whatever caused it. This states the invariant where it can be checked on
     * every frame and prints the whole of the state behind it the first time it
     * fails, so the next occurrence names its own cause instead of being
     * described.
     *
     * Only the failing case costs anything: a handful of comparisons otherwise,
     * and one line per distinct failure rather than one per frame.
     */
    private void reportIfPreviewEscapedPlate(PreviewBackground bg) {
        int plateWidth = bg.getPlateWidth();
        int plateHeight = bg.getPlateHeight();
        if (plateWidth <= 0 || plateHeight <= 0 || mIntrinsicIconSize <= 0) {
            return;
        }
        // Only while the plate is sitting at its settled size. A plate part way
        // through a resize, or swollen by the accept-drop scale, is meant to
        // disagree with apps laid out for the size it is heading to, and saying
        // so every frame of every animation would bury the one report worth
        // reading.
        if (plateWidth != bg.previewWidth || plateHeight != bg.previewHeight) {
            mEscapeSignature = null;
            return;
        }
        // The plate's own corner can shave a pixel or two off an app sitting in
        // it, and an app is allowed to overhang a one-cell plate by design.
        float slack = bg.isSingleCellPlate() ? mIntrinsicIconSize : mIntrinsicIconSize * 0.25f;
        float plateLeft = bg.getOffsetX();
        float plateTop = bg.getOffsetY();

        int escaped = -1;
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            PreviewItemDrawingParams p = mFirstPageParams.get(i);
            if (p.hidden || p.index == EXIT_INDEX || p.index == ENTER_INDEX) {
                continue;
            }
            float left = bg.basePreviewOffsetX + p.transX;
            float top = bg.basePreviewOffsetY + p.transY;
            float size = mIntrinsicIconSize * p.scale;
            if (left < plateLeft - slack || top < plateTop - slack
                    || left + size > plateLeft + plateWidth + slack
                    || top + size > plateTop + plateHeight + slack) {
                escaped = i;
                break;
            }
        }
        if (escaped < 0) {
            mEscapeSignature = null;
            return;
        }

        PreviewItemDrawingParams bad = mFirstPageParams.get(escaped);
        String signature = escaped + "/" + mFirstPageParams.size() + "@"
                + Math.round(bad.transX) + "," + Math.round(bad.transY) + "," + bad.scale
                + "/" + plateWidth + "x" + plateHeight;
        if (signature.equals(mEscapeSignature)) {
            return;
        }
        mEscapeSignature = signature;

        int[] onScreen = new int[2];
        mIcon.getLocationOnScreen(onScreen);
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            PreviewItemDrawingParams p = mFirstPageParams.get(i);
            items.append(" [").append(i).append(" idx=").append(p.index)
                    .append(p.hidden ? " hidden" : "")
                    .append(p.anim != null ? " anim" : "")
                    .append(" t=").append(Math.round(p.transX)).append(',')
                    .append(Math.round(p.transY))
                    .append(" s=").append(p.scale).append(']');
        }
        android.util.Log.w("SoraPlate", "preview escaped plate: item " + escaped
                + " of " + mFirstPageParams.size()
                + " | plate " + plateWidth + "x" + plateHeight
                + " at " + plateLeft + "," + plateTop
                + " previewWH=" + bg.previewWidth + "x" + bg.previewHeight
                + " previewSize=" + bg.previewSize
                + " baseOff=" + bg.basePreviewOffsetX + "," + bg.basePreviewOffsetY
                + " resolved=" + bg.isPlateResolved()
                + " singleCell=" + bg.isSingleCellPlate()
                + " | rule grid=" + mIcon.mPreviewLayoutRule.isPlateGrid()
                + " slots=" + mIcon.mPreviewLayoutRule.getSlotCount()
                + " limit=" + mIcon.mPreviewLayoutRule.getPreviewItemLimit()
                + " | icon span=" + (mIcon.mInfo == null ? "?" : mIcon.mInfo.spanX + "x"
                        + mIcon.mInfo.spanY)
                + " memoSpan=" + mPrevSpanX + "x" + mPrevSpanY
                + " view=" + mIcon.getWidth() + "x" + mIcon.getHeight()
                + "@" + onScreen[0] + "," + onScreen[1]
                + " padTop=" + mIcon.getPaddingTop() + " memoPadTop=" + mPrevTopPadding
                + " totalWidth=" + mTotalWidth + " intrinsic=" + mIntrinsicIconSize
                + " parent=" + (mIcon.getParent() == null ? "null"
                        : mIcon.getParent().getClass().getSimpleName())
                + " grandparent=" + (mIcon.getParent() == null
                        || mIcon.getParent().getParent() == null ? "null"
                        : mIcon.getParent().getParent().getClass().getSimpleName())
                + " |" + items);
    }

    /** The last escape reported, so a stuck one is not reported every frame. */
    private String mEscapeSignature = null;

    public void onParamsChanged() {
        mIcon.invalidate();
    }

    /**
     * Draws each preview item.
     *
     * @param offset         The offset needed to draw the preview items.
     * @param shouldClipPath Iff true, clip path using {@param clipPath}.
     * @param clipPath       The clip path of the folder icon.
     */
    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params, PointF offset,
            boolean shouldClipPath, Path clipPath) {
        canvas.save();
        if (shouldClipPath) {
            canvas.clipPath(clipPath);
        }
        canvas.translate(offset.x + params.transX, offset.y + params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            Rect bounds = d.getBounds();
            canvas.save();
            canvas.translate(-bounds.left, -bounds.top);
            canvas.scale(mIntrinsicIconSize / bounds.width(), mIntrinsicIconSize / bounds.height());
            d.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    public void hidePreviewItem(int index, boolean hidden) {
        // If there are more params than visible in the preview, they are used for
        // enter/exit
        // animation purposes and they were added to the front of the list.
        // To index the params properly, we need to skip these params.
        index = index + Math.max(
                mFirstPageParams.size() - mIcon.mPreviewLayoutRule.getPreviewItemLimit(), 0);

        PreviewItemDrawingParams params = index < mFirstPageParams.size() ? mFirstPageParams.get(index) : null;
        if (params != null) {
            params.hidden = hidden;
        }
    }

    void buildParamsForPage(int page, ArrayList<PreviewItemDrawingParams> params, boolean animate) {
        List<ItemInfo> items = mIcon.getPreviewItemsOnPage(page);

        // We adjust the size of the list to match the number of items in the preview.
        while (items.size() < params.size()) {
            params.remove(params.size() - 1);
        }
        while (items.size() > params.size()) {
            params.add(new PreviewItemDrawingParams(0, 0, 0));
        }

        int numItemsInFirstPagePreview = page == 0
                ? items.size()
                : mIcon.mPreviewLayoutRule.getPreviewItemLimit();
        for (int i = 0; i < params.size(); i++) {
            PreviewItemDrawingParams p = params.get(i);
            setDrawable(p, items.get(i));

            if (!animate) {
                if (p.anim != null) {
                    p.anim.cancel();
                }
                computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, p);
                if (mReferenceDrawable == null) {
                    mReferenceDrawable = p.drawable;
                }
            } else {
                FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, i,
                        mNumOfPrevItems, i, numItemsInFirstPagePreview, DROP_IN_ANIMATION_DURATION,
                        null);

                if (p.anim != null) {
                    if (p.anim.hasEqualFinalState(anim)) {
                        // do nothing, let the current animation finish
                        continue;
                    }
                    p.anim.cancel();
                }
                p.anim = anim;
                p.anim.start();
            }
        }
    }

    void onFolderClose(int currentPage) {
        // Snap back to the first-page preview immediately. The previous slide animation
        // showed a solid preview background while icons rearranged for ~400ms after close.
        if (currentPage != 0) {
            updatePreviewItems(false);
        }
        onParamsChanged();
    }

    void updatePreviewItems(boolean animate) {
        int numOfPrevItemsAux = mFirstPageParams.size();
        buildParamsForPage(0, mFirstPageParams, animate);
        mNumOfPrevItems = numOfPrevItemsAux;
    }

    void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        boolean modified = false;
        for (PreviewItemDrawingParams param : mFirstPageParams) {
            if (itemCheck.test(param.item)
                    || (param.item instanceof AppPairInfo api && api.anyMatch(itemCheck))) {
                setDrawable(param, param.item);
                modified = true;
            }
        }
        if (modified) {
            mIcon.invalidate();
        }
    }

    boolean verifyDrawable(@NonNull Drawable who) {
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            if (mFirstPageParams.get(i).drawable == who) {
                return true;
            }
        }
        return false;
    }

    float getIntrinsicIconSize() {
        return mIntrinsicIconSize;
    }

    /**
     * Handles the case where items in the preview are either:
     * - Moving into the preview
     * - Moving into a new position
     * - Moving out of the preview
     *
     * @param oldItems The list of items in the old preview.
     * @param newItems The list of items in the new preview.
     * @param dropped  The item that was dropped onto the FolderIcon.
     */
    public void onDrop(List<ItemInfo> oldItems, List<ItemInfo> newItems, ItemInfo dropped) {
        int numItems = newItems.size();
        final ArrayList<PreviewItemDrawingParams> params = mFirstPageParams;
        buildParamsForPage(0, params, false);

        // New preview items for items that are moving in (except for the dropped item).
        List<ItemInfo> moveIn = new ArrayList<>();
        for (ItemInfo newItem : newItems) {
            if (!oldItems.contains(newItem) && !newItem.equals(dropped)) {
                moveIn.add(newItem);
            }
        }
        for (int i = 0; i < moveIn.size(); ++i) {
            int prevIndex = newItems.indexOf(moveIn.get(i));
            PreviewItemDrawingParams p = params.get(prevIndex);
            computePreviewItemDrawingParams(prevIndex, numItems, p);
            updateTransitionParam(p, moveIn.get(i), ENTER_INDEX, newItems.indexOf(moveIn.get(i)),
                    numItems);
        }

        // Items that are moving into new positions within the preview.
        for (int newIndex = 0; newIndex < newItems.size(); ++newIndex) {
            int oldIndex = oldItems.indexOf(newItems.get(newIndex));
            if (oldIndex >= 0 && newIndex != oldIndex) {
                PreviewItemDrawingParams p = params.get(newIndex);
                updateTransitionParam(p, newItems.get(newIndex), oldIndex, newIndex, numItems);
            }
        }

        // Old preview items that need to be moved out.
        List<ItemInfo> moveOut = new ArrayList<>(oldItems);
        moveOut.removeAll(newItems);
        for (int i = 0; i < moveOut.size(); ++i) {
            ItemInfo item = moveOut.get(i);
            int oldIndex = oldItems.indexOf(item);
            PreviewItemDrawingParams p = computePreviewItemDrawingParams(oldIndex, numItems, null);
            updateTransitionParam(p, item, oldIndex, EXIT_INDEX, numItems);
            params.add(0, p); // We want these items first so that they are on drawn last.
        }

        for (int i = 0; i < params.size(); ++i) {
            if (params.get(i).anim != null) {
                params.get(i).anim.start();
            }
        }
    }

    private void updateTransitionParam(final PreviewItemDrawingParams p, ItemInfo item,
            int prevIndex, int newIndex, int numItems) {
        setDrawable(p, item);

        FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, prevIndex, numItems,
                newIndex, numItems, DROP_IN_ANIMATION_DURATION, null);
        if (p.anim != null && !p.anim.hasEqualFinalState(anim)) {
            p.anim.cancel();
        }
        p.anim = anim;
    }

    /** Lawnchair: Find the correct folder size depending on which parent owned them 
     * @return Icon size that's typically use for workspace or size that's for allapps page */
    private int getChildIconSize() {
        if (mIcon.isInAppDrawer()) {
            return ActivityContext.lookupContext(mContext).getDeviceProfile().getAllAppsProfile().getIconSizePx();
        }
        return mIconSize;
    }

    @VisibleForTesting
    public void setDrawable(PreviewItemDrawingParams p, ItemInfo item) {
        // Lawnchair: Find the correct folder size depending on which parent owned them
        int iconSize = getChildIconSize();
        if (item instanceof WorkspaceItemInfo wii) {
            if (isActivePendingIcon(wii)) {
                p.drawable = newPendingIcon(mContext, wii);
            } else {
                p.drawable = wii.newIcon(mContext, FLAG_THEMED);
            }
            p.drawable.setBounds(0, 0, iconSize, iconSize);
        } else if (item instanceof AppPairInfo api) {
            AppPairIconDrawingParams appPairParams = new AppPairIconDrawingParams(mContext, DISPLAY_FOLDER);
            p.drawable = AppPairIconGraphic.composeDrawable(api, appPairParams);
            p.drawable.setBounds(0, 0, iconSize, iconSize);
        } else if (item instanceof ItemInfoWithIcon withIcon){
            var isThemed = PreferenceManager.getInstance(mContext).getDrawerThemedIcons().get() ? FLAG_THEMED : 0;
            p.drawable = withIcon.newIcon(mContext, isThemed);
            p.drawable.setBounds(0, 0, iconSize, iconSize);
        }

        p.item = item;
        // Set the callback to FolderIcon as it is responsible to drawing the icon. The
        // callback will be released when the folder is opened.
        p.drawable.setCallback(mIcon);

        // Verify high res
        if (item instanceof ItemInfoWithIcon info
                && info.getMatchingLookupFlag().isVisuallyLessThan(DESKTOP_ICON_FLAG)) {
            LauncherAppState.getInstance(mContext).getIconCache().updateIconInBackground(
                    newInfo -> {
                        if (p.item == newInfo) {
                            setDrawable(p, newInfo);
                            mIcon.invalidate();
                        }
                    }, info, DESKTOP_ICON_FLAG);
        }
    }

    /**
     * Returns true if item is a Promise Icon or actively downloading, and the item is not an
     * inactive archived app.
     */
    private boolean isActivePendingIcon(WorkspaceItemInfo item) {
        return (item.hasPromiseIconUi()
                || (item.runtimeStatusFlags & FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) != 0)
                && !(Flags.useNewIconForArchivedApps() && item.isInactiveArchive());
    }
}
