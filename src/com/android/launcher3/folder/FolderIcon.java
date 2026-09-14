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
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Alarm;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.InsettableFrameLayout;
import app.lawnchair.folder.Centered;
import app.lawnchair.preferences2.PreferenceCacheExtensionsKt;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.ui.liquid.LiquidGlassPanel;
import app.lawnchair.ui.liquid.ShapeCorner;
import app.lawnchair.ui.liquid.LiquidGlassWallpaper;
import app.lawnchair.util.LawnchairUtilsKt;
import app.lawnchair.views.LawnchairScrimView;
import com.android.launcher3.PagedView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.views.SpringRelativeLayout;
import android.graphics.Bitmap;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.views.BaseDragLayer;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.allapps.BaseAllAppsAdapter;
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

import java.util.ArrayList;
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
    /** The title offset a one-cell folder uses, before any plate growth. */
    private int mBaseLabelTopMargin = -1;

    PreviewBackground mBackground = new PreviewBackground(getContext());

    /**
     * Sora: this icon's background, made of the same glass as the folder it
     * opens into -- the same panel, the same effects, the same blur and wash.
     * Anything less and the two read as different materials at either end of the
     * same animation.
     *
     * It sits in the drag layer *below* the workspace rather than in this view,
     * so the preview icons keep drawing in front of it while the image it
     * refracts stays pinned to the screen. That last part is what lets a page
     * scroll slide the icon across the glass instead of dragging the glass with
     * it.
     */
    private LiquidGlassPanel mIconGlass;
    private final int[] mGlassIconLocation = new int[2];
    private final int[] mGlassOverlayLocation = new int[2];

    /**
     * Sora: the glass this icon's background is made of.
     *
     * It sits in the drag layer *below* the workspace, not in this view. Two
     * things follow from that, and both are the point. The preview icons keep
     * drawing in front of it, as they must; and the image it refracts stays
     * pinned to the screen while the icon travels over it, so scrolling a page
     * slides the icon across the glass instead of dragging the glass along --
     * which is the whole difference between glass and a picture of glass.
     */
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
        LawnchairUtilsKt.overrideAllAppsTextColor(icon.mFolderName);

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
        return (willAcceptItemType(item.itemType) && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        return !mFolder.isDestroyed() && willAcceptItem(dragInfo);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayoutLayoutParams lp = (CellLayoutLayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.getCellX(), lp.getCellY());
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

            int previewItemLimit = mPreviewLayoutRule.getPreviewItemLimit();
            int numItemsInPreview = Math.min(previewItemLimit, index + 1);
            boolean itemAdded = false;
            if (itemReturnedOnFailedDrop || index >= previewItemLimit) {
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

                float finalAlpha = index < previewItemLimit ? 1f : 0f;

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

    public boolean isDefaultMode() {
        PreferenceManager2 prefs2 = PreferenceManager2.INSTANCE.get(getContext());
        return PreferenceCacheExtensionsKt.firstCached(prefs2.getFolderOpenMode())
                instanceof Centered;
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
        int previewItemLimit = mPreviewLayoutRule.getPreviewItemLimit();
        mTmpParams = mPreviewItemManager.computePreviewItemDrawingParams(
                Math.min(previewItemLimit, index), curNumItems, mTmpParams);

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
        if (visible) {
            syncIconGlass();
        } else if (mIconGlass != null) {
            mIconGlass.setVisibility(INVISIBLE);
        }
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
        return mPreviewVerifier.setFolderInfo(mInfo)
                .setPreviewLimit(mPreviewLayoutRule.getPreviewItemLimit())
                .previewItemsForPage(page, mInfo.getContents());
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return mPreviewItemManager.verifyDrawable(who) || super.verifyDrawable(who);
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
    }

    public void onItemsChanged(boolean animate) {
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
        if (currentPage != 0) {
            mCurrentPreviewItems.clear();
            mCurrentPreviewItems.addAll(getPreviewItemsOnPage(0));
        }
        mPreviewItemManager.onFolderClose(currentPage);
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

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Posted, for the same reason the removal is. Adding a view to the drag
        // layer asks it to lay out again, and this runs while the drag layer is
        // still walking its children to attach them -- a layout in the middle of
        // that reaches parts of the tree that are not ready to be laid out yet,
        // such as a drawer RecyclerView whose layout manager has not been told
        // about its RecyclerView.
        post(this::attachIconGlass);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        releaseIconGlass();
    }

    /**
     * Gives this icon a pane of glass, if it is somewhere one can be seen.
     *
     * A folder inside the app drawer is not: its pane would sit behind the
     * drawer and never show, so that icon keeps painting its own background.
     */
    private void attachIconGlass() {
        if (mIconGlass != null || !isAttachedToWindow()
                || !(mActivity instanceof Launcher)) {
            return;
        }
        Launcher launcher = (Launcher) mActivity;
        ViewGroup dragLayer = launcher.getDragLayer();
        View workspace = launcher.getWorkspace();
        if (dragLayer == null || workspace == null) {
            return;
        }
        // A folder in the drawer gets the very same pane, only placed higher up.
        //
        // It used to get none at all -- a pane below the workspace would be
        // buried under the drawer and never seen -- so a closed folder there was
        // the plate's outline with the backdrop showing through it unchanged: a
        // window, not glass. Slotted straight above the scrim instead, exactly
        // where the search pill puts its own, it is the same code and so it is
        // the same material, which is the only way the two can be guaranteed to
        // match.
        boolean inDrawer = isInsideAllApps();

        LiquidGlassPanel panel = new LiquidGlassPanel(getContext());
        // BaseDragLayer accepts only its own LayoutParams. Handed the
        // InsettableFrameLayout ones it inherits from, it quietly converts them,
        // and the conversion drops ignoreInsets -- after which onViewAdded lays
        // the system-bar inset on as a margin, and does so again on every add.
        BaseDragLayer.LayoutParams params = new BaseDragLayer.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.ignoreInsets = true;
        panel.setLayoutParams(params);

        // Exactly what the open folder uses. The two are the same surface at two
        // sizes, so they have to be made of the same material.
        panel.setBlurRadiusDp(4f);
        // A tile floating over the bare wallpaper needs a wash of its own to keep
        // its preview icons legible. One set into the drawer does not: the drawer
        // is made of that same wallpaper, so a wash here is a wash its
        // surroundings do not have, and the tile reads as a brighter patch cut
        // into the frosting rather than as part of it. There it wears nothing,
        // and the lens at the rim and the light along the edge are what separate
        // it from the drawer -- exactly as they already do for the search pill.
        //
        // Decided here rather than in syncIconGlass, which was already asking for
        // it and getting nowhere: the pane reads this once, when it is composed,
        // and by the time the first frame callback runs there is nothing left to
        // ask. Set before the panel is added, it is right from the first frame.
        //
        // And no exact colour either. Sampling what the drawer is washed with
        // means sampling the scrim, and at this moment -- an icon attaching,
        // usually with the drawer still shut -- the scrim is wearing the
        // workspace's colour, which follows the light/dark theme. That is how a
        // tile ended up with a wash that changed when the theme did.
        panel.setTinted(!inDrawer);
        if (inDrawer) {
            panel.setTintColor(LawnchairUtilsKt.DRAWER_GLASS_WASH);
        }
        panel.setOnSyncFrame(this::syncIconGlass);

        // On the workspace, the wallpaper unblurred: the pane's own blur is what
        // softens it, and what sits behind a closed folder icon there is the
        // wallpaper and nothing else. In the drawer it is the frosted backdrop
        // the drawer is drawn on, because there the tile is meant to disappear
        // into its surroundings rather than stand out from them -- a tile
        // refracting a sharper scene than the drawer around it reads as a window
        // cut through the frosting, which is the one thing it must not be.
        DisplayMetrics display = getResources().getDisplayMetrics();
        Bitmap scene = null;
        if (inDrawer) {
            ScrimView scrim = launcher.getScrimView();
            if (scrim instanceof LawnchairScrimView lawnScrim) {
                scene = lawnScrim.getDrawerBackdrop();
            }
        }
        if (scene == null) {
            scene = LiquidGlassWallpaper.INSTANCE.get(getContext());
        }
        panel.setScene(scene, 0, 0, display.widthPixels, display.heightPixels);

        int index = inDrawer
                ? dragLayer.indexOfChild(launcher.getScrimView()) + 1
                : dragLayer.indexOfChild(workspace);
        dragLayer.addView(panel, index < 0 ? 0 : index);

        mIconGlass = panel;
        mBackground.setHasGlassPane(true);
    }

    @Nullable
    public LiquidGlassPanel getIconGlass() {
        return mIconGlass;
    }

    private void releaseIconGlass() {
        if (mIconGlass == null) {
            return;
        }
        mBackground.setHasGlassPane(false);
        final LiquidGlassPanel panel = mIconGlass;
        mIconGlass = null;
        panel.setOnSyncFrame(null);
        panel.setVisibility(GONE);

        // Posted, never done here. This runs from onDetachedFromWindow, and the
        // reason it runs is usually that the whole hierarchy is being torn down
        // -- a relaunch after a settings change, say. The drag layer is walking
        // its own children at that moment, and pulling one out from under the
        // walk leaves a hole in the array it is iterating, which it then
        // dereferences. Waiting for the walk to finish costs nothing; if the
        // activity is going away the runnable simply never arrives.
        panel.post(() -> {
            ViewParent parent = panel.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(panel);
            }
        });
    }





    /**
     * Where this icon actually stops being visible, in [overlay]'s coordinates.
     *
     * Asked of the view rather than worked out from its ancestors.
     * {@code getGlobalVisibleRect} already accounts for every clip between here
     * and the window, so it gives the same line the icon's own pixels are cut
     * at -- whichever ancestor happens to do the cutting. Reasoning about it
     * instead put the glass twelve pixels above where the icons stopped: the
     * list's bound is not the line, and neither is its padding box.
     *
     * NaN when the icon is wholly hidden, which leaves the pane unlimited --
     * there is nothing to draw for it anyway.
     */
    int getOverScrollShiftX() {
        for (ViewParent p = getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof PagedView) {
                return ((PagedView<?>) p).getOverScrollShift();
            }
        }
        return 0;
    }

    int getOverScrollShiftY() {
        for (ViewParent p = getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof SpringRelativeLayout) {
                return ((SpringRelativeLayout) p).getOverScrollShift();
            }
        }
        return 0;
    }

    private float visibleTopWithin(View overlay) {
        Rect visible = new Rect();
        if (!getGlobalVisibleRect(visible)) {
            return Float.NaN;
        }
        int[] overlayLocation = new int[2];
        overlay.getLocationOnScreen(overlayLocation);
        return visible.top - overlayLocation[1] + getOverScrollShiftY();
    }

    /** Whether this icon lives in the app drawer rather than on the workspace. */
    boolean isInsideAllApps() {
        for (ViewParent parent = getParent(); parent instanceof View; parent = parent.getParent()) {
            if (((View) parent).getId() == R.id.apps_view) {
                return true;
            }
        }
        return false;
    }

    /** The pane's resting size, which the opening folder grows away from. */
    /**
     * Keeps the title under the plate, however many rows the plate covers.
     *
     * The label is placed at a fixed offset meant for a folder one cell tall.
     * A folder given a second row grows its plate down through that offset, and
     * the title ends up sitting in the middle of the folder instead of beneath
     * it. Shifting it by exactly the height the plate gained keeps the gap
     * between plate and title identical to a normal folder's.
     */
    void alignLabelBelowPlate() {
        if (mFolderName == null) {
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mFolderName.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (mBaseLabelTopMargin < 0) {
            mBaseLabelTopMargin = lp.topMargin;
        }
        int wanted;
        if (isInAppDrawer() && BaseAllAppsAdapter.isCaddy(getContext())) {
            // Placed against the plate's own bottom edge, not shifted by however
            // much the plate grew. A BubbleTextView starts its letters below its
            // icon box, so the margin that puts them where the reference puts
            // them is that drop taken back off again -- which can come out
            // negative, and a FrameLayout is happy to oblige.
            //
            // The old line left the label hanging 52px under the plate where the
            // reference has 25: it was measuring the growth of the plate rather
            // than aiming at a point below it, so the error grew with the plate.
            int gap = Math.round(getResources().getDisplayMetrics().widthPixels
                    * PreviewBackground.CADDY_LABEL_GAP_RATIO);
            // From the label's own top down to the top of its capitals, which is
            // the edge the eye actually measures the gap to.
            //
            // Two things are easy to get wrong here. This view is never given an
            // icon -- the preview is painted by the folder itself -- so its text
            // starts at the top rather than below an icon box; counting an
            // icon's height in took the margin 155px too small, and since the
            // view's height is that margin plus the text, the view shrank below
            // the plate it was drawing and cut the bottom off it. And the
            // ascent line is not the top of a capital: it sits some way above
            // it, which left the label a further 11px low. Measuring a capital
            // settles both without guessing at the font.
            Paint.FontMetrics fm = mFolderName.getPaint().getFontMetrics();
            Rect cap = new Rect();
            mFolderName.getPaint().getTextBounds("H", 0, 1, cap);
            int baseline = mFolderName.getPaddingTop() + Math.round(-fm.top);
            int leading = baseline - (-cap.top);
            wanted = mBackground.basePreviewOffsetY + mBackground.previewHeight + gap - leading;
        } else {
            int grown = Math.max(0, mBackground.previewHeight - mBackground.previewSize);
            wanted = mBaseLabelTopMargin + grown;
        }
        if (lp.topMargin != wanted) {
            lp.topMargin = wanted;
            mFolderName.setLayoutParams(lp);
        }
    }

    public int getGlassRestSize() {
        // A width, because everything it is measured against is one: the open
        // folder's width, and the pane's own as it grows. The shorter of the two
        // sides stood in for it while every plate was square; on a folder two
        // cells wide and one tall it is the height, and the corner then starts
        // the animation part way to the folder's own -- which is the background
        // failing to keep step with the shape it is opening out of.
        return mBackground.getPlateWidth();
    }

    public float getGlassRestCornerRadius() {
        // Exactly the corner the resting plate is cut with -- the same number,
        // from the same place, as the pane draws while the folder sits closed.
        //
        // It used to be worked out separately here, from one cell's size, and
        // the two agreed only while every plate was one cell. On anything
        // larger, and on any shape whose corner is not a plain circle, the
        // animation began at a different corner from the one it was leaving,
        // and the folder jumped on the first frame of opening and again on the
        // last frame of closing.
        return mBackground.getPlateCornerRadius();
    }

    /**
     * The corner the folder shape actually asks for, at a given size.
     *
     * ShapeDelegate states it as a ratio of the half-edge, and a circle is just
     * the case where that ratio is 1. Reading it rather than assuming a number
     * is what lets the glass follow whatever shape the user has chosen instead
     * of always being the one this happened to be written against.
     */
    private float glassCornerRadius(int size) {
        // Measured off the shape rather than read from it. A squircle declares
        // the same full-quadrant corner a circle does and differs only in how
        // the curve bulges, so taking the declared number at face value drew a
        // circle -- which is what a folder set to squircle used to close into.
        return ShapeCorner.radiusFor(ThemeManager.INSTANCE.get(getContext()).getFolderShape(), size);
    }

    /**
     * Follows this icon's own background rectangle, once per frame.
     *
     * Only the rectangle moves; the image behind it does not. That is what makes
     * a still image behave like glass as the home screen scrolls past.
     */
    private void syncIconGlass() {
        if (mIconGlass == null) {
            return;
        }
        int width = mBackground.getPlateWidth();
        int height = mBackground.getPlateHeight();
        // getIconVisible() belongs in here, and was missing.
        //
        // A folder has one background at rest and another while it is open, and
        // only one of them may be on screen at a time. Opening clears this flag
        // -- see Folder.animateOpen -- and closeComplete sets it again once the
        // closing animation has ended, so the flag already describes exactly
        // when the resting background is wanted.
        //
        // The plate drawn in dispatchDraw obeyed it. Its glass did not: the pane
        // is a separate view in the drag layer, the icon itself stays VISIBLE at
        // full alpha the whole time the folder is open, and nothing else told
        // the pane to stop. So the resting background went on showing underneath
        // the folder that had grown out of it, and was still showing while the
        // folder shrank back into it. Reading the flag here means the plate and
        // its glass are the one surface again, and it is the only surface on
        // screen for the whole of the open state.
        if (width <= 0 || height <= 0 || getVisibility() != VISIBLE || getAlpha() <= 0f
                || !mBackgroundIsVisible) {
            mIconGlass.setVisibility(INVISIBLE);
            return;
        }

        mIconGlass.setVisibility(VISIBLE);
        getLocationOnScreen(mGlassIconLocation);
        mIconGlass.screenLocation(mGlassOverlayLocation);

        // Measured on one cell, never on the plate. Four cells given to a folder
        // are four folders merged: the outer corners stay exactly as round as a
        // single folder's and the edges between them run straight. Cutting the
        // corner from the plate's own shorter side is what turned a 2x2 into one
        // enormous circle. Which corner that is comes from the folder shape the
        // user has set, so this follows a squircle or a rounded square as
        // readily as it follows a circle.
        mIconGlass.setCornerRadius(mBackground.getPlateCornerRadius());
        if (isInsideAllApps()) {
            // The drawer's own wash, not the pane's stock light/dark one.
            //
            // The stock tint is a colour of the pane's own choosing and it
            // follows the theme, which is the one thing a surface in this drawer
            // may not do. Every pane in here reads the same value instead.
            mIconGlass.setTinted(false);
            mIconGlass.setTintColor(LawnchairUtilsKt.DRAWER_GLASS_WASH);
        }
        // In the drawer, stop at the top of the list this tile scrolls in.
        //
        // The pane is a sibling of that list, not a child of it, so the list's
        // own clipping does not reach it: a tile scrolled under the header went
        // on drawing its glass over the search bar. Every pane outside a list
        // passes NaN and is not limited at all.
        mIconGlass.setClipTop(isInsideAllApps()
                ? visibleTopWithin(mIconGlass)
                : Float.NaN);
        mIconGlass.setPaneBounds(
                mGlassIconLocation[0] + mBackground.getOffsetX() - mGlassOverlayLocation[0] + getOverScrollShiftX(),
                mGlassIconLocation[1] + mBackground.getOffsetY() - mGlassOverlayLocation[1] + getOverScrollShiftY(),
                width, height, getAlpha());
    }
}
