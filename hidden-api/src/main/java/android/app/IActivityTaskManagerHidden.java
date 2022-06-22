package android.app;

import android.os.IBinder;
import android.os.RemoteException;
import android.view.RemoteAnimationAdapter;

import dev.rikka.tools.refine.RefineAs;

@RefineAs(IActivityTaskManager.class)
public interface IActivityTaskManagerHidden {

    void registerRemoteAnimationForNextActivityStart(
            String packageName, RemoteAnimationAdapter adapter, IBinder launchCookie) throws RemoteException;

    void registerRemoteAnimationForNextActivityStart(
            String packageName, RemoteAnimationAdapter adapter) throws RemoteException;
}
