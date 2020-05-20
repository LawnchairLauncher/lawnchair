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

import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER;
import static com.android.launcher3.logger.LauncherAtom.ItemInfo.ItemCase.WIDGET;

import android.content.Context;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.LogConfig;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.ArrayList;

/**
 * This class calls StatsLog compile time generated methods.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * $ adb root && adb shell statsd
 * $ adb shell cmd stats print-logs
 * $ adb logcat | grep statsd  OR $ adb logcat -b stats
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final String TAG = "StatsLog";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG);

    private static Context sContext;

    private static final int DEFAULT_WIDGET_SPAN_XY = 1;
    private static final int DEFAULT_WORKSPACE_GRID_XY = -1;
    private static final int DEFAULT_PAGE_INDEX = -2;
    private static final InstanceId DEFAULT_INSTANCE_ID = InstanceId.fakeInstanceId(0);

    public StatsLogCompatManager(Context context) {
        sContext = context;
    }

    /**
     * Logs a {@link LauncherEvent}.
     */
    @Override
    public void log(LauncherEvent event) {
        log(event, DEFAULT_INSTANCE_ID, LauncherAtom.ItemInfo.getDefaultInstance());
    }

    /**
     * Logs an event and accompanying {@link InstanceId}.
     */
    @Override
    public void log(LauncherEvent event, InstanceId instanceId) {
        log(event, instanceId, LauncherAtom.ItemInfo.getDefaultInstance());
    }

    /**
     * Logs an event and accompanying {@link ItemInfo}.
     */
    @Override
    public void log(LauncherEvent event, LauncherAtom.ItemInfo itemInfo) {
        log(event, DEFAULT_INSTANCE_ID, itemInfo);
    }

    /**
     * Logs an event and accompanying {@link InstanceId} and {@link LauncherAtom.ItemInfo}.
     */
    @Override
    public void log(LauncherEvent event, InstanceId instanceId, LauncherAtom.ItemInfo itemInfo) {
        if (IS_VERBOSE) {
            Log.d(TAG, instanceId == DEFAULT_INSTANCE_ID
                    ? String.format("\n%s\n%s", event.name(), itemInfo)
                    : String.format("%s(InstanceId:%s)\n%s", event.name(), instanceId, itemInfo));
        }

        if (!Utilities.ATLEAST_R) {
            return;
        }

        SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_EVENT,
                SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION /* deprecated */,
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME /* TODO */,
                SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND /* TODO */,
                null /* launcher extensions, deprecated */,
                false /* quickstep_enabled, deprecated */,
                event.getId() /* event_id */,
                itemInfo.getItemCase().getNumber() /* target_id */,
                instanceId.getId() /* instance_id TODO */,
                0 /* uid TODO */,
                getPackageName(itemInfo) /* package_name */,
                getComponentName(itemInfo) /* component_name */,
                getGridX(itemInfo, false) /* grid_x */,
                getGridY(itemInfo, false) /* grid_y */,
                getPageId(itemInfo, false) /* page_id */,
                getGridX(itemInfo, true) /* grid_x_parent */,
                getGridY(itemInfo, true) /* grid_y_parent */,
                getPageId(itemInfo, true) /* page_id_parent */,
                getHierarchy(itemInfo) /* hierarchy */,
                itemInfo.getIsWork() /* is_work_profile */,
                itemInfo.getRank() /* rank */,
                0 /* fromState */,
                0 /* toState */,
                null /* edittext */,
                0 /* cardinality */);
    }

    /**
     * Logs the workspace layout information on the model thread.
     */
    @Override
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
                writeSnapshot(atomInfo);
            }
            for (FolderInfo fInfo : folders) {
                for (ItemInfo info : fInfo.contents) {
                    LauncherAtom.ItemInfo atomInfo = info.buildProto(fInfo);
                    writeSnapshot(atomInfo);
                }
            }
            for (ItemInfo info : appWidgets) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                writeSnapshot(atomInfo);
            }
        }
    }

    private static void writeSnapshot(LauncherAtom.ItemInfo itemInfo) {
        if (IS_VERBOSE) {
            Log.d(TAG, "\nwriteSnapshot:" + itemInfo);
        }
        if (!Utilities.ATLEAST_R) {
            return;
        }
        SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_SNAPSHOT,
                0 /* event_id */,
                itemInfo.getItemCase().getNumber() /* target_id */,
                0 /* instance_id */,
                0 /* uid */,
                getPackageName(itemInfo) /* package_name */,
                getComponentName(itemInfo) /* component_name */,
                getGridX(itemInfo, false) /* grid_x */,
                getGridY(itemInfo, false) /* grid_y */,
                getPageId(itemInfo, false) /* page_id */,
                getGridX(itemInfo, true) /* grid_x_parent */,
                getGridY(itemInfo, true) /* grid_y_parent */,
                getPageId(itemInfo, true) /* page_id_parent */,
                getHierarchy(itemInfo) /* hierarchy */,
                itemInfo.getIsWork() /* is_work_profile */,
                0 /* origin TODO */,
                0 /* cardinality */,
                getSpanX(itemInfo),
                getSpanY(itemInfo));
    }

    private static int getSpanX(LauncherAtom.ItemInfo atomInfo) {
        if (atomInfo.getItemCase() != WIDGET) {
            return DEFAULT_WIDGET_SPAN_XY;
        }
        return atomInfo.getWidget().getSpanX();
    }

    private static int getSpanY(LauncherAtom.ItemInfo atomInfo) {
        if (atomInfo.getItemCase() != WIDGET) {
            return DEFAULT_WIDGET_SPAN_XY;
        }
        return atomInfo.getWidget().getSpanY();
    }

    private static String getPackageName(LauncherAtom.ItemInfo atomInfo) {
        switch (atomInfo.getItemCase()) {
            case APPLICATION:
                return atomInfo.getApplication().getPackageName();
            case SHORTCUT:
                return atomInfo.getShortcut().getShortcutName();
            case WIDGET:
                return atomInfo.getWidget().getPackageName();
            case TASK:
                return atomInfo.getTask().getPackageName();
            default:
                return null;
        }
    }

    private static String getComponentName(LauncherAtom.ItemInfo atomInfo) {
        switch (atomInfo.getItemCase()) {
            case APPLICATION:
                return atomInfo.getApplication().getComponentName();
            case SHORTCUT:
                return atomInfo.getShortcut().getShortcutName();
            case WIDGET:
                return atomInfo.getWidget().getComponentName();
            case TASK:
                return atomInfo.getTask().getComponentName();
            default:
                return null;
        }
    }

    private static int getGridX(LauncherAtom.ItemInfo info, boolean parent) {
        switch (info.getContainerInfo().getContainerCase()) {
            case WORKSPACE:
                if (parent) {
                    return DEFAULT_WORKSPACE_GRID_XY;
                } else {
                    return info.getContainerInfo().getWorkspace().getGridX();
                }
            case FOLDER:
                if (parent) {
                    switch (info.getContainerInfo().getFolder().getParentContainerCase()) {
                        case WORKSPACE:
                            return info.getContainerInfo().getFolder().getWorkspace().getGridX();
                        default:
                            return DEFAULT_WORKSPACE_GRID_XY;
                    }
                } else {
                    return info.getContainerInfo().getFolder().getGridX();
                }
            default:
                return DEFAULT_WORKSPACE_GRID_XY;
        }
    }

    private static int getGridY(LauncherAtom.ItemInfo info, boolean parent) {
        switch (info.getContainerInfo().getContainerCase()) {
            case WORKSPACE:
                if (parent) {
                    return DEFAULT_WORKSPACE_GRID_XY;
                } else {
                    return info.getContainerInfo().getWorkspace().getGridY();
                }
            case FOLDER:
                if (parent) {
                    switch (info.getContainerInfo().getFolder().getParentContainerCase()) {
                        case WORKSPACE:
                            return info.getContainerInfo().getFolder().getWorkspace().getGridY();
                        default:
                            return DEFAULT_WORKSPACE_GRID_XY;
                    }
                } else {
                    return info.getContainerInfo().getFolder().getGridY();
                }
            default:
                return DEFAULT_WORKSPACE_GRID_XY;
        }
    }

    private static int getPageId(LauncherAtom.ItemInfo info, boolean parent) {
        switch (info.getContainerInfo().getContainerCase()) {
            case HOTSEAT:
                return info.getContainerInfo().getHotseat().getIndex();
            case WORKSPACE:
                return info.getContainerInfo().getWorkspace().getPageIndex();
            default:
                return DEFAULT_PAGE_INDEX;
        }
    }

    /**
     *
     */
    private static int getHierarchy(LauncherAtom.ItemInfo info) {
        // TODO
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            return info.getContainerInfo().getFolder().getParentContainerCase().getNumber() + 100;
        } else {
            return info.getContainerInfo().getContainerCase().getNumber();
        }
    }
}
