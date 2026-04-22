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
 * Plugin to support customizing resource
 */
@ProvidesInterface(action = ResourceProvider.ACTION, version = ResourceProvider.VERSION)
public interface ResourceProvider extends Plugin {
    String ACTION = "com.android.launcher3.action.PLUGIN_DYNAMIC_RESOURCE";
    int VERSION = 1;

    /**
     * @see android.content.res.Resources#getInteger(int)
     */
    int getInt(int resId);

    /**
     * @see android.content.res.Resources#getFraction(int, int, int)
     */
    float getFraction(int resId);

    /**
     * @see android.content.res.Resources#getDimension(int)
     */
    float getDimension(int resId);

    /**
     * @see android.content.res.Resources#getColor(int)
     */
    int getColor(int resId);

    /**
     * @see android.content.res.Resources#getFloat(int)
     */
    float getFloat(int resId);
}
