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

import static android.view.View.NO_ID;

import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;
import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_GESTURE_COMPLETE;
import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_TUTORIAL_TYPE;
import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_USE_TUTORIAL_MENU;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.ArraySet;
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

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.interaction.TutorialController.TutorialType;

import java.util.Set;

/** Displays a gesture nav tutorial step. */
abstract class TutorialFragment extends GestureSandboxFragment implements OnTouchListener {

    private static final String LOG_TAG = "TutorialFragment";

    private static final String TUTORIAL_SKIPPED_PREFERENCE_KEY = "pref_gestureTutorialSkipped";
    private static final String COMPLETED_TUTORIAL_STEPS_PREFERENCE_KEY =
            "pref_completedTutorialSteps";

    private final boolean mFromTutorialMenu;

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

    private DeviceProfile mDeviceProfile;
    private boolean mIsLargeScreen;
    private boolean mIsFoldable;

    public static TutorialFragment newInstance(
            TutorialType tutorialType, boolean gestureComplete, boolean fromTutorialMenu) {
        TutorialFragment fragment = getFragmentForTutorialType(tutorialType, fromTutorialMenu);
        if (fragment == null) {
            fragment = new BackGestureTutorialFragment(fromTutorialMenu);
            tutorialType = TutorialType.BACK_NAVIGATION;
        }

        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
        args.putBoolean(KEY_GESTURE_COMPLETE, gestureComplete);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    GestureSandboxFragment recreateFragment() {
        TutorialType tutorialType = mTutorialController == null
                ? (mTutorialType == null
                        ? getDefaultTutorialType() : mTutorialType)
                : mTutorialController.mTutorialType;
        return newInstance(tutorialType, isGestureComplete(), mFromTutorialMenu);
    }

    @NonNull
    abstract TutorialType getDefaultTutorialType();

    TutorialFragment(boolean fromTutorialMenu) {
        mFromTutorialMenu = fromTutorialMenu;
    }

    @Nullable
    private static TutorialFragment getFragmentForTutorialType(
            TutorialType tutorialType, boolean fromTutorialMenu) {
        switch (tutorialType) {
            case BACK_NAVIGATION:
            case BACK_NAVIGATION_COMPLETE:
                return new BackGestureTutorialFragment(fromTutorialMenu);
            case HOME_NAVIGATION:
            case HOME_NAVIGATION_COMPLETE:
                return new HomeGestureTutorialFragment(fromTutorialMenu);
            case OVERVIEW_NAVIGATION:
            case OVERVIEW_NAVIGATION_COMPLETE:
                return new OverviewGestureTutorialFragment(fromTutorialMenu);
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

    @NonNull
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

        mDeviceProfile = InvariantDeviceProfile.INSTANCE.get(getContext())
                .getDeviceProfile(getContext());
        mIsLargeScreen = mDeviceProfile.isTablet;
        mIsFoldable = mDeviceProfile.isTwoPanels;
    }

    public boolean isLargeScreen() {
        return mIsLargeScreen;
    }

    public boolean isFoldable() {
        return mIsFoldable;
    }

    DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
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
                ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                        ? R.layout.redesigned_gesture_tutorial_fragment
                        : R.layout.gesture_tutorial_fragment,
                container,
                false);

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
            int introTitleResId = mTutorialController.getIntroductionTitle();
            int introSubtitleResId = mTutorialController.getIntroductionSubtitle();
            if (introTitleResId == NO_ID) {
                // Allow crash since this should never be reached with a tutorial controller used in
                // production.
                Log.e(LOG_TAG,
                        "Cannot show introduction feedback for tutorial step: " + mTutorialType
                                + ", no introduction feedback title",
                        new IllegalStateException());
            }
            if (introTitleResId == NO_ID) {
                // Allow crash since this should never be reached with a tutorial controller used in
                // production.
                Log.e(LOG_TAG,
                        "Cannot show introduction feedback for tutorial step: " + mTutorialType
                                + ", no introduction feedback subtitle",
                        new IllegalStateException());
            }
            mTutorialController.showFeedback(
                    introTitleResId,
                    introSubtitleResId,
                    mTutorialController.getSpokenIntroductionSubtitle(),
                    false,
                    true);
            mIntroductionShown = true;
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
        if (mTutorialController != null && !isGestureComplete()) {
            mTutorialController.hideFeedback();
        }

        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mTutorialController.pauseAndHideLottieAnimation();
        }

        // Note: Using logical-or to ensure both functions get called.
        return mEdgeBackGestureHandler.onTouch(view, motionEvent)
                | mNavBarGestureHandler.onTouch(view, motionEvent);
    }

    boolean onInterceptTouch(MotionEvent motionEvent) {
        // Note: Using logical-or to ensure both functions get called.
        return mEdgeBackGestureHandler.onInterceptTouch(motionEvent)
                | mNavBarGestureHandler.onInterceptTouch(motionEvent);
    }

    @Override
    void onAttachedToWindow() {
        StatsLogManager statsLogManager = getStatsLogManager();
        if (statsLogManager != null) {
            logTutorialStepShown(statsLogManager);
        }
        mEdgeBackGestureHandler.setViewGroupParent(getRootView());
    }

    @Override
    void onDetachedFromWindow() {
        mEdgeBackGestureHandler.setViewGroupParent(null);
    }

    void changeController(TutorialType tutorialType) {
        if (getControllerClass().isInstance(mTutorialController)) {
            mTutorialController.setTutorialType(tutorialType);
            if (isGestureComplete()) {
                mTutorialController.setGestureCompleted();
            }
            mTutorialController.fadeTaskViewAndRun(mTutorialController::transitToController);
        } else {
            mTutorialController = createController(tutorialType);
            if (isGestureComplete()) {
                mTutorialController.setGestureCompleted();
            }
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
        savedInstanceState.putBoolean(KEY_GESTURE_COMPLETE, isGestureComplete());
        savedInstanceState.putBoolean(KEY_USE_TUTORIAL_MENU, mFromTutorialMenu);
        super.onSaveInstanceState(savedInstanceState);
    }

    RootSandboxLayout getRootView() {
        return mRootView;
    }

    void continueTutorial() {
        SharedPreferences sharedPrefs = getSharedPreferences();
        if (sharedPrefs != null) {
            Set<String> updatedCompletedSteps = new ArraySet<>(sharedPrefs.getStringSet(
                    COMPLETED_TUTORIAL_STEPS_PREFERENCE_KEY, new ArraySet<>()));

            updatedCompletedSteps.add(mTutorialType.toString());

            sharedPrefs.edit().putStringSet(
                    COMPLETED_TUTORIAL_STEPS_PREFERENCE_KEY, updatedCompletedSteps).apply();
        }
        StatsLogManager statsLogManager = getStatsLogManager();
        if (statsLogManager != null) {
            logTutorialStepCompleted(statsLogManager);
        }

        GestureSandboxActivity gestureSandboxActivity = getGestureSandboxActivity();
        if (gestureSandboxActivity == null) {
            close();
            return;
        }
        gestureSandboxActivity.continueTutorial();
    }

    @Override
    void close() {
        closeTutorialStep(false);
    }

    void closeTutorialStep(boolean tutorialSkipped) {
        if (tutorialSkipped) {
            SharedPreferences sharedPrefs = getSharedPreferences();
            if (sharedPrefs != null) {
                sharedPrefs.edit().putBoolean(TUTORIAL_SKIPPED_PREFERENCE_KEY, true).apply();
            }
            StatsLogManager statsLogManager = getStatsLogManager();
            if (statsLogManager != null) {
                statsLogManager.logger().log(
                        StatsLogManager.LauncherEvent.LAUNCHER_GESTURE_TUTORIAL_SKIPPED);
            }
        }
        GestureSandboxActivity gestureSandboxActivity = getGestureSandboxActivity();
        if (mFromTutorialMenu && gestureSandboxActivity != null) {
            gestureSandboxActivity.launchTutorialMenu();
            return;
        }
        super.close();
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

    abstract void logTutorialStepShown(@NonNull StatsLogManager statsLogManager);

    abstract void logTutorialStepCompleted(@NonNull StatsLogManager statsLogManager);

    @Nullable
    private GestureSandboxActivity getGestureSandboxActivity() {
        Activity activity = getActivity();

        return activity instanceof GestureSandboxActivity
                ? (GestureSandboxActivity) activity : null;
    }

    @Nullable
    private StatsLogManager getStatsLogManager() {
        GestureSandboxActivity activity = getGestureSandboxActivity();

        return activity != null ? activity.getStatsLogManager() : null;
    }

    @Nullable
    private SharedPreferences getSharedPreferences() {
        GestureSandboxActivity activity = getGestureSandboxActivity();

        return activity != null ? activity.getSharedPrefs() : null;
    }
}
