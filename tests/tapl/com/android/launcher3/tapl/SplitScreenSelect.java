/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.tapl;

import com.android.launcher3.tapl.LauncherInstrumentation.ContainerType;

/**
 * Represents a special state in Overview where the initial split app is shoved to the side and a
 * second split app can be selected.
 */
public class SplitScreenSelect extends Overview {

    SplitScreenSelect(LauncherInstrumentation launcher) {
        super(launcher);
    }

    @Override
    protected ContainerType getContainerType() {
        return ContainerType.SPLIT_SCREEN_SELECT;
    }

    @Override
    protected boolean isActionsViewVisible() {
        // We don't show overview actions in split select state.
        return false;
    }
}
