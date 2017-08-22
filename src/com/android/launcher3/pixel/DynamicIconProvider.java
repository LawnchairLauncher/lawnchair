package com.android.launcher3.pixel;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import com.android.launcher3.IconProvider;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.shortcuts.DeepShortcutManager;

import android.content.res.TypedArray;
import android.content.res.Resources;
import android.os.Bundle;

import java.util.Calendar;
import java.util.List;

import android.os.Handler;
import android.content.IntentFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.BroadcastReceiver;
import android.os.UserHandle;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

public class DynamicIconProvider extends IconProvider
{
    private BroadcastReceiver mBroadcastReceiver;
    protected PackageManager mPackageManager;

    public DynamicIconProvider(Context context) {
        mBroadcastReceiver = new DynamicIconProviderReceiver(this);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        context.registerReceiver(mBroadcastReceiver, intentFilter, null, new Handler(LauncherModel.getWorkerLooper()));
        mPackageManager = context.getPackageManager();
    }

    private int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }

    private int getCorrectShape(Bundle bundle, Resources resources) {
        if (bundle != null) {
            int roundIcons = bundle.getInt("com.google.android.calendar.dynamic_icons_nexus_round", 0);
            if (roundIcons != 0) {
                try {
                    TypedArray obtainTypedArray = resources.obtainTypedArray(roundIcons);
                    int resourceId = obtainTypedArray.getResourceId(dayOfMonth(), 0);
                    obtainTypedArray.recycle();
                    return resourceId;
                }
                catch (Resources.NotFoundException ex) {
                }
            }
        }

        return 0;
    }

    private boolean isCalendar(final String s) {
        return "com.google.android.calendar".equals(s);
    }

    private Drawable getRoundIcon(String packageName, int iconDpi) {
        try {
            Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
            AssetManager assets = resourcesForApplication.getAssets();
            XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml");
            int eventType;
            while ((eventType = parseXml.nextToken()) != XmlPullParser.END_DOCUMENT)
                if (eventType == XmlPullParser.START_TAG && parseXml.getName().equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++)
                        if (parseXml.getAttributeName(i).equals("roundIcon"))
                            return resourcesForApplication.getDrawableForDensity(Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi);
            parseXml.close();
        }
        catch (Exception ex) {
            Log.w("getRoundIcon", ex);
        }
        return null;
    }

    @Override
    public Drawable getIcon(final LauncherActivityInfo launcherActivityInfoCompat, int iconDpi) {
        String packageName = launcherActivityInfoCompat.getApplicationInfo().packageName;
        Drawable drawable = getRoundIcon(packageName, iconDpi);

        if (isCalendar(packageName)) {
            try {
                ActivityInfo activityInfo = mPackageManager.getActivityInfo(launcherActivityInfoCompat.getComponentName(), PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                Bundle metaData = activityInfo.metaData;
                Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
                int shape = getCorrectShape(metaData, resourcesForApplication);
                if (shape != 0) {
                    drawable = resourcesForApplication.getDrawableForDensity(shape, iconDpi);
                }
            }
            catch (PackageManager.NameNotFoundException ex3) {}
        }

        if (drawable == null) {
            drawable = super.getIcon(launcherActivityInfoCompat, iconDpi);
        }

        return drawable;
    }

    public String getIconSystemState(String s) {
        if (isCalendar(s)) {
            return mSystemState + " " + dayOfMonth();
        }
        return mSystemState;
    }

    class DynamicIconProviderReceiver extends BroadcastReceiver
    {
        DynamicIconProvider mDynamicIconProvider;

        DynamicIconProviderReceiver(final DynamicIconProvider dynamicIconProvider) {
            mDynamicIconProvider = dynamicIconProvider;
        }

        public void onReceive(final Context context, final Intent intent) {
            for (UserHandle userHandleCompat : UserManagerCompat.getInstance(context).getUserProfiles()) {
                LauncherAppState instance = LauncherAppState.getInstance(context);
                instance.getModel().onPackageChanged("com.google.android.calendar", userHandleCompat);
                List queryForPinnedShortcuts = DeepShortcutManager.getInstance(context).queryForPinnedShortcuts("com.google.android.calendar", userHandleCompat);
                if (!queryForPinnedShortcuts.isEmpty()) {
                    instance.getModel().updatePinnedShortcuts("com.google.android.calendar", queryForPinnedShortcuts, userHandleCompat);
                }
            }
        }
    }
}