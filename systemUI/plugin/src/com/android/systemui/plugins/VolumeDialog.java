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

import com.android.systemui.plugins.VolumeDialog.Callback;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * This interface is really just a stub for initialization/teardown, actual handling of
 * when to show will be done through {@link VolumeDialogController}
 */
@ProvidesInterface(action = VolumeDialog.ACTION, version = VolumeDialog.VERSION)
@DependsOn(target = Callback.class)
public interface VolumeDialog extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_VOLUME";
    String ACTION_VOLUME_UNDO = "com.android.systemui.volume.ACTION_VOLUME_UNDO";
    int VERSION = 1;

    void init(int windowType, Callback callback);
    void destroy();

    @ProvidesInterface(version = VERSION)
    public interface Callback {
        int VERSION = 1;

        void onZenSettingsClicked();
        void onZenPrioritySettingsClicked();
    }
}
