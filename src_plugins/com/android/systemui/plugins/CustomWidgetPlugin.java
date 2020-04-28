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
     * The label to display to the user in the AppWidget picker.
     */
    String getLabel(Context context);

    /**
     * The default width of the widget when added to a host, in dp. The widget will get
     * at least this width, and will often be given more, depending on the host.
     */
    int getSpanX(Context context);

    /**
     * The default height of the widget when added to a host, in dp. The widget will get
     * at least this height, and will often be given more, depending on the host.
     */
    int getSpanY(Context context);

    /**
     * Minimum width (in dp) which the widget can be resized to. This field has no effect if it
     * is greater than minWidth or if horizontal resizing isn't enabled.
     */
    int getMinSpanX(Context context);

    /**
     * Minimum height (in dp) which the widget can be resized to. This field has no effect if it
     * is greater than minHeight or if vertical resizing isn't enabled.
     */
    int getMinSpanY(Context context);

    /**
     * The rules by which a widget can be resized.
     */
    int getResizeMode(Context context);

    /**
     * Notify the plugin that container of the widget has been rendered, where the custom widget
     * can be attached to.
     */
    void onViewCreated(Context context, AppWidgetHostView parent);
}
