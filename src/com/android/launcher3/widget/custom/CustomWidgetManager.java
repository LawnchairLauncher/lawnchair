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

import static com.android.launcher3.config.FeatureFlags.SMARTSPACE_AS_A_WIDGET;
import static com.android.launcher3.model.data.LauncherAppWidgetInfo.CUSTOM_WIDGET_ID;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageUserKey;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * CustomWidgetManager handles custom widgets implemented as a plugin.
 */
public class CustomWidgetManager implements PluginListener<CustomWidgetPlugin>, SafeCloseable {

    public static final MainThreadInitializedObject<CustomWidgetManager> INSTANCE =
            new MainThreadInitializedObject<>(CustomWidgetManager::new);

    private static final String TAG = "CustomWidgetManager";
    private final Context mContext;
    private final HashMap<String, CustomWidgetPlugin> mPlugins;
    private final List<CustomAppWidgetProviderInfo> mCustomWidgets;
    private final HashMap<ComponentName, String> mWidgetsIdMap;
    private Consumer<PackageUserKey> mWidgetRefreshCallback;

    private CustomWidgetManager(Context context) {
        mContext = context;
        mPlugins = new HashMap<>();
        mCustomWidgets = new ArrayList<>();
        mWidgetsIdMap = new HashMap<>();
        PluginManagerWrapper.INSTANCE.get(context)
                .addPluginListener(this, CustomWidgetPlugin.class, true);

        if (SMARTSPACE_AS_A_WIDGET.get()) {
            for (String s: context.getResources()
                    .getStringArray(R.array.custom_widget_providers)) {
                try {
                    Class<?> cls = Class.forName(s);
                    CustomWidgetPlugin plugin = (CustomWidgetPlugin)
                            cls.getDeclaredConstructor(Context.class).newInstance(context);
                    mPlugins.put(plugin.getId(), plugin);
                    onPluginConnected(mPlugins.get(plugin.getId()), context);
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                         | ClassCastException | NoSuchMethodException
                         | InvocationTargetException e) {
                    Log.e(TAG, "Exception found when trying to add custom widgets: " + e);
                }
            }
        }
    }

    @Override
    public void close() {
        PluginManagerWrapper.INSTANCE.get(mContext).removePluginListener(this);
    }

    @Override
    public void onPluginConnected(CustomWidgetPlugin plugin, Context context) {
        List<AppWidgetProviderInfo> providers = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle());
        if (providers.isEmpty()) return;
        Parcel parcel = Parcel.obtain();
        providers.get(0).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CustomAppWidgetProviderInfo info = newInfo(plugin.getId(), plugin, parcel, context);
        parcel.recycle();
        mCustomWidgets.add(info);
        mWidgetsIdMap.put(info.provider, plugin.getId());
    }

    @Override
    public void onPluginDisconnected(CustomWidgetPlugin plugin) {
        String providerId = plugin.getId();
        if (mPlugins.containsKey(providerId)) {
            mPlugins.remove(providerId);
        }

        ComponentName cn = null;
        for (Map.Entry entry: mWidgetsIdMap.entrySet()) {
            if (entry.getValue().equals(providerId)) {
                cn = (ComponentName) entry.getKey();
            }
        }

        if (cn != null) {
            mWidgetsIdMap.remove(cn);
            for (int i = 0; i < mCustomWidgets.size(); i++) {
                if (mCustomWidgets.get(i).getComponent().equals(cn)) {
                    mCustomWidgets.remove(i);
                    return;
                }
            }
        }
    }

    /**
     * Inject a callback function to refresh the widgets.
     */
    public void setWidgetRefreshCallback(Consumer<PackageUserKey> cb) {
        mWidgetRefreshCallback = cb;
    }

    /**
     * Callback method to inform a plugin it's corresponding widget has been created.
     */
    public void onViewCreated(LauncherAppWidgetHostView view) {
        CustomAppWidgetProviderInfo info = (CustomAppWidgetProviderInfo) view.getAppWidgetInfo();
        CustomWidgetPlugin plugin = mPlugins.get(info.providerId);
        if (plugin == null) return;
        plugin.onViewCreated(view);
    }

    /**
     * Returns the stream of custom widgets.
     */
    @NonNull
    public Stream<CustomAppWidgetProviderInfo> stream() {
        return mCustomWidgets.stream();
    }

    /**
     * Returns the widget id for a specific provider.
     */
    public String getWidgetIdForCustomProvider(@NonNull ComponentName provider) {
        if (mWidgetsIdMap.containsKey(provider)) {
            return mWidgetsIdMap.get(provider);
        } else {
            return "";
        }
    }

    /**
     * Returns the widget provider in respect to given widget id.
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getWidgetProvider(ComponentName componentName) {
        for (LauncherAppWidgetProviderInfo info : mCustomWidgets) {
            if (info.provider.equals(componentName)) return info;
        }
        return null;
    }

    private static CustomAppWidgetProviderInfo newInfo(String providerId, CustomWidgetPlugin plugin,
            Parcel parcel, Context context) {
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(
                parcel, false, providerId);
        plugin.updateWidgetInfo(info, context);
        return info;
    }

    /**
     * Returns an id to set as the appWidgetId for a custom widget.
     */
    public int allocateCustomAppWidgetId(ComponentName componentName) {
        return CUSTOM_WIDGET_ID - mCustomWidgets.indexOf(getWidgetProvider(componentName));
    }

}
