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
import static android.view.MotionEvent.ACTION_POINTER_INDEX_SHIFT;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_NONE;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    public static final MainThreadExecutor MAIN_THREAD_EXECUTOR = new MainThreadExecutor();

    private static final SparseArray<String> sMotionEventNames;

    static {
        sMotionEventNames = new SparseArray<>(3);
        sMotionEventNames.put(ACTION_DOWN, "ACTION_DOWN");
        sMotionEventNames.put(ACTION_UP, "ACTION_UP");
        sMotionEventNames.put(ACTION_CANCEL, "ACTION_CANCEL");
    }

    public static final int EDGE_NAV_BAR = 1 << 8;

    private static final String TAG = "TouchInteractionService";

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        public void onActiveNavBarRegionChanges(Region region) { }

        public void onInitialize(Bundle params) { }

        public void onPreMotionEvent(@HitTarget int downHitTarget) {
            mTouchInteractionLog.prepareForNewGesture();

            TraceHelper.beginSection("SysUiBinder");
            mEventQueue.onNewGesture(downHitTarget);
            TraceHelper.partitionSection("SysUiBinder", "Down target " + downHitTarget);
        }

        public void onMotionEvent(MotionEvent ev) {
            mEventQueue.queue(ev);

            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                mOverviewInteractionState.setSwipeGestureInitializing(true);
            } else if (action == ACTION_UP || action == ACTION_CANCEL) {
                mOverviewInteractionState.setSwipeGestureInitializing(false);
            }

            String name = sMotionEventNames.get(action);
            if (name != null){
                TraceHelper.partitionSection("SysUiBinder", name);
            }
        }

        public void onBind(ISystemUiProxy iSystemUiProxy) {
            mISystemUiProxy = iSystemUiProxy;
            mRecentsModel.setSystemUiProxy(mISystemUiProxy);
            mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);
        }

        public void onQuickScrubStart() {
            mEventQueue.onQuickScrubStart();
            mOverviewInteractionState.setSwipeGestureInitializing(false);
            TraceHelper.partitionSection("SysUiBinder", "onQuickScrubStart");
        }

        public void onQuickScrubProgress(float progress) {
            mEventQueue.onQuickScrubProgress(progress);
        }

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
                mEventQueue.onNewGesture(HIT_TARGET_NONE);
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

        public void onQuickStep(MotionEvent motionEvent) {
            mEventQueue.onQuickStep(motionEvent);
            mOverviewInteractionState.setSwipeGestureInitializing(false);
            TraceHelper.endSection("SysUiBinder", "onQuickStep");
        }

        @Override
        public void onTip(int actionType, int viewType) {
            mOverviewCommandHelper.onTip(actionType, viewType);
        }
    };

    private static boolean sConnected = false;

    public static boolean isConnected() {
        return sConnected;
    }

    private ActivityManagerWrapper mAM;
    private RecentsModel mRecentsModel;
    private MotionEventQueue mEventQueue;
    private ISystemUiProxy mISystemUiProxy;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private OverviewInteractionState mOverviewInteractionState;
    private OverviewCallbacks mOverviewCallbacks;
    private TaskOverlayFactory mTaskOverlayFactory;
    private TouchInteractionLog mTouchInteractionLog;
    private InputConsumerController mInputConsumer;
    private SwipeSharedState mSwipeSharedState;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this);
        mOverviewCommandHelper = new OverviewCommandHelper(this, mOverviewComponentObserver);
        mEventQueue = new MotionEventQueue(Looper.myLooper(), Choreographer.getInstance(),
                this::newConsumer);
        mOverviewInteractionState = OverviewInteractionState.INSTANCE.get(this);
        mOverviewCallbacks = OverviewCallbacks.get(this);
        mTaskOverlayFactory = TaskOverlayFactory.INSTANCE.get(this);
        mTouchInteractionLog = new TouchInteractionLog();
        mSwipeSharedState = new SwipeSharedState();
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();
        mInputConsumer.registerInputConsumer();

        sConnected = true;

        // Temporarily disable model preload
        // new ModelPreload().start(this);
    }

    @Override
    public void onDestroy() {
        mInputConsumer.unregisterInputConsumer();
        mOverviewComponentObserver.onDestroy();
        mEventQueue.dispose();
        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private TouchConsumer newConsumer(@HitTarget int downHitTarget, boolean useSharedState) {
        RunningTaskInfo runningTaskInfo = mAM.getRunningTask(0);
        if (!useSharedState) {
            mSwipeSharedState.clearAllState();
        }

        if (runningTaskInfo == null && !mSwipeSharedState.goingToLauncher) {
            return TouchConsumer.NO_OP;
        } else if (mSwipeSharedState.goingToLauncher ||
                mOverviewComponentObserver.getActivityControlHelper().isResumed()) {
            return OverviewTouchConsumer.newInstance(
                    mOverviewComponentObserver.getActivityControlHelper(), false,
                    mTouchInteractionLog);
        } else if (ENABLE_QUICKSTEP_LIVE_TILE.get() &&
                mOverviewComponentObserver.getActivityControlHelper().isInLiveTileMode()) {
            return OverviewTouchConsumer.newInstance(
                    mOverviewComponentObserver.getActivityControlHelper(), false,
                    mTouchInteractionLog, false /* waitForWindowAvailable */);
        } else {
            return new OtherActivityTouchConsumer(this, runningTaskInfo, mRecentsModel,
                    mOverviewComponentObserver.getOverviewIntent(),
                    mOverviewComponentObserver.getActivityControlHelper(),
                    downHitTarget, mOverviewCallbacks,
                    mTaskOverlayFactory, mInputConsumer, mTouchInteractionLog, mEventQueue,
                    mSwipeSharedState);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mTouchInteractionLog.dump(pw);
    }

    public static class OverviewTouchConsumer<T extends BaseDraggingActivity>
            implements TouchConsumer {

        private final ActivityControlHelper<T> mActivityHelper;
        private final T mActivity;
        private final BaseDragLayer mTarget;
        private final int[] mLocationOnScreen = new int[2];
        private final PointF mDownPos = new PointF();
        private final int mTouchSlop;
        private final QuickScrubController mQuickScrubController;
        private final TouchInteractionLog mTouchInteractionLog;

        private final boolean mStartingInActivityBounds;

        private boolean mTrackingStarted = false;
        private boolean mInvalidated = false;

        private float mLastProgress = 0;
        private boolean mStartPending = false;
        private boolean mEndPending = false;
        private boolean mWaitForWindowAvailable;

        OverviewTouchConsumer(ActivityControlHelper<T> activityHelper, T activity,
                boolean startingInActivityBounds, TouchInteractionLog touchInteractionLog,
                boolean waitForWindowAvailable) {
            mActivityHelper = activityHelper;
            mActivity = activity;
            mTarget = activity.getDragLayer();
            mTouchSlop = ViewConfiguration.get(mActivity).getScaledTouchSlop();
            mStartingInActivityBounds = startingInActivityBounds;

            mQuickScrubController = mActivity.<RecentsView>getOverviewPanel()
                    .getQuickScrubController();
            mTouchInteractionLog = touchInteractionLog;
            mTouchInteractionLog.setTouchConsumer(this);

            mWaitForWindowAvailable = waitForWindowAvailable;
        }

        @Override
        public void accept(MotionEvent ev) {
            if (mInvalidated) {
                return;
            }
            mTouchInteractionLog.addMotionEvent(ev);
            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                if (mStartingInActivityBounds) {
                    startTouchTracking(ev, false /* updateLocationOffset */);
                    return;
                }
                mTrackingStarted = false;
                mDownPos.set(ev.getX(), ev.getY());
            } else if (!mTrackingStarted) {
                switch (action) {
                    case ACTION_CANCEL:
                    case ACTION_UP:
                        startTouchTracking(ev, true /* updateLocationOffset */);
                        break;
                    case ACTION_MOVE: {
                        float displacement = mActivity.getDeviceProfile().isLandscape ?
                                ev.getX() - mDownPos.x : ev.getY() - mDownPos.y;
                        if (Math.abs(displacement) >= mTouchSlop) {
                            // Start tracking only when mTouchSlop is crossed.
                            startTouchTracking(ev, true /* updateLocationOffset */);
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

        private void startTouchTracking(MotionEvent ev, boolean updateLocationOffset) {
            if (updateLocationOffset) {
                mTarget.getLocationOnScreen(mLocationOnScreen);
            }

            // Send down touch event
            MotionEvent down = MotionEvent.obtainNoHistory(ev);
            down.setAction(ACTION_DOWN);
            sendEvent(down);

            mTrackingStarted = true;
            // Send pointer down for remaining pointers.
            int pointerCount = ev.getPointerCount();
            for (int i = 1; i < pointerCount; i++) {
                down.setAction(ACTION_POINTER_DOWN | (i << ACTION_POINTER_INDEX_SHIFT));
                sendEvent(down);
            }

            down.recycle();
        }

        private void sendEvent(MotionEvent ev) {
            if (!mTarget.verifyTouchDispatch(this, ev)) {
                mInvalidated = true;
                return;
            }
            int flags = ev.getEdgeFlags();
            ev.setEdgeFlags(flags | TouchInteractionService.EDGE_NAV_BAR);
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
            mTouchInteractionLog.startQuickStep();
        }

        @Override
        public void onQuickScrubStart() {
            if (mInvalidated) {
                return;
            }
            mTouchInteractionLog.startQuickScrub();
            if (!mQuickScrubController.prepareQuickScrub(TAG)) {
                mInvalidated = true;
                mTouchInteractionLog.endQuickScrub("onQuickScrubStart");
                return;
            }
            OverviewCallbacks.get(mActivity).closeAllWindows();
            ActivityManagerWrapper.getInstance()
                    .closeSystemWindows(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);

            mStartPending = true;
            Runnable action = () -> {
                if (!mQuickScrubController.prepareQuickScrub(TAG)) {
                    mInvalidated = true;
                    mTouchInteractionLog.endQuickScrub("onQuickScrubStart");
                    return;
                }
                mActivityHelper.onQuickInteractionStart(mActivity, null, true,
                        mTouchInteractionLog);
                mQuickScrubController.onQuickScrubProgress(mLastProgress);
                mStartPending = false;

                if (mEndPending) {
                    mQuickScrubController.onQuickScrubEnd();
                    mEndPending = false;
                }
            };

            if (mWaitForWindowAvailable) {
                mActivityHelper.executeOnWindowAvailable(mActivity, action);
            } else {
                action.run();
            }
        }

        @Override
        public void onQuickScrubEnd() {
            mTouchInteractionLog.endQuickScrub("onQuickScrubEnd");
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
            mTouchInteractionLog.setQuickScrubProgress(progress);
            mLastProgress = progress;
            if (mInvalidated || mStartPending) {
                return;
            }
            mQuickScrubController.onQuickScrubProgress(progress);
        }

        public static TouchConsumer newInstance(ActivityControlHelper activityHelper,
                boolean startingInActivityBounds, TouchInteractionLog touchInteractionLog) {
            return newInstance(activityHelper, startingInActivityBounds, touchInteractionLog,
                    true /* waitForWindowAvailable */);
        }

        public static TouchConsumer newInstance(ActivityControlHelper activityHelper,
                boolean startingInActivityBounds, TouchInteractionLog touchInteractionLog,
                boolean waitForWindowAvailable) {
            BaseDraggingActivity activity = activityHelper.getCreatedActivity();
            if (activity == null) {
                return TouchConsumer.NO_OP;
            }
            return new OverviewTouchConsumer(activityHelper, activity, startingInActivityBounds,
                    touchInteractionLog, waitForWindowAvailable);
        }
    }
}
