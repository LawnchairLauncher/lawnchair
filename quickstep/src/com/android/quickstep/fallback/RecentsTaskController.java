/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep.fallback;

import com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController;
import com.android.quickstep.RecentsActivity;

public class RecentsTaskController extends TaskViewTouchController<RecentsActivity> {

    public RecentsTaskController(RecentsActivity activity) {
        super(activity);
    }

    @Override
    protected boolean isRecentsInteractive() {
        return mActivity.hasWindowFocus() || mActivity.getStateManager().getState().hasLiveTile();
    }

    @Override
    protected boolean isRecentsModal() {
        return false;
    }
}
