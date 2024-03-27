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
package com.android.launcher3.model;

import static android.app.prediction.AppTargetEvent.ACTION_DISMISS;
import static android.app.prediction.AppTargetEvent.ACTION_LAUNCH;
import static android.app.prediction.AppTargetEvent.ACTION_PIN;
import static android.app.prediction.AppTargetEvent.ACTION_UNDISMISS;
import static android.app.prediction.AppTargetEvent.ACTION_UNPIN;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.logger.LauncherAtomExtensions.ExtendedContainers.ContainerCase.DEVICE_SEARCH_RESULT_CONTAINER;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_DRAGDROP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_DISMISS_PREDICTION_UNDO;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_FOLDER_CONVERTED_TO_ICON;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_PREDICTION_PINNED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DRAG_STARTED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_REMOVE;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_COMPLETED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROP_FOLDER_CREATED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ONRESUME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_LEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_RIGHT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_WIDGET_ADD_BUTTON_TAP;
import static com.android.launcher3.model.PredictionHelper.isTrackedForHotseatPrediction;
import static com.android.launcher3.model.PredictionHelper.isTrackedForWidgetPrediction;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FolderContainer;
import com.android.launcher3.logger.LauncherAtom.HotseatContainer;
import com.android.launcher3.logger.LauncherAtom.WorkspaceContainer;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.shortcuts.ShortcutRequest;
import com.android.launcher3.util.UserIconInfo;
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogConsumer;
import com.android.systemui.shared.system.SysUiStatsLog;

import java.util.Locale;
import java.util.Optional;
import java.util.function.ObjIntConsumer;

/**
 * Utility class to track stats log and emit corresponding app events
 */
public class AppEventProducer implements StatsLogConsumer {

    private static final int MSG_LAUNCH = 0;

    private final Context mContext;
    private final Handler mMessageHandler;
    private final ObjIntConsumer<AppTargetEvent> mCallback;

    private LauncherAtom.ItemInfo mLastDragItem;

    public AppEventProducer(Context context, ObjIntConsumer<AppTargetEvent> callback) {
        mContext = context;
        mMessageHandler = new Handler(MODEL_EXECUTOR.getLooper(), this::handleMessage);
        mCallback = callback;
    }

    @WorkerThread
    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LAUNCH: {
                mCallback.accept((AppTargetEvent) msg.obj, msg.arg1);
                return true;
            }
        }
        return false;
    }

    @AnyThread
    private void sendEvent(LauncherAtom.ItemInfo atomInfo, int eventId, int targetPredictor) {
        sendEvent(toAppTarget(atomInfo), atomInfo, eventId, targetPredictor);
    }

    @AnyThread
    private void sendEvent(AppTarget target, LauncherAtom.ItemInfo locationInfo, int eventId,
            int targetPredictor) {
        // TODO: remove the running test check when b/231648228 is fixed.
        if (target != null && !Utilities.isRunningInTestHarness()) {
            AppTargetEvent event = new AppTargetEvent.Builder(target, eventId)
                    .setLaunchLocation(getContainer(locationInfo))
                    .build();
            mMessageHandler.obtainMessage(MSG_LAUNCH, targetPredictor, 0, event).sendToTarget();
        }
    }

    @Override
    public void consume(EventEnum event, LauncherAtom.ItemInfo atomInfo) {
        if (event == LAUNCHER_APP_LAUNCH_TAP
                || event == LAUNCHER_TASK_LAUNCH_SWIPE_DOWN
                || event == LAUNCHER_TASK_LAUNCH_TAP
                || event == LAUNCHER_QUICKSWITCH_RIGHT
                || event == LAUNCHER_QUICKSWITCH_LEFT
                || event == LAUNCHER_APP_LAUNCH_DRAGDROP) {
            sendEvent(atomInfo, ACTION_LAUNCH, CONTAINER_PREDICTION);
        } else if (event == LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST
                || event == LAUNCHER_SYSTEM_SHORTCUT_DONT_SUGGEST_APP_TAP) {
            sendEvent(atomInfo, ACTION_DISMISS, CONTAINER_PREDICTION);
        } else if (event == LAUNCHER_ITEM_DRAG_STARTED) {
            mLastDragItem = atomInfo;
        } else if (event == LAUNCHER_ITEM_DROP_COMPLETED) {
            if (mLastDragItem == null) {
                return;
            }
            if (isTrackedForHotseatPrediction(mLastDragItem)) {
                sendEvent(mLastDragItem, ACTION_UNPIN, CONTAINER_HOTSEAT_PREDICTION);
            }
            if (isTrackedForHotseatPrediction(atomInfo)) {
                sendEvent(atomInfo, ACTION_PIN, CONTAINER_HOTSEAT_PREDICTION);
            }
            if (isTrackedForWidgetPrediction(atomInfo)) {
                sendEvent(atomInfo, ACTION_PIN, CONTAINER_WIDGETS_PREDICTION);
            }
            mLastDragItem = null;
        } else if (event == LAUNCHER_ITEM_DROP_FOLDER_CREATED) {
            if (isTrackedForHotseatPrediction(atomInfo)) {
                sendEvent(createTempFolderTarget(), atomInfo, ACTION_PIN,
                        CONTAINER_HOTSEAT_PREDICTION);
                sendEvent(atomInfo, ACTION_UNPIN, CONTAINER_HOTSEAT_PREDICTION);
            }
        } else if (event == LAUNCHER_FOLDER_CONVERTED_TO_ICON) {
            if (isTrackedForHotseatPrediction(atomInfo)) {
                sendEvent(createTempFolderTarget(), atomInfo, ACTION_UNPIN,
                        CONTAINER_HOTSEAT_PREDICTION);
                sendEvent(atomInfo, ACTION_PIN, CONTAINER_HOTSEAT_PREDICTION);
            }
        } else if (event == LAUNCHER_ITEM_DROPPED_ON_REMOVE) {
            if (mLastDragItem != null && isTrackedForHotseatPrediction(mLastDragItem)) {
                sendEvent(mLastDragItem, ACTION_UNPIN, CONTAINER_HOTSEAT_PREDICTION);
            }
            if (mLastDragItem != null && isTrackedForWidgetPrediction(mLastDragItem)) {
                sendEvent(mLastDragItem, ACTION_UNPIN, CONTAINER_WIDGETS_PREDICTION);
            }
        } else if (event == LAUNCHER_HOTSEAT_PREDICTION_PINNED) {
            if (isTrackedForHotseatPrediction(atomInfo)) {
                sendEvent(atomInfo, ACTION_PIN, CONTAINER_HOTSEAT_PREDICTION);
            }
        } else if (event == LAUNCHER_ONRESUME) {
            AppTarget target = new AppTarget.Builder(new AppTargetId("launcher:launcher"),
                    mContext.getPackageName(), Process.myUserHandle())
                    .build();
            sendEvent(target, atomInfo, ACTION_LAUNCH, CONTAINER_PREDICTION);
        } else if (event == LAUNCHER_DISMISS_PREDICTION_UNDO) {
            sendEvent(atomInfo, ACTION_UNDISMISS, CONTAINER_HOTSEAT_PREDICTION);
        } else if (event == LAUNCHER_WIDGET_ADD_BUTTON_TAP) {
            if (isTrackedForWidgetPrediction(atomInfo)) {
                sendEvent(atomInfo, ACTION_PIN, CONTAINER_WIDGETS_PREDICTION);
            }
        }
    }

    @Nullable
    AppTarget toAppTarget(LauncherAtom.ItemInfo info) {
        int iconInfoType = getIconInfoTypeFromItemInfo(info);
        UserCache userCache = UserCache.INSTANCE.get(mContext);
        UserHandle userHandle = userCache.getUserProfiles().stream()
                .filter(user -> userCache.getUserInfo(user).type == iconInfoType)
                .findFirst()
                .orElse(null);
        if (userHandle == null) {
            return null;
        }
        ComponentName cn = null;
        ShortcutInfo shortcutInfo = null;
        String id = null;

        switch (info.getItemCase()) {
            case APPLICATION: {
                LauncherAtom.Application app = info.getApplication();
                if ((cn = parseNullable(app.getComponentName())) != null) {
                    id = "app:" + cn.getPackageName();
                }
                break;
            }
            case SHORTCUT: {
                LauncherAtom.Shortcut si = info.getShortcut();
                if (!TextUtils.isEmpty(si.getShortcutId())
                        && (cn = parseNullable(si.getShortcutName())) != null) {
                    Optional<ShortcutInfo> opt = new ShortcutRequest(mContext,
                            userHandle).forPackage(cn.getPackageName(), si.getShortcutId()).query(
                            ShortcutRequest.ALL).stream().findFirst();
                    if (opt.isPresent()) {
                        shortcutInfo = opt.get();
                    } else {
                        return null;
                    }
                    id = "shortcut:" + si.getShortcutId();
                }
                break;
            }
            case WIDGET: {
                LauncherAtom.Widget widget = info.getWidget();
                if ((cn = parseNullable(widget.getComponentName())) != null) {
                    id = "widget:" + cn.getPackageName();
                }
                break;
            }
            case TASK: {
                LauncherAtom.Task task = info.getTask();
                if ((cn = parseNullable(task.getComponentName())) != null) {
                    id = "app:" + cn.getPackageName();
                }
                break;
            }
            case FOLDER_ICON:
                return createTempFolderTarget();
        }
        if (id != null && cn != null) {
            if (shortcutInfo != null) {
                return new AppTarget.Builder(new AppTargetId(id), shortcutInfo).build();
            }
            return new AppTarget.Builder(new AppTargetId(id), cn.getPackageName(), userHandle)
                    .setClassName(cn.getClassName())
                    .build();
        }
        return null;
    }


    private AppTarget createTempFolderTarget() {
        return new AppTarget.Builder(new AppTargetId("folder:" + SystemClock.uptimeMillis()),
                mContext.getPackageName(), Process.myUserHandle())
                .build();
    }

    private String getContainer(LauncherAtom.ItemInfo info) {
        ContainerInfo ci = info.getContainerInfo();
        switch (ci.getContainerCase()) {
            case WORKSPACE: {
                // In case the item type is not widgets, the spaceX and spanY default to 1.
                int spanX = info.getWidget().getSpanX();
                int spanY = info.getWidget().getSpanY();
                return getWorkspaceContainerString(ci.getWorkspace(), spanX, spanY);
            }
            case HOTSEAT: {
                return getHotseatContainerString(ci.getHotseat());
            }
            case TASK_SWITCHER_CONTAINER: {
                return "task-switcher";
            }
            case ALL_APPS_CONTAINER: {
                return "all-apps";
            }
            case PREDICTED_HOTSEAT_CONTAINER: {
                return "predictions/hotseat";
            }
            case PREDICTION_CONTAINER: {
                return "predictions";
            }
            case SHORTCUTS_CONTAINER: {
                return "deep-shortcuts";
            }
            case TASK_BAR_CONTAINER: {
                return "taskbar";
            }
            case FOLDER: {
                FolderContainer fc = ci.getFolder();
                switch (fc.getParentContainerCase()) {
                    case WORKSPACE:
                        return "folder/" + getWorkspaceContainerString(fc.getWorkspace(), 1, 1);
                    case HOTSEAT:
                        return "folder/" + getHotseatContainerString(fc.getHotseat());
                }
                return "folder";
            }
            case SEARCH_RESULT_CONTAINER:
                return "search-results";
            case EXTENDED_CONTAINERS: {
                if (ci.getExtendedContainers().getContainerCase()
                        == DEVICE_SEARCH_RESULT_CONTAINER) {
                    return "search-results";
                }
            }
            default: // fall out
        }
        return "";
    }

    private static String getWorkspaceContainerString(WorkspaceContainer wc, int spanX, int spanY) {
        return String.format(Locale.ENGLISH, "workspace/%d/[%d,%d]/[%d,%d]",
                wc.getPageIndex(), wc.getGridX(), wc.getGridY(), spanX, spanY);
    }

    private static String getHotseatContainerString(HotseatContainer hc) {
        return String.format(Locale.ENGLISH, "hotseat/%1$d/[%1$d,0]/[1,1]", hc.getIndex());
    }

    private static ComponentName parseNullable(String componentNameString) {
        return TextUtils.isEmpty(componentNameString)
                ? null : ComponentName.unflattenFromString(componentNameString);
    }

    private int getIconInfoTypeFromItemInfo(LauncherAtom.ItemInfo info) {
        int userType = info.getUserType();
        return switch (userType) {
            case SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK -> UserIconInfo.TYPE_WORK;
            case SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED ->
                    UserIconInfo.TYPE_CLONED;
            case SysUiStatsLog.LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE ->
                    UserIconInfo.TYPE_PRIVATE;
            default -> UserIconInfo.TYPE_MAIN;
        };
    }
}
