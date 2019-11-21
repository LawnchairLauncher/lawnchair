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
package com.android.launcher3.model;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.content.ComponentName;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Callback for receiving various app launch events
 */
public class AppLaunchTracker implements ResourceBasedOverride {

    /**
     * Derived from LauncherEvent proto.
     * TODO: Use proper descriptive constants
     */
    public static final String CONTAINER_DEFAULT = Integer.toString(ContainerType.WORKSPACE);
    public static final String CONTAINER_ALL_APPS = Integer.toString(ContainerType.ALLAPPS);
    public static final String CONTAINER_PREDICTIONS = Integer.toString(ContainerType.PREDICTION);
    public static final String CONTAINER_SEARCH = Integer.toString(ContainerType.SEARCHRESULT);


    public static final MainThreadInitializedObject<AppLaunchTracker> INSTANCE =
            forOverride(AppLaunchTracker.class, R.string.app_launch_tracker_class);

    public void onStartShortcut(String packageName, String shortcutId, UserHandle user,
            @Nullable String container) { }

    public void onStartApp(ComponentName componentName, UserHandle user,
            @Nullable String container) { }

    public void onDismissApp(ComponentName componentName, UserHandle user,
             @Nullable String container){}

    public void onReturnedToHome() { }
}
