package com.google.android.libraries.launcherclient;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PatternMatcher;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.google.firebase.crash.FirebaseCrash;

import ch.deletescape.lawnchair.Launcher;

public class LauncherClient {
    private static AppServiceConnection sApplicationConnection;

    private final Launcher mLauncher;
    private OverlayCallbacks mCurrentCallbacks;
    private boolean mDestroyed;
    private boolean mIsResumed;
    private boolean mIsServiceConnected;
    private ILauncherOverlay mOverlay;
    private OverlayServiceConnection mServiceConnection;
    private int mServiceConnectionOptions;
    private final Intent mServiceIntent;
    private int mServiceStatus;
    private int mState;
    private final BroadcastReceiver mUpdateReceiver;
    private WindowManager.LayoutParams mWindowAttrs;

    public LauncherClient(Launcher launcher, String targetPackage, boolean overlayEnabled) {
        mUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                reconnect();
            }
        };
        mIsResumed = false;
        mDestroyed = false;
        mIsServiceConnected = false;
        mServiceStatus = -1;
        mLauncher = launcher;
        mServiceIntent = LauncherClient.getServiceIntent(launcher, targetPackage);
        mState = 0;
        mServiceConnection = new OverlayServiceConnection();
        mServiceConnectionOptions = overlayEnabled ? 3 : 2;

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(targetPackage, PatternMatcher.PATTERN_LITERAL);
        mLauncher.registerReceiver(mUpdateReceiver, filter);

        reconnect();
    }

    public LauncherClient(Launcher launcher, boolean overlayEnabled) {
        this(launcher, "com.google.android.googlequicksearchbox", overlayEnabled);
    }

    private void applyWindowToken() {
        if (mOverlay == null) {
            return;
        }

        try {
            if (mCurrentCallbacks == null) {
                mCurrentCallbacks = new OverlayCallbacks();
            }

            mCurrentCallbacks.setClient(this);
            mOverlay.windowAttached(mWindowAttrs, mCurrentCallbacks, mServiceConnectionOptions);

            if (mIsResumed) {
                mOverlay.onResume();
            } else {
                mOverlay.onPause();
            }
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }

    private boolean connectSafely(Context context, ServiceConnection conn, int flags) {
        try {
            return context.bindService(mServiceIntent, conn, flags | Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e("DrawerOverlayClient", "Unable to connect to overlay service");
            FirebaseCrash.report(e);
            return false;
        }
    }

    static Intent getServiceIntent(Context context, String targetPackage) {
        Uri uri = Uri.parse("app://" + context.getPackageName() + ":" + Process.myUid()).buildUpon()
                .appendQueryParameter("v", Integer.toString(5))
                .build();

        return new Intent("com.android.launcher3.WINDOW_OVERLAY")
                .setPackage(targetPackage)
                .setData(uri);
    }

    public boolean isConnected() {
        return mOverlay != null;
    }

    private void notifyStatusChanged(int status) {
        if (mServiceStatus == status) {
            return;
        }

        mServiceStatus = status;
    }

    private void removeClient(boolean removeAppConnection) {
        mDestroyed = true;
        if (mIsServiceConnected) {
            mLauncher.unbindService(mServiceConnection);
            mIsServiceConnected = false;
        }
        mLauncher.unregisterReceiver(mUpdateReceiver);

        if (mCurrentCallbacks != null) {
            mCurrentCallbacks.clear();
            mCurrentCallbacks = null;
        }

        if (removeAppConnection && sApplicationConnection != null) {
            mLauncher.getApplicationContext().unbindService(sApplicationConnection);
            sApplicationConnection = null;
        }
    }

    private void setWindowAttrs(WindowManager.LayoutParams windowAttrs) {
        mWindowAttrs = windowAttrs;
        if (mWindowAttrs != null) {
            applyWindowToken();
        } else if (mOverlay != null) {
            try {
                mOverlay.windowDetached(mLauncher.isChangingConfigurations());
            } catch (RemoteException ignored) {
                FirebaseCrash.report(ignored);
            }
            mOverlay = null;
        }
    }

    public void endMove() {
        if (!isConnected()) {
            return;
        }

        try {
            mOverlay.endScroll();
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }

    public void hideOverlay(boolean animate) {
        if (mOverlay == null) {
            return;
        }

        try {
            mOverlay.closeOverlay(animate ? 1 : 0);
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }

    public void openOverlay() {
        if (mOverlay == null) {
            return;
        }

        try {
            mOverlay.openOverlay(0);
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }

    public final void onAttachedToWindow() {
        if (mDestroyed) {
            return;
        }

        setWindowAttrs(mLauncher.getWindow().getAttributes());
    }

    public void onDestroy() {
        removeClient(!mLauncher.isChangingConfigurations());
    }

    public final void onDetachedFromWindow() {
        if (mDestroyed) {
            return;
        }

        setWindowAttrs(null);
    }

    public void onPause() {
        if (mDestroyed) {
            return;
        }

        mIsResumed = false;
        if (mOverlay != null && mWindowAttrs != null) {
            try {
                mOverlay.onPause();
            } catch (RemoteException ignored) {
                FirebaseCrash.report(ignored);
            }
        }
    }

    public void onResume() {
        if (mDestroyed) {
            return;
        }

        reconnect();
        mIsResumed = true;
        if (mOverlay != null && mWindowAttrs != null) {
            try {
                mOverlay.onResume();
            } catch (RemoteException ignored) {
                FirebaseCrash.report(ignored);
            }
        }
    }

    public void reconnect() {
        if (mDestroyed || mState != 0) {
            return;
        }

        if (sApplicationConnection != null && !sApplicationConnection.packageName.equals(mServiceIntent.getPackage())) {
            mLauncher.getApplicationContext().unbindService(sApplicationConnection);
        }

        if (sApplicationConnection == null) {
            sApplicationConnection = new AppServiceConnection(mServiceIntent.getPackage());

            if (!connectSafely(mLauncher.getApplicationContext(), sApplicationConnection, Context.BIND_WAIVE_PRIORITY)) {
                sApplicationConnection = null;
            }
        }

        if (sApplicationConnection != null) {
            mState = 2;

            if (!connectSafely(mLauncher, mServiceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)) {
                mState = 0;
            } else {
                mIsServiceConnected = true;
            }
        }

        if (mState == 0) {
            mLauncher.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyStatusChanged(0);
                }
            });
        }
    }

    public void startMove() {
        if (!isConnected()) {
            return;
        }

        try {
            mOverlay.startScroll();
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }

    public void updateMove(float progressX) {
        if (!isConnected()) {
            return;
        }

        try {
            mOverlay.onScroll(progressX);
        } catch (RemoteException ignored) {
            FirebaseCrash.report(ignored);
        }
    }


    final class AppServiceConnection implements ServiceConnection {
        public final String packageName;

        public AppServiceConnection(String pkg) {
            packageName = pkg;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (name.getPackageName().equals(packageName)) {
                sApplicationConnection = null;
            }
        }
    }

    private class OverlayCallbacks extends ILauncherOverlayCallback.Stub implements Handler.Callback {
        private LauncherClient mClient;
        private final Handler mUIHandler;
        private Window mWindow;
        private boolean mWindowHidden;
        private WindowManager mWindowManager;
        private int mWindowShift;

        public OverlayCallbacks() {
            mWindowHidden = false;
            mUIHandler = new Handler(Looper.getMainLooper(), this);
        }

        private void hideActivityNonUI(boolean isHidden) {
            if (mWindowHidden != isHidden) {
                mWindowHidden = isHidden;
            }
        }

        public void clear() {
            mClient = null;
            mWindowManager = null;
            mWindow = null;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (mClient == null) {
                return true;
            }

            switch (msg.what) {
                case 3:
                    WindowManager.LayoutParams attrs = mWindow.getAttributes();
                    if ((boolean) msg.obj) {
                        attrs.x = mWindowShift;
                        attrs.flags = attrs.flags | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
                    }
                    mWindowManager.updateViewLayout(mWindow.getDecorView(), attrs);
                    return true;
                case 4:
                    mClient.notifyStatusChanged(msg.arg1);
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void overlayScrollChanged(final float progress) throws RemoteException {
            mUIHandler.removeMessages(2);
            Message.obtain(mUIHandler, 2, progress).sendToTarget();

            if (progress > 0) {
                hideActivityNonUI(false);
            }

            mLauncher.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLauncher.getWorkspace().onOverlayScrollChanged(progress);
                }
            });
        }

        @Override
        public void overlayStatusChanged(int status) {
            Message.obtain(mUIHandler, 4, status, 0).sendToTarget();
        }

        public void setClient(LauncherClient client) {
            mClient = client;
            mWindowManager = client.mLauncher.getWindowManager();

            Point p = new Point();
            mWindowManager.getDefaultDisplay().getRealSize(p);
            mWindowShift = Math.max(p.x, p.y);

            mWindow = client.mLauncher.getWindow();
        }
    }

    private class OverlayServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mState = 1;
            mOverlay = ILauncherOverlay.Stub.asInterface(service);
            if (mWindowAttrs != null) {
                applyWindowToken();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mState = 0;
            mOverlay = null;
            notifyStatusChanged(0);
        }
    }
}
