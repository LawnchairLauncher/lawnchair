/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_GESTURE_COMPLETE;
import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_TUTORIAL_TYPE;
import static com.android.quickstep.interaction.GestureSandboxActivity.KEY_USE_TUTORIAL_MENU;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.wm.shell.shared.TypefaceUtils;

/** Displays the gesture nav tutorial menu. */
public final class MenuFragment extends GestureSandboxFragment {

    @NonNull
    @Override
    GestureSandboxFragment recreateFragment() {
        return new MenuFragment();
    }

    @Override
    boolean canRecreateFragment() {
        return true;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        final View root = inflater.inflate(
                R.layout.gesture_tutorial_step_menu, container, false);

        root.findViewById(R.id.gesture_tutorial_menu_home_button).setOnClickListener(
                v -> launchTutorialStep(TutorialController.TutorialType.HOME_NAVIGATION));
        root.findViewById(R.id.gesture_tutorial_menu_back_button).setOnClickListener(
                v -> launchTutorialStep(TutorialController.TutorialType.BACK_NAVIGATION));
        root.findViewById(R.id.gesture_tutorial_menu_overview_button).setOnClickListener(
                v -> launchTutorialStep(TutorialController.TutorialType.OVERVIEW_NAVIGATION));
        root.findViewById(R.id.gesture_tutorial_menu_done_button).setOnClickListener(
                v -> close());

        setPaddings(
                root,
                root.findViewById(R.id.gesture_tutorial_menu_home_button_text),
                root.findViewById(R.id.gesture_tutorial_home_step_shape),
                /* setVerticalMargins= */ true);
        setPaddings(
                root,
                root.findViewById(R.id.gesture_tutorial_menu_back_button_text),
                root.findViewById(R.id.gesture_tutorial_back_step_shape),
                /* setVerticalMargins= */ false);
        setPaddings(
                root,
                root.findViewById(R.id.gesture_tutorial_menu_overview_button_text),
                root.findViewById(R.id.gesture_tutorial_overview_step_shape),
                /* setVerticalMargins= */ true);

        TypefaceUtils.setTypeface(
                root.findViewById(R.id.gesture_tutorial_menu_home_button_text),
                TypefaceUtils.FontFamily.GSF_DISPLAY_SMALL_EMPHASIZED);
        TypefaceUtils.setTypeface(
                root.findViewById(R.id.gesture_tutorial_menu_back_button_text),
                TypefaceUtils.FontFamily.GSF_DISPLAY_SMALL_EMPHASIZED);
        TypefaceUtils.setTypeface(
                root.findViewById(R.id.gesture_tutorial_menu_overview_button_text),
                TypefaceUtils.FontFamily.GSF_DISPLAY_SMALL_EMPHASIZED);
        TypefaceUtils.setTypeface(
                root.findViewById(R.id.gesture_tutorial_menu_done_button),
                TypefaceUtils.FontFamily.GSF_LABEL_LARGE);

        return root;
    }

    private void setPaddings(
            @NonNull View root,
            @NonNull TextView textView,
            @NonNull View shapeImage,
            boolean setVerticalPaddings) {
        if (!root.isAttachedToWindow()) {
            root.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            setPaddings(root, textView, shapeImage, setVerticalPaddings);
                        }
                    });
            return;
        }
        if (setVerticalPaddings) {
            if (textView.getLineCount() <= 1) {
                return;
            }
            // Don't set top padding to allow room for long strings
            textView.setPadding(0, 0, 0, shapeImage.getHeight());
        } else {
            // set both left and right paddings to keep it horizontally-aligned
            textView.setPadding(shapeImage.getWidth(), 0, shapeImage.getWidth(), 0);
        }
    }

    @Override
    boolean shouldDisableSystemGestures() {
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putBoolean(KEY_USE_TUTORIAL_MENU, true);
        savedInstanceState.remove(KEY_TUTORIAL_TYPE);
        savedInstanceState.remove(KEY_GESTURE_COMPLETE);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void launchTutorialStep(@NonNull TutorialController.TutorialType tutorialType) {
        ((GestureSandboxActivity) getActivity()).launchTutorialStep(tutorialType, true);
    }
}
