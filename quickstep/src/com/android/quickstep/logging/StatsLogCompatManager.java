/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep.logging;

import static android.stats.launcher.nano.Launcher.ALLAPPS;
import static android.stats.launcher.nano.Launcher.BACKGROUND;
import static android.stats.launcher.nano.Launcher.HOME;
import static android.stats.launcher.nano.Launcher.OVERVIEW;

import android.content.Context;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogUtils;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.IntSparseArrayMap;

import java.util.ArrayList;

/**
 * This method calls the StatsLog hidden method until they are made available public.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * $ adb root && adb shell statsd
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd  OR $ adb logcat -b stats
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final int SUPPORTED_TARGET_DEPTH = 2;
    private static final String TAG = "StatsLog";
    private static final boolean DEBUG = false;
    private static Context sContext;

    public StatsLogCompatManager(Context context) {
        sContext = context;
    }

    @Override
    public void verify() {
        if (!(StatsLogUtils.LAUNCHER_STATE_ALLAPPS == ALLAPPS
                && StatsLogUtils.LAUNCHER_STATE_BACKGROUND == BACKGROUND
                && StatsLogUtils.LAUNCHER_STATE_OVERVIEW == OVERVIEW
                && StatsLogUtils.LAUNCHER_STATE_HOME == HOME)) {
            throw new IllegalStateException(
                    "StatsLogUtil constants doesn't match enums in launcher.proto");
        }
    }

    /**
     * Logs the workspace layout information on the model thread.
     */
    public void logSnapshot() {
        LauncherAppState.getInstance(sContext).getModel().enqueueModelUpdateTask(
                new SnapshotWorker());
    }

    private class SnapshotWorker extends BaseModelUpdateTask {
        @Override
        public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
            IntSparseArrayMap<FolderInfo> folders = dataModel.folders.clone();
            ArrayList<ItemInfo> workspaceItems = (ArrayList) dataModel.workspaceItems.clone();
            ArrayList<LauncherAppWidgetInfo> appWidgets = (ArrayList) dataModel.appWidgets.clone();

            for (ItemInfo info : workspaceItems) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                // call StatsLog method
            }
            for (FolderInfo fInfo : folders) {
                for (ItemInfo info : fInfo.contents) {
                    LauncherAtom.ItemInfo atomInfo = info.buildProto(fInfo);
                    // call StatsLog method
                }
            }
            for (ItemInfo info : appWidgets) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                // call StatsLog method
            }
        }
    }
}
