package ch.deletescape.lawnchair;

import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import java.util.Map;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

@SuppressWarnings("unused")
public class IconPackIconProvider extends IconProvider {
    @Override
    public Drawable getIcon(LauncherActivityInfoCompat info, int iconDpi) {
        IconPack iconPack = IconPackProvider.getIconPack("com.shahid.pineapple");
        if(iconPack != null){
            Drawable icon = iconPack.getIcon(info, iconDpi);
            if(icon != null){
                return icon;
            }
        }
        return super.getIcon(info, iconDpi);
    }
}
