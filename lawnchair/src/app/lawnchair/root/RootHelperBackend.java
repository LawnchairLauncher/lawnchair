package app.lawnchair.root;

import android.content.Context;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

public class RootHelperBackend extends IRootHelper.Stub {

    private final Context mContext;

    public RootHelperBackend(Context context) {
        mContext = context;
    }

    private PowerManager getPowerManager() {
        return ContextCompat.getSystemService(mContext, PowerManager.class);
    }

    @Override
    public void goToSleep() {
        getPowerManager().goToSleep(SystemClock.uptimeMillis());
    }
}
