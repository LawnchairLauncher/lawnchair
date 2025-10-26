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

import static com.android.launcher3.Flags.enableExpressiveDismissTaskMotion;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewDismissTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewLaunchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewRecentsTouchContext;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchControllerDeprecated;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.views.RecentsViewContainer;

/**
 * Drag layer for fallback recents activity
 */
public class RecentsDragLayer<T extends Context & RecentsViewContainer
        & StatefulContainer<RecentsState>> extends BaseDragLayer<T> {

    private final TaskViewRecentsTouchContext mTaskViewRecentsTouchContext =
            new TaskViewRecentsTouchContext() {
                @Override
                public boolean isRecentsInteractive() {
                    return mContainer.getRootView().hasWindowFocus()
                            || mContainer.getStateManager().getState().hasLiveTile();
                }

                @Override
                public boolean isRecentsModal() {
                    return false;
                }
            };

    public RecentsDragLayer(Context context, AttributeSet attrs) {
        super(context, attrs, 1 /* alphaChannelCount */);
    }

    @Override
    public void recreateControllers() {
        mControllers = enableExpressiveDismissTaskMotion()
                ? new TouchController[]{
                        new TaskViewLaunchTouchController<>(mContainer,
                                mTaskViewRecentsTouchContext),
                        new TaskViewDismissTouchController<>(mContainer,
                                mTaskViewRecentsTouchContext),
                        new FallbackNavBarTouchController(mContainer)
                }
                : new TouchController[]{
                        new TaskViewTouchControllerDeprecated<>(mContainer,
                                mTaskViewRecentsTouchContext),
                        new FallbackNavBarTouchController(mContainer)
                };
    }
}
