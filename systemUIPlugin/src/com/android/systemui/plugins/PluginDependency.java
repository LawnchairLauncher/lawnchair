/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import com.android.systemui.plugins.annotations.ProvidesInterface;

@ProvidesInterface(version = PluginDependency.VERSION)
public class PluginDependency {
    public static final int VERSION = 1;
    static DependencyProvider sProvider;

    public static <T> T get(Plugin p, Class<T> cls) {
        return sProvider.get(p, cls);
    }

    static abstract class DependencyProvider {
        abstract <T> T get(Plugin p, Class<T> cls);
    }
}
