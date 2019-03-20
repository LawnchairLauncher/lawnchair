/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static androidx.recyclerview.widget.LinearLayoutManager.VERTICAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

import com.android.launcher3.R;
import com.android.quickstep.RecentsToActivityHelper;
import com.android.quickstep.TaskAdapter;
import com.android.quickstep.TaskInputController;
import com.android.quickstep.TaskListLoader;
import com.android.quickstep.TaskSwipeCallback;

/**
 * Root view for the icon recents view. Acts as the main interface to the rest of the Launcher code
 * base.
 */
public final class IconRecentsView extends FrameLayout {

    public static final FloatProperty<IconRecentsView> CONTENT_ALPHA =
            new FloatProperty<IconRecentsView>("contentAlpha") {
                @Override
                public void setValue(IconRecentsView view, float v) {
                    ALPHA.set(view, v);
                    if (view.getVisibility() != VISIBLE && v > 0) {
                        view.setVisibility(VISIBLE);
                    } else if (view.getVisibility() != GONE && v == 0){
                        view.setVisibility(GONE);
                    }
                }

                @Override
                public Float get(IconRecentsView view) {
                    return ALPHA.get(view);
                }
            };
    private static final long CROSSFADE_DURATION = 300;

    /**
     * A ratio representing the view's relative placement within its padded space. For example, 0
     * is top aligned and 0.5 is centered vertically.
     */
    @ViewDebug.ExportedProperty(category = "launcher")

    private final Context mContext;
    private final TaskListLoader mTaskLoader;
    private final TaskAdapter mTaskAdapter;
    private final TaskInputController mTaskInputController;

    private RecentsToActivityHelper mActivityHelper;
    private RecyclerView mTaskRecyclerView;
    private View mEmptyView;

    public IconRecentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTaskLoader = new TaskListLoader(mContext);
        mTaskAdapter = new TaskAdapter(mTaskLoader);
        mTaskInputController = new TaskInputController(mTaskLoader, mTaskAdapter);
        mTaskAdapter.setInputController(mTaskInputController);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mTaskRecyclerView == null) {
            mTaskRecyclerView = findViewById(R.id.recent_task_recycler_view);
            mTaskRecyclerView.setAdapter(mTaskAdapter);
            mTaskRecyclerView.setLayoutManager(
                    new LinearLayoutManager(mContext, VERTICAL, true /* reverseLayout */));
            ItemTouchHelper helper = new ItemTouchHelper(
                    new TaskSwipeCallback(mTaskInputController));
            helper.attachToRecyclerView(mTaskRecyclerView);

            mEmptyView = findViewById(R.id.recent_task_empty_view);
            mTaskAdapter.registerAdapterDataObserver(new AdapterDataObserver() {
                @Override
                public void onChanged() {
                    updateContentViewVisibility();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    updateContentViewVisibility();
                }
            });
        }
    }

    /**
     * Set activity helper for the view to callback to.
     *
     * @param helper the activity helper
     */
    public void setRecentsToActivityHelper(@NonNull RecentsToActivityHelper helper) {
        mActivityHelper = helper;
    }

    /**
     * Logic for when we know we are going to overview/recents and will be putting up the recents
     * view. This should be used to prepare recents (e.g. load any task data, etc.) before it
     * becomes visible.
     *
     * TODO: Hook this up for fallback recents activity as well
     */
    public void onBeginTransitionToOverview() {
        // Load any task changes
        mTaskLoader.loadTaskList(tasks -> {
            // TODO: Put up some loading UI while task content is loading. May have to do something
            // smarter when animating from app to overview.
            mTaskAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Get the thumbnail view associated with a task for the purposes of animation.
     *
     * @param taskId task id of thumbnail view to get
     * @return the thumbnail view for the task if attached, null otherwise
     */
    public @Nullable View getThumbnailViewForTask(int taskId) {
        TaskItemView view = mTaskAdapter.getTaskItemView(taskId);
        if (view == null) {
            return null;
        }
        return view.getThumbnailView();
    }

    /**
     * Update the content view so that the appropriate view is shown based off the current list
     * of tasks.
     */
    private void updateContentViewVisibility() {
        int taskListSize = mTaskLoader.getCurrentTaskList().size();
        if (mEmptyView.getVisibility() != VISIBLE && taskListSize == 0) {
            crossfadeViews(mEmptyView, mTaskRecyclerView);
            mActivityHelper.leaveRecents();
        }
        if (mTaskRecyclerView.getVisibility() != VISIBLE && taskListSize > 0) {
            crossfadeViews(mTaskRecyclerView, mEmptyView);
        }
    }

    /**
     * Animate views so that one view fades in while the other fades out.
     *
     * @param fadeInView view that should fade in
     * @param fadeOutView view that should fade out
     */
    private void crossfadeViews(View fadeInView, View fadeOutView) {
        fadeInView.setVisibility(VISIBLE);
        fadeInView.setAlpha(0f);
        fadeInView.animate()
                .alpha(1f)
                .setDuration(CROSSFADE_DURATION)
                .setListener(null);

        fadeOutView.animate()
                .alpha(0f)
                .setDuration(CROSSFADE_DURATION)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        fadeOutView.setVisibility(GONE);
                    }
                });
    }
}
