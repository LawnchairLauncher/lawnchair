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

import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.quickstep.TaskAdapter.CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT;
import static com.android.quickstep.TaskAdapter.ITEM_TYPE_CLEAR_ALL;
import static com.android.quickstep.TaskAdapter.ITEM_TYPE_TASK;
import static com.android.quickstep.TaskAdapter.MAX_TASKS_TO_DISPLAY;
import static com.android.quickstep.TaskAdapter.TASKS_START_POSITION;
import static com.android.quickstep.util.RemoteAnimationProvider.getLayer;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;
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
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

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

    private static final long REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION = 300;
    private static final long REMOTE_TO_RECENTS_VERTICAL_EASE_IN_DURATION = 400;
    private static final long REMOTE_TO_RECENTS_ITEM_FADE_START_DELAY = 200;
    private static final long REMOTE_TO_RECENTS_ITEM_FADE_DURATION = 217;
    private static final long REMOTE_TO_RECENTS_ITEM_FADE_BETWEEN_DELAY = 33;

    private static final PathInterpolator FAST_OUT_SLOW_IN_1 =
            new PathInterpolator(.4f, 0f, 0f, 1f);
    private static final PathInterpolator FAST_OUT_SLOW_IN_2 =
            new PathInterpolator(.5f, 0f, 0f, 1f);
    private static final LinearOutSlowInInterpolator OUT_SLOW_IN =
            new LinearOutSlowInInterpolator();

    public static final long REMOTE_APP_TO_OVERVIEW_DURATION =
            REMOTE_TO_RECENTS_VERTICAL_EASE_IN_DURATION;

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
    private boolean mUsingRemoteAnimation;
    private boolean mStartedEnterAnimation;
    private boolean mShowStatusBarForegroundScrim;
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
        mTaskActionController = new TaskActionController(mTaskLoader, mTaskAdapter,
                mActivity.getStatsLogManager());
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
                            outRect.bottom = (int) res.getDimension(
                                    R.dimen.clear_all_item_view_bottom_margin);
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
        mStartedEnterAnimation = false;
        if (mContext.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE) {
            // Scroll to bottom of task in landscape mode. This is a non-issue in portrait mode as
            // all tasks should be visible to fill up the screen in portrait mode and the view will
            // not be scrollable.
            mTaskLayoutManager.scrollToPositionWithOffset(TASKS_START_POSITION, 0 /* offset */);
        }
        if (!mUsingRemoteAnimation) {
            scheduleFadeInLayoutAnimation();
        }
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
            // only start content fill animation if there aren't any pending adapter changes and
            // we've started the on enter layout animation.
            boolean needsContentFillAnimation =
                    !mTaskRecyclerView.hasPendingAdapterUpdates() && mStartedEnterAnimation;
            if (needsContentFillAnimation) {
                // Set item animator for content filling animation. The item animator will switch
                // back to the default on completion
                mTaskRecyclerView.setItemAnimator(mLoadingContentItemAnimator);
                mTaskAdapter.notifyItemRangeRemoved(TASKS_START_POSITION + numActualItems,
                        numEmptyItems - numActualItems);
                mTaskAdapter.notifyItemRangeChanged(TASKS_START_POSITION, numActualItems,
                        CHANGE_EVENT_TYPE_EMPTY_TO_CONTENT);
            } else {
                // Notify change without animating.
                mTaskAdapter.notifyDataSetChanged();
            }
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
     * Set whether we're using a custom remote animation. If so, we will not do the default layout
     * animation when entering recents and instead wait for the remote app surface to be ready to
     * use.
     *
     * @param usingRemoteAnimation true if doing a remote animation, false o/w
     */
    public void setUsingRemoteAnimation(boolean usingRemoteAnimation) {
        mUsingRemoteAnimation = usingRemoteAnimation;
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
        mShowStatusBarForegroundScrim = showStatusBarForegroundScrim;
        if (mShowStatusBarForegroundScrim != showStatusBarForegroundScrim) {
            updateStatusBarScrim();
        }
    }

    private void updateStatusBarScrim() {
        boolean shouldShow = mInsets.top != 0 && mShowStatusBarForegroundScrim;
        mActivity.getDragLayer().setForeground(shouldShow ? mStatusBarForegroundScrim : null);
    }

    /**
     * Get the bottom most task view to animate to.
     *
     * @return the task view
     */
    private @Nullable TaskItemView getBottomTaskView() {
        int childCount = mTaskRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = mTaskRecyclerView.getChildAt(i);
            if (mTaskRecyclerView.getChildViewHolder(view).getItemViewType() == ITEM_TYPE_TASK) {
                return (TaskItemView) view;
            }
        }
        return null;
    }

    /**
     * Whether this view has processed all data changes and is ready to animate from the app to
     * the overview.
     *
     * @return true if ready to animate app to overview, false otherwise
     */
    public boolean isReadyForRemoteAnim() {
        return !mTaskRecyclerView.hasPendingAdapterUpdates();
    }

    /**
     * Set a callback for whenever this view is ready to do a remote animation from the app to
     * overview. See {@link #isReadyForRemoteAnim()}.
     *
     * @param callback callback to run when view is ready to animate
     */
    public void setOnReadyForRemoteAnimCallback(onReadyForRemoteAnimCallback callback) {
        mTaskRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (isReadyForRemoteAnim()) {
                            callback.onReadyForRemoteAnim();
                            mTaskRecyclerView.getViewTreeObserver().
                                    removeOnGlobalLayoutListener(this);
                        }
                    }
                });
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
        mStartedEnterAnimation = true;
    }

    /**
     * Play remote app to recents animation when the app is the home activity. We use a simple
     * cross-fade here. Note this is only used if the home activity is a separate app than the
     * recents activity.
     *
     * @param anim animator set
     * @param homeTarget the home surface thats closing
     * @param recentsTarget the surface containing recents
     */
    public void playRemoteHomeToRecentsAnimation(@NonNull AnimatorSet anim,
            @NonNull RemoteAnimationTargetCompat homeTarget,
            @NonNull RemoteAnimationTargetCompat recentsTarget) {
        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(this);

        SurfaceParams[] params = new SurfaceParams[2];
        int boostedMode = MODE_CLOSING;

        ValueAnimator remoteHomeAnim = ValueAnimator.ofFloat(0, 1);
        remoteHomeAnim.setDuration(REMOTE_APP_TO_OVERVIEW_DURATION);

        remoteHomeAnim.addUpdateListener(valueAnimator -> {
            float val = (float) valueAnimator.getAnimatedValue();
            float alpha;
            RemoteAnimationTargetCompat visibleTarget;
            RemoteAnimationTargetCompat invisibleTarget;
            if (val < .5f) {
                visibleTarget = homeTarget;
                invisibleTarget = recentsTarget;
                alpha = 1 - (val * 2);
            } else {
                visibleTarget = recentsTarget;
                invisibleTarget = homeTarget;
                alpha = (val - .5f) * 2;
            }
            params[0] = new SurfaceParams(visibleTarget.leash, alpha, null /* matrix */,
                    null /* windowCrop */, getLayer(visibleTarget, boostedMode),
                    0 /* cornerRadius */);
            params[1] = new SurfaceParams(invisibleTarget.leash, 0.0f, null /* matrix */,
                    null /* windowCrop */, getLayer(invisibleTarget, boostedMode),
                    0 /* cornerRadius */);
            surfaceApplier.scheduleApply(params);
        });
        anim.play(remoteHomeAnim);
        animateFadeInLayoutAnimation();
    }

    /**
     * Play remote animation from app to recents. This should scale the currently closing app down
     * to the recents thumbnail.
     *
     * @param anim animator set
     * @param appTarget the app surface thats closing
     * @param recentsTarget the surface containing recents
     */
    public void playRemoteAppToRecentsAnimation(@NonNull AnimatorSet anim,
            @NonNull RemoteAnimationTargetCompat appTarget,
            @NonNull RemoteAnimationTargetCompat recentsTarget) {
        TaskItemView bottomView = getBottomTaskView();
        if (bottomView == null) {
            // This can be null if there were previously 0 tasks and the recycler view has not had
            // enough time to take in the data change, bind a new view, and lay out the new view.
            // TODO: Have a fallback to animate to
            anim.play(ValueAnimator.ofInt(0, 1).setDuration(REMOTE_APP_TO_OVERVIEW_DURATION));
            return;
        }
        final Matrix appMatrix = new Matrix();
        playRemoteTransYAnim(anim, appMatrix);
        playRemoteAppScaleDownAnim(anim, appMatrix, appTarget, recentsTarget,
                bottomView.getThumbnailView());
        playRemoteTaskListFadeIn(anim, bottomView);
        mStartedEnterAnimation = true;
    }

    /**
     * Play translation Y animation for the remote app to recents animation. Animates over all task
     * views as well as the closing app, easing them into their final vertical positions.
     *
     * @param anim animator set to play on
     * @param appMatrix transformation matrix for the closing app surface
     */
    private void playRemoteTransYAnim(@NonNull AnimatorSet anim, @NonNull Matrix appMatrix) {
        final ArrayList<TaskItemView> views = getTaskViews();

        // Start Y translation from about halfway through the tasks list to the bottom thumbnail.
        float taskHeight = getResources().getDimension(R.dimen.task_item_height);
        float totalTransY = -(MAX_TASKS_TO_DISPLAY / 2.0f - 1) * taskHeight;
        for (int i = 0, size = views.size(); i < size; i++) {
            views.get(i).setTranslationY(totalTransY);
        }

        ValueAnimator transYAnim = ValueAnimator.ofFloat(totalTransY, 0);
        transYAnim.setDuration(REMOTE_TO_RECENTS_VERTICAL_EASE_IN_DURATION);
        transYAnim.setInterpolator(FAST_OUT_SLOW_IN_2);
        transYAnim.addUpdateListener(valueAnimator -> {
            float transY = (float) valueAnimator.getAnimatedValue();
            for (int i = 0, size = views.size(); i < size; i++) {
                views.get(i).setTranslationY(transY);
            }
            appMatrix.postTranslate(0, transY - totalTransY);
        });
        transYAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                for (int i = 0, size = views.size(); i < size; i++) {
                    views.get(i).setTranslationY(0);
                }
            }
        });
        anim.play(transYAnim);
    }

    /**
     * Play the scale down animation for the remote app to recents animation where the app surface
     * scales down to where the thumbnail is.
     *
     * @param anim animator set to play on
     * @param appMatrix transformation matrix for the app surface
     * @param appTarget closing app target
     * @param recentsTarget opening recents target
     * @param thumbnailView thumbnail view to animate to
     */
    private void playRemoteAppScaleDownAnim(@NonNull AnimatorSet anim, @NonNull Matrix appMatrix,
            @NonNull RemoteAnimationTargetCompat appTarget,
            @NonNull RemoteAnimationTargetCompat recentsTarget,
            @NonNull View thumbnailView) {
        // Identify where the entering remote app should animate to.
        Rect endRect = new Rect();
        thumbnailView.getGlobalVisibleRect(endRect);
        Rect appBounds = appTarget.sourceContainerBounds;
        RectF currentAppRect = new RectF();

        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(this);

        // Keep recents visible throughout the animation.
        SurfaceParams[] params = new SurfaceParams[2];
        // Closing app should stay on top.
        int boostedMode = MODE_CLOSING;
        params[0] = new SurfaceParams(recentsTarget.leash, 1f, null /* matrix */,
                null /* windowCrop */, getLayer(recentsTarget, boostedMode), 0 /* cornerRadius */);

        ValueAnimator remoteAppAnim = ValueAnimator.ofInt(0, 1);
        remoteAppAnim.setDuration(REMOTE_TO_RECENTS_VERTICAL_EASE_IN_DURATION);
        remoteAppAnim.addUpdateListener(new MultiValueUpdateListener() {
            private final FloatProp mScaleX;
            private final FloatProp mScaleY;
            private final FloatProp mTranslationX;
            private final FloatProp mTranslationY;
            private final FloatProp mAlpha;

            {
                // Scale down and move to view location.
                float endScaleX = ((float) endRect.width()) / appBounds.width();
                mScaleX = new FloatProp(1f, endScaleX, 0, REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION,
                        FAST_OUT_SLOW_IN_1);
                float endScaleY = ((float) endRect.height()) / appBounds.height();
                mScaleY = new FloatProp(1f, endScaleY, 0, REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION,
                        FAST_OUT_SLOW_IN_1);
                float endTranslationX = endRect.left -
                        (appBounds.width() - thumbnailView.getWidth()) / 2.0f;
                mTranslationX = new FloatProp(0, endTranslationX, 0,
                        REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION, FAST_OUT_SLOW_IN_1);
                float endTranslationY = endRect.top -
                        (appBounds.height() - thumbnailView.getHeight()) / 2.0f;
                mTranslationY = new FloatProp(0, endTranslationY, 0,
                        REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION, FAST_OUT_SLOW_IN_2);
                mAlpha = new FloatProp(1.0f, 0, 0, REMOTE_TO_RECENTS_APP_SCALE_DOWN_DURATION,
                        ACCEL_2);
            }

            @Override
            public void onUpdate(float percent) {
                Matrix m = new Matrix();
                m.preScale(mScaleX.value, mScaleY.value,
                        appBounds.width() / 2.0f, appBounds.height() / 2.0f);
                m.postTranslate(mTranslationX.value, mTranslationY.value);
                appMatrix.preConcat(m);
                params[1] = new SurfaceParams(appTarget.leash, mAlpha.value, appMatrix,
                        null /* windowCrop */, getLayer(appTarget, boostedMode),
                        0 /* cornerRadius */);
                surfaceApplier.scheduleApply(params);

                m.mapRect(currentAppRect, new RectF(appBounds));
                setViewToRect(thumbnailView, new RectF(endRect), currentAppRect);
                appMatrix.reset();
            }
        });
        remoteAppAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                thumbnailView.setTranslationY(0);
                thumbnailView.setTranslationX(0);
                thumbnailView.setScaleX(1);
                thumbnailView.setScaleY(1);
            }
        });
        anim.play(remoteAppAnim);
    }

    /**
     * Play task list fade in animation as part of remote app to recents animation. This animation
     * ensures that the task views in the recents list fade in from bottom to top.
     *
     * @param anim animator set to play on
     * @param appTaskView the task view associated with the remote app closing
     */
    private void playRemoteTaskListFadeIn(@NonNull AnimatorSet anim,
            @NonNull TaskItemView appTaskView) {
        long delay = REMOTE_TO_RECENTS_ITEM_FADE_START_DELAY;
        int childCount = mTaskRecyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ValueAnimator fadeAnim = ValueAnimator.ofFloat(0, 1.0f);
            fadeAnim.setDuration(REMOTE_TO_RECENTS_ITEM_FADE_DURATION).setInterpolator(OUT_SLOW_IN);
            fadeAnim.setStartDelay(delay);
            View view = mTaskRecyclerView.getChildAt(i);
            if (Objects.equals(view, appTaskView)) {
                // Only animate icon and text for the view with snapshot animating in
                final View icon = appTaskView.getIconView();
                final View label = appTaskView.getLabelView();

                icon.setAlpha(0.0f);
                label.setAlpha(0.0f);

                fadeAnim.addUpdateListener(alphaVal -> {
                    float val = alphaVal.getAnimatedFraction();

                    icon.setAlpha(val);
                    label.setAlpha(val);
                });
                fadeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        icon.setAlpha(1.0f);
                        label.setAlpha(1.0f);
                    }
                });
            } else {
                // Otherwise, fade in the entire view.
                view.setAlpha(0.0f);
                fadeAnim.addUpdateListener(alphaVal -> {
                    float val = alphaVal.getAnimatedFraction();
                    view.setAlpha(val);
                });
                fadeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setAlpha(1.0f);
                    }
                });
            }
            anim.play(fadeAnim);

            int itemType = mTaskRecyclerView.getChildViewHolder(view).getItemViewType();
            if (itemType == ITEM_TYPE_CLEAR_ALL) {
                // Don't add delay. Clear all should animate at same time as next view.
                continue;
            }
            delay += REMOTE_TO_RECENTS_ITEM_FADE_BETWEEN_DELAY;
        }
    }

    /**
     * Set view properties so that the view fits to the target rect.
     *
     * @param view view to set
     * @param origRect original rect that view was located
     * @param targetRect rect to set to
     */
    private void setViewToRect(View view, RectF origRect, RectF targetRect) {
        float dX = targetRect.left - origRect.left;
        float dY = targetRect.top - origRect.top;
        view.setTranslationX(dX);
        view.setTranslationY(dY);

        float scaleX = targetRect.width() / origRect.width();
        float scaleY = targetRect.height() / origRect.height();
        view.setPivotX(0);
        view.setPivotY(0);
        view.setScaleX(scaleX);
        view.setScaleY(scaleY);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets = insets;
        mTaskRecyclerView.setPadding(insets.left, insets.top, insets.right, insets.bottom);
        mTaskRecyclerView.invalidateItemDecorations();
        if (mInsets.top != 0) {
            updateStatusBarScrim();
        }
    }

    /**
     * Callback for when this view is ready for a remote animation from app to overview.
     */
    public interface onReadyForRemoteAnimCallback {

        void onReadyForRemoteAnim();
    }
}
