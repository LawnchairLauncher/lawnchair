package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;

import ch.deletescape.lawnchair.Alarm;
import ch.deletescape.lawnchair.OnAlarmListener;

public class C0278a extends BroadcastReceiver implements OnAlarmListener {
    static long INITIAL_LOAD_TIMEOUT = 5;
    private static C0278a bt;
    private GoogleSearchApp gsa;
    private boolean bq = false;
    private final AppWidgetManagerHelper appWidgetManagerHelper;
    private final Alarm mAlarm;
    private final Context mContext;
    private final ArrayList<OnGsaListener> mListeners;

    public static C0278a aS(Context context) {
        if (bt == null) {
            bt = new C0278a(context.getApplicationContext());
        }
        return bt;
    }

    C0278a(Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
        appWidgetManagerHelper = new AppWidgetManagerHelper(mContext);
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
        onReceive(null, null);
        mAlarm.setAlarm(INITIAL_LOAD_TIMEOUT);
        aU(gsa);
        context.registerReceiver(this, Util.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED", "android.intent.action.PACKAGE_DATA_CLEARED"));
    }

    private void aU(GoogleSearchApp gsa) {
        Bundle appWidgetOptions = appWidgetManagerHelper.getAppWidgetOptions();
        if (appWidgetOptions != null) {
            GoogleSearchApp gsa2 = new GoogleSearchApp(appWidgetOptions);
            if (gsa2.mRemoteViews != null && gsa2.gsaVersion == gsa.gsaVersion && gsa2.gsaUpdateTime == gsa.gsaUpdateTime && gsa2.validity() > 0) {
                this.gsa = gsa2;
                aX();
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        gsa = new GoogleSearchApp(mContext, null);
        mContext.sendBroadcast(new Intent("com.google.android.apps.gsa.weatherwidget.ENABLE_UPDATE").setPackage("com.google.android.googlequicksearchbox"));
    }

    private void aX() {
        bq = true;
        for (OnGsaListener bb : mListeners) {
            bb.onGsa(gsa);
        }
        mAlarm.cancelAlarm();
        if (gsa.mRemoteViews != null) {
            mAlarm.setAlarm(gsa.validity());
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (gsa.mRemoteViews != null || !bq) {
            gsa = new GoogleSearchApp(mContext, null);
            aX();
        }
    }

    public GoogleSearchApp getGoogleSearchAppAndAddListener(OnGsaListener onGsaListener) {
        mListeners.add(onGsaListener);
        return bq ? gsa : null;
    }

    public void aY(OnGsaListener onGsaListener) {
        mListeners.remove(onGsaListener);
    }
}