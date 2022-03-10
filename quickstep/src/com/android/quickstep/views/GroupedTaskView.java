package com.android.quickstep.views;

import static com.android.launcher3.util.SplitConfigurationOptions.DEFAULT_SPLIT_RATIO;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.content.Context;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.StagedSplitBounds;
import com.android.launcher3.util.TransformingTouchDelegate;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskIconCache;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.HashMap;
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

    @Nullable
    private Task mSecondaryTask;
    private TaskThumbnailView mSnapshotView2;
    private IconView mIconView2;
    @Nullable
    private CancellableTask<ThumbnailData> mThumbnailLoadRequest2;
    @Nullable
    private CancellableTask mIconLoadRequest2;
    private final float[] mIcon2CenterCoords = new float[2];
    private TransformingTouchDelegate mIcon2TouchDelegate;
    @Nullable private StagedSplitBounds mSplitBoundsConfig;
    private final DigitalWellBeingToast mDigitalWellBeingToast2;

    public GroupedTaskView(Context context) {
        this(context, null);
    }

    public GroupedTaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GroupedTaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDigitalWellBeingToast2 = new DigitalWellBeingToast(mActivity, this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView2 = findViewById(R.id.bottomright_snapshot);
        mIconView2 = findViewById(R.id.bottomRight_icon);
        mIcon2TouchDelegate = new TransformingTouchDelegate(mIconView2);
    }

    public void bind(Task primary, Task secondary, RecentsOrientedState orientedState,
            @Nullable StagedSplitBounds splitBoundsConfig) {
        super.bind(primary, orientedState);
        mSecondaryTask = secondary;
        mTaskIdContainer[1] = secondary.key.id;
        mTaskIdAttributeContainer[1] = new TaskIdAttributeContainer(secondary, mSnapshotView2,
                mIconView2, STAGE_POSITION_BOTTOM_OR_RIGHT);
        mTaskIdAttributeContainer[0].setStagePosition(STAGE_POSITION_TOP_OR_LEFT);
        mSnapshotView2.bind(secondary);
        mSplitBoundsConfig = splitBoundsConfig;
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
            }
        }
    }

    public void updateSplitBoundsConfig(StagedSplitBounds stagedSplitBounds) {
        mSplitBoundsConfig = stagedSplitBounds;
        invalidate();
    }

    public float getSplitRatio() {
        if (mSplitBoundsConfig != null) {
            return mSplitBoundsConfig.appsStackedVertically
                    ? mSplitBoundsConfig.topTaskPercent : mSplitBoundsConfig.leftTaskPercent;
        }
        return DEFAULT_SPLIT_RATIO;
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
        recentsView.getSplitPlaceholder().launchTasks(this /*groupedTaskView*/,
                success -> endCallback.executeAllAndDestroy(),
                false /* freezeTaskList */);

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback);
        return endCallback;
    }

    @Override
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean freezeTaskList) {
        getRecentsView().getSplitPlaceholder().launchTasks(mTask.key.id, mSecondaryTask.key.id,
                STAGE_POSITION_TOP_OR_LEFT, callback, freezeTaskList, getSplitRatio());
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
    public TaskThumbnailView[] getThumbnails() {
        return new TaskThumbnailView[]{mSnapshotView, mSnapshotView2};
    }

    @Override
    protected int getChildTaskIndexAtPosition(PointF position) {
        if (isCoordInView(mIconView2, position) || isCoordInView(mSnapshotView2, position)) {
            return 1;
        }
        return super.getChildTaskIndexAtPosition(position);
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
        if (mSplitBoundsConfig == null || mSnapshotView == null || mSnapshotView2 == null) {
            return;
        }
        getPagedOrientationHandler().measureGroupedTaskViewThumbnailBounds(mSnapshotView,
                mSnapshotView2, widthSize, heightSize, mSplitBoundsConfig,
                mActivity.getDeviceProfile());
        updateIconPlacement();
    }

    @Override
    public void setOverlayEnabled(boolean overlayEnabled) {
        super.setOverlayEnabled(overlayEnabled);
        mSnapshotView2.setOverlayEnabled(overlayEnabled);
    }

    @Override
    public void setOrientationState(RecentsOrientedState orientationState) {
        super.setOrientationState(orientationState);
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        boolean isGridTask = deviceProfile.isTablet && !isFocusedTask();
        int iconDrawableSize = isGridTask ? deviceProfile.overviewTaskIconDrawableSizeGridPx
                : deviceProfile.overviewTaskIconDrawableSizePx;
        mIconView2.setDrawableSize(iconDrawableSize, iconDrawableSize);
        mIconView2.setRotation(getPagedOrientationHandler().getDegreesRotated());
        updateIconPlacement();
        updateSecondaryDwbPlacement();
    }

    private void updateIconPlacement() {
        if (mSplitBoundsConfig == null) {
            return;
        }

        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        int taskIconHeight = deviceProfile.overviewTaskIconSizePx;
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;

        getPagedOrientationHandler().setSplitIconParams(mIconView, mIconView2,
                taskIconHeight, mSnapshotView.getMeasuredWidth(), mSnapshotView.getMeasuredHeight(),
                isRtl, deviceProfile, mSplitBoundsConfig);
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
    protected void setIconAndDimTransitionProgress(float progress, boolean invert) {
        super.setIconAndDimTransitionProgress(progress, invert);
        // Value set by super call
        float scale = mIconView.getAlpha();
        mIconView2.setAlpha(scale);
        mDigitalWellBeingToast2.updateBannerOffset(1f - scale,
                mCurrentFullscreenParams.mCurrentDrawnInsets.top
                        + mCurrentFullscreenParams.mCurrentDrawnInsets.bottom);
    }

    @Override
    public void setColorTint(float amount, int tintColor) {
        super.setColorTint(amount, tintColor);
        mIconView2.setIconColorTint(tintColor, amount);
        mSnapshotView2.setDimAlpha(amount);
        mDigitalWellBeingToast2.setBannerColorTint(tintColor, amount);
    }
}
