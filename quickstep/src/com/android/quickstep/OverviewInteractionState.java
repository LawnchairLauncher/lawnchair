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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.UiThreadHelper;
import com.android.systemui.shared.recents.ISystemUiProxy;

import androidx.annotation.WorkerThread;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Sets alpha for the back button
 */
public class OverviewInteractionState {

    private static final String TAG = "OverviewFlags";

    // We do not need any synchronization for this variable as its only written on UI thread.
    public static final MainThreadInitializedObject<OverviewInteractionState> INSTANCE =
            new MainThreadInitializedObject<>((c) -> new OverviewInteractionState(c));

    private static final int MSG_SET_PROXY = 200;
    private static final int MSG_SET_BACK_BUTTON_ALPHA = 201;

    private final Context mContext;
    private final Handler mUiHandler;
    private final Handler mBgHandler;

    // These are updated on the background thread
    private ISystemUiProxy mISystemUiProxy;
    private float mBackButtonAlpha = 1;

    private OverviewInteractionState(Context context) {
        mContext = context;

        // Data posted to the uihandler will be sent to the bghandler. Data is sent to uihandler
        // because of its high send frequency and data may be very different than the previous value
        // For example, send back alpha on uihandler to avoid flickering when setting its visibility
        mUiHandler = new Handler(this::handleUiMessage);
        mBgHandler = new Handler(UiThreadHelper.getBackgroundLooper(), this::handleBgMessage);
    }

    public float getBackButtonAlpha() {
        return mBackButtonAlpha;
    }

    public void setBackButtonAlpha(float alpha, boolean animate) {
        if (QuickStepContract.isLegacyMode(SysUINavigationMode.getMode(mContext).resValue)) {
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
        }
        return true;
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
}
