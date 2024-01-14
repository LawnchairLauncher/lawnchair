package app.lawnchair.compatlib.thirteen;

import androidx.annotation.NonNull;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.twelve.QuickstepCompatFactoryVS;

public class QuickstepCompatFactoryVT extends QuickstepCompatFactoryVS {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVT();
    }
}
