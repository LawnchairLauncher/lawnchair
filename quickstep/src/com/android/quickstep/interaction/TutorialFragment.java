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

import android.content.Intent;
import android.graphics.Insets;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.R;
import com.android.quickstep.interaction.TutorialController.TutorialType;

abstract class TutorialFragment extends Fragment implements OnTouchListener {

    private static final String LOG_TAG = "TutorialFragment";
    static final String KEY_TUTORIAL_TYPE = "tutorial_type";

    TutorialType mTutorialType;
    @Nullable TutorialController mTutorialController = null;
    View mRootView;
    TutorialHandAnimation mHandCoachingAnimation;
    EdgeBackGestureHandler mEdgeBackGestureHandler;
    NavBarGestureHandler mNavBarGestureHandler;

    public static TutorialFragment newInstance(TutorialType tutorialType) {
        TutorialFragment fragment = getFragmentForTutorialType(tutorialType);
        if (fragment == null) {
            fragment = new BackGestureTutorialFragment();
            tutorialType = TutorialType.RIGHT_EDGE_BACK_NAVIGATION;
        }
        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    private static TutorialFragment getFragmentForTutorialType(TutorialType tutorialType) {
        switch (tutorialType) {
            case RIGHT_EDGE_BACK_NAVIGATION:
            case LEFT_EDGE_BACK_NAVIGATION:
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
            default:
                Log.e(LOG_TAG, "Failed to find an appropriate fragment for " + tutorialType.name());
        }
        return null;
    }

    abstract int getHandAnimationResId();

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

        mRootView = inflater.inflate(R.layout.gesture_tutorial_fragment, container, false);
        mRootView.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            mEdgeBackGestureHandler.setInsets(systemInsets.left, systemInsets.right);
            return insets;
        });
        mRootView.setOnTouchListener(this);
        mHandCoachingAnimation = new TutorialHandAnimation(getContext(), mRootView,
                getHandAnimationResId());
        return mRootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        changeController(mTutorialType);
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandCoachingAnimation.stop();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        // Note: Using logical or to ensure both functions get called.
        return mEdgeBackGestureHandler.onTouch(view, motionEvent)
                | mNavBarGestureHandler.onTouch(view, motionEvent);
    }

    void onAttachedToWindow() {
        mEdgeBackGestureHandler.setViewGroupParent((ViewGroup) getRootView());
    }

    void onDetachedFromWindow() {
        mEdgeBackGestureHandler.setViewGroupParent(null);
    }

    void changeController(TutorialType tutorialType) {
        if (getControllerClass().isInstance(mTutorialController)) {
            mTutorialController.setTutorialType(tutorialType);
        } else {
            mTutorialController = createController(tutorialType);
        }
        mTutorialController.transitToController();
        mEdgeBackGestureHandler.registerBackGestureAttemptCallback(mTutorialController);
        mNavBarGestureHandler.registerNavBarGestureAttemptCallback(mTutorialController);
        mTutorialType = tutorialType;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable(KEY_TUTORIAL_TYPE, mTutorialType);
        super.onSaveInstanceState(savedInstanceState);
    }

    View getRootView() {
        return mRootView;
    }

    TutorialHandAnimation getHandAnimation() {
        return mHandCoachingAnimation;
    }

    void closeTutorial() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    void startSystemNavigationSetting() {
        startActivity(new Intent("com.android.settings.GESTURE_NAVIGATION_SETTINGS"));
    }
}
