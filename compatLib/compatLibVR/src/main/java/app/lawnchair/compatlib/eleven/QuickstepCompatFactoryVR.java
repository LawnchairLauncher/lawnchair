package app.lawnchair.compatlib.eleven;

import androidx.annotation.NonNull;
import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.ten.QuickstepCompatFactoryVQ;

public class QuickstepCompatFactoryVR extends QuickstepCompatFactoryVQ {

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
