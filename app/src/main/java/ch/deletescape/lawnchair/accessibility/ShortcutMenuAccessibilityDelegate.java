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

package ch.deletescape.lawnchair.accessibility;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;

import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.ShortcutInfo;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutView;

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
                    mLauncher.closeFloatingContainer();
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
