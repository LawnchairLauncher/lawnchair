/**
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

import static android.content.pm.PackageManager.MATCH_INSTANT;

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.InstantAppResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class which loads and caches predicted items like instant apps and shortcuts, before
 * they can be displayed on the UI
 */
public class DynamicItemCache {

    private static final String TAG = "DynamicItemCache";
    private static final boolean DEBUG = false;
    private static final String DEFAULT_URL = "default-url";

    private static final int BG_MSG_LOAD_SHORTCUTS = 1;
    private static final int BG_MSG_LOAD_INSTANT_APPS = 2;

    private static final int UI_MSG_UPDATE_SHORTCUTS = 1;
    private static final int UI_MSG_UPDATE_INSTANT_APPS = 2;

    private final Context mContext;
    private final Handler mWorker;
    private final Handler mUiHandler;
    private final InstantAppResolver mInstantAppResolver;
    private final Runnable mOnUpdateCallback;

    private final Map<ShortcutKey, WorkspaceItemInfo> mShortcuts;
    private final Map<String, InstantAppItemInfo> mInstantApps;

    public DynamicItemCache(Context context, Runnable onUpdateCallback) {
        mContext = context;
        mWorker = new Handler(MODEL_EXECUTOR.getLooper(), this::handleWorkerMessage);
        mUiHandler = new Handler(Looper.getMainLooper(), this::handleUiMessage);
        mInstantAppResolver = InstantAppResolver.newInstance(context);
        mOnUpdateCallback = onUpdateCallback;

        mShortcuts = new HashMap<>();
        mInstantApps = new HashMap<>();
    }

    public void cacheItems(List<ShortcutKey> shortcutKeys, List<String> pkgNames) {
        if (!shortcutKeys.isEmpty()) {
            mWorker.removeMessages(BG_MSG_LOAD_SHORTCUTS);
            Message.obtain(mWorker, BG_MSG_LOAD_SHORTCUTS, shortcutKeys).sendToTarget();
        }
        if (!pkgNames.isEmpty()) {
            mWorker.removeMessages(BG_MSG_LOAD_INSTANT_APPS);
            Message.obtain(mWorker, BG_MSG_LOAD_INSTANT_APPS, pkgNames).sendToTarget();
        }
    }

    private boolean handleWorkerMessage(Message msg) {
        switch (msg.what) {
            case BG_MSG_LOAD_SHORTCUTS: {
                List<ShortcutKey> shortcutKeys = msg.obj != null ?
                        (List<ShortcutKey>) msg.obj : Collections.EMPTY_LIST;
                Map<ShortcutKey, WorkspaceItemInfo> shortcutKeyAndInfos = new ArrayMap<>();
                for (ShortcutKey shortcutKey : shortcutKeys) {
                    WorkspaceItemInfo workspaceItemInfo = loadShortcutWorker(shortcutKey);
                    if (workspaceItemInfo != null) {
                        shortcutKeyAndInfos.put(shortcutKey, workspaceItemInfo);
                    }
                }
                Message.obtain(mUiHandler, UI_MSG_UPDATE_SHORTCUTS, shortcutKeyAndInfos)
                        .sendToTarget();
                return true;
            }
            case BG_MSG_LOAD_INSTANT_APPS: {
                List<String> pkgNames = msg.obj != null ?
                        (List<String>) msg.obj : Collections.EMPTY_LIST;
                List<InstantAppItemInfo> instantAppItemInfos = new ArrayList<>();
                for (String pkgName : pkgNames) {
                    InstantAppItemInfo instantAppItemInfo = loadInstantApp(pkgName);
                    if (instantAppItemInfo != null) {
                        instantAppItemInfos.add(instantAppItemInfo);
                    }
                }
                Message.obtain(mUiHandler, UI_MSG_UPDATE_INSTANT_APPS, instantAppItemInfos)
                        .sendToTarget();
                return true;
            }
        }

        return false;
    }

    private boolean handleUiMessage(Message msg) {
        switch (msg.what) {
            case UI_MSG_UPDATE_SHORTCUTS: {
                mShortcuts.clear();
                mShortcuts.putAll((Map<ShortcutKey, WorkspaceItemInfo>) msg.obj);
                mOnUpdateCallback.run();
                return true;
            }
            case UI_MSG_UPDATE_INSTANT_APPS: {
                List<InstantAppItemInfo> instantAppItemInfos = (List<InstantAppItemInfo>) msg.obj;
                mInstantApps.clear();
                for (InstantAppItemInfo instantAppItemInfo : instantAppItemInfos) {
                    mInstantApps.put(instantAppItemInfo.getTargetComponent().getPackageName(),
                            instantAppItemInfo);
                }
                mOnUpdateCallback.run();
                if (DEBUG) {
                    Log.d(TAG, String.format("Cache size: %d, Cache: %s",
                            mInstantApps.size(), mInstantApps.toString()));
                }
                return true;
            }
        }

        return false;
    }

    @WorkerThread
    private WorkspaceItemInfo loadShortcutWorker(ShortcutKey shortcutKey) {
        DeepShortcutManager mgr = DeepShortcutManager.getInstance(mContext);
        List<ShortcutInfo> details = mgr.queryForFullDetails(
                shortcutKey.componentName.getPackageName(),
                Collections.<String>singletonList(shortcutKey.getId()),
                shortcutKey.user);
        if (!details.isEmpty()) {
            WorkspaceItemInfo si = new WorkspaceItemInfo(details.get(0), mContext);
            try (LauncherIcons li = LauncherIcons.obtain(mContext)) {
                si.applyFrom(li.createShortcutIcon(details.get(0), true /* badged */, null));
            } catch (Exception e) {
                if (DEBUG) {
                    Log.e(TAG, "Error loading shortcut icon for " + shortcutKey.toString());
                }
                return null;
            }
            return si;
        }
        if (DEBUG) {
            Log.d(TAG, "No shortcut found: " + shortcutKey.toString());
        }
        return null;
    }

    private InstantAppItemInfo loadInstantApp(String pkgName) {
        PackageManager pm = mContext.getPackageManager();

        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
            if (!mInstantAppResolver.isInstantApp(ai)) {
                return null;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }

        String url = retrieveDefaultUrl(pkgName, pm);
        if (url == null) {
            Log.w(TAG, "no default-url available for pkg " + pkgName);
            return null;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(url));
        InstantAppItemInfo info = new InstantAppItemInfo(intent, pkgName);
        IconCache iconCache = LauncherAppState.getInstance(mContext).getIconCache();
        iconCache.getTitleAndIcon(info, false);
        if (info.iconBitmap == null || iconCache.isDefaultIcon(info.iconBitmap, info.user)) {
            return null;
        }
        return info;
    }

    @Nullable
    public static String retrieveDefaultUrl(String pkgName, PackageManager pm) {
        Intent mainIntent = new Intent().setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkgName);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(
                mainIntent, MATCH_INSTANT | PackageManager.GET_META_DATA);
        String url = null;
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo.metaData != null
                    && resolveInfo.activityInfo.metaData.containsKey(DEFAULT_URL)) {
                url = resolveInfo.activityInfo.metaData.getString(DEFAULT_URL);
            }
        }
        return url;
    }

    @UiThread
    public InstantAppItemInfo getInstantApp(String pkgName) {
        return mInstantApps.get(pkgName);
    }

    @MainThread
    public WorkspaceItemInfo getShortcutInfo(ShortcutKey key) {
        return mShortcuts.get(key);
    }
}
