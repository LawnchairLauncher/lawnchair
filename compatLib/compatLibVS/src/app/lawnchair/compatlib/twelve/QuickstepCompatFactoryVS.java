package app.lawnchair.compatlib.twelve;

import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import app.lawnchair.compatlib.ActivityManagerCompat;
import app.lawnchair.compatlib.ActivityOptionsCompat;
import app.lawnchair.compatlib.QuickstepCompatFactory;
import app.lawnchair.compatlib.RemoteTransitionCompat;

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

    @NonNull
    @Override
    public RemoteTransitionCompat getRemoteTransitionCompat() {
        return new RemoteTransitionCompat() {
            @Override
            public RemoteTransition getRemoteTransition(@NonNull IRemoteTransition remoteTransition,
                                                        @Nullable IApplicationThread appThread,
                                                        @Nullable String debugName) {
                return createRemoteTransition(remoteTransition, appThread, debugName);
            }
        };
    }

    // TODO remove this as it causing glitches on first launch opening/closing app
    private RemoteTransition createRemoteTransition(IRemoteTransition remoteTransition,
                                                    IApplicationThread appThread,
                                                    String debugName) {
        try {
            Class<?> remoteTransitionClass = Class.forName("android.window.RemoteTransition");
            Constructor<?> constructor = remoteTransitionClass.getConstructor(IRemoteTransition.class, IApplicationThread.class, String.class);
            return (RemoteTransition) constructor.newInstance(remoteTransition, appThread);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                 InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Error creating RemoteTransitionCompat" + debugName, e);
        }
    }

}
