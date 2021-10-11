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

package com.android.launcher3.model.data;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_BOTTOM_WIDGETS_TRAY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PIN_WIDGETS;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;
import static com.android.launcher3.Utilities.ATLEAST_S;

import android.appwidget.AppWidgetHostView;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Process;

import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.launcher3.widget.util.WidgetSizes;

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
     * Flags for recording all the features that a widget has enabled.
     * @see widgetFeatures
     */
    public static final int FEATURE_RECONFIGURABLE = 1;
    public static final int FEATURE_OPTIONAL_CONFIGURATION = 1 << 1;
    public static final int FEATURE_PREVIEW_LAYOUT = 1 << 2;
    public static final int FEATURE_TARGET_CELL_SIZE = 1 << 3;
    public static final int FEATURE_MIN_SIZE = 1 << 4;
    public static final int FEATURE_MAX_SIZE = 1 << 5;
    public static final int FEATURE_ROUNDED_CORNERS = 1 << 6;

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

    /**
     * Contains a binary representation indicating which widget features are enabled. This value is
     * -1 if widget features could not be identified.
     */
    private int widgetFeatures;

    private boolean mHasNotifiedInitialWidgetSizeChanged;

    /**
     * The container from which this widget was added (e.g. widgets tray, pin widget, search)
     */
    public int sourceContainer = LauncherSettings.Favorites.CONTAINER_UNKNOWN;

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
        widgetFeatures = -1;
        // We only support app widgets on current user.
        user = Process.myUserHandle();
        restoreStatus = RESTORE_COMPLETED;
    }

    public LauncherAppWidgetInfo(int appWidgetId, ComponentName providerName,
            LauncherAppWidgetProviderInfo providerInfo, AppWidgetHostView hostView) {
        this(appWidgetId, providerName);
        widgetFeatures = computeWidgetFeatures(providerInfo, hostView);
    }

    /** Used for testing **/
    public LauncherAppWidgetInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
    }

    public boolean isCustomWidget() {
        return appWidgetId <= CUSTOM_WIDGET_ID;
    }

    @Nullable
    @Override
    public ComponentName getTargetComponent() {
        return providerName;
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.Favorites.APPWIDGET_ID, appWidgetId)
                .put(LauncherSettings.Favorites.APPWIDGET_PROVIDER, providerName.flattenToString())
                .put(LauncherSettings.Favorites.RESTORED, restoreStatus)
                .put(LauncherSettings.Favorites.OPTIONS, options)
                .put(LauncherSettings.Favorites.INTENT, bindOptions)
                .put(LauncherSettings.Favorites.APPWIDGET_SOURCE, sourceContainer);
    }

    /**
     * When we bind the widget, we should notify the widget that the size has changed if we have not
     * done so already (only really for default workspace widgets).
     */
    public void onBindAppWidget(Launcher launcher, AppWidgetHostView hostView) {
        if (!mHasNotifiedInitialWidgetSizeChanged) {
            WidgetSizes.updateWidgetSizeRanges(hostView, launcher, spanX, spanY);
            mHasNotifiedInitialWidgetSizeChanged = true;
        }
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties()
                + " providerName=" + providerName
                + " appWidgetId=" + appWidgetId;
    }

    public final boolean isWidgetIdAllocated() {
        return (restoreStatus & FLAG_ID_NOT_VALID) == 0
                || (restoreStatus & FLAG_ID_ALLOCATED) == FLAG_ID_ALLOCATED;
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

    @SuppressWarnings("NewApi")
    private static int computeWidgetFeatures(
            LauncherAppWidgetProviderInfo providerInfo, AppWidgetHostView hostView) {
        int widgetFeatures = 0;
        if (providerInfo.isReconfigurable()) {
            widgetFeatures |= FEATURE_RECONFIGURABLE;
        }
        if (providerInfo.isConfigurationOptional()) {
            widgetFeatures |= FEATURE_OPTIONAL_CONFIGURATION;
        }
        if (ATLEAST_S && providerInfo.previewLayout != Resources.ID_NULL) {
            widgetFeatures |= FEATURE_PREVIEW_LAYOUT;
        }
        if (ATLEAST_S && (providerInfo.targetCellWidth > 0 || providerInfo.targetCellHeight > 0)) {
            widgetFeatures |= FEATURE_TARGET_CELL_SIZE;
        }
        if (providerInfo.minResizeWidth > 0 || providerInfo.minResizeHeight > 0) {
            widgetFeatures |= FEATURE_MIN_SIZE;
        }
        if (ATLEAST_S && (providerInfo.maxResizeWidth > 0 || providerInfo.maxResizeHeight > 0)) {
            widgetFeatures |= FEATURE_MAX_SIZE;
        }
        if (hostView instanceof LauncherAppWidgetHostView &&
                ((LauncherAppWidgetHostView) hostView).hasEnforcedCornerRadius()) {
            widgetFeatures |= FEATURE_ROUNDED_CORNERS;
        }
        return widgetFeatures;
    }

    public static LauncherAtom.Attribute getAttribute(int container) {
        switch (container) {
            case CONTAINER_WIDGETS_TRAY:
                return LauncherAtom.Attribute.WIDGETS;
            case CONTAINER_BOTTOM_WIDGETS_TRAY:
                return LauncherAtom.Attribute.WIDGETS_BOTTOM_TRAY;
            case CONTAINER_PIN_WIDGETS:
                return LauncherAtom.Attribute.PINITEM;
            case CONTAINER_WIDGETS_PREDICTION:
                return LauncherAtom.Attribute.WIDGETS_TRAY_PREDICTION;
            case CONTAINER_ALL_APPS:
                return LauncherAtom.Attribute.ALL_APPS_SEARCH_RESULT_WIDGETS;
            default:
                return LauncherAtom.Attribute.UNKNOWN;
        }
    }

    @Override
    public LauncherAtom.ItemInfo buildProto(FolderInfo folderInfo) {
        LauncherAtom.ItemInfo info = super.buildProto(folderInfo);
        return info.toBuilder()
                .setWidget(info.getWidget().toBuilder().setWidgetFeatures(widgetFeatures))
                .setAttribute(getAttribute(sourceContainer))
                .build();
    }
}
