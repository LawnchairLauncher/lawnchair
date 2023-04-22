/*
 * Copyright 2023 The Android Open Source Project
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


package com.android.quickstep.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.quickstep.views.TaskView;

/**
 * Mini controller class that handles app pair interactions: saving, modifying, deleting, etc.
 */
public class AppPairsController {

    private static final int POINT_THREE_RATIO = 0;
    private static final int POINT_FIVE_RATIO = 1;
    private static final int POINT_SEVEN_RATIO = 2;
    /**
     * Used to calculate {@link #complement(int)}
     */
    private static final int FULL_RATIO = 2;

    private static final int LEFT_TOP = 0;
    private static final int RIGHT_BOTTOM = 1 << 2;

    // TODO (jeremysim b/274189428): Support saving different ratios in future.
    public int DEFAULT_RATIO = POINT_FIVE_RATIO;

    private final Context mContext;
    private final SplitSelectStateController mSplitSelectStateController;
    public AppPairsController(Context context,
            SplitSelectStateController splitSelectStateController) {
        mContext = context;
        mSplitSelectStateController = splitSelectStateController;
    }

    /**
     * Creates a new app pair ItemInfo and adds it to the workspace
     */
    public void saveAppPair(TaskView taskView) {
        TaskView.TaskIdAttributeContainer[] attributes = taskView.getTaskIdAttributeContainers();
        WorkspaceItemInfo app1 = attributes[0].getItemInfo().clone();
        WorkspaceItemInfo app2 = attributes[1].getItemInfo().clone();
        app1.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        app2.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        app1.rank = DEFAULT_RATIO + LEFT_TOP;
        app2.rank = complement(DEFAULT_RATIO) + RIGHT_BOTTOM;
        FolderInfo newAppPair = FolderInfo.createAppPair(app1, app2);
        // TODO (jeremysim b/274189428): Generate default title here.
        newAppPair.title = "App pair 1";

        IconCache iconCache = LauncherAppState.getInstance(mContext).getIconCache();
        MODEL_EXECUTOR.execute(() -> {
            newAppPair.contents.forEach(member -> {
                member.title = "";
                member.bitmap = iconCache.getDefaultIcon(newAppPair.user);
                iconCache.getTitleAndIcon(member, member.usingLowResIcon());
            });
            MAIN_EXECUTOR.execute(() -> {
                LauncherAccessibilityDelegate delegate =
                        Launcher.getLauncher(mContext).getAccessibilityDelegate();
                if (delegate != null) {
                    MAIN_EXECUTOR.execute(() -> delegate.addToWorkspace(newAppPair, true));
                }
            });
        });

    }

    /**
     * Used to calculate the "opposite" side of the split ratio, so we can know how big the split
     * apps are supposed to be. This math works because POINT_THREE_RATIO is internally represented
     * by 0, POINT_FIVE_RATIO is represented by 1, and POINT_SEVEN_RATIO is represented by 2. There
     * are no other supported ratios for now.
     */
    private int complement(int ratio1) {
        int ratio2 = FULL_RATIO - ratio1;
        return ratio2;
    }
}
