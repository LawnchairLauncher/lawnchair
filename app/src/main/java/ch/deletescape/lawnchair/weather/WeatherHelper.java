package ch.deletescape.lawnchair.weather;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.kwabenaberko.openweathermaplib.implementation.OpenWeatherMapHelper;
import com.kwabenaberko.openweathermaplib.models.CurrentWeather;

import java.util.Locale;

import ch.deletescape.lawnchair.BuildConfig;
import ch.deletescape.lawnchair.Utilities;

public class WeatherHelper implements OpenWeatherMapHelper.CurrentWeatherCallback, SharedPreferences.OnSharedPreferenceChangeListener, Runnable {
    private static final String KEY_UNITS = "pref_weather_units";
    private static final String KEY_CITY = "pref_weather_city";
    private static final int DELAY = 30 * 3600 * 1000;
    private TextView mTemperatureView;
    private boolean mIsImperial;
    private String mUnits;
    private String mCity;
    private String mTemp;
    private OpenWeatherMapHelper mHelper;
    private Handler mHandler;
    private String mIcon;
    private ImageView mIconView;
    private WeatherIconProvider iconProvider;

    public WeatherHelper(TextView temperatureView, ImageView iconView, Context context) {
        mTemperatureView = temperatureView;
        mIconView = iconView;
        iconProvider = new WeatherIconProvider(context);
        setupOnClickListener(context);
        mHandler = new Handler();
        mHelper = new OpenWeatherMapHelper();
        mHelper.setAppId(BuildConfig.OPENWEATHERMAP_KEY);
        SharedPreferences prefs = Utilities.getPrefs(context);
        setCity(prefs.getString(KEY_CITY, "Lucerne, CH"));
        setUnits(prefs.getString(KEY_UNITS, "metric"));
        refresh();
    }

    private void refresh() {
        mHelper.getCurrentWeatherByCityName(mCity, this);
        mHandler.postDelayed(this, DELAY);
    }

    private String makeTemperatureString(String string) {
        return String.format(mIsImperial ? "%s°F" : "%s°C", string);
    }

    @Override
    public void onSuccess(CurrentWeather currentWeather) {
        mTemp = String.format(Locale.US, "%.0f", currentWeather.getMain().getTemp());
        mIcon = currentWeather.getWeatherArray().get(0).getIcon();
        updateTextView();
        updateIconView();
    }

    @Override
    public void onFailure(Throwable throwable) {
        mTemp = (mTemp != null && !mTemp.equals("ERROR")) ? mTemp : "ERROR";
        mIcon = "-1d";
        updateTextView();
        updateIconView();
    }

    private void updateTextView() {
        mTemperatureView.setText(makeTemperatureString(mTemp));
    }

    private void updateIconView() {
        mIconView.setImageDrawable(iconProvider.getIcon(mIcon));
    }

    private void setCity(String city) {
        mCity = city;
    }

    private void setUnits(String units) {
        mUnits = units;
        mIsImperial = units.equals("imperial");
        mHelper.setUnits(units);
    }

    private void setupOnClickListener(final Context context) {
        mTemperatureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("dynact://velour/weather/ProxyActivity"));
                    intent.setComponent(new ComponentName("com.google.android.googlequicksearchbox",
                            "com.google.android.apps.gsa.velour.DynamicActivityTrampoline"));
                    context.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPrefs, String key) {
        switch (key) {
            case KEY_UNITS:
                setUnits(sharedPrefs.getString(KEY_UNITS, mUnits));
                updateTextView();
                break;
            case KEY_CITY:
                setCity(sharedPrefs.getString(KEY_CITY, mCity));
                break;
        }
    }

    @Override
    public void run() {
        refresh();
    }
}
