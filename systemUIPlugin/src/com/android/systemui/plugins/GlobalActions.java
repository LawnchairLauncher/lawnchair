/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import com.android.systemui.plugins.GlobalActions.GlobalActionsManager;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

@ProvidesInterface(action = GlobalActions.ACTION, version = GlobalActions.VERSION)
@DependsOn(target = GlobalActionsManager.class)
public interface GlobalActions extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_GLOBAL_ACTIONS";
    int VERSION = 1;

    void showGlobalActions(GlobalActionsManager manager);
    default void showShutdownUi(boolean isReboot, String reason) {
    }

    default void destroy() {
    }

    @ProvidesInterface(version = GlobalActionsManager.VERSION)
    public interface GlobalActionsManager {
        int VERSION = 1;

        void onGlobalActionsShown();
        void onGlobalActionsHidden();

        void shutdown();
        void reboot(boolean safeMode);
    }
}
