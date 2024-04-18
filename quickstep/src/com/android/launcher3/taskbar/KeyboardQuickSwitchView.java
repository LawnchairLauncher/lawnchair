/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;

import static com.android.launcher3.taskbar.KeyboardQuickSwitchController.MAX_TASKS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Outline;
import android.graphics.Rect;
import android.icu.text.MessageFormat;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.util.DesktopTask;
import com.android.quickstep.util.GroupTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * View that allows quick switching between recent tasks through keyboard alt-tab and alt-shift-tab
 * commands.
 */
public class KeyboardQuickSwitchView extends ConstraintLayout {

    private static final long OUTLINE_ANIMATION_DURATION_MS = 333;
    private static final float OUTLINE_START_HEIGHT_FACTOR = 0.45f;
    private static final float OUTLINE_START_RADIUS_FACTOR = 0.25f;
    private static final Interpolator OPEN_OUTLINE_INTERPOLATOR =
            Interpolators.EMPHASIZED_DECELERATE;
    private static final Interpolator CLOSE_OUTLINE_INTERPOLATOR =
            Interpolators.EMPHASIZED_ACCELERATE;

    private static final long ALPHA_ANIMATION_DURATION_MS = 83;
    private static final long ALPHA_ANIMATION_START_DELAY_MS = 67;

    private static final long CONTENT_TRANSLATION_X_ANIMATION_DURATION_MS = 500;
    private static final long CONTENT_TRANSLATION_Y_ANIMATION_DURATION_MS = 333;
    private static final float CONTENT_START_TRANSLATION_X_DP = 32;
    private static final float CONTENT_START_TRANSLATION_Y_DP = 40;
    private static final Interpolator OPEN_TRANSLATION_X_INTERPOLATOR = Interpolators.EMPHASIZED;
    private static final Interpolator OPEN_TRANSLATION_Y_INTERPOLATOR =
            Interpolators.EMPHASIZED_DECELERATE;
    private static final Interpolator CLOSE_TRANSLATION_Y_INTERPOLATOR =
            Interpolators.EMPHASIZED_ACCELERATE;

    private static final long CONTENT_ALPHA_ANIMATION_DURATION_MS = 83;
    private static final long CONTENT_ALPHA_ANIMATION_START_DELAY_MS = 83;

    private final AnimatedFloat mOutlineAnimationProgress = new AnimatedFloat(
            this::invalidateOutline);

    private boolean mDisplayingRecentTasks;
    private View mNoRecentItemsPane;
    private HorizontalScrollView mScrollView;
    private ConstraintLayout mContent;

    private int mTaskViewWidth;
    private int mTaskViewHeight;
    private int mSpacing;
    private int mOutlineRadius;
    private boolean mIsRtl;

    @Nullable private AnimatorSet mOpenAnimation;

    @Nullable private KeyboardQuickSwitchViewController.ViewCallbacks mViewCallbacks;

    public KeyboardQuickSwitchView(@NonNull Context context) {
        this(context, null);
    }

    public KeyboardQuickSwitchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyboardQuickSwitchView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public KeyboardQuickSwitchView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNoRecentItemsPane = findViewById(R.id.no_recent_items_pane);
        mScrollView = findViewById(R.id.scroll_view);
        mContent = findViewById(R.id.content);

        Resources resources = getResources();
        mTaskViewWidth = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_taskview_width);
        mTaskViewHeight = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_taskview_height);
        mSpacing = resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_view_spacing);
        mOutlineRadius = resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_view_radius);
        mIsRtl = Utilities.isRtl(resources);
    }

    private KeyboardQuickSwitchTaskView createAndAddTaskView(
            int index,
            boolean isFinalView,
            @LayoutRes int resId,
            @NonNull LayoutInflater layoutInflater,
            @Nullable View previousView) {
        KeyboardQuickSwitchTaskView taskView = (KeyboardQuickSwitchTaskView) layoutInflater.inflate(
                resId, mContent, false);
        taskView.setId(View.generateViewId());
        taskView.setOnClickListener(v -> mViewCallbacks.launchTaskAt(index));

        LayoutParams lp = new LayoutParams(mTaskViewWidth, mTaskViewHeight);
        // Create a left-to-right ordering of views (or right-to-left in RTL locales)
        if (previousView != null) {
            lp.startToEnd = previousView.getId();
        } else {
            lp.startToStart = PARENT_ID;
        }
        lp.topToTop = PARENT_ID;
        lp.bottomToBottom = PARENT_ID;
        // Add spacing between views
        lp.setMarginStart(mSpacing);
        if (isFinalView) {
            // Add spacing to the end of the final view so that scrolling ends with some padding.
            lp.endToEnd = PARENT_ID;
            lp.setMarginEnd(mSpacing);
            lp.horizontalBias = 1f;
        }

        mContent.addView(taskView, lp);

        return taskView;
    }

    protected void applyLoadPlan(
            @NonNull Context context,
            @NonNull List<GroupTask> groupTasks,
            int numHiddenTasks,
            boolean updateTasks,
            int currentFocusIndexOverride,
            @NonNull KeyboardQuickSwitchViewController.ViewCallbacks viewCallbacks) {
        mViewCallbacks = viewCallbacks;
        Resources resources = context.getResources();
        Resources.Theme theme = context.getTheme();

        View previousTaskView = null;
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        int tasksToDisplay = Math.min(MAX_TASKS, groupTasks.size());
        for (int i = 0; i < tasksToDisplay; i++) {
            GroupTask groupTask = groupTasks.get(i);
            KeyboardQuickSwitchTaskView currentTaskView = createAndAddTaskView(
                    i,
                    /* isFinalView= */ i == tasksToDisplay - 1 && numHiddenTasks == 0,
                    groupTask instanceof DesktopTask
                            ? R.layout.keyboard_quick_switch_textonly_taskview
                            : R.layout.keyboard_quick_switch_taskview,
                    layoutInflater,
                    previousTaskView);

            if (groupTask instanceof DesktopTask desktopTask) {
                HashMap<String, Integer> args = new HashMap<>();
                args.put("count", desktopTask.tasks.size());

                currentTaskView.<ImageView>findViewById(R.id.icon).setImageDrawable(
                        ResourcesCompat.getDrawable(resources, R.drawable.ic_desktop, theme));
                currentTaskView.<TextView>findViewById(R.id.text).setText(new MessageFormat(
                        resources.getString(R.string.quick_switch_desktop),
                        Locale.getDefault()).format(args));
            } else {
                currentTaskView.setThumbnails(
                        groupTask.task1,
                        groupTask.task2,
                        updateTasks ? mViewCallbacks::updateThumbnailInBackground : null,
                        updateTasks ? mViewCallbacks::updateIconInBackground : null);
            }
            previousTaskView = currentTaskView;
        }

        if (numHiddenTasks > 0) {
            HashMap<String, Integer> args = new HashMap<>();
            args.put("count", numHiddenTasks);

            View overviewButton = createAndAddTaskView(
                    MAX_TASKS,
                    /* isFinalView= */ true,
                    R.layout.keyboard_quick_switch_textonly_taskview,
                    layoutInflater,
                    previousTaskView);

            overviewButton.<ImageView>findViewById(R.id.icon).setImageDrawable(
                    ResourcesCompat.getDrawable(resources, R.drawable.view_carousel, theme));
            overviewButton.<TextView>findViewById(R.id.text).setText(new MessageFormat(
                    resources.getString(R.string.quick_switch_overflow),
                    Locale.getDefault()).format(args));
        }
        mDisplayingRecentTasks = !groupTasks.isEmpty();

        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        animateOpen(currentFocusIndexOverride);

                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    protected Animator getCloseAnimation() {
        AnimatorSet closeAnimation = new AnimatorSet();

        Animator outlineAnimation = mOutlineAnimationProgress.animateToValue(0f);
        outlineAnimation.setDuration(OUTLINE_ANIMATION_DURATION_MS);
        outlineAnimation.setInterpolator(CLOSE_OUTLINE_INTERPOLATOR);
        closeAnimation.play(outlineAnimation);

        Animator alphaAnimation = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f);
        alphaAnimation.setStartDelay(ALPHA_ANIMATION_START_DELAY_MS);
        alphaAnimation.setDuration(ALPHA_ANIMATION_DURATION_MS);
        closeAnimation.play(alphaAnimation);

        View displayedContent = mDisplayingRecentTasks ? mScrollView : mNoRecentItemsPane;
        Animator translationYAnimation = ObjectAnimator.ofFloat(
                displayedContent,
                TRANSLATION_Y,
                0, -Utilities.dpToPx(CONTENT_START_TRANSLATION_Y_DP));
        translationYAnimation.setDuration(CONTENT_TRANSLATION_Y_ANIMATION_DURATION_MS);
        translationYAnimation.setInterpolator(CLOSE_TRANSLATION_Y_INTERPOLATOR);
        closeAnimation.play(translationYAnimation);

        Animator contentAlphaAnimation = ObjectAnimator.ofFloat(displayedContent, ALPHA, 1f, 0f);
        contentAlphaAnimation.setDuration(CONTENT_ALPHA_ANIMATION_DURATION_MS);
        closeAnimation.play(contentAlphaAnimation);

        closeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (mOpenAnimation != null) {
                    mOpenAnimation.cancel();
                }
            }
        });

        return closeAnimation;
    }

    private void animateOpen(int currentFocusIndexOverride) {
        if (mOpenAnimation != null) {
            // Restart animation since currentFocusIndexOverride can change the initial scroll.
            mOpenAnimation.cancel();
        }
        mOpenAnimation = new AnimatorSet();

        Animator outlineAnimation = mOutlineAnimationProgress.animateToValue(1f);
        outlineAnimation.setDuration(OUTLINE_ANIMATION_DURATION_MS);
        mOpenAnimation.play(outlineAnimation);

        Animator alphaAnimation = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f);
        alphaAnimation.setDuration(ALPHA_ANIMATION_DURATION_MS);
        mOpenAnimation.play(alphaAnimation);

        View displayedContent = mDisplayingRecentTasks ? mScrollView : mNoRecentItemsPane;
        Animator translationXAnimation = ObjectAnimator.ofFloat(
                displayedContent,
                TRANSLATION_X,
                -Utilities.dpToPx(CONTENT_START_TRANSLATION_X_DP), 0);
        translationXAnimation.setDuration(CONTENT_TRANSLATION_X_ANIMATION_DURATION_MS);
        translationXAnimation.setInterpolator(OPEN_TRANSLATION_X_INTERPOLATOR);
        mOpenAnimation.play(translationXAnimation);

        Animator translationYAnimation = ObjectAnimator.ofFloat(
                displayedContent,
                TRANSLATION_Y,
                -Utilities.dpToPx(CONTENT_START_TRANSLATION_Y_DP), 0);
        translationYAnimation.setDuration(CONTENT_TRANSLATION_Y_ANIMATION_DURATION_MS);
        translationYAnimation.setInterpolator(OPEN_TRANSLATION_Y_INTERPOLATOR);
        mOpenAnimation.play(translationYAnimation);

        Animator contentAlphaAnimation = ObjectAnimator.ofFloat(displayedContent, ALPHA, 0f, 1f);
        contentAlphaAnimation.setStartDelay(CONTENT_ALPHA_ANIMATION_START_DELAY_MS);
        contentAlphaAnimation.setDuration(CONTENT_ALPHA_ANIMATION_DURATION_MS);
        mOpenAnimation.play(contentAlphaAnimation);

        ViewOutlineProvider outlineProvider = getOutlineProvider();
        mOpenAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                setClipToPadding(false);
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(
                                /* rect= */ new Rect(
                                        /* left= */ 0,
                                        /* top= */ 0,
                                        /* right= */ getWidth(),
                                        /* bottom= */
                                        (int) (getHeight() * Utilities.mapBoundToRange(
                                                mOutlineAnimationProgress.value,
                                                /* lowerBound= */ 0f,
                                                /* upperBound= */ 1f,
                                                /* toMin= */ OUTLINE_START_HEIGHT_FACTOR,
                                                /* toMax= */ 1f,
                                                OPEN_OUTLINE_INTERPOLATOR))),
                                /* radius= */ mOutlineRadius * Utilities.mapBoundToRange(
                                        mOutlineAnimationProgress.value,
                                        /* lowerBound= */ 0f,
                                        /* upperBound= */ 1f,
                                        /* toMin= */ OUTLINE_START_RADIUS_FACTOR,
                                        /* toMax= */ 1f,
                                        OPEN_OUTLINE_INTERPOLATOR));
                    }
                });
                animateFocusMove(-1, Math.min(
                        mContent.getChildCount() - 1,
                        currentFocusIndexOverride == -1 ? 1 : currentFocusIndexOverride));
                displayedContent.setVisibility(VISIBLE);
                setVisibility(VISIBLE);
                requestFocus();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setClipToPadding(true);
                setOutlineProvider(outlineProvider);
                invalidateOutline();
                mOpenAnimation = null;
            }
        });

        mOpenAnimation.start();
    }

    protected void animateFocusMove(int fromIndex, int toIndex) {
        if (!mDisplayingRecentTasks) {
            return;
        }
        KeyboardQuickSwitchTaskView focusedTask = getTaskAt(toIndex);
        if (focusedTask == null) {
            return;
        }
        AnimatorSet focusAnimation = new AnimatorSet();
        focusAnimation.play(focusedTask.getFocusAnimator(true));

        KeyboardQuickSwitchTaskView previouslyFocusedTask = getTaskAt(fromIndex);
        if (previouslyFocusedTask != null) {
            focusAnimation.play(previouslyFocusedTask.getFocusAnimator(false));
        }

        focusAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                focusedTask.requestAccessibilityFocus();
                if (fromIndex == -1) {
                    int firstVisibleTaskIndex = toIndex == 0
                            ? toIndex
                            : getTaskAt(toIndex - 1) == null
                                    ? toIndex : toIndex - 1;
                    // Scroll so that the previous task view is truncated as a visual hint that
                    // there are more tasks
                    initializeScroll(
                            firstVisibleTaskIndex,
                            /* shouldTruncateTarget= */ firstVisibleTaskIndex != 0
                                    && firstVisibleTaskIndex != toIndex);
                } else if (toIndex > fromIndex || toIndex == 0) {
                    // Scrolling to next task view
                    if (mIsRtl) {
                        scrollLeftTo(focusedTask);
                    } else {
                        scrollRightTo(focusedTask);
                    }
                } else {
                    // Scrolling to previous task view
                    if (mIsRtl) {
                        scrollRightTo(focusedTask);
                    } else {
                        scrollLeftTo(focusedTask);
                    }
                }
                if (mViewCallbacks != null) {
                    mViewCallbacks.updateCurrentFocusIndex(toIndex);
                }
            }
        });

        focusAnimation.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        TestLogging.recordKeyEvent(
                TestProtocol.SEQUENCE_MAIN, "KeyboardQuickSwitchView key event", event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return (mViewCallbacks != null
                && mViewCallbacks.onKeyUp(keyCode, event, mIsRtl, mDisplayingRecentTasks))
                || super.onKeyUp(keyCode, event);
    }

    private void initializeScroll(int index, boolean shouldTruncateTarget) {
        if (!mDisplayingRecentTasks) {
            return;
        }
        View task = getTaskAt(index);
        if (task == null) {
            return;
        }
        if (mIsRtl) {
            scrollLeftTo(
                    task,
                    shouldTruncateTarget,
                    /* smoothScroll= */ false,
                    /* waitForLayout= */ true);
        } else {
            scrollRightTo(
                    task,
                    shouldTruncateTarget,
                    /* smoothScroll= */ false,
                    /* waitForLayout= */ true);
        }
    }

    private void scrollRightTo(@NonNull View targetTask) {
        scrollRightTo(
                targetTask,
                /* shouldTruncateTarget= */ false,
                /* smoothScroll= */ true,
                /* waitForLayout= */ false);
    }

    private void scrollRightTo(
            @NonNull View targetTask,
            boolean shouldTruncateTarget,
            boolean smoothScroll,
            boolean waitForLayout) {
        if (!mDisplayingRecentTasks) {
            return;
        }
        if (smoothScroll && !shouldScroll(targetTask, shouldTruncateTarget)) {
            return;
        }
        runScrollCommand(waitForLayout, () -> {
            int scrollTo = targetTask.getLeft() - mSpacing
                    + (shouldTruncateTarget ? targetTask.getWidth() / 2 : 0);
            // Scroll so that the focused task is to the left of the list
            if (smoothScroll) {
                mScrollView.smoothScrollTo(scrollTo, 0);
            } else {
                mScrollView.scrollTo(scrollTo, 0);
            }
        });
    }

    private void scrollLeftTo(@NonNull View targetTask) {
        scrollLeftTo(
                targetTask,
                /* shouldTruncateTarget= */ false,
                /* smoothScroll= */ true,
                /* waitForLayout= */ false);
    }

    private void scrollLeftTo(
            @NonNull View targetTask,
            boolean shouldTruncateTarget,
            boolean smoothScroll,
            boolean waitForLayout) {
        if (!mDisplayingRecentTasks) {
            return;
        }
        if (smoothScroll && !shouldScroll(targetTask, shouldTruncateTarget)) {
            return;
        }
        runScrollCommand(waitForLayout, () -> {
            int scrollTo = targetTask.getRight() + mSpacing - mScrollView.getWidth()
                    - (shouldTruncateTarget ? targetTask.getWidth() / 2 : 0);
            // Scroll so that the focused task is to the right of the list
            if (smoothScroll) {
                mScrollView.smoothScrollTo(scrollTo, 0);
            } else {
                mScrollView.scrollTo(scrollTo, 0);
            }
        });
    }

    private boolean shouldScroll(@NonNull View targetTask, boolean shouldTruncateTarget) {
        boolean isTargetTruncated =
                targetTask.getRight() + mSpacing > mScrollView.getScrollX() + mScrollView.getWidth()
                        || Math.max(0, targetTask.getLeft() - mSpacing) < mScrollView.getScrollX();

        return isTargetTruncated && !shouldTruncateTarget;
    }

    private void runScrollCommand(boolean waitForLayout, @NonNull Runnable scrollCommand) {
        if (!waitForLayout) {
            scrollCommand.run();
            return;
        }
        mScrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        scrollCommand.run();
                        mScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }

    @Nullable
    protected KeyboardQuickSwitchTaskView getTaskAt(int index) {
        return !mDisplayingRecentTasks || index < 0 || index >= mContent.getChildCount()
                ? null : (KeyboardQuickSwitchTaskView) mContent.getChildAt(index);
    }
}
