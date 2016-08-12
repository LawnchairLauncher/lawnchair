/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.accessibility;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.shortcuts.DeepShortcutView;

import java.util.ArrayList;

/**
 * Extension of {@link LauncherAccessibilityDelegate} with actions specific to shortcuts in
 * deep shortcuts menu.
 */
public class ShortcutMenuAccessibilityDelegate extends LauncherAccessibilityDelegate {

    public ShortcutMenuAccessibilityDelegate(Launcher launcher) {
        super(launcher);
    }

    @Override
    protected void addActions(View host, AccessibilityNodeInfo info) {
        info.addAction(mActions.get(ADD_TO_WORKSPACE));
    }

    @Override
    public boolean performAction(View host, ItemInfo item, int action) {
        if (action == ADD_TO_WORKSPACE) {
            if (!(host.getParent() instanceof DeepShortcutView)) {
                return false;
            }
            final ShortcutInfo info = ((DeepShortcutView) host.getParent()).getFinalInfo();
            final int[] coordinates = new int[2];
            final long screenId = findSpaceOnWorkspace(item, coordinates);
            Runnable onComplete = new Runnable() {
                @Override
                public void run() {
                    LauncherModel.addItemToDatabase(mLauncher, info,
                            LauncherSettings.Favorites.CONTAINER_DESKTOP,
                            screenId, coordinates[0], coordinates[1]);
                    ArrayList<ItemInfo> itemList = new ArrayList<>();
                    itemList.add(info);
                    mLauncher.bindItems(itemList, 0, itemList.size(), true);
                    mLauncher.closeShortcutsContainer();
                    announceConfirmation(R.string.item_added_to_workspace);
                }
            };

            if (!mLauncher.showWorkspace(true, onComplete)) {
                onComplete.run();
            }
            return true;
        }
        return false;
    }
}
