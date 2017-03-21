package ch.deletescape.lawnchair;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        Map<String, String> appFilter;
        try {
            appFilter = parseAppFilter(getAppFilter(context, packageName));
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Drawable> icP = new ArrayMap<>();
        for (Map.Entry<String, String> entry : appFilter.entrySet()) {
            String comp = entry.getKey();
            Drawable drawable = getDrawable(context, packageName, entry.getValue());
            icP.put(comp, drawable);
        }
        iconPacks.put(packageName, new IconPack(icP));
    }

    private static Map<String, String> parseAppFilter(XmlPullParser parser) throws Exception {
        Map<String, String> entries = new ArrayMap<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("item")) {
                String comp = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                if (drawable != null && comp != null) {
                    entries.put(comp, drawable);
                }
            }
        }
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
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    private static Drawable getDrawable(Context context, String packageName, String name) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier(name, "drawable", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getDrawable(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, "Failed to get drawable", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
