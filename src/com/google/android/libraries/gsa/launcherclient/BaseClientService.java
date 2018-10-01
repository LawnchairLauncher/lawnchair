package com.google.android.libraries.gsa.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

public class BaseClientService implements ServiceConnection {
    private boolean mConnected;
    private final Context mContext;
    private final int mFlags;
    private final ServiceConnection mBridge;

    BaseClientService(Context context, int flags) {
        mContext = context;
        mFlags = flags;
        mBridge = LauncherClient.BRIDGE_USE
                ? new LauncherClientBridge(this)
                : this;
    }

    public final boolean connect() {
        if (!mConnected) {
            try {
                mConnected = mContext.bindService(LauncherClient.getIntent(mContext,
                        LauncherClient.BRIDGE_USE), mBridge, mFlags);
            } catch (Throwable e) {
                Log.e("LauncherClient", "Unable to connect to overlay service", e);
            }
        }
        return mConnected;
    }

    public final void disconnect() {
        if (mConnected) {
            mContext.unbindService(mBridge);
            mConnected = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
}
