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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

import com.android.launcher3.R;
import com.android.quickstep.RecentsToActivityHelper;
import com.android.quickstep.TaskActionController;
import com.android.quickstep.TaskAdapter;
import com.android.quickstep.TaskHolder;
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
    private static final long LAYOUT_ITEM_ANIMATE_IN_DURATION = 150;
    private static final long LAYOUT_ITEM_ANIMATE_IN_DELAY_BETWEEN = 40;
    private static final long ITEM_ANIMATE_OUT_DURATION = 150;
    private static final long ITEM_ANIMATE_OUT_DELAY_BETWEEN = 40;
    private static final float ITEM_ANIMATE_OUT_TRANSLATION_X_RATIO = .25f;
    private static final long CLEAR_ALL_FADE_DELAY = 120;

    /**
     * A ratio representing the view's relative placement within its padded space. For example, 0
     * is top aligned and 0.5 is centered vertically.
     */
    @ViewDebug.ExportedProperty(category = "launcher")

    private final Context mContext;
    private final TaskListLoader mTaskLoader;
    private final TaskAdapter mTaskAdapter;
    private final TaskActionController mTaskActionController;
    private final LayoutAnimationController mLayoutAnimation;

    private RecentsToActivityHelper mActivityHelper;
    private RecyclerView mTaskRecyclerView;
    private View mEmptyView;
    private View mContentView;
    private View mClearAllView;
    private boolean mTransitionedFromApp;

    public IconRecentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTaskLoader = new TaskListLoader(mContext);
        mTaskAdapter = new TaskAdapter(mTaskLoader);
        mTaskActionController = new TaskActionController(mTaskLoader, mTaskAdapter);
        mTaskAdapter.setActionController(mTaskActionController);
        mLayoutAnimation = createLayoutAnimation();
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
                    new TaskSwipeCallback(mTaskActionController));
            helper.attachToRecyclerView(mTaskRecyclerView);
            mTaskRecyclerView.setLayoutAnimation(mLayoutAnimation);

            mEmptyView = findViewById(R.id.recent_task_empty_view);
            mContentView = findViewById(R.id.recent_task_content_view);
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
            mClearAllView = findViewById(R.id.clear_all_button);
            mClearAllView.setOnClickListener(v -> animateClearAllTasks());
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        TaskItemView[] itemViews = getTaskViews();
        for (TaskItemView itemView : itemViews) {
            itemView.setEnabled(enabled);
        }
        mClearAllView.setEnabled(enabled);
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
     */
    public void onBeginTransitionToOverview() {
        mTaskRecyclerView.scheduleLayoutAnimation();

        // Load any task changes
        if (!mTaskLoader.needsToLoad()) {
            return;
        }
        mTaskAdapter.setIsShowingLoadingUi(true);
        mTaskAdapter.notifyDataSetChanged();
        mTaskLoader.loadTaskList(tasks -> {
            mTaskAdapter.setIsShowingLoadingUi(false);
            // TODO: Animate the loading UI out and the loaded data in.
            mTaskAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Set whether we transitioned to recents from the most recent app.
     *
     * @param transitionedFromApp true if transitioned from the most recent app, false otherwise
     */
    public void setTransitionedFromApp(boolean transitionedFromApp) {
        mTransitionedFromApp = transitionedFromApp;
    }

    /**
     * Handles input from the overview button. Launch the most recent task unless we just came from
     * the app. In that case, we launch the next most recent.
     */
    public void handleOverviewCommand() {
        int childCount = mTaskRecyclerView.getChildCount();
        if (childCount == 0) {
            // Do nothing
            return;
        }
        TaskHolder taskToLaunch;
        if (mTransitionedFromApp && childCount > 1) {
            // Launch the next most recent app
            TaskItemView itemView = (TaskItemView) mTaskRecyclerView.getChildAt(1);
            taskToLaunch = (TaskHolder) mTaskRecyclerView.getChildViewHolder(itemView);
        } else {
            // Launch the most recent app
            TaskItemView itemView = (TaskItemView) mTaskRecyclerView.getChildAt(0);
            taskToLaunch = (TaskHolder) mTaskRecyclerView.getChildViewHolder(itemView);
        }
        mTaskActionController.launchTask(taskToLaunch);
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
     * Clear all tasks and animate out.
     */
    private void animateClearAllTasks() {
        setEnabled(false);
        TaskItemView[] itemViews = getTaskViews();

        AnimatorSet clearAnim = new AnimatorSet();
        long currentDelay = 0;

        // Animate each item view to the right and fade out.
        for (TaskItemView itemView : itemViews) {
            PropertyValuesHolder transXproperty = PropertyValuesHolder.ofFloat(TRANSLATION_X,
                    0, itemView.getWidth() * ITEM_ANIMATE_OUT_TRANSLATION_X_RATIO);
            PropertyValuesHolder alphaProperty = PropertyValuesHolder.ofFloat(ALPHA, 1.0f, 0f);
            ObjectAnimator itemAnim = ObjectAnimator.ofPropertyValuesHolder(itemView,
                    transXproperty, alphaProperty);
            itemAnim.setDuration(ITEM_ANIMATE_OUT_DURATION);
            itemAnim.setStartDelay(currentDelay);

            clearAnim.play(itemAnim);
            currentDelay += ITEM_ANIMATE_OUT_DELAY_BETWEEN;
        }

        // Animate view fading and leave recents when faded enough.
        ValueAnimator contentAlpha = ValueAnimator.ofFloat(1.0f, 0f)
                .setDuration(CROSSFADE_DURATION);
        contentAlpha.setStartDelay(CLEAR_ALL_FADE_DELAY);
        contentAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private boolean mLeftRecents = false;

            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mContentView.setAlpha((float) valueAnimator.getAnimatedValue());
                // Leave recents while fading out.
                if ((float) valueAnimator.getAnimatedValue() < .5f && !mLeftRecents) {
                    mActivityHelper.leaveRecents();
                    mLeftRecents = true;
                }
            }
        });

        clearAnim.play(contentAlpha);
        clearAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (TaskItemView itemView : itemViews) {
                    itemView.setTranslationX(0);
                    itemView.setAlpha(1.0f);
                }
                setEnabled(true);
                mContentView.setVisibility(GONE);
                mTaskActionController.clearAllTasks();
            }
        });
        clearAnim.start();
    }

    /**
     * Get attached task item views ordered by most recent.
     *
     * @return array of attached task item views
     */
    private TaskItemView[] getTaskViews() {
        int taskCount = mTaskRecyclerView.getChildCount();
        TaskItemView[] itemViews = new TaskItemView[taskCount];
        for (int i = 0; i < taskCount; i ++) {
            itemViews[i] = (TaskItemView) mTaskRecyclerView.getChildAt(i);
        }
        return itemViews;
    }

    /**
     * Update the content view so that the appropriate view is shown based off the current list
     * of tasks.
     */
    private void updateContentViewVisibility() {
        int taskListSize = mTaskLoader.getCurrentTaskList().size();
        if (mEmptyView.getVisibility() != VISIBLE && taskListSize == 0) {
            crossfadeViews(mEmptyView, mContentView);
            mActivityHelper.leaveRecents();
        }
        if (mContentView.getVisibility() != VISIBLE && taskListSize > 0) {
            crossfadeViews(mContentView, mEmptyView);
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

    private static LayoutAnimationController createLayoutAnimation() {
        AnimationSet anim = new AnimationSet(false /* shareInterpolator */);

        Animation alphaAnim = new AlphaAnimation(0, 1);
        alphaAnim.setDuration(LAYOUT_ITEM_ANIMATE_IN_DURATION);
        anim.addAnimation(alphaAnim);

        LayoutAnimationController layoutAnim = new LayoutAnimationController(anim);
        layoutAnim.setDelay(
                (float) LAYOUT_ITEM_ANIMATE_IN_DELAY_BETWEEN / LAYOUT_ITEM_ANIMATE_IN_DURATION);

        return layoutAnim;
    }
}
