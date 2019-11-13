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
package com.android.quickstep.util;

import android.content.Context;

import com.android.launcher3.logging.EventLogArray;
import com.android.launcher3.util.MainThreadInitializedObject;

/**
 * A log to keep track of the active gesture.
 */
public class ActiveGestureLog extends EventLogArray {

    public static final ActiveGestureLog INSTANCE = new ActiveGestureLog();

    /**
     * NOTE: This value should be kept same as
     * ActivityTaskManagerService#INTENT_EXTRA_LOG_TRACE_ID in platform
     */
    public static final String INTENT_EXTRA_LOG_TRACE_ID = "INTENT_EXTRA_LOG_TRACE_ID";

    private ActiveGestureLog() {
        super("touch_interaction_log", 40);
    }
}
