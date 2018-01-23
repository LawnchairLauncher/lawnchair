package com.google.android.libraries.launcherclient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

public class GoogleNow {
    private static int API_VERSION = -1;
    private int mFlags;
    private final Activity mActivity;
    private Bundle mLpBundle;
    private WindowManager.LayoutParams mLayoutParams;
    private int LR;
    private final IScrollCallback mOnScroll;
    private final OverlayServiceConnection mOverlayService;
    private GoogleNowCallback mNowCallback;
    private final BaseOverlayServiceConnection mServiceConnection;
    protected ILauncherOverlay mOverlay;
    private int mScrollCallbackFlags;
    private final BroadcastReceiver mBroadcastReceiver;
    private boolean mDestroyed;

    public GoogleNow(Activity activity, IScrollCallback scrollCallback, IntegerReference refInteger) {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                mServiceConnection.disconnect();
                mOverlayService.disconnect();
                loadServiceApiVersion(context);
                if ((mFlags & 0x2) != 0x0) {
                    tryConnect();
                }
            }
        };

        mFlags = 0;
        mDestroyed = false;
        mScrollCallbackFlags = 0;
        mActivity = activity;
        mOnScroll = scrollCallback;
        mServiceConnection = new BaseOverlayServiceConnection(activity, 64 | 1);
        LR = refInteger.mData;
        mOverlayService = OverlayServiceConnection.get(activity);
        mOverlay = mOverlayService.connectGoogleNow(this);

        IntentFilter intentFilter = new IntentFilter("android.intent.action.PACKAGE_ADDED");
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("com.google.android.googlequicksearchbox", 0);
        mActivity.registerReceiver(mBroadcastReceiver, intentFilter);

        if (GoogleNow.API_VERSION < 1) {
            loadServiceApiVersion(activity);
        }

        tryConnect();
        if (mActivity.getWindow() != null && mActivity.getWindow().peekDecorView() != null && mActivity.getWindow().peekDecorView().isAttachedToWindow()) {
            onAttachedToWindow();
        }
    }

    static Intent RC_getOverlayIntent(final Context context) {
        final String packageName = context.getPackageName();
        return new Intent("com.android.launcher3.WINDOW_OVERLAY")
                .setPackage("com.google.android.googlequicksearchbox"
                ).setData(Uri.parse(new StringBuilder(
                        String.valueOf(packageName).length() + 18)
                        .append("app://")
                        .append(packageName)
                        .append(":")
                        .append(Process.myUid()).toString())
                        .buildUpon().appendQueryParameter("v", Integer.toString(7)).build());
    }

    private void reloadScrollCallback(int flags) {
        if (mScrollCallbackFlags != flags) {
            mScrollCallbackFlags = flags;
            mOnScroll.onServiceStateChanged((flags & 1) != 0, (flags & 2) != 0);
        }
    }

    private void destroy(boolean b) {
        if (!mDestroyed) {
            mActivity.unregisterReceiver(mBroadcastReceiver);
        }
        mDestroyed = true;
        mServiceConnection.disconnect();
        if (mNowCallback != null) {
            mNowCallback.clear();
            mNowCallback = null;
        }
        mOverlayService.detach(this, b);
    }

    private void exchangeConfigData() {
        if (mOverlay != null) {
            try {
                if (mNowCallback == null) {
                    mNowCallback = new GoogleNowCallback();
                }

                mNowCallback.RJ(this);
                if (API_VERSION < 3) {
                    mOverlay.windowAttached(mLayoutParams, mNowCallback, LR);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("layout_params", mLayoutParams);
                    bundle.putParcelable("configuration", mActivity.getResources().getConfiguration());
                    bundle.putInt("client_options", LR); //15
                    if (mLpBundle != null) {
                        bundle.putAll(mLpBundle);
                    }
                    mOverlay.windowAttached2(bundle, mNowCallback);
                }

                if (API_VERSION >= 4) {
                    mOverlay.setActivityState(mFlags);
                } else if ((mFlags & 2) != 0) {
                    mOverlay.onResume();
                } else {
                    mOverlay.onPause();
                }
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    private boolean overlayNotNull() {
        return mOverlay != null;
    }

    private static void loadServiceApiVersion(Context context) {
        int defaultVersion = 1;
        ResolveInfo resolveService = context.getPackageManager().resolveService(RC_getOverlayIntent(context), PackageManager.GET_META_DATA);
        GoogleNow.API_VERSION = (resolveService != null && resolveService.serviceInfo.metaData != null) ?
                resolveService.serviceInfo.metaData.getInt("service.api.version", defaultVersion) :
                defaultVersion;
    }

    private void detached(WindowManager.LayoutParams layoutParams) {
        if (mLayoutParams != layoutParams) {
            mLayoutParams = layoutParams;
            if (mLayoutParams != null) {
                exchangeConfigData();
            } else if (mOverlay != null) {
                try {
                    mOverlay.windowDetached(mActivity.isChangingConfigurations());
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
                mOverlay = null;
            }
        }
    }

    public void RB(IntegerReference integer) {
        if (integer.mData != LR) {
            LR = integer.mData;
            if (mLayoutParams != null) {
                exchangeConfigData();
            }
        }
    }

    public void RD(boolean b) {
        if (mOverlay != null) {
            try {
                mOverlay.openOverlay(b ? 1 : 0);
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void endScroll() {
        if (overlayNotNull()) {
            try {
                mOverlay.endScroll();
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    void setLauncherOverlay(ILauncherOverlay iLauncherOverlay) {
        mOverlay = iLauncherOverlay;
        if (mOverlay == null) {
            reloadScrollCallback(0);
        } else if (mLayoutParams != null) {
            exchangeConfigData();
        }
    }

    public void startScroll() {
        if (overlayNotNull()) {
            try {
                mOverlay.startScroll();
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void closeOverlay(final boolean b) {
        if (mOverlay != null) {
            try {
                mOverlay.closeOverlay(b ? 1 : 0);
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void redraw(Bundle lp) {
        mLpBundle = lp;
        if (mLayoutParams != null && GoogleNow.API_VERSION >= 7) {
            exchangeConfigData();
        }
    }

    public void tryConnect() {
        if (!mDestroyed) {
            if (!mOverlayService.tryConnect() || !mServiceConnection.tryConnect()) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reloadScrollCallback(0);
                    }
                });
            }
        }
    }

    public void onScroll(final float progress) {
        if (overlayNotNull()) {
            try {
                mOverlay.onScroll(progress);
            } catch (RemoteException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void onAttachedToWindow() {
        if (!mDestroyed) {
            detached(mActivity.getWindow().getAttributes());
        }
    }

    public void onDestroy() {
        destroy(!mActivity.isChangingConfigurations());
    }

    public final void onDetachedFromWindow() {
        if (!mDestroyed) {
            detached(null);
        }
    }

    public void onPause() {
        if (!mDestroyed) {
            mFlags &= -3;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    if (GoogleNow.API_VERSION >= 4) {
                        mOverlay.setActivityState(mFlags);
                    } else {
                        mOverlay.onPause();
                    }
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void onResume() throws RemoteException {
        if (!mDestroyed) {
            mFlags |= 0x2;
            if (mOverlay != null && mLayoutParams != null) {
                if (GoogleNow.API_VERSION >= 4) {
                    mOverlay.setActivityState(mFlags);
                } else {
                    mOverlay.onResume();
                }
            }
        }
    }

    public void onStart() {
        if (!mDestroyed) {
            mOverlayService.changeState(false);
            tryConnect();
            mFlags |= 0x1;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    mOverlay.setActivityState(mFlags);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public void onStop() {
        if (!mDestroyed) {
            mOverlayService.changeState(true);
            mServiceConnection.disconnect();
            mFlags &= 0xFFFFFFFE;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    mOverlay.setActivityState(mFlags);
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    public boolean startSearch(byte[] array, Bundle bundle) {
        if (GoogleNow.API_VERSION >= 6 && mOverlay != null) {
            try {
                return mOverlay.startSearch(array, bundle);
            } catch (Throwable ex) {
                Log.e("DrawerOverlayClient", "Error starting session for search", ex);
            }
        }
        return false;
    }

    class GoogleNowCallback extends ILauncherOverlayCallback.Stub implements Handler.Callback {
        private boolean mHasFocus = false;
        private final Handler mHandler = new Handler(Looper.getMainLooper(), this);
        private int mMaxDimension;
        private GoogleNow mGoogleNow;
        private WindowManager mWindowManager;
        private Window mWindow;

        private void setFocus(boolean hasFocus) {
            if (mHasFocus != hasFocus) {
                mHasFocus = hasFocus;
            }
        }

        public void RJ(GoogleNow googleNow) {
            mGoogleNow = googleNow;
            mWindowManager = googleNow.mActivity.getWindowManager();
            Point point = new Point();
            mWindowManager.getDefaultDisplay().getRealSize(point);
            mMaxDimension = -Math.max(point.x, point.y);
            mWindow = googleNow.mActivity.getWindow();
        }

        public void clear() {
            mGoogleNow = null;
            mWindowManager = null;
            mWindow = null;
        }

        public boolean handleMessage(Message message) {
            if (mGoogleNow == null) {
                return true;
            }
            switch (message.what) {
                case 2: {
                    if ((mGoogleNow.mScrollCallbackFlags & 1) != 0) {
                        mGoogleNow.mOnScroll.onOverlayScrollChanged((float) message.obj);
                    }
                    return true;
                }
                case 3: {
                    WindowManager.LayoutParams attributes = mWindow.getAttributes();
                    if (!(boolean) message.obj) {
                        attributes.x = 0;
                        attributes.flags &= -513;
                    } else {
                        attributes.x = mMaxDimension;
                        attributes.flags |= 512;
                    }
                    mWindowManager.updateViewLayout(mWindow.getDecorView(), attributes);
                    return true;
                }
                case 4: {
                    mGoogleNow.reloadScrollCallback(message.arg1);
                    if (mGoogleNow.mOnScroll instanceof ISerializableScrollCallback) {
                        ((ISerializableScrollCallback) mGoogleNow.mOnScroll).setPersistentFlags(message.arg1);
                    }
                    return true;
                }
                default: {
                    return false;
                }
            }
        }

        public void overlayScrollChanged(float progress) {
            int messageId = 2;
            mHandler.removeMessages(messageId);
            Message.obtain(mHandler, messageId, progress).sendToTarget();
            if (progress > 0f) {
                setFocus(false);
            }
        }

        public void overlayStatusChanged(int status) {
            Message.obtain(mHandler, 4, status, 0).sendToTarget();
        }
    }

    public static class IntegerReference {
        private final int mData;

        public IntegerReference(int data) {
            mData = data;
        }
    }
}
