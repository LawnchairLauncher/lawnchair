package ch.deletescape.lawnchair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.util.Xml;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();

    private static IconPack getIconPack(String packageName) {
        return iconPacks.get(packageName);
    }

    public static IconPack loadAndGetIconPack(Context context) {
        SharedPreferences prefs = Utilities.getPrefs(context);
        String packageName = prefs.getString("pref_iconPackPackage", "");
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
        clearCache(context, packageName);
        Map<String, String> appFilter;
        try {
            iconPacks.put(packageName, parseAppFilter(context, packageName));
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            iconPacks.put(packageName, null);
        }
    }

    private static void clearCache(Context context, String packageName) {
        File cacheFolder = new File(context.getCacheDir(), "iconpack");
        File indicatorFile = new File(cacheFolder, packageName);
        if (cacheFolder.exists()) {
            if (!indicatorFile.exists()) {
                for (File file : cacheFolder.listFiles()) {
                    file.delete();
                }
            }
        } else {
            cacheFolder.mkdir();
            try {
                indicatorFile.createNewFile();
            } catch (IOException e) {
            }
        }
    }

    private static IconPack parseAppFilter(Context context, String packageName) throws Exception {
        XmlPullParser parser = getAppFilter(context, packageName);
        float scale = 1f;
        String iconBack = null;
        String iconUpon = null;
        String iconMask = null;
        Map<String, String> entries = new ArrayMap<>();
        while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            try {
                if (name.equals("item")) {
                    String comp = parser.getAttributeValue(null, "component");
                    String drawable = parser.getAttributeValue(null, "drawable");
                    if (drawable != null && comp != null) {
                        entries.put(comp, drawable);
                    }
                } else if (name.equals("iconback")) {
                    iconBack = parser.getAttributeValue(null, "img1");
                } else if (name.equals("iconupon")) {
                    iconUpon = parser.getAttributeValue(null, "img1");
                } else if (name.equals("iconmask")) {
                    iconMask = parser.getAttributeValue(null, "img");
                } else if (name.equals("scale")) {
                    scale = Float.parseFloat(parser.getAttributeValue(null, "factor"));
                }
            } catch (Exception ignored) {

            }
        }
        return new IconPack(entries, context, packageName, iconBack, iconUpon, iconMask, scale);
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            } else {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(res.getAssets().open("appfilter.xml"), Xml.Encoding.UTF_8.toString());
                return parser;
            }
        } catch (PackageManager.NameNotFoundException | IOException | XmlPullParserException e) {
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
