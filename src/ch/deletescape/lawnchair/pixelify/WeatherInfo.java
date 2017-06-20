package ch.deletescape.lawnchair.pixelify;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.RemoteViews;

public class WeatherInfo {
    static long VALIDITY_DURATION = 7200000;
    public final RemoteViews mRemoteViews;
    public final int gsaVersion;
    public final long gsaUpdateTime;
    public final long publishTime;

    public WeatherInfo(Context context, RemoteViews remoteViews) {
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

    public WeatherInfo(Bundle bundle) {
        gsaVersion = bundle.getInt("gsa_version", 0);
        gsaUpdateTime = bundle.getLong("gsa_update_time", 0);
        publishTime = bundle.getLong("publish_time", 0);
        mRemoteViews = bundle.getParcelable("views");
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putInt("gsa_version", gsaVersion);
        bundle.putLong("gsa_update_time", gsaUpdateTime);
        bundle.putLong("publish_time", publishTime);
        bundle.putParcelable("views", mRemoteViews);
        return bundle;
    }

    public long validity() {
        return (VALIDITY_DURATION + publishTime) - SystemClock.uptimeMillis();
    }
}