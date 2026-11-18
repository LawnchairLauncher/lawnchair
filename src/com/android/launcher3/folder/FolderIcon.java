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
 * Modifications copyright 2025 Lawnchair
 */

package com.android.launcher3.folder;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderGridOrganizer.createFolderGridOrganizer;
import static com.android.launcher3.folder.PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_PRIMARY;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_AUTO_LABELING_SKIPPED_EMPTY_SUGGESTIONS;
import static com.android.launcher3.model.data.FolderInfo.willAcceptItemType;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.app.animation.Interpolators;
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
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
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
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.FolderInfo.LabelState;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemFactory;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingIconViewCompanion;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import app.lawnchair.util.LawnchairUtilsKt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FloatingIconViewCompanion,
        DraggableView, Reorderable {

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
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

    PreviewBackground mBackground = new PreviewBackground(getContext());
    private boolean mBackgroundIsVisible = true;

    FolderGridOrganizer mPreviewVerifier;
    ClippedFolderIconLayoutRule mPreviewLayoutRule;
    private PreviewItemManager mPreviewItemManager;
    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0);
    private List<ItemInfo> mCurrentPreviewItems = new ArrayList<>();

    boolean mAnimating = false;

    private Alarm mOpenAlarm = new Alarm(Looper.getMainLooper());

    private boolean mForceHideDot;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private final FolderDotInfo mDotInfo = new FolderDotInfo();
    private DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    private DotRenderer.DrawParams mDotParams;
    private float mDotScale;
    private Animator mDotScaleAnim;

    private Rect mTouchArea = new Rect();

    private float mScaleForReorderBounce = 1f;

    // Lawnchair: large folder support. A large folder spans several cells and shows its first
    // items as directly launchable icons. Remaining items either go to an overflow slot that
    // opens the folder, or the tile scrolls (see FolderInfo#FLAG_LARGE_FOLDER_SCROLL).
    private static final float LARGE_TILE_PADDING_FACTOR = 0.06f;
    private static final float LARGE_ICON_SLOT_FACTOR = 0.80f;
    private static final float LARGE_OVERFLOW_ICON_FACTOR = 0.80f;
    private static final float LARGE_PRESSED_SCALE = 0.88f;
    private static final float LARGE_CORNER_FACTOR = 0.3f;
    private static final int LARGE_OVERFLOW_PREVIEW_COUNT = 4;
    private static final int LARGE_SCROLL_SETTLE_DURATION = 250;
    private static final float LARGE_SCROLL_FLING_FACTOR = 0.15f;
    private final Rect mLargeTileRect = new Rect();
    private final Rect mLargeViewport = new Rect();
    private float mLargeSlotWidth;
    private float mLargeSlotHeight;
    private int mLargeSlotIconSize;
    private final ArrayList<PreviewItemDrawingParams> mLargeItems = new ArrayList<>();
    private final ArrayList<PreviewItemDrawingParams> mLargeOverflowItems = new ArrayList<>();
    private final Paint mLargeTilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLargeIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mTmpRect = new Rect();
    private final Rect mTmpRect2 = new Rect();
    private boolean mLargeHasOverflow;
    private int mSmallLabelTopMargin;
    private int mLargePressedSlot = -1;
    private int mLargeTouchedSlot = -1;
    private int mLargeLaunchSlot = -1;
    private int mLargeHiddenSlot = -1;
    private boolean mLargeAccepting;
    private float mLargeScrollOffset;
    private float mLargeScrollStartOffset;
    private float mLargeDownX;
    private float mLargeDownY;
    private boolean mLargeScrolling;
    private boolean mLargeDisallowingIntercept;
    @Nullable private VelocityTracker mLargeVelocityTracker;
    @Nullable private ValueAnimator mLargeScrollAnimator;

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

    /**
     * Builds a FolderIcon to be added to the activity.
     * This method doesn't add any listeners to the FolderInfo, and hence any changes to the info
     * will not be reflected in the folder.
     */
    public static FolderIcon inflateIcon(int resId, ActivityContext activity,
            @Nullable ViewGroup group, FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = activity.getDeviceProfile();
        LayoutInflater inflater = (group != null)
                ? LayoutInflater.from(group.getContext())
                : activity.getLayoutInflater();
        FolderIcon icon = (FolderIcon) inflater.inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        if (folderInfo.container == ItemInfo.NO_ID) {
            lp.topMargin = grid.getAllAppsProfile().getIconSizePx() + grid.getAllAppsProfile().getIconDrawablePaddingPx();
            icon.mBackground = new PreviewBackground(activity.getDragLayer().getContext());
        } else {
            lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;
        }
        icon.mSmallLabelTopMargin = lp.topMargin;

        icon.setTag(folderInfo);
        icon.setOnClickListener(activity.getItemOnClickListener());
        icon.mInfo = folderInfo;
        icon.mActivity = activity;
        icon.mDotRenderer = grid.mDotRendererWorkSpace;

        icon.setContentDescription(icon.getAccessiblityTitle(folderInfo.title));
        icon.updateDotInfo();

        icon.setAccessibilityDelegate(activity.getAccessibilityDelegate());

        icon.mPreviewVerifier = createFolderGridOrganizer(activity.getDeviceProfile());
        icon.mPreviewVerifier.setFolderInfo(folderInfo);
        icon.updatePreviewItems(false);
        icon.rebuildLargeFolderItems();

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
        if (isLargeFolderMode()) {
            // While an app launched from the large folder animates, it originates from (and
            // returns to) its own slot rather than the whole tile.
            ensureLargeFolderLayout();
            if (mLargeLaunchSlot >= 0 && mLargeLaunchSlot < getLargeSlotCount()) {
                getLargeSlotIconRect(mLargeLaunchSlot, outBounds);
            } else {
                outBounds.set(mLargeTileRect);
            }
            return;
        }
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
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        if (isLargeFolderMode()) {
            mLargeAccepting = true;
            invalidate();
        } else {
            CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
            CellLayout cl = (CellLayout) getParent().getParent();
            mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
        }
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof WorkspaceItemFactory)
                        || (dragInfo instanceof PendingAddShortcutInfo)
                        || Folder.willAccept(dragInfo))) {
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

    public void performCreateAnimation(final ItemInfo destInfo, final View destView,
            final ItemInfo srcInfo, final DragObject d, Rect dstRect,
            float scaleRelativeToDragLayer) {
        prepareCreateAnimation(destView);
        getFolder().addFolderContent(destInfo);
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
        if (mLargeAccepting) {
            mLargeAccepting = false;
            invalidate();
        }
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ItemInfo item, DragObject d, Rect finalRect,
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
            if (to == null && !isInAppDrawer()) {
                to = new Rect();
                Workspace<?> workspace = launcher.getWorkspace();
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
                List<ItemInfo> oldPreviewItems = new ArrayList<>(mCurrentPreviewItems);
                getFolder().addFolderContent(item, index, false);
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
                    getFolder().removeFolderContent(false, item);
                }
            }

            if (!itemAdded) {
                getFolder().addFolderContent(item, index, true);
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, numItemsInPreview, center);
            center[0] = Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = Math.round(scaleRelativeToDragLayer * center[1]);

            // Lawnchair-TODO: if to is null we skip immediately to place the item. destination can be null (or nowhere)
            if (to != null) {
                to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                        center[1] - animateView.getMeasuredHeight() / 2);

                float finalAlpha = index < MAX_NUM_ITEMS_IN_PREVIEW ? 1f : 0f;

                float finalScale = scale * scaleRelativeToDragLayer;

                // Account for potentially different icon sizes with non-default grid settings
                if (d.dragSource instanceof ActivityAllAppsContainerView) {
                    DeviceProfile grid = mActivity.getDeviceProfile();
                    float containerScale = (1f * grid.iconSizePx
                            / grid.getAllAppsProfile().getIconSizePx());
                    finalScale *= containerScale;
                }

                final int finalIndex = index;
                dragLayer.animateView(animateView, to, finalAlpha,
                        finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                        Interpolators.DECELERATE_2,
                        () -> {
                            mPreviewItemManager.hidePreviewItem(finalIndex, false);
                            mFolder.showItem(item);
                        },
                        DragLayer.ANIMATION_END_DISAPPEAR, null);

                mFolder.hideItem(item);

                if (!itemAdded) mPreviewItemManager.hidePreviewItem(index, true);
            }
            d.folderNameSuggestionLoader.getSuggestedFolderName(mInfo.getAppContents(),
                    folderNameInfos -> postDelayed(() -> {
                        setLabelSuggestion(folderNameInfos, d.logInstanceId);
                        invalidate();
                    }, DROP_IN_ANIMATION_DURATION));

        } else {
            getFolder().addFolderContent(item);
        }
    }
    
    public boolean isInAppDrawer() {
        return mInfo.container == ItemInfo.NO_ID;
    }

    /**
     * Set the suggested folder name.
     */
    public void setLabelSuggestion(FolderNameInfos nameInfos, InstanceId instanceId) {
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

        mInfo.setTitle(newTitle, mActivity.getModelWriter());
        onTitleChanged(mInfo.title);
        mFolder.getFolderName().setText(mInfo.title);

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
        ItemInfo item;
        if (d.dragInfo instanceof WorkspaceItemFactory) {
            // Came from all apps -- make a copy
            item = ((WorkspaceItemFactory) d.dragInfo).makeWorkspaceItem(getContext());
        } else if (d.dragSource instanceof BaseItemDragListener){
            // Came from a different window -- make a copy
            if (d.dragInfo instanceof AppPairInfo) {
                // dragged item is app pair
                item = new AppPairInfo((AppPairInfo) d.dragInfo);
            } else {
                // dragged item is WorkspaceItemInfo
                item = new WorkspaceItemInfo((WorkspaceItemInfo) d.dragInfo);
            }
        } else {
            item = d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d, null, 1.0f,
                itemReturnedOnFailedDrop ? item.rank : mInfo.getContents().size(),
                itemReturnedOnFailedDrop
        );
    }

    /** Keep the notification dot up to date with the sum of all the content's dots. */
    public void updateDotInfo() {
        boolean hadDot = mDotInfo.hasDot();
        mDotInfo.reset();
        for (ItemInfo si : mInfo.getContents()) {
            mDotInfo.addDotInfo(mActivity.getDotInfoForItem(si));
        }
        boolean isDotted = mDotInfo.hasDot();
        float newDotScale = isDotted ? 1f : 0f;
        // Animate when a dot is first added or when it is removed.
        if ((hadDot ^ isDotted) && isShown()) {
            animateDotScale(newDotScale);
        } else {
            cancelDotScaleAnim();
            mDotScale = newDotScale;
            invalidate();
        }
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
        if (isLargeFolderMode()) {
            computeLargeFolderLayout(getWidth(), getHeight());
            int slot = Math.min(index, getLargeSlotCount() - 1);
            Rect r = mTmpRect;
            if (slot >= 0) {
                getLargeSlotIconRect(slot, r);
            }
            if (slot < 0 || !Rect.intersects(r, mLargeViewport)) {
                center[0] = mLargeTileRect.centerX();
                center[1] = mLargeTileRect.centerY();
                return (float) mLargeSlotIconSize / mActivity.getDeviceProfile().iconSizePx;
            }
            center[0] = r.centerX();
            center[1] = r.centerY();
            return (float) r.width() / mActivity.getDeviceProfile().iconSizePx;
        }
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
        if (isLargeFolderMode() && mLargeLaunchSlot >= 0) {
            // An app launched from a large folder: only hide the slot it is animating from.
            mLargeHiddenSlot = visible ? -1 : mLargeLaunchSlot;
            invalidate();
            return;
        }
        mLargeHiddenSlot = -1;
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

        if (isLargeFolderMode()) {
            drawLargeFolder(canvas);
            return;
        }

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
            // FolderIcon draws the icon to be top-aligned (with padding) & horizontally-centered
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            iconBounds.left = (getWidth() - iconSize) / 2;
            iconBounds.right = iconBounds.left + iconSize;
            iconBounds.top = getPaddingTop();
            iconBounds.bottom = iconBounds.top + iconSize;

            float iconScale = (float) mBackground.previewSize / iconSize;
            Utilities.scaleRectAboutCenter(iconBounds, iconScale);

            // If we are animating to the accepting state, animate the dot out.
            mDotParams.scale = Math.max(0, mDotScale - mBackground.getAcceptScaleProgress());
            mDotParams.dotColor = mBackground.getDotColor();
            mDotRenderer.draw(canvas, mDotParams);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        FrameLayout.LayoutParams nameLp =
                (FrameLayout.LayoutParams) mFolderName.getLayoutParams();
        if (isLargeFolderMode()) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            computeLargeFolderLayout(width, height);
            // The label sits below the tile, where the labels of the last covered row would be.
            int labelTop = mLargeTileRect.bottom
                    + mActivity.getDeviceProfile().iconDrawablePaddingPx;
            nameLp.topMargin = labelTop - getPaddingTop();
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        nameLp.topMargin = mSmallLabelTopMargin;
        boolean shouldCenterIcon = mActivity.getDeviceProfile().iconCenterVertically;
        if (shouldCenterIcon) {
            int iconSize = mActivity.getDeviceProfile().iconSizePx;
            Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
            int cellHeightPx = iconSize + mFolderName.getCompoundDrawablePadding()
                    + (int) Math.ceil(fm.bottom - fm.top);
            setPadding(getPaddingLeft(), (MeasureSpec.getSize(heightMeasureSpec)
                    - cellHeightPx) / 2, getPaddingRight(), getPaddingBottom());
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** Sets the visibility of the icon's title text */
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
    public List<ItemInfo> getPreviewItemsOnPage(int page) {
        return mPreviewVerifier.setFolderInfo(mInfo).previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || isLargeFolderDrawable(who)
                || super.verifyDrawable(who);
    }

    private void updatePreviewItems(boolean animate) {
        mPreviewItemManager.updatePreviewItems(animate);
        mCurrentPreviewItems.clear();
        mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
    }

    /**
     * Updates the preview items which match the provided condition
     */
    public void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        mPreviewItemManager.updatePreviewItems(itemCheck);
        if (isLargeFolderMode()) {
            for (PreviewItemDrawingParams p : mLargeItems) {
                if (p.item != null && itemCheck.test(p.item)) {
                    mPreviewItemManager.setDrawable(p, p.item);
                }
            }
            for (PreviewItemDrawingParams p : mLargeOverflowItems) {
                if (p.item != null && itemCheck.test(p.item)) {
                    mPreviewItemManager.setDrawable(p, p.item);
                }
            }
            invalidate();
        }
    }

    public void onItemsChanged(boolean animate) {
        rebuildLargeFolderItems();
        updatePreviewItems(false);
        updateDotInfo();
        setContentDescription(getAccessiblityTitle(mInfo.title));
        updatePreviewItems(animate);
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

        if (isLargeFolderMode() && handleLargeFolderTouch(event)) {
            // Scrolling inside a large folder: cancel any pending click / long press.
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel);
            mLongPressHelper.onTouchEvent(cancel);
            cancel.recycle();
            return true;
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
        if (isLargeFolderMode()) {
            // Tile plus the label below it.
            mTouchArea.set(mLargeTileRect.left, mLargeTileRect.top, mLargeTileRect.right,
                    getHeight());
            return !mTouchArea.contains((int) x, (int) y);
        }
        mTouchArea.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom());
        return !mTouchArea.contains((int) x, (int) y);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    @Override
    public boolean performLongClick() {
        // A long press never launches the touched slot afterwards.
        mLargeTouchedSlot = -1;
        setLargePressedSlot(-1);
        // Let the drag layer receive the rest of the gesture so the folder can be dragged.
        setLargeDisallowIntercept(false);
        return super.performLongClick();
    }

    private boolean isInHotseat() {
        return mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    public void clearLeaveBehindIfExists() {
        if (isInAppDrawer()) return;
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).clearFolderLeaveBehind(this);
        }
    }

    public void drawLeaveBehindIfExists() {
        if (isInAppDrawer()) return;
        if (getParent() instanceof FolderIconParent) {
            ((FolderIconParent) getParent()).drawFolderLeaveBehindForIcon(this);
        }
    }

    public void onFolderClose(int currentPage) {
        mPreviewItemManager.onFolderClose(currentPage);
        if (isLargeFolderMode()) {
            // Picks up icons that were updated (e.g. loaded in high-res) while the folder was open.
            rebuildLargeFolderItems();
            invalidate();
        }
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        if (isLargeFolderMode()) {
            bounds.set(mLargeTileRect);
            return;
        }
        getPreviewBounds(bounds);
    }

    /**
     * Returns a formatted accessibility title for folder
     */
    public String getAccessiblityTitle(CharSequence title) {
        if (title == null) {
            // Avoids "Talkback -> Folder: null" announcement.
            title = getContext().getString(R.string.unnamed_folder);
        }
        int size = mInfo.getContents().size();
        if (size < MAX_NUM_ITEMS_IN_PREVIEW) {
            return getContext().getString(R.string.folder_name_format_exact, title, size);
        } else {
            return getContext().getString(R.string.folder_name_format_overflow, title,
                    MAX_NUM_ITEMS_IN_PREVIEW);
        }
    }

    @Override
    public void onHoverChanged(boolean hovered) {
        super.onHoverChanged(hovered);
        if (enableCursorHoverStates()) {
            mBackground.setHovered(hovered);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Lawnchair: large folders
    // ---------------------------------------------------------------------------------------

    /**
     * Returns true if this icon is rendered as a large folder: a multi-cell tile whose items can
     * be launched directly.
     */
    public boolean isLargeFolderMode() {
        return mInfo != null && mInfo.isLargeFolder()
                && mInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP;
    }

    /** Returns true if this large folder scrolls inside its tile instead of overflowing. */
    public boolean isLargeFolderScrollMode() {
        return isLargeFolderMode() && mInfo.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL);
    }

    private boolean isLargeScrollHorizontal() {
        return mInfo.hasOption(FolderInfo.FLAG_LARGE_FOLDER_SCROLL_HORIZONTAL);
    }

    /**
     * Must be called after the folder's span or large folder options changed.
     */
    public void onLargeFolderSpanChanged() {
        mLargeLaunchSlot = -1;
        mLargeHiddenSlot = -1;
        mLargeTouchedSlot = -1;
        mLargePressedSlot = -1;
        mLargeAccepting = false;
        cancelLargeScrollAnimation();
        mLargeScrollOffset = 0;
        mLargeTileRect.setEmpty();
        mLargeViewport.setEmpty();
        rebuildLargeFolderItems();
        if (!isLargeFolderMode()) {
            updatePreviewItems(false);
        }
        requestLayout();
        invalidate();
    }

    /**
     * Returns the item to launch directly for the tap that triggered the current click, or null
     * if the folder should be opened instead (overflow slot, empty slot, label or a non-touch
     * click such as accessibility). Consumes the tap.
     */
    @Nullable
    public WorkspaceItemInfo consumeLargeFolderTapTarget() {
        int slot = mLargeTouchedSlot;
        mLargeTouchedSlot = -1;
        if (!isLargeFolderMode() || slot < 0 || slot >= mLargeItems.size()) {
            return null;
        }
        if (mLargeItems.get(slot).item instanceof WorkspaceItemInfo wii) {
            mLargeLaunchSlot = slot;
            return wii;
        }
        return null;
    }

    /**
     * Clears the state of a previous direct launch, e.g. when the folder itself is opened.
     */
    public void clearLargeFolderLaunchState() {
        if (mLargeLaunchSlot != -1 || mLargeHiddenSlot != -1) {
            mLargeLaunchSlot = -1;
            mLargeHiddenSlot = -1;
            invalidate();
        }
    }

    /** Returns the corner radius of the large folder tile, in this view's coordinates. */
    public float getLargeFolderCornerRadius() {
        return Math.min(mActivity.getDeviceProfile().iconSizePx * LARGE_CORNER_FACTOR,
                Math.min(mLargeTileRect.width(), mLargeTileRect.height()) / 2f);
    }

    private void rebuildLargeFolderItems() {
        mLargeItems.clear();
        mLargeOverflowItems.clear();
        mLargeHasOverflow = false;
        mLargeLaunchSlot = -1;
        mLargeHiddenSlot = -1;
        if (!isLargeFolderMode()) {
            return;
        }
        ArrayList<ItemInfo> contents = new ArrayList<>(mInfo.getContents());
        contents.sort(Comparator.comparingInt(item -> item.rank));
        if (isLargeFolderScrollMode()) {
            for (ItemInfo item : contents) {
                mLargeItems.add(newLargeFolderParams(item));
            }
            clampLargeScroll();
            return;
        }
        int slots = Math.max(1, mInfo.spanX * mInfo.spanY);
        mLargeHasOverflow = contents.size() > slots;
        int direct = mLargeHasOverflow ? slots - 1 : contents.size();
        for (int i = 0; i < direct; i++) {
            mLargeItems.add(newLargeFolderParams(contents.get(i)));
        }
        if (mLargeHasOverflow) {
            int end = Math.min(contents.size(), direct + LARGE_OVERFLOW_PREVIEW_COUNT);
            for (int i = direct; i < end; i++) {
                mLargeOverflowItems.add(newLargeFolderParams(contents.get(i)));
            }
        }
    }

    private PreviewItemDrawingParams newLargeFolderParams(ItemInfo item) {
        PreviewItemDrawingParams p = new PreviewItemDrawingParams(0, 0, 1);
        mPreviewItemManager.setDrawable(p, item);
        return p;
    }

    private boolean isLargeFolderDrawable(Drawable who) {
        for (PreviewItemDrawingParams p : mLargeItems) {
            if (p.drawable == who) return true;
        }
        for (PreviewItemDrawingParams p : mLargeOverflowItems) {
            if (p.drawable == who) return true;
        }
        return false;
    }

    /**
     * Lays out the tile so that it lines up with regular icons: it starts at the icon of the
     * top-left covered cell and ends at the icon of the bottom-right covered cell.
     */
    private void computeLargeFolderLayout(int width, int height) {
        if (width <= 0 || height <= 0 || mInfo == null) {
            mLargeTileRect.setEmpty();
            mLargeViewport.setEmpty();
            return;
        }
        DeviceProfile dp = mActivity.getDeviceProfile();
        int spanX = Math.max(1, mInfo.spanX);
        int spanY = Math.max(1, mInfo.spanY);
        float cellWidth = (width - (spanX - 1f) * dp.cellLayoutBorderSpacePx.x) / spanX;
        float cellHeight = (height - (spanY - 1f) * dp.cellLayoutBorderSpacePx.y) / spanY;

        int iconSize = dp.iconSizePx;
        Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
        int textHeight = (int) Math.ceil(fm.bottom - fm.top);
        int contentHeight = iconSize + dp.iconDrawablePaddingPx + textHeight;
        int iconTop = dp.cellYPaddingPx >= 0
                ? dp.cellYPaddingPx
                : (int) Math.max(0, (cellHeight - contentHeight) / 2f);
        int iconLeft = (int) Math.max(0, (cellWidth - iconSize) / 2f);
        int tileBottom = Math.round(height - cellHeight + iconTop + iconSize);
        mLargeTileRect.set(iconLeft, iconTop, width - iconLeft,
                Math.max(iconTop + iconSize, Math.min(height, tileBottom)));
        int tileColor = LawnchairUtilsKt.resolveFolderPreviewColor(getContext());
        mLargeTilePaint.setColor(tileColor);
        boolean lightTile = ColorUtils.calculateLuminance(tileColor | 0xFF000000) > 0.5;
        mLargeIndicatorPaint.setColor(lightTile ? 0x66000000 : 0x99FFFFFF);

        int pad = Math.round(Math.min(mLargeTileRect.width(), mLargeTileRect.height())
                * LARGE_TILE_PADDING_FACTOR);
        mLargeViewport.set(mLargeTileRect.left + pad, mLargeTileRect.top + pad,
                mLargeTileRect.right - pad, mLargeTileRect.bottom - pad);
        mLargeSlotWidth = mLargeViewport.width() / (float) spanX;
        mLargeSlotHeight = mLargeViewport.height() / (float) spanY;
        mLargeSlotIconSize = Math.round(Math.min(
                Math.min(mLargeSlotWidth, mLargeSlotHeight) * LARGE_ICON_SLOT_FACTOR, iconSize));
        clampLargeScroll();
    }

    private void ensureLargeFolderLayout() {
        if (mLargeViewport.isEmpty()) {
            computeLargeFolderLayout(getWidth(), getHeight());
        }
    }

    /** Number of touchable slots: all grid cells, or every item when scrolling. */
    private int getLargeSlotCount() {
        return isLargeFolderScrollMode()
                ? mLargeItems.size()
                : Math.max(1, mInfo.spanX * mInfo.spanY);
    }

    /** Bounds of the given slot in this view's coordinates, with the scroll applied. */
    private void getLargeSlotRect(int slot, Rect out) {
        int spanX = Math.max(1, mInfo.spanX);
        int spanY = Math.max(1, mInfo.spanY);
        float x;
        float y;
        if (isLargeFolderScrollMode() && isLargeScrollHorizontal()) {
            int perPage = spanX * spanY;
            int page = slot / perPage;
            int inPage = slot % perPage;
            x = (page * spanX + inPage % spanX) * mLargeSlotWidth - mLargeScrollOffset;
            y = (inPage / spanX) * mLargeSlotHeight;
        } else {
            x = (slot % spanX) * mLargeSlotWidth;
            y = (slot / spanX) * mLargeSlotHeight;
            if (isLargeFolderScrollMode()) {
                y -= mLargeScrollOffset;
            }
        }
        float left = Utilities.isRtl(getResources())
                ? mLargeViewport.right - x - mLargeSlotWidth
                : mLargeViewport.left + x;
        float top = mLargeViewport.top + y;
        out.set(Math.round(left), Math.round(top), Math.round(left + mLargeSlotWidth),
                Math.round(top + mLargeSlotHeight));
    }

    /** Bounds of the icon drawn in the given slot. */
    private void getLargeSlotIconRect(int slot, Rect out) {
        getLargeSlotRect(slot, out);
        int left = out.centerX() - mLargeSlotIconSize / 2;
        int top = out.centerY() - mLargeSlotIconSize / 2;
        out.set(left, top, left + mLargeSlotIconSize, top + mLargeSlotIconSize);
    }

    private int getLargeSlotAt(float x, float y) {
        ensureLargeFolderLayout();
        if (!mLargeViewport.contains((int) x, (int) y)) {
            return -1;
        }
        int count = getLargeSlotCount();
        for (int i = 0; i < count; i++) {
            getLargeSlotRect(i, mTmpRect);
            if (mTmpRect.contains((int) x, (int) y)) {
                return i;
            }
        }
        return -1;
    }

    private float getLargeMaxScroll() {
        if (!isLargeFolderScrollMode() || mLargeViewport.isEmpty()) {
            return 0;
        }
        int spanX = Math.max(1, mInfo.spanX);
        int spanY = Math.max(1, mInfo.spanY);
        int count = mLargeItems.size();
        if (isLargeScrollHorizontal()) {
            int perPage = spanX * spanY;
            int pages = (count + perPage - 1) / perPage;
            return Math.max(0, (pages - 1) * spanX * mLargeSlotWidth);
        }
        int rows = (count + spanX - 1) / spanX;
        return Math.max(0, (rows - spanY) * mLargeSlotHeight);
    }

    /** Scroll snaps to whole pages (horizontal) or rows (vertical). */
    private float getLargeScrollStep() {
        return isLargeScrollHorizontal()
                ? Math.max(1, mInfo.spanX) * mLargeSlotWidth
                : mLargeSlotHeight;
    }

    private void clampLargeScroll() {
        mLargeScrollOffset = Utilities.boundToRange(mLargeScrollOffset, 0f, getLargeMaxScroll());
    }

    private void cancelLargeScrollAnimation() {
        if (mLargeScrollAnimator != null) {
            mLargeScrollAnimator.cancel();
            mLargeScrollAnimator = null;
        }
    }

    private void settleLargeScroll(float velocity) {
        float step = getLargeScrollStep();
        float projected = mLargeScrollOffset - velocity * LARGE_SCROLL_FLING_FACTOR;
        float target = step > 0 ? Math.round(projected / step) * step : projected;
        target = Utilities.boundToRange(target, 0f, getLargeMaxScroll());
        cancelLargeScrollAnimation();
        if (Math.abs(target - mLargeScrollOffset) < 0.5f) {
            mLargeScrollOffset = target;
            invalidate();
            return;
        }
        mLargeScrollAnimator = ValueAnimator.ofFloat(mLargeScrollOffset, target);
        mLargeScrollAnimator.setDuration(LARGE_SCROLL_SETTLE_DURATION);
        mLargeScrollAnimator.setInterpolator(Interpolators.DECELERATE_2);
        mLargeScrollAnimator.addUpdateListener(anim -> {
            mLargeScrollOffset = (float) anim.getAnimatedValue();
            invalidate();
        });
        mLargeScrollAnimator.start();
    }

    private void setLargeDisallowIntercept(boolean disallow) {
        if (mLargeDisallowingIntercept != disallow) {
            mLargeDisallowingIntercept = disallow;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(disallow);
            }
        }
    }

    /**
     * Handles slot presses and in-tile scrolling. Returns true while the gesture is a scroll, in
     * which case the event must not be used for clicks / long presses.
     */
    private boolean handleLargeFolderTouch(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        boolean horizontal = isLargeScrollHorizontal();
        // In RTL, horizontal content is mirrored, so dragging right scrolls forward.
        float direction = horizontal && Utilities.isRtl(getResources()) ? -1f : 1f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mLargeLaunchSlot = -1;
                mLargeHiddenSlot = -1;
                mLargeScrolling = false;
                cancelLargeScrollAnimation();
                mLargeTouchedSlot = getLargeSlotAt(x, y);
                setLargePressedSlot(mLargeTouchedSlot);
                if (isLargeFolderScrollMode() && getLargeMaxScroll() > 0) {
                    mLargeDownX = x;
                    mLargeDownY = y;
                    mLargeScrollStartOffset = mLargeScrollOffset;
                    if (mLargeVelocityTracker == null) {
                        mLargeVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mLargeVelocityTracker.clear();
                    }
                    mLargeVelocityTracker.addMovement(event);
                    // Keep the workspace / swipe gestures from stealing the scroll; released
                    // again below if the gesture turns out not to be a scroll.
                    setLargeDisallowIntercept(true);
                }
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!mLargeDisallowingIntercept && !mLargeScrolling) {
                    return false;
                }
                if (mLargeVelocityTracker != null) {
                    mLargeVelocityTracker.addMovement(event);
                }
                float along = (horizontal ? x - mLargeDownX : y - mLargeDownY) * direction;
                float cross = horizontal ? y - mLargeDownY : x - mLargeDownX;
                if (!mLargeScrolling) {
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.abs(along) > slop && Math.abs(along) >= Math.abs(cross)) {
                        boolean canScroll = along < 0
                                ? mLargeScrollOffset < getLargeMaxScroll()
                                : mLargeScrollOffset > 0;
                        if (!canScroll) {
                            setLargeDisallowIntercept(false);
                            return false;
                        }
                        mLargeScrolling = true;
                        mLargeTouchedSlot = -1;
                        setLargePressedSlot(-1);
                        mLargeDownX = x;
                        mLargeDownY = y;
                        mLargeScrollStartOffset = mLargeScrollOffset;
                        return true;
                    } else if (Math.abs(cross) > slop) {
                        setLargeDisallowIntercept(false);
                        return false;
                    }
                    return false;
                }
                mLargeScrollOffset = Utilities.boundToRange(
                        mLargeScrollStartOffset - along, 0f, getLargeMaxScroll());
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean wasScrolling = mLargeScrolling;
                if (wasScrolling) {
                    float velocity = 0;
                    if (mLargeVelocityTracker != null) {
                        mLargeVelocityTracker.addMovement(event);
                        mLargeVelocityTracker.computeCurrentVelocity(1000);
                        velocity = (horizontal ? mLargeVelocityTracker.getXVelocity()
                                : mLargeVelocityTracker.getYVelocity()) * direction;
                    }
                    settleLargeScroll(velocity);
                }
                mLargeScrolling = false;
                setLargeDisallowIntercept(false);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    mLargeTouchedSlot = -1;
                }
                setLargePressedSlot(-1);
                return wasScrolling;
            }
        }
        return mLargeScrolling;
    }

    private void setLargePressedSlot(int slot) {
        if (mLargePressedSlot != slot) {
            mLargePressedSlot = slot;
            invalidate();
        }
    }

    private void drawLargeFolder(Canvas canvas) {
        ensureLargeFolderLayout();
        if (mLargeViewport.isEmpty()) return;
        int saveCount = canvas.save();
        if (mLargeAccepting) {
            canvas.scale(1.04f, 1.04f, mLargeTileRect.exactCenterX(),
                    mLargeTileRect.exactCenterY());
        }
        float radius = getLargeFolderCornerRadius();
        canvas.drawRoundRect(mLargeTileRect.left, mLargeTileRect.top, mLargeTileRect.right,
                mLargeTileRect.bottom, radius, radius, mLargeTilePaint);

        boolean scroll = isLargeFolderScrollMode();
        if (scroll) {
            canvas.save();
            canvas.clipRect(mLargeViewport);
        }
        // Directly launchable items
        int count = Math.min(mLargeItems.size(), getLargeSlotCount());
        for (int i = 0; i < count; i++) {
            if (i == mLargeHiddenSlot) continue;
            getLargeSlotIconRect(i, mTmpRect);
            if (scroll && !Rect.intersects(mTmpRect, mLargeViewport)) continue;
            int itemSave = canvas.save();
            if (i == mLargePressedSlot) {
                canvas.scale(LARGE_PRESSED_SCALE, LARGE_PRESSED_SCALE,
                        mTmpRect.exactCenterX(), mTmpRect.exactCenterY());
            }
            drawLargeFolderItem(canvas, mLargeItems.get(i).drawable, mTmpRect);
            canvas.restoreToCount(itemSave);
            if (hasLargeFolderDot(mLargeItems.get(i).item)) {
                drawLargeFolderDot(canvas, mTmpRect);
            }
        }
        if (scroll) {
            canvas.restore();
            drawLargeScrollIndicator(canvas);
        }

        // Overflow slot: a mini grid of the following items, tapping it opens the folder.
        if (mLargeHasOverflow && !scroll && mLargeItems.size() < getLargeSlotCount()) {
            int slot = mLargeItems.size();
            Rect slotRect = mTmpRect2;
            getLargeSlotIconRect(slot, slotRect);
            int overflowSave = canvas.save();
            if (slot == mLargePressedSlot) {
                canvas.scale(LARGE_PRESSED_SCALE, LARGE_PRESSED_SCALE,
                        slotRect.exactCenterX(), slotRect.exactCenterY());
            }
            float cell = slotRect.width() / 2f;
            int mini = Math.round(cell * LARGE_OVERFLOW_ICON_FACTOR);
            boolean rtl = Utilities.isRtl(getResources());
            for (int k = 0; k < mLargeOverflowItems.size(); k++) {
                int col = rtl ? 1 - (k % 2) : k % 2;
                int row = k / 2;
                int cx = Math.round(slotRect.left + cell * (col + 0.5f));
                int cy = Math.round(slotRect.top + cell * (row + 0.5f));
                mTmpRect.set(cx - mini / 2, cy - mini / 2,
                        cx - mini / 2 + mini, cy - mini / 2 + mini);
                drawLargeFolderItem(canvas, mLargeOverflowItems.get(k).drawable, mTmpRect);
            }
            canvas.restoreToCount(overflowSave);
            if (hasLargeOverflowDot()) {
                drawLargeFolderDot(canvas, slotRect);
            }
        }
        canvas.restoreToCount(saveCount);
    }

    /** A thin scroll bar in the tile's padding, showing where the scroll is. */
    private void drawLargeScrollIndicator(Canvas canvas) {
        float max = getLargeMaxScroll();
        if (max <= 0 || mInfo.hasOption(FolderInfo.FLAG_LARGE_FOLDER_HIDE_SCROLL_BAR)) return;
        int pad = mLargeViewport.left - mLargeTileRect.left;
        float thickness = Math.min(3 * getResources().getDisplayMetrics().density, pad * 0.5f);
        if (thickness <= 0) return;
        float progress = mLargeScrollOffset / max;
        if (isLargeScrollHorizontal()) {
            float track = mLargeViewport.width();
            float length = Math.max(thickness * 4, track * track / (track + max));
            float start = mLargeViewport.left + (track - length)
                    * (Utilities.isRtl(getResources()) ? 1 - progress : progress);
            float cy = mLargeViewport.bottom + pad / 2f;
            canvas.drawRoundRect(start, cy - thickness / 2, start + length, cy + thickness / 2,
                    thickness / 2, thickness / 2, mLargeIndicatorPaint);
        } else {
            float track = mLargeViewport.height();
            float length = Math.max(thickness * 4, track * track / (track + max));
            float start = mLargeViewport.top + (track - length) * progress;
            float cx = Utilities.isRtl(getResources())
                    ? mLargeViewport.left - pad / 2f
                    : mLargeViewport.right + pad / 2f;
            canvas.drawRoundRect(cx - thickness / 2, start, cx + thickness / 2, start + length,
                    thickness / 2, thickness / 2, mLargeIndicatorPaint);
        }
    }

    private static void drawLargeFolderItem(Canvas canvas, @Nullable Drawable d, Rect bounds) {
        if (d == null) return;
        Rect oldBounds = d.copyBounds();
        d.setBounds(bounds);
        d.draw(canvas);
        d.setBounds(oldBounds);
    }

    private boolean hasLargeFolderDot(@Nullable ItemInfo item) {
        return item != null && !mForceHideDot && mActivity.getDotInfoForItem(item) != null;
    }

    private boolean hasLargeOverflowDot() {
        if (mForceHideDot) return false;
        for (ItemInfo item : mInfo.getContents()) {
            boolean isDirect = false;
            for (PreviewItemDrawingParams p : mLargeItems) {
                if (p.item == item) {
                    isDirect = true;
                    break;
                }
            }
            if (!isDirect && mActivity.getDotInfoForItem(item) != null) {
                return true;
            }
        }
        return false;
    }

    private void drawLargeFolderDot(Canvas canvas, Rect iconRect) {
        if (mDotRenderer == null) return;
        mDotParams.iconBounds.set(iconRect);
        mDotParams.scale = 1f;
        mDotParams.dotColor = mBackground.getDotColor();
        mDotRenderer.draw(canvas, mDotParams);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelLargeScrollAnimation();
        if (mLargeVelocityTracker != null) {
            mLargeVelocityTracker.recycle();
            mLargeVelocityTracker = null;
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
