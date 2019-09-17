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
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.appprediction.PredictionUiStateManager.Client;
import com.android.launcher3.model.AppLaunchTracker;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.systemui.plugins.AppLaunchEventsPlugin;
import com.android.systemui.plugins.PluginListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Subclass of app tracker which publishes the data to the prediction engine and gets back results.
 */
@TargetApi(Build.VERSION_CODES.Q)
public class PredictionAppTracker extends AppLaunchTracker
        implements PluginListener<AppLaunchEventsPlugin> {

    private static final String TAG = "PredictionAppTracker";
    private static final boolean DBG = false;

    private static final int MSG_INIT = 0;
    private static final int MSG_DESTROY = 1;
    private static final int MSG_LAUNCH = 2;
    private static final int MSG_PREDICT = 3;

    protected final Context mContext;
    private final Handler mMessageHandler;
    private final List<AppLaunchEventsPlugin> mAppLaunchEventsPluginsList;

    // Accessed only on worker thread
    private AppPredictor mHomeAppPredictor;
    private AppPredictor mRecentsOverviewPredictor;

    public PredictionAppTracker(Context context) {
        mContext = context;
        mMessageHandler = new Handler(UI_HELPER_EXECUTOR.getLooper(), this::handleMessage);
        InvariantDeviceProfile.INSTANCE.get(mContext).addOnChangeListener(this::onIdpChanged);

        mMessageHandler.sendEmptyMessage(MSG_INIT);

        mAppLaunchEventsPluginsList = new ArrayList<>();
        PluginManagerWrapper.INSTANCE.get(context)
                .addPluginListener(this, AppLaunchEventsPlugin.class, true);
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
        if (mRecentsOverviewPredictor != null) {
            mRecentsOverviewPredictor.destroy();
            mRecentsOverviewPredictor = null;
        }
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
                mRecentsOverviewPredictor = createPredictor(Client.OVERVIEW, count);
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
                    String client = (String) msg.obj;
                    if (Client.HOME.id.equals(client)) {
                        mHomeAppPredictor.requestPredictionUpdate();
                    } else {
                        mRecentsOverviewPredictor.requestPredictionUpdate();
                    }
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

        // Relay onReturnedToHome to every plugin.
        mAppLaunchEventsPluginsList.forEach(AppLaunchEventsPlugin::onReturnedToHome);
    }

    @Override
    @UiThread
    public void onStartShortcut(String packageName, String shortcutId, UserHandle user,
                                String container) {
        // TODO: Use the full shortcut info
        AppTarget target = new AppTarget.Builder(
                new AppTargetId("shortcut:" + shortcutId), packageName, user)
                .setClassName(shortcutId)
                .build();

        sendLaunch(target, container);

        // Relay onStartShortcut info to every connected plugin.
        mAppLaunchEventsPluginsList
                .forEach(plugin -> plugin.onStartShortcut(
                        packageName,
                        shortcutId,
                        user,
                        container != null ? container : CONTAINER_DEFAULT)
        );

    }

    @Override
    @UiThread
    public void onStartApp(ComponentName cn, UserHandle user, String container) {
        if (cn != null) {
            AppTarget target = new AppTarget.Builder(
                    new AppTargetId("app:" + cn), cn.getPackageName(), user)
                    .setClassName(cn.getClassName())
                    .build();
            sendLaunch(target, container);

            // Relay onStartApp to every connected plugin.
            mAppLaunchEventsPluginsList
                    .forEach(plugin -> plugin.onStartApp(
                            cn,
                            user,
                            container != null ? container : CONTAINER_DEFAULT)
            );
        }
    }

    @Override
    @UiThread
    public void onDismissApp(ComponentName cn, UserHandle user, String container) {
        if (cn == null) return;
        AppTarget target = new AppTarget.Builder(
                new AppTargetId("app: " + cn), cn.getPackageName(), user)
                .setClassName(cn.getClassName())
                .build();
        sendDismiss(target, container);

        // Relay onDismissApp to every connected plugin.
        mAppLaunchEventsPluginsList
                .forEach(plugin -> plugin.onDismissApp(
                        cn,
                        user,
                        container != null ? container : CONTAINER_DEFAULT)
        );
    }

    @UiThread
    private void sendEvent(AppTarget target, String container, int eventId) {
        AppTargetEvent event = new AppTargetEvent.Builder(target, eventId)
                .setLaunchLocation(container == null ? CONTAINER_DEFAULT : container)
                .build();
        Message.obtain(mMessageHandler, MSG_LAUNCH, event).sendToTarget();
    }

    @UiThread
    private void sendLaunch(AppTarget target, String container) {
        sendEvent(target, container, AppTargetEvent.ACTION_LAUNCH);
    }

    @UiThread
    private void sendDismiss(AppTarget target, String container) {
        sendEvent(target, container, AppTargetEvent.ACTION_DISMISS);
    }

    @Override
    public void onPluginConnected(AppLaunchEventsPlugin appLaunchEventsPlugin, Context context) {
        mAppLaunchEventsPluginsList.add(appLaunchEventsPlugin);
    }

    @Override
    public void onPluginDisconnected(AppLaunchEventsPlugin appLaunchEventsPlugin) {
        mAppLaunchEventsPluginsList.remove(appLaunchEventsPlugin);
    }
}
