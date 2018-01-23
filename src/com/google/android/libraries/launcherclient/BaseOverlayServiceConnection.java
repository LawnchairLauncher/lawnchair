package com.google.android.libraries.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

class BaseOverlayServiceConnection implements ServiceConnection {
    private boolean mConnected;
    private final Context mContext;
    private final int mFlags;

    BaseOverlayServiceConnection(Context context, int flags) {
        mContext = context;
        mFlags = flags;
    }

    boolean tryConnect() {
        if (!mConnected) {
            Intent intent = GoogleNow.RC_getOverlayIntent(mContext);
            try {
                mConnected = mContext.bindService(intent, this, mFlags);
            } catch (SecurityException ex) {
                Log.e("LauncherClient", "Unable to connect to overlay service", ex);
            }
        }
        return mConnected;
    }

    void disconnect() {
        if (mConnected) {
            mContext.unbindService(this);
            mConnected = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
    }
}
