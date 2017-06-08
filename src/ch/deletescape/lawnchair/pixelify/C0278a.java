package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.RemoteViews;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

import ch.deletescape.lawnchair.Alarm;
import ch.deletescape.lawnchair.OnAlarmListener;

public class C0278a {
    static long INITIAL_LOAD_TIMEOUT = 5000;
    private static C0278a bt;
    private C0280e bp;
    private boolean bq = false;
    private final C0331b br;
    private final Alarm bs;
    private final Context mContext;
    private final ArrayList<C0277b> mListeners;

    public static C0278a aS(Context context) {
        if (bt == null) {
            bt = new C0278a(context.getApplicationContext());
        }
        return bt;
    }

    C0278a(Context context) {
        this.mContext = context;
        this.mListeners = new ArrayList();
        this.br = new C0331b(this.mContext);
        this.bs = new Alarm();
        this.bs.setOnAlarmListener(new C0282g(this));
        aV();
        this.bs.setAlarm(INITIAL_LOAD_TIMEOUT);
        aU(this.bp);
        context.registerReceiver(new C0283h(this), C0330a.ca("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED", "android.intent.action.PACKAGE_DATA_CLEARED"));
    }

    private void aU(C0280e c0280e) {
        Bundle cb = this.br.cb();
        if (cb != null) {
            C0280e c0280e2 = new C0280e(cb);
            if (c0280e2.bQ != null && c0280e2.bR == c0280e.bR && c0280e2.bS == c0280e.bS && c0280e2.bE() > 0) {
                this.bp = c0280e2;
                aX();
            }
        }
    }

    private void aV() {
        this.bp = new C0280e(this.mContext, null);
        this.mContext.sendBroadcast(new Intent("com.google.android.apps.gsa.weatherwidget.ENABLE_UPDATE").setPackage("com.google.android.googlequicksearchbox"));
    }

    public void aT(RemoteViews remoteViews) {
        if (remoteViews != null) {
            this.bp = new C0280e(this.mContext, remoteViews);
            aX();
            this.br.cc(this.bp.toBundle());
        }
    }

    private void aX() {
        this.bq = true;
        for (C0277b bb : this.mListeners) {
            bb.bb(this.bp);
        }
        this.bs.cancelAlarm();
        if (this.bp.bQ != null) {
            this.bs.setAlarm(this.bp.bE());
        }
    }

    private void aW(Alarm alarm) {
        if (this.bp.bQ != null || !this.bq) {
            this.bp = new C0280e(this.mContext, null);
            aX();
        }
    }

    public C0280e aR(C0277b c0277b) {
        this.mListeners.add(c0277b);
        return this.bq ? this.bp : null;
    }

    public void aY(C0277b c0277b) {
        this.mListeners.remove(c0277b);
    }

    public void dump(String str, FileDescriptor fileDescriptor, PrintWriter printWriter, String[] strArr) {
        printWriter.println();
        printWriter.println(str + "WeatherManager");
        if (this.bp == null) {
            printWriter.println(str + "  mCachedData = null");
            return;
        }
        printWriter.println(str + "  views " + this.bp.bQ);
        printWriter.println(str + "  gsaVersion " + this.bp.bR);
        printWriter.println(str + "  gsaUpdateTime " + this.bp.bS);
        printWriter.println(str + "  publishTime " + this.bp.bT);
        printWriter.println(str + "  elapsedDuration " + (SystemClock.uptimeMillis() - this.bp.bT));
    }

    final class C0282g implements OnAlarmListener {
        final /* synthetic */ C0278a cm;

        C0282g(C0278a c0278a) {
            this.cm = c0278a;
        }

        public void onAlarm(Alarm alarm) {
            this.cm.aW(alarm);
        }
    }

    final class C0283h extends BroadcastReceiver {
        final /* synthetic */ C0278a cn;

        C0283h(C0278a c0278a) {
            this.cn = c0278a;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            this.cn.aV();
        }
    }
}