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
import android.content.Intent;
import android.os.Process;

import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.util.ContentWriter;

/**
 * Represents a widget (either instantiated or about to be) in the Launcher.
 */
public class LauncherAppWidgetInfo extends ItemInfo {

    public static final int OPTION_SEARCH_WIDGET = 1;


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
     * Indicates that the widget has been allocated an Id. The id is still not valid, as it has
     * not been bound yet.
     */
    public static final int FLAG_ID_ALLOCATED = 16;

    /**
     * Indicates that the widget does not need to show config activity, even if it has a
     * configuration screen. It can also optionally have some extras which are sent during bind.
     */
    public static final int FLAG_DIRECT_CONFIG = 32;

    /**
     * Indicates that the widget hasn't been instantiated yet.
     */
    public static final int NO_ID = -1;

    /**
     * Indicates that this is a locally defined widget and hence has no system allocated id.
     */
    public static final int CUSTOM_WIDGET_ID = -100;

    /**
     * Identifier for this widget when talking with
     * {@link android.appwidget.AppWidgetManager} for updates.
     */
    public int appWidgetId = NO_ID;

    public ComponentName providerName;

    /**
     * Indicates the restore status of the widget.
     */
    public int restoreStatus;

    /**
     * Indicates the installation progress of the widget provider
     */
    public int installProgress = -1;

    /**
     * Optional extras sent during widget bind. See {@link #FLAG_DIRECT_CONFIG}.
     */
    public Intent bindOptions;

    /**
     * Widget options
     */
    public int options;

    /**
     * Nonnull for pending widgets. We use this to get the icon and title for the widget.
     */
    public PackageItemInfo pendingItemInfo;

    private boolean mHasNotifiedInitialWidgetSizeChanged;

    public LauncherAppWidgetInfo(int appWidgetId, ComponentName providerName) {
        this.appWidgetId = appWidgetId;
        this.providerName = providerName;

        if (isCustomWidget()) {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
        } else {
            itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
        }

        // Since the widget isn't instantiated yet, we don't know these values. Set them to -1
        // to indicate that they should be calculated based on the layout and minWidth/minHeight
        spanX = -1;
        spanY = -1;
        // We only support app widgets on current user.
        user = Process.myUserHandle();
        restoreStatus = RESTORE_COMPLETED;
    }

    /** Used for testing **/
    public LauncherAppWidgetInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
    }

    public boolean isCustomWidget() {
        return appWidgetId <= CUSTOM_WIDGET_ID;
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId)
                .put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, providerName.flattenToString())
                .put(LauncherSettings.Favorites.RESTORED, restoreStatus)
                .put(LauncherSettings.Favorites.OPTIONS, options)
                .put(LauncherSettings.Favorites.INTENT, bindOptions);
    }

    /**
     * When we bind the widget, we should notify the widget that the size has changed if we have not
     * done so already (only really for default workspace widgets).
     */
    void onBindAppWidget(Launcher launcher, AppWidgetHostView hostView) {
        if (!mHasNotifiedInitialWidgetSizeChanged) {
            AppWidgetResizeFrame.updateWidgetSizeRanges(hostView, launcher, spanX, spanY);
            mHasNotifiedInitialWidgetSizeChanged = true;
        }
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " appWidgetId=" + appWidgetId;
    }

    public final boolean isWidgetIdAllocated() {
        return (restoreStatus & FLAG_ID_NOT_VALID) == 0 ||
                (restoreStatus & FLAG_ID_ALLOCATED) == FLAG_ID_ALLOCATED;
    }

    public final boolean hasRestoreFlag(int flag) {
        return (restoreStatus & flag) == flag;
    }

    /**
     * returns if widget options include an option or not
     * @param option
     * @return
     */
    public final boolean hasOptionFlag(int option) {
        return (options & option) != 0;
    }
}
