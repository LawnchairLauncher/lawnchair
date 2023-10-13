/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.appiconmenu;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;

import org.junit.Before;

/**
 * Tests the Icon App Chip Menu in overview.
 *
 * <p>Same tests as TaplOverviewIconTest with the Flag FLAG_ENABLE_OVERVIEW_ICON_MENU enabled.
 * This class can be removed once FLAG_ENABLE_OVERVIEW_ICON_MENU is enabled by default.
 */
public class TaplOverviewIconAppChipMenuTest extends TaplOverviewIconTest {

    @Before
    public void setUp() throws Exception {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU); // Call before super.setUp
        super.setUp();
        executeOnLauncher(launcher -> InvariantDeviceProfile.INSTANCE.get(launcher).onConfigChanged(
                launcher));
    }
}
