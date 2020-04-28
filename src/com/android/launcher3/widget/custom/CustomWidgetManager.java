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

import static com.android.launcher3.LauncherAppWidgetProviderInfo.CLS_CUSTOM_WIDGET_PREFIX;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Process;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.systemui.plugins.CustomWidgetPlugin;
import com.android.systemui.plugins.PluginListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * CustomWidgetManager handles custom widgets implemented as a plugin.
 */
public class CustomWidgetManager implements PluginListener<CustomWidgetPlugin> {

    public static final MainThreadInitializedObject<CustomWidgetManager> INSTANCE =
            new MainThreadInitializedObject<>(CustomWidgetManager::new);

    /**
     * auto provider Id is an ever-increasing number that serves as the providerId whenever a new
     * custom widget has been connected.
     */
    private int mAutoProviderId = 0;
    private final SparseArray<CustomWidgetPlugin> mPlugins;
    private final SparseArray<WeakReference<Context>> mContexts;
    private final List<CustomAppWidgetProviderInfo> mCustomWidgets;
    private final SparseArray<ComponentName> mWidgetsIdMap;
    private Consumer<PackageUserKey> mWidgetRefreshCallback;

    private CustomWidgetManager(Context context) {
        mPlugins = new SparseArray<>();
        mContexts = new SparseArray<>();
        mCustomWidgets = new ArrayList<>();
        mWidgetsIdMap = new SparseArray<>();
        PluginManagerWrapper.INSTANCE.get(context)
                .addPluginListener(this, CustomWidgetPlugin.class, true);
    }

    @Override
    public void onPluginConnected(CustomWidgetPlugin plugin, Context context) {
        mPlugins.put(mAutoProviderId, plugin);
        mContexts.put(mAutoProviderId, new WeakReference<>(context));
        List<AppWidgetProviderInfo> providers = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle());
        if (providers.isEmpty()) return;
        Parcel parcel = Parcel.obtain();
        providers.get(0).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CustomAppWidgetProviderInfo info = newInfo(mAutoProviderId, plugin, parcel, context);
        parcel.recycle();
        mCustomWidgets.add(info);
        mWidgetsIdMap.put(mAutoProviderId, info.provider);
        mWidgetRefreshCallback.accept(null);
        mAutoProviderId++;
    }

    @Override
    public void onPluginDisconnected(CustomWidgetPlugin plugin) {
        int providerId = findProviderId(plugin);
        if (providerId == -1) return;
        mPlugins.remove(providerId);
        mContexts.remove(providerId);
        mCustomWidgets.remove(getWidgetProvider(providerId));
        mWidgetsIdMap.remove(providerId);
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
        WeakReference<Context> context = mContexts.get(info.providerId);
        if (plugin == null) return;
        plugin.onViewCreated(context == null ? null : context.get(), view);
    }

    /**
     * Returns the list of custom widgets.
     */
    @NonNull
    public List<CustomAppWidgetProviderInfo> getCustomWidgets() {
        return mCustomWidgets;
    }

    /**
     * Returns the widget id for a specific provider.
     */
    public int getWidgetIdForCustomProvider(@NonNull ComponentName provider) {
        int index = mWidgetsIdMap.indexOfValue(provider);
        if (index >= 0) {
            return LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - mWidgetsIdMap.keyAt(index);
        } else {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }
    }

    /**
     * Returns the widget provider in respect to given widget id.
     */
    @Nullable
    public LauncherAppWidgetProviderInfo getWidgetProvider(int widgetId) {
        ComponentName cn = mWidgetsIdMap.get(LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - widgetId);
        for (LauncherAppWidgetProviderInfo info : mCustomWidgets) {
            if (info.provider.equals(cn)) return info;
        }
        return null;
    }

    private static CustomAppWidgetProviderInfo newInfo(int providerId, CustomWidgetPlugin plugin,
            Parcel parcel, Context context) {
        CustomAppWidgetProviderInfo info = new CustomAppWidgetProviderInfo(
                parcel, false, providerId);
        info.provider = new ComponentName(
                context.getPackageName(), CLS_CUSTOM_WIDGET_PREFIX + providerId);

        info.label = plugin.getLabel(context);
        info.resizeMode = plugin.getResizeMode(context);

        info.spanX = plugin.getSpanX(context);
        info.spanY = plugin.getSpanY(context);
        info.minSpanX = plugin.getMinSpanX(context);
        info.minSpanY = plugin.getMinSpanY(context);
        return info;
    }

    private int findProviderId(CustomWidgetPlugin plugin) {
        for (int i = 0; i < mPlugins.size(); i++) {
            int providerId = mPlugins.keyAt(i);
            if (mPlugins.get(providerId) == plugin) {
                return providerId;
            }
        }
        return -1;
    }
}
