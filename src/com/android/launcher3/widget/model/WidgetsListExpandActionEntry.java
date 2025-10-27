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

package com.android.launcher3.widget.model;

import android.os.Process;

import com.android.launcher3.model.data.PackageItemInfo;

import java.util.Collections;

/**
 * Binds the section to be displayed at the bottom of the widgets list that enables user to expand
 * and view all the widget apps including non-default. Bound when
 * {@link WidgetsListExpandActionEntry} exists in the list on adapter.
 */
public class WidgetsListExpandActionEntry extends WidgetsListBaseEntry {

    public WidgetsListExpandActionEntry() {
        super(/*pkgItem=*/ new PackageItemInfo(/* packageName= */ "", Process.myUserHandle()),
                /*titleSectionName=*/ "",
                /*items=*/ Collections.EMPTY_LIST);
        mPkgItem.title = "";
    }

    @Override
    public WidgetsListBaseEntry copy() {
        return new WidgetsListExpandActionEntry();
    }
}
