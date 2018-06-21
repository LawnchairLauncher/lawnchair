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
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
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
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ChoreographerCompat;
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
            } else {
                mOverviewCommandHelper.onOverviewShown();
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
            mEventQueue.onQuickStep(motionEvent);
            TraceHelper.endSection("SysUiBinder", "onQuickStep");

        }

        @Override
        public void onTip(int actionType, int viewType) {
            mOverviewCommandHelper.onTip(actionType, viewType);
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
    private OverviewCallbacks mOverviewCallbacks;
    private TaskOverlayFactory mTaskOverlayFactory;

    private Choreographer mMainThreadChoreographer;
    private Choreographer mBackgroundThreadChoreographer;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.getInstance(this);
        mRecentsModel.setPreloadTasksInBackground(true);
        mMainThreadExecutor = new MainThreadExecutor();
        mOverviewCommandHelper = new OverviewCommandHelper(this);
        mMainThreadChoreographer = Choreographer.getInstance();
        mEventQueue = new MotionEventQueue(mMainThreadChoreographer, mNoOpTouchConsumer);
        mOverviewInteractionState = OverviewInteractionState.getInstance(this);
        mOverviewCallbacks = OverviewCallbacks.get(this);
        mTaskOverlayFactory = TaskOverlayFactory.get(this);

        sConnected = true;

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        initBackgroundChoreographer();
    }

    @Override
    public void onDestroy() {
        mOverviewCommandHelper.onDestroy();
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
        RunningTaskInfo runningTaskInfo = mAM.getRunningTask(0);

        if (runningTaskInfo == null && !forceToLauncher) {
            return mNoOpTouchConsumer;
        } else if (forceToLauncher ||
                runningTaskInfo.topActivity.equals(mOverviewCommandHelper.overviewComponent)) {
            return getOverviewConsumer();
        } else {
            if (tracker == null) {
                tracker = VelocityTracker.obtain();
            }
            return new OtherActivityTouchConsumer(this, runningTaskInfo, mRecentsModel,
                            mOverviewCommandHelper.overviewIntent,
                            mOverviewCommandHelper.getActivityControlHelper(), mMainThreadExecutor,
                            mBackgroundThreadChoreographer, downHitTarget, mOverviewCallbacks,
                            mTaskOverlayFactory, tracker);
        }
    }

    private TouchConsumer getOverviewConsumer() {
        ActivityControlHelper activityHelper = mOverviewCommandHelper.getActivityControlHelper();
        BaseDraggingActivity activity = activityHelper.getCreatedActivity();
        if (activity == null) {
            return mNoOpTouchConsumer;
        }
        return new OverviewTouchConsumer(activityHelper, activity);
    }

    private static class OverviewTouchConsumer<T extends BaseDraggingActivity>
            implements TouchConsumer {

        private final ActivityControlHelper<T> mActivityHelper;
        private final T mActivity;
        private final BaseDragLayer mTarget;
        private final int[] mLocationOnScreen = new int[2];
        private final PointF mDownPos = new PointF();
        private final int mTouchSlop;
        private final QuickScrubController mQuickScrubController;

        private boolean mTrackingStarted = false;
        private boolean mInvalidated = false;

        private float mLastProgress = 0;
        private boolean mStartPending = false;
        private boolean mEndPending = false;

        OverviewTouchConsumer(ActivityControlHelper<T> activityHelper, T activity) {
            mActivityHelper = activityHelper;
            mActivity = activity;
            mTarget = activity.getDragLayer();
            mTouchSlop = ViewConfiguration.get(mTarget.getContext()).getScaledTouchSlop();

            mQuickScrubController = mActivity.<RecentsView>getOverviewPanel()
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
                            mTarget.getLocationOnScreen(mLocationOnScreen);

                            // Send a down event only when mTouchSlop is crossed.
                            MotionEvent down = MotionEvent.obtain(ev);
                            down.setAction(ACTION_DOWN);
                            sendEvent(down);
                            down.recycle();
                            mTrackingStarted = true;
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
            if (!mTrackingStarted) {
                mTarget.onInterceptTouchEvent(ev);
            }
            mTarget.onTouchEvent(ev);
            ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
            ev.setEdgeFlags(flags);
        }

        @Override
        public void onQuickStep(MotionEvent ev) {
            if (mInvalidated) {
                return;
            }
            OverviewCallbacks.get(mActivity).closeAllWindows();
            ActivityManagerWrapper.getInstance()
                    .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
        }

        @Override
        public void updateTouchTracking(int interactionType) {
            if (mInvalidated) {
                return;
            }
            if (interactionType == INTERACTION_QUICK_SCRUB) {
                if (!mQuickScrubController.prepareQuickScrub(TAG)) {
                    mInvalidated = true;
                    return;
                }
                OverviewCallbacks.get(mActivity).closeAllWindows();
                ActivityManagerWrapper.getInstance()
                        .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);

                mStartPending = true;
                Runnable action = () -> {
                    if (!mQuickScrubController.prepareQuickScrub(TAG)) {
                        mInvalidated = true;
                        return;
                    }
                    mActivityHelper.onQuickInteractionStart(mActivity, null, true);
                    mQuickScrubController.onQuickScrubProgress(mLastProgress);
                    mStartPending = false;

                    if (mEndPending) {
                        mQuickScrubController.onQuickScrubEnd();
                        mEndPending = false;
                    }
                };

                mActivityHelper.executeOnWindowAvailable(mActivity, action);
            }
        }

        @Override
        public void onQuickScrubEnd() {
            if (mInvalidated) {
                return;
            }
            if (mStartPending) {
                mEndPending = true;
            } else {
                mQuickScrubController.onQuickScrubEnd();
            }
        }

        @Override
        public void onQuickScrubProgress(float progress) {
            mLastProgress = progress;
            if (mInvalidated || mStartPending) {
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
                mBackgroundThreadChoreographer = ChoreographerCompat.getSfInstance());
    }
}
