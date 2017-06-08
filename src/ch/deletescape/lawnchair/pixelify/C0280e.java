package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class C0280e {
    static long VALIDITY_DURATION = 7200000;
    public final RemoteViews bQ;
    public final int bR;
    public final long bS;
    public final long bT;

    public C0280e(Context context, RemoteViews remoteViews) {
        PackageInfo packageInfo = null;
        this.bQ = remoteViews;
        try {
            packageInfo = context.getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", 0);
        } catch (NameNotFoundException e) {
        }
        if (packageInfo != null) {
            this.bS = packageInfo.lastUpdateTime;
            this.bR = packageInfo.versionCode;
        } else {
            this.bS = 0;
            this.bR = 0;
        }
        this.bT = SystemClock.uptimeMillis();
    }

    public C0280e(Bundle bundle) {
        this.bR = bundle.getInt("gsa_version", 0);
        this.bS = bundle.getLong("gsa_update_time", 0);
        this.bT = bundle.getLong("publish_time", 0);
        this.bQ = (RemoteViews) bundle.getParcelable("views");
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("gsa_version", this.bR);
        bundle.putLong("gsa_update_time", this.bS);
        bundle.putLong("publish_time", this.bT);
        bundle.putParcelable("views", this.bQ);
        return bundle;
    }

    public long bE() {
        return (VALIDITY_DURATION + this.bT) - SystemClock.uptimeMillis();
    }
}