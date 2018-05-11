package ch.deletescape.lawnchair;

import android.app.Application;

import ch.deletescape.lawnchair.preferences.PreferenceImpl;
import ch.deletescape.lawnchair.preferences.PreferenceProvider;

public class App extends Application {

     @Override
    public void onCreate() {
        super.onCreate();

        PreferenceProvider.INSTANCE.init(new PreferenceImpl(this));
    }
}
