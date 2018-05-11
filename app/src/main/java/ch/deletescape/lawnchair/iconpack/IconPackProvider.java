package ch.deletescape.lawnchair.iconpack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.util.Xml;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.Utilities;

public class IconPackProvider {
    private static Map<String, IconPack> iconPacks = new ArrayMap<>();

    private static IconPack getIconPack(String packageName) {
        return iconPacks.get(packageName);
    }

    public static IconPack loadAndGetIconPack(Context context) {
        String packageName = Utilities.getPrefs(context).getIconPackPackage();
        return loadAndGetIconPack(context, packageName);
    }

    public static IconPack loadAndGetIconPack(Context context, String packageName) {
        if ("".equals(packageName)) {
            return null;
        }
        if (!iconPacks.containsKey(packageName)) {
            loadIconPack(context, packageName);
        }
        return getIconPack(packageName);
    }

    private static void loadIconPack(Context context, String packageName) {
        if ("".equals(packageName)) {
            iconPacks.put("", null);
        }
        try {
            iconPacks.put(packageName, parseAppFilter(context, packageName));
        } catch (Exception e) {
            Toast.makeText(context, "Invalid IconPack", Toast.LENGTH_SHORT).show();
            iconPacks.put(packageName, null);
        }
    }

    private static IconPack parseAppFilter(Context context, String packageName) throws Exception {
        XmlPullParser parser = getXml(context, packageName, "appfilter");
        float scale = 1f;
        String iconBack = null;
        String iconUpon = null;
        String iconMask = null;
        Map<String, IconInfo> entries = new ArrayMap<>();
        List<String> calendars = new ArrayList<>();
        while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            try {
                switch (name) {
                    case "item": {
                        String comp = parser.getAttributeValue(null, "component");
                        String drawable = parser.getAttributeValue(null, "drawable");
                        if (drawable != null && comp != null) {
                            IconInfo iconInfo;
                            if (!entries.containsKey(comp)) {
                                iconInfo = new IconInfo();
                                entries.put(comp, iconInfo);
                            } else {
                                iconInfo = entries.get(comp);
                            }
                            iconInfo.drawable = drawable;
                        }
                        break;
                    }
                    case "iconback":
                        iconBack = getImg(parser);
                        break;
                    case "iconupon":
                        iconUpon = getImg(parser);
                        break;
                    case "iconmask":
                        iconMask = getImg(parser);
                        break;
                    case "scale":
                        scale = Float.parseFloat(parser.getAttributeValue(null, "factor"));
                        break;
                    case "calendar": {
                        String comp = parser.getAttributeValue(null, "component");
                        String prefix = parser.getAttributeValue(null, "prefix");
                        if (prefix != null && comp != null) {
                            IconInfo iconInfo;
                            if (!entries.containsKey(comp)) {
                                iconInfo = new IconInfo();
                                entries.put(comp, iconInfo);
                            } else {
                                iconInfo = entries.get(comp);
                            }
                            iconInfo.prefix = prefix;
                            try {
                                String calendar = comp.split("/")[0].split("\\{")[1];
                                calendars.add(calendar);
                            } catch (Exception ignored) {

                            }
                        }
                        break;
                    }
                }
            } catch (Exception ignored) {

            }
        }
        return new IconPack(entries, context, packageName, iconBack, iconUpon, iconMask, scale, calendars);
    }

    private static String getImg(XmlPullParser parser) {
        String img = parser.getAttributeValue(null, "img");
        if (img == null)
            img = parser.getAttributeValue(null, "img0");
        if (img == null)
            img = parser.getAttributeValue(null, "img1");
        if (img == null)
            img = parser.getAttributeValue(null, "img2");
        if (img == null)
            img = parser.getAttributeValue(null, "img3");
        return img;
    }

    static XmlPullParser getXml(Context context, String packageName, String name) {
        Resources res;
        try {
            res = context.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier(name, "xml", packageName);
            if (0 != resourceId) {
                return context.getPackageManager().getXml(packageName, resourceId, null);
            } else {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                XmlPullParser parser = factory.newPullParser();
                parser.setInput(res.getAssets().open(name + ".xml"), Xml.Encoding.UTF_8.toString());
                return parser;
            }
        } catch (PackageManager.NameNotFoundException | IOException | XmlPullParserException e) {
            Toast.makeText(context, "Failed to get AppFilter", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    public static class IconInfo {

        public String drawable;
        public String prefix;
    }
}
