package ch.deletescape.lawnchair.iconpack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.FastBitmapDrawable;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.pixelify.PixelIconProvider;

public class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_ITEM = "item";
    private static final String ATTR_TITLE = "title";
    private static final String ATTR_DRAWABLE = "drawable";

    private final String mIconBack;
    private final String mIconUpon;
    private final String mIconMask;
    private final float mScale;
    private final List<String> mCalendars;
    private Map<String, IconPackProvider.IconInfo> icons = new ArrayMap<>();
    private String packageName;
    private Context mContext;
    private Comparator<IconEntry> mIconComparator = new Comparator<IconEntry>() {
        @Override
        public int compare(IconEntry t1, IconEntry t2) {
            return t1.resourceName.compareTo(t2.resourceName);
        }
    };

    public IconPack(Map<String, IconPackProvider.IconInfo> icons, Context context, String packageName,
                    String iconBack, String iconUpon, String iconMask, float scale, List<String> calendars) {
        this.icons = icons;
        this.packageName = packageName;
        mContext = context;
        mIconBack = iconBack;
        mIconUpon = iconUpon;
        mIconMask = iconMask;
        mScale = scale;
        mCalendars = calendars;
    }

    public Drawable getIcon(LauncherActivityInfoCompat info) {
        IconPackProvider.IconInfo iconInfo = icons.get(info.getComponentName().toString());
        if (iconInfo != null && iconInfo.prefix != null) {
            Drawable drawable = getDrawable(iconInfo.prefix + (PixelIconProvider.dayOfMonth() + 1));
            if (drawable != null) {
                return drawable;
            }
        }
        if (iconInfo != null && iconInfo.drawable != null)
            return getDrawable(iconInfo.drawable);
        if (mIconBack != null || mIconUpon != null || mIconMask != null)
            return getMaskedDrawable(info);
        return null;
    }

    private Drawable getMaskedDrawable(LauncherActivityInfoCompat info) {
        try {
            return new CustomIconDrawable(mContext, this, info);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Resources getResources() throws PackageManager.NameNotFoundException {
        return mContext.getPackageManager().getResourcesForApplication(packageName);
    }

    private boolean iconExists(Resources res, String name) {
        return res.getIdentifier(name, "drawable", packageName) != 0;
    }

    public Drawable getDrawable(String name) {
        try {
            Resources res = getResources();
            int resourceId = res.getIdentifier(name, "drawable", packageName);
            if (0 != resourceId) {
                Bitmap b = BitmapFactory.decodeResource(res, resourceId);
                return new FastBitmapDrawable(b);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public Drawable getDrawable(int resId) {
        try {
            Resources res = getResources();
            Bitmap b = BitmapFactory.decodeResource(res, resId);
            return new FastBitmapDrawable(b);
        } catch (Exception ignored) {
        }
        return null;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getIconBack() {
        return mIconBack;
    }

    public String getIconUpon() {
        return mIconUpon;
    }

    public String getIconMask() {
        return mIconMask;
    }

    public float getScale() {
        return mScale;
    }

    public List<String> getCalendars() {
        return mCalendars;
    }

    public List<IconCategory> getIconList() {
        List<IconCategory> categoryList = new ArrayList<>();
        IconCategory allIcons = new IconCategory(mContext.getString(R.string.all_icons));
        categoryList.add(allIcons);
        IconCategory category = null;
        IconEntry entry;
        try {
            Resources res = getResources();
            XmlPullParser parser = IconPackProvider.getXml(mContext, packageName, "drawable");
            while (parser != null && parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() != XmlPullParser.START_TAG) continue;
                if (TAG_CATEGORY.equals(parser.getName())) {
                    String title = parser.getAttributeValue(null, ATTR_TITLE);
                    category = new IconCategory(resolveString(res, title));
                    categoryList.add(category);
                } else if (TAG_ITEM.equals(parser.getName())) {
                    int resId = resolveResource(res, parser.getAttributeValue(null, ATTR_DRAWABLE));
                    if (resId != 0) {
                        entry = new IconEntry(this, resId);
                        allIcons.addEntry(entry);
                        if (category != null)
                            category.addEntry(entry);
                    }
                }
            }
            allIcons.sort(mIconComparator);
            return categoryList;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private String resolveString(Resources res, String title) {
        try {
            if (!title.startsWith("@")) return title;
            title = title.substring(1);
            try {
                int resId = Integer.parseInt(title);
                return res.getString(resId);
            } catch (NumberFormatException | Resources.NotFoundException ignored2) {
                String[] parts = title.split("/");
                if (parts.length < 2) throw new IllegalStateException();
                int resId = res.getIdentifier(parts[1], parts[0], packageName);
                return res.getString(resId);
            }
        } catch (Exception ignored) {
            return title;
        }
    }

    private int resolveResource(Resources res, String name) {
        try {
            if (!name.startsWith("@"))
                throw new IllegalStateException();

            name = name.substring(1);
            try {
                return Integer.parseInt(name);
            } catch (NumberFormatException ignored) {
                String[] parts = name.split("/");
                if (parts.length < 2) throw new IllegalStateException();
                return res.getIdentifier(parts[1], parts[0], packageName);
            }
        } catch (IllegalStateException e) {
            return res.getIdentifier(name, "drawable", packageName);
        }
    }

    public static class IconCategory {

        public final String title;
        private final List<IconEntry> iconList;

        private IconCategory(String t) {
            title = t;
            iconList = new ArrayList<>();
        }

        public String getTitle() {
            return title;
        }

        public int getIconCount() {
            return iconList.size();
        }

        public IconEntry get(int position) {
            return iconList.get(position);
        }

        private void addEntry(IconEntry entry) {
            iconList.add(entry);
        }

        public void sort(Comparator<IconEntry> comparator) {
            Collections.sort(iconList, comparator);
        }
    }

    public static class IconEntry {

        private final IconPack iconPack;
        final int resId;
        final String resourceName;

        private IconEntry(IconPack ip, int id) {
            iconPack = ip;
            resId = id;
            resourceName = loadResourceName();
        }

        private String loadResourceName() {
            try {
                return iconPack.getResources().getResourceEntryName(resId);
            } catch (Exception e) {
                return "";
            }
        }

        Drawable loadDrawable() {
            return iconPack.getDrawable(resId);
        }

        public String getPackageName() {
            return iconPack.getPackageName();
        }
    }
}
