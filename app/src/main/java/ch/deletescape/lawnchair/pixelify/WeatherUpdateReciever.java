package ch.deletescape.lawnchair.pixelify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class WeatherUpdateReciever extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    RemoteViews remoteViews = intent.getParcelableExtra("com.google.android.apps.nexuslauncher.weather_view");
    WeatherThing.getInstance(context).aT(remoteViews);
  }
}
