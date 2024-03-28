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

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;

import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Implement this plugin interface to add a custom widget.
 */
@ProvidesInterface(action = CustomWidgetPlugin.ACTION, version = CustomWidgetPlugin.VERSION)
public interface CustomWidgetPlugin extends Plugin {

    String ACTION = "com.android.systemui.action.PLUGIN_CUSTOM_WIDGET";
    int VERSION = 1;

    /**
     * Notify the plugin that container of the widget has been rendered, where the custom widget
     * can be attached to.
     */
    void onViewCreated(AppWidgetHostView parent);

    /**
     * Get the UUID for the custom widget.
     */
    String getId();

    /**
     * Used to modify a widgets' info.
     */
    default void updateWidgetInfo(AppWidgetProviderInfo info, Context context) {

    }
}
