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

package com.android.launcher3.model;

import static android.content.ContentResolver.SCHEME_CONTENT;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.createAndStartNewLooper;

import android.annotation.TargetApi;
import android.app.RemoteAction;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherProvider;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.RemoteActionShortcut;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SimpleBroadcastReceiver;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Data model for digital wellbeing status of apps.
 */
@TargetApi(Build.VERSION_CODES.Q)
public final class WellbeingModel {
    private static final String TAG = "WellbeingModel";
    private static final int[] RETRY_TIMES_MS = {5000, 15000, 30000};
    private static final boolean DEBUG = false;

    private static final int MSG_PACKAGE_ADDED = 1;
    private static final int MSG_PACKAGE_REMOVED = 2;
    private static final int MSG_FULL_REFRESH = 3;

    private static final int UNKNOWN_MINIMAL_DEVICE_STATE = 0;
    private static final int IN_MINIMAL_DEVICE = 2;

    // Welbeing contract
    private static final String PATH_ACTIONS = "actions";
    private static final String PATH_MINIMAL_DEVICE = "minimal_device";
    private static final String METHOD_GET_MINIMAL_DEVICE_CONFIG = "get_minimal_device_config";
    private static final String METHOD_GET_ACTIONS = "get_actions";
    private static final String EXTRA_ACTIONS = "actions";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MAX_NUM_ACTIONS_SHOWN = "max_num_actions_shown";
    private static final String EXTRA_PACKAGES = "packages";
    private static final String EXTRA_SUCCESS = "success";
    private static final String EXTRA_MINIMAL_DEVICE_STATE = "minimal_device_state";
    private static final String DB_NAME_MINIMAL_DEVICE = "minimal.db";

    public static final MainThreadInitializedObject<WellbeingModel> INSTANCE =
            new MainThreadInitializedObject<>(WellbeingModel::new);

    private final Context mContext;
    private final String mWellbeingProviderPkg;
    private final Handler mWorkerHandler;

    private final ContentObserver mContentObserver;

    private final Object mModelLock = new Object();
    // Maps the action Id to the corresponding RemoteAction
    private final Map<String, RemoteAction> mActionIdMap = new ArrayMap<>();
    private final Map<String, String> mPackageToActionId = new HashMap<>();

    private boolean mIsInTest;

    private WellbeingModel(final Context context) {
        mContext = context;
        mWorkerHandler =
                new Handler(createAndStartNewLooper("WellbeingHandler"), this::handleMessage);

        mWellbeingProviderPkg = mContext.getString(R.string.wellbeing_provider_pkg);
        mContentObserver = new ContentObserver(MAIN_EXECUTOR.getHandler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (DEBUG || mIsInTest) {
                    Log.d(TAG, "ContentObserver.onChange() called with: selfChange = ["
                            + selfChange + "], uri = [" + uri + "]");
                }
                Preconditions.assertUIThread();

                if (uri.getPath().contains(PATH_ACTIONS)) {
                    // Wellbeing reports that app actions have changed.
                    updateWellbeingData();
                } else if (uri.getPath().contains(PATH_MINIMAL_DEVICE)) {
                    // Wellbeing reports that minimal device state or config is changed.
                    updateLauncherModel(context);
                }
            }
        };
        FeatureFlags.ENABLE_MINIMAL_DEVICE.addChangeListener(mContext, () ->
                updateLauncherModel(context));

        if (!TextUtils.isEmpty(mWellbeingProviderPkg)) {
            context.registerReceiver(
                    new SimpleBroadcastReceiver(this::onWellbeingProviderChanged),
                    PackageManagerHelper.getPackageFilter(mWellbeingProviderPkg,
                            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_CHANGED,
                            Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_DATA_CLEARED,
                            Intent.ACTION_PACKAGE_RESTARTED));

            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            context.registerReceiver(new SimpleBroadcastReceiver(this::onAppPackageChanged),
                    filter);

            restartObserver();
        }
    }

    public void setInTest(boolean inTest) {
        mIsInTest = inTest;
    }

    protected void onWellbeingProviderChanged(Intent intent) {
        if (DEBUG || mIsInTest) {
            Log.d(TAG, "Changes to Wellbeing package: intent = [" + intent + "]");
        }
        restartObserver();
    }

    private void restartObserver() {
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mContentObserver);
        Uri actionsUri = apiBuilder().path(PATH_ACTIONS).build();
        Uri minimalDeviceUri = apiBuilder().path(PATH_MINIMAL_DEVICE).build();
        try {
            resolver.registerContentObserver(
                    actionsUri, true /* notifyForDescendants */, mContentObserver);
            resolver.registerContentObserver(
                    minimalDeviceUri, true /* notifyForDescendants */, mContentObserver);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register content observer for " + actionsUri + ": " + e);
            if (mIsInTest) throw new RuntimeException(e);
        }
        updateWellbeingData();
    }

    @MainThread
    private SystemShortcut getShortcutForApp(String packageName, int userId,
            BaseDraggingActivity activity, ItemInfo info) {
        Preconditions.assertUIThread();
        // Work profile apps are not recognized by digital wellbeing.
        if (userId != UserHandle.myUserId()) {
            if (DEBUG || mIsInTest) {
                Log.d(TAG, "getShortcutForApp [" + packageName + "]: not current user");
            }
            return null;
        }

        synchronized (mModelLock) {
            String actionId = mPackageToActionId.get(packageName);
            final RemoteAction action = actionId != null ? mActionIdMap.get(actionId) : null;
            if (action == null) {
                if (DEBUG || mIsInTest) {
                    Log.d(TAG, "getShortcutForApp [" + packageName + "]: no action");
                }
                return null;
            }
            if (DEBUG || mIsInTest) {
                Log.d(TAG,
                        "getShortcutForApp [" + packageName + "]: action: '" + action.getTitle()
                                + "'");
            }
            return new RemoteActionShortcut(action, activity, info);
        }
    }

    private void updateWellbeingData() {
        mWorkerHandler.sendEmptyMessage(MSG_FULL_REFRESH);
    }

    private void updateLauncherModel(@NonNull final Context context) {
        if (!FeatureFlags.ENABLE_MINIMAL_DEVICE.get()) {
            reloadLauncherInNormalMode(context);
            return;
        }
        runWithMinimalDeviceConfigs((bundle) -> {
            if (bundle.getInt(EXTRA_MINIMAL_DEVICE_STATE, UNKNOWN_MINIMAL_DEVICE_STATE)
                    == IN_MINIMAL_DEVICE) {
                reloadLauncherInMinimalMode(context);
            } else {
                reloadLauncherInNormalMode(context);
            }
        });
    }

    private void reloadLauncherInNormalMode(@NonNull final Context context) {
        LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_SWITCH_DATABASE,
                InvariantDeviceProfile.INSTANCE.get(context).dbFile);
    }

    private void reloadLauncherInMinimalMode(@NonNull final Context context) {
        final Bundle extras = new Bundle();
        extras.putString(LauncherProvider.KEY_LAYOUT_PROVIDER_AUTHORITY,
                mWellbeingProviderPkg + ".api");
        LauncherSettings.Settings.call(context.getContentResolver(),
                LauncherSettings.Settings.METHOD_SWITCH_DATABASE,
                DB_NAME_MINIMAL_DEVICE, extras);
    }

    private Uri.Builder apiBuilder() {
        return new Uri.Builder()
                .scheme(SCHEME_CONTENT)
                .authority(mWellbeingProviderPkg + ".api");
    }

    /**
     * Fetch most up-to-date minimal device config.
     */
    @WorkerThread
    private void runWithMinimalDeviceConfigs(Consumer<Bundle> consumer) {
        if (!FeatureFlags.ENABLE_MINIMAL_DEVICE.get()) {
            return;
        }
        if (DEBUG || mIsInTest) {
            Log.d(TAG, "runWithMinimalDeviceConfigs() called");
        }
        Preconditions.assertNonUiThread();

        final Uri contentUri = apiBuilder().build();
        final Bundle remoteBundle;
        try (ContentProviderClient client = mContext.getContentResolver()
                .acquireUnstableContentProviderClient(contentUri)) {
            remoteBundle = client.call(
                    METHOD_GET_MINIMAL_DEVICE_CONFIG, null /* args */, null /* extras */);
            consumer.accept(remoteBundle);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve data from " + contentUri + ": " + e);
            if (mIsInTest) throw new RuntimeException(e);
        }
        if (DEBUG || mIsInTest) Log.i(TAG, "runWithMinimalDeviceConfigs(): finished");
    }

    private boolean updateActions(String... packageNames) {
        if (packageNames.length == 0) {
            return true;
        }
        if (DEBUG || mIsInTest) {
            Log.d(TAG, "retrieveActions() called with: packageNames = [" + String.join(", ",
                    packageNames) + "]");
        }
        Preconditions.assertNonUiThread();

        Uri contentUri = apiBuilder().build();
        final Bundle remoteActionBundle;
        try (ContentProviderClient client = mContext.getContentResolver()
                .acquireUnstableContentProviderClient(contentUri)) {
            if (client == null) {
                if (DEBUG || mIsInTest) Log.i(TAG, "retrieveActions(): null provider");
                return false;
            }

            // Prepare wellbeing call parameters.
            final Bundle params = new Bundle();
            params.putStringArray(EXTRA_PACKAGES, packageNames);
            params.putInt(EXTRA_MAX_NUM_ACTIONS_SHOWN, 1);
            // Perform wellbeing call .
            remoteActionBundle = client.call(METHOD_GET_ACTIONS, null, params);
            if (!remoteActionBundle.getBoolean(EXTRA_SUCCESS, true)) return false;

            synchronized (mModelLock) {
                // Remove the entries for requested packages, and then update the fist with what we
                // got from service
                Arrays.stream(packageNames).forEach(mPackageToActionId::remove);

                // The result consists of sub-bundles, each one is per a remote action. Each
                // sub-bundle has a RemoteAction and a list of packages to which the action applies.
                for (String actionId :
                        remoteActionBundle.getStringArray(EXTRA_ACTIONS)) {
                    final Bundle actionBundle = remoteActionBundle.getBundle(actionId);
                    mActionIdMap.put(actionId,
                            actionBundle.getParcelable(EXTRA_ACTION));

                    final String[] packagesForAction =
                            actionBundle.getStringArray(EXTRA_PACKAGES);
                    if (DEBUG || mIsInTest) {
                        Log.d(TAG, "....actionId: " + actionId + ", packages: " + String.join(", ",
                                packagesForAction));
                    }
                    for (String packageName : packagesForAction) {
                        mPackageToActionId.put(packageName, actionId);
                    }
                }
            }
        } catch (DeadObjectException e) {
            Log.i(TAG, "retrieveActions(): DeadObjectException");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve data from " + contentUri + ": " + e);
            if (mIsInTest) throw new RuntimeException(e);
            return true;
        }
        if (DEBUG || mIsInTest) Log.i(TAG, "retrieveActions(): finished");
        return true;
    }

    private boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_PACKAGE_REMOVED: {
                String packageName = (String) msg.obj;
                mWorkerHandler.removeCallbacksAndMessages(packageName);
                synchronized (mModelLock) {
                    mPackageToActionId.remove(packageName);
                }
                return true;
            }
            case MSG_PACKAGE_ADDED: {
                String packageName = (String) msg.obj;
                mWorkerHandler.removeCallbacksAndMessages(packageName);
                if (!updateActions(packageName)) {
                    scheduleRefreshRetry(msg);
                }
                return true;
            }

            case MSG_FULL_REFRESH: {
                // Remove all existing messages
                mWorkerHandler.removeCallbacksAndMessages(null);
                final String[] packageNames = mContext.getSystemService(LauncherApps.class)
                        .getActivityList(null, Process.myUserHandle()).stream()
                        .map(li -> li.getApplicationInfo().packageName).distinct()
                        .toArray(String[]::new);
                if (!updateActions(packageNames)) {
                    scheduleRefreshRetry(msg);
                }
                return true;
            }
        }
        return false;
    }

    private void scheduleRefreshRetry(Message originalMsg) {
        int retryCount = originalMsg.arg1;
        if (retryCount >= RETRY_TIMES_MS.length) {
            // To many retries, skip
            return;
        }

        Message msg = Message.obtain(originalMsg);
        msg.arg1 = retryCount + 1;
        mWorkerHandler.sendMessageDelayed(msg, RETRY_TIMES_MS[retryCount]);
    }

    private void onAppPackageChanged(Intent intent) {
        if (DEBUG || mIsInTest) Log.d(TAG, "Changes in apps: intent = [" + intent + "]");
        Preconditions.assertUIThread();

        final String packageName = intent.getData().getSchemeSpecificPart();
        if (packageName == null || packageName.length() == 0) {
            // they sent us a bad intent
            return;
        }

        final String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            Message.obtain(mWorkerHandler, MSG_PACKAGE_REMOVED, packageName).sendToTarget();
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            Message.obtain(mWorkerHandler, MSG_PACKAGE_ADDED, packageName).sendToTarget();
        }
    }

    /**
     * Shortcut factory for generating wellbeing action
     */
    public static final SystemShortcut.Factory SHORTCUT_FACTORY =
            (activity, info) -> (info.getTargetComponent() == null) ? null : INSTANCE.get(activity)
                    .getShortcutForApp(
                            info.getTargetComponent().getPackageName(), info.user.getIdentifier(),
                            activity, info);
}
