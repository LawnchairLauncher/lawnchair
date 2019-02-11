/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.hints;

import static com.android.quickstep.hints.UiHintListenerConstants.HINTS_KEY;
import static com.android.quickstep.hints.UiHintListenerConstants.ON_HINTS_RETURNED_CODE;
import static com.android.quickstep.hints.UiInterfaceConstants.REQUEST_HINTS_CODE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.R;

import java.util.ArrayList;

public class HintsContainer extends LinearLayout {

    private static final String TAG = "HintsView";

    public static final FloatProperty<HintsContainer> HINT_VISIBILITY =
            new FloatProperty<HintsContainer>("hint_visibility") {
                @Override
                public void setValue(HintsContainer hintsContainer, float v) {
                    hintsContainer.setHintVisibility(v);
                }

                @Override
                public Float get(HintsContainer hintsContainer) {
                    return hintsContainer.mHintVisibility;
                }
            };

    private static Intent mServiceIntent =
            new Intent("com.android.systemui.action.UI_PULL_INTERFACE")
                    .setClassName(
                            "com.android.systemui.navbarhint",
                            "com.android.systemui.navbarhint.service.HintService");

    @Nullable
    private Messenger mHintServiceInterface;
    private UiHintListener mUiHintListener;
    private boolean mBound = false;
    private float mHintVisibility;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mHintServiceInterface = new Messenger(iBinder);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mHintServiceInterface = null;
            attemptBinding();
        }

        @Override
        public void onBindingDied(ComponentName componentName) {
            mHintServiceInterface = null;
            attemptBinding();
        }
    };

    public HintsContainer(Context context) {
        super(context);
    }

    public HintsContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HintsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HintsContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mUiHintListener == null) {
            mUiHintListener = new UiHintListener(this);
        }
        if (!mBound) {
            attemptBinding();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mBound) {
            getContext().unbindService(mServiceConnection);
            mBound = false;
        }
        super.onDetachedFromWindow();
    }

    public void setHintVisibility(float v) {
        if (v == 1) {
            getHints();
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
        mHintVisibility = v;
    }

    private void attemptBinding() {
        if (mBound) {
            getContext().unbindService(mServiceConnection);
            mBound = false;
        }
        boolean success = getContext().bindService(mServiceIntent,
                mServiceConnection, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
        if (success) {
            mBound = true;
        } else {
            Log.w(TAG, "Binding to hint supplier failed");
        }
    }
    
    private void sendOnHintTap(Bundle hint) {
        if (mHintServiceInterface != null) {
            Message msg = Message.obtain(null, UiInterfaceConstants.ON_HINT_TAP_CODE);
            Bundle data = new Bundle();
            data.putString(UiInterfaceConstants.HINT_ID_KEY, HintUtil.getId(hint));
            data.putInt(UiInterfaceConstants.WIDTH_PX_KEY, getWidth());
            data.putInt(UiInterfaceConstants.HEIGHT_PX_KEY, getHeight());
            data.putInt(UiInterfaceConstants.HINT_SPACE_WIDTH_PX_KEY, 0);
            data.putInt(UiInterfaceConstants.HINT_SPACE_HEIGHT_PX_KEY, 0);
            msg.setData(data);
            try {
                mHintServiceInterface.send(msg);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send hint tap", e);
            }
        }
    }

    private void getHints() {
        if (mHintServiceInterface != null) {
            try {
                Message m = Message.obtain(null, REQUEST_HINTS_CODE);
                m.replyTo = new Messenger(mUiHintListener);
                mHintServiceInterface.send(m);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }
    }

    private static class UiHintListener extends Handler {
        private HintsContainer mView;

        UiHintListener(HintsContainer v) {
            mView = v;
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ON_HINTS_RETURNED_CODE:
                    handleHints(msg);
                    break;
                default:
                    Log.e(TAG, "UiPullInterface got unrecognized code: " + msg.what);
                    break;
            }
        }

        private void handleHints(Message msg) {
            Bundle bundle = msg.getData();
            ArrayList<Bundle> hints = bundle.getParcelableArrayList(HINTS_KEY);

            if (hints != null) {
                mView.removeAllViews();

                for (Bundle hint : hints) {
                    HintView h = (HintView) LayoutInflater.from(mView.getContext()).inflate(
                            R.layout.hint, mView, false);
                    h.setHint(hint);
                    h.setOnClickListener((v) -> mView.sendOnHintTap(hint));
                    mView.addView(h);
                }
            }
        }
    }
}
