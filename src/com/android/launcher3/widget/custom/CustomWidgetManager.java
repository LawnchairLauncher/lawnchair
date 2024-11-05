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

import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageUserKey;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * CustomWidgetManager handles custom widgets implemented as a plugin.
 */
public class CustomWidgetManager implements PluginListener<CustomWidgetPlugin>, SafeCloseable {

    public static final MainThreadInitializedObject<CustomWidgetManager> INSTANCE =
            new MainThreadInitializedObject<>(CustomWidgetManager::new);

    private static final String TAG = "CustomWidgetManager";
    private static final String PLUGIN_PKG = "android";
    private final Context mContext;
    private final HashMap<ComponentName, CustomWidgetPlugin> mPlugins;
    private final List<CustomAppWidgetProviderInfo> mCustomWidgets;
    private Consumer<PackageUserKey> mWidgetRefreshCallback;

    private CustomWidgetManager(Context context) {
        mContext = context;
        mPlugins = new HashMap<>();
        mCustomWidgets = new ArrayList<>();
        PluginManagerWrapper.INSTANCE.get(context)
                .addPluginListener(this, CustomWidgetPlugin.class, true);

        if (enableSmartspaceAsAWidget()) {
            for (String s: context.getResources()
                    .getStringArray(R.array.custom_widget_providers)) {
                try {
                    Class<?> cls = Class.forName(s);
                    CustomWidgetPlugin plugin = (CustomWidgetPlugin)
                            cls.getDeclaredConstructor(Context.class).newInstance(context);
                    onPluginConnected(plugin, context);
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
        CustomAppWidgetProviderInfo info = newInfo(plugin, parcel);
        parcel.recycle();
        mPlugins.put(info.provider, plugin);
        mCustomWidgets.add(info);
    }

    @Override
    public void onPluginDisconnected(CustomWidgetPlugin plugin) {
        ComponentName cn = getWidgetProviderComponent(plugin);
        mPlugins.remove(cn);
        mCustomWidgets.removeIf(w -> w.getComponent().equals(cn));
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
        CustomWidgetPlugin plugin = mPlugins.get(info.provider);
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
     * Returns the widget provider in respect to given widget id.
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getWidgetProvider(ComponentName cn) {
        return mCustomWidgets.stream()
                .filter(w -> w.getComponent().equals(cn)).findAny().orElse(null);
    }

    private CustomAppWidgetProviderInfo newInfo(CustomWidgetPlugin plugin, Parcel parcel) {
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(parcel, false);
        info.provider = getWidgetProviderComponent(plugin);
        plugin.updateWidgetInfo(info, mContext);
        return info;
    }

    /**
     * Returns an id to set as the appWidgetId for a custom widget.
     */
    public int allocateCustomAppWidgetId(ComponentName componentName) {
        return CUSTOM_WIDGET_ID - mCustomWidgets.indexOf(getWidgetProvider(componentName));
    }

    private ComponentName getWidgetProviderComponent(CustomWidgetPlugin plugin) {
        return new ComponentName(
                PLUGIN_PKG, CLS_CUSTOM_WIDGET_PREFIX + plugin.getClass().getName());
    }
}
