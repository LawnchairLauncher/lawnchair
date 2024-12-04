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

import static com.android.launcher3.util.SimpleBroadcastReceiver.getPackageFilter;

import android.app.RemoteAction;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.RemoteActionShortcut;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.launcher3.views.ActivityContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Data model for digital wellbeing status of apps.
 */
public final class WellbeingModel implements SafeCloseable {
    private static final String TAG = "WellbeingModel";
    private static final int[] RETRY_TIMES_MS = {5000, 15000, 30000};
    private static final boolean DEBUG = false;

    // Welbeing contract
    private static final String PATH_ACTIONS = "actions";
    private static final String METHOD_GET_ACTIONS = "get_actions";
    private static final String EXTRA_ACTIONS = "actions";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MAX_NUM_ACTIONS_SHOWN = "max_num_actions_shown";
    private static final String EXTRA_PACKAGES = "packages";
    private static final String EXTRA_SUCCESS = "success";

    public static final MainThreadInitializedObject<WellbeingModel> INSTANCE =
            new MainThreadInitializedObject<>(WellbeingModel::new);

    private final Context mContext;
    private final String mWellbeingProviderPkg;

    private final Handler mWorkerHandler;
    private final ContentObserver mContentObserver;
    private final SimpleBroadcastReceiver mWellbeingAppChangeReceiver =
            new SimpleBroadcastReceiver(t -> restartObserver());
    private final SimpleBroadcastReceiver mAppAddRemoveReceiver =
            new SimpleBroadcastReceiver(this::onAppPackageChanged);

    private final Object mModelLock = new Object();
    // Maps the action Id to the corresponding RemoteAction
    private final Map<String, RemoteAction> mActionIdMap = new ArrayMap<>();
    private final Map<String, String> mPackageToActionId = new HashMap<>();

    private boolean mIsInTest;

    private WellbeingModel(final Context context) {
        mContext = context;
        mWellbeingProviderPkg = mContext.getString(R.string.wellbeing_provider_pkg);
        mWorkerHandler = new Handler(TextUtils.isEmpty(mWellbeingProviderPkg)
                ? Executors.UI_HELPER_EXECUTOR.getLooper()
                : Executors.getPackageExecutor(mWellbeingProviderPkg).getLooper());

        mContentObserver = new ContentObserver(mWorkerHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                updateAllPackages();
            }
        };
        mWorkerHandler.post(this::initializeInBackground);
    }

    private void initializeInBackground() {
        if (!TextUtils.isEmpty(mWellbeingProviderPkg)) {
            mContext.registerReceiver(
                    mWellbeingAppChangeReceiver,
                    getPackageFilter(mWellbeingProviderPkg,
                            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_CHANGED,
                            Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_DATA_CLEARED,
                            Intent.ACTION_PACKAGE_RESTARTED),
                    null, mWorkerHandler);

            IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addDataScheme("package");
            mContext.registerReceiver(mAppAddRemoveReceiver, filter, null, mWorkerHandler);

            restartObserver();
        }
    }

    @Override
    public void close() {
        if (!TextUtils.isEmpty(mWellbeingProviderPkg)) {
            mWorkerHandler.post(() -> {
                mWellbeingAppChangeReceiver.unregisterReceiverSafely(mContext);
                mAppAddRemoveReceiver.unregisterReceiverSafely(mContext);
                mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            });
        }
    }

    public void setInTest(boolean inTest) {
        mIsInTest = inTest;
    }

    @WorkerThread
    private void restartObserver() {
        final ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mContentObserver);
        Uri actionsUri = apiBuilder().path(PATH_ACTIONS).build();
        try {
            resolver.registerContentObserver(
                    actionsUri, true /* notifyForDescendants */, mContentObserver);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register content observer for " + actionsUri + ": " + e);
            if (mIsInTest) throw new RuntimeException(e);
        }
        updateAllPackages();
    }

    @MainThread
    private SystemShortcut getShortcutForApp(String packageName, int userId,
            Context context, ItemInfo info, View originalView) {
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
            return new RemoteActionShortcut(action, context, info, originalView);
        }
    }

    private Uri.Builder apiBuilder() {
        return new Uri.Builder()
                .scheme(SCHEME_CONTENT)
                .authority(mWellbeingProviderPkg + ".api");
    }

    @WorkerThread
    private boolean updateActions(String[] packageNames) {
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

    @WorkerThread
    private void updateActionsWithRetry(int retryCount, @Nullable String packageName) {
        if (DEBUG || mIsInTest) {
            Log.i(TAG,
                    "updateActionsWithRetry(); retryCount: " + retryCount + ", package: "
                            + packageName);
        }
        String[] packageNames = TextUtils.isEmpty(packageName)
                ? mContext.getSystemService(LauncherApps.class)
                .getActivityList(null, Process.myUserHandle()).stream()
                .map(li -> li.getApplicationInfo().packageName).distinct()
                .toArray(String[]::new)
                : new String[]{packageName};

        mWorkerHandler.removeCallbacksAndMessages(packageName);
        if (updateActions(packageNames)) {
            return;
        }
        if (retryCount >= RETRY_TIMES_MS.length) {
            // To many retries, skip
            return;
        }
        mWorkerHandler.postDelayed(
                () -> {
                    if (DEBUG || mIsInTest) Log.i(TAG, "Retrying; attempt " + (retryCount + 1));
                    updateActionsWithRetry(retryCount + 1, packageName);
                },
                packageName, RETRY_TIMES_MS[retryCount]);
    }

    @WorkerThread
    private void updateAllPackages() {
        if (DEBUG || mIsInTest) Log.i(TAG, "updateAllPackages");
        updateActionsWithRetry(0, null);
    }

    @WorkerThread
    private void onAppPackageChanged(Intent intent) {
        if (DEBUG || mIsInTest) Log.d(TAG, "Changes in apps: intent = [" + intent + "]");
        Preconditions.assertNonUiThread();

        final String packageName = intent.getData().getSchemeSpecificPart();
        if (packageName == null || packageName.length() == 0) {
            // they sent us a bad intent
            return;
        }
        final String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            mWorkerHandler.removeCallbacksAndMessages(packageName);
            synchronized (mModelLock) {
                mPackageToActionId.remove(packageName);
            }
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            updateActionsWithRetry(0, packageName);
        }
    }

    /**
     * Shortcut factory for generating wellbeing action
     */
    public static final SystemShortcut.Factory<ActivityContext> SHORTCUT_FACTORY =
            (context, info, originalView) ->
                    (info.getTargetComponent() == null) ? null
                            : INSTANCE.get(originalView.getContext()).getShortcutForApp(
                                    info.getTargetComponent().getPackageName(), info.user.getIdentifier(),
                                    ActivityContext.lookupContext(originalView.getContext()),
                                    info, originalView);
}
