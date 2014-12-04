/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.json.JSONTokener;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class InstallShortcutReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallShortcutReceiver";
    private static final boolean DBG = false;

    private static final String ACTION_INSTALL_SHORTCUT =
            "com.android.launcher.action.INSTALL_SHORTCUT";

    private static final String LAUNCH_INTENT_KEY = "intent.launch";
    private static final String NAME_KEY = "name";
    private static final String ICON_KEY = "icon";
    private static final String ICON_RESOURCE_NAME_KEY = "iconResource";
    private static final String ICON_RESOURCE_PACKAGE_NAME_KEY = "iconResourcePackage";

    private static final String APP_SHORTCUT_TYPE_KEY = "isAppShortcut";
    private static final String USER_HANDLE_KEY = "userHandle";

    // The set of shortcuts that are pending install
    private static final String APPS_PENDING_INSTALL = "apps_to_install";

    public static final int NEW_SHORTCUT_BOUNCE_DURATION = 450;
    public static final int NEW_SHORTCUT_STAGGER_DELAY = 85;

    private static final Object sLock = new Object();

    private static void addToInstallQueue(
            SharedPreferences sharedPrefs, PendingInstallShortcutInfo info) {
        synchronized(sLock) {
            String encoded = info.encodeToString();
            if (encoded != null) {
                Set<String> strings = sharedPrefs.getStringSet(APPS_PENDING_INSTALL, null);
                if (strings == null) {
                    strings = new HashSet<String>(1);
                } else {
                    strings = new HashSet<String>(strings);
                }
                strings.add(encoded);
                sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, strings).commit();
            }
        }
    }

    public static void removeFromInstallQueue(Context context, ArrayList<String> packageNames,
            UserHandleCompat user) {
        if (packageNames.isEmpty()) {
            return;
        }
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        synchronized(sLock) {
            Set<String> strings = sp.getStringSet(APPS_PENDING_INSTALL, null);
            if (DBG) {
                Log.d(TAG, "APPS_PENDING_INSTALL: " + strings
                        + ", removing packages: " + packageNames);
            }
            if (strings != null) {
                Set<String> newStrings = new HashSet<String>(strings);
                Iterator<String> newStringsIter = newStrings.iterator();
                while (newStringsIter.hasNext()) {
                    String encoded = newStringsIter.next();
                    PendingInstallShortcutInfo info = decode(encoded, context);
                    if (info == null || (packageNames.contains(info.getTargetPackage())
                            && user.equals(info.user))) {
                        newStringsIter.remove();
                    }
                }
                sp.edit().putStringSet(APPS_PENDING_INSTALL, newStrings).commit();
            }
        }
    }

    private static ArrayList<PendingInstallShortcutInfo> getAndClearInstallQueue(
            SharedPreferences sharedPrefs, Context context) {
        synchronized(sLock) {
            Set<String> strings = sharedPrefs.getStringSet(APPS_PENDING_INSTALL, null);
            if (DBG) Log.d(TAG, "Getting and clearing APPS_PENDING_INSTALL: " + strings);
            if (strings == null) {
                return new ArrayList<PendingInstallShortcutInfo>();
            }
            ArrayList<PendingInstallShortcutInfo> infos =
                new ArrayList<PendingInstallShortcutInfo>();
            for (String encoded : strings) {
                PendingInstallShortcutInfo info = decode(encoded, context);
                if (info != null) {
                    infos.add(info);
                }
            }
            sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, new HashSet<String>()).commit();
            return infos;
        }
    }

    // Determines whether to defer installing shortcuts immediately until
    // processAllPendingInstalls() is called.
    private static boolean mUseInstallQueue = false;

    public void onReceive(Context context, Intent data) {
        if (!ACTION_INSTALL_SHORTCUT.equals(data.getAction())) {
            return;
        }

        if (DBG) Log.d(TAG, "Got INSTALL_SHORTCUT: " + data.toUri(0));
        PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(data, context);

        queuePendingShortcutInfo(info, context);
    }

    static void queueInstallShortcut(LauncherActivityInfoCompat info, Context context) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(info, context), context);
    }

    private static void queuePendingShortcutInfo(PendingInstallShortcutInfo info, Context context) {
        // Queue the item up for adding if launcher has not loaded properly yet
        LauncherAppState.setApplicationContext(context.getApplicationContext());
        LauncherAppState app = LauncherAppState.getInstance();
        boolean launcherNotLoaded = app.getModel().getCallback() == null;

        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        addToInstallQueue(sp, info);
        if (!mUseInstallQueue && !launcherNotLoaded) {
            flushInstallQueue(context);
        }
    }

    static void enableInstallQueue() {
        mUseInstallQueue = true;
    }
    static void disableAndFlushInstallQueue(Context context) {
        mUseInstallQueue = false;
        flushInstallQueue(context);
    }
    static void flushInstallQueue(Context context) {
        String spKey = LauncherAppState.getSharedPreferencesKey();
        SharedPreferences sp = context.getSharedPreferences(spKey, Context.MODE_PRIVATE);
        ArrayList<PendingInstallShortcutInfo> installQueue = getAndClearInstallQueue(sp, context);
        if (!installQueue.isEmpty()) {
            Iterator<PendingInstallShortcutInfo> iter = installQueue.iterator();
            ArrayList<ItemInfo> addShortcuts = new ArrayList<ItemInfo>();
            while (iter.hasNext()) {
                final PendingInstallShortcutInfo pendingInfo = iter.next();
                final Intent intent = pendingInfo.launchIntent;

                if (LauncherAppState.isDisableAllApps() && !isValidShortcutLaunchIntent(intent)) {
                    if (DBG) Log.d(TAG, "Ignoring shortcut with launchIntent:" + intent);
                    continue;
                }

                // If the intent specifies a package, make sure the package exists
                String packageName = pendingInfo.getTargetPackage();
                if (!TextUtils.isEmpty(packageName)) {
                    UserHandleCompat myUserHandle = UserHandleCompat.myUserHandle();
                    if (!LauncherModel.isValidPackage(context, packageName, myUserHandle)) {
                        if (DBG) Log.d(TAG, "Ignoring shortcut for absent package:" + intent);
                        continue;
                    }
                }

                final boolean exists = LauncherModel.shortcutExists(context, pendingInfo.label,
                        intent, pendingInfo.user);
                if (!exists) {
                    // Generate a shortcut info to add into the model
                    addShortcuts.add(pendingInfo.getShortcutInfo());
                }
            }

            // Add the new apps to the model and bind them
            if (!addShortcuts.isEmpty()) {
                LauncherAppState app = LauncherAppState.getInstance();
                app.getModel().addAndBindAddedWorkspaceApps(context, addShortcuts);
            }
        }
    }

    /**
     * Returns true if the intent is a valid launch intent for a shortcut.
     * This is used to identify shortcuts which are different from the ones exposed by the
     * applications' manifest file.
     *
     * When DISABLE_ALL_APPS is true, shortcuts exposed via the app's manifest should never be
     * duplicated or removed(unless the app is un-installed).
     *
     * @param launchIntent The intent that will be launched when the shortcut is clicked.
     */
    static boolean isValidShortcutLaunchIntent(Intent launchIntent) {
        if (launchIntent != null
                && Intent.ACTION_MAIN.equals(launchIntent.getAction())
                && launchIntent.getComponent() != null
                && launchIntent.getCategories() != null
                && launchIntent.getCategories().size() == 1
                && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && launchIntent.getExtras() == null
                && TextUtils.isEmpty(launchIntent.getDataString())) {
            return false;
        }
        return true;
    }

    /**
     * Ensures that we have a valid, non-null name.  If the provided name is null, we will return
     * the application name instead.
     */
    private static CharSequence ensureValidName(Context context, Intent intent, CharSequence name) {
        if (name == null) {
            try {
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                name = info.loadLabel(pm).toString();
            } catch (PackageManager.NameNotFoundException nnfe) {
                return "";
            }
        }
        return name;
    }

    private static class PendingInstallShortcutInfo {

        final LauncherActivityInfoCompat activityInfo;

        final Intent data;
        final Context mContext;
        final Intent launchIntent;
        final String label;
        final UserHandleCompat user;

        /**
         * Initializes a PendingInstallShortcutInfo received from a different app.
         */
        public PendingInstallShortcutInfo(Intent data, Context context) {
            this.data = data;
            mContext = context;

            launchIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
            user = UserHandleCompat.myUserHandle();
            activityInfo = null;
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a launcher target.
         */
        public PendingInstallShortcutInfo(LauncherActivityInfoCompat info, Context context) {
            this.data = null;
            mContext = context;
            activityInfo = info;
            user = info.getUser();

            launchIntent = AppInfo.makeLaunchIntent(context, info, user);
            label = info.getLabel().toString();
        }

        public String encodeToString() {
            if (activityInfo != null) {
                try {
                    // If it a launcher target, we only need component name, and user to
                    // recreate this.
                    return new JSONStringer()
                        .object()
                        .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                        .key(APP_SHORTCUT_TYPE_KEY).value(true)
                        .key(USER_HANDLE_KEY).value(UserManagerCompat.getInstance(mContext)
                                .getSerialNumberForUser(user))
                        .endObject().toString();
                } catch (JSONException e) {
                    Log.d(TAG, "Exception when adding shortcut: " + e);
                    return null;
                }
            }

            if (launchIntent.getAction() == null) {
                launchIntent.setAction(Intent.ACTION_VIEW);
            } else if (launchIntent.getAction().equals(Intent.ACTION_MAIN) &&
                    launchIntent.getCategories() != null &&
                    launchIntent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }

            // This name is only used for comparisons and notifications, so fall back to activity
            // name if not supplied
            String name = ensureValidName(mContext, launchIntent, label).toString();
            Bitmap icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
            Intent.ShortcutIconResource iconResource =
                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);

            // Only encode the parameters which are supported by the API.
            try {
                JSONStringer json = new JSONStringer()
                    .object()
                    .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                    .key(NAME_KEY).value(name);
                if (icon != null) {
                    byte[] iconByteArray = ItemInfo.flattenBitmap(icon);
                    json = json.key(ICON_KEY).value(
                            Base64.encodeToString(
                                    iconByteArray, 0, iconByteArray.length, Base64.DEFAULT));
                }
                if (iconResource != null) {
                    json = json.key(ICON_RESOURCE_NAME_KEY).value(iconResource.resourceName);
                    json = json.key(ICON_RESOURCE_PACKAGE_NAME_KEY)
                            .value(iconResource.packageName);
                }
                return json.endObject().toString();
            } catch (JSONException e) {
                Log.d(TAG, "Exception when adding shortcut: " + e);
            }
            return null;
        }

        public ShortcutInfo getShortcutInfo() {
            if (activityInfo != null) {
                final ShortcutInfo info = new ShortcutInfo();
                info.user = user;
                info.title = label;
                info.contentDescription = label;
                info.customIcon = false;
                info.intent = launchIntent;
                info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
                info.flags = AppInfo.initFlags(activityInfo);
                info.firstInstallTime = activityInfo.getFirstInstallTime();
                return info;
            } else {
                return LauncherAppState.getInstance().getModel().infoFromShortcutIntent(mContext, data);
            }
        }

        public String getTargetPackage() {
            String packageName = launchIntent.getPackage();
            if (packageName == null) {
                packageName = launchIntent.getComponent() == null ? null :
                    launchIntent.getComponent().getPackageName();
            }
            return packageName;
        }
    }

    private static PendingInstallShortcutInfo decode(String encoded, Context context) {
        try {
            JSONObject object = (JSONObject) new JSONTokener(encoded).nextValue();
            Intent launcherIntent = Intent.parseUri(object.getString(LAUNCH_INTENT_KEY), 0);

            if (object.optBoolean(APP_SHORTCUT_TYPE_KEY)) {
                // The is an internal launcher target shortcut.
                UserHandleCompat user = UserManagerCompat.getInstance(context)
                        .getUserForSerialNumber(object.getLong(USER_HANDLE_KEY));
                if (user == null) {
                    return null;
                }

                LauncherActivityInfoCompat info = LauncherAppsCompat.getInstance(context)
                        .resolveActivity(launcherIntent, user);
                return info == null ? null : new PendingInstallShortcutInfo(info, context);
            }

            Intent data = new Intent();
            data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launcherIntent);
            data.putExtra(Intent.EXTRA_SHORTCUT_NAME, object.getString(NAME_KEY));

            String iconBase64 = object.optString(ICON_KEY);
            String iconResourceName = object.optString(ICON_RESOURCE_NAME_KEY);
            String iconResourcePackageName = object.optString(ICON_RESOURCE_PACKAGE_NAME_KEY);
            if (iconBase64 != null && !iconBase64.isEmpty()) {
                byte[] iconArray = Base64.decode(iconBase64, Base64.DEFAULT);
                Bitmap b = BitmapFactory.decodeByteArray(iconArray, 0, iconArray.length);
                data.putExtra(Intent.EXTRA_SHORTCUT_ICON, b);
            } else if (iconResourceName != null && !iconResourceName.isEmpty()) {
                Intent.ShortcutIconResource iconResource =
                    new Intent.ShortcutIconResource();
                iconResource.resourceName = iconResourceName;
                iconResource.packageName = iconResourcePackageName;
                data.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, iconResource);
            }

            return new PendingInstallShortcutInfo(data, context);
        } catch (JSONException e) {
            Log.d(TAG, "Exception reading shortcut to add: " + e);
        } catch (URISyntaxException e) {
            Log.d(TAG, "Exception reading shortcut to add: " + e);
        }
        return null;
    }
}
