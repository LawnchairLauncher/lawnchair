/*
 * Copyright (C) 2015 The Android Open Source Project
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

import androidx.annotation.IntDef;

import com.android.launcher3.LauncherSettings;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Represents a {@link Package} in the widget tray section.
 */
public class PackageItemInfo extends ItemInfoWithIcon {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({NO_CATEGORY, CONVERSATIONS})
    public @interface Category{}
    /** The package is not categorized in the widget tray. */
    public static final int NO_CATEGORY = 0;
    /** The package is categorized to conversations widget in the widget tray. */
    public static final int CONVERSATIONS = 1;

    /**
     * Package name of the {@link PackageItemInfo}.
     */
    public final String packageName;

    /** Represents a widget category shown in the widget tray section. */
    @Category public final int category;

    public PackageItemInfo(String packageName) {
        this(packageName, NO_CATEGORY);
    }

    public PackageItemInfo(String packageName, @Category int category) {
        this.packageName = packageName;
        this.category = category;
        this.itemType = LauncherSettings.Favorites.ITEM_TYPE_NON_ACTIONABLE;
    }

    public PackageItemInfo(PackageItemInfo copy) {
        this.packageName = copy.packageName;
        this.category = copy.category;
        this.itemType = LauncherSettings.Favorites.ITEM_TYPE_NON_ACTIONABLE;
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " packageName=" + packageName;
    }

    @Override
    public PackageItemInfo clone() {
        return new PackageItemInfo(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PackageItemInfo that = (PackageItemInfo) o;
        return Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packageName, user);
    }
}
