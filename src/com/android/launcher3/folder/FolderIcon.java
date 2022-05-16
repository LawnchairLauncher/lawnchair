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
 *
 * Modifications copyright 2022 Lawnchair
 */

package com.android.launcher3.folder;

import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dot.FolderDotInfo;
import com.android.launcher3.dragndrop.BaseItemDragListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.FolderListener;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.IconLabelDotView;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;


/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener, IconLabelDotView,
        DraggableView, Reorderable {

    @Thunk ActivityContext mActivity;
    @Thunk Folder mFolder;
    public FolderInfo mInfo;

    private CheckLongPressHelper mLongPressHelper;

    static final int DROP_IN_ANIMATION_DURATION = 400;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;

    PreviewBackground mBackground = new PreviewBackground();
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private List<WorkspaceItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private Alarm mOpenAlarm = new Alarm();

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    private final PointF mTranslationForMoveFromCenterAnimation = new PointF(0, 0);
    private float mTranslationXForTaskbarAlignmentAnimation = 0f;

    private final PointF mTranslationForReorderBounce = new PointF(0, 0);
    private final PointF mTranslationForReorderPreview = new PointF(0, 0);
    private float mScaleForReorderBounce = 1f;

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
        mPreviewLayoutRule = new ClippedFolderIconLayoutRule();
        mPreviewItemManager = new PreviewItemManager(this);
        mDotParams = new DotRenderer.DrawParams();
    }

    public static <T extends Context & ActivityContext> FolderIcon inflateFolderAndIcon(int resId,
            T activityContext, ViewGroup group, FolderInfo folderInfo) {
        Folder folder = Folder.fromXml(activityContext);

        FolderIcon icon = inflateIcon(resId, activityContext, group, folderInfo);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        return icon;
    }

    public static FolderIcon inflateIcon(int resId, ActivityContext activity, ViewGroup group,
            FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
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
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));

        // Keep the notification dot up to date with the sum of all the content's dots.
        FolderDotInfo folderDotInfo = new FolderDotInfo();
        for (WorkspaceItemInfo si : folderInfo.contents) {
            folderDotInfo.addDotInfo(activity.getDotInfoForItem(si));
        }
        icon.setDotInfo(folderDotInfo);

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = new FolderGridOrganizer(activity.getDeviceProfile().inv);
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);

        folderInfo.addListener(icon);

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
        // The preview items go outside of the bounds of the background.
        Utilities.scaleRectAboutCenter(outBounds, ICON_OVERLAP_FACTOR);
    }

    public float getBackgroundStrokeWidth() {
        return mBackground.getStrokeWidth();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
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
        mInfo.add(item, true);
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
        }
    };

    public Drawable prepareCreateAnimation(final View destView) {
        return mPreviewItemManager.prepareCreateAnimation(destView);
    }

    public void performCreateAnimation(final WorkspaceItemInfo destInfo, final View destView,
            final WorkspaceItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        final DragView srcView = d.dragView;
        prepareCreateAnimation(destView);
        addItem(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        mPreviewItemManager.createFirstItemAnimation(false /* reverse */, null)
                .start();

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, d, dstRect, scaleRelativeToDragLayer, 1,
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

    private void onDrop(final WorkspaceItemInfo item, DragObject d, Rect finalRect,
            float scaleRelativeToDragLayer, int index, boolean itemReturnedOnFailedDrop) {
        item.cellX = -1;
        item.cellY = -1;
        DragView animateView = d.dragView;
        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null && mActivity instanceof Launcher) {
            final Launcher launcher = (Launcher) mActivity;
            DragLayer dragLayer = launcher.getDragLayer();
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace workspace = launcher.getWorkspace();
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
                List<WorkspaceItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                mInfo.add(item, index, false);
                mCurrentPreviewItems.clear();
                mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));

                if (!oldPreviewItems.equals(mCurrentPreviewItems)) {
                    int newIndex = mCurrentPreviewItems.indexOf(item);
                    if (newIndex >= 0) {
                        // If the item dropped is going to be in the preview, we update the
                        // index here to reflect its position in the preview.
                        index = newIndex;
                    }

                    mPreviewItemManager.hidePreviewItem(index, true);
                    mPreviewItemManager.onDrop(oldPreviewItems, mCurrentPreviewItems, item);
                    itemAdded = true;
                } else {
                    removeItem(item, false);
                }
            }

            if (!itemAdded) {
                mInfo.add(item, index, true);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                    center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;

            // Account for potentially different icon sizes with non-default grid settings
            if (d.dragSource instanceof AllAppsContainerView) {
                DeviceProfile grid = mActivity.getDeviceProfile();
                float containerScale = (1f * grid.iconSizePx / grid.allAppsIconSizePx);
                finalScale *= containerScale;
            }

            final int finalIndex = index;
            dragLayer.animateView(animateView, to, finalAlpha,
                    finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DEACCEL_2,
                    () -> {
                        mPreviewItemManager.hidePreviewItem(finalIndex, false);
                        mFolder.showItem(item);
                    }, 
                    DragLayer.ANIMATION_END_DISAPPEAR, null);

            mFolder.hideItem(item);

            if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);

            FolderNameInfos nameInfos = new FolderNameInfos();
            if (FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
                Executors.MODEL_EXECUTOR.post(() -> {
                    d.folderNameProvider.getSuggestedFolderName(
                            getContext(), mInfo.contents, nameInfos);
                    showFinalView(finalIndex, item, nameInfos, d.logInstanceId);
                });
            } else {
                showFinalView(finalIndex, item, nameInfos, d.logInstanceId);
            }
        } else {
            addItem(item);
        }
    }

    private void showFinalView(int finalIndex, final WorkspaceItemInfo item,
            FolderNameInfos nameInfos, InstanceId instanceId) {
        postDelayed(() -> {
            setLabelSuggestion(nameInfos, instanceId);
            invalidate();
        }, DROP_IN_ANIMATION_DURATION);
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
        if (!FeatureFlags.FOLDER_NAME_SUGGEST.get()) {
            return;
        }
        if (!mInfo.getLabelState().equals(LabelState.UNLABELED)) {
            return;
        }
        if (nameInfos == null || !nameInfos.hasSuggestions()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS);
            return;
        }
        if (!nameInfos.hasPrimary()) {
            StatsLogManager.newInstance(getContext()).logger()
                    .withInstanceId(instanceId)
                    .withItemInfo(mInfo)
                    .log(LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY);
            return;
        }
        CharSequence newTitle = nameInfos.getLabels()[0];
        FromState fromState = mInfo.getFromLabelState();

        mInfo.setTitle(newTitle, mFolder.mLauncherDelegate.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.mFolderName.setText(mInfo.title);

        // Logging for folder creation flow
        StatsLogManager.newInstance(getContext()).logger()
                .withInstanceId(instanceId)
                .withItemInfo(mInfo)
                .withFromState(fromState)
                .withToState(ToState.TO_SUGGESTION0)
                // When LAUNCHER_FOLDER_LABEL_UPDATED event.edit_text does not have delimiter,
                // event is assumed to be folder creation on the server side.
                .withEditText(newTitle.toString())
                .log(LAUNCHER_FOLDER_AUTO_LABELED);
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
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.contents.size(),
                itemReturnedOnFailedDrop
        );
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

    public boolean getIconVisible() {
        return mBackgroundIsVisible;
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

        if (mCurrentPreviewItems.isEmpty() && !mAnimating) return;

        mPreviewItemManager.draw(canvas);

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        drawDot(canvas);
    }

    public void drawDot(Canvas canvas) {
        if (!mForceHideDot && ((mDotInfo != null && mDotInfo.hasDot()) || mDotScale > 0)) {
            Rect iconBounds = mDotParams.iconBounds;

            Utilities.setRectToViewCenter(this, mActivity.getDeviceProfile().iconSizePx,
                    iconBounds);
            iconBounds.offsetTo(iconBounds.left, getPaddingTop());
            float iconScale = (float) mBackground.previewSize / iconBounds.width();
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getScaleProgress());
            mDotParams.color = mBackground.getDotColor();
            mDotRenderer.draw(canvas, mDotParams, mDotInfo == null ? -1 : mDotInfo.getNotificationCount());
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
     * Returns the list of items which should be visible in the preview
     */
    public List<WorkspaceItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.contents);
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
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<WorkspaceItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
    }

    @Override
    public void onAdd(WorkspaceItemInfo item, int rank) {
        updatePreviewItems(false);
        boolean wasDotted = mDotInfo.hasDot();
        mDotInfo.addDotInfo(mActivity.getDotInfoForItem(item));
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
        setContentDescription(getAccessiblityTitle(mInfo.title));
        invalidate();
        requestLayout();
    }

    @Override
    public void onRemove(List<WorkspaceItemInfo> items) {
        updatePreviewItems(false);
        boolean wasDotted = mDotInfo.hasDot();
        items.stream().map(mActivity::getDotInfoForItem).forEach(mDotInfo::subtractDotInfo);
        boolean isDotted = mDotInfo.hasDot();
        updateDotScale(wasDotted, isDotted);
        setContentDescription(getAccessiblityTitle(mInfo.title));
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title);
        setContentDescription(getAccessiblityTitle(title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }

        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        super.onTouchEvent(event);
        mLongPressHelper.onTouchEvent(event);
        // Keep receiving the rest of the events
        return true;
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        mTouchArea.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
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

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
    }

    private void updateTranslation() {
        super.setTranslationX(mTranslationForReorderBounce.x + mTranslationForReorderPreview.x
                + mTranslationForMoveFromCenterAnimation.x
                + mTranslationXForTaskbarAlignmentAnimation);
        super.setTranslationY(mTranslationForReorderBounce.y + mTranslationForReorderPreview.y
                + mTranslationForMoveFromCenterAnimation.y);
    }

    public void setReorderBounceOffset(float x, float y) {
        mTranslationForReorderBounce.set(x, y);
        updateTranslation();
    }

    public void getReorderBounceOffset(PointF offset) {
        offset.set(mTranslationForReorderBounce);
    }

    /**
     * Sets translationX value for taskbar to launcher alignment animation
     */
    public void setTranslationForTaskbarAlignmentAnimation(float translationX) {
        mTranslationXForTaskbarAlignmentAnimation = translationX;
        updateTranslation();
    }

    /**
     * Returns translation values for taskbar to launcher alignment animation
     */
    public float getTranslationXForTaskbarAlignmentAnimation() {
        return mTranslationXForTaskbarAlignmentAnimation;
    }

    /**
     * Sets translation values for move from center animation
     */
    public void setTranslationForMoveFromCenterAnimation(float x, float y) {
        mTranslationForMoveFromCenterAnimation.set(x, y);
        updateTranslation();
    }

    @Override
    public void setReorderPreviewOffset(float x, float y) {
        mTranslationForReorderPreview.set(x, y);
        updateTranslation();
    }

    @Override
    public void getReorderPreviewOffset(PointF offset) {
        offset.set(mTranslationForReorderPreview);
    }

    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    public View getView() {
        return this;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        getPreviewBounds(bounds);
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        int size = mInfo.contents.size();
        if (size < MAX_NUM_ITEMS_IN_PREVIEW) {
            return getContext().getString(R.string.folder_name_format_exact, title, size);
        } else {
            return getContext().getString(R.string.folder_name_format_overflow, title,
                    MAX_NUM_ITEMS_IN_PREVIEW);
        }
    }

    /**
     * Interface that provides callbacks to a parent ViewGroup that hosts this FolderIcon.
     */
    public interface FolderIconParent {
        /**
         * Tells the FolderIconParent to draw a "leave-behind" when the Folder is open and leaving a
         * gap where the FolderIcon would be when the Folder is closed.
         */
        void drawFolderLeaveBehindForIcon(FolderIcon child);
        /**
         * Tells the FolderIconParent to stop drawing the "leave-behind" as the Folder is closed.
         */
        void clearFolderLeaveBehind(FolderIcon child);
    }
}
