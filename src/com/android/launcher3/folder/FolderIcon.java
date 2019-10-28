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

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

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
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.SimpleOnStylusPressListener;
import com.android.launcher3.StylusEventHelper;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.IconLabelDotView;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener, IconLabelDotView {

    @Thunk Launcher mLauncher;
    @Thunk Folder mFolder;
    private FolderInfo mInfo;

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
    ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private List<BubbleTextView> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private float mSlop;

    private Alarm mOpenAlarm = new Alarm();

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private static final Property<FolderIcon, Float> DOT_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mDotScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mDotScale = value;
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
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
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

        DeviceProfile grid = launcher.getWallpaperDeviceProfile();
        FolderIcon icon = (FolderIcon) LayoutInflater.from(group.getContext())
                .inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;

        icon.setTag(folderInfo);
        icon.setOnClickListener(ItemClickHandler.INSTANCE);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;
        icon.mDotRenderer = grid.mDotRenderer;
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

    public void animateBgShadowAndStroke() {
        mBackground.fadeInBackgroundShadow();
        mBackground.animateBackgroundStroke();
    }

    public BubbleTextView getFolderName() {
        return mFolderName;
    }

    public void getPreviewBounds(Rect outBounds) {
        mPreviewItemManager.recomputePreviewDrawingParams();
        mBackground.getBounds(outBounds);
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
        mPreviewVerifier = new FolderIconPreviewVerifier(mLauncher.getDeviceProfile().inv);
        mPreviewVerifier.setFolderInfo(mFolder.getInfo());
        updatePreviewItems(false);
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) &&
                item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void addItem(WorkspaceItemInfo item) {
        addItem(item, true);
    }

    public void addItem(WorkspaceItemInfo item, boolean animate) {
        mInfo.add(item, animate);
    }

    public void removeItem(WorkspaceItemInfo item, boolean animate) {
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
                        || (dragInfo instanceof WorkspaceItemInfo)
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

    public void performCreateAnimation(final WorkspaceItemInfo destInfo, final View destView,
            final WorkspaceItemInfo srcInfo, final DragView srcView, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        addItem(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1,
                false /* itemReturnedOnFailedDrop */);
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

    private void onDrop(final WorkspaceItemInfo item, DragView animateView, Rect finalRect,
            float scaleRelativeToDragLayer, int index,
            boolean itemReturnedOnFailedDrop) {
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
                workspace.setFinalTransitionTransform();
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform();
            }

            int numItemsInPreview = Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= MAX_NUM_ITEMS_IN_PREVIEW) {
                List<BubbleTextView> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                addItem(item, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItems());

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    for (int i = 0; i < mCurrentPreviewItems.size(); ++i) {
                        if (mCurrentPreviewItems.get(i).getTag().equals(item)) {
                            // If the item dropped is going to be in the preview, we update the
                            // index here to reflect its position in the preview.
                            index = i;
                        }
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    removeItem(item, false);
                }
            }

            if (!itemAdded) {
                addItem(item);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = (int) Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = (int) Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 0.5f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;
            dragLayer.animateView(animateView, from, to, finalAlpha,
                    1, 1, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DEACCEL_2, Interpolators.ACCEL_2,
                    null, DragLayer.ANIMATION_END_DISAPPEAR, null);

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

    public void onDrop(DragObject d, boolean itemReturnedOnFailedDrop) {
        WorkspaceItemInfo item;
        if (d.dragInfo instanceof AppInfo) {
            // Came from all apps -- make a copy
            item = ((AppInfo) d.dragInfo).makeWorkspaceItem();
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
        } else {
            item = (WorkspaceItemInfo) d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d.dragView, null, 1.0f, mInfo.contents.size(),
                itemReturnedOnFailedDrop);
    }

    public void setDotInfo(FolderDotInfo dotInfo) {
        updateDotScale(mDotInfo.hasDot(), dotInfo.hasDot());
        mDotInfo = dotInfo;
    }

    public ClippedFolderIconLayoutRule getLayoutRule() {
        return mPreviewLayoutRule;
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    /**
     * Sets mDotScale to 1 or 0, animating if wasDotted or isDotted is false
     * (the dot is being added or removed).
     */
    private void updateDotScale(boolean wasDotted, boolean isDotted) {
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((wasDotted ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    public boolean hasDot() {
        return mDotInfo != null && mDotInfo.hasDot();
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, mTmpParams);

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

    @Override
    public void setIconVisible(boolean visible) {
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

        final int saveCount = canvas.save();
        canvas.clipPath(mBackground.getClipPath());
        mPreviewItemManager.draw(canvas);
        canvas.restoreToCount(saveCount);

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        drawDot(canvas);
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;
            BubbleTextView.getIconBounds(this, iconBounds,
                    mLauncher.getWallpaperDeviceProfile().iconSizePx);
            float iconScale = (float) mBackground.previewSize / iconBounds.width();
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getScaleProgress());
            mDotParams.color = mBackground.getDotColor();
            mDotRenderer.draw(canvas, mDotParams);
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

            if (itemsToDisplay.size() == MAX_NUM_ITEMS_IN_PREVIEW) {
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
        updatePreviewItems(animate);
        invalidate();
        requestLayout();
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItems());
    }

    @Override
    public void prepareAutoUpdate() {
    }

    @Override
    public void onAdd(WorkspaceItemInfo item, int rank) {
        boolean wasDotted = mDotInfo.hasDot();
        mDotInfo.addDotInfo(mLauncher.getDotInfoForItem(item));
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
        invalidate();
        requestLayout();
    }

    @Override
    public void onRemove(WorkspaceItemInfo item) {
        boolean wasDotted = mDotInfo.hasDot();
        mDotInfo.subtractDotInfo(mLauncher.getDotInfoForItem(item));
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
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

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }
}
