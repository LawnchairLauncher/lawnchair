package ch.deletescape.lawnchair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

import org.xmlpull.v1.XmlPullParser;

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
        FirebaseAnalytics.getInstance(context).setUserProperty("iconpack", packageName);
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
        Trace trace = FirebasePerformance.getInstance().newTrace("iconpack_load");
        trace.start();
        clearCache(context, packageName);
        Map<String, String> appFilter;
        try {
            appFilter = parseAppFilter(getAppFilter(context, packageName));
        } catch (Exception e) {
            FirebaseCrash.report(e);
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            iconPacks.put(packageName, null);
            trace.stop();
            return;
        }
        iconPacks.put(packageName, new IconPack(appFilter, context, packageName));
        trace.stop();
        FirebaseAnalytics.getInstance(context).logEvent("iconpack_loaded", null);
    }

    private static void clearCache(Context context, String packageName) {
        Trace trace = FirebasePerformance.getInstance().newTrace("iconpack_cache_clear");
        trace.start();
        File cacheFolder = new File(context.getCacheDir(), "iconpack");
        File indicatorFile = new File(cacheFolder, packageName);
        if(cacheFolder.exists()){
            if(!indicatorFile.exists()){
                for(File file : cacheFolder.listFiles()){
                    file.delete();
                }
            }
        } else {
            cacheFolder.mkdir();
            try {
                indicatorFile.createNewFile();
            } catch (IOException e) {
                FirebaseCrash.report(e);
            }
        }
        trace.stop();
        FirebaseAnalytics.getInstance(context).logEvent("iconpack_clearcache", null);
    }

    private static Map<String, String> parseAppFilter(XmlPullParser parser) throws Exception {
        Trace trace = FirebasePerformance.getInstance().newTrace("iconpack_parse_appfilter");
        trace.start();
        Map<String, String> entries = new ArrayMap<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("item")) {
                String comp = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                if (drawable != null && comp != null) {
                    entries.put(comp, drawable);
                    trace.incrementCounter("icons");
                }
            }
        }
        trace.stop();
        return entries;
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            FirebaseCrash.report(e);
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
