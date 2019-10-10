package xyz.paphonb.quickstep.compat.pie;

import androidx.annotation.NonNull;

import xyz.paphonb.quickstep.compat.ActivityManagerCompat;
import xyz.paphonb.quickstep.compat.InputCompat;
import xyz.paphonb.quickstep.compat.QuickstepCompatFactory;
import xyz.paphonb.quickstep.compat.RecentsCompat;

public class QuickstepCompatFactoryVP extends QuickstepCompatFactory {

    @NonNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVP();
    }

    @NonNull
    @Override
    public RecentsCompat getRecentsModelCompat() {
        return new RecentsCompatVP();
    }

    @NonNull
    @Override
    public InputCompat getInputCompat() {
        return new InputCompatVP();
    }
}
