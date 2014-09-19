/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher3;

import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;

import com.android.launcher3.compat.UserHandleCompat;

/**
 * Represents a widget (either instantiated or about to be) in the Launcher.
 */
public class LauncherAppWidgetInfo extends ItemInfo {

    public static final int RESTORE_COMPLETED = 0;

    /**
     * This is set during the package backup creation.
     */
    public static final int FLAG_ID_NOT_VALID = 1;

    /**
     * Indicates that the provider is not available yet.
     */
    public static final int FLAG_PROVIDER_NOT_READY = 2;

    /**
     * Indicates that the widget UI is not yet ready, and user needs to set it up again.
     */
    public static final int FLAG_UI_NOT_READY = 4;

    /**
     * Indicates that the widget restore has started.
     */
    public static final int FLAG_RESTORE_STARTED = 8;

    /**
     * Indicates that the widget hasn't been instantiated yet.
     */
    static final int NO_ID = -1;

    /**
     * Identifier for this widget when talking with
     * {@link android.appwidget.AppWidgetManager} for updates.
     */
    int appWidgetId = NO_ID;

    ComponentName providerName;

    // TODO: Are these necessary here?
    int minWidth = -1;
    int minHeight = -1;

    /**
     * Indicates the restore status of the widget.
     */
    int restoreStatus;

    /**
     * Indicates the installation progress of the widget provider
     */
    int installProgress = -1;

    private boolean mHasNotifiedInitialWidgetSizeChanged;

    /**
     * View that holds this widget after it's been created.  This view isn't created
     * until Launcher knows it's needed.
     */
    AppWidgetHostView hostView = null;

    LauncherAppWidgetInfo(int appWidgetId, ComponentName providerName) {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        this.appWidgetId = appWidgetId;
        this.providerName = providerName;

        // Since the widget isn't instantiated yet, we don't know these values. Set them to -1
        // to indicate that they should be calculated based on the layout and minWidth/minHeight
        spanX = -1;
        spanY = -1;
        // We only support app widgets on current user.
        user = UserHandleCompat.myUserHandle();
        restoreStatus = RESTORE_COMPLETED;
    }

    @Override
    void onAddToDatabase(Context context, ContentValues values) {
        super.onAddToDatabase(context, values);
        values.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId);
        values.put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, providerName.flattenToString());
        values.put(LauncherSettings.Favorites.RESTORED, restoreStatus);
    }

    /**
     * When we bind the widget, we should notify the widget that the size has changed if we have not
     * done so already (only really for default workspace widgets).
     */
    void onBindAppWidget(Launcher launcher) {
        if (!mHasNotifiedInitialWidgetSizeChanged) {
            notifyWidgetSizeChanged(launcher);
        }
    }

    /**
     * Trigger an update callback to the widget to notify it that its size has changed.
     */
    void notifyWidgetSizeChanged(Launcher launcher) {
        AppWidgetResizeFrame.updateWidgetSizeRanges(hostView, launcher, spanX, spanY);
        mHasNotifiedInitialWidgetSizeChanged = true;
    }

    @Override
    public String toString() {
        return "AppWidget(id=" + Integer.toString(appWidgetId) + ")";
    }

    @Override
    void unbind() {
        super.unbind();
        hostView = null;
    }

    public final boolean isWidgetIdValid() {
        return (restoreStatus & FLAG_ID_NOT_VALID) == 0;
    }

    public final boolean hasRestoreFlag(int flag) {
        return (restoreStatus & flag) == flag;
    }
}
