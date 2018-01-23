package com.google.android.apps.nexuslauncher.smartspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.android.launcher3.Alarm;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.OnAlarmListener;
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter;
import com.google.android.apps.nexuslauncher.smartspace.nano.SmartspaceProto.i;
import com.google.android.apps.nexuslauncher.utils.ProtoStore;

import java.io.PrintWriter;
import java.util.List;

public class SmartspaceController implements Handler.Callback {
    enum Store {
        WEATHER("smartspace_weather"),
        CURRENT("smartspace_current");

        final String filename;

        Store(final String filename) {
            this.filename = filename;
        }
    }

    private static SmartspaceController dU;
    private final SmartspaceDataContainer dQ;
    private final Alarm dR;
    private ISmartspace dS;
    private final ProtoStore dT;
    private final Context mAppContext;
    private final Handler mUiHandler;
    private final Handler mWorker;

    public SmartspaceController(final Context mAppContext) {
        this.mWorker = new Handler(LauncherModel.getWorkerLooper(), this);
        this.mUiHandler = new Handler(Looper.getMainLooper(), this);
        this.mAppContext = mAppContext;
        this.dQ = new SmartspaceDataContainer();
        this.dT = new ProtoStore(mAppContext);
        (this.dR = new Alarm()).setOnAlarmListener(new OnAlarmListener() {
            @Override
            public void onAlarm(Alarm alarm) {
                dc();
            }
        });
        this.dd();
        mAppContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                dd();
            }
        }, ActionIntentFilter.googleInstance(
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_DATA_CLEARED));
    }

    private Intent db() {
        return new Intent("com.google.android.apps.gsa.smartspace.SETTINGS")
                .setPackage("com.google.android.googlequicksearchbox")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private void dc() {
        final boolean cr = this.dQ.isWeatherAvailable();
        final boolean cs = this.dQ.cS();
        this.dQ.cU();
        if (cr && !this.dQ.isWeatherAvailable()) {
            this.df(null, SmartspaceController.Store.WEATHER);
        }
        if (cs && !this.dQ.cS()) {
            this.df(null, SmartspaceController.Store.CURRENT);
            this.mAppContext.sendBroadcast(new Intent("com.google.android.apps.gsa.smartspace.EXPIRE_EVENT")
                    .setPackage("com.google.android.googlequicksearchbox")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private void dd() {
        if (this.dS != null) {
            this.dS.cq();
        }
        this.de();
    }

    private void de() {
        this.mAppContext.sendBroadcast(new Intent("com.google.android.apps.gsa.smartspace.ENABLE_UPDATE")
                .setPackage("com.google.android.googlequicksearchbox")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void df(final NewCardInfo a, final SmartspaceController.Store SmartspaceControllerStore) {
        Message.obtain(this.mWorker, 2, SmartspaceControllerStore.ordinal(), 0, a).sendToTarget();
    }

    public static SmartspaceController get(final Context context) {
        if (SmartspaceController.dU == null) {
            SmartspaceController.dU = new SmartspaceController(context.getApplicationContext());
        }
        return SmartspaceController.dU;
    }

    private void update() {
        this.dR.cancelAlarm();
        final long ct = this.dQ.cT();
        if (ct > 0L) {
            this.dR.setAlarm(ct);
        }
        if (this.dS != null) {
            this.dS.cr(this.dQ);
        }
    }

    public void cV(final NewCardInfo a) {
        if (a != null && !a.dj) {
            this.df(a, SmartspaceController.Store.WEATHER);
        } else {
            this.df(a, SmartspaceController.Store.CURRENT);
        }
    }

    public void cW() {
        Message.obtain(this.mWorker, 1).sendToTarget();
    }

    public void cX(final String s, final PrintWriter printWriter) {
        printWriter.println();
        printWriter.println(s + "SmartspaceController");
        printWriter.println(s + "  weather " + this.dQ.dO);
        printWriter.println(s + "  current " + this.dQ.dP);
    }

    public boolean cY() {
        boolean b = false;
        final List queryBroadcastReceivers = this.mAppContext.getPackageManager().queryBroadcastReceivers(this.db(), 0);
        if (queryBroadcastReceivers != null) {
            b = !queryBroadcastReceivers.isEmpty();
        }
        return b;
    }

    public void cZ() {
        this.mAppContext.sendBroadcast(this.db());
    }

    public void da(final ISmartspace ds) {
        this.dS = ds;
    }

    public boolean handleMessage(final Message message) {
        SmartspaceCard dVar = null;
        switch (message.what) {
            case 1:
                i data = new i();
                SmartspaceCard weatherCard = this.dT.dv(SmartspaceController.Store.WEATHER.filename, data) ?
                        SmartspaceCard.cD(this.mAppContext, data, true) :
                        null;

                data = new i();
                SmartspaceCard eventCard = this.dT.dv(SmartspaceController.Store.CURRENT.filename, data) ?
                        SmartspaceCard.cD(this.mAppContext, data, false) :
                        null;

                Message.obtain(this.mUiHandler, 101, new SmartspaceCard[]{ weatherCard, eventCard }).sendToTarget();
                break;
            case 2:
                this.dT.dw(SmartspaceCard.cQ(this.mAppContext, (NewCardInfo) message.obj), SmartspaceController.Store.values()[message.arg1].filename);
                Message.obtain(this.mUiHandler, 1).sendToTarget();
                break;
            case 101:
                SmartspaceCard[] dVarArr = (SmartspaceCard[]) message.obj;
                if (dVarArr != null) {
                    this.dQ.dO = dVarArr.length > 0 ?
                            dVarArr[0] :
                            null;

                    SmartspaceDataContainer eVar = this.dQ;
                    if (dVarArr.length > 1) {
                        dVar = dVarArr[1];
                    }

                    eVar.dP = dVar;
                }
                this.dQ.cU();
                update();
                break;
        }
        return true;
    }
}
