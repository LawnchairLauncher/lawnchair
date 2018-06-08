/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep;

import static com.android.systemui.shared.system.NavigationBarCompat.FLAG_DISABLE_QUICK_SCRUB;
import static com.android.systemui.shared.system.NavigationBarCompat.FLAG_DISABLE_SWIPE_UP;
import static com.android.systemui.shared.system.NavigationBarCompat.FLAG_SHOW_OVERVIEW_BUTTON;
import static com.android.systemui.shared.system.SettingsCompat.SWIPE_UP_SETTING_NAME;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.WorkerThread;
import android.util.Log;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.util.UiThreadHelper;
import com.android.systemui.shared.recents.ISystemUiProxy;

import java.util.concurrent.ExecutionException;

/**
 * Sets overview interaction flags, such as:
 *
 *   - FLAG_DISABLE_QUICK_SCRUB
 *   - FLAG_DISABLE_SWIPE_UP
 *   - FLAG_SHOW_OVERVIEW_BUTTON
 *
 * @see com.android.systemui.shared.system.NavigationBarCompat.InteractionType and associated flags.
 */
public class OverviewInteractionState {

    private static final String TAG = "OverviewFlags";

    private static final String HAS_ENABLED_QUICKSTEP_ONCE = "launcher.has_enabled_quickstep_once";
    private static final String SWIPE_UP_SETTING_AVAILABLE_RES_NAME =
            "config_swipe_up_gesture_setting_available";
    private static final String SWIPE_UP_ENABLED_DEFAULT_RES_NAME =
            "config_swipe_up_gesture_default";

    // We do not need any synchronization for this variable as its only written on UI thread.
    private static OverviewInteractionState INSTANCE;

    public static OverviewInteractionState getInstance(final Context context) {
        if (INSTANCE == null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                INSTANCE = new OverviewInteractionState(context.getApplicationContext());
            } else {
                try {
                    return new MainThreadExecutor().submit(
                            () -> OverviewInteractionState.getInstance(context)).get();
                } catch (InterruptedException|ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return INSTANCE;
    }

    private static final int MSG_SET_PROXY = 200;
    private static final int MSG_SET_BACK_BUTTON_ALPHA = 201;
    private static final int MSG_SET_SWIPE_UP_ENABLED = 202;

    private final SwipeUpGestureEnabledSettingObserver mSwipeUpSettingObserver;

    private final Context mContext;
    private final Handler mUiHandler;
    private final Handler mBgHandler;

    // These are updated on the background thread
    private ISystemUiProxy mISystemUiProxy;
    private boolean mSwipeUpEnabled = true;
    private float mBackButtonAlpha = 1;

    private Runnable mOnSwipeUpSettingChangedListener;

    private OverviewInteractionState(Context context) {
        mContext = context;

        // Data posted to the uihandler will be sent to the bghandler. Data is sent to uihandler
        // because of its high send frequency and data may be very different than the previous value
        // For example, send back alpha on uihandler to avoid flickering when setting its visibility
        mUiHandler = new Handler(this::handleUiMessage);
        mBgHandler = new Handler(UiThreadHelper.getBackgroundLooper(), this::handleBgMessage);

        if (getSystemBooleanRes(SWIPE_UP_SETTING_AVAILABLE_RES_NAME)) {
            mSwipeUpSettingObserver = new SwipeUpGestureEnabledSettingObserver(mUiHandler,
                    context.getContentResolver());
            mSwipeUpSettingObserver.register();
        } else {
            mSwipeUpSettingObserver = null;
            mSwipeUpEnabled = getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME);
        }
    }

    public boolean isSwipeUpGestureEnabled() {
        return mSwipeUpEnabled;
    }

    public float getBackButtonAlpha() {
        return mBackButtonAlpha;
    }

    public void setBackButtonAlpha(float alpha, boolean animate) {
        if (!mSwipeUpEnabled) {
            alpha = 1;
        }
        mUiHandler.removeMessages(MSG_SET_BACK_BUTTON_ALPHA);
        mUiHandler.obtainMessage(MSG_SET_BACK_BUTTON_ALPHA, animate ? 1 : 0, 0, alpha)
                .sendToTarget();
    }

    public void setSystemUiProxy(ISystemUiProxy proxy) {
        mBgHandler.obtainMessage(MSG_SET_PROXY, proxy).sendToTarget();
    }

    private boolean handleUiMessage(Message msg) {
        if (msg.what == MSG_SET_BACK_BUTTON_ALPHA) {
            mBackButtonAlpha = (float) msg.obj;
        }
        mBgHandler.obtainMessage(msg.what, msg.arg1, msg.arg2, msg.obj).sendToTarget();
        return true;
    }

    private boolean handleBgMessage(Message msg) {
        switch (msg.what) {
            case MSG_SET_PROXY:
                mISystemUiProxy = (ISystemUiProxy) msg.obj;
                break;
            case MSG_SET_BACK_BUTTON_ALPHA:
                applyBackButtonAlpha((float) msg.obj, msg.arg1 == 1);
                return true;
            case MSG_SET_SWIPE_UP_ENABLED:
                mSwipeUpEnabled = msg.arg1 != 0;
                resetHomeBounceSeenOnQuickstepEnabledFirstTime();

                if (mOnSwipeUpSettingChangedListener != null) {
                    mOnSwipeUpSettingChangedListener.run();
                }
                break;
        }
        applyFlags();
        return true;
    }

    public void setOnSwipeUpSettingChangedListener(Runnable listener) {
        mOnSwipeUpSettingChangedListener = listener;
    }

    @WorkerThread
    private void applyFlags() {
        if (mISystemUiProxy == null) {
            return;
        }

        int flags = 0;
        if (!mSwipeUpEnabled) {
            flags = FLAG_DISABLE_SWIPE_UP | FLAG_DISABLE_QUICK_SCRUB | FLAG_SHOW_OVERVIEW_BUTTON;
        }
        try {
            mISystemUiProxy.setInteractionState(flags);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to update overview interaction flags", e);
        }
    }

    @WorkerThread
    private void applyBackButtonAlpha(float alpha, boolean animate) {
        if (mISystemUiProxy == null) {
            return;
        }
        try {
            mISystemUiProxy.setBackButtonAlpha(alpha, animate);
        } catch (RemoteException e) {
            Log.w(TAG, "Unable to update overview back button alpha", e);
        }
    }

    private class SwipeUpGestureEnabledSettingObserver extends ContentObserver {
        private Handler mHandler;
        private ContentResolver mResolver;
        private final int defaultValue;

        SwipeUpGestureEnabledSettingObserver(Handler handler, ContentResolver resolver) {
            super(handler);
            mHandler = handler;
            mResolver = resolver;
            defaultValue = getSystemBooleanRes(SWIPE_UP_ENABLED_DEFAULT_RES_NAME) ? 1 : 0;
        }

        public void register() {
            mResolver.registerContentObserver(Settings.Secure.getUriFor(SWIPE_UP_SETTING_NAME),
                    false, this);
            mSwipeUpEnabled = getValue();
            resetHomeBounceSeenOnQuickstepEnabledFirstTime();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mHandler.removeMessages(MSG_SET_SWIPE_UP_ENABLED);
            mHandler.obtainMessage(MSG_SET_SWIPE_UP_ENABLED, getValue() ? 1 : 0, 0).sendToTarget();
        }

        private boolean getValue() {
            return Settings.Secure.getInt(mResolver, SWIPE_UP_SETTING_NAME, defaultValue) == 1;
        }
    }

    private boolean getSystemBooleanRes(String resName) {
        Resources res = Resources.getSystem();
        int resId = res.getIdentifier(resName, "bool", "android");

        if (resId != 0) {
            return res.getBoolean(resId);
        } else {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
            return false;
        }
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (mSwipeUpEnabled && !Utilities.getPrefs(mContext).getBoolean(
                HAS_ENABLED_QUICKSTEP_ONCE, true)) {
            Utilities.getPrefs(mContext).edit()
                    .putBoolean(HAS_ENABLED_QUICKSTEP_ONCE, true)
                    .putBoolean(DiscoveryBounce.HOME_BOUNCE_SEEN, false)
                    .apply();
        }
    }
}
