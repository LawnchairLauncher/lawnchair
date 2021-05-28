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

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
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

abstract class TutorialController implements BackGestureAttemptCallback,
        NavBarGestureAttemptCallback {

    private static final String TAG = "TutorialController";

    private static final String PIXEL_TIPS_APP_PACKAGE_NAME = "com.google.android.apps.tips";
    private static final CharSequence DEFAULT_PIXEL_TIPS_APP_NAME = "Pixel Tips";

    private static final int FEEDBACK_ANIMATION_MS = 250;
    private static final int RIPPLE_VISIBLE_MS = 300;

    final TutorialFragment mTutorialFragment;
    TutorialType mTutorialType;
    final Context mContext;

    final TextView mCloseButton;
    final ViewGroup mFeedbackView;
    final ImageView mFeedbackVideoView;
    final ImageView mGestureVideoView;
    final RelativeLayout mFakeLauncherView;
    final ImageView mFakeHotseatView;
    final ClipIconView mFakeIconView;
    final View mFakeTaskView;
    final View mFakePreviousTaskView;
    final View mRippleView;
    final RippleDrawable mRippleDrawable;
    final Button mActionButton;
    final TutorialStepIndicator mTutorialStepView;
    private final AlertDialog mSkipTutorialDialog;

    protected boolean mGestureCompleted = false;

    TutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        mTutorialFragment = tutorialFragment;
        mTutorialType = tutorialType;
        mContext = mTutorialFragment.getContext();

        RootSandboxLayout rootView = tutorialFragment.getRootView();
        mCloseButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mCloseButton.setOnClickListener(button -> showSkipTutorialDialog());
        mFeedbackView = rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mFeedbackVideoView = rootView.findViewById(R.id.gesture_tutorial_feedback_video);
        mGestureVideoView = rootView.findViewById(R.id.gesture_tutorial_gesture_video);
        mFakeLauncherView = rootView.findViewById(R.id.gesture_tutorial_fake_launcher_view);
        mFakeHotseatView = rootView.findViewById(R.id.gesture_tutorial_fake_hotseat_view);
        mFakeIconView = rootView.findViewById(R.id.gesture_tutorial_fake_icon_view);
        mFakeTaskView = rootView.findViewById(R.id.gesture_tutorial_fake_task_view);
        mFakePreviousTaskView =
                rootView.findViewById(R.id.gesture_tutorial_fake_previous_task_view);
        mRippleView = rootView.findViewById(R.id.gesture_tutorial_ripple_view);
        mRippleDrawable = (RippleDrawable) mRippleView.getBackground();
        mActionButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);
        mTutorialStepView =
                rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_tutorial_step);
        mSkipTutorialDialog = createSkipTutorialDialog();
    }

    private void showSkipTutorialDialog() {
        if (mSkipTutorialDialog != null) {
            mSkipTutorialDialog.show();
        }
    }

    void setTutorialType(TutorialType tutorialType) {
        mTutorialType = tutorialType;
    }

    @DrawableRes
    protected int getMockHotseatResId() {
        return R.drawable.default_sandbox_mock_launcher;
    }

    @DrawableRes
    protected int getMockAppTaskThumbnailResId(boolean forDarkMode) {
        return R.drawable.default_sandbox_app_task_thumbnail;
    }

    @DrawableRes
    protected int getMockPreviousAppTaskThumbnailResId() {
        return R.drawable.default_sandbox_app_previous_task_thumbnail;
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
        AnimatedVectorDrawable tutorialAnimation = mTutorialFragment.getTutorialAnimation();
        AnimatedVectorDrawable gestureAnimation = mTutorialFragment.getGestureAnimation();

        if (tutorialAnimation != null && gestureAnimation != null) {
            TextView title = mFeedbackView.findViewById(
                    R.id.gesture_tutorial_fragment_feedback_title);

            playFeedbackVideo(tutorialAnimation, gestureAnimation, () -> {
                mFeedbackView.setTranslationY(0);
                title.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
            });
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
                isGestureSuccessful);
    }

    void showFeedback(
            int titleResId,
            int subtitleResId,
            boolean isGestureSuccessful) {
        TextView title = mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_title);
        title.setText(titleResId);
        TextView subtitle =
                mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_subtitle);
        subtitle.setText(subtitleResId);
        if (isGestureSuccessful) {
            showActionButton();
        }
        mGestureCompleted = isGestureSuccessful;

        AnimatedVectorDrawable tutorialAnimation = mTutorialFragment.getTutorialAnimation();
        AnimatedVectorDrawable gestureAnimation = mTutorialFragment.getGestureAnimation();
        if (tutorialAnimation != null && gestureAnimation != null) {
            if (!isGestureSuccessful) {
                playFeedbackVideo(tutorialAnimation, gestureAnimation, () -> {
                    mFeedbackView.setTranslationY(
                            -mFeedbackView.getHeight() - mFeedbackView.getTop());
                    mFeedbackView.setVisibility(View.VISIBLE);
                    mFeedbackView.animate()
                            .setDuration(FEEDBACK_ANIMATION_MS)
                            .translationY(0)
                            .start();
                    title.postDelayed(
                            () -> title.sendAccessibilityEvent(
                                    AccessibilityEvent.TYPE_VIEW_FOCUSED),
                            FEEDBACK_ANIMATION_MS);
                });
                return;
            } else {
                mTutorialFragment.releaseFeedbackVideoView();
            }
        }
        mFeedbackView.setTranslationY(-mFeedbackView.getHeight() - mFeedbackView.getTop());
        mFeedbackView.setVisibility(View.VISIBLE);
        mFeedbackView.animate()
                .setDuration(FEEDBACK_ANIMATION_MS)
                .translationY(0)
                .start();
        title.postDelayed(
                () -> title.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_FOCUSED),
                FEEDBACK_ANIMATION_MS);
    }

    void hideFeedback(boolean releaseFeedbackVideo) {
        mFeedbackView.clearAnimation();
        mFeedbackView.setVisibility(View.INVISIBLE);
        if (releaseFeedbackVideo) {
            mTutorialFragment.releaseFeedbackVideoView();
        }
    }

    private void playFeedbackVideo(
            @NonNull AnimatedVectorDrawable tutorialAnimation,
            @NonNull AnimatedVectorDrawable gestureAnimation,
            @NonNull Runnable onStartRunnable) {

        if (tutorialAnimation.isRunning()) {
            tutorialAnimation.reset();
        }
        tutorialAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {

            @Override
            public void onAnimationStart(Drawable drawable) {
                super.onAnimationStart(drawable);

                mGestureVideoView.setVisibility(GONE);
                if (gestureAnimation.isRunning()) {
                    gestureAnimation.stop();
                }

                onStartRunnable.run();
            }

            @Override
            public void onAnimationEnd(Drawable drawable) {
                super.onAnimationEnd(drawable);

                mGestureVideoView.setVisibility(View.VISIBLE);
                gestureAnimation.start();

                tutorialAnimation.unregisterAnimationCallback(this);
            }
        });

        tutorialAnimation.start();
        mFeedbackVideoView.setVisibility(View.VISIBLE);
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
        hideFeedback(false);
        hideActionButton();
        updateSubtext();
        updateDrawables();

        mGestureCompleted = false;
        if (mFakeHotseatView != null) {
            mFakeHotseatView.setVisibility(View.INVISIBLE);
        }
    }

    void hideActionButton() {
        mCloseButton.setVisibility(View.VISIBLE);
        mCloseButton.setTextAppearance(Utilities.isDarkTheme(mContext)
                ? R.style.TextAppearance_GestureTutorial_Feedback_Subtext
                : R.style.TextAppearance_GestureTutorial_Feedback_Subtext_Dark);

        // Invisible to maintain the layout.
        mActionButton.setVisibility(View.INVISIBLE);
        mActionButton.setOnClickListener(null);
    }

    void showActionButton() {
        mCloseButton.setVisibility(GONE);
        mActionButton.setVisibility(View.VISIBLE);
        mActionButton.setOnClickListener(this::onActionButtonClicked);
    }

    private void updateSubtext() {
        mTutorialStepView.setTutorialProgress(
                mTutorialFragment.getCurrentStep(), mTutorialFragment.getNumSteps());
    }

    private void updateDrawables() {
        if (mContext != null) {
            mTutorialFragment.getRootView().setBackground(AppCompatResources.getDrawable(
                    mContext, getMockWallpaperResId()));
            mTutorialFragment.updateFeedbackVideo();
            mFakeLauncherView.setBackgroundColor(
                    mContext.getColor(Utilities.isDarkTheme(mContext)
                            ? R.color.fake_wallpaper_color_dark_mode
                            : R.color.fake_wallpaper_color_light_mode));
            mFakeHotseatView.setImageDrawable(AppCompatResources.getDrawable(
                    mContext, getMockHotseatResId()));
            mFakeTaskView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppTaskThumbnailResId(Utilities.isDarkTheme(mContext))));
            mFakeTaskView.animate().alpha(1).setListener(
                    AnimatorListeners.forSuccessCallback(() -> mFakeTaskView.animate().cancel()));
            mFakePreviousTaskView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockPreviousAppTaskThumbnailResId()));
            mFakeIconView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppIconResId()));
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

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        RIGHT_EDGE_BACK_NAVIGATION,
        LEFT_EDGE_BACK_NAVIGATION,
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
