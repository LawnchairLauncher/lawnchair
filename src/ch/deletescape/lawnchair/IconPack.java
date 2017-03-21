package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;
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
    private Map<String, Drawable> icons = new ArrayMap<>();

    public IconPack(Map<String, Drawable> icons){
        this.icons = icons;
    }

    public Drawable getIcon(LauncherActivityInfoCompat info) {
        return icons.get(info.getComponentName().toString());
    }

    public Drawable getIcon(ActivityInfo info) {
        return icons.get(new ComponentName(info.packageName, info.name).toString());
    }

    public Drawable getIcon(ComponentName name) {
        return icons.get(name.toString());
    }
}
