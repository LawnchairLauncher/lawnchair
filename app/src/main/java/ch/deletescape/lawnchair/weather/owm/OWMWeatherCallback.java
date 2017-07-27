package ch.deletescape.lawnchair.weather.owm;

public interface OWMWeatherCallback {
    void onSuccess(OWMWeatherData weatherData);
    void onFailed();
}
