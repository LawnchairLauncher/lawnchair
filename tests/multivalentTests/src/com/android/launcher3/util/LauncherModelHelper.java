/*
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
package com.android.launcher3.util;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class to help manage Launcher Model and related objects for test.
 */
public class LauncherModelHelper {

    public static final String TEST_PACKAGE = getInstrumentation().getContext().getPackageName();
    public static final String TEST_ACTIVITY = "com.android.launcher3.tests.Activity2";
    public static final String TEST_ACTIVITY2 = "com.android.launcher3.tests.Activity3";
    public static final String TEST_ACTIVITY3 = "com.android.launcher3.tests.Activity4";
    public static final String TEST_ACTIVITY4 = "com.android.launcher3.tests.Activity5";
    public static final String TEST_ACTIVITY5 = "com.android.launcher3.tests.Activity6";
    public static final String TEST_ACTIVITY6 = "com.android.launcher3.tests.Activity7";
    public static final String TEST_ACTIVITY7 = "com.android.launcher3.tests.Activity8";
    public static final String TEST_ACTIVITY8 = "com.android.launcher3.tests.Activity9";
    public static final String TEST_ACTIVITY9 = "com.android.launcher3.tests.Activity10";
    public static final String TEST_ACTIVITY10 = "com.android.launcher3.tests.Activity11";
    public static final String TEST_ACTIVITY11 = "com.android.launcher3.tests.Activity12";
    public static final String TEST_ACTIVITY12 = "com.android.launcher3.tests.Activity13";
    public static final String TEST_ACTIVITY13 = "com.android.launcher3.tests.Activity14";
    public static final String TEST_ACTIVITY14 = "com.android.launcher3.tests.Activity15";

    public static final List<String> ACTIVITY_LIST = Arrays.asList(
            TEST_ACTIVITY,
            TEST_ACTIVITY2,
            TEST_ACTIVITY3,
            TEST_ACTIVITY4,
            TEST_ACTIVITY5,
            TEST_ACTIVITY6,
            TEST_ACTIVITY7,
            TEST_ACTIVITY8,
            TEST_ACTIVITY9,
            TEST_ACTIVITY10,
            TEST_ACTIVITY11,
            TEST_ACTIVITY12,
            TEST_ACTIVITY13,
            TEST_ACTIVITY14
    );
}
