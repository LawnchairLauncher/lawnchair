package xyz.paphonb.quickstep.compat.ten;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

import xyz.paphonb.quickstep.compat.ActivityManagerCompat;
import xyz.paphonb.quickstep.compat.InputCompat;
import xyz.paphonb.quickstep.compat.QuickstepCompatFactory;
import xyz.paphonb.quickstep.compat.RecentsCompat;

@RequiresApi(Build.VERSION_CODES.Q)
public class QuickstepCompatFactoryVQ extends QuickstepCompatFactory {

    @NotNull
    @Override
    public ActivityManagerCompat getActivityManagerCompat() {
        return new ActivityManagerCompatVQ();
    }

    @NotNull
    @Override
    public RecentsCompat getRecentsModelCompat() {
        return new RecentsCompatVQ();
    }

    @NonNull
    @Override
    public InputCompat getInputCompat() {
        return new InputCompatVQ();
    }
}
