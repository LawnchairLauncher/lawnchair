package app.lawnchair.iconpack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.util.Map;

import app.lawnchair.preferences.PreferenceManager;

public class IconPackProvider {
    private static final Map<String, IconPack> iconPacks = new ArrayMap<>();

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static int getIconPackLength(String packageName) {
        return iconPacks.get(packageName).getTotalIcons();
    }

    public static IconPack loadAndGetIconPack(Context context) {
        PreferenceManager prefs = PreferenceManager.getInstance(context);
        String packageName = prefs.getIconPackPackage().get();
        if ("".equals(packageName)) {
            return null;
        }
        if (!iconPacks.containsKey(packageName)) {
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        if ("".equals(packageName)) {
            iconPacks.put("", null);
        }
        try {
            XmlPullParser appFilter = getAppFilter(context, packageName);
            if (appFilter != null) {
                IconPack pack = new IconPack(context, packageName);
                pack.parseAppFilter(packageName, appFilter);
                iconPacks.put(packageName, pack);
            }
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
        }
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("IconPackProvider", "Failed to get AppFilter", e);
        }
        return null;
    }
}
