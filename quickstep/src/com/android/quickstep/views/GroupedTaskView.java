package com.android.quickstep.views;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.launcher3.Flags.enableOverviewIconMenu;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.quickstep.util.SplitScreenUtils.convertLauncherSplitBoundsToShell;

import android.app.ActivityTaskManager;
import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitBounds;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.recents.utilities.PreviewPositionHelper;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;

import kotlin.Unit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * TaskView that contains and shows thumbnails for not one, BUT TWO(!!) tasks
 *
 * That's right. If you call within the next 5 minutes we'll go ahead and double your order and
 * send you !! TWO !! Tasks along with their TaskThumbnailViews complimentary. On. The. House.
 * And not only that, we'll even clean up your thumbnail request if you don't like it.
 * All the benefits of one TaskView, except DOUBLED!
 *
 * (Icon loading sold separately, fees may apply. Shipping & Handling for Overlays not included).
 */
public class GroupedTaskView extends TaskView {

    private static final String TAG = GroupedTaskView.class.getSimpleName();
    @Nullable
    private Task mSecondaryTask;
    // TODO(b/336612373): Support new TTV for GroupedTaskView
    private TaskThumbnailViewDeprecated mSnapshotView2;
    private TaskViewIcon mIconView2;
    @Nullable
    private CancellableTask<ThumbnailData> mThumbnailLoadRequest2;
    @Nullable
    private CancellableTask mIconLoadRequest2;
    private final float[] mIcon2CenterCoords = new float[2];
    private TransformingTouchDelegate mIcon2TouchDelegate;
    @Nullable
    private SplitBounds mSplitBoundsConfig;
    private final DigitalWellBeingToast mDigitalWellBeingToast2;

    public GroupedTaskView(Context context) {
        this(context, null);
    }

    public GroupedTaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GroupedTaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDigitalWellBeingToast2 = new DigitalWellBeingToast(mContainer, this);
    }

    @Override
    protected Unit updateBorderBounds(@NonNull Rect bounds) {
        if (mSplitBoundsConfig == null) {
            super.updateBorderBounds(bounds);
            return Unit.INSTANCE;
        }
        bounds.set(
                Math.min(mTaskThumbnailViewDeprecated.getLeft() + Math.round(
                                mTaskThumbnailViewDeprecated.getTranslationX()),
                        mSnapshotView2.getLeft() + Math.round(mSnapshotView2.getTranslationX())),
                Math.min(mTaskThumbnailViewDeprecated.getTop() + Math.round(
                                mTaskThumbnailViewDeprecated.getTranslationY()),
                        mSnapshotView2.getTop() + Math.round(mSnapshotView2.getTranslationY())),
                Math.max(mTaskThumbnailViewDeprecated.getRight() + Math.round(
                                mTaskThumbnailViewDeprecated.getTranslationX()),
                        mSnapshotView2.getRight() + Math.round(mSnapshotView2.getTranslationX())),
                Math.max(mTaskThumbnailViewDeprecated.getBottom() + Math.round(
                                mTaskThumbnailViewDeprecated.getTranslationY()),
                        mSnapshotView2.getBottom() + Math.round(mSnapshotView2.getTranslationY())));
        return Unit.INSTANCE;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView2 = findViewById(R.id.bottomright_snapshot);
        ViewStub iconViewStub2 = findViewById(R.id.bottomRight_icon);
        if (enableOverviewIconMenu()) {
            iconViewStub2.setLayoutResource(R.layout.icon_app_chip_view);
        } else {
            iconViewStub2.setLayoutResource(R.layout.icon_view);
        }
        mIconView2 = (TaskViewIcon) iconViewStub2.inflate();
        mIcon2TouchDelegate = new TransformingTouchDelegate(mIconView2.asView());
    }

    public void bind(Task primary, Task secondary, RecentsOrientedState orientedState,
            @Nullable SplitBounds splitBoundsConfig) {
        super.bind(primary, orientedState);
        mSecondaryTask = secondary;
        mTaskIdContainer = new int[]{mTaskIdContainer[0], secondary.key.id};
        mTaskIdAttributeContainer = new TaskIdAttributeContainer[]{
                mTaskIdAttributeContainer[0],
                new TaskIdAttributeContainer(secondary, mSnapshotView2,
                        mIconView2, STAGE_POSITION_BOTTOM_OR_RIGHT)};
        mTaskIdAttributeContainer[0].setStagePosition(
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT);
        mSnapshotView2.bind(secondary);
        mSplitBoundsConfig = splitBoundsConfig;
        if (mSplitBoundsConfig == null) {
            return;
        }
        mTaskThumbnailViewDeprecated.getPreviewPositionHelper().setSplitBounds(
                convertLauncherSplitBoundsToShell(splitBoundsConfig),
                PreviewPositionHelper.STAGE_POSITION_TOP_OR_LEFT);
        mSnapshotView2.getPreviewPositionHelper().setSplitBounds(
                convertLauncherSplitBoundsToShell(splitBoundsConfig),
                PreviewPositionHelper.STAGE_POSITION_BOTTOM_OR_RIGHT);
    }

    /**
     * Sets up an on-click listener and the visibility for show_windows icon on top of each task.
     */
    @Override
    public void setUpShowAllInstancesListener() {
        // sets up the listener for the left/top task
        super.setUpShowAllInstancesListener();
        if (mTaskIdAttributeContainer.length < 2) {
            return;
        }

        // right/bottom task's base package name
        String taskPackageName = mTaskIdAttributeContainer[1].getTask().key.getPackageName();

        // icon of the right/bottom task
        View showWindowsView = findViewById(R.id.show_windows_right);
        updateFilterCallback(showWindowsView, getFilterUpdateCallback(taskPackageName));
    }

    @Override
    public void onTaskListVisibilityChanged(boolean visible, int changes) {
        super.onTaskListVisibilityChanged(visible, changes);
        if (visible) {
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();
            TaskIconCache iconCache = model.getIconCache();

            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mThumbnailLoadRequest2 = thumbnailCache.updateThumbnailInBackground(mSecondaryTask,
                        thumbnailData -> mSnapshotView2.setThumbnail(
                                mSecondaryTask, thumbnailData
                        ));
            }

            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                mIconLoadRequest2 = iconCache.updateIconInBackground(mSecondaryTask,
                        (task) -> {
                            setIcon(mIconView2, task.icon);
                            if (enableOverviewIconMenu()) {
                                setText(mIconView2, task.title);
                            }
                            mDigitalWellBeingToast2.initialize(mSecondaryTask);
                            mDigitalWellBeingToast2.setSplitConfiguration(mSplitBoundsConfig);
                            mDigitalWellBeingToast.setSplitConfiguration(mSplitBoundsConfig);
                        });
            }
        } else {
            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mSnapshotView2.setThumbnail(null, null);
                // Reset the task thumbnail reference as well (it will be fetched from the cache or
                // reloaded next time we need it)
                mSecondaryTask.thumbnail = null;
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                setIcon(mIconView2, null);
                if (enableOverviewIconMenu()) {
                    setText(mIconView2, null);
                }
            }
        }
    }

    public void updateSplitBoundsConfig(SplitBounds splitBounds) {
        mSplitBoundsConfig = splitBounds;
        invalidate();
    }

    @Nullable
    public SplitBounds getSplitBoundsConfig() {
        return mSplitBoundsConfig;
    }

    /**
     * Returns the {@link PersistentSnapPosition} of this pair of tasks.
     */
    public @PersistentSnapPosition int getSnapPosition() {
        if (mSplitBoundsConfig == null) {
            throw new IllegalStateException("mSplitBoundsConfig is null");
        }

        return mSplitBoundsConfig.snapPosition;
    }

    @Override
    public boolean offerTouchToChildren(MotionEvent event) {
        computeAndSetIconTouchDelegate(mIconView2, mIcon2CenterCoords, mIcon2TouchDelegate);
        if (mIcon2TouchDelegate.onTouchEvent(event)) {
            return true;
        }

        return super.offerTouchToChildren(event);
    }

    @Override
    protected void cancelPendingLoadTasks() {
        super.cancelPendingLoadTasks();
        if (mThumbnailLoadRequest2 != null) {
            mThumbnailLoadRequest2.cancel();
            mThumbnailLoadRequest2 = null;
        }
        if (mIconLoadRequest2 != null) {
            mIconLoadRequest2.cancel();
            mIconLoadRequest2 = null;
        }
    }

    @Nullable
    @Override
    public RunnableList launchTaskAnimated() {
        if (mTask == null || mSecondaryTask == null) {
            return null;
        }

        RunnableList endCallback = new RunnableList();
        RecentsView recentsView = getRecentsView();
        // Callbacks run from remote animation when recents animation not currently running
        InteractionJankMonitorWrapper.begin(this, Cuj.CUJ_SPLIT_SCREEN_ENTER,
                "Enter form GroupedTaskView");
        launchTaskInternal(success -> {
            endCallback.executeAllAndDestroy();
            InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
        }, false /* freezeTaskList */, true /*launchingExistingTaskview*/);

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback);
        return endCallback;
    }

    @Override
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean isQuickswitch) {
        launchTaskInternal(callback, isQuickswitch, false /*launchingExistingTaskview*/);
    }

    /**
     * @param launchingExistingTaskView {@link SplitSelectStateController#launchExistingSplitPair}
     * uses existence of GroupedTaskView as control flow of how to animate in the incoming task. If
     * we're launching from overview (from overview thumbnails) then pass in {@code true},
     * otherwise pass in {@code false} for case like quickswitching from home to task
     */
    private void launchTaskInternal(@NonNull Consumer<Boolean> callback, boolean isQuickswitch,
            boolean launchingExistingTaskView) {
        getRecentsView().getSplitSelectController().launchExistingSplitPair(
                launchingExistingTaskView ? this : null, mTask.key.id,
                mSecondaryTask.key.id, SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                callback, isQuickswitch, getSnapPosition());
        Log.d(TAG, "launchTaskInternal - launchExistingSplitPair: " + Arrays.toString(
                getTaskIds()));
    }

    @Override
    void refreshThumbnails(@Nullable HashMap<Integer, ThumbnailData> thumbnailDatas) {
        super.refreshThumbnails(thumbnailDatas);
        if (mSecondaryTask != null && thumbnailDatas != null) {
            final ThumbnailData thumbnailData = thumbnailDatas.get(mSecondaryTask.key.id);
            if (thumbnailData != null) {
                mSnapshotView2.setThumbnail(mSecondaryTask, thumbnailData);
                return;
            }
        }

        mSnapshotView2.refresh();
    }

    @Override
    public TaskThumbnailViewDeprecated[] getThumbnails() {
        return new TaskThumbnailViewDeprecated[]{mTaskThumbnailViewDeprecated, mSnapshotView2};
    }

    /**
     * Returns taskId that split selection was initiated with,
     * {@link ActivityTaskManager#INVALID_TASK_ID} if no tasks in this TaskView are part of
     * split selection
     */
    protected int getThisTaskCurrentlyInSplitSelection() {
        int initialTaskId = getRecentsView().getSplitSelectController().getInitialTaskId();
        return containsTaskId(initialTaskId) ? initialTaskId : INVALID_TASK_ID;
    }

    @Override
    protected int getLastSelectedChildTaskIndex() {
        SplitSelectStateController splitSelectController =
                getRecentsView().getSplitSelectController();
        if (splitSelectController.isDismissingFromSplitPair()) {
            // return the container index of the task that wasn't initially selected to split with
            // because that is the only remaining app that can be selected. The coordinate checks
            // below aren't reliable since both of those views may be gone/transformed
            int initSplitTaskId = getThisTaskCurrentlyInSplitSelection();
            if (initSplitTaskId != INVALID_TASK_ID) {
                return initSplitTaskId == mTask.key.id ? 1 : 0;
            }
        }

        // Check which of the two apps was selected
        if (isCoordInView(mIconView2.asView(), mLastTouchDownPosition)
                || isCoordInView(mSnapshotView2, mLastTouchDownPosition)) {
            return 1;
        }
        return super.getLastSelectedChildTaskIndex();
    }

    private boolean isCoordInView(View v, PointF position) {
        float[] localPos = new float[]{position.x, position.y};
        Utilities.mapCoordInSelfToDescendant(v, this, localPos);
        return Utilities.pointInView(v, localPos[0], localPos[1], 0f /* slop */);
    }

    @Override
    public void onRecycle() {
        super.onRecycle();
        mSnapshotView2.setThumbnail(mSecondaryTask, null);
        mSplitBoundsConfig = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(widthSize, heightSize);
        if (mSplitBoundsConfig == null || mTaskThumbnailViewDeprecated == null
                || mSnapshotView2 == null) {
            return;
        }
        int initSplitTaskId = getThisTaskCurrentlyInSplitSelection();
        if (initSplitTaskId == INVALID_TASK_ID) {
            getPagedOrientationHandler().measureGroupedTaskViewThumbnailBounds(
                    mTaskThumbnailViewDeprecated,
                    mSnapshotView2, widthSize, heightSize, mSplitBoundsConfig,
                    mContainer.getDeviceProfile(), getLayoutDirection() == LAYOUT_DIRECTION_RTL);
            // Should we be having a separate translation step apart from the measuring above?
            // The following only applies to large screen for now, but for future reference
            // we'd want to abstract this out in PagedViewHandlers to get the primary/secondary
            // translation directions
            mTaskThumbnailViewDeprecated.applySplitSelectTranslateX(
                    mTaskThumbnailViewDeprecated.getTranslationX());
            mTaskThumbnailViewDeprecated.applySplitSelectTranslateY(
                    mTaskThumbnailViewDeprecated.getTranslationY());
            mSnapshotView2.applySplitSelectTranslateX(mSnapshotView2.getTranslationX());
            mSnapshotView2.applySplitSelectTranslateY(mSnapshotView2.getTranslationY());
        } else {
            // Currently being split with this taskView, let the non-split selected thumbnail
            // take up full thumbnail area
            Optional<TaskIdAttributeContainer> nonSplitContainer = Arrays.stream(
                    mTaskIdAttributeContainer).filter(
                            container -> container.getTask().key.id != initSplitTaskId).findAny();
            nonSplitContainer.ifPresent(
                    taskIdAttributeContainer -> taskIdAttributeContainer.getThumbnailView().measure(
                            widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                                    heightSize - mContainer.getDeviceProfile()
                                            .overviewTaskThumbnailTopMarginPx,
                                    MeasureSpec.EXACTLY)));
        }
        if (!enableOverviewIconMenu()) {
            updateIconPlacement();
        }
    }

    @Override
    public void setOverlayEnabled(boolean overlayEnabled) {
        if (FeatureFlags.enableAppPairs()) {
            super.setOverlayEnabled(overlayEnabled);
        } else {
            // Intentional no-op to prevent setting smart actions overlay on thumbnails
        }
    }

    @Override
    public void setOrientationState(RecentsOrientedState orientationState) {
        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        if (enableOverviewIconMenu() && mSplitBoundsConfig != null) {
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            Pair<Point, Point> groupedTaskViewSizes =
                    orientationState.getOrientationHandler().getGroupedTaskViewSizes(
                            deviceProfile,
                            mSplitBoundsConfig,
                            layoutParams.width,
                            layoutParams.height
                    );
            int iconViewMarginStart = getResources().getDimensionPixelSize(
                    R.dimen.task_thumbnail_icon_menu_expanded_top_start_margin);
            int iconViewBackgroundMarginStart = getResources().getDimensionPixelSize(
                    R.dimen.task_thumbnail_icon_menu_background_margin_top_start);
            int iconMargins = (iconViewMarginStart + iconViewBackgroundMarginStart) * 2;
            ((IconAppChipView) mIconView).setMaxWidth(groupedTaskViewSizes.first.x - iconMargins);
            ((IconAppChipView) mIconView2).setMaxWidth(groupedTaskViewSizes.second.x - iconMargins);
        }
        // setMaxWidth() needs to be called before mIconView.setIconOrientation which is called in
        // the super below.
        super.setOrientationState(orientationState);

        boolean isGridTask = deviceProfile.isTablet && !isFocusedTask();
        mIconView2.setIconOrientation(orientationState, isGridTask);
        updateIconPlacement();
        updateSecondaryDwbPlacement();
    }

    private void updateIconPlacement() {
        if (mSplitBoundsConfig == null) {
            return;
        }

        DeviceProfile deviceProfile = mContainer.getDeviceProfile();
        int taskIconHeight = deviceProfile.overviewTaskIconSizePx;
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        if (enableOverviewIconMenu()) {
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            Pair<Point, Point> groupedTaskViewSizes =
                    getPagedOrientationHandler()
                            .getGroupedTaskViewSizes(
                                    deviceProfile,
                                    mSplitBoundsConfig,
                                    layoutParams.width,
                                    layoutParams.height
                            );

            getPagedOrientationHandler().setSplitIconParams(mIconView.asView(), mIconView2.asView(),
                    taskIconHeight, groupedTaskViewSizes.first.x, groupedTaskViewSizes.first.y,
                    getLayoutParams().height, getLayoutParams().width, isRtl, deviceProfile,
                    mSplitBoundsConfig);
        } else {
            getPagedOrientationHandler().setSplitIconParams(mIconView.asView(), mIconView2.asView(),
                    taskIconHeight, mTaskThumbnailViewDeprecated.getMeasuredWidth(),
                    mTaskThumbnailViewDeprecated.getMeasuredHeight(), getMeasuredHeight(),
                    getMeasuredWidth(),
                    isRtl, deviceProfile, mSplitBoundsConfig);
        }
    }

    private void updateSecondaryDwbPlacement() {
        if (mSecondaryTask == null) {
            return;
        }
        mDigitalWellBeingToast2.initialize(mSecondaryTask);
    }

    @Override
    protected void updateSnapshotRadius() {
        super.updateSnapshotRadius();
        mSnapshotView2.setFullscreenParams(mCurrentFullscreenParams);
    }

    @Override
    protected void setIconsAndBannersTransitionProgress(float progress, boolean invert) {
        super.setIconsAndBannersTransitionProgress(progress, invert);
        // Value set by super call
        float scale = mIconView.getAlpha();
        mIconView2.setContentAlpha(scale);
        mDigitalWellBeingToast2.updateBannerOffset(1f - scale);
    }

    @Override
    public void setColorTint(float amount, int tintColor) {
        super.setColorTint(amount, tintColor);
        mIconView2.setIconColorTint(tintColor, amount);
        mSnapshotView2.setDimAlpha(amount);
        mDigitalWellBeingToast2.setBannerColorTint(tintColor, amount);
    }

    @Override
    protected void applyThumbnailSplashAlpha() {
        super.applyThumbnailSplashAlpha();
        mSnapshotView2.setSplashAlpha(mTaskThumbnailSplashAlpha);
    }

    @Override
    protected void refreshTaskThumbnailSplash() {
        super.refreshTaskThumbnailSplash();
        mSnapshotView2.refreshSplashView();
    }

    @Override
    protected void resetViewTransforms() {
        super.resetViewTransforms();
        mSnapshotView2.resetViewTransforms();
    }

    /**
     * Sets visibility for thumbnails and associated elements (DWB banners).
     * IconView is unaffected.
     *
     * When setting INVISIBLE, sets the visibility for the last selected child task.
     * When setting VISIBLE (as a reset), sets the visibility for both tasks.
     */
    @Override
    void setThumbnailVisibility(int visibility, int taskId) {
        if (visibility == VISIBLE) {
            mTaskThumbnailViewDeprecated.setVisibility(visibility);
            mDigitalWellBeingToast.setBannerVisibility(visibility);
            mSnapshotView2.setVisibility(visibility);
            mDigitalWellBeingToast2.setBannerVisibility(visibility);
        } else if (mTaskIdContainer.length > 0 && mTaskIdContainer[0] == taskId) {
            mTaskThumbnailViewDeprecated.setVisibility(visibility);
            mDigitalWellBeingToast.setBannerVisibility(visibility);
        } else {
            mSnapshotView2.setVisibility(visibility);
            mDigitalWellBeingToast2.setBannerVisibility(visibility);
        }
    }
}
