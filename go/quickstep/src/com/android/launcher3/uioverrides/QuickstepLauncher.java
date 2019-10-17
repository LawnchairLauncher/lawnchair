/**
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeEdgeSwipeController;
import com.android.launcher3.uioverrides.touchcontrollers.LandscapeStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.SysUINavigationMode;

import java.util.ArrayList;

public class QuickstepLauncher extends BaseQuickstepLauncher {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = true;

    @Override
    public TouchController[] createTouchControllers() {
        ArrayList<TouchController> list = new ArrayList<>();
        list.add(getDragController());

        if (getDeviceProfile().isVerticalBarLayout()) {
            list.add(new LandscapeStatesTouchController(this));
            list.add(new LandscapeEdgeSwipeController(this));
        } else {
            boolean allowDragToOverview = SysUINavigationMode.INSTANCE.get(this)
                    .getMode().hasGestures;
            list.add(new PortraitStatesTouchController(this, allowDragToOverview));
        }
        return list.toArray(new TouchController[list.size()]);
    }
}
