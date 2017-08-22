package com.android.launcher3.pixel;

import android.content.Intent;
import java.util.Calendar;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.LauncherAppState;
import android.graphics.drawable.Drawable;
import android.content.res.Resources;
import android.os.Handler;
import com.android.launcher3.LauncherModel;
import android.content.Context;
import android.graphics.drawable.LayerDrawable;
import android.content.ComponentName;
import android.content.BroadcastReceiver;

public class ClockUpdateReceiver extends BroadcastReceiver
{
    private static final Object LOCK;
    public static final ComponentName dl;
    private static ClockUpdateReceiver dp;
    LayerDrawable layerDrawable;
    private int dn;
    private int do2;
    private int dq;
    private final Context mContext;
    private float mScale;

    static {
        LOCK = new Object();
        dl = new ComponentName("com.google.android.deskclock", "com.android.deskclock.DeskClock");
    }

    private ClockUpdateReceiver(final Context mContext) {
        this.mContext = mContext;
        final Handler handler = new Handler(LauncherModel.getWorkerLooper());
        mContext.registerReceiver(this, cC("com.google.android.deskclock", "android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED"), null, handler);
        handler.post(new SomeRunnable(this));
    }

    public static IntentFilter cC(final String s, final String... array) {
        final IntentFilter intentFilter = new IntentFilter();
        for (int length = array.length, i = 0; i < length; ++i) {
            intentFilter.addAction(array[i]);
        }
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart(s, 0);
        return intentFilter;
    }

    private static Drawable cL(final Context context, final Resources resources, final int n) {
        if (n != 0) {
            return resources.getDrawableForDensity(n, LauncherAppState.getInstance(context).getInvariantDeviceProfile().fillResIconDpi);
        }
        return null;
    }

    private void cP() {
        try {
            final Bundle metaData = mContext.getPackageManager().getApplicationInfo("com.google.android.deskclock", PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES).metaData;
            if (metaData != null) {
                final Resources resourcesForApplication = mContext.getPackageManager().getResourcesForApplication("com.google.android.deskclock");
                final Drawable cl = cL(mContext, resourcesForApplication, metaData.getInt("com.google.android.apps.nexuslauncher.LEVEL_PER_TICK_ICON", 0));
                this.layerDrawable = (LayerDrawable)cl;
                this.dn = metaData.getInt("com.google.android.apps.nexuslauncher.HOUR_LAYER_INDEX", -1);
                this.do2 = metaData.getInt("com.google.android.apps.nexuslauncher.MINUTE_LAYER_INDEX", -1);
                this.dq = metaData.getInt("com.google.android.apps.nexuslauncher.SECOND_LAYER_INDEX", -1);
                if (this.layerDrawable != null) {
                    this.mScale = IconNormalizer.getInstance(mContext).getScale(this.layerDrawable, null, null, null);
                }
            }
        }
        catch (Exception ex) {}
    }

    public static ClockUpdateReceiver getInstance(final Context context) {
        synchronized (ClockUpdateReceiver.LOCK) {
            if (ClockUpdateReceiver.dp == null) {
                ClockUpdateReceiver.dp = new ClockUpdateReceiver(context.getApplicationContext());
            }
            return ClockUpdateReceiver.dp;
        }
    }

    public Drawable cM(final Calendar calendar) {
        final int n3 = -1;
        if (this.dn != n3) {
            this.layerDrawable.getDrawable(this.dn).setLevel(calendar.get(Calendar.HOUR) * 60 + calendar.get(Calendar.MINUTE));
        }
        if (this.do2 != n3) {
            this.layerDrawable.getDrawable(this.do2).setLevel(calendar.get(Calendar.HOUR) * 60 + calendar.get(Calendar.MINUTE));
        }
        if (this.dq != n3) {
            this.layerDrawable.getDrawable(this.dq).setLevel(calendar.get(Calendar.SECOND) * 10 + calendar.get(Calendar.MILLISECOND) / 100);
        }
        return this.layerDrawable;
    }

    public float cN() {
        return this.mScale;
    }

    public boolean cO() {
        return this.layerDrawable != null;
    }

    public void onReceive(final Context context, final Intent intent) {
        this.cP();
    }

    final class SomeRunnable implements Runnable
    {
        final /* synthetic */ ClockUpdateReceiver dt;

        SomeRunnable(final ClockUpdateReceiver dt) {
            this.dt = dt;
        }

        public void run() {
            this.dt.cP();
        }
    }
}