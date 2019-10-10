package xyz.paphonb.quickstep.compat;

import androidx.annotation.NonNull;

public abstract class QuickstepCompatFactory {

    @NonNull
    public abstract ActivityManagerCompat getActivityManagerCompat();

    @NonNull
    public abstract RecentsCompat getRecentsModelCompat();

    @NonNull
    public abstract InputCompat getInputCompat();
}
