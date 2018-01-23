package com.google.android.libraries.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;

import java.lang.ref.WeakReference;

class OverlayServiceConnection extends BaseOverlayServiceConnection
{
    private static OverlayServiceConnection instance;
    private boolean mIsStopping;
    private ILauncherOverlay mOverlay;
    private WeakReference<GoogleNow> mGoogleNow;

    private OverlayServiceConnection(Context context) {
        super(context, Context.BIND_AUTO_CREATE | Context.BIND_WAIVE_PRIORITY);
    }

    private void cleanUp() {
        if (mIsStopping && mOverlay == null) {
            disconnect();
        }
    }

    private void setLauncherOverlay(final ILauncherOverlay overlay) {
        mOverlay = overlay;
        GoogleNow googleNow = getGoogleNow();
        if (googleNow != null) {
            googleNow.setLauncherOverlay(mOverlay);
        }
    }

    private GoogleNow getGoogleNow() {
        return mGoogleNow == null ? null : mGoogleNow.get();
    }

    static OverlayServiceConnection get(Context context) {
        if (instance == null) {
            instance = new OverlayServiceConnection(context.getApplicationContext());
        }
        return instance;
    }

    public void detach(GoogleNow toDetach, boolean disconnect) {
        GoogleNow googleNow = getGoogleNow();
        if (googleNow != null && googleNow.equals(toDetach)) {
            mGoogleNow = null;
            if (disconnect) {
                disconnect();
                if (instance == this) {
                    instance = null;
                }
            }
        }
    }

    public void changeState(boolean isStopping) {
        mIsStopping = isStopping;
        cleanUp();
    }

    public ILauncherOverlay connectGoogleNow(GoogleNow now) {
        mGoogleNow = new WeakReference<>(now);
        return mOverlay;
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder binder) {
        setLauncherOverlay(ILauncherOverlay.Stub.asInterface(binder));
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        setLauncherOverlay(null);
        cleanUp();
    }
}
