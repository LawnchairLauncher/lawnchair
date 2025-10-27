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
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.animation.Interpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;
import android.window.WindowOnBackInvokedDispatcher;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.app.animation.Interpolators;
import com.android.internal.jank.Cuj;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.SingleTask;
import com.android.quickstep.util.SplitTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.wm.shell.shared.TypefaceUtils;
import com.android.wm.shell.shared.TypefaceUtils.FontFamily;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * View that allows quick switching between recent tasks.
 *
 * Can be access via:
 * - keyboard alt-tab
 * - alt-shift-tab
 * - taskbar overflow button
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

    private boolean mSupportsScrollArrows = false;
    private ImageButton mStartScrollArrow;
    private ImageButton mEndScrollArrow;

    private int mTaskViewBorderWidth;
    private int mTaskViewRadius;
    private int mSpacing;
    private int mSmallSpacing;
    private int mOutlineRadius;
    private boolean mIsRtl;

    private int mOverviewTaskIndex = -1;
    private int mDesktopTaskIndex = -1;

    @Nullable
    private AnimatorSet mOpenAnimation;

    private boolean mIsBackCallbackRegistered = false;

    @Nullable
    private KeyboardQuickSwitchViewController.ViewCallbacks mViewCallbacks;

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
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mViewCallbacks != null) {
            mViewCallbacks.onViewDetchedFromWindow();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNoRecentItemsPane = findViewById(R.id.no_recent_items_pane);
        mScrollView = findViewById(R.id.scroll_view);
        mContent = findViewById(R.id.content);
        mStartScrollArrow = findViewById(R.id.scroll_button_start);
        mEndScrollArrow = findViewById(R.id.scroll_button_end);

        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        Resources resources = getResources();
        mSpacing = resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_view_spacing);
        mSmallSpacing = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_view_small_spacing);
        mOutlineRadius = resources.getDimensionPixelSize(R.dimen.keyboard_quick_switch_view_radius);
        mTaskViewBorderWidth = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_border_width);
        mTaskViewRadius = resources.getDimensionPixelSize(
                R.dimen.keyboard_quick_switch_task_view_radius);

        mIsRtl = Utilities.isRtl(resources);

        TypefaceUtils.setTypeface(
                mNoRecentItemsPane.findViewById(R.id.no_recent_items_text),
                FontFamily.GSF_LABEL_LARGE);
    }

    private void registerOnBackInvokedCallback() {
        OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();

        if (isOnBackInvokedCallbackEnabled(dispatcher)
                && !mIsBackCallbackRegistered) {
            dispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY, mViewCallbacks.onBackInvokedCallback);
            mIsBackCallbackRegistered = true;
        }
    }

    private void unregisterOnBackInvokedCallback() {
        OnBackInvokedDispatcher dispatcher = findOnBackInvokedDispatcher();

        if (isOnBackInvokedCallbackEnabled(dispatcher)
                && mIsBackCallbackRegistered) {
            dispatcher.unregisterOnBackInvokedCallback(
                    mViewCallbacks.onBackInvokedCallback);
            mIsBackCallbackRegistered = false;
        }
    }

    private boolean isOnBackInvokedCallbackEnabled(OnBackInvokedDispatcher dispatcher) {
        return dispatcher instanceof WindowOnBackInvokedDispatcher
                && ((WindowOnBackInvokedDispatcher) dispatcher).isOnBackInvokedCallbackEnabled()
                && mViewCallbacks != null;
    }

    private KeyboardQuickSwitchTaskView createAndAddTaskView(
            int index,
            boolean isFinalView,
            boolean useSmallStartSpacing,
            @LayoutRes int resId,
            @NonNull LayoutInflater layoutInflater,
            @Nullable View previousView) {
        KeyboardQuickSwitchTaskView taskView = (KeyboardQuickSwitchTaskView) layoutInflater.inflate(
                resId, mContent, false);
        taskView.setId(View.generateViewId());
        taskView.setOnClickListener(v -> mViewCallbacks.launchTaskAt(index));

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        // Create a left-to-right ordering of views (or right-to-left in RTL locales)
        if (previousView != null) {
            lp.startToEnd = previousView.getId();
        } else {
            lp.startToStart = PARENT_ID;
        }
        lp.topToTop = PARENT_ID;
        lp.bottomToBottom = PARENT_ID;
        // Add spacing between views
        lp.setMarginStart(useSmallStartSpacing ? mSmallSpacing : mSpacing);
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
            @NonNull KeyboardQuickSwitchViewController.ViewCallbacks viewCallbacks,
            boolean useDesktopTaskView) {
        mContent.removeAllViews();

        mViewCallbacks = viewCallbacks;
        Resources resources = context.getResources();
        Resources.Theme theme = context.getTheme();

        View previousTaskView = null;
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        int tasksToDisplay = groupTasks.size();
        for (int i = 0; i < tasksToDisplay; i++) {
            GroupTask groupTask = groupTasks.get(i);
            KeyboardQuickSwitchTaskView currentTaskView = createAndAddTaskView(
                    i,
                    /* isFinalView= */ i == tasksToDisplay - 1
                            && numHiddenTasks == 0 && !useDesktopTaskView,
                    /* useSmallStartSpacing= */ false,
                    mViewCallbacks.isAspectRatioSquare()
                            ? R.layout.keyboard_quick_switch_taskview_square
                            : R.layout.keyboard_quick_switch_taskview,
                    layoutInflater,
                    previousTaskView);

            Task task1;
            Task task2;
            if (groupTask instanceof SplitTask splitTask) {
                task1 = splitTask.getTopLeftTask();
                task2 = splitTask.getBottomRightTask();
            } else if (groupTask instanceof SingleTask singleTask) {
                task1 = singleTask.getTask();
                task2 = null;
            } else {
                continue;
            }

            currentTaskView.setPositionInformation(i, tasksToDisplay);
            currentTaskView.setThumbnailsForSplitTasks(
                    task1,
                    task2,
                    updateTasks ? mViewCallbacks::updateThumbnailInBackground : null,
                    updateTasks ? mViewCallbacks::updateIconInBackground : null,
                    groupTask instanceof SplitTask splitTask ? splitTask.getSplitBounds() : null);

            previousTaskView = currentTaskView;
        }
        if (numHiddenTasks > 0) {
            HashMap<String, Integer> args = new HashMap<>();
            args.put("count", numHiddenTasks);

            mOverviewTaskIndex = getTaskCount();
            View overviewButton = createAndAddTaskView(
                    mOverviewTaskIndex,
                    /* isFinalView= */ !useDesktopTaskView,
                    /* useSmallStartSpacing= */ false,
                    R.layout.keyboard_quick_switch_overview_taskview,
                    layoutInflater,
                    previousTaskView);

            overviewButton.<TextView>findViewById(R.id.large_text).setText(
                    String.format(Locale.getDefault(), "%d", numHiddenTasks));
            overviewButton.<TextView>findViewById(R.id.small_text).setText(new MessageFormat(
                    resources.getString(R.string.quick_switch_overflow),
                    Locale.getDefault()).format(args));

            previousTaskView = overviewButton;
        }
        if (useDesktopTaskView) {
            mDesktopTaskIndex = getTaskCount();
            View desktopButton = createAndAddTaskView(
                    mDesktopTaskIndex,
                    /* isFinalView= */ true,
                    /* useSmallStartSpacing= */ numHiddenTasks > 0,
                    R.layout.keyboard_quick_switch_desktop_taskview,
                    layoutInflater,
                    previousTaskView);

            desktopButton.<TextView>findViewById(R.id.small_text).setText(
                    resources.getString(R.string.quick_switch_desktop));
        }
        mDisplayingRecentTasks = !groupTasks.isEmpty() || useDesktopTaskView;

        getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        registerOnBackInvokedCallback();
                        animateOpen(currentFocusIndexOverride);

                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
    }


    void enableScrollArrowSupport() {
        if (mSupportsScrollArrows) {
            return;
        }
        mSupportsScrollArrows = true;

        if (mIsRtl) {
            mStartScrollArrow.setContentDescription(
                    getResources().getString(R.string.quick_switch_scroll_arrow_right));
            mEndScrollArrow.setContentDescription(
                    getResources().getString(R.string.quick_switch_scroll_arrow_left));
        }


        mStartScrollArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRtl) {
                    runScrollCommand(false, () -> {
                        mScrollView.smoothScrollBy(mScrollView.getWidth(), 0);
                    });
                } else {
                    runScrollCommand(false, () -> {
                        mScrollView.smoothScrollBy(-mScrollView.getWidth(), 0);
                    });
                }
            }
        });

        mEndScrollArrow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mIsRtl) {
                    runScrollCommand(false, () -> {
                        mScrollView.smoothScrollBy(-mScrollView.getWidth(), 0);
                    });
                } else {
                    runScrollCommand(false, () -> {
                        mScrollView.smoothScrollBy(mScrollView.getWidth(), 0);
                    });
                }
            }
        });

        // Add listeners to disable arrow buttons when the scroll view cannot be further scrolled in
        // the associated direction.
        mScrollView.setOnScrollChangeListener(new OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX,
                    int oldScrollY) {
                updateArrowButtonsEnabledState();
            }
        });

        // Update scroll view outline to clip its contents with rounded corners.
        mScrollView.setClipToOutline(true);
        mScrollView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int spacingWithoutBorder = mSpacing - mTaskViewBorderWidth;
                outline.setRoundRect(spacingWithoutBorder,
                        spacingWithoutBorder, view.getWidth() - spacingWithoutBorder,
                        view.getHeight() - spacingWithoutBorder,
                        mTaskViewRadius);
            }
        });
    }

    private void updateArrowButtonsEnabledState() {
        if (!mDisplayingRecentTasks) {
            return;
        }

        int scrollX = mScrollView.getScrollX();
        if (mIsRtl) {
            mEndScrollArrow.setEnabled(scrollX > 0);
            mStartScrollArrow.setEnabled(scrollX < mContent.getWidth() - mScrollView.getWidth());
        } else {
            mStartScrollArrow.setEnabled(scrollX > 0);
            mEndScrollArrow.setEnabled(scrollX < mContent.getWidth() - mScrollView.getWidth());
        }
    }

    int getOverviewTaskIndex() {
        return mOverviewTaskIndex;
    }

    int getDesktopTaskIndex() {
        return mDesktopTaskIndex;
    }

    void resetViewCallbacks() {
        // Unregister the back invoked callback after the view is closed and before the
        // mViewCallbacks is reset.
        unregisterOnBackInvokedCallback();
        mViewCallbacks = null;
    }

    private void animateDisplayedContentForClose(View view, AnimatorSet animator) {
        Animator translationYAnimation = ObjectAnimator.ofFloat(
                view,
                TRANSLATION_Y,
                0, -Utilities.dpToPx(CONTENT_START_TRANSLATION_Y_DP));
        translationYAnimation.setDuration(CONTENT_TRANSLATION_Y_ANIMATION_DURATION_MS);
        translationYAnimation.setInterpolator(CLOSE_TRANSLATION_Y_INTERPOLATOR);
        animator.play(translationYAnimation);

        Animator contentAlphaAnimation = ObjectAnimator.ofFloat(view, ALPHA, 1f, 0f);
        contentAlphaAnimation.setDuration(CONTENT_ALPHA_ANIMATION_DURATION_MS);
        animator.play(contentAlphaAnimation);

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
        animateDisplayedContentForClose(displayedContent, closeAnimation);
        if (mSupportsScrollArrows) {
            animateDisplayedContentForClose(mStartScrollArrow, closeAnimation);
            animateDisplayedContentForClose(mEndScrollArrow, closeAnimation);
        }

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

    private void animateDisplayedContentForOpen(View view, AnimatorSet animator) {
        Animator translationXAnimation = ObjectAnimator.ofFloat(
                view,
                TRANSLATION_X,
                -Utilities.dpToPx(CONTENT_START_TRANSLATION_X_DP), 0);
        translationXAnimation.setDuration(CONTENT_TRANSLATION_X_ANIMATION_DURATION_MS);
        translationXAnimation.setInterpolator(OPEN_TRANSLATION_X_INTERPOLATOR);
        animator.play(translationXAnimation);

        Animator translationYAnimation = ObjectAnimator.ofFloat(
                view,
                TRANSLATION_Y,
                -Utilities.dpToPx(CONTENT_START_TRANSLATION_Y_DP), 0);
        translationYAnimation.setDuration(CONTENT_TRANSLATION_Y_ANIMATION_DURATION_MS);
        translationYAnimation.setInterpolator(OPEN_TRANSLATION_Y_INTERPOLATOR);
        animator.play(translationYAnimation);

        view.setAlpha(0.0f);
        Animator contentAlphaAnimation = ObjectAnimator.ofFloat(view, ALPHA, 0f,
                1f);
        contentAlphaAnimation.setStartDelay(CONTENT_ALPHA_ANIMATION_START_DELAY_MS);
        contentAlphaAnimation.setDuration(CONTENT_ALPHA_ANIMATION_DURATION_MS);
        animator.play(contentAlphaAnimation);
    }

    protected void animateOpen(int currentFocusIndexOverride) {
        if (mOpenAnimation != null) {
            // Restart animation since currentFocusIndexOverride can change the initial scroll.
            mOpenAnimation.cancel();
        }

        // Reset the alpha for the case where the KQS view is opened before.
        setAlpha(0);
        mScrollView.setAlpha(0);
        mNoRecentItemsPane.setAlpha(0);

        mOpenAnimation = new AnimatorSet();

        Animator outlineAnimation = mOutlineAnimationProgress.animateToValue(1f);
        outlineAnimation.setDuration(OUTLINE_ANIMATION_DURATION_MS);
        mOpenAnimation.play(outlineAnimation);

        Animator alphaAnimation = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f);
        alphaAnimation.setDuration(ALPHA_ANIMATION_DURATION_MS);
        mOpenAnimation.play(alphaAnimation);

        View displayedContent = mDisplayingRecentTasks ? mScrollView : mNoRecentItemsPane;
        animateDisplayedContentForOpen(displayedContent, mOpenAnimation);
        if (mSupportsScrollArrows) {
            animateDisplayedContentForOpen(mStartScrollArrow, mOpenAnimation);
            animateDisplayedContentForOpen(mEndScrollArrow, mOpenAnimation);
        }


        ViewOutlineProvider outlineProvider = getOutlineProvider();
        int defaultFocusedTaskIndex = Math.min(
                getTaskCount() - 1,
                currentFocusIndexOverride == -1 ? 1 : currentFocusIndexOverride);
        mOpenAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                InteractionJankMonitorWrapper.begin(
                        KeyboardQuickSwitchView.this, Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_OPEN);
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

                if (mSupportsScrollArrows) {
                    mScrollView.getViewTreeObserver().addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    if (mScrollView.getWidth() == 0) {
                                        return;
                                    }

                                    if (mContent.getWidth() > mScrollView.getWidth()) {
                                        mStartScrollArrow.setVisibility(VISIBLE);
                                        mEndScrollArrow.setVisibility(VISIBLE);
                                        updateArrowButtonsEnabledState();
                                    }
                                    mScrollView.getViewTreeObserver().removeOnGlobalLayoutListener(
                                            this);
                                }
                            });
                }

                animateFocusMove(-1, defaultFocusedTaskIndex);
                displayedContent.setVisibility(VISIBLE);
                setVisibility(VISIBLE);
                requestFocus();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_OPEN);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setClipToPadding(true);
                setOutlineProvider(outlineProvider);
                invalidateOutline();
                mOpenAnimation = null;
                InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_KEYBOARD_QUICK_SWITCH_OPEN);

                View focusedTask = getTaskAt(defaultFocusedTaskIndex);
                if (focusedTask != null) {
                    focusedTask.requestAccessibilityFocus();
                }
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
        return !mDisplayingRecentTasks || index < 0 || index >= getTaskCount()
                ? null : (KeyboardQuickSwitchTaskView) mContent.getChildAt(index);
    }

    public int getTaskCount() {
        return mContent.getChildCount();
    }
}
