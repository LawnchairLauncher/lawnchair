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
        if (!iconID.startsWith("01")) {
            iconID = iconID.substring(0, 2);
        }
        return mContext.getDrawable(ID_MAP.get(iconID));
    }

    private void fillMap() {
        ID_MAP.put("01d", R.drawable.weather_clear_day);
        ID_MAP.put("01n", R.drawable.weather_clear_night);
        ID_MAP.put("02", R.drawable.weather_cloudy);
        ID_MAP.put("03", R.drawable.weather_cloudy);
        ID_MAP.put("04", R.drawable.weather_cloudy);
        ID_MAP.put("09", R.drawable.weather_rainy);
        ID_MAP.put("10", R.drawable.weather_rainy);
        ID_MAP.put("11", R.drawable.weather_stormy);
        ID_MAP.put("13", R.drawable.weather_snowy);
        ID_MAP.put("50", R.drawable.weather_hazy);
        ID_MAP.put("-1", R.drawable.weather_unknown);
    }
}
