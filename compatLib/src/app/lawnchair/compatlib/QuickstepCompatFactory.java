package app.lawnchair.compatlib;

import androidx.annotation.NonNull;

public abstract class QuickstepCompatFactory {

    @NonNull
    public abstract ActivityManagerCompat getActivityManagerCompat();

    @NonNull
    public abstract ActivityOptionsCompat getActivityOptionsCompat();

}
