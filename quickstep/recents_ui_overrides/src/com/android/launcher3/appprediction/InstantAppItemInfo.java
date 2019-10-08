/**
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

package com.android.launcher3.appprediction;

import static com.android.quickstep.InstantAppResolverImpl.COMPONENT_CLASS_MARKER;

import android.content.ComponentName;
import android.content.Intent;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.WorkspaceItemInfo;

public class InstantAppItemInfo extends AppInfo {

    public InstantAppItemInfo(Intent intent, String packageName) {
        this.intent = intent;
        this.componentName = new ComponentName(packageName, COMPONENT_CLASS_MARKER);
    }

    @Override
    public ComponentName getTargetComponent() {
        return componentName;
    }

    @Override
    public WorkspaceItemInfo makeWorkspaceItem() {
        WorkspaceItemInfo workspaceItemInfo = super.makeWorkspaceItem();
        workspaceItemInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        workspaceItemInfo.status = WorkspaceItemInfo.FLAG_AUTOINSTALL_ICON
                | WorkspaceItemInfo.FLAG_RESTORE_STARTED
                | WorkspaceItemInfo.FLAG_SUPPORTS_WEB_UI;
        workspaceItemInfo.intent.setPackage(componentName.getPackageName());
        return workspaceItemInfo;
    }
}
