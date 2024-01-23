package app.lawnchair.compatlib.ten;

import androidx.annotation.NonNull;
import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.eleven.QuickstepCompatFactoryVR;

public class QuickstepCompatFactoryVQ extends QuickstepCompatFactoryVR {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVQ();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVQ();
    }
}
