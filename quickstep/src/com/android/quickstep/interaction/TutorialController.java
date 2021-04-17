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

import android.content.Context;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.CallSuper;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.views.ClipIconView;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureAttemptCallback;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureAttemptCallback;

abstract class TutorialController implements BackGestureAttemptCallback,
        NavBarGestureAttemptCallback {

    private static final int FEEDBACK_VISIBLE_MS = 2500;
    private static final int FEEDBACK_ANIMATION_MS = 250;
    private static final int RIPPLE_VISIBLE_MS = 300;

    final TutorialFragment mTutorialFragment;
    TutorialType mTutorialType;
    final Context mContext;

    final ImageButton mCloseButton;
    final TextView mTitleTextView;
    final TextView mSubtitleTextView;
    final ViewGroup mFeedbackView;
    final VideoView mFeedbackVideoView;
    final ImageView mFakeLauncherView;
    final ClipIconView mFakeIconView;
    final View mFakeTaskView;
    final View mFakePreviousTaskView;
    final View mRippleView;
    final RippleDrawable mRippleDrawable;
    final Button mActionTextButton;
    final Button mActionButton;
    private final Runnable mHideFeedbackRunnable;
    Runnable mHideFeedbackEndAction;

    TutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        mTutorialFragment = tutorialFragment;
        mTutorialType = tutorialType;
        mContext = mTutorialFragment.getContext();

        RootSandboxLayout rootView = tutorialFragment.getRootView();
        mCloseButton = rootView.findViewById(R.id.gesture_tutorial_fragment_close_button);
        mCloseButton.setOnClickListener(button -> mTutorialFragment.closeTutorial());
        mTitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_title_view);
        mSubtitleTextView = rootView.findViewById(R.id.gesture_tutorial_fragment_subtitle_view);
        mFeedbackView = rootView.findViewById(R.id.gesture_tutorial_fragment_feedback_view);
        mFeedbackVideoView = rootView.findViewById(R.id.gesture_tutorial_feedback_video);
        mFakeLauncherView = rootView.findViewById(R.id.gesture_tutorial_fake_launcher_view);
        mFakeIconView = rootView.findViewById(R.id.gesture_tutorial_fake_icon_view);
        mFakeTaskView = rootView.findViewById(R.id.gesture_tutorial_fake_task_view);
        mFakePreviousTaskView =
                rootView.findViewById(R.id.gesture_tutorial_fake_previous_task_view);
        mRippleView = rootView.findViewById(R.id.gesture_tutorial_ripple_view);
        mRippleDrawable = (RippleDrawable) mRippleView.getBackground();
        mActionTextButton =
                rootView.findViewById(R.id.gesture_tutorial_fragment_action_text_button);
        mActionButton = rootView.findViewById(R.id.gesture_tutorial_fragment_action_button);

        mHideFeedbackRunnable =
                () -> mFeedbackView.animate()
                        .translationY(-mFeedbackView.getTop() - mFeedbackView.getHeight())
                        .setDuration(FEEDBACK_ANIMATION_MS)
                        .withEndAction(this::hideFeedbackEndAction).start();
    }

    void setTutorialType(TutorialType tutorialType) {
        mTutorialType = tutorialType;
    }

    @Nullable
    Integer getTitleStringId() {
        return null;
    }

    @Nullable
    Integer getSubtitleStringId() {
        return null;
    }

    @Nullable
    Integer getActionButtonStringId() {
        return null;
    }

    @Nullable
    Integer getActionTextButtonStringId() {
        return null;
    }

    @DrawableRes
    protected int getMockLauncherResId() {
        return R.drawable.default_sandbox_mock_launcher;
    }

    @DrawableRes
    protected int getMockAppTaskThumbnailResId() {
        return R.drawable.default_sandbox_app_task_thumbnail;
    }

    @DrawableRes
    protected int getMockPreviousAppTaskThumbnailResId() {
        return R.drawable.default_sandbox_app_previous_task_thumbnail;
    }

    @Nullable
    public View getMockLauncherView() {
        InvariantDeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext);

        return new SandboxLauncherRenderer(mContext, dp, true).getRenderedView();
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
        mFakeTaskView.animate().alpha(0).setListener(AnimationSuccessListener.forRunnable(r));
    }

    /**
     * Show feedback reflecting a failed gesture attempt.
     *
     * @param subtitleResId Resource of the text to display.
     **/
    void showFeedback(int subtitleResId) {
        showFeedback(subtitleResId, null);
    }

    /**
     * Show feedback reflecting the result of a gesture attempt.
     *
     * @param successEndAction Non-null iff the gesture was successful; this is run after the
     *                        feedback is shown (i.e. to go to the next step)
     **/
    void showFeedback(int subtitleResId, @Nullable Runnable successEndAction) {
        if (mHideFeedbackEndAction != null) {
            return;
        }
        int visibleDuration = FEEDBACK_VISIBLE_MS;
        if (mTutorialFragment.getFeedbackVideoResId() != null) {
            if (successEndAction == null) {
                if (mFeedbackVideoView.isPlaying()) {
                    mFeedbackVideoView.seekTo(1);
                } else {
                    mFeedbackVideoView.start();
                }
                mFeedbackVideoView.setVisibility(View.VISIBLE);
                visibleDuration = mTutorialFragment.getFeedbackVideoDuration();
            } else {
                mTutorialFragment.releaseFeedbackVideoView();
            }
        }
        TextView title = mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_title);
        title.setText(successEndAction == null
                ? R.string.gesture_tutorial_try_again
                : R.string.gesture_tutorial_nice);
        TextView subtitle =
                mFeedbackView.findViewById(R.id.gesture_tutorial_fragment_feedback_subtitle);
        subtitle.setText(subtitleResId);
        mHideFeedbackEndAction = successEndAction;
        mFeedbackView.setTranslationY(-mFeedbackView.getHeight() - mFeedbackView.getTop());
        mFeedbackView.setVisibility(View.VISIBLE);
        mFeedbackView.animate()
                .setDuration(FEEDBACK_ANIMATION_MS)
                .translationY(0)
                .start();
        mFeedbackView.removeCallbacks(mHideFeedbackRunnable);
        mFeedbackView.postDelayed(mHideFeedbackRunnable, visibleDuration);
    }

    void hideFeedback(boolean releaseFeedbackVideo) {
        mFeedbackView.removeCallbacks(mHideFeedbackRunnable);
        mHideFeedbackEndAction = null;
        mFeedbackView.clearAnimation();
        mFeedbackView.setVisibility(View.INVISIBLE);
        if (releaseFeedbackVideo) {
            mTutorialFragment.releaseFeedbackVideoView();
        }
    }

    void hideFeedbackEndAction() {
        if (mHideFeedbackEndAction != null) {
            mHideFeedbackEndAction.run();
            mHideFeedbackEndAction = null;
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
        mTutorialFragment.closeTutorial();
    }

    void onActionTextButtonClicked(View button) {}

    @CallSuper
    void transitToController() {
        hideFeedback(false);
        updateTitles();
        updateActionButtons();
        updateDrawables();

        if (mFakeLauncherView != null) {
            mFakeLauncherView.setVisibility(View.INVISIBLE);
        }
    }

    private void updateTitles() {
        updateTitleView(mTitleTextView, getTitleStringId(),
                R.style.TextAppearance_GestureTutorial_Title);
        updateTitleView(mSubtitleTextView, getSubtitleStringId(),
                R.style.TextAppearance_GestureTutorial_Subtitle);
    }

    private void updateTitleView(TextView textView, @Nullable Integer stringId, int styleId) {
        if (stringId == null) {
            textView.setVisibility(View.GONE);
            return;
        }

        textView.setVisibility(View.VISIBLE);
        textView.setText(stringId);
        textView.setTextAppearance(styleId);
    }

    private void updateActionButtons() {
        updateButton(mActionButton, getActionButtonStringId(), this::onActionButtonClicked);
        updateButton(
                mActionTextButton, getActionTextButtonStringId(), this::onActionTextButtonClicked);
    }

    private void updateButton(Button button, @Nullable Integer stringId, OnClickListener listener) {
        if (stringId == null) {
            button.setVisibility(View.INVISIBLE);
            return;
        }

        button.setVisibility(View.VISIBLE);
        button.setText(stringId);
        button.setOnClickListener(listener);
    }

    private void updateDrawables() {
        if (mContext != null) {
            mTutorialFragment.getRootView().setBackground(AppCompatResources.getDrawable(
                    mContext, getMockWallpaperResId()));
            mTutorialFragment.updateFeedbackVideo();
            mFakeLauncherView.setImageDrawable(AppCompatResources.getDrawable(
                    mContext, getMockLauncherResId()));
            mFakeTaskView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppTaskThumbnailResId()));
            mFakeTaskView.animate().alpha(1).setListener(AnimationSuccessListener.forRunnable(
                    () -> mFakeTaskView.animate().cancel()));
            mFakePreviousTaskView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockPreviousAppTaskThumbnailResId()));
            mFakeIconView.setBackground(AppCompatResources.getDrawable(
                    mContext, getMockAppIconResId()));
        }
    }

    private boolean isComplete() {
        return mTutorialType == TutorialType.BACK_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.HOME_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.OVERVIEW_NAVIGATION_COMPLETE
                || mTutorialType == TutorialType.ASSISTANT_COMPLETE;
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
