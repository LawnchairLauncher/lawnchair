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

package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.Alarm;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.FolderInfo.FolderListener;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.SimpleOnStylusPressListener;
import com.android.launcher3.StylusEventHelper;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.badge.BadgeRenderer;
import com.android.launcher3.badge.FolderBadgeInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;

import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener {
    @Thunk Launcher mLauncher;
    @Thunk Folder mFolder;
    private FolderInfo mInfo;
    @Thunk static boolean sStaticValuesDirty = true;

    public static final int NUM_ITEMS_IN_PREVIEW = FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON ?
            StackFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW :
            ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;

    private CheckLongPressHelper mLongPressHelper;
    private StylusEventHelper mStylusEventHelper;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;

    PreviewBackground mBackground = new PreviewBackground();
    private boolean mBackgroundIsVisible = true;

    FolderIconPreviewVerifier mPreviewVerifier;
    PreviewLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);

    boolean mAnimating = false;
    private Rect mTempBounds = new Rect();

    private float mSlop;

    private Alarm mOpenAlarm = new Alarm();

    private FolderBadgeInfo mBadgeInfo = new FolderBadgeInfo();
    private BadgeRenderer mBadgeRenderer;
    private float mBadgeScale;
    private Point mTempSpaceForBadgeOffset = new Point();

    private static final Property<FolderIcon, Float> BADGE_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "badgeScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mBadgeScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mBadgeScale = value;
            folderIcon.invalidate();
        }
    };

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        mStylusEventHelper = new StylusEventHelper(new SimpleOnStylusPressListener(this), this);
        mPreviewLayoutRule = FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON ?
                new StackFolderIconLayoutRule() :
                new ClippedFolderIconLayoutRule();
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mPreviewItemManager = new PreviewItemManager(this);
    }

    public static FolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
            FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = launcher.getDeviceProfile();
        FolderIcon icon = (FolderIcon) LayoutInflater.from(group.getContext())
                .inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = (BubbleTextView) icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;

        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;
        icon.mBadgeRenderer = launcher.getDeviceProfile().mBadgeRenderer;
        icon.setContentDescription(launcher.getString(R.string.folder_name_format, folderInfo.title));
        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        icon.setAccessibilityDelegate(launcher.getAccessibilityDelegate());

        folderInfo.addListener(icon);

        icon.setOnFocusChangeListener(launcher.mFocusHandler);
        return icon;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        sStaticValuesDirty = true;
        return super.onSaveInstanceState();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
        mPreviewVerifier = new FolderIconPreviewVerifier(mLauncher.getDeviceProfile().inv);
        mPreviewItemManager.updateItemDrawingParams(false);
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) &&
                !mFolder.isFull() && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void addItem(ShortcutInfo item) {
        addItem(item, true);
    }

    public void addItem(ShortcutInfo item, boolean animate) {
        mInfo.add(item, animate);
    }

    public void removeItem(ShortcutInfo item, boolean animate) {
        mInfo.remove(item, animate);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.cellX, lp.cellY);
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof AppInfo)
                        || (dragInfo instanceof ShortcutInfo)
                        || (dragInfo instanceof PendingAddShortcutInfo))) {
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
            mFolder.animateOpen();
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final ShortcutInfo destInfo, final View destView,
            final ShortcutInfo srcInfo, final DragView srcView, Rect dstRect,
            float scaleRelativeToDragLayer, Runnable postAnimationRunnable) {
        prepareCreateAnimation(destView);
        addItem(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1, postAnimationRunnable);
    }

    public void performDestroyAnimation(Runnable onCompleteRunnable) {
        // This will animate the final item in the preview to be full size.
        mPreviewItemManager.createFirstItemAnimation(true /* reverse */, onCompleteRunnable)
                .start();
    }

    public void onDragExit() {
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ShortcutInfo item, DragView animateView, Rect finalRect,
            float scaleRelativeToDragLayer, int index, Runnable postAnimationRunnable) {
        item.cellX = -1;
        item.cellY = -1;

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            Rect from = new Rect();
            dragLayer.getViewRectRelativeToSelf(animateView, from);
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace workspace = mLauncher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform((CellLayout) getParent().getParent());
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform((CellLayout) getParent().getParent());
            }

            boolean itemAdded = false;
            if (index >= mPreviewLayoutRule.maxNumItems()
                    && mPreviewLayoutRule.hasEnterExitIndices()) {
                List<BubbleTextView> oldPreviewItems = getPreviewItemsOnPage(0);
                addItem(item, false);
                List<BubbleTextView> newPreviewItems = getPreviewItemsOnPage(0);

                if (!oldPreviewItems.containsAll(newPreviewItems)) {
                    for (int i = 0; i < newPreviewItems.size(); ++i) {
                        if (newPreviewItems.get(i).getTag().equals(item)) {
                            // If the item dropped is going to be in the preview, we update the
                            // index here to reflect its position in the preview.
                            index = i;
                        }
                    }
                    mPreviewItemManager.onDrop(oldPreviewItems, newPreviewItems, item);
                    itemAdded = true;
                } else {
                    removeItem(item, false);
                }
            }

            if (!itemAdded) {
                addItem(item);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, index + 1, center);
            center[0] = (int) Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = (int) Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < mPreviewLayoutRule.maxNumItems() ? 0.5f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;
            dragLayer.animateView(animateView, from, to, finalAlpha,
                    1, 1, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    new DecelerateInterpolator(2), new AccelerateInterpolator(2),
                    postAnimationRunnable, DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);
            final int finalIndex = index;
            postDelayed(new Runnable() {
                public void run() {
                    mPreviewItemManager.hidePreviewItem(finalIndex, false);
                    mFolder.showItem(item);
                    invalidate();
                }
            }, DROP_IN_ANIMATION_DURATION);
        } else {
            addItem(item);
        }
    }

    public void onDrop(DragObject d) {
        ShortcutInfo item;
        if (d.dragInfo instanceof AppInfo) {
            // Came from all apps -- make a copy
            item = ((AppInfo) d.dragInfo).makeShortcut();
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            item = new ShortcutInfo((ShortcutInfo) d.dragInfo);
        } else {
            item = (ShortcutInfo) d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d.dragView, null, 1.0f, mInfo.contents.size(), d.postAnimationRunnable);
    }

    public void setBadgeInfo(FolderBadgeInfo badgeInfo) {
        updateBadgeScale(mBadgeInfo.hasBadge(), badgeInfo.hasBadge());
        mBadgeInfo = badgeInfo;
    }

    public PreviewLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    /**
     * Sets mBadgeScale to 1 or 0, animating if wasBadged or isBadged is false
     * (the badge is being added or removed).
     */
    private void updateBadgeScale(boolean wasBadged, boolean isBadged) {
        float newBadgeScale = isBadged ? 1f : 0f;
        // Animate when a badge is first added or when it is removed.
        if ((wasBadged ^ isBadged) && isShown()) {
            createBadgeScaleAnimator(newBadgeScale).start();
        } else {
            mBadgeScale = newBadgeScale;
            invalidate();
        }
    }

    public Animator createBadgeScaleAnimator(float... badgeScales) {
        return ObjectAnimator.ofFloat(this, BADGE_SCALE_PROPERTY, badgeScales);
    }

    public boolean hasBadge() {
        return mBadgeInfo != null && mBadgeInfo.hasBadge();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(mPreviewLayoutRule.maxNumItems(), index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.basePreviewOffsetX;
        mTmpParams.transY += mBackground.basePreviewOffsetY;

        float intrinsicIconSize = mPreviewItemManager.getIntrinsicIconSize();
        float offsetX = mTmpParams.transX + (mTmpParams.scale * intrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * intrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mTmpParams.scale;
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    public void setBackgroundVisible(boolean visible) {
        mBackgroundIsVisible = visible;
        invalidate();
    }

    public PreviewBackground getFolderBackground() {
        return mBackground;
    }

    public PreviewItemManager getPreviewItemManager() {
        return mPreviewItemManager;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!mBackgroundIsVisible) return;

        mPreviewItemManager.recomputePreviewDrawingParams();

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackground(canvas);
        }

        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0 && !mAnimating) return;

        final int saveCount;

        if (canvas.isHardwareAccelerated()) {
            saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null,
                    Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG);
        } else {
            saveCount = canvas.save(Canvas.CLIP_SAVE_FLAG);
            if (mPreviewLayoutRule.clipToBackground()) {
                canvas.clipPath(mBackground.getClipPath(), Region.Op.INTERSECT);
            }
        }

        mPreviewItemManager.draw(canvas);

        if (mPreviewLayoutRule.clipToBackground() && canvas.isHardwareAccelerated()) {
            mBackground.clipCanvasHardware(canvas);
        }
        canvas.restoreToCount(saveCount);

        if (mPreviewLayoutRule.clipToBackground() && !mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        drawBadge(canvas);
    }

    public void drawBadge(Canvas canvas) {
        if ((mBadgeInfo != null && mBadgeInfo.hasBadge()) || mBadgeScale > 0) {
            int offsetX = mBackground.getOffsetX();
            int offsetY = mBackground.getOffsetY();
            int previewSize = (int) (mBackground.previewSize * mBackground.mScale);
            mTempBounds.set(offsetX, offsetY, offsetX + previewSize, offsetY + previewSize);

            // If we are animating to the accepting state, animate the badge out.
            float badgeScale = Math.max(0, mBadgeScale - mBackground.getScaleProgress());
            mTempSpaceForBadgeOffset.set(getWidth() - mTempBounds.right, mTempBounds.top);
            IconPalette badgePalette = IconPalette.getFolderBadgePalette(getResources());
            mBadgeRenderer.draw(canvas, badgePalette, mBadgeInfo, mTempBounds,
                    badgeScale, mTempSpaceForBadgeOffset);
        }
    }

    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    /**
     * Returns the list of preview items displayed in the icon.
     */
    public List<BubbleTextView> getPreviewItems() {
        return getPreviewItemsOnPage(0);
    }

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    public List<BubbleTextView> getPreviewItemsOnPage(int page) {
        mPreviewVerifier.setFolderInfo(mFolder.getInfo());

        List<BubbleTextView> itemsToDisplay = new ArrayList<>();
        List<BubbleTextView> itemsOnPage = mFolder.getItemsOnPage(page);
        int numItems = itemsOnPage.size();
        for (int rank = 0; rank < numItems; ++rank) {
            if (mPreviewVerifier.isItemInPreview(page, rank)) {
                itemsToDisplay.add(itemsOnPage.get(rank));
            }

            if (itemsToDisplay.size() == FolderIcon.NUM_ITEMS_IN_PREVIEW) {
                break;
            }
        }
        return itemsToDisplay;
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
    }

    @Override
    public void onItemsChanged(boolean animate) {
        mPreviewItemManager.updateItemDrawingParams(animate);
        invalidate();
        requestLayout();
    }

    @Override
    public void prepareAutoUpdate() {
    }

    @Override
    public void onAdd(ShortcutInfo item, int rank) {
        boolean wasBadged = mBadgeInfo.hasBadge();
        mBadgeInfo.addBadgeInfo(mLauncher.getPopupDataProvider().getBadgeInfoForItem(item));
        boolean isBadged = mBadgeInfo.hasBadge();
        updateBadgeScale(wasBadged, isBadged);
        invalidate();
        requestLayout();
    }

    @Override
    public void onRemove(ShortcutInfo item) {
        boolean wasBadged = mBadgeInfo.hasBadge();
        mBadgeInfo.subtractBadgeInfo(mLauncher.getPopupDataProvider().getBadgeInfoForItem(item));
        boolean isBadged = mBadgeInfo.hasBadge();
        updateBadgeScale(wasBadged, isBadged);
        invalidate();
        requestLayout();
    }

    @Override
    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title);
        setContentDescription(getContext().getString(R.string.folder_name_format, title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        // Check for a stylus button press, if it occurs cancel any long press checks.
        if (mStylusEventHelper.onMotionEvent(event)) {
            mLongPressHelper.cancelLongPress();
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLongPressHelper.postCheckForLongPress();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mLongPressHelper.cancelLongPress();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(this, event.getX(), event.getY(), mSlop)) {
                    mLongPressHelper.cancelLongPress();
                }
                break;
        }
        return result;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    public void removeListeners() {
        mInfo.removeListener(this);
        mInfo.removeListener(mFolder);
    }

    public void shrinkAndFadeIn(boolean animate) {
        // We remove and re-draw the FolderIcon in-case it has changed
        final PreviewImageView previewImage = PreviewImageView.get(getContext());
        previewImage.removeFromParent();
        copyToPreview(previewImage);

        clearLeaveBehindIfExists();

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(previewImage, 1, 1, 1);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Remove the ImageView copy of the FolderIcon and make the original visible.
                previewImage.removeFromParent();
                setVisibility(View.VISIBLE);
            }
        });
        oa.start();
        if (!animate) {
            oa.end();
        }
    }

    public boolean onHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        ((CellLayout.LayoutParams) getLayoutParams()).canReorder = true;
        if (mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) getParent().getParent();
            cl.clearFolderLeaveBehind();
        }
    }

    public void drawLeaveBehindIfExists() {
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        // While the folder is open, the position of the icon cannot change.
        lp.canReorder = false;
        if (mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) getParent().getParent();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }
    }

    public void growAndFadeOut() {
        drawLeaveBehindIfExists();

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        PreviewImageView previewImage = PreviewImageView.get(getContext());
        copyToPreview(previewImage);
        setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(previewImage, 0, 1.5f, 1.5f);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.start();
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyToPreview(PreviewImageView previewImageView) {
        previewImageView.copy(this);
        if (mFolder != null) {
            previewImageView.setPivotX(mFolder.getPivotXForIconAnimation());
            previewImageView.setPivotY(mFolder.getPivotYForIconAnimation());
            mFolder.bringToFront();
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    interface PreviewLayoutRule {
        PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
                PreviewItemDrawingParams params);
        void init(int availableSpace, float intrinsicIconSize, boolean rtl);
        float scaleForItem(int index, int totalNumItems);
        float getIconSize();
        int maxNumItems();
        boolean clipToBackground();

        boolean hasEnterExitIndices();
        int getExitIndex();
        int getEnterIndex();

    }
}
