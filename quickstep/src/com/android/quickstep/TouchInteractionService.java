/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.quickstep.QuickScrubController.QUICK_SWITCH_START_DURATION;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    private static final SparseArray<String> sMotionEventNames;

    static {
        sMotionEventNames = new SparseArray<>(3);
        sMotionEventNames.put(ACTION_DOWN, "ACTION_DOWN");
        sMotionEventNames.put(ACTION_UP, "ACTION_UP");
        sMotionEventNames.put(ACTION_CANCEL, "ACTION_CANCEL");
    }

    public static final int EDGE_NAV_BAR = 1 << 8;

    private static final String TAG = "TouchInteractionService";

    /**
     * A background thread used for handling UI for another window.
     */
    private static HandlerThread sRemoteUiThread;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onPreMotionEvent(@HitTarget int downHitTarget) throws RemoteException {
            TraceHelper.beginSection("SysUiBinder");
            onBinderPreMotionEvent(downHitTarget);
            TraceHelper.partitionSection("SysUiBinder", "Down target " + downHitTarget);
        }

        @Override
        public void onMotionEvent(MotionEvent ev) {
            mEventQueue.queue(ev);

            String name = sMotionEventNames.get(ev.getActionMasked());
            if (name != null){
                TraceHelper.partitionSection("SysUiBinder", name);
            }
        }

        @Override
        public void onBind(ISystemUiProxy iSystemUiProxy) {
            mISystemUiProxy = iSystemUiProxy;
            mRecentsModel.setSystemUiProxy(mISystemUiProxy);
            RemoteRunnable.executeSafely(() -> mISystemUiProxy.setRecentsOnboardingText(
                    getResources().getString(R.string.recents_swipe_up_onboarding)));
            Launcher launcher = (Launcher) LauncherAppState.getInstance(
                    TouchInteractionService.this).getModel().getCallback();
            UiFactory.onLauncherStateOrFocusChanged(launcher);
        }

        @Override
        public void onQuickSwitch() {
            mEventQueue.onQuickSwitch();
            TraceHelper.endSection("SysUiBinder", "onQuickSwitch");
        }

        @Override
        public void onQuickScrubStart() {
            mEventQueue.onQuickScrubStart();
            sQuickScrubEnabled = true;
            TraceHelper.partitionSection("SysUiBinder", "onQuickScrubStart");
        }

        @Override
        public void onQuickScrubProgress(float progress) {
            mEventQueue.onQuickScrubProgress(progress);
        }

        @Override
        public void onQuickScrubEnd() {
            mEventQueue.onQuickScrubEnd();
            TraceHelper.endSection("SysUiBinder", "onQuickScrubEnd");
            sQuickScrubEnabled = false;
        }
    };

    private final TouchConsumer mNoOpTouchConsumer = (ev) -> {};

    private static boolean sConnected = false;
    private static boolean sQuickScrubEnabled = false;

    public static boolean isConnected() {
        return sConnected;
    }

    public static boolean isQuickScrubEnabled() {
        return sQuickScrubEnabled;
    }

    private ActivityManagerWrapper mAM;
    private RunningTaskInfo mRunningTask;
    private RecentsModel mRecentsModel;
    private Intent mHomeIntent;
    private ComponentName mLauncher;
    private MotionEventQueue mEventQueue;
    private MainThreadExecutor mMainThreadExecutor;
    private ISystemUiProxy mISystemUiProxy;

    private Choreographer mMainThreadChoreographer;
    private Choreographer mBackgroundThreadChoreographer;
    private MotionEventQueue mNoOpEventQueue;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.getInstance(this);
        mMainThreadExecutor = new MainThreadExecutor();

        mHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = getPackageManager().resolveActivity(mHomeIntent, 0);
        mLauncher = new ComponentName(getPackageName(), info.activityInfo.name);
        // Clear the packageName as system can fail to dedupe it b/64108432
        mHomeIntent.setComponent(mLauncher).setPackage(null);

        mMainThreadChoreographer = Choreographer.getInstance();
        mNoOpEventQueue = new MotionEventQueue(mMainThreadChoreographer, mNoOpTouchConsumer);
        mEventQueue = mNoOpEventQueue;

        sConnected = true;

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        initBackgroundChoreographer();
    }

    @Override
    public void onDestroy() {
        sConnected = false;
        sQuickScrubEnabled = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void onBinderPreMotionEvent(@HitTarget int downHitTarget) {
        mRunningTask = mAM.getRunningTask();

        mEventQueue.reset();

        if (mRunningTask == null) {
            mEventQueue = mNoOpEventQueue;
        } else if (mRunningTask.topActivity.equals(mLauncher)) {
            mEventQueue = getLauncherEventQueue();
        } else {
            mEventQueue = new MotionEventQueue(mMainThreadChoreographer,
                    new OtherActivityTouchConsumer(this, mRunningTask, mRecentsModel,
                    mHomeIntent, mISystemUiProxy, mMainThreadExecutor,
                    mBackgroundThreadChoreographer, downHitTarget));
        }
    }

    private MotionEventQueue getLauncherEventQueue() {
        Launcher launcher = (Launcher) LauncherAppState.getInstance(this).getModel().getCallback();
        if (launcher == null) {
            return mNoOpEventQueue;
        }

        View target = launcher.getDragLayer();
        return new MotionEventQueue(mMainThreadChoreographer,
                new LauncherTouchConsumer(launcher, target));
    }

    private static class LauncherTouchConsumer implements TouchConsumer {

        private final Launcher mLauncher;
        private final View mTarget;
        private final int[] mLocationOnScreen = new int[2];
        private final PointF mDownPos = new PointF();
        private final int mTouchSlop;
        private final QuickScrubController mQuickScrubController;

        private boolean mTrackingStarted = false;
        private boolean mInvalidated = false;

        LauncherTouchConsumer(Launcher launcher, View target) {
            mLauncher = launcher;
            mTarget = target;
            mTouchSlop = ViewConfiguration.get(mTarget.getContext()).getScaledTouchSlop();

            mQuickScrubController = mLauncher.<RecentsView>getOverviewPanel()
                    .getQuickScrubController();
        }

        @Override
        public void accept(MotionEvent ev) {
            if (mInvalidated) {
                return;
            }
            if (!mTarget.hasWindowFocus()) {
                return;
            }
            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                mTrackingStarted = false;
                mDownPos.set(ev.getX(), ev.getY());
            } else if (!mTrackingStarted) {
                switch (action) {
                    case ACTION_POINTER_UP:
                    case ACTION_POINTER_DOWN:
                        if (!mTrackingStarted) {
                            mInvalidated = true;
                        }
                        break;
                    case ACTION_MOVE: {
                        float displacement = ev.getY() - mDownPos.y;
                        if (Math.abs(displacement) >= mTouchSlop) {
                            mTrackingStarted = true;
                            mTarget.getLocationOnScreen(mLocationOnScreen);

                            // Send a down event only when mTouchSlop is crossed.
                            MotionEvent down = MotionEvent.obtain(ev);
                            down.setAction(ACTION_DOWN);
                            sendEvent(down);
                            down.recycle();
                        }
                    }
                }
            }

            if (mTrackingStarted) {
                sendEvent(ev);
            }

            if (action == ACTION_UP || action == ACTION_CANCEL) {
                mInvalidated = true;
            }
        }

        private void sendEvent(MotionEvent ev) {
            int flags = ev.getEdgeFlags();
            ev.setEdgeFlags(flags | EDGE_NAV_BAR);
            ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
            mTarget.dispatchTouchEvent(ev);
            ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
            ev.setEdgeFlags(flags);
        }

        @Override
        public void updateTouchTracking(int interactionType) {
            if (mInvalidated) {
                return;
            }
            if (TouchConsumer.isInteractionQuick(interactionType)) {
                Runnable action = () -> {
                    Runnable onComplete = null;
                    if (interactionType == INTERACTION_QUICK_SCRUB) {
                        mQuickScrubController.onQuickScrubStart(true);
                    } else if (interactionType == INTERACTION_QUICK_SWITCH) {
                        onComplete = mQuickScrubController::onQuickSwitch;
                    }
                    mLauncher.getStateManager().goToState(OVERVIEW, true, 0,
                            QUICK_SWITCH_START_DURATION, onComplete);
                };

                if (mLauncher.getWorkspace().runOnOverlayHidden(action)) {
                    // Hide the minus one overlay so launcher can get window focus.
                    mLauncher.onQuickstepGestureStarted(true);
                }
            }
        }

        @Override
        public void onQuickScrubEnd() {
            if (mInvalidated) {
                return;
            }
            mQuickScrubController.onQuickScrubEnd();
        }

        @Override
        public void onQuickScrubProgress(float progress) {
            if (mInvalidated) {
                return;
            }
            mQuickScrubController.onQuickScrubProgress(progress);
        }

    }

    private void initBackgroundChoreographer() {
        if (sRemoteUiThread == null) {
            sRemoteUiThread = new HandlerThread("remote-ui");
            sRemoteUiThread.start();
        }
        new Handler(sRemoteUiThread.getLooper()).post(() ->
                mBackgroundThreadChoreographer = Choreographer.getInstance());
    }
}
