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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.view.WindowInsets;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.interaction.TutorialController.TutorialType;

abstract class TutorialFragment extends Fragment implements OnTouchListener {

    private static final String LOG_TAG = "TutorialFragment";
    static final String KEY_TUTORIAL_TYPE = "tutorial_type";

    TutorialType mTutorialType;
    @Nullable TutorialController mTutorialController = null;
    RootSandboxLayout mRootView;
    EdgeBackGestureHandler mEdgeBackGestureHandler;
    NavBarGestureHandler mNavBarGestureHandler;
    private ImageView mFeedbackVideoView;
    private ImageView mGestureVideoView;

    @Nullable private AnimatedVectorDrawable mTutorialAnimation = null;
    @Nullable private AnimatedVectorDrawable mGestureAnimation = null;
    private boolean mIntroductionShown = false;

    private boolean mFragmentStopped = false;

    public static TutorialFragment newInstance(TutorialType tutorialType) {
        TutorialFragment fragment = getFragmentForTutorialType(tutorialType);
        if (fragment == null) {
            fragment = new BackGestureTutorialFragment();
            tutorialType = TutorialType.BACK_NAVIGATION;
        }

        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
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

    @Nullable Integer getFeedbackVideoResId(boolean forDarkMode) {
        return null;
    }

    @Nullable Integer getGestureVideoResId() {
        return null;
    }

    @Nullable
    AnimatedVectorDrawable getTutorialAnimation() {
        return mTutorialAnimation;
    }

    @Nullable
    AnimatedVectorDrawable getGestureAnimation() {
        return mGestureAnimation;
    }

    abstract TutorialController createController(TutorialType type);

    abstract Class<? extends TutorialController> getControllerClass();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        mTutorialType = (TutorialType) args.getSerializable(KEY_TUTORIAL_TYPE);
        mEdgeBackGestureHandler = new EdgeBackGestureHandler(getContext());
        mNavBarGestureHandler = new NavBarGestureHandler(getContext());
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
        mFeedbackVideoView = mRootView.findViewById(R.id.gesture_tutorial_feedback_video);
        mGestureVideoView = mRootView.findViewById(R.id.gesture_tutorial_gesture_video);
        return mRootView;
    }

    @Override
    public void onStop() {
        super.onStop();
        releaseFeedbackVideoView();
        releaseGestureVideoView();
        mFragmentStopped = true;
    }

    void initializeFeedbackVideoView() {
        if (!updateFeedbackVideo()) {
            return;
        }

        if (!mIntroductionShown && mTutorialController != null) {
            Integer introTileStringResId = mTutorialController.getIntroductionTitle();
            Integer introSubtitleResId = mTutorialController.getIntroductionSubtitle();
            if (introTileStringResId != null && introSubtitleResId != null) {
                mTutorialController.showFeedback(
                        introTileStringResId, introSubtitleResId, false, true);
                mIntroductionShown = true;
            }
        }
    }

    boolean updateFeedbackVideo() {
        if (getContext() == null) {
            return false;
        }
        Integer feedbackVideoResId = getFeedbackVideoResId(Utilities.isDarkTheme(getContext()));

        if (feedbackVideoResId == null || !updateGestureVideo()) {
            return false;
        }
        mTutorialAnimation = (AnimatedVectorDrawable) getContext().getDrawable(feedbackVideoResId);

        if (mTutorialAnimation != null) {
            mTutorialAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {

                @Override
                public void onAnimationStart(Drawable drawable) {
                    super.onAnimationStart(drawable);

                    mFeedbackVideoView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Drawable drawable) {
                    super.onAnimationEnd(drawable);

                    releaseFeedbackVideoView();
                }
            });
        }
        mFeedbackVideoView.setImageDrawable(mTutorialAnimation);

        return true;
    }

    boolean updateGestureVideo() {
        Integer gestureVideoResId = getGestureVideoResId();
        if (gestureVideoResId == null || getContext() == null) {
            return false;
        }
        mGestureAnimation = (AnimatedVectorDrawable) getContext().getDrawable(gestureVideoResId);

        if (mGestureAnimation != null) {
            mGestureAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {

                @Override
                public void onAnimationEnd(Drawable drawable) {
                    super.onAnimationEnd(drawable);

                    mGestureAnimation.start();
                }
            });
        }
        mGestureVideoView.setImageDrawable(mGestureAnimation);

        return true;
    }

    void releaseFeedbackVideoView() {
        if (mTutorialAnimation != null && mTutorialAnimation.isRunning()) {
            mTutorialAnimation.stop();
        }

        mFeedbackVideoView.setVisibility(View.GONE);
    }

    void releaseGestureVideoView() {
        if (mGestureAnimation != null && mGestureAnimation.isRunning()) {
            mGestureAnimation.stop();
        }

        mGestureVideoView.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFragmentStopped && mTutorialController != null) {
            mTutorialController.showFeedback();
            mFragmentStopped = false;
        } else {
            changeController(mTutorialType);
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

    @Nullable
    private GestureSandboxActivity getGestureSandboxActivity() {
        Context context = getContext();

        return context instanceof GestureSandboxActivity ? (GestureSandboxActivity) context : null;
    }
}
