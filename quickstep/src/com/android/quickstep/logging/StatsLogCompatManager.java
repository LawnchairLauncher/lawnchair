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

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.formatElapsedTime;

import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.FOLDER;
import static com.android.launcher3.logger.LauncherAtom.ContainerInfo.ContainerCase.SEARCH_RESULT_CONTAINER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WORKSPACE_SNAPSHOT;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__ALLAPPS;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__BACKGROUND;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__HOME;
import static com.android.systemui.shared.system.SysUiStatsLog.LAUNCHER_UICHANGED__DST_STATE__OVERVIEW;

import static java.lang.System.currentTimeMillis;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FolderContainer.ParentContainerCase;
import com.android.launcher3.logger.LauncherAtom.FolderIcon;
import com.android.launcher3.logger.LauncherAtom.FromState;
import com.android.launcher3.logger.LauncherAtom.ToState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.LogConfig;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * This class calls StatsLog compile time generated methods.
 *
 * To see if the logs are properly sent to statsd, execute following command.
 * <ul>
 * $ wwdebug (to turn on the logcat printout)
 * $ wwlogcat (see logcat with grep filter on)
 * $ statsd_testdrive (see how ww is writing the proto to statsd buffer)
 * </ul>
 */
public class StatsLogCompatManager extends StatsLogManager {

    private static final String TAG = "StatsLog";
    private static final boolean IS_VERBOSE = Utilities.isPropertyEnabled(LogConfig.STATSLOG);

    private static final String LAST_SNAPSHOT_TIME_MILLIS = "LAST_SNAPSHOT_TIME_MILLIS";
    private static final InstanceId DEFAULT_INSTANCE_ID = InstanceId.fakeInstanceId(0);
    // LauncherAtom.ItemInfo.getDefaultInstance() should be used but until launcher proto migrates
    // from nano to lite, bake constant to prevent robo test failure.
    private static final int DEFAULT_PAGE_INDEX = -2;
    private static final int FOLDER_HIERARCHY_OFFSET = 100;
    private static final int SEARCH_RESULT_HIERARCHY_OFFSET = 200;

    private final Context mContext;

    public StatsLogCompatManager(Context context) {
        mContext = context;
    }

    @Override
    public StatsLogger logger() {
        return new StatsCompatLogger();
    }

    /**
     * Logs a ranking event and accompanying {@link InstanceId} and package name.
     */
    @Override
    public void log(EventEnum rankingEvent, InstanceId instanceId, @Nullable String packageName,
            int position) {
        SysUiStatsLog.write(SysUiStatsLog.RANKING_SELECTED,
                rankingEvent.getId() /* event_id = 1; */,
                packageName /* package_name = 2; */,
                instanceId.getId() /* instance_id = 3; */,
                position /* position_picked = 4; */);
    }

    /**
     * Logs impression of the current workspace with additional launcher events.
     */
    @Override
    public void logSnapshot(List<EventEnum> extraEvents) {
        LauncherAppState.getInstance(mContext).getModel().enqueueModelUpdateTask(
                new SnapshotWorker(extraEvents));
    }

    private class SnapshotWorker extends BaseModelUpdateTask {
        private final InstanceId mInstanceId;
        private final List<EventEnum> mExtraEvents;

        SnapshotWorker(List<EventEnum> extraEvents) {
            mInstanceId = new InstanceIdSequence(1 << 20 /*InstanceId.INSTANCE_ID_MAX*/)
                    .newInstanceId();
            this.mExtraEvents = extraEvents;
        }

        @Override
        public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
            long lastSnapshotTimeMillis = getDevicePrefs(mContext)
                    .getLong(LAST_SNAPSHOT_TIME_MILLIS, 0);
            // Log snapshot only if previous snapshot was older than a day
            if (currentTimeMillis() - lastSnapshotTimeMillis < DAY_IN_MILLIS) {
                if (IS_VERBOSE) {
                    String elapsedTime = formatElapsedTime(
                            (currentTimeMillis() - lastSnapshotTimeMillis) / 1000);
                    Log.d(TAG, String.format(
                            "Skipped snapshot logging since previous snapshot was %s old.",
                            elapsedTime));
                }
                return;
            }

            IntSparseArrayMap<FolderInfo> folders = dataModel.folders.clone();
            ArrayList<ItemInfo> workspaceItems = (ArrayList) dataModel.workspaceItems.clone();
            ArrayList<LauncherAppWidgetInfo> appWidgets = (ArrayList) dataModel.appWidgets.clone();
            for (ItemInfo info : workspaceItems) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                writeSnapshot(atomInfo, mInstanceId);
            }
            for (FolderInfo fInfo : folders) {
                try {
                    ArrayList<WorkspaceItemInfo> folderContents =
                            (ArrayList) Executors.MAIN_EXECUTOR.submit(fInfo.contents::clone).get();
                    for (ItemInfo info : folderContents) {
                        LauncherAtom.ItemInfo atomInfo = info.buildProto(fInfo);
                        writeSnapshot(atomInfo, mInstanceId);
                    }
                } catch (Exception e) {
                }
            }
            for (ItemInfo info : appWidgets) {
                LauncherAtom.ItemInfo atomInfo = info.buildProto(null);
                writeSnapshot(atomInfo, mInstanceId);
            }
            mExtraEvents
                    .forEach(eventName -> logger().withInstanceId(mInstanceId).log(eventName));

            getDevicePrefs(mContext).edit()
                    .putLong(LAST_SNAPSHOT_TIME_MILLIS, currentTimeMillis()).apply();
        }
    }

    private void writeSnapshot(LauncherAtom.ItemInfo info, InstanceId instanceId) {
        if (IS_VERBOSE) {
            Log.d(TAG, String.format("\nwriteSnapshot(%d):\n%s", instanceId.getId(), info));
        }
        if (!Utilities.ATLEAST_R) {
            return;
        }
        SysUiStatsLog.write(SysUiStatsLog.LAUNCHER_SNAPSHOT,
                LAUNCHER_WORKSPACE_SNAPSHOT.getId() /* event_id */,
                info.getItemCase().getNumber() /* target_id */,
                instanceId.getId() /* instance_id */,
                0 /* uid */,
                getPackageName(info) /* package_name */,
                getComponentName(info) /* component_name */,
                getGridX(info, false) /* grid_x */,
                getGridY(info, false) /* grid_y */,
                getPageId(info) /* page_id */,
                getGridX(info, true) /* grid_x_parent */,
                getGridY(info, true) /* grid_y_parent */,
                getParentPageId(info) /* page_id_parent */,
                getHierarchy(info) /* hierarchy */,
                info.getIsWork() /* is_work_profile */,
                info.getAttribute().getNumber() /* origin */,
                getCardinality(info) /* cardinality */,
                info.getWidget().getSpanX(),
                info.getWidget().getSpanY());
    }

    /**
     * Helps to construct and write statsd compatible log message.
     */
    private static class StatsCompatLogger implements StatsLogger {

        private static final ItemInfo DEFAULT_ITEM_INFO = new ItemInfo();
        private ItemInfo mItemInfo = DEFAULT_ITEM_INFO;
        private InstanceId mInstanceId = DEFAULT_INSTANCE_ID;
        private OptionalInt mRank = OptionalInt.empty();
        private Optional<ContainerInfo> mContainerInfo = Optional.empty();
        private int mSrcState = LAUNCHER_STATE_UNSPECIFIED;
        private int mDstState = LAUNCHER_STATE_UNSPECIFIED;
        private Optional<FromState> mFromState = Optional.empty();
        private Optional<ToState> mToState = Optional.empty();
        private Optional<String> mEditText = Optional.empty();

        @Override
        public StatsLogger withItemInfo(ItemInfo itemInfo) {
            if (mContainerInfo.isPresent()) {
                throw new IllegalArgumentException(
                        "ItemInfo and ContainerInfo are mutual exclusive; cannot log both.");
            }
            this.mItemInfo = itemInfo;
            return this;
        }

        @Override
        public StatsLogger withInstanceId(InstanceId instanceId) {
            this.mInstanceId = instanceId;
            return this;
        }

        @Override
        public StatsLogger withRank(int rank) {
            this.mRank = OptionalInt.of(rank);
            return this;
        }

        @Override
        public StatsLogger withSrcState(int srcState) {
            this.mSrcState = srcState;
            return this;
        }

        @Override
        public StatsLogger withDstState(int dstState) {
            this.mDstState = dstState;
            return this;
        }

        @Override
        public StatsLogger withContainerInfo(ContainerInfo containerInfo) {
            if (mItemInfo != DEFAULT_ITEM_INFO) {
                throw new IllegalArgumentException(
                        "ItemInfo and ContainerInfo are mutual exclusive; cannot log both.");
            }
            this.mContainerInfo = Optional.of(containerInfo);
            return this;
        }

        @Override
        public StatsLogger withFromState(FromState fromState) {
            this.mFromState = Optional.of(fromState);
            return this;
        }

        @Override
        public StatsLogger withToState(ToState toState) {
            this.mToState = Optional.of(toState);
            return this;
        }

        @Override
        public StatsLogger withEditText(String editText) {
            this.mEditText = Optional.of(editText);
            return this;
        }

        @Override
        public void log(EventEnum event) {
            if (!Utilities.ATLEAST_R) {
                return;
            }

            if (mItemInfo.container < 0) {
                // Item is not within a folder. Write to StatsLog in same thread.
                write(event, mInstanceId, applyOverwrites(mItemInfo.buildProto()), mSrcState,
                        mDstState);
            } else {
                // Item is inside the folder, fetch folder info in a BG thread
                // and then write to StatsLog.
                LauncherAppState.getInstanceNoCreate().getModel().enqueueModelUpdateTask(
                        new BaseModelUpdateTask() {
                            @Override
                            public void execute(LauncherAppState app, BgDataModel dataModel,
                                    AllAppsList apps) {
                                FolderInfo folderInfo = dataModel.folders.get(mItemInfo.container);
                                write(event, mInstanceId,
                                        applyOverwrites(mItemInfo.buildProto(folderInfo)),
                                        mSrcState, mDstState);
                            }
                        });
            }
        }

        private LauncherAtom.ItemInfo applyOverwrites(LauncherAtom.ItemInfo atomInfo) {
            LauncherAtom.ItemInfo.Builder itemInfoBuilder =
                    (LauncherAtom.ItemInfo.Builder) atomInfo.toBuilder();

            mRank.ifPresent(itemInfoBuilder::setRank);
            mContainerInfo.ifPresent(itemInfoBuilder::setContainerInfo);

            if (mFromState.isPresent() || mToState.isPresent() || mEditText.isPresent()) {
                FolderIcon.Builder folderIconBuilder = (FolderIcon.Builder) itemInfoBuilder
                        .getFolderIcon()
                        .toBuilder();
                mFromState.ifPresent(folderIconBuilder::setFromLabelState);
                mToState.ifPresent(folderIconBuilder::setToLabelState);
                mEditText.ifPresent(folderIconBuilder::setLabelInfo);
                itemInfoBuilder.setFolderIcon(folderIconBuilder);
            }
            return itemInfoBuilder.build();
        }

        private void write(EventEnum event, InstanceId instanceId, LauncherAtom.ItemInfo atomInfo,
                int srcState, int dstState) {
            if (IS_VERBOSE) {
                String name = (event instanceof Enum) ? ((Enum) event).name() :
                        event.getId() + "";

                Log.d(TAG, instanceId == DEFAULT_INSTANCE_ID
                        ? String.format("\n%s (State:%s->%s)\n%s", name, getStateString(srcState),
                        getStateString(dstState), atomInfo)
                        : String.format("\n%s (State:%s->%s) (InstanceId:%s)\n%s", name,
                                getStateString(srcState), getStateString(dstState), instanceId,
                                atomInfo));
            }

            SysUiStatsLog.write(
                    SysUiStatsLog.LAUNCHER_EVENT,
                    SysUiStatsLog.LAUNCHER_UICHANGED__ACTION__DEFAULT_ACTION /* deprecated */,
                    srcState,
                    dstState,
                    null /* launcher extensions, deprecated */,
                    false /* quickstep_enabled, deprecated */,
                    event.getId() /* event_id */,
                    atomInfo.getItemCase().getNumber() /* target_id */,
                    instanceId.getId() /* instance_id TODO */,
                    0 /* uid TODO */,
                    getPackageName(atomInfo) /* package_name */,
                    getComponentName(atomInfo) /* component_name */,
                    getGridX(atomInfo, false) /* grid_x */,
                    getGridY(atomInfo, false) /* grid_y */,
                    getPageId(atomInfo) /* page_id */,
                    getGridX(atomInfo, true) /* grid_x_parent */,
                    getGridY(atomInfo, true) /* grid_y_parent */,
                    getParentPageId(atomInfo) /* page_id_parent */,
                    getHierarchy(atomInfo) /* hierarchy */,
                    atomInfo.getIsWork() /* is_work_profile */,
                    atomInfo.getRank() /* rank */,
                    atomInfo.getFolderIcon().getFromLabelState().getNumber() /* fromState */,
                    atomInfo.getFolderIcon().getToLabelState().getNumber() /* toState */,
                    atomInfo.getFolderIcon().getLabelInfo() /* edittext */,
                    getCardinality(atomInfo) /* cardinality */);
        }
    }

    private static int getCardinality(LauncherAtom.ItemInfo info) {
        switch (info.getContainerInfo().getContainerCase()) {
            case PREDICTED_HOTSEAT_CONTAINER:
                return info.getContainerInfo().getPredictedHotseatContainer().getCardinality();
            case SEARCH_RESULT_CONTAINER:
                return info.getContainerInfo().getSearchResultContainer().getQueryLength();
            default:
                return info.getFolderIcon().getCardinality();
        }
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

    private static int getPageId(LauncherAtom.ItemInfo info) {
        if (info.hasTask()) {
            return info.getTask().getIndex();
        }
        switch (info.getContainerInfo().getContainerCase()) {
            case FOLDER:
                return info.getContainerInfo().getFolder().getPageIndex();
            case HOTSEAT:
                return info.getContainerInfo().getHotseat().getIndex();
            case PREDICTED_HOTSEAT_CONTAINER:
                return info.getContainerInfo().getPredictedHotseatContainer().getIndex();
            default:
                return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getParentPageId(LauncherAtom.ItemInfo info) {
        switch (info.getContainerInfo().getContainerCase()) {
            case FOLDER:
                if (info.getContainerInfo().getFolder().getParentContainerCase()
                        == ParentContainerCase.HOTSEAT) {
                    return info.getContainerInfo().getFolder().getHotseat().getIndex();
                }
                return info.getContainerInfo().getFolder().getWorkspace().getPageIndex();
            case SEARCH_RESULT_CONTAINER:
                return info.getContainerInfo().getSearchResultContainer().getWorkspace()
                        .getPageIndex();
            default:
                return info.getContainerInfo().getWorkspace().getPageIndex();
        }
    }

    private static int getHierarchy(LauncherAtom.ItemInfo info) {
        if (info.getContainerInfo().getContainerCase() == FOLDER) {
            return info.getContainerInfo().getFolder().getParentContainerCase().getNumber()
                    + FOLDER_HIERARCHY_OFFSET;
        } else if (info.getContainerInfo().getContainerCase() == SEARCH_RESULT_CONTAINER) {
            return info.getContainerInfo().getSearchResultContainer().getParentContainerCase()
                    .getNumber() + SEARCH_RESULT_HIERARCHY_OFFSET;
        } else {
            return info.getContainerInfo().getContainerCase().getNumber();
        }
    }

    private static String getStateString(int state) {
        switch (state) {
            case LAUNCHER_UICHANGED__DST_STATE__BACKGROUND:
                return "BACKGROUND";
            case LAUNCHER_UICHANGED__DST_STATE__HOME:
                return "HOME";
            case LAUNCHER_UICHANGED__DST_STATE__OVERVIEW:
                return "OVERVIEW";
            case LAUNCHER_UICHANGED__DST_STATE__ALLAPPS:
                return "ALLAPPS";
            default:
                return "INVALID";

        }
    }
}
