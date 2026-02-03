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

package com.android.systemui.plugins;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.systemui.plugins.annotations.ProvidesInterface;

import java.util.function.BiConsumer;

/**
 * Implement this interface to process Launcher icon drawables before they are displayed.
 */
@ProvidesInterface(action = IconProcessorPlugin.ACTION, version = IconProcessorPlugin.VERSION)
public interface IconProcessorPlugin extends Plugin {
    String ACTION = "com.android.systemui.action.ICON_WRAPPER_PLUGIN";
    int VERSION = 1;

    /**
     * Sets a callback to be called with packageName and userHandle, whenever an icon changes
     */
    void setIconChangeNotifier(BiConsumer<String, UserHandle> callback);

    /** Preprocess the provided drawable and returns the modified drawable */
    Drawable preprocessDrawable(Drawable original, int resId, ApplicationInfo appInfo);

    /** Notifies when an app icon is loaded from cache */
    void notifyAppIconLoaded(ComponentName cn, UserHandle user, int bitmapInfoFlags);
}
