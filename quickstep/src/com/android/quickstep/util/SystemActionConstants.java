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

package com.android.quickstep.util;

/**
 * Constants for registering SystemActions.
 *
 * Prefer to use AccessibilityService.GLOBAL_ACTION_* if applicable.
 */
public final class SystemActionConstants {

    public static final int SYSTEM_ACTION_ID_TASKBAR = 499;
    public static final int SYSTEM_ACTION_ID_SEARCH_SCREEN = 500;

    /**
     * For Taskbar broadcast intent filter.
     */
    public static final String ACTION_SHOW_TASKBAR = "ACTION_SHOW_TASKBAR";

    /**
     * For Search Screen broadcast intent filter.
     */
    public static final String ACTION_SEARCH_SCREEN = "ACTION_SEARCH_SCREEN";

    private SystemActionConstants() {}
}
