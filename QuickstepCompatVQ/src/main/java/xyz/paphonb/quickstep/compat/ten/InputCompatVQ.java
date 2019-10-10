package xyz.paphonb.quickstep.compat.ten;

import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.view.BatchedInputEventReceiver;;
import android.view.Choreographer;
import android.view.IWindowManager;
import android.view.InputChannel;
import android.view.InputEvent;

import androidx.annotation.RequiresApi;

import xyz.paphonb.quickstep.compat.InputCompat;

@RequiresApi(Build.VERSION_CODES.Q)
public class InputCompatVQ extends InputCompat {

    @Override
    public BatchedInputEventReceiver createBatchedInputEventReceiver(
            InputChannel inputChannel, Looper looper, Choreographer choreographer,
            final InputCompat.InputEventListener listener) {
        return new BatchedInputEventReceiver(inputChannel, looper, choreographer) {

            @Override
            public void onInputEvent(InputEvent event) {
                boolean handled = listener.onInputEvent(event);
                finishInputEvent(event, handled);
            }
        };
    }

    @Override
    public void createInputConsumer(
            IWindowManager windowManager, IBinder token, String name, int displayId,
            InputChannel inputChannel) throws RemoteException {
        windowManager.createInputConsumer(token, name, displayId, inputChannel);
    }

    @Override
    public boolean destroyInputConsumer(
            IWindowManager windowManager, String name, int displayId) throws RemoteException {
        return windowManager.destroyInputConsumer(name, displayId);
    }
}
