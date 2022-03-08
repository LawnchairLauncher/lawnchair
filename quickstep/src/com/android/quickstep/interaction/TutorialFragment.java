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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Insets;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.quickstep.interaction.TutorialController.TutorialType;

abstract class TutorialFragment extends Fragment implements OnTouchListener {

    private static final String LOG_TAG = "TutorialFragment";
    static final String KEY_TUTORIAL_TYPE = "tutorial_type";
    static final String KEY_GESTURE_COMPLETE = "gesture_complete";

    TutorialType mTutorialType;
    boolean mGestureComplete = false;
    @Nullable TutorialController mTutorialController = null;
    RootSandboxLayout mRootView;
    View mFingerDotView;
    View mFakePreviousTaskView;
    EdgeBackGestureHandler mEdgeBackGestureHandler;
    NavBarGestureHandler mNavBarGestureHandler;
    private ImageView mEdgeGestureVideoView;

    @Nullable private Animator mGestureAnimation = null;
    @Nullable private AnimatedVectorDrawable mEdgeAnimation = null;
    private boolean mIntroductionShown = false;

    private boolean mFragmentStopped = false;

    private boolean mIsLargeScreen;

    public static TutorialFragment newInstance(TutorialType tutorialType, boolean gestureComplete) {
        TutorialFragment fragment = getFragmentForTutorialType(tutorialType);
        if (fragment == null) {
            fragment = new BackGestureTutorialFragment();
            tutorialType = TutorialType.BACK_NAVIGATION;
        }

        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
        args.putBoolean(KEY_GESTURE_COMPLETE, gestureComplete);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    private static TutorialFragment getFragmentForTutorialType(TutorialType tutorialType) {
        switch (tutorialType) {
            case BACK_NAVIGATION:
            case BACK_NAVIGATION_COMPLETE:
                return new BackGestureTutorialFragment();
            case HOME_NAVIGATION:
            case HOME_NAVIGATION_COMPLETE:
                return new HomeGestureTutorialFragment();
            case OVERVIEW_NAVIGATION:
            case OVERVIEW_NAVIGATION_COMPLETE:
                return new OverviewGestureTutorialFragment();
            case ASSISTANT:
            case ASSISTANT_COMPLETE:
                return new AssistantGestureTutorialFragment();
            case SANDBOX_MODE:
                return new SandboxModeTutorialFragment();
            default:
                Log.e(LOG_TAG, "Failed to find an appropriate fragment for " + tutorialType.name());
        }
        return null;
    }

    @Nullable Integer getEdgeAnimationResId() {
        return null;
    }

    @Nullable
    Animator getGestureAnimation() {
        return mGestureAnimation;
    }

    @Nullable
    AnimatedVectorDrawable getEdgeAnimation() {
        return mEdgeAnimation;
    }


    @Nullable
    protected Animator createGestureAnimation() {
        return null;
    }

    abstract TutorialController createController(TutorialType type);

    abstract Class<? extends TutorialController> getControllerClass();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        mTutorialType = (TutorialType) args.getSerializable(KEY_TUTORIAL_TYPE);
        mGestureComplete = args.getBoolean(KEY_GESTURE_COMPLETE, false);
        mEdgeBackGestureHandler = new EdgeBackGestureHandler(getContext());
        mNavBarGestureHandler = new NavBarGestureHandler(getContext());

        mIsLargeScreen = InvariantDeviceProfile.INSTANCE.get(getContext())
                .getDeviceProfile(getContext()).isTablet;

        if (mIsLargeScreen) {
            ((Activity) getContext()).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        } else {
            // Temporary until UI mocks for landscape mode for phones are created.
            ((Activity) getContext()).setRequestedOrientation(
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    public boolean isLargeScreen() {
        return mIsLargeScreen;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mEdgeBackGestureHandler.unregisterBackGestureAttemptCallback();
        mNavBarGestureHandler.unregisterNavBarGestureAttemptCallback();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mRootView = (RootSandboxLayout) inflater.inflate(
                R.layout.gesture_tutorial_fragment, container, false);
        mRootView.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            mEdgeBackGestureHandler.setInsets(systemInsets.left, systemInsets.right);
            return insets;
        });
        mRootView.setOnTouchListener(this);
        mEdgeGestureVideoView = mRootView.findViewById(R.id.gesture_tutorial_edge_gesture_video);
        mFingerDotView = mRootView.findViewById(R.id.gesture_tutorial_finger_dot);
        mFakePreviousTaskView = mRootView.findViewById(
                R.id.gesture_tutorial_fake_previous_task_view);
        return mRootView;
    }

    @Override
    public void onStop() {
        super.onStop();
        releaseFeedbackAnimation();
        mFragmentStopped = true;
    }

    void initializeFeedbackVideoView() {
        if (!updateFeedbackAnimation() || mTutorialController == null) {
            return;
        }

        if (isGestureComplete()) {
            mTutorialController.showSuccessFeedback();
        } else if (!mIntroductionShown) {
            Integer introTileStringResId = mTutorialController.getIntroductionTitle();
            Integer introSubtitleResId = mTutorialController.getIntroductionSubtitle();
            if (introTileStringResId != null && introSubtitleResId != null) {
                mTutorialController.showFeedback(
                        introTileStringResId, introSubtitleResId, false, true);
                mIntroductionShown = true;
            }
        }
    }

    boolean updateFeedbackAnimation() {
        if (!updateEdgeAnimation()) {
            return false;
        }
        mGestureAnimation = createGestureAnimation();

        if (mGestureAnimation != null) {
            mGestureAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    mFingerDotView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    mFingerDotView.setVisibility(View.GONE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    mFingerDotView.setVisibility(View.GONE);
                }
            });
        }

        return mGestureAnimation != null;
    }

    boolean updateEdgeAnimation() {
        Integer edgeAnimationResId = getEdgeAnimationResId();
        if (edgeAnimationResId == null || getContext() == null) {
            return false;
        }
        mEdgeAnimation = (AnimatedVectorDrawable) getContext().getDrawable(edgeAnimationResId);

        if (mEdgeAnimation != null) {
            mEdgeAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {

                @Override
                public void onAnimationEnd(Drawable drawable) {
                    super.onAnimationEnd(drawable);

                    mEdgeAnimation.start();
                }
            });
        }
        mEdgeGestureVideoView.setImageDrawable(mEdgeAnimation);

        return mEdgeAnimation != null;
    }

    void releaseFeedbackAnimation() {
        if (mTutorialController != null && !mTutorialController.isGestureCompleted()) {
            mTutorialController.cancelQueuedGestureAnimation();
        }
        if (mGestureAnimation != null && mGestureAnimation.isRunning()) {
            mGestureAnimation.cancel();
        }
        if (mEdgeAnimation != null && mEdgeAnimation.isRunning()) {
            mEdgeAnimation.stop();
        }

        mEdgeGestureVideoView.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        releaseFeedbackAnimation();
        if (mFragmentStopped && mTutorialController != null) {
            mTutorialController.showFeedback();
            mFragmentStopped = false;
        } else {
            mRootView.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            changeController(mTutorialType);
                            mRootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    });
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        // Note: Using logical-or to ensure both functions get called.
        return mEdgeBackGestureHandler.onTouch(view, motionEvent)
                | mNavBarGestureHandler.onTouch(view, motionEvent);
    }

    boolean onInterceptTouch(MotionEvent motionEvent) {
        // Note: Using logical-or to ensure both functions get called.
        return mEdgeBackGestureHandler.onInterceptTouch(motionEvent)
                | mNavBarGestureHandler.onInterceptTouch(motionEvent);
    }

    void onAttachedToWindow() {
        mEdgeBackGestureHandler.setViewGroupParent(getRootView());
    }

    void onDetachedFromWindow() {
        mEdgeBackGestureHandler.setViewGroupParent(null);
    }

    void changeController(TutorialType tutorialType) {
        if (getControllerClass().isInstance(mTutorialController)) {
            mTutorialController.setTutorialType(tutorialType);
            mTutorialController.fadeTaskViewAndRun(mTutorialController::transitToController);
        } else {
            mTutorialController = createController(tutorialType);
            mTutorialController.transitToController();
        }
        mEdgeBackGestureHandler.registerBackGestureAttemptCallback(mTutorialController);
        mNavBarGestureHandler.registerNavBarGestureAttemptCallback(mTutorialController);
        mTutorialType = tutorialType;

        initializeFeedbackVideoView();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(KEY_TUTORIAL_TYPE, mTutorialType);
        super.onSaveInstanceState(savedInstanceState);
    }

    RootSandboxLayout getRootView() {
        return mRootView;
    }

    void continueTutorial() {
        GestureSandboxActivity gestureSandboxActivity = getGestureSandboxActivity();

        if (gestureSandboxActivity == null) {
            closeTutorial();
            return;
        }
        gestureSandboxActivity.continueTutorial();
    }

    void closeTutorial() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
        }
    }

    void startSystemNavigationSetting() {
        startActivity(new Intent("com.android.settings.GESTURE_NAVIGATION_SETTINGS"));
    }

    int getCurrentStep() {
        GestureSandboxActivity gestureSandboxActivity = getGestureSandboxActivity();

        return gestureSandboxActivity == null ? -1 : gestureSandboxActivity.getCurrentStep();
    }

    int getNumSteps() {
        GestureSandboxActivity gestureSandboxActivity = getGestureSandboxActivity();

        return gestureSandboxActivity == null ? -1 : gestureSandboxActivity.getNumSteps();
    }

    boolean isAtFinalStep() {
        return getCurrentStep() == getNumSteps();
    }

    boolean isGestureComplete() {
        return mGestureComplete
                || (mTutorialController != null && mTutorialController.isGestureCompleted());
    }

    @Nullable
    private GestureSandboxActivity getGestureSandboxActivity() {
        Context context = getContext();

        return context instanceof GestureSandboxActivity ? (GestureSandboxActivity) context : null;
    }
}
