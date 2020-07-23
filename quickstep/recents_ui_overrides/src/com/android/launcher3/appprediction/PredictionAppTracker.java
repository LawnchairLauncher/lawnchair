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
package com.android.launcher3.appprediction;

import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_GRID;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ITEM_DROPPED_ON_DONT_SUGGEST;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_LEFT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_QUICKSWITCH_RIGHT;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_SWIPE_DOWN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASK_LAUNCH_TAP;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.TargetApi;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.FolderContainer;
import com.android.launcher3.logger.LauncherAtom.HotseatContainer;
import com.android.launcher3.logger.LauncherAtom.WorkspaceContainer;
import com.android.launcher3.logging.StatsLogManager.EventEnum;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.pm.UserCache;
import com.android.quickstep.logging.StatsLogCompatManager;
import com.android.quickstep.logging.StatsLogCompatManager.StatsLogConsumer;

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Subclass of app tracker which publishes the data to the prediction engine and gets back results.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class PredictionAppTracker extends AppLaunchTracker implements StatsLogConsumer {

    private static final String TAG = "PredictionAppTracker";
    private static final boolean DBG = false;

    private static final int MSG_INIT = 0;
    private static final int MSG_DESTROY = 1;
    private static final int MSG_LAUNCH = 2;
    private static final int MSG_PREDICT = 3;

    protected final Context mContext;
    private final Handler mMessageHandler;

    // Accessed only on worker thread
    private AppPredictor mHomeAppPredictor;

    public PredictionAppTracker(Context context) {
        mContext = context;
        mMessageHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::handleMessage);
        InvariantDeviceProfile.INSTANCE.get(mContext).addOnChangeListener(this::onIdpChanged);

        mMessageHandler.sendEmptyMessage(MSG_INIT);
    }

    @UiThread
    private void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        if ((changeFlags & CHANGE_FLAG_GRID) != 0) {
            // Reinitialize everything
            mMessageHandler.sendEmptyMessage(MSG_INIT);
        }
    }

    @WorkerThread
    private void destroy() {
        if (mHomeAppPredictor != null) {
            mHomeAppPredictor.destroy();
            mHomeAppPredictor = null;
        }
        StatsLogCompatManager.LOGS_CONSUMER.remove(this);
    }

    @WorkerThread
    private AppPredictor createPredictor(Client client, int count) {
        AppPredictionManager apm = mContext.getSystemService(AppPredictionManager.class);

        if (apm == null) {
            return null;
        }

        AppPredictor predictor = apm.createAppPredictionSession(
                new AppPredictionContext.Builder(mContext)
                        .setUiSurface(client.id)
                        .setPredictedTargetCount(count)
                        .setExtras(getAppPredictionContextExtras(client))
                        .build());
        predictor.registerPredictionUpdates(mContext.getMainExecutor(),
                PredictionUiStateManager.INSTANCE.get(mContext).appPredictorCallback(client));
        predictor.requestPredictionUpdate();
        return predictor;
    }

    /**
     * Override to add custom extras.
     */
    @WorkerThread
    @Nullable
    public Bundle getAppPredictionContextExtras(Client client) {
        return null;
    }

    @WorkerThread
    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_INIT: {
                // Destroy any existing clients
                destroy();

                // Initialize the clients
                int count = InvariantDeviceProfile.INSTANCE.get(mContext).numAllAppsColumns;
                mHomeAppPredictor = createPredictor(Client.HOME, count);
                StatsLogCompatManager.LOGS_CONSUMER.add(this);
                return true;
            }
            case MSG_DESTROY: {
                destroy();
                return true;
            }
            case MSG_LAUNCH: {
                if (mHomeAppPredictor != null) {
                    mHomeAppPredictor.notifyAppTargetEvent((AppTargetEvent) msg.obj);
                }
                return true;
            }
            case MSG_PREDICT: {
                if (mHomeAppPredictor != null) {
                    mHomeAppPredictor.requestPredictionUpdate();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    @UiThread
    public void onReturnedToHome() {
        String client = Client.HOME.id;
        mMessageHandler.removeMessages(MSG_PREDICT, client);
        Message.obtain(mMessageHandler, MSG_PREDICT, client).sendToTarget();
        if (DBG) {
            Log.d(TAG, String.format("Sent immediate message to update %s", client));
        }
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
