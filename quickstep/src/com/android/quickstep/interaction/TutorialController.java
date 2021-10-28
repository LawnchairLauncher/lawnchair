/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.interaction;

import static android.view.View.GONE;
import static android.view.View.NO_ID;
import static android.view.View.inflate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.ColorRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.views.ClipIconView;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureAttemptCallback;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureAttemptCallback;

import java.util.ArrayList;

abstract class TutorialController implements BackGestureAttemptCallback,
        NavBarGestureAttemptCallback {

    private static final String TAG = "TutorialController";

    private static final float FINGER_DOT_VISIBLE_ALPHA = 0.6f;
    private static final float FINGER_DOT_SMALL_SCALE = 0.7f;
    private static final int FINGER_DOT_ANIMATION_DURATION_MILLIS = 500;

    private static final String PIXEL_TIPS_APP_PACKAGE_NAME = "com.google.android.apps.tips";
    private static final CharSequence DEFAULT_PIXEL_TIPS_APP_NAME = "Pixel Tips";

    private static final int FEEDBACK_ANIMATION_MS = 133;
    private static final int RIPPLE_VISIBLE_MS = 300;
    private static final int GESTURE_ANIMATION_DELAY_MS = 1500;
    private static final int ADVANCE_TUTORIAL_TIMEOUT_MS = 4000;
    private static final long GESTURE_ANIMATION_PAUSE_DURATION_MILLIS = 1000;

    final TutorialFragment mTutorialFragment;
    TutorialType mTutorialType;
    final Context mContext;

    final TextView mCloseButton;
    final ViewGroup mFeedbackView;
    final TextView mFeedbackTitleView;
    final ImageView mEdgeGestureVideoView;
    final RelativeLayout mFakeLauncherView;
    final FrameLayout mFakeHotseatView;
    @Nullable View mHotseatIconView;
    final ClipIconView mFakeIconView;
    final FrameLayout mFakeTaskView;
    final AnimatedTaskbarView mFakeTaskbarView;
    final AnimatedTaskView mFakePreviousTaskView;
    final View mRippleView;
    final RippleDrawable mRippleDrawable;
    final Button mActionButton;
    final TutorialStepIndicator mTutorialStepView;
    final ImageView mFingerDotView;
    private final AlertDialog mSkipTutorialDialog;

    private boolean mGestureCompleted = false;

    // These runnables  should be used when posting callbacks to their views and cleared from their
    // views before posting new callbacks.
    private final Runnable mTitleViewCallback;
    @Nullable private Runnable mFeedbackViewCallback;
    @Nullable private Runnable mFakeTaskViewCallback;
    @Nullable private Runnable mFakeTaskbarViewCallback;
    private final Runnable mShowFeedbackRunnable;

    TutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        mTutorialFragment = tutorialFragment;
        mTutorialType = tutorialType;
        mContext = mTutorialFragment.getContext();

        RootSandboxLayout rootView = tutorialFragment.getRootView();
        mCloseButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mCloseButton.setOnClickListener(button -> showSkipTutorialDialog());
        mFeedbackView = rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mFeedbackTitleView = mFeedbackView.findViewById(
                R.id.gesture_tutorial_fragment_feedback_title);
        mEdgeGestureVideoView = rootView.findViewById(R.id.gesture_tutorial_edge_gesture_video);
        mFakeLauncherView = rootView.findViewById(R.id.gesture_tutorial_fake_launcher_view);
        mFakeHotseatView = rootView.findViewById(R.id.gesture_tutorial_fake_hotseat_view);
        mFakeIconView = rootView.findViewById(R.id.gesture_tutorial_fake_icon_view);
        mFakeTaskView = rootView.findViewById(R.id.gesture_tutorial_fake_task_view);
        mFakeTaskbarView = rootView.findViewById(R.id.gesture_tutorial_fake_taskbar_view);
        mFakePreviousTaskView =
                rootView.findViewById(R.id.gesture_tutorial_fake_previous_task_view);
        mRippleView = rootView.findViewById(R.id.gesture_tutorial_ripple_view);
        mRippleDrawable = (RippleDrawable) mRippleView.getBackground();
        mActionButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);
        mTutorialStepView =
                rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_tutorial_step);
        mFingerDotView = rootView.findViewById(R.id.gesture_tutorial_finger_dot);
        mSkipTutorialDialog = createSkipTutorialDialog();

        mTitleViewCallback = () -> mFeedbackTitleView.sendAccessibilityEvent(
                AccessibilityEvent.TYPE_VIEW_FOCUSED);
        mShowFeedbackRunnable = () -> {
            mFeedbackView.setAlpha(0f);
            mFeedbackView.setScaleX(0.95f);
            mFeedbackView.setScaleY(0.95f);
            mFeedbackView.setVisibility(View.VISIBLE);
            mFeedbackView.animate()
                    .setDuration(FEEDBACK_ANIMATION_MS)
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .withEndAction(() -> {
                        if (mGestureCompleted && !mTutorialFragment.isAtFinalStep()) {
                            if (mFeedbackViewCallback != null) {
                                mFeedbackView.removeCallbacks(mFeedbackViewCallback);
                            }
                            mFeedbackViewCallback = mTutorialFragment::continueTutorial;
                            mFeedbackView.postDelayed(mFeedbackViewCallback,
                                    ADVANCE_TUTORIAL_TIMEOUT_MS);
                        }
                    })
                    .start();
            mFeedbackTitleView.postDelayed(mTitleViewCallback, FEEDBACK_ANIMATION_MS);
        };
    }

    private void showSkipTutorialDialog() {
        if (mSkipTutorialDialog != null) {
            mSkipTutorialDialog.show();
        }
    }

    public int getHotseatIconTop() {
        return mHotseatIconView == null
                ? 0 : mFakeHotseatView.getTop() + mHotseatIconView.getTop();
    }

    public int getHotseatIconLeft() {
        return mHotseatIconView == null
                ? 0 : mFakeHotseatView.getLeft() + mHotseatIconView.getLeft();
    }

    void setTutorialType(TutorialType tutorialType) {
        mTutorialType = tutorialType;
    }

    @LayoutRes
    protected int getMockHotseatResId() {
        return mTutorialFragment.isLargeScreen()
                ? R.layout.gesture_tutorial_foldable_mock_hotseat
                : R.layout.gesture_tutorial_mock_hotseat;
    }

    @LayoutRes
    protected int getMockAppTaskLayoutResId() {
        return View.NO_ID;
    }

    @ColorRes
    protected int getMockPreviousAppTaskThumbnailColorResId() {
        return R.color.gesture_tutorial_fake_previous_task_view_color;
    }

    @DrawableRes
    public int getMockAppIconResId() {
        return R.drawable.default_sandbox_app_icon;
    }

    @DrawableRes
    public int getMockWallpaperResId() {
        return R.drawable.default_sandbox_wallpaper;
    }

    void fadeTaskViewAndRun(Runnable r) {
        mFakeTaskView.animate().alpha(0).setListener(AnimatorListeners.forSuccessCallback(r));
    }

    @StringRes
    public Integer getIntroductionTitle() {
        return null;
    }

    @StringRes
    public Integer getIntroductionSubtitle() {
        return null;
    }

    void showFeedback() {
        if (mGestureCompleted) {
            mFeedbackView.setTranslationY(0);
            return;
        }
        Animator gestureAnimation = mTutorialFragment.getGestureAnimation();
        AnimatedVectorDrawable edgeAnimation = mTutorialFragment.getEdgeAnimation();
        if (gestureAnimation != null && edgeAnimation != null) {
            playFeedbackAnimation(gestureAnimation, edgeAnimation, mShowFeedbackRunnable, true);
        }
    }

    /**
     * Show feedback reflecting a failed gesture attempt.
     *
     * @param subtitleResId Resource of the text to display.
     **/
    void showFeedback(int subtitleResId) {
        showFeedback(subtitleResId, false);
    }

    /**
     * Show feedback reflecting the result of a gesture attempt.
     *
     * @param isGestureSuccessful Whether the tutorial feedback's action button should be shown.
     **/
    void showFeedback(int subtitleResId, boolean isGestureSuccessful) {
        showFeedback(
                isGestureSuccessful
                        ? R.string.gesture_tutorial_nice : R.string.gesture_tutorial_try_again,
                subtitleResId,
                isGestureSuccessful,
                false);
    }

    void showFeedback(
            int titleResId,
            int subtitleResId,
            boolean isGestureSuccessful,
            boolean useGestureAnimationDelay) {
        mFeedbackTitleView.removeCallbacks(mTitleViewCallback);
        if (mFeedbackViewCallback != null) {
            mFeedbackView.removeCallbacks(mFeedbackViewCallback);
            mFeedbackViewCallback = null;
        }

        mFeedbackTitleView.setText(titleResId);
        TextView subtitle =
                mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_subtitle);
        subtitle.setText(subtitleResId);
        if (isGestureSuccessful) {
            hideCloseButton();
            if (mTutorialFragment.isAtFinalStep()) {
                showActionButton();
            }

            if (mFakeTaskViewCallback != null) {
                mFakeTaskView.removeCallbacks(mFakeTaskViewCallback);
                mFakeTaskViewCallback = null;
            }
        }
        mGestureCompleted = isGestureSuccessful;

        Animator gestureAnimation = mTutorialFragment.getGestureAnimation();
        AnimatedVectorDrawable edgeAnimation = mTutorialFragment.getEdgeAnimation();
        if (!isGestureSuccessful && gestureAnimation != null && edgeAnimation != null) {
            playFeedbackAnimation(
                    gestureAnimation,
                    edgeAnimation,
                    mShowFeedbackRunnable,
                    useGestureAnimationDelay);
            return;
        } else {
            mTutorialFragment.releaseFeedbackAnimation();
        }
        mFeedbackViewCallback = mShowFeedbackRunnable;

        mFeedbackView.post(mFeedbackViewCallback);
    }

    public boolean isGestureCompleted() {
        return mGestureCompleted;
    }

    void hideFeedback() {
        cancelQueuedGestureAnimation();
        mFeedbackView.clearAnimation();
        mFeedbackView.setVisibility(View.INVISIBLE);
    }

    void cancelQueuedGestureAnimation() {
        if (mFeedbackViewCallback != null) {
            mFeedbackView.removeCallbacks(mFeedbackViewCallback);
            mFeedbackViewCallback = null;
        }
        if (mFakeTaskViewCallback != null) {
            mFakeTaskView.removeCallbacks(mFakeTaskViewCallback);
            mFakeTaskViewCallback = null;
        }
        if (mFakeTaskbarViewCallback != null) {
            mFakeTaskbarView.removeCallbacks(mFakeTaskbarViewCallback);
            mFakeTaskbarViewCallback = null;
        }
        mFeedbackTitleView.removeCallbacks(mTitleViewCallback);
    }

    private void playFeedbackAnimation(
            @NonNull Animator gestureAnimation,
            @NonNull AnimatedVectorDrawable edgeAnimation,
            @NonNull Runnable onStartRunnable,
            boolean useGestureAnimationDelay) {

        if (gestureAnimation.isRunning()) {
            gestureAnimation.cancel();
        }
        if (edgeAnimation.isRunning()) {
            edgeAnimation.reset();
        }
        gestureAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);

                mEdgeGestureVideoView.setVisibility(GONE);
                if (edgeAnimation.isRunning()) {
                    edgeAnimation.stop();
                }

                if (!useGestureAnimationDelay) {
                    onStartRunnable.run();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);

                mEdgeGestureVideoView.setVisibility(View.VISIBLE);
                edgeAnimation.start();

                gestureAnimation.removeListener(this);
            }
        });

        cancelQueuedGestureAnimation();
        if (useGestureAnimationDelay) {
            mFeedbackViewCallback = onStartRunnable;
            mFakeTaskViewCallback = gestureAnimation::start;

            mFeedbackView.post(mFeedbackViewCallback);
            mFakeTaskView.postDelayed(mFakeTaskViewCallback, GESTURE_ANIMATION_DELAY_MS);
        } else {
            gestureAnimation.start();
        }
    }

    void setRippleHotspot(float x, float y) {
        mRippleDrawable.setHotspot(x, y);
    }

    void showRippleEffect(@Nullable Runnable onCompleteRunnable) {
        mRippleDrawable.setState(
                new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled});
        mRippleView.postDelayed(() -> {
            mRippleDrawable.setState(new int[] {});
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
        }, RIPPLE_VISIBLE_MS);
    }

    void onActionButtonClicked(View button) {
        mTutorialFragment.continueTutorial();
    }

    @CallSuper
    void transitToController() {
        hideFeedback();
        hideActionButton();
        updateSubtext();
        updateDrawables();
        updateLayout();

        mGestureCompleted = false;
        if (mFakeHotseatView != null) {
            mFakeHotseatView.setVisibility(View.INVISIBLE);
        }
    }

    void hideCloseButton() {
        mCloseButton.setVisibility(GONE);
    }

    void showCloseButton() {
        mCloseButton.setVisibility(View.VISIBLE);
        mCloseButton.setTextAppearance(Utilities.isDarkTheme(mContext)
                ? R.style.TextAppearance_GestureTutorial_Feedback_Subtext
                : R.style.TextAppearance_GestureTutorial_Feedback_Subtext_Dark);
    }

    void hideActionButton() {
        showCloseButton();
        // Invisible to maintain the layout.
        mActionButton.setVisibility(View.INVISIBLE);
        mActionButton.setOnClickListener(null);
    }

    void showActionButton() {
        hideCloseButton();
        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setOnClickListener(this::onActionButtonClicked);
    }

    void hideFakeTaskbar(boolean animateToHotseat) {
        if (!mTutorialFragment.isLargeScreen()) {
            return;
        }
        if (mFakeTaskbarViewCallback != null) {
            mFakeTaskbarView.removeCallbacks(mFakeTaskbarViewCallback);
        }
        if (animateToHotseat) {
            mFakeTaskbarViewCallback = () ->
                    mFakeTaskbarView.animateDisappearanceToHotseat(mFakeHotseatView);
        } else {
            mFakeTaskbarViewCallback = mFakeTaskbarView::animateDisappearanceToBottom;
        }
        mFakeTaskbarView.post(mFakeTaskbarViewCallback);
    }

    void showFakeTaskbar(boolean animateFromHotseat) {
        if (!mTutorialFragment.isLargeScreen()) {
            return;
        }
        if (mFakeTaskbarViewCallback != null) {
            mFakeTaskbarView.removeCallbacks(mFakeTaskbarViewCallback);
        }
        if (animateFromHotseat) {
            mFakeTaskbarViewCallback = () ->
                    mFakeTaskbarView.animateAppearanceFromHotseat(mFakeHotseatView);
        } else {
            mFakeTaskbarViewCallback = mFakeTaskbarView::animateAppearanceFromBottom;
        }
        mFakeTaskbarView.post(mFakeTaskbarViewCallback);
    }

    void updateFakeAppTaskViewLayout(@LayoutRes int mockAppTaskLayoutResId) {
        updateFakeViewLayout(mFakeTaskView, mockAppTaskLayoutResId);
    }

    void updateFakeViewLayout(ViewGroup view, @LayoutRes int mockLayoutResId) {
        view.removeAllViews();
        if (mockLayoutResId != NO_ID) {
            view.addView(
                    inflate(mContext, mockLayoutResId, null),
                    new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void updateSubtext() {
        mTutorialStepView.setTutorialProgress(
                mTutorialFragment.getCurrentStep(), mTutorialFragment.getNumSteps());
    }

    private void updateDrawables() {
        if (mContext != null) {
            mTutorialFragment.getRootView().setBackground(AppCompatResources.getDrawable(
                    mContext, getMockWallpaperResId()));
            mTutorialFragment.updateFeedbackAnimation();
            mFakeLauncherView.setBackgroundColor(
                    mContext.getColor(R.color.gesture_tutorial_fake_wallpaper_color));
            updateFakeViewLayout(mFakeHotseatView, getMockHotseatResId());
            mHotseatIconView = mFakeHotseatView.findViewById(R.id.hotseat_icon_1);
            updateFakeViewLayout(mFakeTaskView, getMockAppTaskLayoutResId());
            mFakeTaskView.animate().alpha(1).setListener(
                    AnimatorListeners.forSuccessCallback(() -> mFakeTaskView.animate().cancel()));
            mFakePreviousTaskView.setFakeTaskViewFillColor(mContext.getResources().getColor(
                    getMockPreviousAppTaskThumbnailColorResId()));
            mFakeIconView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppIconResId()));
        }
    }

    private void updateLayout() {
        if (mContext != null) {
            RelativeLayout.LayoutParams feedbackLayoutParams =
                    (RelativeLayout.LayoutParams) mFeedbackView.getLayoutParams();
            feedbackLayoutParams.setMarginStart(mContext.getResources().getDimensionPixelSize(
                    mTutorialFragment.isLargeScreen()
                            ? R.dimen.gesture_tutorial_foldable_feedback_margin_start_end
                            : R.dimen.gesture_tutorial_feedback_margin_start_end));
            feedbackLayoutParams.setMarginEnd(mContext.getResources().getDimensionPixelSize(
                    mTutorialFragment.isLargeScreen()
                            ? R.dimen.gesture_tutorial_foldable_feedback_margin_start_end
                            : R.dimen.gesture_tutorial_feedback_margin_start_end));

            mFakeTaskbarView.setVisibility(mTutorialFragment.isLargeScreen() ? View.VISIBLE : GONE);
        }
    }

    private AlertDialog createSkipTutorialDialog() {
        if (mContext instanceof GestureSandboxActivity) {
            GestureSandboxActivity sandboxActivity = (GestureSandboxActivity) mContext;
            View contentView = View.inflate(
                    sandboxActivity, R.layout.gesture_tutorial_dialog, null);
            AlertDialog tutorialDialog = new AlertDialog
                    .Builder(sandboxActivity, R.style.Theme_AppCompat_Dialog_Alert)
                    .setView(contentView)
                    .create();

            PackageManager packageManager = mContext.getPackageManager();
            CharSequence tipsAppName = DEFAULT_PIXEL_TIPS_APP_NAME;

            try {
                tipsAppName = packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(
                                PIXEL_TIPS_APP_PACKAGE_NAME, PackageManager.GET_META_DATA));
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG,
                        "Could not find app label for package name: "
                                + PIXEL_TIPS_APP_PACKAGE_NAME
                                + ". Defaulting to 'Pixel Tips.'",
                        e);
            }

            TextView subtitleTextView = (TextView) contentView.findViewById(
                    R.id.gesture_tutorial_dialog_subtitle);
            if (subtitleTextView != null) {
                subtitleTextView.setText(
                        mContext.getString(R.string.skip_tutorial_dialog_subtitle, tipsAppName));
            } else {
                Log.w(TAG, "No subtitle view in the skip tutorial dialog to update.");
            }

            Button cancelButton = (Button) contentView.findViewById(
                    R.id.gesture_tutorial_dialog_cancel_button);
            if (cancelButton != null) {
                cancelButton.setOnClickListener(
                        v -> tutorialDialog.dismiss());
            } else {
                Log.w(TAG, "No cancel button in the skip tutorial dialog to update.");
            }

            Button confirmButton = contentView.findViewById(
                    R.id.gesture_tutorial_dialog_confirm_button);
            if (confirmButton != null) {
                confirmButton.setOnClickListener(v -> {
                    sandboxActivity.closeTutorial();
                    tutorialDialog.dismiss();
                });
            } else {
                Log.w(TAG, "No confirm button in the skip tutorial dialog to update.");
            }

            tutorialDialog.getWindow().setBackgroundDrawable(
                    new ColorDrawable(sandboxActivity.getColor(android.R.color.transparent)));

            return tutorialDialog;
        }

        return null;
    }

    protected AnimatorSet createFingerDotAppearanceAnimatorSet() {
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.ALPHA, 0f, FINGER_DOT_VISIBLE_ALPHA);
        ObjectAnimator yScaleAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.SCALE_Y, FINGER_DOT_SMALL_SCALE, 1f);
        ObjectAnimator xScaleAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.SCALE_X, FINGER_DOT_SMALL_SCALE, 1f);
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(alphaAnimator);
        animators.add(xScaleAnimator);
        animators.add(yScaleAnimator);

        AnimatorSet appearanceAnimatorSet = new AnimatorSet();

        appearanceAnimatorSet.playTogether(animators);
        appearanceAnimatorSet.setDuration(FINGER_DOT_ANIMATION_DURATION_MILLIS);

        return appearanceAnimatorSet;
    }

    protected AnimatorSet createFingerDotDisappearanceAnimatorSet() {
        ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.ALPHA, FINGER_DOT_VISIBLE_ALPHA, 0f);
        ObjectAnimator yScaleAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.SCALE_Y, 1f, FINGER_DOT_SMALL_SCALE);
        ObjectAnimator xScaleAnimator = ObjectAnimator.ofFloat(
                mFingerDotView, View.SCALE_X, 1f, FINGER_DOT_SMALL_SCALE);
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(alphaAnimator);
        animators.add(xScaleAnimator);
        animators.add(yScaleAnimator);

        AnimatorSet appearanceAnimatorSet = new AnimatorSet();

        appearanceAnimatorSet.playTogether(animators);
        appearanceAnimatorSet.setDuration(FINGER_DOT_ANIMATION_DURATION_MILLIS);

        return appearanceAnimatorSet;
    }

    protected Animator createAnimationPause() {
        return ValueAnimator.ofFloat(0f, 1f).setDuration(GESTURE_ANIMATION_PAUSE_DURATION_MILLIS);
    }

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        BACK_NAVIGATION,
        BACK_NAVIGATION_COMPLETE,
        HOME_NAVIGATION,
        HOME_NAVIGATION_COMPLETE,
        OVERVIEW_NAVIGATION,
        OVERVIEW_NAVIGATION_COMPLETE,
        ASSISTANT,
        ASSISTANT_COMPLETE,
        SANDBOX_MODE
    }
}
