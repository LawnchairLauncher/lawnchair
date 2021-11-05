/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import com.android.launcher3.BaseQuickstepLauncher;

/**
 * A data source which integrates with a Launcher instance, used specifically for a
 * desktop environment.
 */
public class DesktopTaskbarUIController extends TaskbarUIController {

    private final BaseQuickstepLauncher mLauncher;

    public DesktopTaskbarUIController(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    protected void init(TaskbarControllers taskbarControllers) {
        mLauncher.getHotseat().setIconsAlpha(0f);
    }

    @Override
    protected void onDestroy() {
        mLauncher.getHotseat().setIconsAlpha(1f);
    }
}
