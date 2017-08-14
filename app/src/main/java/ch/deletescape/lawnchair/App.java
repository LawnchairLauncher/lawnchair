package ch.deletescape.lawnchair;

import android.app.Application;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        //if(!BuildConfig.MOBILE_CENTER_KEY.equalsIgnoreCase("null"))
        //MobileCenter.start(this, BuildConfig.MOBILE_CENTER_KEY, Analytics.class, Crashes.class, Distribute.class);

        // TODO: Initiliase the default preference provider => if called, this single instance will be used in the whole app
//        PreferenceProvider.INSTANCE.init(new PreferenceImpl(this));
    }
}
