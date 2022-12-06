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
 *
 * Modifications copyright 2021, Lawnchair
 */
package com.android.launcher3.uioverrides.touchcontrollers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.WindowManager.LayoutParams.FLAG_SLIPPERY;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN;

import android.annotation.SuppressLint;
import android.graphics.PointF;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.util.TouchController;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.VibratorWrapper;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import app.lawnchair.LawnchairAppKt;
import app.lawnchair.util.CompatibilityKt;

/**
 * TouchController for handling touch events that get sent to the StatusBar. Once the
 * Once the event delta mDownY passes the touch slop, the events start getting forwarded.
 * All events are offset by initial Y value of the pointer.
 */
public class StatusBarTouchController implements TouchController {

    private static final String TAG = "StatusBarController";

    private final Launcher mLauncher;
    private final SystemUiProxy mSystemUiProxy;
    private final float mTouchSlop;
    private int mLastAction;
    private final SparseArray<PointF> mDownEvents;

    /* If {@code false}, this controller should not handle the input {@link MotionEvent}.*/
    private boolean mCanIntercept;

    private boolean mExpanded;
    private boolean mVibrated;

    public StatusBarTouchController(Launcher l) {
        mLauncher = l;
        mSystemUiProxy = SystemUiProxy.INSTANCE.get(mLauncher);
        // Guard against TAPs by increasing the touch slop.
        mTouchSlop = 2 * ViewConfiguration.get(l).getScaledTouchSlop();
        mDownEvents = new SparseArray<>();
    }

    @Override
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "mCanIntercept:" + mCanIntercept);
        writer.println(prefix + "mLastAction:" + MotionEvent.actionToString(mLastAction));
        writer.println(prefix + "mSysUiProxy available:"
                + SystemUiProxy.INSTANCE.get(mLauncher).isActive());
    }

    private void dispatchTouchEvent(MotionEvent ev) {
        if (mSystemUiProxy.isActive()) {
            mLastAction = ev.getActionMasked();
            mSystemUiProxy.onStatusBarMotionEvent(ev);
        } else if (!mExpanded) {
            mExpanded = true;
            expand();
        }
        if (!mVibrated) {
            mVibrated = true;
            vibrate();
        }
    }

    @SuppressLint({"WrongConstant", "PrivateApi"})
    private void expand() {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(mLauncher.getSystemService("statusbar"));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void vibrate() {
        if (!LawnchairAppKt.getLawnchairApp(mLauncher).isVibrateOnIconAnimation()) {
            VibratorWrapper.INSTANCE.get(mLauncher).vibrate(VibratorWrapper.OVERVIEW_HAPTIC);
        }
    }

    @Override
    public final boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();
        int pid = ev.getPointerId(idx);
        if (action == ACTION_DOWN) {
            mCanIntercept = canInterceptTouch(ev);
            if (!mCanIntercept) {
                return false;
            }
            mExpanded = false;
            mVibrated = false;
            mDownEvents.put(pid, new PointF(ev.getX(), ev.getY()));
        } else if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
           // Check!! should only set it only when threshold is not entered.
           mDownEvents.put(pid, new PointF(ev.getX(idx), ev.getY(idx)));
        }
        if (!mCanIntercept) {
            return false;
        }
        if (action == ACTION_MOVE) {
            float dy = ev.getY(idx) - mDownEvents.get(pid).y;
            float dx = ev.getX(idx) - mDownEvents.get(pid).x;
            // Currently input dispatcher will not do touch transfer if there are more than
            // one touch pointer. Hence, even if slope passed, only set the slippery flag
            // when there is single touch event. (context: InputDispatcher.cpp line 1445)
            if (dy > mTouchSlop && dy > Math.abs(dx) && ev.getPointerCount() == 1) {
                ev.setAction(ACTION_DOWN);
                dispatchTouchEvent(ev);
                setWindowSlippery(true);
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
        int action = ev.getAction();
        if (action == ACTION_UP || action == ACTION_CANCEL) {
            dispatchTouchEvent(ev);
            mLauncher.getStatsLogManager().logger()
                    .log(LAUNCHER_SWIPE_DOWN_WORKSPACE_NOTISHADE_OPEN);
            setWindowSlippery(false);
            return true;
        } else if (CompatibilityKt.isOnePlusStock() && action == ACTION_MOVE) {
            dispatchTouchEvent(ev);
        }
        return true;
    }

    /**
     * FLAG_SLIPPERY enables touches to slide out of a window into neighboring
     * windows in mid-gesture instead of being captured for the duration of
     * the gesture.
     *
     * This flag changes the behavior of touch focus for this window only.
     * Touches can slide out of the window but they cannot necessarily slide
     * back in (unless the other window with touch focus permits it).
     */
    private void setWindowSlippery(boolean enable) {
        Window w = mLauncher.getWindow();
        WindowManager.LayoutParams wlp = w.getAttributes();
        if (enable) {
            wlp.flags |= FLAG_SLIPPERY;
        } else {
            wlp.flags &= ~FLAG_SLIPPERY;
        }
        w.setAttributes(wlp);
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
        return true;
    }
}
