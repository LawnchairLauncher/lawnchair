package com.android.launcher3;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.launcher3.util.Preconditions;

import java.util.ArrayList;

public class SuperWeatherListener extends BroadcastReceiver implements OnAlarmListener { //e
    static long INITIAL_LOAD_TIMEOUT = 5000L;
    private static SuperWeatherListener instance; //bt
    private SuperGoogleSearchApp gsa;
    private boolean bq = false;
    private final SuperAppWidgetManagerHelper appWidgetManagerHelper;
    private final Alarm mAlarm;
    private final Context mContext;
    private final ArrayList<SuperOnGsaListener> mListeners;

    public static SuperWeatherListener aS(Context context) {
        if (instance == null) {
            instance = new SuperWeatherListener(context.getApplicationContext());
        }
        return instance;
    }

    SuperWeatherListener(Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
        appWidgetManagerHelper = new SuperAppWidgetManagerHelper(mContext);
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
        onReceive(null, null);
        mAlarm.setAlarm(INITIAL_LOAD_TIMEOUT);
        aU(gsa);
        context.registerReceiver(this, SuperUtil.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED", "android.intent.action.PACKAGE_DATA_CLEARED"));
    }

    private void aU(SuperGoogleSearchApp gsa) {
        Bundle appWidgetOptions = appWidgetManagerHelper.getAppWidgetOptions();
        if (appWidgetOptions != null) {
            SuperGoogleSearchApp gsa2 = new SuperGoogleSearchApp(appWidgetOptions);
            if (gsa2.mRemoteViews != null && gsa2.gsaVersion == gsa.gsaVersion && gsa2.gsaUpdateTime == gsa.gsaUpdateTime && gsa2.validity() > 0) {
                this.gsa = gsa2;
                aX();
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("SuperWeatherListener", "onReceive");
        gsa = new SuperGoogleSearchApp(mContext, null);
        mContext.sendBroadcast(new Intent("com.google.android.apps.gsa.weatherwidget.ENABLE_UPDATE").setPackage("com.google.android.googlequicksearchbox"));
    }

    private void aX() { //bL
        bq = true;
        for (SuperOnGsaListener bb : mListeners) {
            bb.onGsa(gsa.mRemoteViews);
        }
        mAlarm.cancelAlarm();
        if (gsa.mRemoteViews != null) {
            mAlarm.setAlarm(gsa.validity());
        }
    }

    public static SuperWeatherListener getInstance(final Context context) { //bH
        Preconditions.assertUIThread();
        if (SuperWeatherListener.instance == null) {
            SuperWeatherListener.instance = new SuperWeatherListener(context.getApplicationContext());
        }
        return SuperWeatherListener.instance;
    }

    public void bH(final RemoteViews remoteViews) {
        if (remoteViews != null) {
            this.gsa = new SuperGoogleSearchApp(this.mContext, remoteViews);
            this.aX();
        }
    }


    @Override
    public void onAlarm(Alarm alarm) {
        if (gsa.mRemoteViews != null || !bq) {
            gsa = new SuperGoogleSearchApp(mContext, null);
            aX();
        }
    }

    public SuperGoogleSearchApp getGoogleSearchAppAndAddListener(SuperOnGsaListener onGsaListener) {
        mListeners.add(onGsaListener);
        return bq ? gsa : null;
    }

    public void removeListener(SuperOnGsaListener onGsaListener) {
        mListeners.remove(onGsaListener);
    }
}
