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

import com.android.launcher3.ItemInfoWithIcon;

/**
 * Represents a {@link Package} in the widget tray section.
 */
public class PackageItemInfo extends ItemInfoWithIcon {

    /**
     * Package name of the {@link PackageItemInfo}.
     */
    public String packageName;

    public PackageItemInfo(String packageName) {
        this.packageName = packageName;
    }

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " packageName=" + packageName;
    }
}
