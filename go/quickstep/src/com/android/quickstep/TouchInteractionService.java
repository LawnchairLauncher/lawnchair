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
package com.android.quickstep;

import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    private static final String TAG = "GoTouchInteractionService";
    private boolean mIsUserUnlocked;
    private BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                initWhenUserUnlocked();
            }
        }
    };

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onActiveNavBarRegionChanges(Region region) throws RemoteException { }

        @Override
        public void onInitialize(Bundle bundle) throws RemoteException {
            ISystemUiProxy iSystemUiProxy = ISystemUiProxy.Stub
                    .asInterface(bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            mRecentsModel.setSystemUiProxy(iSystemUiProxy);
        }

        @Override
        public void onOverviewToggle() {
            if (mIsUserUnlocked) {
                mOverviewCommandHelper.onOverviewToggle();
            }
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            if (mIsUserUnlocked) {
                mOverviewCommandHelper.onOverviewShown(triggeredFromAltTab);
            }
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (mIsUserUnlocked && triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab hides the overview and ends at the target app
                mOverviewCommandHelper.onOverviewHidden();
            }
        }

        @Override
        public void onTip(int actionType, int viewType) {
            if (mIsUserUnlocked) {
                mOverviewCommandHelper.onTip(actionType, viewType);
            }
        }

        @Override
        public void onAssistantAvailable(boolean available) {
            // TODO handle assistant
        }

        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            // TODO handle assistant
        }

        public void onBackAction(boolean completed, int downX, int downY, boolean isButton,
                boolean gestureSwipeLeft) {
        }

        public void onSystemUiStateChanged(int stateFlags) {
            // To be implemented
        }

        /** Deprecated methods **/
        public void onQuickStep(MotionEvent motionEvent) { }

        public void onQuickScrubEnd() { }

        public void onQuickScrubProgress(float progress) { }

        public void onQuickScrubStart() { }

        public void onPreMotionEvent(int downHitTarget) { }

        public void onMotionEvent(MotionEvent ev) { }

        public void onBind(ISystemUiProxy iSystemUiProxy) {
            mRecentsModel.setSystemUiProxy(iSystemUiProxy);
        }
    };

    private static boolean sConnected = false;;

    public static boolean isConnected() {
        return sConnected;
    }

    private RecentsModel mRecentsModel;
    private OverviewComponentObserver mOverviewComponentObserver;
    private OverviewCommandHelper mOverviewCommandHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        if (UserManagerCompat.getInstance(this).isUserUnlocked(Process.myUserHandle())) {
            initWhenUserUnlocked();
        } else {
            mIsUserUnlocked = false;
            registerReceiver(mUserUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }

        sConnected = true;
    }

    private void initWhenUserUnlocked() {
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this);
        mOverviewCommandHelper = new OverviewCommandHelper(this,
                mOverviewComponentObserver);
        mIsUserUnlocked = true;
        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
    }

    @Override
    public void onDestroy() {
        if (mIsUserUnlocked) {
            mOverviewComponentObserver.onDestroy();
        }
        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Touch service connected");
        }
        return mMyBinder;
    }

    public static boolean isInitialized() {
        return true;
    }
}
