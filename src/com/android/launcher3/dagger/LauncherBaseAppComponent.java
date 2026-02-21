/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.dagger;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.RemoveAnimationSettingsTracker;
import com.android.launcher3.backuprestore.LauncherRestoreEventLogger;
import com.android.launcher3.compose.core.widgetpicker.WidgetPickerComposeWrapper;
import com.android.launcher3.folder.FolderNameSuggestionLoader;
import com.android.launcher3.graphics.GridCustomizationsProxy;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.LauncherIcons.IconPool;
import com.android.launcher3.logging.DumpManager;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.ItemInstallQueue;
import com.android.launcher3.model.LoaderCursor.LoaderCursorFactory;
import com.android.launcher3.pm.InstallSessionHelper;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PluginManagerWrapper;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.VibratorWrapper;
import com.android.launcher3.util.WallpaperColorHints;
import com.android.launcher3.util.window.RefreshRateTracker;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.launcher3.widget.LauncherWidgetHolder.WidgetHolderFactory;
import com.android.launcher3.widget.custom.CustomWidgetManager;
import com.android.launcher3.widget.util.WidgetSizeHandler;

import javax.inject.Named;

import app.lawnchair.DeviceProfileOverrides;
import app.lawnchair.HeadlessWidgetsManager;
import app.lawnchair.LawnchairActivityCachingLogic;
import app.lawnchair.NotificationManager;
import app.lawnchair.data.folder.service.FolderService;
import app.lawnchair.data.iconoverride.IconOverrideRepository;
import app.lawnchair.data.wallpaper.service.WallpaperService;
import app.lawnchair.font.FontCache;
import app.lawnchair.font.FontManager;
import app.lawnchair.font.googlefonts.GoogleFontsListing;
import app.lawnchair.icons.iconpack.IconPackProvider;
import app.lawnchair.icons.shape.IconShapeManager;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;
import app.lawnchair.smartspace.provider.SmartspaceProvider;
import app.lawnchair.theme.ThemeProvider;
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceModelList;
import app.lawnchair.ui.preferences.data.liveinfo.LiveInformationManager;
import app.lawnchair.util.LawnchairWindowManagerProxy;
import dagger.BindsInstance;

/**
 * Launcher base component for Dagger injection.
 *
 * This class is not actually annotated as a Dagger component, since it is not used directly as one.
 * Doing so generates unnecessary code bloat.
 *
 * See {@link LauncherAppComponent} for the one actually used by AOSP.
 */
public interface LauncherBaseAppComponent {
    DaggerSingletonTracker getDaggerSingletonTracker();
    ApiWrapper getApiWrapper();
    CustomWidgetManager getCustomWidgetManager();
    DynamicResource getDynamicResource();
    InstallSessionHelper getInstallSessionHelper();
    ItemInstallQueue getItemInstallQueue();
    RefreshRateTracker getRefreshRateTracker();
    ScreenOnTracker getScreenOnTracker();
    SettingsCache getSettingsCache();
    PackageManagerHelper getPackageManagerHelper();
    PluginManagerWrapper getPluginManagerWrapper();
    VibratorWrapper getVibratorWrapper();
    MSDLPlayerWrapper getMSDLPlayerWrapper();
    WindowManagerProxy getWmProxy();
    LauncherPrefs getLauncherPrefs();
    ThemeManager getThemeManager();
    UserCache getUserCache();
    DisplayController getDisplayController();
    WallpaperColorHints getWallpaperColorHints();
    LockedUserState getLockedUserState();
    InvariantDeviceProfile getIDP();
    IconPool getIconPool();
    RemoveAnimationSettingsTracker getRemoveAnimationSettingsTracker();
    LauncherAppState getLauncherAppState();

    LauncherRestoreEventLogger getLauncherRestoreEventLogger();
    GridCustomizationsProxy getGridCustomizationsProxy();
    FolderNameSuggestionLoader getFolderNameSuggestionLoader();
    LoaderCursorFactory getLoaderCursorFactory();
    WidgetHolderFactory getWidgetHolderFactory();
    RefreshRateTracker getFrameRateProvider();
    InstantAppResolver getInstantAppResolver();
    DumpManager getDumpManager();
    StatsLogManager.StatsLogManagerFactory getStatsLogManagerFactory();
    ActivityContextComponent.Builder getActivityContextComponentBuilder();
    WidgetPickerComposeWrapper getWidgetPickerComposeWrapper();
    WidgetSizeHandler getWidgetSizeHandler();


    // Lawnchair-specific
    
    LawnchairWindowManagerProxy getLWMP();
    DeviceProfileOverrides getDPO();
    ThemeProvider getThemeProvider();
    SmartspaceProvider getSmartspaceProvider();
    HeadlessWidgetsManager getHeadlessWidgetsManager();
    NotificationManager getNotificationManager();
    ColorPreferenceModelList getColorPreferenceModelList();
    LiveInformationManager getLiveInformationManager();
    PreferenceManager2 getPreferenceManager2();
    PreferenceManager getPreferenceManager();
    FontCache getFontCache();
    FontManager getFontManager();
    IconShapeManager getIconShapeManager();
    IconPackProvider getIconPackProvider();
    GoogleFontsListing getGoogleFontsListing();
    WallpaperService getWallpaperService();
    IconOverrideRepository getIconOverrideRepository();

    LawnchairActivityCachingLogic getLawnchairActivityCachingLogic();
    FolderService getFolderService();

    /** Builder for LauncherBaseAppComponent. */
    interface Builder {
        @BindsInstance Builder appContext(@ApplicationContext Context context);
        @BindsInstance Builder iconsDbName(@Nullable @Named("ICONS_DB") String dbFileName);
        @BindsInstance Builder setSafeModeEnabled(@Named("SAFE_MODE") boolean safeModeEnabled);
        LauncherBaseAppComponent build();
    }
}
