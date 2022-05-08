/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.ComponentWithLabelAndIcon;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {

    // True is the widget support is disabled.
    public static final boolean GO_DISABLE_WIDGETS = true;
    public static final boolean GO_DISABLE_NOTIFICATION_DOTS = true;

    private static final ArrayList<WidgetsListBaseEntry> EMPTY_WIDGET_LIST = new ArrayList<>();

    /**
     * Returns a list of {@link WidgetsListBaseEntry}. All {@link WidgetItem} in a single row are
     * sorted (based on label and user), but the overall list of {@link WidgetsListBaseEntry}s is
     * not sorted. This list is sorted at the UI when using
     * {@link com.android.launcher3.widget.picker.WidgetsDiffReporter}
     *
     * @see com.android.launcher3.widget.picker.WidgetsListAdapter#setWidgets(List)
     */
    public synchronized ArrayList<WidgetsListBaseEntry> getWidgetsListForPicker(Context context) {
        return EMPTY_WIDGET_LIST;
    }

    /** Returns a mapping of packages to their widgets without static shortcuts. */
    public synchronized Map<PackageUserKey, List<WidgetItem>> getAllWidgetsWithoutShortcuts() {
        return Map.of();
    }

    /**
     * @param packageUser If null, all widgets and shortcuts are updated and returned, otherwise
     *                    only widgets and shortcuts associated with the package/user are.
     */
    public List<ComponentWithLabelAndIcon> update(LauncherAppState app,
            @Nullable PackageUserKey packageUser) {
        return Collections.emptyList();
    }


    public void onPackageIconsUpdated(Set<String> packageNames, UserHandle user,
            LauncherAppState app) {
    }

    public WidgetItem getWidgetProviderInfoByProviderName(
            ComponentName providerName, UserHandle user) {
        return null;
    }

    /** Returns {@link PackageItemInfo} of a pending widget. */
    public static PackageItemInfo newPendingItemInfo(
            Context context, ComponentName provider, UserHandle userHandle) {
        return new PackageItemInfo(provider.getPackageName(), userHandle);
    }
}