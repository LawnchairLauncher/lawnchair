package app.lawnchair.iconpack;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.lawnchair.preferences.PreferenceManager;

public class IconPack {
    /*
    Useful Links:
    https://github.com/teslacoil/Example_NovaTheme
    http://stackoverflow.com/questions/7205415/getting-resources-of-another-application
    http://stackoverflow.com/questions/3890012/how-to-access-string-resource-from-another-application
     */
    public static final String ICON_MASK_TAG = "iconmask";
    public static final String ICON_BACK_TAG = "iconback";
    public static final String ICON_UPON_TAG = "iconupon";
    public static final String ICON_SCALE_TAG = "scale";

    private String packageName;
    private Context mContext;
    private Map<String, String> mIconPackResources = new HashMap<>();
    private List<String> mIconBackStrings;
    private List<Drawable> mIconBackList;
    private Drawable mIconUpon, mIconMask;
    private Resources mLoadedIconPackResource;
    private float mIconScale;

    public IconPack(Context context, String packageName) {
        this.packageName = packageName;
        mContext = context;
    }

    void parseAppFilter(String packageName, XmlPullParser parser) throws Exception {
        List<String> iconBackStrings = new ArrayList<String>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName().toLowerCase();
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

                Map<String, String> iconPackResources = mIconPackResources;
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

            if (name.equals(ICON_BACK_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        iconBackStrings.add(parser.getAttributeValue(i));
                    }
                }
                continue;
            }

            if (name.equals(ICON_MASK_TAG) ||
                    name.equals(ICON_UPON_TAG)) {
                String icon = parser.getAttributeValue(null, "img");
                if (icon == null) {
                    if (parser.getAttributeCount() > 0) {
                        icon = parser.getAttributeValue(0);
                    }
                }
                mIconPackResources.put(parser.getName().toLowerCase(), icon);
                continue;
            }

            if (name.equals(ICON_SCALE_TAG)) {
                String factor = parser.getAttributeValue(null, "factor");
                if (factor == null) {
                    if (parser.getAttributeCount() > 0) {
                        factor = parser.getAttributeValue(0);
                    }
                }
                mIconPackResources.put(parser.getName().toLowerCase(), factor);
                continue;
            }
        }
        setIcons(mIconPackResources, iconBackStrings);
    }

    public void setIcons(Map<String, String> iconPackResources, List<String> iconBackStrings) {
        mIconPackResources = iconPackResources;
        mIconBackStrings = iconBackStrings;
        mIconBackList = new ArrayList<>();
        try {
            mLoadedIconPackResource = mContext.getPackageManager().getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            // must never happen cause itys checked already in the provider
            return;
        }
        mIconMask = getDrawableForName(ICON_MASK_TAG);
        mIconUpon = getDrawableForName(ICON_UPON_TAG);
        for (int i = 0; i < mIconBackStrings.size(); i++) {
            String backIconString = mIconBackStrings.get(i);
            Drawable backIcon = getDrawableWithName(backIconString);
            if (backIcon != null) {
                mIconBackList.add(backIcon);
            }
        }
        String scale = mIconPackResources.get(ICON_SCALE_TAG);
        if (scale != null) {
            try {
                mIconScale = Float.parseFloat(scale);
            } catch (NumberFormatException e) {
            }
        }
    }

    public Drawable getIcon(LauncherActivityInfo info, Drawable appIcon) {
        return getIcon(info.getComponentName(), appIcon);
    }

    public Drawable getIcon(ActivityInfo info, Drawable appIcon) {
        return getIcon(new ComponentName(info.packageName, info.name), appIcon);
    }

    public Drawable getIcon(ComponentName name, Drawable appIcon) {
        return getDrawable(name.flattenToString(), appIcon);
    }

    public Drawable getIcon(String packageName, Drawable appIcon) {
        return getDrawable(packageName, appIcon);
    }

    private static Bitmap pad(Bitmap src) {
        int dpi = (int) Resources.getSystem().getDisplayMetrics().density;
        int newSrcSize = 192;
        int retSize = newSrcSize + 200;
        int cOffsetTopLeft = (retSize - newSrcSize) / 2;

        Bitmap newSrc = Bitmap.createScaledBitmap(src, newSrcSize, newSrcSize, true);
        newSrc.setDensity(dpi);

        Bitmap ret = Bitmap.createBitmap(retSize, retSize, Bitmap.Config.ARGB_8888);
        ret.setDensity(dpi);

        Canvas c = new Canvas(ret);
        c.drawBitmap(newSrc, cOffsetTopLeft, cOffsetTopLeft, null);

        return ret;
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int width = drawable.getIntrinsicWidth();
        width = width > 0 ? width : 1;
        int height = drawable.getIntrinsicHeight();
        height = height > 0 ? height : 1;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private Drawable getDrawable(String name, Drawable appIcon) {
        Drawable d = getDrawableForName(name);
        if (d == null && appIcon != null) {
            d = appIcon;
        }
        return wrapAdaptiveIcon(d, mContext);
    }

    public static Drawable wrapAdaptiveIcon(Drawable d, Context context) {
        PreferenceManager prefs = PreferenceManager.getInstance(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !(d instanceof AdaptiveIconDrawable)
                && prefs.getWrapAdaptiveIcons().get()) {
            assert d != null;
            Bitmap b = drawableToBitmap(d);
            // Already running on UI_HELPER, no need to async this.
            Palette p = (new Palette.Builder(b)).generate();
            ColorDrawable backgroundColor = new ColorDrawable(makeBackgroundColor(p.getDominantColor(Color.WHITE), prefs));
            d = new AdaptiveIconDrawable(backgroundColor, new BitmapDrawable(pad(b)));
        }
        return d;
    }

    private static @ColorInt int makeBackgroundColor(@ColorInt int dominantColor, PreferenceManager prefs) {
        float lightness = prefs.getColoredBackgroundLightness().get();
        if (dominantColor != Color.WHITE) {
            float[] outHsl = new float[]{0F, 0F, 0F};
            ColorUtils.colorToHSL(dominantColor, outHsl);
            outHsl[2] = lightness;
            return ColorUtils.HSLToColor(outHsl);
        }
        return dominantColor;
    }

    private Drawable getIconBackFor(CharSequence tag) {
        if (mIconBackList != null && mIconBackList.size() != 0) {
            if (mIconBackList.size() == 1) {
                return mIconBackList.get(0);
            }
            try {
                Drawable back = mIconBackList.get((tag.hashCode() & 0x7fffffff) % mIconBackList.size());
                return back;
            } catch (ArrayIndexOutOfBoundsException e) {
                return mIconBackList.get(0);
            }
        }
        return null;
    }

    private int getResourceIdForDrawable(String resource) {
        int resId = mLoadedIconPackResource.getIdentifier(resource, "drawable", packageName);
        return resId;
    }

    private Drawable getDrawableForName(String name) {
        String item = mIconPackResources.get(name);
        if (!TextUtils.isEmpty(item)) {
            int id = getResourceIdForDrawable(item);
            if (id != 0) {
                return mLoadedIconPackResource.getDrawable(id);
            }
        }
        return null;
    }

    private Drawable getDrawableWithName(String name) {
        int id = getResourceIdForDrawable(name);
        if (id != 0) {
            return mLoadedIconPackResource.getDrawable(id);
        }
        return null;
    }

    private BitmapDrawable getBitmapDrawable(Drawable image) {
        if (image instanceof BitmapDrawable) {
            return (BitmapDrawable) image;
        }
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,
                Paint.FILTER_BITMAP_FLAG));

        Bitmap bmResult = Bitmap.createBitmap(image.getIntrinsicWidth(), image.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmResult);
        image.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        image.draw(canvas);
        return new BitmapDrawable(mLoadedIconPackResource, bmResult);
    }

    public int getTotalIcons() {
        return mIconBackStrings.size();
    }
}
