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

package com.android.launcher3.model;

import android.graphics.Bitmap;

import com.android.launcher3.ItemInfo;

import java.util.Arrays;

/**
 * Represents a {@link Package} in the widget tray section.
 */
public class PackageItemInfo extends ItemInfo {

    /**
     * A bitmap version of the application icon.
     */
    public Bitmap iconBitmap;

    /**
     * Indicates whether we're using a low res icon.
     */
    public boolean usingLowResIcon;

    /**
     * Package name of the {@link ItemInfo}.
     */
    public String packageName;

    /**
     * Character that is used as a section name for the {@link ItemInfo#title}.
     * (e.g., "G" will be stored if title is "Google")
     */
    public String titleSectionName;

    int flags = 0;

    PackageItemInfo(String packageName) {
        this.packageName = packageName;
    }

    @Override
    public String toString() {
        return "PackageItemInfo(title=" + title + " id=" + this.id
                + " type=" + this.itemType + " container=" + this.container
                + " screen=" + screenId + " cellX=" + cellX + " cellY=" + cellY
                + " spanX=" + spanX + " spanY=" + spanY + " dropPos=" + Arrays.toString(dropPos)
                + " user=" + user + ")";
    }
}
