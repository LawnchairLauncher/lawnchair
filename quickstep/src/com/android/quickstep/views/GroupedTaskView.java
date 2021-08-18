package com.android.quickstep.views;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.util.CancellableTask;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.recents.model.Task;

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

    public void bind(Task primary, Task secondary, RecentsOrientedState orientedState) {
        super.bind(primary, orientedState);
        mSecondaryTask = secondary;
        mTaskIdContainer[1] = secondary.key.id;
        mTaskIdAttributeContainer[1] = new TaskIdAttributeContainer(secondary, mSnapshotView2);
        mSnapshotView2.bind(secondary);
        adjustThumbnailBoundsForSplit();
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
    public void onRecycle() {
        super.onRecycle();
        mSnapshotView2.setThumbnail(mSecondaryTask, null);
    }

    @Override
    public void setOverlayEnabled(boolean overlayEnabled) {
        super.setOverlayEnabled(overlayEnabled);
        mSnapshotView2.setOverlayEnabled(overlayEnabled);
    }

    private void adjustThumbnailBoundsForSplit() {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        ViewGroup.LayoutParams primaryLp = mSnapshotView.getLayoutParams();
        primaryLp.width = mSecondaryTask == null ?
                MATCH_PARENT :
                getWidth();
        int spaceAboveSnapshot = deviceProfile.overviewTaskThumbnailTopMarginPx;
        // TODO get divider height
        int dividerBar = 20;
        primaryLp.height = mSecondaryTask == null ?
                MATCH_PARENT :
                (getHeight() - spaceAboveSnapshot - dividerBar) / 2;
        mSnapshotView.setLayoutParams(primaryLp);

        if (mSecondaryTask == null) {
            mSnapshotView2.setVisibility(GONE);
            return;
        }

        mSnapshotView2.setVisibility(VISIBLE);
        ViewGroup.LayoutParams secondaryLp = mSnapshotView2.getLayoutParams();
        secondaryLp.width = getWidth();
        secondaryLp.height = primaryLp.height;
        mSnapshotView2.setLayoutParams(secondaryLp);
        mSnapshotView2.setTranslationY(primaryLp.height + spaceAboveSnapshot + dividerBar);
    }
}
