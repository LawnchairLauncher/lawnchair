package com.google.android.apps.nexuslauncher;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomIconProvider extends DynamicIconProvider implements Runnable {
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final BroadcastReceiver mDateChangeReceiver;
    private final Map<String, Integer> mIconPackComponents = new HashMap<>();
    private final Map<String, String> mIconPackCalendars = new HashMap<>();
    private Thread mThread;
    private String mIconPack;
    private int mDateOfMonth;

    public CustomIconProvider(Context context) {
        super(context);
        mContext = context;
        mDateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Utilities.ATLEAST_NOUGAT) {
                    int dateOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                    if (dateOfMonth == mDateOfMonth) {
                        return;
                    }
                    mDateOfMonth = dateOfMonth;
                }
                for (UserHandle user : UserManagerCompat.getInstance(context).getUserProfiles()) {
                    LauncherModel model = LauncherAppState.getInstance(context).getModel();
                    LauncherAppsCompat apps = LauncherAppsCompat.getInstance(mContext);
                    for (Map.Entry<String, String> calendars : mIconPackCalendars.entrySet()) {
                        ComponentName componentName = ComponentName.unflattenFromString(calendars.getKey());
                        if (componentName != null) {
                            String pkg = componentName.getPackageName();
                            if (!apps.getActivityList(pkg, user).isEmpty()) {
                                model.onPackageChanged(pkg, user);
                                List<ShortcutInfoCompat> shortcuts = DeepShortcutManager.getInstance(context).queryForPinnedShortcuts(pkg, user);
                                if (!shortcuts.isEmpty()) {
                                    model.updatePinnedShortcuts(pkg, shortcuts, user);
                                }
                            }
                        }
                    }
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_DATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        if (!Utilities.ATLEAST_NOUGAT) {
            intentFilter.addAction(Intent.ACTION_TIME_TICK);
        }
        mContext.registerReceiver(mDateChangeReceiver, intentFilter, null, new Handler(LauncherModel.getWorkerLooper()));

        mPackageManager = context.getPackageManager();

        mThread = new Thread(this);
        mThread.start();
    }

    @Override
    public void run() {
        mIconPack = Utilities.getPrefs(mContext).getString(SettingsActivity.ICON_PACK_PREF, "");
        if (CustomIconUtils.isPackProvider(mContext, mIconPack)) {
            try {
                Resources res = mPackageManager.getResourcesForApplication(mIconPack);
                int resId = res.getIdentifier("appfilter", "xml", mIconPack);
                if (resId != 0) {
                    XmlResourceParser parseXml = mPackageManager.getXml(mIconPack, resId, null);
                    while (parseXml.next() != XmlPullParser.END_DOCUMENT) {
                        if (parseXml.getEventType() == XmlPullParser.START_TAG) {
                            boolean isCalendar = parseXml.getName().equals("calendar");
                            if (isCalendar || parseXml.getName().equals("item")) {
                                String componentName = parseXml.getAttributeValue(null, "component");
                                String drawableName = parseXml.getAttributeValue(null, isCalendar ? "prefix" : "drawable");
                                if (componentName != null && drawableName != null) {
                                    if (isCalendar) {
                                        mIconPackCalendars.put(componentName, drawableName);
                                    } else {
                                        int drawableId = res.getIdentifier(drawableName, "drawable", mIconPack);
                                        if (drawableId != 0) {
                                            mIconPackComponents.put(componentName, drawableId);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException | XmlPullParserException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public Drawable getIcon(LauncherActivityInfo launcherActivityInfo, int iconDpi, boolean flattenDrawable) {
        try {
            mThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String packageName = launcherActivityInfo.getApplicationInfo().packageName;
        String component = launcherActivityInfo.getComponentName().toString();
        Drawable drawable = null;
        if (mIconPackCalendars.containsKey(component)) {
            try {
                Resources res = mPackageManager.getResourcesForApplication(mIconPack);
                int drawableId = res.getIdentifier(mIconPackCalendars.get(component)
                        + Calendar.getInstance().get(Calendar.DAY_OF_MONTH), "drawable", mIconPack);
                if (drawableId != 0) {
                    drawable = mPackageManager.getDrawable(mIconPack, drawableId, null);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        } else if (mIconPackComponents.containsKey(component)) {
            drawable = mPackageManager.getDrawable(mIconPack, mIconPackComponents.get(component), null);
        }

        if (drawable == null) {
            drawable = super.getIcon(launcherActivityInfo, iconDpi, flattenDrawable);
            if ((!Utilities.ATLEAST_OREO || !(drawable instanceof AdaptiveIconDrawable)) &&
                    !"com.google.android.calendar".equals(packageName)) {
                Drawable roundIcon = getRoundIcon(packageName, iconDpi);
                if (roundIcon != null) {
                    drawable = roundIcon;
                }
            }
        }
        return drawable;
    }

    private Drawable getRoundIcon(String packageName, int iconDpi) {
        try {
            Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
            AssetManager assets = resourcesForApplication.getAssets();
            XmlResourceParser parseXml = assets.openXmlResourceParser("AndroidManifest.xml");
            while (parseXml.next() != XmlPullParser.END_DOCUMENT)
                if (parseXml.getEventType() == XmlPullParser.START_TAG && parseXml.getName().equals("application"))
                    for (int i = 0; i < parseXml.getAttributeCount(); i++)
                        if (parseXml.getAttributeName(i).equals("roundIcon"))
                            return resourcesForApplication.getDrawableForDensity(Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi);
            parseXml.close();
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException | IOException | XmlPullParserException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
