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

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this plugin interface to access user event log on the device for prototype purpose.
 * NOTE: plugin is for internal prototype only and is not visible in production environment.
 */
@ProvidesInterface(action = UserEventPlugin.ACTION, version = UserEventPlugin.VERSION)
public interface UserEventPlugin extends Plugin {
    String ACTION = "com.android.launcher3.action.PLUGIN_USER_EVENT_LOG";
    int VERSION = 1;

    /**
     * Callback to be triggered whenever an user event occurs.
     */
    void onUserEvent(Object event);
}
