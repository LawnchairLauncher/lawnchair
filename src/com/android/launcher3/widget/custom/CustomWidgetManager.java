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

package com.android.launcher3.widget.custom;

import static com.android.launcher3.Flags.enableSmartspaceAsAWidget;
import static com.android.launcher3.model.data.LauncherAppWidgetInfo.CUSTOM_WIDGET_ID;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.widget.LauncherAppWidgetProviderInfo.CLS_CUSTOM_WIDGET_PREFIX;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.R;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.PluginManagerWrapper;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.systemui.plugins.CustomWidgetPlugin;
import com.android.systemui.plugins.PluginListener;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * CustomWidgetManager handles custom widgets implemented as a plugin.
 */
@LauncherAppSingleton
public class CustomWidgetManager implements PluginListener<CustomWidgetPlugin> {

    public static final DaggerSingletonObject<CustomWidgetManager> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getCustomWidgetManager);

    private static final String TAG = "CustomWidgetManager";
    private static final String PLUGIN_PKG = "android";
    private final Context mContext;
    private final HashMap<ComponentName, CustomWidgetPlugin> mPlugins;
    private final List<CustomAppWidgetProviderInfo> mCustomWidgets;
    private final List<Runnable> mWidgetRefreshCallbacks = new CopyOnWriteArrayList<>();
    private final @NonNull AppWidgetManager mAppWidgetManager;

    @Inject
    CustomWidgetManager(@ApplicationContext Context context, PluginManagerWrapper pluginManager,
            DaggerSingletonTracker tracker) {
        this(context, pluginManager, AppWidgetManager.getInstance(context), tracker);
    }

    @VisibleForTesting
    CustomWidgetManager(@ApplicationContext Context context,
            PluginManagerWrapper pluginManager,
            @NonNull AppWidgetManager widgetManager,
            DaggerSingletonTracker tracker) {
        mContext = context;
        mAppWidgetManager = widgetManager;
        mPlugins = new HashMap<>();
        mCustomWidgets = new ArrayList<>();

        pluginManager.addPluginListener(this, CustomWidgetPlugin.class, true);
        if (enableSmartspaceAsAWidget()) {
            for (String s: context.getResources()
                    .getStringArray(R.array.custom_widget_providers)) {
                try {
                    Class<?> cls = Class.forName(s);
                    CustomWidgetPlugin plugin = (CustomWidgetPlugin)
                            cls.getDeclaredConstructor(Context.class).newInstance(context);
                    MAIN_EXECUTOR.execute(() -> onPluginConnected(plugin, context));
                } catch (ClassNotFoundException | InstantiationException
                         | IllegalAccessException
                         | ClassCastException | NoSuchMethodException
                         | InvocationTargetException e) {
                    Log.e(TAG, "Exception found when trying to add custom widgets: " + e);
                }
            }
        }
        tracker.addCloseable(() -> pluginManager.removePluginListener(this));
    }

    @Override
    public void onPluginConnected(CustomWidgetPlugin plugin, Context context) {
        CustomAppWidgetProviderInfo info = getAndAddInfo(new ComponentName(
                PLUGIN_PKG, CLS_CUSTOM_WIDGET_PREFIX + plugin.getClass().getName()));
        if (info != null) {
            plugin.updateWidgetInfo(info, mContext);
            mPlugins.put(info.provider, plugin);
            mWidgetRefreshCallbacks.forEach(MAIN_EXECUTOR::execute);
        }
    }

    @Override
    public void onPluginDisconnected(CustomWidgetPlugin plugin) {
        // Leave the providerInfo as plugins can get disconnected/reconnected multiple times
        mPlugins.values().remove(plugin);
        mWidgetRefreshCallbacks.forEach(MAIN_EXECUTOR::execute);
    }

    @VisibleForTesting
    @NonNull
    Map<ComponentName, CustomWidgetPlugin> getPlugins() {
        return mPlugins;
    }

    /**
     * Inject a callback function to refresh the widgets.
     * @return a closeable to remove this callback
     */
    public SafeCloseable addWidgetRefreshCallback(Runnable callback) {
        mWidgetRefreshCallbacks.add(callback);
        return () -> mWidgetRefreshCallbacks.remove(callback);
    }

    /**
     * Callback method to inform a plugin it's corresponding widget has been created.
     */
    public void onViewCreated(LauncherAppWidgetHostView view) {
        CustomAppWidgetProviderInfo info = (CustomAppWidgetProviderInfo) view.getAppWidgetInfo();
        CustomWidgetPlugin plugin = mPlugins.get(info.provider);
        if (plugin != null) {
            plugin.onViewCreated(view);
        }
    }

    /**
     * Returns the stream of custom widgets.
     */
    @NonNull
    public Stream<CustomAppWidgetProviderInfo> stream() {
        return mCustomWidgets.stream();
    }

    /**
     * Returns the widget provider in respect to given widget id.
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getWidgetProvider(ComponentName cn) {
        LauncherAppWidgetProviderInfo info = mCustomWidgets.stream()
                .filter(w -> w.getComponent().equals(cn)).findAny().orElse(null);
        if (info == null) {
            // If the info is not present, add a placeholder info since the
            // plugin might get loaded later
            info = getAndAddInfo(cn);
        }
        return info;
    }

    /**
     * Returns an id to set as the appWidgetId for a custom widget.
     */
    public int allocateCustomAppWidgetId(ComponentName componentName) {
        return CUSTOM_WIDGET_ID - mCustomWidgets.indexOf(getWidgetProvider(componentName));
    }

    @Nullable
    private CustomAppWidgetProviderInfo getAndAddInfo(ComponentName cn) {
        for (CustomAppWidgetProviderInfo info : mCustomWidgets) {
            if (info.provider.equals(cn)) return info;
        }

        List<AppWidgetProviderInfo> providers = mAppWidgetManager
                .getInstalledProvidersForProfile(Process.myUserHandle());
        if (providers.isEmpty()) return null;
        Parcel parcel = Parcel.obtain();
        providers.get(0).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(parcel, false);
        parcel.recycle();

        info.provider = cn;
        info.initialLayout = 0;
        mCustomWidgets.add(info);
        return info;
    }
}
