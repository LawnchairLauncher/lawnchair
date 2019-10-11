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
package com.android.launcher3.folder;

import android.content.ComponentName;
import android.content.Context;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.WorkspaceItemInfo;

import java.util.ArrayList;

/**
 * Locates provider for the folder name.
 */
public class FolderNameProvider {

    /**
     * Returns suggested folder name.
     */
    public CharSequence getSuggestedFolderName(Context context,
            ArrayList<WorkspaceItemInfo> workspaceItemInfos, CharSequence[] suggestName) {
        // Currently only run the algorithm on initial folder creation.
        // For more than 2 items in the folder, the ranking algorithm for finding
        // candidate folder name should be rewritten.
        if (workspaceItemInfos.size() == 2) {
            ComponentName cmp1 = workspaceItemInfos.get(0).getTargetComponent();
            ComponentName cmp2 = workspaceItemInfos.get(1).getTargetComponent();

            String pkgName0 = cmp1 == null ? "" : cmp1.getPackageName();
            String pkgName1 = cmp2 == null ? "" : cmp2.getPackageName();
            // If the two icons are from the same package,
            // then assign the main icon's name
            if (pkgName0.equals(pkgName1)) {
                WorkspaceItemInfo wInfo0 = workspaceItemInfos.get(0);
                WorkspaceItemInfo wInfo1 = workspaceItemInfos.get(1);
                if (workspaceItemInfos.get(0).itemType == Favorites.ITEM_TYPE_APPLICATION) {
                    suggestName[0] = wInfo0.title;
                } else if (wInfo1.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                    suggestName[0] = wInfo1.title;
                }
                return suggestName[0];
                // two icons are all shortcuts. Don't assign title
            }
        }
        return suggestName[0];
    }
}
