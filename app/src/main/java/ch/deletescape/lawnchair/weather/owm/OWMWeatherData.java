package ch.deletescape.lawnchair.weather.owm;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import ch.deletescape.lawnchair.weather.WeatherUnits;

public class OWMWeatherData {

    private String TAG = "OWMWeatherData";

    private String temperature;
    private int conditionID;
    private String condition;
    private String conditionDescription;
    private long sunriseTimeInMillis;
    private long sunsetTimeInMillis;


    public OWMWeatherData(JSONObject json, OWMWeatherCallback weatherCallback, WeatherUnits weatherUnits) {
        // Parse JSON.
        try {
            temperature = String.valueOf(Math.round(json.getJSONObject("main").getDouble("temp"))) + (weatherUnits == WeatherUnits.IMPERIAL ? "\u00b0F" : "\u00b0C");
            JSONObject weatherCondition = json.getJSONArray("weather").getJSONObject(0);
            conditionID = weatherCondition.getInt("id");
            condition = weatherCondition.getString("main");
            conditionDescription = weatherCondition.getString("description");
            // We can use the sunrise and sunset times to choose whether we use a day or night time weather icon.
            // The times are in UNIX UTC format.
            JSONObject sys = json.getJSONObject("sys");
            sunriseTimeInMillis = new Date(sys.getLong("sunrise") * 1000).getTime();
            sunsetTimeInMillis = new Date(sys.getLong("sunset") * 1000).getTime();
            weatherCallback.onSuccess(this);
        } catch (JSONException e) {
            Log.d(TAG, "Failed to parse JSON.");
            weatherCallback.onFailed();
        }
    }

    public String getTemperature() {
        return String.valueOf(temperature);
    }

    public int getConditionID() {
        return conditionID;
    }

    public String getCondition() {
        return condition;
    }

    public String getConditionDescription() {
        return conditionDescription;
    }

    public long getSunriseTimeInMillis() {
        return sunriseTimeInMillis;
    }

    public long getSunsetTimeInMillis() {
        return sunsetTimeInMillis;
    }
}
