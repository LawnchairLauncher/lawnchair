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

import android.os.Bundle;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureAttemptCallback;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.TutorialController.TutorialType;

/** Shows the Back gesture interactive tutorial. */
public class BackGestureTutorialFragment extends TutorialFragment
        implements BackGestureAttemptCallback {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEdgeBackGestureHandler.registerBackGestureAttemptCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mEdgeBackGestureHandler.unregisterBackGestureAttemptCallback();
    }

    @Override
    int getHandAnimationResId() {
        return R.drawable.back_gesture;
    }

    @Override
    TutorialController createController(TutorialType type) {
        return new BackGestureTutorialController(this, type);
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (mTutorialController != null) {
            mTutorialController.onBackGestureAttempted(result);
        }
    }
}
