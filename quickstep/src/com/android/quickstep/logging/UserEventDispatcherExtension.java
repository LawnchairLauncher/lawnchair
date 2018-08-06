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
package com.android.quickstep.logging;

import android.content.Context;
import android.util.Log;

import static com.android.launcher3.logging.LoggerUtils.newLauncherEvent;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ControlType.CANCEL_TARGET;
import static com.android.systemui.shared.system.LauncherEventUtil.VISIBLE;
import static com.android.systemui.shared.system.LauncherEventUtil.DISMISS;
import static com.android.systemui.shared.system.LauncherEventUtil.RECENTS_QUICK_SCRUB_ONBOARDING_TIP;
import static com.android.systemui.shared.system.LauncherEventUtil.RECENTS_SWIPE_UP_ONBOARDING_TIP;

import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.systemui.shared.system.MetricsLoggerCompat;

/**
 * This class handles AOSP MetricsLogger function calls and logging around
 * quickstep interactions.
 */
@SuppressWarnings("unused")
public class UserEventDispatcherExtension extends UserEventDispatcher {

    private static final String TAG = "UserEventDispatcher";

    public UserEventDispatcherExtension(Context context) { }

    public void logStateChangeAction(int action, int dir, int srcChildTargetType,
                                     int srcParentContainerType, int dstContainerType,
                                     int pageIndex) {
        new MetricsLoggerCompat().visibility(MetricsLoggerCompat.OVERVIEW_ACTIVITY,
                dstContainerType == LauncherLogProto.ContainerType.TASKSWITCHER);
        super.logStateChangeAction(action, dir, srcChildTargetType, srcParentContainerType,
                dstContainerType, pageIndex);
    }

    public void logActionTip(int actionType, int viewType) {
        LauncherLogProto.Action action = new LauncherLogProto.Action();
        LauncherLogProto.Target target = new LauncherLogProto.Target();
        switch(actionType) {
            case VISIBLE:
                action.type = LauncherLogProto.Action.Type.TIP;
                target.type = LauncherLogProto.Target.Type.CONTAINER;
                target.containerType = LauncherLogProto.ContainerType.TIP;
                break;
            case DISMISS:
                action.type = LauncherLogProto.Action.Type.TOUCH;
                action.touch = LauncherLogProto.Action.Touch.TAP;
                target.type = LauncherLogProto.Target.Type.CONTROL;
                target.controlType = CANCEL_TARGET;
                break;
            default:
                Log.e(TAG, "Unexpected action type = " + actionType);
        }

        switch(viewType) {
            case RECENTS_QUICK_SCRUB_ONBOARDING_TIP:
                target.tipType = LauncherLogProto.TipType.QUICK_SCRUB_TEXT;
                break;
            case RECENTS_SWIPE_UP_ONBOARDING_TIP:
                target.tipType = LauncherLogProto.TipType.SWIPE_UP_TEXT;
                break;
            default:
                Log.e(TAG, "Unexpected viewType = " + viewType);
        }
        LauncherLogProto.LauncherEvent event = newLauncherEvent(action, target);
        dispatchUserEvent(event, null);
    }
}
