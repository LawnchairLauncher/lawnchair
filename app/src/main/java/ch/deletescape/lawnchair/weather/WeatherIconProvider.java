package ch.deletescape.lawnchair.weather;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.config.FeatureFlags;

public class WeatherIconProvider {
    private static final Map<String, Integer> ID_MAP = new HashMap<>();
    private Context mContext;

    public WeatherIconProvider(Context context) {
        mContext = context;
        if (ID_MAP.isEmpty()) {
            fillMap();
        }
    }

    public Drawable getIcon(String iconID) {
        return mContext.getDrawable(ID_MAP.get(iconID));
    }

    private void fillMap() {
        if(!FeatureFlags.uglifyWeather(mContext)) {
            ID_MAP.put("01d", R.drawable.weather_01);
            ID_MAP.put("01n", R.drawable.weather_01n);
            ID_MAP.put("02d", R.drawable.weather_02);
            ID_MAP.put("02n", R.drawable.weather_02n);
            ID_MAP.put("03d", R.drawable.weather_03);
            ID_MAP.put("03n", R.drawable.weather_03n);
            ID_MAP.put("04d", R.drawable.weather_04);
            ID_MAP.put("04n", R.drawable.weather_04n);
            ID_MAP.put("09d", R.drawable.weather_09);
            ID_MAP.put("09n", R.drawable.weather_09n);
            ID_MAP.put("10d", R.drawable.weather_10);
            ID_MAP.put("10n", R.drawable.weather_10n);
            ID_MAP.put("11d", R.drawable.weather_11);
            ID_MAP.put("11n", R.drawable.weather_11n);
            ID_MAP.put("13d", R.drawable.weather_13);
            ID_MAP.put("13n", R.drawable.weather_13n);
            ID_MAP.put("50d", R.drawable.weather_50);
            ID_MAP.put("50n", R.drawable.weather_50n);
            ID_MAP.put("-1", R.drawable.weather_none_available);
        } else {
            ID_MAP.put("01d", R.drawable.ugly_01);
            ID_MAP.put("01n", R.drawable.ugly_01n);
            ID_MAP.put("02d", R.drawable.ugly_02);
            ID_MAP.put("02n", R.drawable.ugly_02n);
            ID_MAP.put("03d", R.drawable.ugly_03);
            ID_MAP.put("03n", R.drawable.ugly_03n);
            ID_MAP.put("04d", R.drawable.ugly_04);
            ID_MAP.put("04n", R.drawable.ugly_04n);
            ID_MAP.put("09d", R.drawable.ugly_09);
            ID_MAP.put("09n", R.drawable.ugly_09n);
            ID_MAP.put("10d", R.drawable.ugly_10);
            ID_MAP.put("10n", R.drawable.ugly_10n);
            ID_MAP.put("11d", R.drawable.ugly_11);
            ID_MAP.put("11n", R.drawable.ugly_11n);
            ID_MAP.put("13d", R.drawable.ugly_13);
            ID_MAP.put("13n", R.drawable.ugly_13n);
            ID_MAP.put("50d", R.drawable.ugly_50);
            ID_MAP.put("50n", R.drawable.ugly_50n);
            ID_MAP.put("-1", R.drawable.ugly_none_available);
        }
    }
}
