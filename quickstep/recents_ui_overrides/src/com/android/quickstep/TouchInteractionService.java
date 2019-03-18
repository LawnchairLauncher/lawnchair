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

import static android.view.MotionEvent.ACTION_DOWN;

import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_INPUT_CHANNEL;
import static com.android.systemui.shared.system.QuickStepContract.KEY_EXTRA_SYSUI_PROXY;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Region;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.util.Pair;
import android.view.Choreographer;
import android.view.InputEvent;
import android.view.MotionEvent;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.EventLogArray;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.UiThreadHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputChannelCompat;
import com.android.systemui.shared.system.InputChannelCompat.InputEventDispatcher;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    public static final MainThreadExecutor MAIN_THREAD_EXECUTOR = new MainThreadExecutor();
    public static final LooperExecutor BACKGROUND_EXECUTOR =
            new LooperExecutor(UiThreadHelper.getBackgroundLooper());

    public static final EventLogArray TOUCH_INTERACTION_LOG =
            new EventLogArray("touch_interaction_log", 40);

    private static final String TAG = "TouchInteractionService";

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        public void onActiveNavBarRegionChanges(Region region) {
            mActiveNavBarRegion = region;
        }

        public void onInitialize(Bundle bundle) {
            mISystemUiProxy = ISystemUiProxy.Stub
                    .asInterface(bundle.getBinder(KEY_EXTRA_SYSUI_PROXY));
            runWhenUserUnlocked(() -> {
                mRecentsModel.setSystemUiProxy(mISystemUiProxy);
                mRecentsModel.onInitializeSystemUI(bundle);
                mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);
            });

            disposeEventHandlers();
            mInputEventReceiver = InputChannelCompat.fromBundle(bundle, KEY_EXTRA_INPUT_CHANNEL,
                    Looper.getMainLooper(), mMainChoreographer,
                    TouchInteractionService.this::onInputEvent);
        }

        @Override
        public void onOverviewToggle() {
            mOverviewCommandHelper.onOverviewToggle();
        }

        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            mOverviewCommandHelper.onOverviewShown(triggeredFromAltTab);
        }

        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            if (triggeredFromAltTab && !triggeredFromHomeKey) {
                // onOverviewShownFromAltTab hides the overview and ends at the target app
                mOverviewCommandHelper.onOverviewHidden();
            }
        }

        @Override
        public void onTip(int actionType, int viewType) {
            mOverviewCommandHelper.onTip(actionType, viewType);
        }

        @Override
        public void onAssistantAvailable(boolean available) {
            mAssistantAvailable = available;
        }

        /** Deprecated methods **/
        public void onQuickStep(MotionEvent motionEvent) { }

        public void onQuickScrubEnd() { }

        public void onQuickScrubProgress(float progress) { }

        public void onQuickScrubStart() { }

        public void onPreMotionEvent(int downHitTarget) { }

        public void onMotionEvent(MotionEvent ev) {
            if (mDeprecatedDispatcher == null) {
                ev.recycle();
            } else {
                mDeprecatedDispatcher.dispatch(ev);
            }
        }

        public void onBind(ISystemUiProxy iSystemUiProxy) {
            mISystemUiProxy = iSystemUiProxy;
            runWhenUserUnlocked(() -> {
                mRecentsModel.setSystemUiProxy(mISystemUiProxy);
                mOverviewInteractionState.setSystemUiProxy(mISystemUiProxy);
            });

            // On Bind is received before onInitialize which will dispose these handlers
            disposeEventHandlers();
            Pair<InputEventDispatcher, InputEventReceiver> pair = InputChannelCompat.createPair(
                    "sysui-callbacks", Looper.getMainLooper(), mMainChoreographer,
                    TouchInteractionService.this::onInputEvent);
            mDeprecatedDispatcher = pair.first;
            mInputEventReceiver = pair.second;
        }
    };

    private static boolean sConnected = false;

    public static boolean isConnected() {
        return sConnected;
    }

    private KeyguardManager mKM;
    private ActivityManagerWrapper mAM;
    private RecentsModel mRecentsModel;
    private ISystemUiProxy mISystemUiProxy;
    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private OverviewInteractionState mOverviewInteractionState;
    private OverviewCallbacks mOverviewCallbacks;
    private TaskOverlayFactory mTaskOverlayFactory;
    private InputConsumerController mInputConsumer;
    private SwipeSharedState mSwipeSharedState;
    private boolean mAssistantAvailable;

    private boolean mIsUserUnlocked;
    private List<Runnable> mOnUserUnlockedCallbacks;
    private BroadcastReceiver mUserUnlockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                initWhenUserUnlocked();
            }
        }
    };

    private InputConsumer mConsumer = InputConsumer.NO_OP;
    private Choreographer mMainChoreographer;

    private InputEventReceiver mInputEventReceiver;
    private Region mActiveNavBarRegion = new Region();

    private InputEventDispatcher mDeprecatedDispatcher;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in initWhenUserUnlocked() below.
        mKM = getSystemService(KeyguardManager.class);
        mMainChoreographer = Choreographer.getInstance();
        mOnUserUnlockedCallbacks = new ArrayList<>();

        if (UserManagerCompat.getInstance(this).isUserUnlocked(Process.myUserHandle())) {
            initWhenUserUnlocked();
        } else {
            mIsUserUnlocked = false;
            registerReceiver(mUserUnlockedReceiver, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        }

        sConnected = true;
    }

    private void initWhenUserUnlocked() {
        mIsUserUnlocked = true;

        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.INSTANCE.get(this);
        mOverviewComponentObserver = new OverviewComponentObserver(this);

        mOverviewCommandHelper = new OverviewCommandHelper(this, mOverviewComponentObserver);
        mOverviewInteractionState = OverviewInteractionState.INSTANCE.get(this);
        mOverviewCallbacks = OverviewCallbacks.get(this);
        mTaskOverlayFactory = TaskOverlayFactory.INSTANCE.get(this);
        mSwipeSharedState = new SwipeSharedState(mOverviewComponentObserver);
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();
        mInputConsumer.registerInputConsumer();

        for (Runnable callback : mOnUserUnlockedCallbacks) {
            callback.run();
        }
        mOnUserUnlockedCallbacks.clear();

        // Temporarily disable model preload
        // new ModelPreload().start(this);

        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
    }

    private void runWhenUserUnlocked(Runnable callback) {
        if (mIsUserUnlocked) {
            callback.run();
        } else {
            mOnUserUnlockedCallbacks.add(callback);
        }
    }

    @Override
    public void onDestroy() {
        if (mIsUserUnlocked) {
            mInputConsumer.unregisterInputConsumer();
            mOverviewComponentObserver.onDestroy();
        }
        disposeEventHandlers();
        sConnected = false;
        Utilities.unregisterReceiverSafely(this, mUserUnlockedReceiver);
        super.onDestroy();
    }

    private void disposeEventHandlers() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mDeprecatedDispatcher != null) {
            mDeprecatedDispatcher.dispose();
            mDeprecatedDispatcher = null;
        }
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
        TOUCH_INTERACTION_LOG.addLog("onMotionEvent", event.getActionMasked());
        if (event.getAction() == ACTION_DOWN) {
            boolean useSharedState = mConsumer.isActive();
            mConsumer.onConsumerAboutToBeSwitched();
            mConsumer = newConsumer(useSharedState, event);
            TOUCH_INTERACTION_LOG.addLog("setInputConsumer", mConsumer.getType());
        }
        mConsumer.onMotionEvent(event);
    }

    private InputConsumer newConsumer(boolean useSharedState, MotionEvent event) {
        // TODO: this makes a binder call every touch down. we should move to a listener pattern.
        if (mKM.isDeviceLocked()) {
            // This handles apps launched in direct boot mode (e.g. dialer) as well as apps launched
            // while device is locked even after exiting direct boot mode (e.g. camera).
            return new DeviceLockedInputConsumer(this);
        }

        RunningTaskInfo runningTaskInfo = mAM.getRunningTask(0);
        if (!useSharedState) {
            mSwipeSharedState.clearAllState();
        }

        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        if (runningTaskInfo == null && !mSwipeSharedState.goingToLauncher) {
            return InputConsumer.NO_OP;
        } else if (mAssistantAvailable && mOverviewInteractionState.isSwipeUpGestureEnabled()
                && FeatureFlags.ENABLE_ASSISTANT_GESTURE.get()
                && AssistantTouchConsumer.withinTouchRegion(this, event)) {
            return new AssistantTouchConsumer(this, mISystemUiProxy, !activityControl.isResumed()
                            ? createOtherActivityInputConsumer(event, runningTaskInfo) : null);
        } else if (mSwipeSharedState.goingToLauncher || activityControl.isResumed()) {
            return OverviewInputConsumer.newInstance(activityControl, false);
        } else if (ENABLE_QUICKSTEP_LIVE_TILE.get() &&
                activityControl.isInLiveTileMode()) {
            return OverviewInputConsumer.newInstance(activityControl, false);
        } else {
            return createOtherActivityInputConsumer(event, runningTaskInfo);
        }
    }

    private OtherActivityInputConsumer createOtherActivityInputConsumer(MotionEvent event,
            RunningTaskInfo runningTaskInfo) {
        final ActivityControlHelper activityControl =
                mOverviewComponentObserver.getActivityControlHelper();
        boolean shouldDefer = activityControl.deferStartingActivity(mActiveNavBarRegion, event);
        return new OtherActivityInputConsumer(this, runningTaskInfo, mRecentsModel,
                mOverviewComponentObserver.getOverviewIntent(), activityControl,
                shouldDefer, mOverviewCallbacks, mTaskOverlayFactory, mInputConsumer,
                this::onConsumerInactive, mSwipeSharedState);
    }

    /**
     * To be called by the consumer when it's no longer active.
     */
    private void onConsumerInactive(InputConsumer caller) {
        if (mConsumer == caller) {
            mConsumer = InputConsumer.NO_OP;
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        TOUCH_INTERACTION_LOG.dump("", pw);
    }
}
