package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import java.util.ArrayList;

import ch.deletescape.lawnchair.Alarm;
import ch.deletescape.lawnchair.OnAlarmListener;

public class WeatherThing extends BroadcastReceiver implements OnAlarmListener {
    static long INITIAL_LOAD_TIMEOUT = 5000;
    private static WeatherThing instance;
    private WeatherInfo weatherInfo;
    private boolean weatherInfoLoaded = false;
    private final AppWidgetManagerHelper appWidgetManagerHelper;
    private final Alarm mAlarm;
    private final Context mContext;
    private final ArrayList<OnWeatherInfoListener> mListeners;

    public static WeatherThing getInstance(Context context) {
        if (instance == null) {
            instance = new WeatherThing(context.getApplicationContext());
        }
        return instance;
    }

    WeatherThing(Context context) {
        mContext = context;
        mListeners = new ArrayList<>();
        appWidgetManagerHelper = new AppWidgetManagerHelper(mContext);
        mAlarm = new Alarm();
        mAlarm.setOnAlarmListener(this);
        onReceive(null, null);
        mAlarm.setAlarm(INITIAL_LOAD_TIMEOUT);
        aU(weatherInfo);
        context.registerReceiver(this, Util.createIntentFilter("android.intent.action.PACKAGE_ADDED", "android.intent.action.PACKAGE_CHANGED", "android.intent.action.PACKAGE_REMOVED", "android.intent.action.PACKAGE_DATA_CLEARED"));
    }

    private void aU(WeatherInfo gsa) {
        Bundle appWidgetOptions = appWidgetManagerHelper.getAppWidgetOptions();
        if (appWidgetOptions != null) {
            WeatherInfo gsa2 = new WeatherInfo(appWidgetOptions);
            if (gsa2.mRemoteViews != null && gsa2.gsaVersion == gsa.gsaVersion && gsa2.gsaUpdateTime == gsa.gsaUpdateTime && gsa2.validity() > 0) {
                this.weatherInfo = gsa2;
                onNewWeatherInfo();
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        weatherInfo = new WeatherInfo(mContext, null);
        mContext.sendBroadcast(new Intent("com.google.android.apps.gsa.weatherwidget.ENABLE_UPDATE").setPackage("com.google.android.googlequicksearchbox"));
    }

    private void onNewWeatherInfo() {
        weatherInfoLoaded = true;
        for (OnWeatherInfoListener listener : mListeners) {
            listener.onWeatherInfo(weatherInfo);
        }
        mAlarm.cancelAlarm();
        if (weatherInfo.mRemoteViews != null) {
            mAlarm.setAlarm(weatherInfo.validity());
        }
    }

    public void aT(RemoteViews remoteViews) {
        if (remoteViews != null) {
            weatherInfo = new WeatherInfo(mContext, remoteViews);
            onNewWeatherInfo();
            appWidgetManagerHelper.updateAppWidgetOptions(weatherInfo.toBundle());
        }
    }

    @Override
    public void onAlarm(Alarm alarm) {
        if (weatherInfo.mRemoteViews != null || !weatherInfoLoaded) {
            weatherInfo = new WeatherInfo(mContext, null);
            onNewWeatherInfo();
        }
    }

    public WeatherInfo getWeatherInfoAndAddListener(OnWeatherInfoListener onWeatherInfoListener) {
        mListeners.add(onWeatherInfoListener);
        return weatherInfoLoaded ? weatherInfo : null;
    }

    public void removeListener(OnWeatherInfoListener onWeatherInfoListener) {
        mListeners.remove(onWeatherInfoListener);
    }
}