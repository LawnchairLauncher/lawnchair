package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;

import com.android.launcher3.R;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.recents.model.Task;

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

    private Task mSecondaryTask;
    private TaskThumbnailView mSnapshotView2;
    private CancellableTask mThumbnailLoadRequest2;

    public GroupedTaskView(Context context) {
        super(context);
    }

    public GroupedTaskView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GroupedTaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSnapshotView2 = findViewById(R.id.bottomright_snapshot);
    }

    public void bind(Task primary, Task secondary, RecentsOrientedState orientedState,
            SplitConfigurationOptions.StagedSplitBounds splitBoundsConfig) {
        super.bind(primary, orientedState);
        mSecondaryTask = secondary;
        mTaskIdContainer[1] = secondary.key.id;
        mTaskIdAttributeContainer[1] = new TaskIdAttributeContainer(secondary, mSnapshotView2);
        mSnapshotView2.bind(secondary);
        adjustThumbnailBoundsForSplit(splitBoundsConfig, orientedState);
    }

    @Override
    public void onTaskListVisibilityChanged(boolean visible, int changes) {
        super.onTaskListVisibilityChanged(visible, changes);
        if (visible) {
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();

            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mThumbnailLoadRequest2 = thumbnailCache.updateThumbnailInBackground(mSecondaryTask,
                        thumbnailData -> mSnapshotView2.setThumbnail(
                                mSecondaryTask, thumbnailData
                        ));
            }

            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                // TODO What's the Icon for this going to look like? :o
            }
        } else {
            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                mSnapshotView2.setThumbnail(null, null);
                // Reset the task thumbnail reference as well (it will be fetched from the cache or
                // reloaded next time we need it)
                mSecondaryTask.thumbnail = null;
            }
            if (needsUpdate(changes, FLAG_UPDATE_ICON)) {
                // TODO
            }
        }
    }

    @Override
    protected void cancelPendingLoadTasks() {
        super.cancelPendingLoadTasks();
        if (mThumbnailLoadRequest2 != null) {
            mThumbnailLoadRequest2.cancel();
            mThumbnailLoadRequest2 = null;
        }
    }

    @Override
    public RunnableList launchTaskAnimated() {
        getRecentsView().getSplitPlaceholder().launchTasks(mTask, mSecondaryTask,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT, null /*callback*/);
        return null;
    }

    @Override
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean freezeTaskList) {
        getRecentsView().getSplitPlaceholder().launchTasks(mTask, mSecondaryTask,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT, callback);
    }

    @Override
    public TaskThumbnailView[] getThumbnails() {
        return new TaskThumbnailView[]{mSnapshotView, mSnapshotView2};
    }

    @Override
    public void onRecycle() {
        super.onRecycle();
        mSnapshotView2.setThumbnail(mSecondaryTask, null);
    }

    @Override
    public void setOverlayEnabled(boolean overlayEnabled) {
        super.setOverlayEnabled(overlayEnabled);
        mSnapshotView2.setOverlayEnabled(overlayEnabled);
    }

    private void adjustThumbnailBoundsForSplit(
            SplitConfigurationOptions.StagedSplitBounds splitBoundsConfig,
            RecentsOrientedState orientedState) {
        if (splitBoundsConfig == null) {
            return;
        }

        orientedState.getOrientationHandler().setGroupedTaskViewThumbnailBounds(
                mSnapshotView, mSnapshotView2, this, splitBoundsConfig,
                mActivity.getDeviceProfile());
    }
}
