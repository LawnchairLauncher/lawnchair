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

import static com.android.quickstep.interaction.TutorialFragment.KEY_TUTORIAL_TYPE;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.Window;

import androidx.fragment.app.FragmentActivity;

import com.android.launcher3.R;
import com.android.quickstep.interaction.TutorialController.TutorialType;

import java.util.List;

/** Shows the gesture interactive sandbox in full screen mode. */
public class GestureSandboxActivity extends FragmentActivity {

    private static final String LOG_TAG = "GestureSandboxActivity";

    private TutorialFragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.gesture_tutorial_activity);

        mFragment = TutorialFragment.newInstance(getTutorialType(getIntent().getExtras()));
        getSupportFragmentManager().beginTransaction()
                .add(R.id.gesture_tutorial_fragment_container, mFragment)
                .commit();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        disableSystemGestures();
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

    private TutorialType getTutorialType(Bundle extras) {
        TutorialType defaultType = TutorialType.RIGHT_EDGE_BACK_NAVIGATION;

        if (extras == null || !extras.containsKey(KEY_TUTORIAL_TYPE)) {
            return defaultType;
        }
        try {
            return TutorialType.valueOf(extras.getString(KEY_TUTORIAL_TYPE, ""));
        } catch (IllegalArgumentException e) {
            return defaultType;
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
    }

    private void disableSystemGestures() {
        Display display = getDisplay();
        if (display != null) {
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            getWindow().setSystemGestureExclusionRects(
                    List.of(new Rect(0, 0, metrics.widthPixels, metrics.heightPixels)));
        }
    }
}
