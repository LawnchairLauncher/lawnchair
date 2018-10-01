package com.google.android.libraries.gsa.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;

import com.google.android.libraries.launcherclient.ILauncherOverlay;
import java.lang.ref.WeakReference;

public class LauncherClientService extends BaseClientService {
    public static LauncherClientService sInstance;
    public ILauncherOverlay mOverlay;
    public WeakReference<LauncherClient> mClient;
    private boolean mStopped;

    static LauncherClientService getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LauncherClientService(context.getApplicationContext());
        }
        return sInstance;
    }

    private LauncherClientService(Context context) {
        super(context, Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY);
    }

    public final void setStopped(boolean stopped) {
        mStopped = stopped;
        cleanUp();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        setClient(ILauncherOverlay.Stub.asInterface(service));
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        setClient(null);
        cleanUp();
    }

    private void cleanUp() {
        if (mStopped && mOverlay == null) {
            disconnect();
        }
    }

    private void setClient(ILauncherOverlay overlay) {
        mOverlay = overlay;
        LauncherClient client = getClient();
        if (client != null) {
            client.setOverlay(mOverlay);
        }
    }

    public final LauncherClient getClient() {
        return mClient != null ? mClient.get() : null;
    }
}
