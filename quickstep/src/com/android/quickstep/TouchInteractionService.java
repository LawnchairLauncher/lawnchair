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
import static com.android.launcher3.LauncherState.FAST_OVERVIEW;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_NONE;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.Intent;
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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.views.RecentsView;
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
            setupTouchConsumer(downHitTarget);
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
            mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);
        }

        @Override
        public void onQuickScrubStart() {
            mEventQueue.onQuickScrubStart();
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
        }

        @Override
        public void onOverviewToggle() {
            mOverviewCommandHelper.onOverviewToggle();
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            if (triggeredFromAltTab) {
                setupTouchConsumer(HIT_TARGET_NONE);
                mEventQueue.onOverviewShownFromAltTab();
            }
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab initiates quick scrub. Ending it here.
                mEventQueue.onQuickScrubEnd();
            }
        }

        @Override
        public void onQuickStep(MotionEvent motionEvent) {

        }
    };

    private final TouchConsumer mNoOpTouchConsumer = (ev) -> {};

    private static boolean sConnected = false;

    public static boolean isConnected() {
        return sConnected;
    }

    private ActivityManagerWrapper mAM;
    private RecentsModel mRecentsModel;
    private MotionEventQueue mEventQueue;
    private MainThreadExecutor mMainThreadExecutor;
    private ISystemUiProxy mISystemUiProxy;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewInteractionState mOverviewInteractionState;

    private Choreographer mMainThreadChoreographer;
    private Choreographer mBackgroundThreadChoreographer;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.getInstance(this);
        mMainThreadExecutor = new MainThreadExecutor();
        mOverviewCommandHelper = new OverviewCommandHelper(this);
        mMainThreadChoreographer = Choreographer.getInstance();
        mEventQueue = new MotionEventQueue(mMainThreadChoreographer, mNoOpTouchConsumer);
        mOverviewInteractionState = OverviewInteractionState.getInstance(this);

        sConnected = true;

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        initBackgroundChoreographer();
    }

    @Override
    public void onDestroy() {
        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void setupTouchConsumer(@HitTarget int downHitTarget) {
        mEventQueue.reset();
        TouchConsumer oldConsumer = mEventQueue.getConsumer();
        if (oldConsumer.deferNextEventToMainThread()) {
            mEventQueue = new MotionEventQueue(mMainThreadChoreographer,
                    new DeferredTouchConsumer((v) -> getCurrentTouchConsumer(downHitTarget,
                            oldConsumer.forceToLauncherConsumer(), v)));
            mEventQueue.deferInit();
        } else {
            mEventQueue = new MotionEventQueue(
                    mMainThreadChoreographer, getCurrentTouchConsumer(downHitTarget, false, null));
        }
    }

    private TouchConsumer getCurrentTouchConsumer(
            @HitTarget int downHitTarget, boolean forceToLauncher, VelocityTracker tracker) {
        RunningTaskInfo runningTaskInfo = mAM.getRunningTask();

        if (runningTaskInfo == null && !forceToLauncher) {
            return mNoOpTouchConsumer;
        } else if (forceToLauncher ||
                runningTaskInfo.topActivity.equals(mOverviewCommandHelper.launcher)) {
            return getLauncherConsumer();
        } else {
            if (tracker == null) {
                tracker = VelocityTracker.obtain();
            }
            return new OtherActivityTouchConsumer(this, runningTaskInfo, mRecentsModel,
                            mOverviewCommandHelper.homeIntent,
                            mOverviewCommandHelper.getActivityControlHelper(), mMainThreadExecutor,
                            mBackgroundThreadChoreographer, downHitTarget, tracker);
        }
    }

    private TouchConsumer getLauncherConsumer() {
        Launcher launcher = (Launcher) LauncherAppState.getInstance(this).getModel().getCallback();
        if (launcher == null) {
            return mNoOpTouchConsumer;
        }
        View target = launcher.getDragLayer();
        return new LauncherTouchConsumer(launcher, target);
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
        private boolean mHadWindowFocusOnDown;

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
            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                mTrackingStarted = false;
                mDownPos.set(ev.getX(), ev.getY());
                mHadWindowFocusOnDown = mTarget.hasWindowFocus();
            } else if (!mTrackingStarted && mHadWindowFocusOnDown) {
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
            if (interactionType == INTERACTION_QUICK_SCRUB) {
                Runnable action = () -> {
                    LauncherState fromState = mLauncher.getStateManager().getState();
                    mLauncher.getStateManager().goToState(FAST_OVERVIEW, true);
                    mQuickScrubController.onQuickScrubStart(fromState == NORMAL);
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
