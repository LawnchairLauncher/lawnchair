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
package com.android.launcher3.uioverrides;

import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;

import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.touch.TouchEventTranslator;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.RecentsModel;
import com.android.systemui.shared.recents.ISystemUiProxy;

/**
 * TouchController for handling touch events that get sent to the StatusBar. Once the
 * Once the event delta y passes the touch slop, the events start getting forwarded.
 * All events are offset by initial Y value of the pointer.
 */
public class StatusBarTouchController implements TouchController {

    private static final String TAG = "StatusBarController";

    protected final Launcher mLauncher;
    protected final TouchEventTranslator mTranslator;
    private final float mTouchSlop;
    private ISystemUiProxy mSysUiProxy;

    /* If {@code false}, this controller should not handle the input {@link MotionEvent}.*/
    private boolean mCanIntercept;

    public StatusBarTouchController(Launcher l) {
        mLauncher = l;
        // Guard against TAPs by increasing the touch slop.
        mTouchSlop = 2 * ViewConfiguration.get(l).getScaledTouchSlop();
        mTranslator = new TouchEventTranslator((MotionEvent ev)-> dispatchTouchEvent(ev));
    }

    private void dispatchTouchEvent(MotionEvent ev) {
        try {
            if (mSysUiProxy != null) {
                mSysUiProxy.onStatusBarMotionEvent(ev);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception on sysUiProxy.", e);
        }
    }

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mTranslator.reset();
            mTranslator.setDownParameters(0, ev);
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            // Check!! should only set it only when threshold is not entered.
            mTranslator.setDownParameters(ev.getActionIndex(), ev);
        }
        if (!mCanIntercept) {
            return false;
        }
        if (action == ACTION_MOVE) {
            float dy = ev.getY() - mTranslator.getDownY();
            float dx = ev.getX() - mTranslator.getDownX();
            if (dy > mTouchSlop && dy > Math.abs(dx)) {
                mTranslator.dispatchDownEvents(ev);
                mTranslator.processMotionEvent(ev);
                return true;
            }
            if (Math.abs(dx) > mTouchSlop) {
                mCanIntercept = false;
            }
        }
        return false;
    }


    @Override
    public final boolean onControllerTouchEvent(MotionEvent ev) {
        mTranslator.processMotionEvent(ev);
        return true;
    }

    private boolean canInterceptTouch(MotionEvent ev) {
        if (!mLauncher.isInState(LauncherState.NORMAL) ||
                AbstractFloatingView.getTopOpenViewWithType(mLauncher,
                        AbstractFloatingView.TYPE_STATUS_BAR_SWIPE_DOWN_DISALLOW) != null) {
            return false;
        } else {
            // For NORMAL state, only listen if the event originated above the navbar height
            DeviceProfile dp = mLauncher.getDeviceProfile();
            if (ev.getY() > (mLauncher.getDragLayer().getHeight() - dp.getInsets().bottom)) {
                return false;
            }
        }
        mSysUiProxy = RecentsModel.INSTANCE.get(mLauncher).getSystemUiProxy();
        return mSysUiProxy != null;
    }
}
