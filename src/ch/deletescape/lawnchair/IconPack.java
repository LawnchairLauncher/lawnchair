package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import java.util.Map;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    private Map<String, String> icons = new ArrayMap<>();
    private Map<String, Drawable> cache = new ArrayMap<>();
    private String packageName;
    private Context mContext;

    public IconPack(Map<String, String> icons, Context context, String packageName) {
        this.icons = icons;
        this.packageName = packageName;
        mContext = context;
    }

    public Drawable getIcon(LauncherActivityInfoCompat info) {
        return getIcon(info.getComponentName());
    }

    public Drawable getIcon(ActivityInfo info) {
        return getIcon(new ComponentName(info.packageName, info.name));
    }

    public Drawable getIcon(ComponentName name) {
        return getDrawable(icons.get(name.toString()));
    }

    private Drawable getDrawable(String name) {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }
        Resources res;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier(name, "drawable", packageName);
            if (0 != resourceId) {
                Drawable drawable = mContext.getPackageManager().getDrawable(packageName, resourceId, null);
                cache.put(name, drawable);
                return drawable;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
