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

import android.content.Context;
import android.os.UserHandle;

import com.android.launcher3.icons.ComponentWithLabel;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;

/**
 * Widgets data model that is used by the adapters of the widget views and controllers.
 *
 * <p> The widgets and shortcuts are organized using package name as its index.
 */
public class WidgetsModel {
    private static final ArrayList<WidgetListRowEntry> EMPTY_WIDGET_LIST = new ArrayList<>();

    /**
     * Returns a list of {@link WidgetListRowEntry}. All {@link WidgetItem} in a single row
     * are sorted (based on label and user), but the overall list of {@link WidgetListRowEntry}s
     * is not sorted. This list is sorted at the UI when using
     * {@link com.android.launcher3.widget.WidgetsDiffReporter}
     *
     * @see com.android.launcher3.widget.WidgetsListAdapter#setWidgets(ArrayList)
     */
    public synchronized ArrayList<WidgetListRowEntry> getWidgetsList(Context context) {
        return EMPTY_WIDGET_LIST;
    }

    /**
     * @param packageUser If null, all widgets and shortcuts are updated and returned, otherwise
     *                    only widgets and shortcuts associated with the package/user are.
     */
    public List<ComponentWithLabel> update(LauncherAppState app,
            @Nullable PackageUserKey packageUser) {
        return Collections.emptyList();
    }


    public void onPackageIconsUpdated(Set<String> packageNames, UserHandle user,
            LauncherAppState app) {
    }
}