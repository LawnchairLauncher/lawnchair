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

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.android.launcher3.R;

import java.net.URISyntaxException;
import java.util.Optional;

/** Shows the Back gesture interactive tutorial. */
public class BackGestureTutorialFragment extends Fragment {

    private static final String LOG_TAG = "TutorialFragment";
    private static final String KEY_TUTORIAL_STEP = "tutorialStep";
    private static final String KEY_TUTORIAL_TYPE = "tutorialType";
    private static final String SYSTEM_NAVIGATION_SETTING_INTENT =
            "#Intent;action=com.android.settings.SEARCH_RESULT_TRAMPOLINE;S"
                    + ".:settings:fragment_args_key=gesture_system_navigation_input_summary;S"
                    + ".:settings:show_fragment=com.android.settings.gestures"
                    + ".SystemNavigationGestureSettings;end";

    private TutorialStep mTutorialStep;
    private TutorialType mTutorialType;
    private Optional<BackGestureTutorialController> mTutorialController = Optional.empty();
    private View mRootView;
    private BackGestureTutorialHandAnimation mHandCoachingAnimation;

    public static BackGestureTutorialFragment newInstance(
            TutorialStep tutorialStep, TutorialType tutorialType) {
        BackGestureTutorialFragment fragment = new BackGestureTutorialFragment();
        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_STEP, tutorialStep);
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        mTutorialStep = (TutorialStep) args.getSerializable(KEY_TUTORIAL_STEP);
        mTutorialType = (TutorialType) args.getSerializable(KEY_TUTORIAL_TYPE);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mRootView = inflater.inflate(R.layout.back_gesture_tutorial_fragment,
                container, /* attachToRoot= */ false);
        mRootView.findViewById(R.id.back_gesture_tutorial_fragment_close_button)
                .setOnClickListener(this::onCloseButtonClicked);
        mHandCoachingAnimation = new BackGestureTutorialHandAnimation(getContext(), mRootView);

        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        changeController(mTutorialStep, mTutorialType);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandCoachingAnimation.stop();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(KEY_TUTORIAL_STEP, mTutorialStep);
        savedInstanceState.putSerializable(KEY_TUTORIAL_TYPE, mTutorialType);
        super.onSaveInstanceState(savedInstanceState);
    }

    View getRootView() {
        return mRootView;
    }

    BackGestureTutorialHandAnimation getHandAnimation() {
        return mHandCoachingAnimation;
    }

    void changeController(TutorialStep tutorialStep) {
        changeController(tutorialStep, mTutorialType);
    }

    void changeController(TutorialStep tutorialStep, TutorialType tutorialType) {
        Optional<BackGestureTutorialController> tutorialController =
                BackGestureTutorialController.getTutorialController(/* fragment= */ this,
                        tutorialStep, tutorialType);
        if (!tutorialController.isPresent()) {
            return;
        }

        mTutorialController = tutorialController;
        mTutorialController.get().transitToController();
        this.mTutorialStep = mTutorialController.get().mTutorialStep;
        this.mTutorialType = tutorialType;
    }

    void onBackPressed() {
        if (mTutorialController.isPresent()) {
            mTutorialController.get().onGestureDetected();
        }
    }

    void closeTutorial() {
        getActivity().finish();
    }

    void startSystemNavigationSetting() {
        try {
            startActivityForResult(
                    Intent.parseUri(SYSTEM_NAVIGATION_SETTING_INTENT, /* flags= */ 0),
                    /* requestCode= */ 0);
        } catch (URISyntaxException e) {
            Log.e(LOG_TAG, "The launch Intent Uri is wrong syntax: " + e);
        } catch (ActivityNotFoundException e) {
            Log.e(LOG_TAG, "The launch Activity not found: " + e);
        }
    }

    private void onCloseButtonClicked(View button) {
        closeTutorial();
    }

    /** Denotes the step of the tutorial. */
    enum TutorialStep {
        ENGAGED,
        CONFIRM,
    }

    /** Denotes the type of the tutorial. */
    enum TutorialType {
        RIGHT_EDGE_BACK_NAVIGATION,
        LEFT_EDGE_BACK_NAVIGATION,
    }
}
