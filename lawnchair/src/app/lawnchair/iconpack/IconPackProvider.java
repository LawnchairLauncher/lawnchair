package app.lawnchair.iconpack;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.lawnchair.preferences.PreferenceManager;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();
    public static final String ICON_MASK_TAG = "iconmask";
    public static final String ICON_BACK_TAG = "iconback";
    public static final String ICON_UPON_TAG = "iconupon";
    public static final String ICON_SCALE_TAG = "scale";

    public static IconPack getIconPack(String packageName){
        return iconPacks.get(packageName);
    }

    public static int getIconPackLength(String packageName) {
        return iconPacks.get(packageName).getTotalIcons();
    }

    public static IconPack loadAndGetIconPack(Context context) {
        PreferenceManager prefs = PreferenceManager.getInstance(context);
        String packageName = prefs.getIconPackPackage().get();
        if("".equals(packageName)){
            return null;
        }
        if(!iconPacks.containsKey(packageName)){
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    public static void loadIconPack(Context context, String packageName) {
        if("".equals(packageName)){
            iconPacks.put("", null);
        }
        try {
            XmlPullParser appFilter = getAppFilter(context, packageName);
            if (appFilter != null) {
                IconPack pack = new IconPack(context, packageName);
                parseAppFilter(packageName, appFilter, pack);
                iconPacks.put(packageName, pack);
            }
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private static void parseAppFilter(String packageName, XmlPullParser parser, IconPack pack) throws Exception {
        Map<String, String> iconPackResources = new HashMap<String, String>();
        List<String> iconBackStrings = new ArrayList<String>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("item")) {
                String component = parser.getAttributeValue(null, "component");
                String drawable = parser.getAttributeValue(null, "drawable");
                // Validate component/drawable exist

                if (TextUtils.isEmpty(component) || TextUtils.isEmpty(drawable)) {
                    continue;
                }

                // Validate format/length of component
                if (!component.startsWith("ComponentInfo{") || !component.endsWith("}")
                        || component.length() < 16) {
                    continue;
                }

                // Sanitize stored value
                component = component.substring(14, component.length() - 1);

                if (!component.contains("/")) {
                    // Package icon reference
                    iconPackResources.put(component, drawable);
                } else {
                    ComponentName componentName = ComponentName.unflattenFromString(component);
                    if (componentName != null) {
                        iconPackResources.put(componentName.getPackageName(), drawable);
                        iconPackResources.put(component, drawable);
                    }
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_BACK_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        iconBackStrings.add(parser.getAttributeValue(i));
                    }
                }
                continue;
            }

            if (name.equalsIgnoreCase(ICON_MASK_TAG) ||
                    name.equalsIgnoreCase(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() > 0) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (name.equalsIgnoreCase(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() > 0) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                iconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }
        }
        pack.setIcons(iconPackResources, iconBackStrings);
    }

    private static XmlPullParser getAppFilter(Context context, String packageName) {
        try {
            Resources res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier("appfilter", "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            }
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }
}
