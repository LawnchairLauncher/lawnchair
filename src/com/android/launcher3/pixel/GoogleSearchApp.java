package com.android.launcher3.pixel;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class GoogleSearchApp { //a
    static long VALIDITY_DURATION = 7200000L;
    public RemoteViews mRemoteViews; //bs
    public int gsaVersion; //bt
    public long gsaUpdateTime; //bu
    public long publishTime; //bv

    public GoogleSearchApp(Context context, RemoteViews remoteViews) { //a
        PackageInfo packageInfo = null;
        mRemoteViews = remoteViews;
        try {
            packageInfo = context.getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", 0);
        } catch (NameNotFoundException e) {
        }
        if (packageInfo != null) {
            gsaUpdateTime = packageInfo.lastUpdateTime;
            gsaVersion = packageInfo.versionCode;
        } else {
            gsaUpdateTime = 0;
            gsaVersion = 0;
        }
        publishTime = SystemClock.uptimeMillis();
    }

    public GoogleSearchApp(Bundle bundle) { //a
        gsaVersion = bundle.getInt("gsa_version", 0);
        gsaUpdateTime = bundle.getLong("gsa_update_time", 0);
        publishTime = bundle.getLong("publish_time", 0);
        mRemoteViews = bundle.getParcelable("views");
    }

    public long validity() { //aY
        return (VALIDITY_DURATION + publishTime) - SystemClock.uptimeMillis();
    }

    public Bundle getBundle() { //aZ
        Bundle bundle = new Bundle();
        bundle.putInt("gsa_version", gsaVersion);
        bundle.putLong("gsa_update_time", gsaUpdateTime);
        bundle.putLong("publish_time", publishTime);
        bundle.putParcelable("views", mRemoteViews);
        return bundle;
    }
}