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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_LEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_RIGHT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.annotation.TargetApi;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FolderContainer;
import com.android.launcher3.logger.LauncherAtom.HotseatContainer;
import com.android.launcher3.logger.LauncherAtom.WorkspaceContainer;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.pm.UserCache;
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogConsumer;

import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class to track stats log and emit corresponding app events
 */
@TargetApi(Build.VERSION_CODES.R)
public class AppEventProducer implements StatsLogConsumer {

    private static final int MSG_LAUNCH = 0;

    private final Context mContext;
    private final Handler mMessageHandler;
    private final Consumer<AppTargetEvent> mCallback;

    public AppEventProducer(Context context, Consumer<AppTargetEvent> callback) {
        mContext = context;
        mMessageHandler = new Handler(MODEL_EXECUTOR.getLooper(), this::handleMessage);
        mCallback = callback;
    }

    @WorkerThread
    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_LAUNCH: {
                mCallback.accept((AppTargetEvent) msg.obj);
                return true;
            }
        }
        return false;
    }

    @AnyThread
    private void sendEvent(LauncherAtom.ItemInfo atomInfo, int eventId) {
        AppTarget target = toAppTarget(atomInfo);
        if (target != null) {
            AppTargetEvent event = new AppTargetEvent.Builder(target, eventId)
                    .setLaunchLocation(getContainer(atomInfo))
                    .build();
            Message.obtain(mMessageHandler, MSG_LAUNCH, event).sendToTarget();
        }
    }

    @Override
    public void consume(EventEnum event, LauncherAtom.ItemInfo atomInfo) {
        if (event == LAUNCHER_APP_LAUNCH_TAP
                || event == LAUNCHER_TASK_LAUNCH_SWIPE_DOWN
                || event == LAUNCHER_TASK_LAUNCH_TAP
                || event == LAUNCHER_QUICKSWITCH_RIGHT
                || event == LAUNCHER_QUICKSWITCH_LEFT) {
            sendEvent(atomInfo, AppTargetEvent.ACTION_LAUNCH);
        } else if (event == LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST) {
            sendEvent(atomInfo, AppTargetEvent.ACTION_DISMISS);
        }
    }

    @Nullable
    private AppTarget toAppTarget(LauncherAtom.ItemInfo info) {
        UserHandle userHandle = Process.myUserHandle();
        if (info.getIsWork()) {
            userHandle = UserCache.INSTANCE.get(mContext).getUserProfiles().stream()
                    .filter(((Predicate<UserHandle>) userHandle::equals).negate())
                    .findAny()
                    .orElse(null);
        }
        if (userHandle == null) {
            return null;
        }
        ComponentName cn = null;
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
            case FOLDER_ICON: {
                id = "folder:" + SystemClock.uptimeMillis();
                cn = new ComponentName(mContext.getPackageName(), "#folder");
            }
        }
        if (id != null && cn != null) {
            return new AppTarget.Builder(new AppTargetId(id), cn.getPackageName(), userHandle)
                    .setClassName(cn.getClassName())
                    .build();
        }
        return null;
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
            case SEARCH_RESULT_CONTAINER: {
                return "search-results";
            }
            case PREDICTED_HOTSEAT_CONTAINER: {
                return "predictions/hotseat";
            }
            case PREDICTION_CONTAINER: {
                return "predictions";
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
        }
        return "";
    }

    private static String getWorkspaceContainerString(WorkspaceContainer wc, int spanX, int spanY) {
        return String.format(Locale.ENGLISH, "workspace/%d/[%d,%d]/[%d,%d]",
                wc.getPageIndex(), wc.getGridX(), wc.getGridY(), spanX, spanY);
    }

    private static String getHotseatContainerString(HotseatContainer hc) {
        return String.format(Locale.ENGLISH, "hotseat/%d", hc.getIndex());
    }

    private static ComponentName parseNullable(String componentNameString) {
        return TextUtils.isEmpty(componentNameString)
                ? null : ComponentName.unflattenFromString(componentNameString);
    }
}
