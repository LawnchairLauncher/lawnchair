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
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

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
 * $ wwdebug (to turn on the logcat printout)
 * $ wwlogcat (see logcat with grep filter on)
 * $ statsd_testdrive (see how ww is writing the proto to statsd buffer)
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final String TAG = "StatsLog";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG);

    private static Context sContext;

    private static final InstanceId DEFAULT_INSTANCE_ID = InstanceId.fakeInstanceId(0);
    private static final int FOLDER_HIERARCHY_OFFSET = 100;

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
    public void log(LauncherEvent event, @Nullable LauncherAtom.ItemInfo info) {
        log(event, DEFAULT_INSTANCE_ID, info);
    }

    /**
     * Logs an event and accompanying {@link InstanceId} and {@link LauncherAtom.ItemInfo}.
     */
    @Override
    public void log(LauncherEvent event, InstanceId instanceId,
            @Nullable LauncherAtom.ItemInfo info) {
        logInternal(event, instanceId, info,
                LAUNCHER_UICHANGED__DST_STATE__HOME,
                LAUNCHER_UICHANGED__DST_STATE__BACKGROUND);
    }

    /**
     * Logs an event and accompanying {@link InstanceId} and {@link LauncherAtom.ItemInfo}.
     */
    private void logInternal(LauncherEvent event, InstanceId instanceId,
            @Nullable LauncherAtom.ItemInfo info, int startState, int endState) {
        info = info == null ? LauncherAtom.ItemInfo.getDefaultInstance() : info;

        if (IS_VERBOSE) {
            Log.d(TAG, instanceId == DEFAULT_INSTANCE_ID
                    ? String.format("\n%s\n%s", event.name(), info)
                    : String.format("%s(InstanceId:%s)\n%s", event.name(), instanceId, info));
        }

        if (!Utilities.ATLEAST_R) {
            return;
        }

        SysUiStatsLog.write(
                SysUiStatsLog.LAUNCHER_EVENT,
                SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION /* deprecated */,
                startState,
                endState,
                null /* launcher extensions, deprecated */,
                false /* quickstep_enabled, deprecated */,
                event.getId() /* event_id */,
                info.getItemCase().getNumber() /* target_id */,
                instanceId.getId() /* instance_id TODO */,
                0 /* uid TODO */,
                getPackageName(info) /* package_name */,
                getComponentName(info) /* component_name */,
                getGridX(info, false) /* grid_x */,
                getGridY(info, false) /* grid_y */,
                getPageId(info, false) /* page_id */,
                getGridX(info, true) /* grid_x_parent */,
                getGridY(info, true) /* grid_y_parent */,
                getPageId(info, true) /* page_id_parent */,
                getHierarchy(info) /* hierarchy */,
                info.getIsWork() /* is_work_profile */,
                info.getRank() /* rank */,
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

    private static void writeSnapshot(LauncherAtom.ItemInfo info) {
        if (IS_VERBOSE) {
            Log.d(TAG, "\nwriteSnapshot:" + info);
        }
        if (!Utilities.ATLEAST_R) {
            return;
        }
        SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_SNAPSHOT,
                0 /* event_id */,
                info.getItemCase().getNumber() /* target_id */,
                0 /* instance_id */,
                0 /* uid */,
                getPackageName(info) /* package_name */,
                getComponentName(info) /* component_name */,
                getGridX(info, false) /* grid_x */,
                getGridY(info, false) /* grid_y */,
                getPageId(info, false) /* page_id */,
                getGridX(info, true) /* grid_x_parent */,
                getGridY(info, true) /* grid_y_parent */,
                getPageId(info, true) /* page_id_parent */,
                getHierarchy(info) /* hierarchy */,
                info.getIsWork() /* is_work_profile */,
                0 /* origin TODO */,
                0 /* cardinality */,
                info.getWidget().getSpanX(),
                info.getWidget().getSpanY());
    }

    private static String getPackageName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getPackageName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getPackageName();
            case TASK:
                return info.getTask().getPackageName();
            default:
                return null;
        }
    }

    private static String getComponentName(LauncherAtom.ItemInfo info) {
        switch (info.getItemCase()) {
            case APPLICATION:
                return info.getApplication().getComponentName();
            case SHORTCUT:
                return info.getShortcut().getShortcutName();
            case WIDGET:
                return info.getWidget().getComponentName();
            case TASK:
                return info.getTask().getComponentName();
            default:
                return null;
        }
    }

    private static int getGridX(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getGridX();
            } else {
                return info.getContainerInfo().getFolder().getGridX();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getGridX();
        }
    }

    private static int getGridY(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getGridY();
            } else {
                return info.getContainerInfo().getFolder().getGridY();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getGridY();
        }
    }

    private static int getPageId(LauncherAtom.ItemInfo info, boolean parent) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            if (parent) {
                return info.getContainerInfo().getFolder().getWorkspace().getPageIndex();
            } else {
                return info.getContainerInfo().getFolder().getPageIndex();
            }
        } else {
            return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getHierarchy(LauncherAtom.ItemInfo info) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            return info.getContainerInfo().getFolder().getParentContainerCase().getNumber()
                    + FOLDER_HIERARCHY_OFFSET;
        } else {
            return info.getContainerInfo().getContainerCase().getNumber();
        }
    }
}
