package com.google.android.libraries.launcherclient;

import android.app.Activity;
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

public class LauncherClient {
    private static AppServiceConnection sApplicationConnection;

    private final Activity mActivity;
    private OverlayCallbacks mCurrentCallbacks;
    private boolean mDestroyed;
    private boolean mIsResumed;
    private boolean mIsServiceConnected;
    private LauncherClientCallbacks mLauncherClientCallbacks;
    private ILauncherOverlay mOverlay;
    private OverlayServiceConnection mServiceConnection;
    private int mServiceConnectionOptions;
    private final Intent mServiceIntent;
    private int mServiceStatus;
    private int mState;
    private final BroadcastReceiver mUpdateReceiver;
    private WindowManager.LayoutParams mWindowAttrs;

    public LauncherClient(Activity activity, LauncherClientCallbacks callbacks, String targetPackage, boolean overlayEnabled) {
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
        mActivity = activity;
        mServiceIntent = LauncherClient.getServiceIntent(activity, targetPackage);
        mLauncherClientCallbacks = callbacks;
        mState = 0;
        mServiceConnection = new OverlayServiceConnection();
        mServiceConnectionOptions = overlayEnabled ? 3 : 2;

        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(targetPackage, PatternMatcher.PATTERN_LITERAL);
        mActivity.registerReceiver(mUpdateReceiver, filter);

        reconnect();
    }

    public LauncherClient(Activity activity, LauncherClientCallbacks callbacks, boolean overlayEnabled) {
        this(activity, callbacks, "com.google.android.googlequicksearchbox", overlayEnabled);
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
            }
            else {
                mOverlay.onPause();
            }
        }
        catch (RemoteException ignored) {

        }
    }

    private boolean connectSafely(Context context, ServiceConnection conn, int flags) {
        try {
            return context.bindService(mServiceIntent, conn, flags | Context.BIND_AUTO_CREATE);
        }
        catch (SecurityException e) {
            Log.e("DrawerOverlayClient", "Unable to connect to overlay service");
            return false;
        }
    }

    static Intent getServiceIntent(Context context, String targetPackage) {
        Uri uri = Uri.parse("app://" + context.getPackageName() + ":" + Process.myUid()).buildUpon()
            .appendQueryParameter("v", Integer.toString(0))
            .build();

        return new Intent("com.android.launcher3.WINDOW_OVERLAY")
            .setPackage(targetPackage)
            .setData(uri);
    }

    private boolean isConnected() {
        return mOverlay != null;
    }

    private void notifyStatusChanged(int status) {
        if (mServiceStatus == status) {
            return;
        }

        mServiceStatus = status;
        mLauncherClientCallbacks.onServiceStateChanged((status & 1) != 0, true);
    }

    private void removeClient(boolean removeAppConnection) {
        mDestroyed = true;
        if (mIsServiceConnected) {
            mActivity.unbindService(mServiceConnection);
            mIsServiceConnected = false;
        }
        mActivity.unregisterReceiver(mUpdateReceiver);

        if (mCurrentCallbacks != null) {
            mCurrentCallbacks.clear();
            mCurrentCallbacks = null;
        }

        if (removeAppConnection && sApplicationConnection != null) {
            mActivity.getApplicationContext().unbindService(sApplicationConnection);
            sApplicationConnection = null;
        }
    }

    private void setWindowAttrs(WindowManager.LayoutParams windowAttrs) {
        mWindowAttrs = windowAttrs;
        if (mWindowAttrs != null) {
            applyWindowToken();
        }
        else if (mOverlay != null) {
            try {
                mOverlay.windowDetached(mActivity.isChangingConfigurations());
            }
            catch (RemoteException ignored) {

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
        }
        catch (RemoteException ignored) {

        }
    }

    public void hideOverlay(boolean animate) {
        if (mOverlay == null) {
            return;
        }

        try {
            mOverlay.closeOverlay(animate ? 1 : 0);
        }
        catch (RemoteException ignored) {

        }
    }

    public final void onAttachedToWindow() {
        if (mDestroyed) {
            return;
        }

        setWindowAttrs(mActivity.getWindow().getAttributes());
    }

    public void onDestroy() {
        removeClient(!mActivity.isChangingConfigurations());
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
            }
            catch (RemoteException ignored) {

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
            }
            catch (RemoteException ignored) {

            }
        }
    }

    public void reconnect() {
        if (mDestroyed || mState != 0) {
            return;
        }

        if (sApplicationConnection != null && !sApplicationConnection.packageName.equals(mServiceIntent.getPackage())) {
            mActivity.getApplicationContext().unbindService(sApplicationConnection);
        }

        if (sApplicationConnection == null) {
            sApplicationConnection = new AppServiceConnection(mServiceIntent.getPackage());

            if (!connectSafely(mActivity.getApplicationContext(), sApplicationConnection, Context.BIND_WAIVE_PRIORITY)) {
                sApplicationConnection = null;
            }
        }

        if (sApplicationConnection != null) {
            mState = 2;

            if (!connectSafely(mActivity, mServiceConnection, Context.BIND_ADJUST_WITH_ACTIVITY)) {
                mState = 0;
            } else {
                mIsServiceConnected = true;
            }
        }

        if (mState == 0) {
            mActivity.runOnUiThread(new Runnable() {
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
        }
        catch (RemoteException ignored) {

        }
    }

    public void updateMove(float progressX) {
        if (!isConnected()) {
            return;
        }

        try {
            mOverlay.onScroll(progressX);
        }
        catch (RemoteException ignored) {

        }
    }


    final class AppServiceConnection implements ServiceConnection {
        public final String packageName;

        public AppServiceConnection(String pkg) {
            packageName = pkg;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {

        }

        public void onServiceDisconnected(ComponentName name) {
            if (name.getPackageName().equals(packageName)) {
                sApplicationConnection = null;
            }
        }
    }

    private static class OverlayCallbacks extends ILauncherOverlayCallback.Stub implements Handler.Callback {
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

        public boolean handleMessage(Message msg) {
            if (mClient == null) {
                return true;
            }

            switch (msg.what) {
                case 2:
                    if ((mClient.mServiceStatus & 1) != 0) {
                        mClient.mLauncherClientCallbacks.onOverlayScrollChanged((float) msg.obj);
                    }
                    return true;
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

        public void overlayScrollChanged(float progress) throws RemoteException {
            mUIHandler.removeMessages(2);
            Message.obtain(mUIHandler, 2, progress).sendToTarget();

            if (progress > 0) {
                hideActivityNonUI(false);
            }
        }

        public void overlayStatusChanged(int status) {
            Message.obtain(mUIHandler, 4, status, 0).sendToTarget();
        }

        public void setClient(LauncherClient client) {
            mClient = client;
            mWindowManager = client.mActivity.getWindowManager();

            Point p = new Point();
            mWindowManager.getDefaultDisplay().getRealSize(p);
            mWindowShift = Math.max(p.x, p.y);

            mWindow = client.mActivity.getWindow();
        }
    }

    private class OverlayServiceConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            mState = 1;
            mOverlay = ILauncherOverlay.Stub.asInterface(service);
            if (mWindowAttrs != null) {
                applyWindowToken();
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mState = 0;
            mOverlay = null;
            notifyStatusChanged(0);
        }
    }


}
