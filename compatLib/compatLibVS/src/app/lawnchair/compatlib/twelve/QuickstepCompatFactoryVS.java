package app.lawnchair.compatlib.twelve;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;

public class QuickstepCompatFactoryVS extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVS();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVS();
    }

}
