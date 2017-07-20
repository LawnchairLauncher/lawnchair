package ch.deletescape.lawnchair.iconpack;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.deletescape.lawnchair.FastBitmapDrawable;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.pixelify.PixelIconProvider;

public class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    private final String mIconBack;
    private final String mIconUpon;
    private final String mIconMask;
    private final float mScale;
    private final List<String> mCalendars;
    private Map<String, IconPackProvider.IconInfo> icons = new ArrayMap<>();
    private String packageName;
    private Context mContext;

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

    public List<IconEntry> getIconList() {
        Map<String, IconEntry> iconMap = new HashMap<>();
        try {
            Resources res = getResources();

            for (Map.Entry<String, IconPackProvider.IconInfo> entry : icons.entrySet()) {
                IconPackProvider.IconInfo iconInfo = entry.getValue();
                if (iconInfo.drawable != null) {
                    if (iconExists(res, iconInfo.drawable))
                        iconMap.put(iconInfo.drawable, new IconEntry(this, iconInfo.drawable));
                } else if (iconInfo.prefix != null) {
                    for (int i = 1; i <= 31; i++) {
                        String resourceName = iconInfo.prefix + i;
                        if (iconExists(res, resourceName))
                            iconMap.put(resourceName, new IconEntry(this, resourceName));
                    }
                }
            }

            List<IconEntry> iconList = new ArrayList<>();
            for (Map.Entry<String, IconEntry> entry : iconMap.entrySet()) {
                iconList.add(entry.getValue());
            }

            Collections.sort(iconList, new Comparator<IconEntry>() {
                @Override
                public int compare(IconEntry t1, IconEntry t2) {
                    return t1.resourceName.compareTo(t2.resourceName);
                }
            });

            return iconList;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return Collections.EMPTY_LIST;
    }

    public static class IconEntry {

        private final IconPack iconPack;
        public final String resourceName;

        private IconEntry(IconPack ip, String n) {
            iconPack = ip;
            resourceName = n;
        }

        public Drawable loadDrawable() {
            return iconPack.getDrawable(resourceName);
        }

        public String getPackageName() {
            return iconPack.getPackageName();
        }
    }
}
