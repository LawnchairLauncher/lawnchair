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
import android.graphics.Insets;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.R;
import com.android.quickstep.interaction.TutorialController.TutorialType;

import java.net.URISyntaxException;

abstract class TutorialFragment extends Fragment {

    private static final String LOG_TAG = "TutorialFragment";
    private static final String SYSTEM_NAVIGATION_SETTING_INTENT =
            "#Intent;action=com.android.settings.SEARCH_RESULT_TRAMPOLINE;S"
                    + ".:settings:fragment_args_key=gesture_system_navigation_input_summary;S"
                    + ".:settings:show_fragment=com.android.settings.gestures"
                    + ".SystemNavigationGestureSettings;end";
    private static final String KEY_TUTORIAL_TYPE = "tutorialType";

    TutorialType mTutorialType;
    @Nullable TutorialController mTutorialController = null;
    View mRootView;
    TutorialHandAnimation mHandCoachingAnimation;
    EdgeBackGestureHandler mEdgeBackGestureHandler;

    public static TutorialFragment newInstance(
            Class<? extends TutorialFragment> fragmentClass, TutorialType tutorialType)
            throws java.lang.InstantiationException, IllegalAccessException {
        TutorialFragment fragment = fragmentClass.newInstance();
        Bundle args = new Bundle();
        args.putSerializable(KEY_TUTORIAL_TYPE, tutorialType);
        fragment.setArguments(args);
        return fragment;
    }

    abstract int getHandAnimationResId();

    abstract TutorialController createController(TutorialType type);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = savedInstanceState != null ? savedInstanceState : getArguments();
        mTutorialType = (TutorialType) args.getSerializable(KEY_TUTORIAL_TYPE);
        mEdgeBackGestureHandler = new EdgeBackGestureHandler(getContext());
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mRootView = inflater.inflate(R.layout.gesture_tutorial_fragment,
                container, /* attachToRoot= */ false);
        mRootView.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets systemInsets = insets.getInsets(WindowInsets.Type.systemBars());
            mEdgeBackGestureHandler.setInsets(systemInsets.left, systemInsets.right);
            return insets;
        });
        mRootView.setOnTouchListener(mEdgeBackGestureHandler);
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

    void onAttachedToWindow() {
        mEdgeBackGestureHandler.setViewGroupParent((ViewGroup) getRootView());
    }

    void onDetachedFromWindow() {
        mEdgeBackGestureHandler.setViewGroupParent(null);
    }

    void changeController(TutorialType tutorialType) {
        mTutorialController = createController(tutorialType);
        mTutorialController.transitToController();
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

}
