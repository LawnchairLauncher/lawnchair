package com.android.launcher3.pixel;

import android.content.Intent;
import java.util.Calendar;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;

import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.LauncherAppState;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.os.Handler;
import com.android.launcher3.LauncherModel;
import android.content.Context;
import android.content.ComponentName;
import android.content.BroadcastReceiver;

public class ClockUpdateReceiver extends BroadcastReceiver
{
    private static Object LOCK;
    public static ComponentName componentName;
    private static ClockUpdateReceiver dp;
    private LayerDrawable layerDrawable;
    private int hourResource;
    private int minuteResource;
    private int secondResource;
    private final Context mContext;
    private float mScale;

    static {
        LOCK = new Object();
        componentName = new ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock");
    }

    private ClockUpdateReceiver(Context mContext) {
        this.mContext = mContext;
        final Handler handler = new Handler(LauncherModel.getWorkerLooper());
        mContext.registerReceiver(this, createIntentFilter("com.google.android.deskclock", "android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED"), null, handler);
        handler.post(new SomeRunnable(this));
    }

    public static IntentFilter createIntentFilter(String s, String... array) {
        IntentFilter intentFilter = new IntentFilter();
        for (int length = array.length, i = 0; i < length; ++i) {
            intentFilter.addAction(array[i]);
        }
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart(s, 0);
        return intentFilter;
    }

    private static Drawable cL(Context context, Resources resources, int resId) {
        if (resId != 0) {
            return resources.getDrawableForDensity(resId, LauncherAppState.getInstance(context).getInvariantDeviceProfile().fillResIconDpi);
        }
        return null;
    }

    private void loadResourceIds() {
        try {
            final Bundle metaData = mContext.getPackageManager().getApplicationInfo("com.google.android.deskclock", PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES).metaData;
            if (metaData != null) {
                Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication("com.google.android.deskclock");
                int bitmapResource = metaData.getInt("com.google.android.apps.nexuslauncher.LEVEL_PER_TICK_ICON", 0);
                this.layerDrawable = (LayerDrawable)cL(mContext, resourcesForApplication, bitmapResource);
                this.hourResource = metaData.getInt("com.google.android.apps.nexuslauncher.HOUR_LAYER_INDEX", -1);
                this.minuteResource = metaData.getInt("com.google.android.apps.nexuslauncher.MINUTE_LAYER_INDEX", -1);
                this.secondResource = metaData.getInt("com.google.android.apps.nexuslauncher.SECOND_LAYER_INDEX", -1);
                if (this.layerDrawable != null) {
                    this.mScale = IconNormalizer.getInstance(mContext).getScale(this.layerDrawable, null, null, null);
                }
            }
        }
        catch (Exception ex) { } //will crash, LEVEL_PER_TICK_ICON not available
    }

    public static ClockUpdateReceiver getInstance(final Context context) {
        synchronized (ClockUpdateReceiver.LOCK) {
            if (ClockUpdateReceiver.dp == null) {
                ClockUpdateReceiver.dp = new ClockUpdateReceiver(context.getApplicationContext());
            }
            return ClockUpdateReceiver.dp;
        }
    }

    public Drawable updateTime(final Calendar calendar) {
        if (this.hourResource != -1) {
            this.layerDrawable.getDrawable(this.hourResource).setLevel(calendar.get(Calendar.HOUR) * 60 + calendar.get(Calendar.MINUTE));
        }
        if (this.minuteResource != -1) {
            this.layerDrawable.getDrawable(this.minuteResource).setLevel(calendar.get(Calendar.HOUR) * 60 + calendar.get(Calendar.MINUTE));
        }
        if (this.secondResource != -1) {
            this.layerDrawable.getDrawable(this.secondResource).setLevel(calendar.get(Calendar.SECOND) * 10 + calendar.get(Calendar.MILLISECOND) / 100);
        }
        return this.layerDrawable;
    }

    public float getScale() {
        return this.mScale;
    }

    public boolean bitmapAvailable() {
        return this.layerDrawable != null;
    }

    public void onReceive(final Context context, final Intent intent) {
        this.loadResourceIds();
    }

    class SomeRunnable implements Runnable
    {
        private ClockUpdateReceiver updateReceiver;

        SomeRunnable(final ClockUpdateReceiver dt) {
            this.updateReceiver = dt;
        }

        public void run() {
            this.updateReceiver.loadResourceIds();
        }
    }
}