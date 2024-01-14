package app.lawnchair.compatlib;


import android.app.IApplicationThread;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class RemoteTransitionCompat {

    private final RemoteTransition remoteTransition;

    public RemoteTransitionCompat(@NonNull IRemoteTransition remoteTransition,
                                  @Nullable IApplicationThread appThread,
                                  @Nullable String debugName) {
        this.remoteTransition = createRemoteTransition(remoteTransition, appThread, debugName);
    }

    private RemoteTransition createRemoteTransition(IRemoteTransition remoteTransition, IApplicationThread appThread, String debugName) {
        try {
            Class<?> remoteTransitionClass = Class.forName("android.window.RemoteTransition");
            try {
                Constructor<?> constructor = remoteTransitionClass.getConstructor(IRemoteTransition.class, IApplicationThread.class, String.class);
                return (RemoteTransition) constructor.newInstance(remoteTransition, appThread, debugName);
            } catch (NoSuchMethodException ignored) {
                Constructor<?> constructor = remoteTransitionClass.getConstructor(IRemoteTransition.class, IApplicationThread.class);
                return (RemoteTransition) constructor.newInstance(remoteTransition, appThread);
            }

        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException |
                 InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Error creating RemoteTransitionCompat", e);
        }
    }

    public RemoteTransition getRemoteTransition() {
        return remoteTransition;
    }
}