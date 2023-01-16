/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.content.ClipDescription;
import android.content.Intent;

/**
 * Wrapper around ClipDescription.
 */
public abstract class ClipDescriptionCompat {

    public static String MIMETYPE_APPLICATION_ACTIVITY =
            ClipDescription.MIMETYPE_APPLICATION_ACTIVITY;

    public static String MIMETYPE_APPLICATION_SHORTCUT =
            ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;

    public static String MIMETYPE_APPLICATION_TASK =
            ClipDescription.MIMETYPE_APPLICATION_TASK;

    public static String EXTRA_PENDING_INTENT = ClipDescription.EXTRA_PENDING_INTENT;

    public static String EXTRA_TASK_ID = Intent.EXTRA_TASK_ID;
}
