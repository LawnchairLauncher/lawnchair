/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.widget.picker;

import androidx.recyclerview.widget.RecyclerView.ViewHolder;

/**
 * A {@link ViewHolder} for {@link WidgetsListHeader} of an app, which renders the app icon, the app
 * name, label and a button for showing / hiding widgets.
 */
public final class WidgetsListHeaderHolder extends ViewHolder {
    final WidgetsListHeader mWidgetsListHeader;

    public WidgetsListHeaderHolder(WidgetsListHeader view) {
        super(view);

        mWidgetsListHeader = view;
    }
}
