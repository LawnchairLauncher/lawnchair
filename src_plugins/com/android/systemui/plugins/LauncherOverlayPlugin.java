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
package com.android.systemui.plugins;

import android.app.Activity;

import com.android.systemui.plugins.annotations.ProvidesInterface;
import com.android.systemui.plugins.shared.LauncherOverlayManager;

/**
 * Implement this interface to add a -1 content on the home screen.
 */
@ProvidesInterface(action = LauncherOverlayPlugin.ACTION, version = LauncherOverlayPlugin.VERSION)
public interface LauncherOverlayPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.PLUGIN_LAUNCHER_OVERLAY";
    int VERSION = 1;

    LauncherOverlayManager createOverlayManager(Activity activity);

}
