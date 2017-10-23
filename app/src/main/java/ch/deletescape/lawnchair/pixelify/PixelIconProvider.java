package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;

import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.compat.UserManagerCompat;
import ch.deletescape.lawnchair.graphics.IconShapeOverride;
import ch.deletescape.lawnchair.iconpack.CustomIconDrawable;
import ch.deletescape.lawnchair.iconpack.IconPack;
import ch.deletescape.lawnchair.iconpack.IconPackProvider;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;

public class PixelIconProvider {
    private BroadcastReceiver mBroadcastReceiver;
    private PackageManager mPackageManager;
    private IconPack sIconPack;
    private Context mContext;
    private final boolean mBackportAdaptive;
    private final IconShapeOverride.ShapeInfo mShapeInfo;
    private final IPreferenceProvider mPrefs;

    private ArrayList<String> mCalendars;

    public PixelIconProvider(Context context) {
        mBroadcastReceiver = new DynamicIconProviderReceiver(this);
        IntentFilter intentFilter = new IntentFilter("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.TIMEZONE_CHANGED");
        context.registerReceiver(mBroadcastReceiver, intentFilter, null, new Handler(LauncherModel.getWorkerLooper()));
        mPackageManager = context.getPackageManager();
        mContext = context;
        mPrefs = Utilities.getPrefs(mContext);
        mBackportAdaptive = mPrefs.getBackportAdaptiveIcons();
        mShapeInfo = IconShapeOverride.Companion.getAppliedValue(context);
        updateIconPack();
    }

    public static int dayOfMonth() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }

    private int getCorrectShape(Bundle bundle, Resources resources) {
        if (bundle != null) {
            int roundIcons = bundle.getInt((mShapeInfo.getUseRoundIcon() && !TextUtils.isEmpty(mShapeInfo.getSavedPref())) ?
                    "com.google.android.calendar.dynamic_icons_nexus_round" :
                    "com.google.android.calendar.dynamic_icons", 0);
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
                        if (parseXml.getAttributeName(i).equals(mShapeInfo.getXmlAttrName()))
                            return mBackportAdaptive ?
                                    AdaptiveIconProvider.Companion.
                                        getDrawableForDensity(resourcesForApplication, Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi, mShapeInfo) :
                                    resourcesForApplication.getDrawableForDensity(Integer.parseInt(parseXml.getAttributeValue(i).substring(1)), iconDpi);
            parseXml.close();
        } catch (Exception ex) {
            Log.w("getRoundIcon", ex);
        }
        return null;
    }

    public void updateIconPack() {
        sIconPack = IconPackProvider.loadAndGetIconPack(mContext);
        mCalendars = new ArrayList<>();
        mCalendars.add("com.google.android.calendar");
        if (sIconPack != null) {
            mCalendars.addAll(sIconPack.getCalendars());
        }
    }

    private IconPack getIconPackForComponent(ComponentName componentName) {
        String alternateIcon = mPrefs.alternateIcon(componentName.flattenToString());
        if (alternateIcon == null) return sIconPack;
        if (alternateIcon.startsWith("iconPacks")) {
            return getIconPack(alternateIcon);
        }
        return sIconPack;
    }

    @Nullable
    private IconPack getIconPack(String alternateIcon) {
        if (alternateIcon.startsWith("iconPacks")) {
            String[] parts = alternateIcon.split("/");
            if (parts.length == 2) {
                return IconPackProvider.loadAndGetIconPack(mContext, parts[1]);
            }
        }
        return null;
    }

    private Drawable getIconForComponent(ComponentName componentName) {
        String alternateIcon = mPrefs.alternateIcon(componentName.flattenToString());
        return getAlternateIcon(alternateIcon, null);
    }

    public Drawable getAlternateIcon(String alternateIcon, LauncherActivityInfoCompat laic) {
        if (alternateIcon == null) return null;
        if (alternateIcon.startsWith("uri")) {
            alternateIcon = alternateIcon.substring(4);
            Uri uri = Uri.parse(alternateIcon);
            try {
                InputStream inputStream = mContext.getContentResolver().openInputStream(uri);
                return Drawable.createFromStream(inputStream, alternateIcon);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else if (alternateIcon.startsWith("resourceId")) {
            try {
                String[] parts = alternateIcon.substring(11).split("/");
                IconPack iconPack = IconPackProvider.loadAndGetIconPack(mContext, parts[0]);
                return iconPack.getDrawable(Integer.parseInt(parts[1]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (alternateIcon.startsWith("resource")) {
            try {
                String[] parts = alternateIcon.substring(9).split("/");
                IconPack iconPack = IconPackProvider.loadAndGetIconPack(mContext, parts[0]);
                return iconPack.getDrawable(parts[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (laic != null && alternateIcon.startsWith("iconPacks")) {
            IconPack iconPack = getIconPack(alternateIcon);
            if (iconPack == null) return null;
            return iconPack.getIcon(laic);
        }
        return null;
    }

    public Drawable getIcon(final LauncherActivityInfoCompat info, int iconDpi) {
        Drawable drawable = getIconForComponent(info.getComponentName());
        if (drawable == null) {
            IconPack iconPack = getIconPackForComponent(info.getComponentName());
            drawable = iconPack == null ? null : iconPack.getIcon(info);
        }
        return getDefaultIcon(info, iconDpi, drawable);
    }

    public Drawable getDefaultIcon(LauncherActivityInfoCompat info, int iconDpi, Drawable drawable) {
        boolean isRoundPack = isRoundIconPack(sIconPack);
        if ((drawable == null && (mBackportAdaptive || mShapeInfo.getUseRoundIcon()) && !TextUtils.isEmpty(mShapeInfo.getSavedPref())) ||
                (isRoundPack && drawable instanceof CustomIconDrawable)) {
            Drawable roundIcon = getRoundIcon(info.getComponentName().getPackageName(), iconDpi);
            if (roundIcon != null)
                drawable = roundIcon;
        }

        if (drawable == null) {
            drawable = info.getIcon(iconDpi);
        }

        String packageName = info.getApplicationInfo().packageName;
        if (isCalendar(packageName) && TextUtils.isEmpty(mPrefs.getIconPackPackage())) {
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

        return drawable;
    }

    private boolean isRoundIconPack(IconPack iconPack) {
        return iconPack != null && iconPack.getPackageName().contains("pixel");
    }

    class DynamicIconProviderReceiver extends BroadcastReceiver {
        PixelIconProvider mDynamicIconProvider;

        DynamicIconProviderReceiver(final PixelIconProvider dynamicIconProvider) {
            mDynamicIconProvider = dynamicIconProvider;
        }

        @Override
        public void onReceive(final Context context, final Intent intent) {
            for (UserHandle userHandle : UserManagerCompat.getInstance(context).getUserProfiles()) {
                for (String calendar : mCalendars) {
                    Utilities.updatePackage(context, userHandle, calendar);
                }
            }
        }
    }
}
