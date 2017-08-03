package ch.deletescape.lawnchair;

import android.app.Application;
import android.os.Build;

import com.microsoft.azure.mobile.MobileCenter;
import com.microsoft.azure.mobile.analytics.Analytics;
import com.microsoft.azure.mobile.crashes.Crashes;
import com.microsoft.azure.mobile.distribute.Distribute;


public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        //if(!BuildConfig.MOBILE_CENTER_KEY.equalsIgnoreCase("null"))
            //MobileCenter.start(this, BuildConfig.MOBILE_CENTER_KEY, Analytics.class, Crashes.class, Distribute.class);
    }
}
