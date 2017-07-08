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
    private final String mIconBack;
    private final String mIconUpon;
    private final String mIconMask;
    private final float mScale;
    private Map<String, String> icons = new ArrayMap<>();
    private String packageName;
    private Context mContext;
    private FirebaseAnalytics mFirebaseAnalytics;

    public IconPack(Map<String, String> icons, Context context, String packageName,
                    String iconBack, String iconUpon, String iconMask, float scale) {
        this.icons = icons;
        this.packageName = packageName;
        mContext = context;
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        mIconBack = iconBack;
        mIconUpon = iconUpon;
        mIconMask = iconMask;
        mScale = scale;
    }

    public Drawable getIcon(LauncherActivityInfoCompat info) {
        mFirebaseAnalytics.logEvent("iconpack_icon_get", null);
        String iconName = icons.get(info.getComponentName().toString());
        if (iconName != null)
            return getDrawable(iconName);
        else if (mIconBack != null || mIconUpon != null || mIconMask != null || mScale != 1f)
            return getMaskedDrawable(info);
        return null;
    }

    private Drawable getMaskedDrawable(LauncherActivityInfoCompat info) {
        try {
            return new CustomIconDrawable(mContext, this, info);
        } catch (Exception ignored) {
            return null;
        }
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
}
