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

package com.android.launcher3.icons;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo.Extender;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SafeCloseable;

import java.util.Calendar;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Class to handle icon loading from different packages
 */
public class IconProvider {

    private static final String TAG = "IconProvider";
    private static final boolean DEBUG = false;

    private static final String ICON_METADATA_KEY_PREFIX = ".dynamic_icons";

    private static final String SYSTEM_STATE_SEPARATOR = " ";

    // Default value returned if there are problems getting resources.
    private static final int NO_ID = 0;

    private static final BiFunction<LauncherActivityInfo, Integer, Drawable> LAI_LOADER =
            LauncherActivityInfo::getIcon;

    private static final BiFunction<ActivityInfo, PackageManager, Drawable> AI_LOADER =
            ActivityInfo::loadUnbadgedIcon;


    private final Context mContext;
    private final ComponentName mCalendar;
    private final ComponentName mClock;

    public IconProvider(Context context) {
        mContext = context;
        mCalendar = parseComponentOrNull(context, R.string.calendar_component_name);
        mClock = parseComponentOrNull(context, R.string.clock_component_name);
    }

    /**
     * Adds any modification to the provided systemState for dynamic icons. This system state
     * is used by caches to check for icon invalidation.
     */
    public String getSystemStateForPackage(String systemState, String packageName) {
        if (mCalendar != null && mCalendar.getPackageName().equals(packageName)) {
            return systemState + SYSTEM_STATE_SEPARATOR + getDay();
        } else {
            return systemState;
        }
    }

    /**
     * Loads the icon for the provided LauncherActivityInfo such that it can be drawn directly
     * on the UI
     */
    public Drawable getIconForUI(LauncherActivityInfo info, int iconDpi) {
        Drawable icon = getIcon(info, iconDpi);
        if (icon instanceof BitmapInfo.Extender) {
            ((Extender) icon).prepareToDrawOnUi();
        }
        return icon;
    }

    /**
     * Loads the icon for the provided LauncherActivityInfo
     */
    public Drawable getIcon(LauncherActivityInfo info, int iconDpi) {
        return getIcon(info.getApplicationInfo().packageName, info.getUser(),
                info, iconDpi, LAI_LOADER);
    }

    /**
     * Loads the icon for the provided activity info
     */
    public Drawable getIcon(ActivityInfo info, UserHandle user) {
        return getIcon(info.applicationInfo.packageName, user, info, mContext.getPackageManager(),
                AI_LOADER);
    }

    private <T, P> Drawable getIcon(String packageName, UserHandle user, T obj, P param,
            BiFunction<T, P, Drawable> loader) {
        Drawable icon = null;
        if (mCalendar != null && mCalendar.getPackageName().equals(packageName)) {
            icon = loadCalendarDrawable(0);
        } else if (mClock != null
                && mClock.getPackageName().equals(packageName)
                && Process.myUserHandle().equals(user)) {
            icon = loadClockDrawable(0);
        }
        return icon == null ? loader.apply(obj, param) : icon;
    }

    private Drawable loadCalendarDrawable(int iconDpi) {
        PackageManager pm = mContext.getPackageManager();
        try {
            final Bundle metadata = pm.getActivityInfo(
                    mCalendar,
                    PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_META_DATA)
                    .metaData;
            final Resources resources = pm.getResourcesForApplication(mCalendar.getPackageName());
            final int id = getDynamicIconId(metadata, resources);
            if (id != NO_ID) {
                if (DEBUG) Log.d(TAG, "Got icon #" + id);
                return resources.getDrawableForDensity(id, iconDpi, null /* theme */);
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "Could not get activityinfo or resources for package: "
                        + mCalendar.getPackageName());
            }
        }
        return null;
    }

    private Drawable loadClockDrawable(int iconDpi) {
        return ClockDrawableWrapper.forPackage(mContext, mClock.getPackageName(), iconDpi);
    }

    protected boolean isClockIcon(ComponentKey key) {
        return mClock != null && mClock.equals(key.componentName)
                && Process.myUserHandle().equals(key.user);
    }

    /**
     * @param metadata metadata of the default activity of Calendar
     * @param resources from the Calendar package
     * @return the resource id for today's Calendar icon; 0 if resources cannot be found.
     */
    private int getDynamicIconId(Bundle metadata, Resources resources) {
        if (metadata == null) {
            return NO_ID;
        }
        String key = mCalendar.getPackageName() + ICON_METADATA_KEY_PREFIX;
        final int arrayId = metadata.getInt(key, NO_ID);
        if (arrayId == NO_ID) {
            return NO_ID;
        }
        try {
            return resources.obtainTypedArray(arrayId).getResourceId(getDay(), NO_ID);
        } catch (Resources.NotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "package defines '" + key + "' but corresponding array not found");
            }
            return NO_ID;
        }
    }

    /**
     * @return Today's day of the month, zero-indexed.
     */
    private int getDay() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }


    /**
     * Registers a callback to listen for calendar icon changes.
     * The callback receives the packageName for the calendar icon
     */
    public static SafeCloseable registerIconChangeListener(Context context,
            BiConsumer<String, UserHandle> callback, Handler handler) {
        ComponentName calendar = parseComponentOrNull(context, R.string.calendar_component_name);
        ComponentName clock = parseComponentOrNull(context, R.string.clock_component_name);

        if (calendar == null && clock == null) {
            return () -> { };
        }

        BroadcastReceiver receiver = new DateTimeChangeReceiver(callback);
        final IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
        if (calendar != null) {
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
        }
        context.registerReceiver(receiver, filter, null, handler);

        return () -> context.unregisterReceiver(receiver);
    }

    private static class DateTimeChangeReceiver extends BroadcastReceiver {

        private final BiConsumer<String, UserHandle> mCallback;

        DateTimeChangeReceiver(BiConsumer<String, UserHandle> callback) {
            mCallback = callback;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
                ComponentName clock = parseComponentOrNull(context, R.string.clock_component_name);
                if (clock != null) {
                    mCallback.accept(clock.getPackageName(), Process.myUserHandle());
                }
            }

            ComponentName calendar =
                    parseComponentOrNull(context, R.string.calendar_component_name);
            if (calendar != null) {
                for (UserHandle user : UserCache.INSTANCE.get(context).getUserProfiles()) {
                    mCallback.accept(calendar.getPackageName(), user);
                }
            }

        }
    }

    private static ComponentName parseComponentOrNull(Context context, int resId) {
        String cn = context.getString(resId);
        return TextUtils.isEmpty(cn) ? null : ComponentName.unflattenFromString(cn);

    }
}
