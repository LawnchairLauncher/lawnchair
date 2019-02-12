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
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_NONE;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_INPUT_CHANNEL;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.content.Intent;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
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

        public void onActiveNavBarRegionChanges(Region region) {
            mActiveNavBarRegion = region;
        }

        public void onInitialize(Bundle bundle) {
            mISystemUiProxy = ISystemUiProxy.Stub
                    .asInterface(bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            mRecentsModel.setSystemUiProxy(mISystemUiProxy);
            mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);

            if (mInputEventReceiver != null) {
                mInputEventReceiver.dispose();
            }
            mInputEventReceiver = InputChannelCompat.fromBundle(bundle, KEY_EXTRA_INPUT_CHANNEL,
                    Looper.getMainLooper(), mMainChoreographer,
                    TouchInteractionService.this::onInputEvent);
        }

        public void onPreMotionEvent(@HitTarget int downHitTarget) {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                return;
            }
            mTouchInteractionLog.prepareForNewGesture();

            TraceHelper.beginSection("SysUiBinder");
            mEventQueue.onNewGesture(downHitTarget);
            TraceHelper.partitionSection("SysUiBinder", "Down target " + downHitTarget);
        }

        public void onMotionEvent(MotionEvent ev) {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                ev.recycle();
                return;
            }
            mEventQueue.queue(ev);

            int action = ev.getActionMasked();
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
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                return;
            }
            mEventQueue.onQuickScrubStart();
            TraceHelper.partitionSection("SysUiBinder", "onQuickScrubStart");
        }

        public void onQuickScrubProgress(float progress) {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                return;
            }
            mEventQueue.onQuickScrubProgress(progress);
        }

        public void onQuickScrubEnd() {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                return;
            }
            mEventQueue.onQuickScrubEnd();
            TraceHelper.endSection("SysUiBinder", "onQuickScrubEnd");
        }

        @Override
        public void onOverviewToggle() {
            mOverviewCommandHelper.onOverviewToggle();
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                mOverviewCommandHelper.onOverviewShown();
                return;
            }
            if (triggeredFromAltTab) {
                mEventQueue.onNewGesture(HIT_TARGET_NONE);
                mEventQueue.onOverviewShownFromAltTab();
            } else {
                mOverviewCommandHelper.onOverviewShown();
            }
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            // If ev are using the new dispatching system, skip the old logic
            if (mInputEventReceiver != null) {
                return;
            }
            if (triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab initiates quick scrub. Ending it here.
                mEventQueue.onQuickScrubEnd();
            }
        }

        public void onQuickStep(MotionEvent motionEvent) { }

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

    private TouchConsumer mConsumer = TouchConsumer.NO_OP;
    private Choreographer mMainChoreographer;
    private InputEventReceiver mInputEventReceiver;
    private Region mActiveNavBarRegion = new Region();

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this);
        mMainChoreographer = Choreographer.getInstance();

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
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
        }
        sConnected = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    private void onInputEvent(InputEvent ev) {
        if (!(ev instanceof MotionEvent)) {
            Log.e(TAG, "Unknown event " + ev);
            return;
        }
        MotionEvent event = (MotionEvent) ev;
        if (event.getAction() == ACTION_DOWN) {
            mTouchInteractionLog.prepareForNewGesture();
            boolean useSharedState = mConsumer.isActive();
            mConsumer.onConsumerAboutToBeSwitched();
            mConsumer = newConsumer(useSharedState, event);
        }

        mConsumer.accept(event);
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
            ActivityControlHelper activityControl =
                    mOverviewComponentObserver.getActivityControlHelper();
            return new OtherActivityTouchConsumer(this, runningTaskInfo, mRecentsModel,
                    mOverviewComponentObserver.getOverviewIntent(),
                    mOverviewComponentObserver.getActivityControlHelper(),
                    activityControl.deferStartingActivity(downHitTarget), mOverviewCallbacks,
                    mTaskOverlayFactory, mInputConsumer, mTouchInteractionLog,
                    mEventQueue::onConsumerInactive, mSwipeSharedState);
        }
    }

    private TouchConsumer newConsumer(boolean useSharedState, MotionEvent event) {
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
            ActivityControlHelper activityControl =
                    mOverviewComponentObserver.getActivityControlHelper();
            boolean shouldDefer = activityControl.deferStartingActivity(mActiveNavBarRegion, event);
            return new OtherActivityTouchConsumer(this, runningTaskInfo, mRecentsModel,
                    mOverviewComponentObserver.getOverviewIntent(),
                    mOverviewComponentObserver.getActivityControlHelper(),
                    shouldDefer, mOverviewCallbacks,
                    mTaskOverlayFactory, mInputConsumer, mTouchInteractionLog,
                    this::onConsumerInactive, mSwipeSharedState);
        }
    }

    /**
     * To be called by the consumer when it's no longer active.
     */
    private void onConsumerInactive(TouchConsumer caller) {
        if (mConsumer == caller) {
            mConsumer = TouchConsumer.NO_OP;
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mTouchInteractionLog.dump(pw);
    }
}
