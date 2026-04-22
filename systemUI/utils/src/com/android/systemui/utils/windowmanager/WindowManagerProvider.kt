/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.utils.windowmanager

import android.content.Context
import android.view.WindowManager

/**
 * Provider for [WindowManager] in SystemUI.
 *
 * Use this class over [WindowManagerUtils] in cases where
 * a [WindowManager] is needed for a context created inside the class. [WindowManagerUtils] should
 * only be used in a class where the [WindowManager] is needed for a custom context inside the
 * class, and the class is not part of the dagger graph. Example usage:
 * ```kotlin
 * class Sample {
 *      private final WindowManager mWindowManager;
 *
 *      @Inject
 *      public Sample(WindowManagerProvider windowManagerProvider) {
 *          Context context = getCustomContext();
 *          mWindowManager = windowManagerProvider.getWindowManager(context);
 *      }
 *      // use mWindowManager
 * }
 *
 * class SampleTest {
 *
 *      @Mock
 *      WindowManager mWindowManager;
 *
 *      FakeWindowManagerProvider fakeProvider = new FakeWindowManagerProvider(mWindowManager);
 *
 *      // define the behaviour of mWindowManager to get required WindowManager instance in tests.
 * }
 * ```
 */
interface WindowManagerProvider {

    /** Method to return the required [WindowManager]. */
    fun getWindowManager(context: Context): WindowManager
}
