package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.util.Calendar;
import java.util.List;

import ch.deletescape.lawnchair.IconPack;
import ch.deletescape.lawnchair.IconPackProvider;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.config.FeatureFlags;
import ch.deletescape.lawnchair.shortcuts.DeepShortcutManager;

public class PixelIconProvider {
    private BroadcastReceiver mBroadcastReceiver;
    private PackageManager mPackageManager;
    private IconPack sIconPack;
    private Context mContext;

    public PixelIconProvider(Context context) {
        mBroadcastReceiver = new DynamicIconProviderReceiver(this);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        context.registerReceiver(mBroadcastReceiver, intentFilter, null, new Handler(LauncherModel.getWorkerLooper()));
        mPackageManager = context.getPackageManager();
        sIconPack = IconPackProvider.loadAndGetIconPack(context);
        mContext = context;
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
                } catch (Resources.NotFoundException ex) {
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
        } catch (Exception ex) {
            Log.w("getRoundIcon", ex);
        }
        return null;
    }

    public void updateIconPack() {
        sIconPack = IconPackProvider.loadAndGetIconPack(mContext);
    }

    public Drawable getIcon(final LauncherActivityInfoCompat info, int iconDpi) {
        Drawable drawable = sIconPack == null ? null : sIconPack.getIcon(info);
        if (drawable == null && FeatureFlags.usePixelIcons(mContext)) {
            drawable = getRoundIcon(info.getComponentName().getPackageName(), iconDpi);
            String packageName = info.getApplicationInfo().packageName;
            if (isCalendar(packageName)) {
                try {
                    ActivityInfo activityInfo = mPackageManager.getActivityInfo(info.getComponentName(), PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES);
                    Bundle metaData = activityInfo.metaData;
                    Resources resourcesForApplication = mPackageManager.getResourcesForApplication(packageName);
                    int shape = getCorrectShape(metaData, resourcesForApplication);
                    if (shape != 0) {
                        drawable = resourcesForApplication.getDrawableForDensity(shape, iconDpi);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
        }

        if (drawable == null) {
            drawable = info.getIcon(iconDpi);
        }
        return drawable;
    }

    class DynamicIconProviderReceiver extends BroadcastReceiver {
        PixelIconProvider mDynamicIconProvider;

        DynamicIconProviderReceiver(final PixelIconProvider dynamicIconProvider) {
            mDynamicIconProvider = dynamicIconProvider;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            for (UserHandle userHandle : UserManagerCompat.getInstance(context).getUserProfiles()) {
                LauncherAppState instance = LauncherAppState.getInstance();
                instance.getModel().onPackageChanged("com.google.android.calendar", userHandle);
                List queryForPinnedShortcuts = DeepShortcutManager.getInstance(context).queryForPinnedShortcuts("com.google.android.calendar", userHandle);
                if (!queryForPinnedShortcuts.isEmpty()) {
                    instance.getModel().updatePinnedShortcuts("com.google.android.calendar", queryForPinnedShortcuts, userHandle);
                }
            }
        }
    }
}
