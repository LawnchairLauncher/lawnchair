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

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import static androidx.recyclerview.widget.LinearLayoutManager.VERTICAL;

import static com.android.quickstep.TaskAdapter.CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT;
import static com.android.quickstep.TaskAdapter.ITEM_TYPE_CLEAR_ALL;
import static com.android.quickstep.TaskAdapter.ITEM_TYPE_TASK;
import static com.android.quickstep.TaskAdapter.TASKS_START_POSITION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.quickstep.ContentFillItemAnimator;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RecentsToActivityHelper;
import com.android.quickstep.TaskActionController;
import com.android.quickstep.TaskAdapter;
import com.android.quickstep.TaskHolder;
import com.android.quickstep.TaskListLoader;
import com.android.quickstep.TaskSwipeCallback;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Root view for the icon recents view. Acts as the main interface to the rest of the Launcher code
 * base.
 */
public final class IconRecentsView extends FrameLayout implements Insettable {

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
    private final LinearLayoutManager mTaskLayoutManager;
    private final TaskActionController mTaskActionController;
    private final DefaultItemAnimator mDefaultItemAnimator = new DefaultItemAnimator();
    private final ContentFillItemAnimator mLoadingContentItemAnimator =
            new ContentFillItemAnimator();
    private final BaseActivity mActivity;
    private final Drawable mStatusBarForegroundScrim;

    private RecentsToActivityHelper mActivityHelper;
    private RecyclerView mTaskRecyclerView;
    private View mShowingContentView;
    private View mEmptyView;
    private View mContentView;
    private boolean mTransitionedFromApp;
    private AnimatorSet mLayoutAnimation;
    private final ArraySet<View> mLayingOutViews = new ArraySet<>();
    private Rect mInsets;
    private final RecentsModel.TaskThumbnailChangeListener listener = (taskId, thumbnailData) -> {
        ArrayList<TaskItemView> itemViews = getTaskViews();
        for (int i = 0, size = itemViews.size(); i < size; i++) {
            TaskItemView taskView = itemViews.get(i);
            TaskHolder taskHolder = (TaskHolder) mTaskRecyclerView.getChildViewHolder(taskView);
            Optional<Task> optTask = taskHolder.getTask();
            if (optTask.filter(task -> task.key.id == taskId).isPresent()) {
                Task task = optTask.get();
                // Update thumbnail on the task.
                task.thumbnail = thumbnailData;
                taskView.setThumbnail(thumbnailData);
                return task;
            }
        }
        return null;
    };

    public IconRecentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mActivity = BaseActivity.fromContext(context);
        mContext = context;
        mStatusBarForegroundScrim  =
                Themes.getAttrDrawable(mContext, R.attr.workspaceStatusBarScrim);
        mTaskLoader = new TaskListLoader(mContext);
        mTaskAdapter = new TaskAdapter(mTaskLoader);
        mTaskAdapter.setOnClearAllClickListener(view -> animateClearAllTasks());
        mTaskActionController = new TaskActionController(mTaskLoader, mTaskAdapter);
        mTaskAdapter.setActionController(mTaskActionController);
        mTaskLayoutManager = new LinearLayoutManager(mContext, VERTICAL, true /* reverseLayout */);
        RecentsModel.INSTANCE.get(context).addThumbnailChangeListener(listener);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mTaskRecyclerView == null) {
            mTaskRecyclerView = findViewById(R.id.recent_task_recycler_view);
            mTaskRecyclerView.setAdapter(mTaskAdapter);
            mTaskRecyclerView.setLayoutManager(mTaskLayoutManager);
            ItemTouchHelper helper = new ItemTouchHelper(
                    new TaskSwipeCallback(holder -> {
                        mTaskActionController.removeTask(holder);
                        if (mTaskLoader.getCurrentTaskList().isEmpty()) {
                            mActivityHelper.leaveRecents();
                        }
                    }));
            helper.attachToRecyclerView(mTaskRecyclerView);
            mTaskRecyclerView.addOnChildAttachStateChangeListener(
                    new OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@NonNull View view) {
                            if (mLayoutAnimation != null && !mLayingOutViews.contains(view)) {
                                // Child view was added that is not part of current layout animation
                                // so restart the animation.
                                animateFadeInLayoutAnimation();
                            }
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(@NonNull View view) { }
                    });
            mTaskRecyclerView.setItemAnimator(mDefaultItemAnimator);
            mLoadingContentItemAnimator.setOnAnimationFinishedRunnable(
                    () -> mTaskRecyclerView.setItemAnimator(new DefaultItemAnimator()));
            ItemDecoration marginDecorator = new ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                        @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    // TODO: Determine if current margins cause off screen item to be fully off
                    // screen and if so, modify them so that it is partially off screen.
                    int itemType = parent.getChildViewHolder(view).getItemViewType();
                    Resources res = getResources();
                    switch (itemType) {
                        case ITEM_TYPE_CLEAR_ALL:
                            outRect.top = (int) res.getDimension(
                                    R.dimen.clear_all_item_view_top_margin);
                            int desiredBottomMargin = (int) res.getDimension(
                                    R.dimen.clear_all_item_view_bottom_margin);
                            // Only add bottom margin if insets aren't enough.
                            if (mInsets.bottom < desiredBottomMargin) {
                                outRect.bottom = desiredBottomMargin - mInsets.bottom;
                            }
                            break;
                        case ITEM_TYPE_TASK:
                            int desiredTopMargin = (int) res.getDimension(
                                    R.dimen.task_item_top_margin);
                            if (mTaskRecyclerView.getChildAdapterPosition(view) ==
                                    state.getItemCount() - 1) {
                                // Only add top margin to top task view if insets aren't enough.
                                if (mInsets.top < desiredTopMargin) {
                                    outRect.top = desiredTopMargin - mInsets.bottom;
                                }
                                return;
                            }
                            outRect.top = desiredTopMargin;
                            break;
                        default:
                    }
                }
            };
            mTaskRecyclerView.addItemDecoration(marginDecorator);

            mEmptyView = findViewById(R.id.recent_task_empty_view);
            mContentView = mTaskRecyclerView;
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

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int childCount = mTaskRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            mTaskRecyclerView.getChildAt(i).setEnabled(enabled);
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
     */
    public void onBeginTransitionToOverview() {
        if (mContext.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
            // Scroll to bottom of task in landscape mode. This is a non-issue in portrait mode as
            // all tasks should be visible to fill up the screen in portrait mode and the view will
            // not be scrollable.
            mTaskLayoutManager.scrollToPositionWithOffset(TASKS_START_POSITION, 0 /* offset */);
        }
        scheduleFadeInLayoutAnimation();
        // Load any task changes
        if (!mTaskLoader.needsToLoad()) {
            return;
        }
        mTaskAdapter.setIsShowingLoadingUi(true);
        mTaskAdapter.notifyDataSetChanged();
        mTaskLoader.loadTaskList(tasks -> {
            int numEmptyItems = mTaskAdapter.getItemCount() - TASKS_START_POSITION;
            mTaskAdapter.setIsShowingLoadingUi(false);
            int numActualItems = mTaskAdapter.getItemCount() - TASKS_START_POSITION;
            if (numEmptyItems < numActualItems) {
                throw new IllegalStateException("There are less empty item views than the number "
                        + "of items to animate to.");
            }
            // Possible that task list loads faster than adapter changes propagate to layout so
            // only start content fill animation if there aren't any pending adapter changes.
            if (!mTaskRecyclerView.hasPendingAdapterUpdates()) {
                // Set item animator for content filling animation. The item animator will switch
                // back to the default on completion
                mTaskRecyclerView.setItemAnimator(mLoadingContentItemAnimator);
            }
            mTaskAdapter.notifyItemRangeRemoved(TASKS_START_POSITION + numActualItems,
                    numEmptyItems - numActualItems);
            mTaskAdapter.notifyItemRangeChanged(TASKS_START_POSITION, numActualItems,
                    CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT);
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
        List<Task> tasks = mTaskLoader.getCurrentTaskList();
        int tasksSize = tasks.size();
        if (tasksSize == 0) {
            // Do nothing
            return;
        }
        Task taskToLaunch;
        if (mTransitionedFromApp && tasksSize > 1) {
            // Launch the next most recent app
            taskToLaunch = tasks.get(1);
        } else {
            // Launch the most recent app
            taskToLaunch = tasks.get(0);
        }

        // See if view for this task is attached, and if so, animate launch from that view.
        ArrayList<TaskItemView> itemViews = getTaskViews();
        for (int i = 0, size = itemViews.size(); i < size; i++) {
            TaskItemView taskView = itemViews.get(i);
            TaskHolder holder = (TaskHolder) mTaskRecyclerView.getChildViewHolder(taskView);
            if (Objects.equals(holder.getTask(), Optional.of(taskToLaunch))) {
                mTaskActionController.launchTaskFromView(holder);
                return;
            }
        }

        // Otherwise, just use a basic launch animation.
        mTaskActionController.launchTask(taskToLaunch);
    }

    /**
     * Set whether or not to show the scrim in between the view and the top insets. This only works
     * if the view is being insetted in the first place.
     *
     * The scrim is added to the activity's root view to prevent animations on this view
     * affecting the scrim. As a result, it is the activity's responsibility to show/hide this
     * scrim as appropriate.
     *
     * @param showStatusBarForegroundScrim true to show the scrim, false to hide
     */
    public void setShowStatusBarForegroundScrim(boolean showStatusBarForegroundScrim) {
        boolean shouldShow = mInsets.top != 0 && showStatusBarForegroundScrim;
        mActivity.getDragLayer().setForeground(shouldShow ? mStatusBarForegroundScrim : null);
    }

    /**
     * Get the bottom most thumbnail view to animate to.
     *
     * @return the thumbnail view if laid out
     */
    public @Nullable View getBottomThumbnailView() {
        ArrayList<TaskItemView> taskViews = getTaskViews();
        if (taskViews.isEmpty()) {
            return null;
        }
        TaskItemView view = taskViews.get(0);
        return view.getThumbnailView();
    }

    /**
     * Clear all tasks and animate out.
     */
    private void animateClearAllTasks() {
        setEnabled(false);
        ArrayList<TaskItemView> itemViews = getTaskViews();

        AnimatorSet clearAnim = new AnimatorSet();
        long currentDelay = 0;

        // Animate each item view to the right and fade out.
        for (int i = 0, size = itemViews.size(); i < size; i++) {
            TaskItemView itemView = itemViews.get(i);
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
                for (int i = 0, size = itemViews.size(); i < size; i++) {
                    TaskItemView itemView = itemViews.get(i);
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
     * @return array list of attached task item views
     */
    private ArrayList<TaskItemView> getTaskViews() {
        int taskCount = mTaskRecyclerView.getChildCount();
        ArrayList<TaskItemView> itemViews = new ArrayList<>();
        for (int i = 0; i < taskCount; i ++) {
            View child = mTaskRecyclerView.getChildAt(i);
            if (child instanceof TaskItemView) {
                itemViews.add((TaskItemView) child);
            }
        }
        return itemViews;
    }

    /**
     * Update the content view so that the appropriate view is shown based off the current list
     * of tasks.
     */
    private void updateContentViewVisibility() {
        int taskListSize = mTaskAdapter.getItemCount() - TASKS_START_POSITION;
        if (mShowingContentView != mEmptyView && taskListSize == 0) {
            mShowingContentView = mEmptyView;
            crossfadeViews(mEmptyView, mContentView);
        }
        if (mShowingContentView != mContentView && taskListSize > 0) {
            mShowingContentView = mContentView;
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
        fadeInView.animate().cancel();
        fadeInView.setVisibility(VISIBLE);
        fadeInView.setAlpha(0f);
        fadeInView.animate()
                .alpha(1f)
                .setDuration(CROSSFADE_DURATION)
                .setListener(null);

        fadeOutView.animate().cancel();
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

    /**
     * Schedule a one-shot layout animation on the next layout. Separate from
     * {@link #scheduleLayoutAnimation()} as the animation is {@link Animator} based and acts on the
     * view properties themselves, allowing more controllable behavior and making it easier to
     * manage when the animation conflicts with another animation.
     */
    private void scheduleFadeInLayoutAnimation() {
        mTaskRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        animateFadeInLayoutAnimation();
                        mTaskRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    /**
     * Start animating the layout animation where items fade in.
     */
    private void animateFadeInLayoutAnimation() {
        if (mLayoutAnimation != null) {
            // If layout animation still in progress, cancel and restart.
            mLayoutAnimation.cancel();
        }
        ArrayList<TaskItemView> views = getTaskViews();
        int delay = 0;
        mLayoutAnimation = new AnimatorSet();
        for (int i = 0, size = views.size(); i < size; i++) {
            TaskItemView view = views.get(i);
            view.setAlpha(0.0f);
            Animator alphaAnim = ObjectAnimator.ofFloat(view, ALPHA, 0.0f, 1.0f);
            alphaAnim.setDuration(LAYOUT_ITEM_ANIMATE_IN_DURATION).setStartDelay(delay);
            alphaAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setAlpha(1.0f);
                    mLayingOutViews.remove(view);
                }
            });
            delay += LAYOUT_ITEM_ANIMATE_IN_DELAY_BETWEEN;
            mLayoutAnimation.play(alphaAnim);
            mLayingOutViews.add(view);
        }
        mLayoutAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLayoutAnimation = null;
            }
        });
        mLayoutAnimation.start();
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets = insets;
        mTaskRecyclerView.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        mTaskRecyclerView.invalidateItemDecorations();
    }
}
