package ch.deletescape.lawnchair.weather.owm;

import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import ch.deletescape.lawnchair.weather.WeatherUnits;

public class OWMWeatherDownload {

    private String TAG = "OWMWeatherDownload";

    private String apiKey;
    private String location;
    private WeatherUnits weatherUnits;
    private OWMWeatherCallback weatherCallback;

    public OWMWeatherDownload setAPIKey(String apiKey) {
        this.apiKey = apiKey;
        return this;
    }

    public OWMWeatherDownload setLocation(String location) {
        this.location = location.replaceAll(" ", "");
        return this;
    }

    public OWMWeatherDownload setWeatherUnits(WeatherUnits weatherUnits) {
        this.weatherUnits = weatherUnits;
        return this;
    }

    public OWMWeatherDownload download(OWMWeatherCallback weatherCallback) {
        this.weatherCallback = weatherCallback;
        new WeatherDownload().execute();
        return this;
    }

    private class WeatherDownload extends AsyncTask<Void, Void, Void> {
        JSONObject json = null;
        @Override
        protected Void doInBackground(Void... voids) {
            json = download();
            return null;
        }
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (json != null)
                new OWMWeatherData(json, weatherCallback, weatherUnits);
            else
                weatherCallback.onFailed();
        }
    }

    @Nullable
    private JSONObject download() {
        try {
            Uri uri = Uri.parse("http://api.openweathermap.org/data/2.5/weather?")
                    .buildUpon()
                    .appendQueryParameter("q", location)
                    .appendQueryParameter("units", weatherUnits == WeatherUnits.IMPERIAL ? "imperial" : "metric")
                    .appendQueryParameter("APPID", apiKey)
                    .build();
            URL url = new URL(uri.toString());
            HttpURLConnection request = (HttpURLConnection) url.openConnection();
            request.setRequestMethod("GET");
            request.connect();
            if (request.getInputStream() != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length = request.getInputStream().read(buffer);
                while (length != -1) {
                    byteArrayOutputStream.write(buffer, 0, length);
                    length = request.getInputStream().read(buffer);
                }
                return new JSONObject(byteArrayOutputStream.toString("UTF-8"));
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to download JSON.");
        }
        return null;
    }

}
