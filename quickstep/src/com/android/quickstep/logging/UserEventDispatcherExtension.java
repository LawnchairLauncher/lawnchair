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

import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.systemui.shared.system.MetricsLoggerCompat;

/**
 * This class handles AOSP MetricsLogger function calls.
 */
public class UserEventDispatcherExtension extends UserEventDispatcher {

    public void logStateChangeAction(int action, int dir, int srcChildTargetType,
                                     int srcParentContainerType, int dstContainerType,
                                     int pageIndex) {
        new MetricsLoggerCompat().visibility(MetricsLoggerCompat.OVERVIEW_ACTIVITY,
                dstContainerType == LauncherLogProto.ContainerType.TASKSWITCHER);
        super.logStateChangeAction(action, dir, srcChildTargetType, srcParentContainerType,
                dstContainerType, pageIndex);
    }
}
