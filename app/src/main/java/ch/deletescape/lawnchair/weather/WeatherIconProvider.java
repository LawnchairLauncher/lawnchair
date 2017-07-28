package ch.deletescape.lawnchair.weather;

import android.content.Context;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

import ch.deletescape.lawnchair.R;

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
        ID_MAP.put("01d", R.drawable.weather_clear);
        ID_MAP.put("01n", R.drawable.weather_clear_night);
        ID_MAP.put("02d", R.drawable.weather_few_clouds);
        ID_MAP.put("02n", R.drawable.weather_few_clouds_night);
        ID_MAP.put("03d", R.drawable.weather_clouds);
        ID_MAP.put("03n", R.drawable.weather_clouds_night);
        ID_MAP.put("04d", R.drawable.weather_clouds);
        ID_MAP.put("04n", R.drawable.weather_clouds_night);
        ID_MAP.put("09d", R.drawable.weather_showers_day);
        ID_MAP.put("09n", R.drawable.weather_showers_night);
        ID_MAP.put("10d", R.drawable.weather_rain_day);
        ID_MAP.put("10n", R.drawable.weather_rain_night);
        ID_MAP.put("11d", R.drawable.weather_storm_day);
        ID_MAP.put("11n", R.drawable.weather_storm_night);
        ID_MAP.put("13d", R.drawable.weather_snow_scattered_day);
        ID_MAP.put("13n", R.drawable.weather_snow_scattered_night);
        ID_MAP.put("50d", R.drawable.weather_mist);
        ID_MAP.put("50n", R.drawable.weather_mist);
        ID_MAP.put("-1", R.drawable.weather_none_available);
    }
}
