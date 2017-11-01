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

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;
import com.android.launcher3.util.Thunk;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
    private static final String DEEPSHORTCUT_TYPE_KEY = "isDeepShortcut";
    private static final String APP_WIDGET_TYPE_KEY = "isAppWidget";
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
                strings = (strings != null) ? new HashSet<>(strings) : new HashSet<String>(1);
                strings.add(encoded);
                sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, strings).apply();
            }
        }
    }

    public static void removeFromInstallQueue(Context context, HashSet<String> packageNames,
            UserHandle user) {
        if (packageNames.isEmpty()) {
            return;
        }
        SharedPreferences sp = Utilities.getPrefs(context);
        synchronized(sLock) {
            Set<String> strings = sp.getStringSet(APPS_PENDING_INSTALL, null);
            if (DBG) {
                Log.d(TAG, "APPS_PENDING_INSTALL: " + strings
                        + ", removing packages: " + packageNames);
            }
            if (Utilities.isEmpty(strings)) {
                return;
            }
            Set<String> newStrings = new HashSet<>(strings);
            Iterator<String> newStringsIter = newStrings.iterator();
            while (newStringsIter.hasNext()) {
                String encoded = newStringsIter.next();
                try {
                    Decoder decoder = new Decoder(encoded, context);
                    if (packageNames.contains(getIntentPackage(decoder.launcherIntent)) &&
                            user.equals(decoder.user)) {
                        newStringsIter.remove();
                    }
                } catch (JSONException | URISyntaxException e) {
                    Log.d(TAG, "Exception reading shortcut to add: " + e);
                    newStringsIter.remove();
                }
            }
            sp.edit().putStringSet(APPS_PENDING_INSTALL, newStrings).apply();
        }
    }

    private static ArrayList<PendingInstallShortcutInfo> getAndClearInstallQueue(Context context) {
        SharedPreferences sharedPrefs = Utilities.getPrefs(context);
        synchronized(sLock) {
            ArrayList<PendingInstallShortcutInfo> infos = new ArrayList<>();
            Set<String> strings = sharedPrefs.getStringSet(APPS_PENDING_INSTALL, null);
            if (DBG) Log.d(TAG, "Getting and clearing APPS_PENDING_INSTALL: " + strings);
            if (strings == null) {
                return infos;
            }
            for (String encoded : strings) {
                PendingInstallShortcutInfo info = decode(encoded, context);
                if (info != null) {
                    infos.add(info);
                }
            }
            sharedPrefs.edit().putStringSet(APPS_PENDING_INSTALL, new HashSet<String>()).apply();
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
        PendingInstallShortcutInfo info = createPendingInfo(context, data);
        if (info != null) {
            if (!info.isLauncherActivity()) {
                // Since its a custom shortcut, verify that it is safe to launch.
                if (!new PackageManagerHelper(context).hasPermissionForActivity(
                        info.launchIntent, null)) {
                    // Target cannot be launched, or requires some special permission to launch
                    Log.e(TAG, "Ignoring malicious intent " + info.launchIntent.toUri(0));
                    return;
                }
            }
            queuePendingShortcutInfo(info, context);
        }
    }

    /**
     * @return true is the extra is either null or is of type {@param type}
     */
    private static boolean isValidExtraType(Intent intent, String key, Class type) {
        Object extra = intent.getParcelableExtra(key);
        return extra == null || type.isInstance(extra);
    }

    /**
     * Verifies the intent and creates a {@link PendingInstallShortcutInfo}
     */
    private static PendingInstallShortcutInfo createPendingInfo(Context context, Intent data) {
        if (!isValidExtraType(data, Intent.EXTRA_SHORTCUT_INTENT, Intent.class) ||
                !(isValidExtraType(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.class)) ||
                !(isValidExtraType(data, Intent.EXTRA_SHORTCUT_ICON, Bitmap.class))) {

            if (DBG) Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        PendingInstallShortcutInfo info = new PendingInstallShortcutInfo(
                data, Process.myUserHandle(), context);
        if (info.launchIntent == null || info.label == null) {
            if (DBG) Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        return convertToLauncherActivityIfPossible(info);
    }

    public static ShortcutInfo fromShortcutIntent(Context context, Intent data) {
        PendingInstallShortcutInfo info = createPendingInfo(context, data);
        return info == null ? null : (ShortcutInfo) info.getItemInfo();
    }

    public static void queueShortcut(ShortcutInfoCompat info, Context context) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(info, context), context);
    }

    public static void queueWidget(AppWidgetProviderInfo info, int widgetId, Context context) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(info, widgetId, context), context);
    }

    public static void queueActivityInfo(LauncherActivityInfo activity, Context context) {
        queuePendingShortcutInfo(new PendingInstallShortcutInfo(activity, context), context);
    }

    public static HashSet<ShortcutKey> getPendingShortcuts(Context context) {
        HashSet<ShortcutKey> result = new HashSet<>();

        Set<String> strings = Utilities.getPrefs(context).getStringSet(APPS_PENDING_INSTALL, null);
        if (Utilities.isEmpty(strings)) {
            return result;
        }

        for (String encoded : strings) {
            try {
                Decoder decoder = new Decoder(encoded, context);
                if (decoder.optBoolean(DEEPSHORTCUT_TYPE_KEY)) {
                    result.add(ShortcutKey.fromIntent(decoder.launcherIntent, decoder.user));
                }
            } catch (JSONException | URISyntaxException e) {
                Log.d(TAG, "Exception reading shortcut to add: " + e);
            }
        }
        return result;
    }

    private static void queuePendingShortcutInfo(PendingInstallShortcutInfo info, Context context) {
        // Queue the item up for adding if launcher has not loaded properly yet
        LauncherAppState app = LauncherAppState.getInstance(context);
        boolean launcherNotLoaded = app.getModel().getCallback() == null;

        addToInstallQueue(Utilities.getPrefs(context), info);
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
        ArrayList<PendingInstallShortcutInfo> items = getAndClearInstallQueue(context);
        if (!items.isEmpty()) {
            LauncherAppState.getInstance(context).getModel().addAndBindAddedWorkspaceItems(
                    new LazyShortcutsProvider(context.getApplicationContext(), items));
        }
    }

    /**
     * Ensures that we have a valid, non-null name.  If the provided name is null, we will return
     * the application name instead.
     */
    @Thunk static CharSequence ensureValidName(Context context, Intent intent, CharSequence name) {
        if (name == null) {
            try {
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = pm.getActivityInfo(intent.getComponent(), 0);
                name = info.loadLabel(pm);
            } catch (PackageManager.NameNotFoundException nnfe) {
                return "";
            }
        }
        return name;
    }

    private static class PendingInstallShortcutInfo {

        final LauncherActivityInfo activityInfo;
        final ShortcutInfoCompat shortcutInfo;
        final AppWidgetProviderInfo providerInfo;

        final Intent data;
        final Context mContext;
        final Intent launchIntent;
        final String label;
        final UserHandle user;

        /**
         * Initializes a PendingInstallShortcutInfo received from a different app.
         */
        public PendingInstallShortcutInfo(Intent data, UserHandle user, Context context) {
            activityInfo = null;
            shortcutInfo = null;
            providerInfo = null;

            this.data = data;
            this.user = user;
            mContext = context;

            launchIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a launcher target.
         */
        public PendingInstallShortcutInfo(LauncherActivityInfo info, Context context) {
            activityInfo = info;
            shortcutInfo = null;
            providerInfo = null;

            data = null;
            user = info.getUser();
            mContext = context;

            launchIntent = AppInfo.makeLaunchIntent(info);
            label = info.getLabel().toString();
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a launcher target.
         */
        public PendingInstallShortcutInfo(ShortcutInfoCompat info, Context context) {
            activityInfo = null;
            shortcutInfo = info;
            providerInfo = null;

            data = null;
            mContext = context;
            user = info.getUserHandle();

            launchIntent = info.makeIntent();
            label = info.getShortLabel().toString();
        }

        /**
         * Initializes a PendingInstallShortcutInfo to represent a launcher target.
         */
        public PendingInstallShortcutInfo(
                AppWidgetProviderInfo info, int widgetId, Context context) {
            activityInfo = null;
            shortcutInfo = null;
            providerInfo = info;

            data = null;
            mContext = context;
            user = info.getProfile();

            launchIntent = new Intent().setComponent(info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
            label = info.label;
        }

        public String encodeToString() {
            try {
                if (activityInfo != null) {
                    // If it a launcher target, we only need component name, and user to
                    // recreate this.
                    return new JSONStringer()
                        .object()
                        .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                        .key(APP_SHORTCUT_TYPE_KEY).value(true)
                        .key(USER_HANDLE_KEY).value(UserManagerCompat.getInstance(mContext)
                                .getSerialNumberForUser(user))
                        .endObject().toString();
                } else if (shortcutInfo != null) {
                    // If it a launcher target, we only need component name, and user to
                    // recreate this.
                    return new JSONStringer()
                            .object()
                            .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                            .key(DEEPSHORTCUT_TYPE_KEY).value(true)
                            .key(USER_HANDLE_KEY).value(UserManagerCompat.getInstance(mContext)
                                    .getSerialNumberForUser(user))
                            .endObject().toString();
                } else if (providerInfo != null) {
                    // If it a launcher target, we only need component name, and user to
                    // recreate this.
                    return new JSONStringer()
                            .object()
                            .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                            .key(APP_WIDGET_TYPE_KEY).value(true)
                            .key(USER_HANDLE_KEY).value(UserManagerCompat.getInstance(mContext)
                                    .getSerialNumberForUser(user))
                            .endObject().toString();
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
                JSONStringer json = new JSONStringer()
                    .object()
                    .key(LAUNCH_INTENT_KEY).value(launchIntent.toUri(0))
                    .key(NAME_KEY).value(name);
                if (icon != null) {
                    byte[] iconByteArray = Utilities.flattenBitmap(icon);
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
                return null;
            }
        }

        public ItemInfo getItemInfo() {
            if (activityInfo != null) {
                AppInfo appInfo = new AppInfo(mContext, activityInfo, user);
                final LauncherAppState app = LauncherAppState.getInstance(mContext);
                // Set default values until proper values is loaded.
                appInfo.title = "";
                appInfo.iconBitmap = app.getIconCache().getDefaultIcon(user);
                final ShortcutInfo si = appInfo.makeShortcut();
                if (Looper.myLooper() == LauncherModel.getWorkerLooper()) {
                    app.getIconCache().getTitleAndIcon(si, activityInfo, false /* useLowResIcon */);
                } else {
                    app.getModel().updateAndBindShortcutInfo(new Provider<ShortcutInfo>() {
                        @Override
                        public ShortcutInfo get() {
                            app.getIconCache().getTitleAndIcon(
                                    si, activityInfo, false /* useLowResIcon */);
                            return si;
                        }
                    });
                }
                return si;
            } else if (shortcutInfo != null) {
                ShortcutInfo si = new ShortcutInfo(shortcutInfo, mContext);
                si.iconBitmap = LauncherIcons.createShortcutIcon(shortcutInfo, mContext);
                return si;
            } else if (providerInfo != null) {
                LauncherAppWidgetProviderInfo info = LauncherAppWidgetProviderInfo
                        .fromProviderInfo(mContext, providerInfo);
                LauncherAppWidgetInfo widgetInfo = new LauncherAppWidgetInfo(
                        launchIntent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0),
                        info.provider);
                InvariantDeviceProfile idp = LauncherAppState.getIDP(mContext);
                widgetInfo.minSpanX = info.minSpanX;
                widgetInfo.minSpanY = info.minSpanY;
                widgetInfo.spanX = Math.min(info.spanX, idp.numColumns);
                widgetInfo.spanY = Math.min(info.spanY, idp.numRows);
                return widgetInfo;
            } else {
                return createShortcutInfo(data, LauncherAppState.getInstance(mContext));
            }
        }

        public boolean isLauncherActivity() {
            return activityInfo != null;
        }
    }

    private static String getIntentPackage(Intent intent) {
        return intent.getComponent() == null
                ? intent.getPackage() : intent.getComponent().getPackageName();
    }

    private static PendingInstallShortcutInfo decode(String encoded, Context context) {
        try {
            Decoder decoder = new Decoder(encoded, context);
            if (decoder.optBoolean(APP_SHORTCUT_TYPE_KEY)) {
                LauncherActivityInfo info = LauncherAppsCompat.getInstance(context)
                        .resolveActivity(decoder.launcherIntent, decoder.user);
                return info == null ? null : new PendingInstallShortcutInfo(info, context);
            } else if (decoder.optBoolean(DEEPSHORTCUT_TYPE_KEY)) {
                DeepShortcutManager sm = DeepShortcutManager.getInstance(context);
                List<ShortcutInfoCompat> si = sm.queryForFullDetails(
                        decoder.launcherIntent.getPackage(),
                        Arrays.asList(decoder.launcherIntent.getStringExtra(
                                ShortcutInfoCompat.EXTRA_SHORTCUT_ID)),
                        decoder.user);
                if (si.isEmpty()) {
                    return null;
                } else {
                    return new PendingInstallShortcutInfo(si.get(0), context);
                }
            } else if (decoder.optBoolean(APP_WIDGET_TYPE_KEY)) {
                int widgetId = decoder.launcherIntent
                        .getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
                AppWidgetProviderInfo info = AppWidgetManager.getInstance(context)
                        .getAppWidgetInfo(widgetId);
                if (info == null || !info.provider.equals(decoder.launcherIntent.getComponent()) ||
                        !info.getProfile().equals(decoder.user)) {
                    return null;
                }
                return new PendingInstallShortcutInfo(info, widgetId, context);
            }

            Intent data = new Intent();
            data.putExtra(Intent.EXTRA_SHORTCUT_INTENT, decoder.launcherIntent);
            data.putExtra(Intent.EXTRA_SHORTCUT_NAME, decoder.getString(NAME_KEY));

            String iconBase64 = decoder.optString(ICON_KEY);
            String iconResourceName = decoder.optString(ICON_RESOURCE_NAME_KEY);
            String iconResourcePackageName = decoder.optString(ICON_RESOURCE_PACKAGE_NAME_KEY);
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

            return new PendingInstallShortcutInfo(data, decoder.user, context);
        } catch (JSONException | URISyntaxException e) {
            Log.d(TAG, "Exception reading shortcut to add: " + e);
        }
        return null;
    }

    private static class Decoder extends JSONObject {
        public final Intent launcherIntent;
        public final UserHandle user;

        private Decoder(String encoded, Context context) throws JSONException, URISyntaxException {
            super(encoded);
            launcherIntent = Intent.parseUri(getString(LAUNCH_INTENT_KEY), 0);
            user = has(USER_HANDLE_KEY) ? UserManagerCompat.getInstance(context)
                    .getUserForSerialNumber(getLong(USER_HANDLE_KEY))
                    : Process.myUserHandle();
            if (user == null) {
                throw new JSONException("Invalid user");
            }
        }
    }

    /**
     * Tries to create a new PendingInstallShortcutInfo which represents the same target,
     * but is an app target and not a shortcut.
     * @return the newly created info or the original one.
     */
    private static PendingInstallShortcutInfo convertToLauncherActivityIfPossible(
            PendingInstallShortcutInfo original) {
        if (original.isLauncherActivity()) {
            // Already an activity target
            return original;
        }
        if (!Utilities.isLauncherAppTarget(original.launchIntent)) {
            return original;
        }

        LauncherActivityInfo info = LauncherAppsCompat.getInstance(original.mContext)
                .resolveActivity(original.launchIntent, original.user);
        if (info == null) {
            return original;
        }
        // Ignore any conflicts in the label name, as that can change based on locale.
        return new PendingInstallShortcutInfo(info, original.mContext);
    }

    private static class LazyShortcutsProvider extends Provider<List<ItemInfo>> {

        private final Context mContext;
        private final ArrayList<PendingInstallShortcutInfo> mPendingItems;

        public LazyShortcutsProvider(Context context, ArrayList<PendingInstallShortcutInfo> items) {
            mContext = context;
            mPendingItems = items;
        }

        /**
         * This must be called on the background thread as this requires multiple calls to
         * packageManager and icon cache.
         */
        @Override
        public ArrayList<ItemInfo> get() {
            Preconditions.assertNonUiThread();
            ArrayList<ItemInfo> installQueue = new ArrayList<>();
            LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(mContext);
            for (PendingInstallShortcutInfo pendingInfo : mPendingItems) {
                // If the intent specifies a package, make sure the package exists
                String packageName = getIntentPackage(pendingInfo.launchIntent);
                if (!TextUtils.isEmpty(packageName) && !launcherApps.isPackageEnabledForProfile(
                        packageName, pendingInfo.user)) {
                    if (DBG) Log.d(TAG, "Ignoring shortcut for absent package: "
                            + pendingInfo.launchIntent);
                    continue;
                }

                // Generate a shortcut info to add into the model
                installQueue.add(pendingInfo.getItemInfo());
            }
            return installQueue;
        }
    }

    private static ShortcutInfo createShortcutInfo(Intent data, LauncherAppState app) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Parcelable bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        if (intent == null) {
            // If the intent is null, we can't construct a valid ShortcutInfo, so we return null
            Log.e(TAG, "Can't construct ShorcutInfo with null intent");
            return null;
        }

        final ShortcutInfo info = new ShortcutInfo();

        // Only support intents for current user for now. Intents sent from other
        // users wouldn't get here without intent forwarding anyway.
        info.user = Process.myUserHandle();

        if (bitmap instanceof Bitmap) {
            info.iconBitmap = LauncherIcons.createIconBitmap((Bitmap) bitmap, app.getContext());
        } else {
            Parcelable extra = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra instanceof Intent.ShortcutIconResource) {
                info.iconResource = (Intent.ShortcutIconResource) extra;
                info.iconBitmap = LauncherIcons.createIconBitmap(info.iconResource, app.getContext());
            }
        }
        if (info.iconBitmap == null) {
            info.iconBitmap = app.getIconCache().getDefaultIcon(info.user);
        }

        info.title = Utilities.trim(name);
        info.contentDescription = UserManagerCompat.getInstance(app.getContext())
                .getBadgedLabelForUser(info.title, info.user);
        info.intent = intent;
        return info;
    }

}
