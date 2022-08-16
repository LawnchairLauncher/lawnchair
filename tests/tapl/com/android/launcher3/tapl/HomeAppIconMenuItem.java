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

import androidx.test.uiautomator.UiObject2;

/**
 * Menu item in a home screen app icon menu.
 */
public final class HomeAppIconMenuItem extends AppIconMenuItem implements WorkspaceDragSource {

    HomeAppIconMenuItem(LauncherInstrumentation launcher,
            UiObject2 shortcut) {
        super(launcher, shortcut);
    }

    /** This method requires public access, however should not be called in tests. */
    @Override
    public Launchable getLaunchable() {
        return this;
    }
}
