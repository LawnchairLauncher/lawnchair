package ch.deletescape.lawnchair;

import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import java.util.Map;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

public class IconPackIconProvider extends IconProvider {
    private Map<String, IconPack> iconPacks = new ArrayMap<>();
    @Override
    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {
        IconPack iconPack = getIconPackForPackage("");
        if(iconPack != null){
            return iconPack.getIcon(info, iconDpi);
        }
        return super.getIcon(info, iconDpi);
    }

    private IconPack getIconPackForPackage(String packageName) {
        return iconPacks.get(packageName);
    }
}
