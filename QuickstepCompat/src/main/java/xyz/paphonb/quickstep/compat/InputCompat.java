package xyz.paphonb.quickstep.compat;

import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.BatchedInputEventReceiver;;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;

public abstract class InputCompat {

    public abstract BatchedInputEventReceiver createBatchedInputEventReceiver(
            InputChannel inputChannel, Looper looper, Choreographer choreographer,
            final InputEventListener listener);

    public abstract void createInputConsumer(
            IWindowManager windowManager, IBinder token, String name, int displayId,
            InputChannel inputChannel) throws RemoteException;

    public abstract boolean destroyInputConsumer(
            IWindowManager windowManager, String name, int displayId) throws RemoteException;

    public interface InputEventListener {

        boolean onInputEvent(InputEvent event);
    }
}
