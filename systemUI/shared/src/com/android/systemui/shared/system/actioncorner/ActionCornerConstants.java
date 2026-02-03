/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.shared.system.actioncorner;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ActionCornerConstants {
    /** Go to the home screen. */
    public static final int HOME = 0;
    /** Open the overview screen. */
    public static final int OVERVIEW = 1;
    /** Open the notification shade. */
    public static final int NOTIFICATION = 2;
    /** Open the quick settings panel. */
    public static final int QUICK_SETTING = 3;

    @IntDef({HOME, OVERVIEW, NOTIFICATION, QUICK_SETTING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }
}
