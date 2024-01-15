package app.lawnchair.compatlib.fourteen;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.thirteen.ActivityOptionsCompatVT;
import app.lawnchair.compatlib.thirteen.QuickstepCompatFactoryVT;

public class QuickstepCompatFactoryVU extends QuickstepCompatFactoryVT {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVU();
    }

    @NonNull
    @Override
    public ActivityOptionsCompat getActivityOptionsCompat() {
        return new ActivityOptionsCompatVU();
    }
}
