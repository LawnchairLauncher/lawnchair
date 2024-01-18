package app.lawnchair.compatlib.eleven;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;

public class QuickstepCompatFactoryVR extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVR();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVR();
    }

}
