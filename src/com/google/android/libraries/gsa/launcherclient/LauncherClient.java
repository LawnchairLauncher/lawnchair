package com.google.android.libraries.gsa.launcherclient;

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
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import com.google.android.libraries.launcherclient.ILauncherOverlay;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;
import java.lang.ref.WeakReference;

public class LauncherClient {
    public final static boolean BRIDGE_USE = true;
    public final static String BRIDGE_PACKAGE = "com.google.android.apps.nexuslauncher";

    private static int apiVersion = -1;

    private ILauncherOverlay mOverlay;
    private final IScrollCallback mScrollCallback;

    public final BaseClientService mBaseService;
    public final LauncherClientService mLauncherService;

    public final BroadcastReceiver googleInstallListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBaseService.disconnect();
            mLauncherService.disconnect();
            LauncherClient.loadApiVersion(context);
            if ((mActivityState & 2) != 0) {
                reconnect();
            }
        }
    };

    private int mActivityState = 0;
    private int mServiceState = 0;
    public int mFlags;

    public LayoutParams mLayoutParams;
    public OverlayCallback mOverlayCallback;
    public final Activity mActivity;

    public boolean mDestroyed = false;
    private Bundle mLayoutBundle;

    public class OverlayCallback extends ILauncherOverlayCallback.Stub implements Callback {
        public LauncherClient mClient;
        private final Handler mUIHandler = new Handler(Looper.getMainLooper(), this);
        public Window mWindow;
        private boolean mWindowHidden = false;
        public WindowManager mWindowManager;
        int mWindowShift;

        @Override
        public final void overlayScrollChanged(float f) {
            mUIHandler.removeMessages(2);
            Message.obtain(mUIHandler, 2, f).sendToTarget();
            if (f > 0f && mWindowHidden) {
                mWindowHidden = false;
            }
        }

        @Override
        public final void overlayStatusChanged(int i) {
            Message.obtain(mUIHandler, 4, i, 0).sendToTarget();
        }

        @Override
        public boolean handleMessage(Message message) {
            if (mClient == null) {
                return true;
            }

            switch (message.what) {
                case 2:
                    if ((mClient.mServiceState & 1) != 0) {
                        float floatValue = (float) message.obj;
                        mClient.mScrollCallback.onOverlayScrollChanged(floatValue);
                    }
                    return true;
                case 3:
                    WindowManager.LayoutParams attributes = mWindow.getAttributes();
                    if ((Boolean) message.obj) {
                        attributes.x = mWindowShift;
                        attributes.flags |= 512;
                    } else {
                        attributes.x = 0;
                        attributes.flags &= -513;
                    }
                    mWindowManager.updateViewLayout(mWindow.getDecorView(), attributes);
                    return true;
                case 4:
                    mClient.setServiceState(message.arg1);
                    if (mClient.mScrollCallback instanceof ISerializableScrollCallback) {
                        ((ISerializableScrollCallback) mClient.mScrollCallback).setPersistentFlags(message.arg1);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public LauncherClient(Activity activity, IScrollCallback scrollCallback, StaticInteger flags) {
        mActivity = activity;
        mScrollCallback = scrollCallback;
        mBaseService = new BaseClientService(activity, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        mFlags = flags.mData;

        mLauncherService = LauncherClientService.getInstance(activity);
        mLauncherService.mClient = new WeakReference<>(this);
        mOverlay = mLauncherService.mOverlay;

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("com.google.android.googlequicksearchbox", 0);
        mActivity.registerReceiver(googleInstallListener, intentFilter);

        if (apiVersion <= 0) {
            loadApiVersion(activity);
        }

        reconnect();
        if (mActivity.getWindow() != null &&
                mActivity.getWindow().peekDecorView() != null &&
                mActivity.getWindow().peekDecorView().isAttachedToWindow()) {
            onAttachedToWindow();
        }
    }

    public final void onAttachedToWindow() {
        if (!mDestroyed) {
            setLayoutParams(mActivity.getWindow().getAttributes());
        }
    }

    public final void onDetachedFromWindow() {
        if (!mDestroyed) {
            setLayoutParams(null);
        }
    }

    public final void onResume() {
        if (!mDestroyed) {
            mActivityState |= 2;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    if (apiVersion < 4) {
                        mOverlay.onResume();
                    } else {
                        mOverlay.setActivityState(mActivityState);
                    }
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onPause() {
        if (!mDestroyed) {
            mActivityState &= -3;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    if (apiVersion < 4) {
                        mOverlay.onPause();
                    } else {
                        mOverlay.setActivityState(mActivityState);
                    }
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onStart() {
        if (!mDestroyed) {
            mLauncherService.setStopped(false);
            reconnect();
            mActivityState |= 1;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    mOverlay.setActivityState(mActivityState);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onStop() {
        if (!mDestroyed) {
            mLauncherService.setStopped(true);
            mBaseService.disconnect();
            mActivityState &= -2;
            if (!(mOverlay == null || mLayoutParams == null)) {
                try {
                    mOverlay.setActivityState(mActivityState);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    private void reconnect() {
        if (!mDestroyed && (!mLauncherService.connect() || !mBaseService.connect())) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setServiceState(0);
                }
            });
        }
    }

    public final void setLayoutParams(LayoutParams layoutParams) {
        if (mLayoutParams != layoutParams) {
            mLayoutParams = layoutParams;
            if (mLayoutParams != null) {
                exchangeConfig();
            } else if (mOverlay != null) {
                try {
                    mOverlay.windowDetached(mActivity.isChangingConfigurations());
                } catch (RemoteException ignored) {
                }
                mOverlay = null;
            }
        }
    }

    public final void exchangeConfig() {
        if (mOverlay != null) {
            try {
                if (mOverlayCallback == null) {
                    mOverlayCallback = new OverlayCallback();
                }
                OverlayCallback overlayCallback = mOverlayCallback;
                overlayCallback.mClient = this;
                overlayCallback.mWindowManager = mActivity.getWindowManager();
                Point point = new Point();
                overlayCallback.mWindowManager.getDefaultDisplay().getRealSize(point);
                overlayCallback.mWindowShift = -Math.max(point.x, point.y);
                overlayCallback.mWindow = mActivity.getWindow();
                if (apiVersion < 3) {
                    mOverlay.windowAttached(mLayoutParams, mOverlayCallback, mFlags);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("layout_params", mLayoutParams);
                    bundle.putParcelable("configuration", mActivity.getResources().getConfiguration());
                    bundle.putInt("client_options", mFlags);
                    if (mLayoutBundle != null) {
                        bundle.putAll(mLayoutBundle);
                    }
                    mOverlay.windowAttached2(bundle, mOverlayCallback);
                }
                if (apiVersion >= 4) {
                    mOverlay.setActivityState(mActivityState);
                } else if ((mActivityState & 2) != 0) {
                    mOverlay.onResume();
                } else {
                    mOverlay.onPause();
                }
            } catch (RemoteException ignored) {
            }
        }
    }

    private boolean isConnected() {
        return mOverlay != null;
    }

    public final void startScroll() {
        if (isConnected()) {
            try {
                mOverlay.startScroll();
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void endScroll() {
        if (isConnected()) {
            try {
                mOverlay.endScroll();
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void setScroll(float f) {
        if (isConnected()) {
            try {
                mOverlay.onScroll(f);
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void hideOverlay(boolean feedRunning) {
        if (mOverlay != null) {
            try {
                mOverlay.closeOverlay(feedRunning ? 1 : 0);
            } catch (RemoteException ignored) {
            }
        }
    }

    /* Only used for accessibility
    public final void showOverlay(boolean feedRunning) {
        if (mOverlay != null) {
            try {
                mOverlay.openOverlay(feedRunning ? 1 : 0);
            } catch (RemoteException ignored) {
            }
        }
    }
    */

    public final boolean startSearch(byte[] bArr, Bundle bundle) {
        if (apiVersion >= 6 && mOverlay != null) {
            try {
                return mOverlay.startSearch(bArr, bundle);
            } catch (Throwable e) {
                Log.e("DrawerOverlayClient", "Error starting session for search", e);
            }
        }
        return false;
    }

    public final void redraw(Bundle layoutBundle) {
        mLayoutBundle = layoutBundle;
        if (mLayoutParams != null && apiVersion >= 7) {
            exchangeConfig();
        }
    }

    final void setOverlay(ILauncherOverlay overlay) {
        mOverlay = overlay;
        if (mOverlay == null) {
            setServiceState(0);
        } else if (mLayoutParams != null) {
            exchangeConfig();
        }
    }

    private void setServiceState(int serviceState) {
        if (mServiceState != serviceState) {
            mServiceState = serviceState;
            mScrollCallback.onServiceStateChanged((serviceState & 1) != 0);
        }
    }

    static Intent getIntent(Context context, boolean proxy) {
        String pkg = context.getPackageName();
        return new Intent("com.android.launcher3.WINDOW_OVERLAY")
                .setPackage(proxy ? BRIDGE_PACKAGE : "com.google.android.googlequicksearchbox")
                .setData(Uri.parse(new StringBuilder(pkg.length() + 18)
                            .append("app://")
                            .append(pkg)
                            .append(":")
                            .append(Process.myUid())
                            .toString())
                        .buildUpon()
                        .appendQueryParameter("v", Integer.toString(7))
                        .appendQueryParameter("cv", Integer.toString(9))
                        .build());
    }

    private static void loadApiVersion(Context context) {
        ResolveInfo resolveService = context.getPackageManager().resolveService(getIntent(context, false), PackageManager.GET_META_DATA);
        apiVersion = resolveService == null || resolveService.serviceInfo.metaData == null ?
                1 :
                resolveService.serviceInfo.metaData.getInt("service.api.version", 1);
    }
}
