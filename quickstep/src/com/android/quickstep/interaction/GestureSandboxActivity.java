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

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.interaction.TutorialController.TutorialType;
import com.android.quickstep.util.TISBindHelper;

import java.util.Arrays;

/** Shows the gesture interactive sandbox in full screen mode. */
public class GestureSandboxActivity extends FragmentActivity {

    private static final String KEY_TUTORIAL_STEPS = "tutorial_steps";
    private static final String KEY_CURRENT_STEP = "current_step";
    static final String KEY_TUTORIAL_TYPE = "tutorial_type";
    static final String KEY_GESTURE_COMPLETE = "gesture_complete";
    static final String KEY_USE_TUTORIAL_MENU = "use_tutorial_menu";

    @Nullable private TutorialType[] mTutorialSteps;
    private GestureSandboxFragment mFragment;

    private int mCurrentStep;
    private int mNumSteps;
    private boolean mShowRotationPrompt;

    private SharedPreferences mSharedPrefs;
    private StatsLogManager mStatsLogManager;

    private View mRotationPrompt;
    private TISBindHelper mTISBindHelper;
    private TISBinder mBinder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.gesture_tutorial_activity);

        mSharedPrefs = LauncherPrefs.getPrefs(this);
        mStatsLogManager = StatsLogManager.newInstance(getApplicationContext());

        Bundle args = savedInstanceState == null ? getIntent().getExtras() : savedInstanceState;

        boolean gestureComplete = args != null && args.getBoolean(KEY_GESTURE_COMPLETE, false);
        if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                && args != null
                && args.getBoolean(KEY_USE_TUTORIAL_MENU, false)) {
            mTutorialSteps = null;
            TutorialType tutorialTypeOverride = (TutorialType) args.get(KEY_TUTORIAL_TYPE);
            mFragment = tutorialTypeOverride == null
                    ? new MenuFragment()
                    : makeTutorialFragment(
                            tutorialTypeOverride,
                            gestureComplete,
                            /* fromMenu= */ true);
        } else {
            mTutorialSteps = getTutorialSteps(args);
            mFragment = makeTutorialFragment(
                    mTutorialSteps[mCurrentStep - 1],
                    gestureComplete,
                    /* fromMenu= */ false);
        }
        getSupportFragmentManager().beginTransaction()
                .add(R.id.gesture_tutorial_fragment_container, mFragment)
                .commit();

        mRotationPrompt = findViewById(R.id.rotation_prompt);
        if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            correctUserOrientation();
        }
        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Ensure the prompt to rotate the screen is updated
        if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            correctUserOrientation();
        }
    }

    /**
     * Gesture animations are only in landscape for large screens and portrait for mobile. This
     * method enforces the following flows:
     *     1) phone / two-panel closed -> lock to portrait
     *     2) two-panel open / tablet + portrait -> prompt the user to rotate the screen
     *     3) two-panel open / tablet + landscape -> hide potential rotating prompt
     */
    private void correctUserOrientation() {
        DeviceProfile deviceProfile = InvariantDeviceProfile.INSTANCE.get(
                getApplicationContext()).getDeviceProfile(this);
        if (deviceProfile.isTablet) {
            mShowRotationPrompt = getResources().getConfiguration().orientation
                    == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            updateVisibility(mRotationPrompt, mShowRotationPrompt ? View.VISIBLE : View.GONE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    void updateVisibility(View view, int visibility) {
        if (view == null || view.getVisibility() == visibility) {
            return;
        }
        view.setVisibility(visibility);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mFragment.shouldDisableSystemGestures()) {
            disableSystemGestures();
        }
        mFragment.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFragment.onDetachedFromWindow();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        savedInstanceState.putStringArray(KEY_TUTORIAL_STEPS, getTutorialStepNames());
        savedInstanceState.putInt(KEY_CURRENT_STEP, mCurrentStep);
        mFragment.onSaveInstanceState(savedInstanceState);
        super.onSaveInstanceState(savedInstanceState);
    }

    protected boolean isRotationPromptShowing() {
        return mShowRotationPrompt;
    }

    protected SharedPreferences getSharedPrefs() {
        return mSharedPrefs;
    }

    protected StatsLogManager getStatsLogManager() {
        return mStatsLogManager;
    }

    /** Returns true iff there aren't anymore tutorial types to display to the user. */
    public boolean isTutorialComplete() {
        return mCurrentStep >= mNumSteps;
    }

    public int getCurrentStep() {
        return mCurrentStep;
    }

    public int getNumSteps() {
        return mNumSteps;
    }

    /**
     * Replaces the current TutorialFragment, continuing to the next tutorial step if there is one.
     *
     * If there is no following step, the tutorial is closed.
     */
    public void continueTutorial() {
        if (isTutorialComplete() || mTutorialSteps == null) {
            mFragment.close();
            return;
        }
        launchTutorialStep(mTutorialSteps[mCurrentStep], false);
        mCurrentStep++;
    }

    private TutorialFragment makeTutorialFragment(
            @NonNull TutorialType tutorialType, boolean gestureComplete, boolean fromMenu) {
        return TutorialFragment.newInstance(tutorialType, gestureComplete, fromMenu);
    }

    /**
     * Launches the given gesture nav tutorial step.
     *
     * If the step is being launched from the gesture nav tutorial menu, then that step will launch
     * the menu when complete.
     */
    public void launchTutorialStep(@NonNull TutorialType tutorialType, boolean fromMenu) {
        mFragment = makeTutorialFragment(tutorialType, false, fromMenu);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.gesture_tutorial_fragment_container, mFragment)
                .runOnCommit(() -> mFragment.onAttachedToWindow())
                .commit();
    }

    /** Launches the gesture nav tutorial menu page */
    public void launchTutorialMenu() {
        mFragment = new MenuFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.gesture_tutorial_fragment_container, mFragment)
                .runOnCommit(() -> mFragment.onAttachedToWindow())
                .commit();
    }

    private String[] getTutorialStepNames() {
        if (mTutorialSteps == null) {
            return new String[0];
        }
        String[] tutorialStepNames = new String[mTutorialSteps.length];

        int i = 0;
        for (TutorialType tutorialStep : mTutorialSteps) {
            tutorialStepNames[i++] = tutorialStep.name();
        }

        return tutorialStepNames;
    }

    private TutorialType[] getTutorialSteps(Bundle extras) {
        TutorialType[] defaultSteps = new TutorialType[] {
                TutorialType.HOME_NAVIGATION,
                TutorialType.BACK_NAVIGATION,
                TutorialType.OVERVIEW_NAVIGATION};
        mCurrentStep = 1;
        mNumSteps = defaultSteps.length;

        if (extras == null || !extras.containsKey(KEY_TUTORIAL_STEPS)) {
            return defaultSteps;
        }

        String[] savedStepsNames;
        Object savedSteps = extras.get(KEY_TUTORIAL_STEPS);
        if (savedSteps instanceof String) {
            savedStepsNames = TextUtils.isEmpty((String) savedSteps)
                    ? null : ((String) savedSteps).split(",");
        } else if (savedSteps instanceof String[]) {
            savedStepsNames = (String[]) savedSteps;
        } else {
            return defaultSteps;
        }

        if (savedStepsNames == null || savedStepsNames.length == 0) {
            return defaultSteps;
        }

        TutorialType[] tutorialSteps = new TutorialType[savedStepsNames.length];
        for (int i = 0; i < savedStepsNames.length; i++) {
            tutorialSteps[i] = TutorialType.valueOf(savedStepsNames[i]);
        }

        mCurrentStep = Math.max(extras.getInt(KEY_CURRENT_STEP, -1), 1);
        mNumSteps = tutorialSteps.length;

        return tutorialSteps;
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void disableSystemGestures() {
        Display display = getDisplay();
        if (display != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            getWindow().setSystemGestureExclusionRects(
                    Arrays.asList(new Rect(0, 0, metrics.widthPixels, metrics.heightPixels)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceState(true);
    }

    private void onTISConnected(TISBinder binder) {
        mBinder = binder;
        updateServiceState(isResumed());
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateServiceState(false);
    }

    private void updateServiceState(boolean isEnabled) {
        if (mBinder != null) {
            mBinder.setGestureBlockedTaskId(isEnabled ? getTaskId() : -1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTISBindHelper.onDestroy();
        updateServiceState(false);
    }
}
