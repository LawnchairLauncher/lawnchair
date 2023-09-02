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

import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.RawRes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.views.ClipIconView;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureAttemptCallback;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureAttemptCallback;
import com.android.systemui.shared.system.QuickStepContract;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;

import java.util.ArrayList;

abstract class TutorialController implements BackGestureAttemptCallback,
        NavBarGestureAttemptCallback {

    private static final String LOG_TAG = "TutorialController";

    private static final float FINGER_DOT_VISIBLE_ALPHA = 0.7f;
    private static final float FINGER_DOT_SMALL_SCALE = 0.7f;
    private static final int FINGER_DOT_ANIMATION_DURATION_MILLIS = 500;

    private static final String PIXEL_TIPS_APP_PACKAGE_NAME = "com.google.android.apps.tips";
    private static final CharSequence DEFAULT_PIXEL_TIPS_APP_NAME = "Pixel Tips";

    private static final int FEEDBACK_ANIMATION_MS = 133;
    private static final int RIPPLE_VISIBLE_MS = 300;
    private static final int GESTURE_ANIMATION_DELAY_MS = 1500;
    private static final int ADVANCE_TUTORIAL_TIMEOUT_MS = 2000;
    private static final long GESTURE_ANIMATION_PAUSE_DURATION_MILLIS = 1000;
    protected float mExitingAppEndingCornerRadius;
    protected float mExitingAppStartingCornerRadius;
    protected int mScreenHeight;
    protected float mScreenWidth;
    protected float mExitingAppMargin;

    final TutorialFragment mTutorialFragment;
    TutorialType mTutorialType;
    final Context mContext;

    final TextView mSkipButton;
    final Button mDoneButton;
    final ViewGroup mFeedbackView;
    final TextView mFeedbackTitleView;
    final TextView mFeedbackSubtitleView;
    final ImageView mEdgeGestureVideoView;
    final RelativeLayout mFakeLauncherView;
    final FrameLayout mFakeHotseatView;
    @Nullable View mHotseatIconView;
    final ClipIconView mFakeIconView;
    final FrameLayout mFakeTaskView;
    @Nullable final AnimatedTaskbarView mFakeTaskbarView;
    final AnimatedTaskView mFakePreviousTaskView;
    final View mRippleView;
    final RippleDrawable mRippleDrawable;
    final TutorialStepIndicator mTutorialStepView;
    final ImageView mFingerDotView;
    private final Rect mExitingAppRect = new Rect();
    protected View mExitingAppView;
    protected int mExitingAppRadius;
    private final AlertDialog mSkipTutorialDialog;

    private boolean mGestureCompleted = false;
    protected LottieAnimationView mAnimatedGestureDemonstration;
    protected LottieAnimationView mCheckmarkAnimation;
    private RelativeLayout mFullGestureDemonstration;

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
        mSkipButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mSkipButton.setOnClickListener(button -> showSkipTutorialDialog());
        mFeedbackView = rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mFeedbackTitleView = mFeedbackView.findViewById(
                R.id.gesture_tutorial_fragment_feedback_title);
        mFeedbackSubtitleView = mFeedbackView.findViewById(
                R.id.gesture_tutorial_fragment_feedback_subtitle);
        mEdgeGestureVideoView = rootView.findViewById(R.id.gesture_tutorial_edge_gesture_video);
        mFakeLauncherView = rootView.findViewById(R.id.gesture_tutorial_fake_launcher_view);
        mFakeHotseatView = rootView.findViewById(R.id.gesture_tutorial_fake_hotseat_view);
        mFakeIconView = rootView.findViewById(R.id.gesture_tutorial_fake_icon_view);
        mFakeTaskView = rootView.findViewById(R.id.gesture_tutorial_fake_task_view);
        mFakeTaskbarView = ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? null : rootView.findViewById(R.id.gesture_tutorial_fake_taskbar_view);
        mFakePreviousTaskView =
                rootView.findViewById(R.id.gesture_tutorial_fake_previous_task_view);
        mRippleView = rootView.findViewById(R.id.gesture_tutorial_ripple_view);
        mRippleDrawable = (RippleDrawable) mRippleView.getBackground();
        mDoneButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);
        mTutorialStepView =
                rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_tutorial_step);
        mFingerDotView = rootView.findViewById(R.id.gesture_tutorial_finger_dot);
        mSkipTutorialDialog = createSkipTutorialDialog();

        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mFullGestureDemonstration = rootView.findViewById(R.id.full_gesture_demonstration);
            mCheckmarkAnimation = rootView.findViewById(R.id.checkmark_animation);
            mAnimatedGestureDemonstration = rootView.findViewById(
                    R.id.gesture_demonstration_animations);
            mExitingAppView = rootView.findViewById(R.id.exiting_app_back);
            mScreenWidth = mTutorialFragment.getDeviceProfile().widthPx;
            mScreenHeight = mTutorialFragment.getDeviceProfile().heightPx;
            mExitingAppMargin = mContext.getResources().getDimensionPixelSize(
                    R.dimen.gesture_tutorial_back_gesture_exiting_app_margin);
            mExitingAppStartingCornerRadius = QuickStepContract.getWindowCornerRadius(mContext);
            mExitingAppEndingCornerRadius = mContext.getResources().getDimensionPixelSize(
                    R.dimen.gesture_tutorial_back_gesture_end_corner_radius);
            mAnimatedGestureDemonstration.addLottieOnCompositionLoadedListener(
                    this::createScalingMatrix);

            mFeedbackTitleView.setText(getIntroductionTitle());
            mFeedbackSubtitleView.setText(getIntroductionSubtitle());
            mExitingAppView.setClipToOutline(true);
            mExitingAppView.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(mExitingAppRect, mExitingAppRadius);
                }
            });
        }

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

    /** Scale the Lottie gesture animation to fit the device based on device dimensions */
    private void createScalingMatrix(LottieComposition composition) {
        Rect animationBoundsRect = composition.getBounds();
        if (animationBoundsRect == null) {
            mAnimatedGestureDemonstration.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return;
        }
        Matrix scaleMatrix = new Matrix();
        float scaleFactor = mScreenWidth / animationBoundsRect.width();
        float heightTranslate = (mScreenHeight - (scaleFactor * animationBoundsRect.height()));

        scaleMatrix.postScale(scaleFactor, scaleFactor);
        scaleMatrix.postTranslate(0, heightTranslate);
        mAnimatedGestureDemonstration.setImageMatrix(scaleMatrix);
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
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            return mTutorialFragment.isLargeScreen()
                    ? mTutorialFragment.isFoldable()
                        ? R.layout.redesigned_gesture_tutorial_foldable_mock_hotseat
                        : R.layout.redesigned_gesture_tutorial_tablet_mock_hotseat
                    : R.layout.redesigned_gesture_tutorial_mock_hotseat;
        } else {
            return mTutorialFragment.isLargeScreen()
                    ? mTutorialFragment.isFoldable()
                        ? R.layout.gesture_tutorial_foldable_mock_hotseat
                        : R.layout.gesture_tutorial_tablet_mock_hotseat
                    : R.layout.gesture_tutorial_mock_hotseat;
        }
    }

    @LayoutRes
    protected int getMockAppTaskLayoutResId() {
        return NO_ID;
    }

    @RawRes
    protected int getGestureLottieAnimationId() {
        return NO_ID;
    }

    @ColorInt
    protected int getMockPreviousAppTaskThumbnailColor() {
        return mContext.getResources().getColor(
                R.color.gesture_tutorial_fake_previous_task_view_color);
    }

    @ColorInt
    protected int getFakeTaskViewColor() {
        return Color.TRANSPARENT;
    }

    @ColorInt
    protected abstract int getFakeLauncherColor();

    @ColorInt
    protected int getExitingAppColor() {
        return Color.TRANSPARENT;
    }

    @ColorInt
    protected int getHotseatIconColor() {
        return Color.TRANSPARENT;
    }

    @DrawableRes
    public int getMockAppIconResId() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.drawable.redesigned_hotseat_icon
                : R.drawable.default_sandbox_app_icon;
    }

    @DrawableRes
    public int getMockWallpaperResId() {
        return R.drawable.default_sandbox_wallpaper;
    }

    void fadeTaskViewAndRun(Runnable r) {
        mFakeTaskView.animate().alpha(0).setListener(AnimatorListeners.forSuccessCallback(r));
    }

    @StringRes
    public int getIntroductionTitle() {
        return NO_ID;
    }

    @StringRes
    public int getIntroductionSubtitle() {
        return NO_ID;
    }

    @StringRes
    public int getSpokenIntroductionSubtitle() {
        return NO_ID;
    }

    @StringRes
    public int getSuccessFeedbackSubtitle() {
        return NO_ID;
    }

    @StringRes
    public int getSuccessFeedbackTitle() {
        return NO_ID;
    }

    @StyleRes
    public int getTitleTextAppearance() {
        return NO_ID;
    }

    @StyleRes
    public int getSuccessTitleTextAppearance() {
        return NO_ID;
    }

    @StyleRes
    public int getDoneButtonTextAppearance() {
        return NO_ID;
    }

    @ColorInt
    public abstract int getDoneButtonColor();

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
     * Only use this when a gesture is completed, but the feedback shouldn't be shown immediately.
     * In that case, call this method immediately instead.
     */
    public void setGestureCompleted() {
        mGestureCompleted = true;
    }

    /**
     * Show feedback reflecting a successful gesture attempt.
     **/
    void showSuccessFeedback() {
        int successSubtitleResId = getSuccessFeedbackSubtitle();
        if (successSubtitleResId == NO_ID) {
            // Allow crash since this should never be reached with a tutorial controller used in
            // production.
            Log.e(LOG_TAG,
                    "Cannot show success feedback for tutorial step: " + mTutorialType
                            + ", no success feedback subtitle",
                    new IllegalStateException());
        }
        showFeedback(successSubtitleResId, true);
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
                        ? getSuccessFeedbackTitle() : R.string.gesture_tutorial_try_again,
                subtitleResId,
                NO_ID,
                isGestureSuccessful,
                false);
    }

    void showFeedback(
            int titleResId,
            int subtitleResId,
            int spokenSubtitleResId,
            boolean isGestureSuccessful,
            boolean useGestureAnimationDelay) {
        mFeedbackTitleView.removeCallbacks(mTitleViewCallback);
        if (mFeedbackViewCallback != null) {
            mFeedbackView.removeCallbacks(mFeedbackViewCallback);
            mFeedbackViewCallback = null;
        }

        mFeedbackTitleView.setText(titleResId);
        mFeedbackSubtitleView.setText(
                ENABLE_NEW_GESTURE_NAV_TUTORIAL.get() || spokenSubtitleResId == NO_ID
                        ? mContext.getText(subtitleResId)
                        : Utilities.wrapForTts(
                                mContext.getText(subtitleResId),
                                mContext.getString(spokenSubtitleResId)));
        if (isGestureSuccessful) {
            if (mTutorialFragment.isAtFinalStep()) {
                showActionButton();
            }

            if (mFakeTaskViewCallback != null) {
                mFakeTaskView.removeCallbacks(mFakeTaskViewCallback);
                mFakeTaskViewCallback = null;
            }

            if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                showSuccessPage();
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

    private void showSuccessPage() {
        pauseAndHideLottieAnimation();
        mCheckmarkAnimation.setVisibility(View.VISIBLE);
        mCheckmarkAnimation.playAnimation();
        mFeedbackTitleView.setTextAppearance(mContext, getSuccessTitleTextAppearance());
    }

    public boolean isGestureCompleted() {
        return mGestureCompleted;
    }

    void hideFeedback() {
        if (mFeedbackView.getVisibility() != View.VISIBLE) {
            return;
        }
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
        if (mFakeTaskbarViewCallback != null && mFakeTaskbarView != null) {
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

        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mFeedbackView.setVisibility(View.VISIBLE);
            mAnimatedGestureDemonstration.setVisibility(View.VISIBLE);
            mFullGestureDemonstration.setVisibility(View.VISIBLE);
            mAnimatedGestureDemonstration.playAnimation();
            return;
        }

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
        updateCloseButton();
        updateSubtext();
        updateDrawables();
        updateLayout();

        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mFeedbackTitleView.setTextAppearance(mContext, getTitleTextAppearance());
            mDoneButton.setTextAppearance(mContext, getDoneButtonTextAppearance());
            mDoneButton.getBackground().setTint(getDoneButtonColor());
            mCheckmarkAnimation.setAnimation(mTutorialFragment.isAtFinalStep()
                    ? R.raw.checkmark_animation_end
                    : R.raw.checkmark_animation_in_progress);
            if (!isGestureCompleted()) {
                mCheckmarkAnimation.setVisibility(GONE);
                startGestureAnimation();
                if (mTutorialType == TutorialType.BACK_NAVIGATION) {
                    resetViewsForBackGesture();
                }

            }
        } else {
            hideFeedback();
            hideActionButton();
        }

        mGestureCompleted = false;
        if (mFakeHotseatView != null) {
            mFakeHotseatView.setVisibility(View.INVISIBLE);
        }
    }

    protected void resetViewsForBackGesture() {
        mFakeTaskView.setVisibility(View.VISIBLE);
        mFakeTaskView.setBackgroundColor(getFakeTaskViewColor());
        mExitingAppView.setVisibility(View.VISIBLE);

        // reset the exiting app's dimensions
        mExitingAppRect.set(0, 0, (int) mScreenWidth, (int) mScreenHeight);
        mExitingAppRadius = 0;
        mExitingAppView.resetPivot();
        mExitingAppView.setScaleX(1f);
        mExitingAppView.setScaleY(1f);
        mExitingAppView.setTranslationX(0);
        mExitingAppView.setTranslationY(0);
        mExitingAppView.invalidateOutline();
    }

    private void startGestureAnimation() {
        mAnimatedGestureDemonstration.setAnimation(getGestureLottieAnimationId());
        mAnimatedGestureDemonstration.playAnimation();
    }

    void updateCloseButton() {
        mSkipButton.setTextAppearance(Utilities.isDarkTheme(mContext)
                ? R.style.TextAppearance_GestureTutorial_Feedback_Subtext
                : R.style.TextAppearance_GestureTutorial_Feedback_Subtext_Dark);
    }

    void hideActionButton() {
        mSkipButton.setVisibility(View.VISIBLE);
        // Invisible to maintain the layout.
        mDoneButton.setVisibility(View.INVISIBLE);
        mDoneButton.setOnClickListener(null);
    }

    void showActionButton() {
        mSkipButton.setVisibility(GONE);
        mDoneButton.setVisibility(View.VISIBLE);
        mDoneButton.setOnClickListener(this::onActionButtonClicked);
    }

    void hideFakeTaskbar(boolean animateToHotseat) {
        if (!mTutorialFragment.isLargeScreen() || mFakeTaskbarView == null) {
            return;
        }
        if (mFakeTaskbarViewCallback != null) {
            mFakeTaskbarView.removeCallbacks(mFakeTaskbarViewCallback);
        }
        if (animateToHotseat) {
            mFakeTaskbarViewCallback = () ->
                    mFakeTaskbarView.animateDisappearanceToHotseat(mFakeHotseatView);
        }
        mFakeTaskbarView.post(mFakeTaskbarViewCallback);
    }

    void showFakeTaskbar(boolean animateFromHotseat) {
        if (!mTutorialFragment.isLargeScreen() || mFakeTaskbarView == null) {
            return;
        }
        if (mFakeTaskbarViewCallback != null) {
            mFakeTaskbarView.removeCallbacks(mFakeTaskbarViewCallback);
        }
        if (animateFromHotseat) {
            mFakeTaskbarViewCallback = () ->
                    mFakeTaskbarView.animateAppearanceFromHotseat(mFakeHotseatView);
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
        if (!ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mTutorialStepView.setTutorialProgress(
                    mTutorialFragment.getCurrentStep(), mTutorialFragment.getNumSteps());
        }
    }

    private void updateHotseatChildViewColor(@Nullable View child) {
        if (child == null) return;
        child.getBackground().setTint(getHotseatIconColor());
    }

    private void updateDrawables() {
        if (mContext != null) {
            mTutorialFragment.getRootView().setBackground(AppCompatResources.getDrawable(
                    mContext, getMockWallpaperResId()));
            mTutorialFragment.updateFeedbackAnimation();
            mFakeLauncherView.setBackgroundColor(ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                    ? getFakeLauncherColor()
                    : mContext.getColor(R.color.gesture_tutorial_fake_wallpaper_color));
            updateFakeViewLayout(mFakeHotseatView, getMockHotseatResId());
            mHotseatIconView = mFakeHotseatView.findViewById(R.id.hotseat_icon_1);
            mFakeTaskView.animate().alpha(1).setListener(
                    AnimatorListeners.forSuccessCallback(() -> mFakeTaskView.animate().cancel()));
            mFakePreviousTaskView.setFakeTaskViewFillColor(getMockPreviousAppTaskThumbnailColor());
            mFakeIconView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppIconResId()));

            if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                mExitingAppView.setBackgroundColor(getExitingAppColor());
                mFakeTaskView.setBackgroundColor(getFakeTaskViewColor());
                updateHotseatChildViewColor(mHotseatIconView);
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_icon_2));
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_icon_3));
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_icon_4));
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_icon_5));
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_icon_6));
                updateHotseatChildViewColor(mFakeHotseatView.findViewById(R.id.hotseat_search_bar));
            } else {
                updateFakeViewLayout(mFakeTaskView, getMockAppTaskLayoutResId());
            }
        }
    }

    private void updateLayout() {
        if (mContext == null) {
            return;
        }
        RelativeLayout.LayoutParams feedbackLayoutParams =
                (RelativeLayout.LayoutParams) mFeedbackView.getLayoutParams();
        feedbackLayoutParams.setMarginStart(mContext.getResources().getDimensionPixelSize(
                mTutorialFragment.isLargeScreen()
                        ? R.dimen.gesture_tutorial_tablet_feedback_margin_start_end
                        : R.dimen.gesture_tutorial_feedback_margin_start_end));
        feedbackLayoutParams.setMarginEnd(mContext.getResources().getDimensionPixelSize(
                mTutorialFragment.isLargeScreen()
                        ? R.dimen.gesture_tutorial_tablet_feedback_margin_start_end
                        : R.dimen.gesture_tutorial_feedback_margin_start_end));
        feedbackLayoutParams.topMargin = mContext.getResources().getDimensionPixelSize(
                mTutorialFragment.isLargeScreen()
                        ? R.dimen.gesture_tutorial_tablet_feedback_margin_top
                        : R.dimen.gesture_tutorial_feedback_margin_top);

        if (mFakeTaskbarView != null) {
            mFakeTaskbarView.setVisibility(
                    mTutorialFragment.isLargeScreen() ? View.VISIBLE : GONE);
        }

        RelativeLayout.LayoutParams hotseatLayoutParams =
                (RelativeLayout.LayoutParams) mFakeHotseatView.getLayoutParams();
        if (!mTutorialFragment.isLargeScreen()) {
            DeviceProfile dp = mTutorialFragment.getDeviceProfile();
            dp.updateIsSeascape(mContext);

            hotseatLayoutParams.addRule(dp.isLandscape
                    ? (dp.isSeascape()
                            ? RelativeLayout.ALIGN_PARENT_START
                            : RelativeLayout.ALIGN_PARENT_END)
                    : RelativeLayout.ALIGN_PARENT_BOTTOM);
        } else {
            hotseatLayoutParams.width = RelativeLayout.LayoutParams.MATCH_PARENT;
            hotseatLayoutParams.height = RelativeLayout.LayoutParams.WRAP_CONTENT;
            hotseatLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            hotseatLayoutParams.removeRule(RelativeLayout.ALIGN_PARENT_START);
            hotseatLayoutParams.removeRule(RelativeLayout.ALIGN_PARENT_END);
        }
        mFakeHotseatView.setLayoutParams(hotseatLayoutParams);
    }

    private AlertDialog createSkipTutorialDialog() {
        if (!(mContext instanceof GestureSandboxActivity)) {
            return null;
        }
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
            Log.e(LOG_TAG,
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
            Log.w(LOG_TAG, "No subtitle view in the skip tutorial dialog to update.");
        }

        Button cancelButton = (Button) contentView.findViewById(
                R.id.gesture_tutorial_dialog_cancel_button);
        if (cancelButton != null) {
            cancelButton.setOnClickListener(
                    v -> tutorialDialog.dismiss());
        } else {
            Log.w(LOG_TAG, "No cancel button in the skip tutorial dialog to update.");
        }

        Button confirmButton = contentView.findViewById(
                R.id.gesture_tutorial_dialog_confirm_button);
        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                mTutorialFragment.closeTutorialStep(true);
                tutorialDialog.dismiss();
            });
        } else {
            Log.w(LOG_TAG, "No confirm button in the skip tutorial dialog to update.");
        }

        tutorialDialog.getWindow().setBackgroundDrawable(
                new ColorDrawable(sandboxActivity.getColor(android.R.color.transparent)));

        return tutorialDialog;
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

    void pauseAndHideLottieAnimation() {
        mAnimatedGestureDemonstration.pauseAnimation();
        mAnimatedGestureDemonstration.setVisibility(View.INVISIBLE);
        mFullGestureDemonstration.setVisibility(View.INVISIBLE);
    }

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        BACK_NAVIGATION,
        BACK_NAVIGATION_COMPLETE,
        HOME_NAVIGATION,
        HOME_NAVIGATION_COMPLETE,
        OVERVIEW_NAVIGATION,
        OVERVIEW_NAVIGATION_COMPLETE
    }
}
