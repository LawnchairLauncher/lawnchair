/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_UNDEFINED;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.icons.IconProvider;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.RunnableList;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.TaskThumbnailCache;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.QuickStepContract;

import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * TaskView that contains all tasks that are part of the desktop.
 */
// TODO(b/249371338): TaskView needs to be refactored to have better support for N tasks.
public class DesktopTaskView extends TaskView {

    private static final String TAG = DesktopTaskView.class.getSimpleName();

    private static final boolean DEBUG = false;

    @NonNull
    private List<Task> mTasks = new ArrayList<>();

    private final ArrayList<TaskThumbnailView> mSnapshotViews = new ArrayList<>();

    /** Maps {@code taskIds} to corresponding {@link TaskThumbnailView}s */
    private final SparseArray<TaskThumbnailView> mSnapshotViewMap = new SparseArray<>();

    private final ArrayList<CancellableTask<?>> mPendingThumbnailRequests = new ArrayList<>();

    private final TaskView.FullscreenDrawParams mSnapshotDrawParams;

    private View mBackgroundView;

    private int mChildCountAtInflation;

    public DesktopTaskView(Context context) {
        this(context, null);
    }

    public DesktopTaskView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DesktopTaskView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mSnapshotDrawParams = new FullscreenDrawParams(context) {
            @Override
            public float computeTaskCornerRadius(Context context) {
                return QuickStepContract.getWindowCornerRadius(context);
            }

            @Override
            public float computeWindowCornerRadius(Context context) {
                return QuickStepContract.getWindowCornerRadius(context);
            }
        };
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBackgroundView = findViewById(R.id.background);

        int topMarginPx =
                mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx;
        FrameLayout.LayoutParams params = (LayoutParams) mBackgroundView.getLayoutParams();
        params.topMargin = topMarginPx;
        mBackgroundView.setLayoutParams(params);

        float[] outerRadii = new float[8];
        Arrays.fill(outerRadii, getTaskCornerRadius());
        RoundRectShape shape = new RoundRectShape(outerRadii, null, null);
        ShapeDrawable background = new ShapeDrawable(shape);
        background.setTint(getResources().getColor(android.R.color.system_neutral2_300,
                getContext().getTheme()));
        // TODO(b/244348395): this should be wallpaper
        mBackgroundView.setBackground(background);

        Drawable icon = getResources().getDrawable(R.drawable.ic_desktop, getContext().getTheme());
        Drawable iconBackground = getResources().getDrawable(R.drawable.bg_circle,
                getContext().getTheme());
        mIconView.setDrawable(new LayerDrawable(new Drawable[]{iconBackground, icon}));

        mChildCountAtInflation = getChildCount();
    }

    @Override
    protected Unit updateBorderBounds(@NonNull Rect bounds) {
        bounds.set(mBackgroundView.getLeft(), mBackgroundView.getTop(), mBackgroundView.getRight(),
                mBackgroundView.getBottom());
        return Unit.INSTANCE;
    }

    @Override
    public void bind(Task task, RecentsOrientedState orientedState) {
        bind(Collections.singletonList(task), orientedState);
    }

    /**
     * Updates this desktop task to the gives task list defined in {@code tasks}
     */
    public void bind(List<Task> tasks, RecentsOrientedState orientedState) {
        if (DEBUG) {
            StringBuilder sb = new StringBuilder();
            sb.append("bind tasks=").append(tasks.size()).append("\n");
            for (Task task : tasks) {
                sb.append(" key=").append(task.key).append("\n");
            }
            Log.d(TAG, sb.toString());
        }
        cancelPendingLoadTasks();

        mTasks = new ArrayList<>(tasks);
        mSnapshotViewMap.clear();

        // Ensure there are equal number of snapshot views and tasks.
        // More tasks than views, add views. More views than tasks, remove views.
        // TODO(b/251586230): use a ViewPool for creating TaskThumbnailViews
        if (mSnapshotViews.size() > mTasks.size()) {
            int diff = mSnapshotViews.size() - mTasks.size();
            for (int i = 0; i < diff; i++) {
                TaskThumbnailView snapshotView = mSnapshotViews.remove(0);
                removeView(snapshotView);
            }
        } else if (mSnapshotViews.size() < mTasks.size()) {
            int diff = mTasks.size() - mSnapshotViews.size();
            for (int i = 0; i < diff; i++) {
                TaskThumbnailView snapshotView = new TaskThumbnailView(getContext());
                mSnapshotViews.add(snapshotView);
                // Add snapshots from to position after the initial child views.
                addView(snapshotView, mChildCountAtInflation,
                        new LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            }
        }

        for (int i = 0; i < mTasks.size(); i++) {
            Task task = mTasks.get(i);
            TaskThumbnailView snapshotView = mSnapshotViews.get(i);
            snapshotView.bind(task);
            mSnapshotViewMap.put(task.key.id, snapshotView);
        }

        updateTaskIdContainer();
        updateTaskIdAttributeContainer();

        setOrientationState(orientedState);
    }

    private void updateTaskIdContainer() {
        // TODO(b/249371338): TaskView expects the array to have at least 2 elements.
        // At least 2 elements in the array
        mTaskIdContainer = new int[Math.max(mTasks.size(), 2)];
        for (int i = 0; i < mTasks.size(); i++) {
            mTaskIdContainer[i] = mTasks.get(i).key.id;
        }
    }

    private void updateTaskIdAttributeContainer() {
        // TODO(b/249371338): TaskView expects the array to have at least 2 elements.
        // At least 2 elements in the array
        mTaskIdAttributeContainer = new TaskIdAttributeContainer[Math.max(mTasks.size(), 2)];
        for (int i = 0; i < mTasks.size(); i++) {
            Task task = mTasks.get(i);
            TaskThumbnailView thumbnailView = mSnapshotViewMap.get(task.key.id);
            mTaskIdAttributeContainer[i] = createAttributeContainer(task, thumbnailView);
        }
    }

    private TaskIdAttributeContainer createAttributeContainer(Task task,
            TaskThumbnailView thumbnailView) {
        return new TaskIdAttributeContainer(task, thumbnailView, createIconView(task),
                STAGE_POSITION_UNDEFINED);
    }

    private IconView createIconView(Task task) {
        IconView iconView = new IconView(mContext);
        PackageManager pm = mContext.getApplicationContext().getPackageManager();
        try {
            IconProvider provider = new IconProvider(mContext);
            Drawable appIcon = provider.getIcon(pm.getActivityInfo(task.topActivity,
                    PackageManager.ComponentInfoFlags.of(0)));
            iconView.setDrawable(appIcon);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + task.topActivity.getPackageName(), e);
        }
        return iconView;
    }

    @Nullable
    @Override
    public Task getTask() {
        // TODO(b/249371338): returning first task. This won't work well with multiple tasks.
        return mTasks.size() > 0 ? mTasks.get(0) : null;
    }

    @Override
    public TaskThumbnailView getThumbnail() {
        // TODO(b/249371338): returning single thumbnail. This won't work well with multiple tasks.
        Task task = getTask();
        if (task != null) {
            return mSnapshotViewMap.get(task.key.id);
        }
        // Return the place holder snapshot views. Callers expect this to be non-null
        return mSnapshotView;
    }

    @Override
    public boolean containsTaskId(int taskId) {
        // Thumbnail map contains taskId -> thumbnail map. Use the keys for contains
        return mSnapshotViewMap.contains(taskId);
    }

    @Override
    public void onTaskListVisibilityChanged(boolean visible, int changes) {
        cancelPendingLoadTasks();
        if (visible) {
            RecentsModel model = RecentsModel.INSTANCE.get(getContext());
            TaskThumbnailCache thumbnailCache = model.getThumbnailCache();

            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                for (Task task : mTasks) {
                    CancellableTask<?> thumbLoadRequest =
                            thumbnailCache.updateThumbnailInBackground(task, thumbnailData -> {
                                TaskThumbnailView thumbnailView = mSnapshotViewMap.get(task.key.id);
                                if (thumbnailView != null) {
                                    thumbnailView.setThumbnail(task, thumbnailData);
                                }
                            });
                    if (thumbLoadRequest != null) {
                        mPendingThumbnailRequests.add(thumbLoadRequest);
                    }
                }
            }
        } else {
            if (needsUpdate(changes, FLAG_UPDATE_THUMBNAIL)) {
                for (Task task : mTasks) {
                    TaskThumbnailView thumbnailView = mSnapshotViewMap.get(task.key.id);
                    if (thumbnailView != null) {
                        thumbnailView.setThumbnail(null, null);
                    }
                    // Reset the task thumbnail ref
                    task.thumbnail = null;
                }
            }
        }
    }

    @Override
    protected void setThumbnailOrientation(RecentsOrientedState orientationState) {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        int thumbnailTopMargin = deviceProfile.overviewTaskThumbnailTopMarginPx;

        LayoutParams snapshotParams = (LayoutParams) mSnapshotView.getLayoutParams();
        snapshotParams.topMargin = thumbnailTopMargin;

        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            TaskThumbnailView thumbnailView = mSnapshotViewMap.valueAt(i);
            thumbnailView.setLayoutParams(snapshotParams);
        }
    }

    @Override
    protected void cancelPendingLoadTasks() {
        for (CancellableTask<?> cancellableTask : mPendingThumbnailRequests) {
            cancellableTask.cancel();
        }
        mPendingThumbnailRequests.clear();
    }

    @Override
    public boolean offerTouchToChildren(MotionEvent event) {
        return false;
    }

    @Override
    protected boolean showTaskMenuWithContainer(TaskViewIcon iconView) {
        return false;
    }

    @Nullable
    @Override
    public RunnableList launchTaskAnimated() {
        RunnableList endCallback = new RunnableList();

        RecentsView recentsView = getRecentsView();
        DesktopRecentsTransitionController recentsController =
                recentsView.getDesktopRecentsController();
        if (recentsController != null) {
            recentsController.launchDesktopFromRecents(this, success -> {
                endCallback.executeAllAndDestroy();
            });
        }

        // Callbacks get run from recentsView for case when recents animation already running
        recentsView.addSideTaskLaunchCallback(endCallback);
        return endCallback;
    }

    @Override
    public void launchTask(@NonNull Consumer<Boolean> callback, boolean isQuickswitch) {
        launchTasks();
        callback.accept(true);
    }

    @Override
    public boolean isDesktopTask() {
        return true;
    }

    @Override
    void refreshThumbnails(@Nullable HashMap<Integer, ThumbnailData> thumbnailDatas) {
        // Sets new thumbnails based on the incoming data and refreshes the rest.
        // Create a copy of the thumbnail map, so we can track thumbnails that need refreshing.
        SparseArray<TaskThumbnailView> thumbnailsToRefresh = mSnapshotViewMap.clone();
        if (thumbnailDatas != null) {
            for (Task task : mTasks) {
                int key = task.key.id;
                TaskThumbnailView thumbnailView = thumbnailsToRefresh.get(key);
                ThumbnailData thumbnailData = thumbnailDatas.get(key);
                if (thumbnailView != null && thumbnailData != null) {
                    thumbnailView.setThumbnail(task, thumbnailData);
                    // Remove this thumbnail from the list that should be refreshed.
                    thumbnailsToRefresh.remove(key);
                }
            }
        }

        // Refresh the rest that were not updated.
        for (int i = 0; i < thumbnailsToRefresh.size(); i++) {
            thumbnailsToRefresh.valueAt(i).refresh();
        }
    }

    @Override
    public TaskThumbnailView[] getThumbnails() {
        TaskThumbnailView[] thumbnails = new TaskThumbnailView[mSnapshotViewMap.size()];
        for (int i = 0; i < thumbnails.length; i++) {
            thumbnails[i] = mSnapshotViewMap.valueAt(i);
        }
        return thumbnails;
    }

    @Override
    public void onRecycle() {
        resetPersistentViewTransforms();
        // Clear any references to the thumbnail (it will be re-read either from the cache or the
        // system on next bind)
        for (Task task : mTasks) {
            TaskThumbnailView thumbnailView = mSnapshotViewMap.get(task.key.id);
            if (thumbnailView != null) {
                thumbnailView.setThumbnail(task, null);
            }
        }
        setOverlayEnabled(false);
        onTaskListVisibilityChanged(false);
        setVisibility(VISIBLE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int containerWidth = MeasureSpec.getSize(widthMeasureSpec);
        int containerHeight = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(containerWidth, containerHeight);

        int thumbnailTopMarginPx = mActivity.getDeviceProfile().overviewTaskThumbnailTopMarginPx;
        containerHeight -= thumbnailTopMarginPx;

        int thumbnails = mSnapshotViewMap.size();
        if (thumbnails == 0) {
            return;
        }

        int windowWidth = mActivity.getDeviceProfile().widthPx;
        int windowHeight = mActivity.getDeviceProfile().heightPx;

        float scaleWidth = containerWidth / (float) windowWidth;
        float scaleHeight = containerHeight / (float) windowHeight;

        if (DEBUG) {
            Log.d(TAG,
                    "onMeasure: container=[" + containerWidth + "," + containerHeight + "] window=["
                            + windowWidth + "," + windowHeight + "] scale=[" + scaleWidth + ","
                            + scaleHeight + "]");
        }

        // Desktop tile is a shrunk down version of launcher and freeform task thumbnails.
        for (int i = 0; i < mTasks.size(); i++) {
            Task task = mTasks.get(i);
            Rect taskSize = task.appBounds;
            if (taskSize == null) {
                // Default to quarter of the desktop if we did not get app bounds.
                taskSize = new Rect(0, 0, windowWidth / 4, windowHeight / 4);
            }

            int thumbWidth = (int) (taskSize.width() * scaleWidth);
            int thumbHeight = (int) (taskSize.height() * scaleHeight);

            TaskThumbnailView thumbnailView = mSnapshotViewMap.get(task.key.id);
            if (thumbnailView != null) {
                thumbnailView.measure(MeasureSpec.makeMeasureSpec(thumbWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(thumbHeight, MeasureSpec.EXACTLY));

                // Position the task to the same position as it would be on the desktop
                Point positionInParent = task.positionInParent;
                if (positionInParent == null) {
                    positionInParent = new Point(0, 0);
                }
                int taskX = (int) (positionInParent.x * scaleWidth);
                int taskY = (int) (positionInParent.y * scaleHeight);
                // move task down by margin size
                taskY += thumbnailTopMarginPx;
                thumbnailView.setX(taskX);
                thumbnailView.setY(taskY);

                if (DEBUG) {
                    Log.d(TAG, "onMeasure: task=" + task.key + " thumb=[" + thumbWidth + ","
                            + thumbHeight + "]" + " pos=[" + taskX + "," + taskY + "]");
                }
            }
        }
    }

    @Override
    public void setOverlayEnabled(boolean overlayEnabled) {
        // Intentional no-op to prevent setting smart actions overlay on thumbnails
    }

    @Override
    public void setFullscreenProgress(float progress) {
        // TODO(b/249371338): this copies parent implementation and makes it work for N thumbs
        progress = Utilities.boundToRange(progress, 0, 1);
        mFullscreenProgress = progress;
        if (mFullscreenProgress > 0) {
            // Don't show background while we are transitioning to/from fullscreen
            mBackgroundView.setVisibility(INVISIBLE);
        } else {
            mBackgroundView.setVisibility(VISIBLE);
        }
        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            TaskThumbnailView thumbnailView = mSnapshotViewMap.valueAt(i);
            thumbnailView.getTaskOverlay().setFullscreenProgress(progress);
        }
        updateSnapshotRadius();
    }

    @Override
    protected void updateSnapshotRadius() {
        super.updateSnapshotRadius();
        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            if (i == 0) {
                // All snapshots share the same params. Only update it with the first snapshot.
                updateFullscreenParams(mSnapshotDrawParams);
            }
            mSnapshotViewMap.valueAt(i).setFullscreenParams(mSnapshotDrawParams);
        }
    }

    @Override
    protected void setIconsAndBannersTransitionProgress(float progress, boolean invert) {
        // no-op
    }

    @Override
    public void setColorTint(float amount, int tintColor) {
        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            mSnapshotViewMap.valueAt(i).setDimAlpha(amount);
        }
    }

    @Override
    protected void applyThumbnailSplashAlpha() {
        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            mSnapshotViewMap.valueAt(i).setSplashAlpha(mTaskThumbnailSplashAlpha);
        }
    }

    @Override
    void setThumbnailVisibility(int visibility, int taskId) {
        for (int i = 0; i < mSnapshotViewMap.size(); i++) {
            mSnapshotViewMap.valueAt(i).setVisibility(visibility);
        }
    }

    @Override
    protected boolean confirmSecondSplitSelectApp() {
        // Desktop tile can't be in split screen
        return false;
    }
}
