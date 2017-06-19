package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Map;

import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;

public class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    private Map<String, String> icons = new ArrayMap<>();
    private String packageName;
    private Context mContext;
    private FirebaseAnalytics mFirebaseAnalytics;


    public IconPack(Map<String, String> icons, Context context, String packageName) {
        this.icons = icons;
        this.packageName = packageName;
        mContext = context;
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
    }


    public Drawable getIcon(LauncherActivityInfoCompat info) {
        return getIcon(info.getComponentName());
    }

    public Drawable getIcon(ActivityInfo info) {
        return getIcon(new ComponentName(info.packageName, info.name));
    }

    public Drawable getIcon(ComponentName name) {
        mFirebaseAnalytics.logEvent("iconpack_icon_get", null);
        return getDrawable(icons.get(name.toString()));
    }

    private Drawable getDrawable(String name) {
        Resources res;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier(name, "drawable", packageName);
            if (0 != resourceId) {
                Bitmap b = BitmapFactory.decodeResource(res, resourceId);
                return new FastBitmapDrawable(b);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
