package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.Log;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private Map<String, Drawable> memoryCache = new ArrayMap<>();
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
        Trace trace = FirebasePerformance.getInstance().newTrace("iconpack_getdrawable");
        trace.start();
        if (memoryCache.containsKey(name)) {
            trace.incrementCounter("memorycache");
            Drawable d = memoryCache.get(name);
            trace.stop();
            return d;
        }
        File cachePath = new File(mContext.getCacheDir(), "iconpack/" + name);
        if(cachePath.exists()){
            Bitmap b = BitmapFactory.decodeFile(cachePath.toString());
            if(b != null) {
                Drawable d = new FastBitmapDrawable(b);
                memoryCache.put(name, d);
                trace.incrementCounter("filecache");
                trace.stop();
                return d;
            }
        }
        Resources res;
        try {
            res = mContext.getPackageManager().getResourcesForApplication(packageName);
            int resourceId = res.getIdentifier(name, "drawable", packageName);
            if (0 != resourceId) {
                Bitmap b = BitmapFactory.decodeResource(res, resourceId);
                saveBitmapToFile(cachePath, b);
                Drawable drawable = new FastBitmapDrawable(b);
                memoryCache.put(name, drawable);
                trace.incrementCounter("nocache");
                trace.stop();
                return drawable;
            }
        } catch (Exception ignored) {
        }
        trace.stop();
        return null;
    }
    private boolean saveBitmapToFile(File imageFile, Bitmap bm) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(imageFile);

            bm.compress(Bitmap.CompressFormat.PNG,100,fos);

            fos.close();

            return true;
        }
        catch (IOException e) {
            FirebaseCrash.report(e);
            Log.e("IconPack",e.getMessage());
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    FirebaseCrash.report(e1);
                }
            }
        }
        return false;
    }
}
