package com.cyanogenmod.trebuchet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.XmlResourceParser;
import android.text.TextUtils;
import android.widget.Toast;

import com.cyanogenmod.trebuchet.preference.PreferencesProvider;

public class IconPackHelper {

    public final static String[] sSupportedActions = new String[] {
        "org.adw.launcher.THEMES", "com.gau.go.launcherex.theme"
    };

    public static final String[] sSupportedCategories = new String[] {
        "com.fede.launcher.THEME_ICONPACK", "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME"
    };

    private final Context mContext;
    private Map<ComponentName, String> mIconPackResources;
    private String mLoadedIconPackName;
    private Resources mLoadedIconPackResource;

    IconPackHelper(Context context) {
        mContext = context;
        mIconPackResources = new HashMap<ComponentName, String>();
    }

    public static HashMap<CharSequence, String> getSupportedPackages(Context context) {
        Intent i = new Intent();
        HashMap<CharSequence, String> packages = new HashMap<CharSequence, String>();
        PackageManager packageManager = context.getPackageManager();
        for (String action : sSupportedActions) {
            i.setAction(action);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                packages.put(r.loadLabel(packageManager), r.activityInfo.packageName);
            }
        }
        i = new Intent(Intent.ACTION_MAIN);
        for (String category : sSupportedCategories) {
            i.addCategory(category);
            for (ResolveInfo r : packageManager.queryIntentActivities(i, 0)) {
                packages.put(r.loadLabel(packageManager), r.activityInfo.packageName);
            }
            i.removeCategory(category);
        }
        return packages;
    }

    private void loadResourcesFromXmlParser(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        mIconPackResources.clear();
        do {

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            if (!parser.getName().equalsIgnoreCase("item")) {
                continue;
            }

            String component = parser.getAttributeValue(null, "component");
            String drawable = parser.getAttributeValue(null, "drawable");

            // Validate component/drawable exist
            if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                continue;
            }

            // Validate format/length of component
            if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")
                    || component.length() < 16 || drawable.length() == 0) {
                continue;
            }

            // Sanitize stored value
            component = component.substring(14, component.length() - 1).toLowerCase();

            ComponentName name = null;
            if (!component.contains("/")) {
                // Package icon reference
                name = new ComponentName(component.toLowerCase(), "");
            } else {
                name = ComponentName.unflattenFromString(component);
            }

            if (name != null) {
                mIconPackResources.put(name, drawable);
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
    }

    public void loadIconPack(String packageName) {
        String defaultIcons = mContext.getResources().getString(R.string.default_iconpack_title);
        if (packageName.equals(defaultIcons)) {
            return;
        }

        Resources res = null;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        XmlPullParser parser = null;
        InputStream inputStream = null;

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            inputStream = res.getAssets().open("appfilter.xml");
            parser = factory.newPullParser();
            parser.setInput(inputStream, "UTF-8");
        } catch (Exception e) {
            // Catch any exception since we want to fall back to parsing the xml/
            // resource in all cases
            int resId = res.getIdentifier("appfilter", "xml", packageName);
            if (resId != 0) {
                parser = res.getXml(resId);
            }
        }

        if (parser != null) {
            try {
                loadResourcesFromXmlParser(parser);
                mLoadedIconPackResource = res;
                mLoadedIconPackName = packageName;
                return;
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // Cleanup resources
                if (parser instanceof XmlResourceParser) {
                    ((XmlResourceParser) parser).close();
                } else if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        // Application uses a different theme format (most likely launcher pro)
        int arrayId = res.getIdentifier("theme_iconpack", "array", packageName);
        if (arrayId == 0) {
            arrayId = res.getIdentifier("icon_pack", "array", packageName);
            if (arrayId == 0) {
                return;
            }
        }

        String[] iconPack = res.getStringArray(arrayId);
        ComponentName compName = null;
        mIconPackResources.clear();
        for (String entry : iconPack) {

            if (TextUtils.isEmpty(entry)) {
                continue;
            }

            String icon = entry;
            entry = entry.replaceAll("_", ".");

            compName = new ComponentName(entry.toLowerCase(), "");
            mIconPackResources.put(compName, icon);

            int activityIndex = entry.lastIndexOf(".");
            if (activityIndex <= 0 || activityIndex == entry.length() - 1) {
                continue;
            }

            String iconPackage = entry.substring(0, activityIndex);
            if (TextUtils.isEmpty(iconPackage)) {
                continue;
            }

            String iconActivity = entry.substring(activityIndex + 1);
            if (TextUtils.isEmpty(iconActivity)) {
                continue;
            }

            // Store entries as lower case to ensure match
            iconPackage = iconPackage.toLowerCase();
            iconActivity = iconActivity.toLowerCase();

            iconActivity = iconPackage + "." + iconActivity;
            compName = new ComponentName(iconPackage, iconActivity);
            mIconPackResources.put(compName, icon);
        }
        mLoadedIconPackResource = res;
        mLoadedIconPackName = packageName;
    }

    public static void pickIconPack(final Context context) {
        final HashMap<CharSequence, String> supportedPackages = getSupportedPackages(context);

        final CharSequence[] dialogEntries = new CharSequence[supportedPackages.size() + 1];
        supportedPackages.keySet().toArray(dialogEntries);

        final String defaultIcons = context.getResources().getString(R.string.default_iconpack_title);
        dialogEntries[dialogEntries.length - 1] = defaultIcons;
        Arrays.sort(dialogEntries);

        String iconPack = PreferencesProvider.Interface.General.getIconPack();

        int selectedIndex = -1;
        int defaultIndex = 0;
        for (int i = 0; i < dialogEntries.length; i++) {
            CharSequence appLabel = dialogEntries[i];
            if (appLabel.equals(defaultIcons)) {
                defaultIndex = i;
            } else if (supportedPackages.get(appLabel).equals(iconPack)) {
                selectedIndex = i;
                break;
            }
        }

        // Icon pack either uninstalled or
        // user had selected default icons
        if (selectedIndex == -1) {
            selectedIndex = defaultIndex;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_pick_iconpack_title);
        builder.setSingleChoiceItems(dialogEntries, selectedIndex, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                CharSequence selectedPackage = dialogEntries[which];
                if (selectedPackage.equals(defaultIcons)) {
                    PreferencesProvider.Interface.General.setIconPack(context, "");
                } else {
                    PreferencesProvider.Interface.General.setIconPack(context, supportedPackages.get(selectedPackage));
                }
                dialog.dismiss();
            }
        });
        builder.show();
    }

    boolean isIconPackLoaded() {
        return mLoadedIconPackResource != null &&
                mLoadedIconPackName != null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", mLoadedIconPackName);
        return resId;
    }

    public Resources getIconPackResources() {
        return mLoadedIconPackResource;
    }

    public int getResourceIdForActivityIcon(ActivityInfo info) {
        ComponentName compName = new ComponentName(info.packageName.toLowerCase(), info.name.toLowerCase());
        String drawable = mIconPackResources.get(compName);
        if (drawable == null) {
            // Icon pack doesn't have an icon for the activity, fallback to package icon
            compName = new ComponentName(info.packageName.toLowerCase(), "");
            drawable = mIconPackResources.get(compName);
            if (drawable == null) {
                return 0;
            }
        }
        return getResourceIdForDrawable(drawable);
    }

}