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
import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.PreloadOptions;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;

import java.util.function.Consumer;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    private static final String TAG = "TouchInteractionService";

    private static RecentsTaskLoader sRecentsTaskLoader;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onMotionEvent(MotionEvent ev) {
            mEventQueue.queue(ev);
        }

        @Override
        public void onBind(ISystemUiProxy iSystemUiProxy) throws RemoteException {
            mISystemUiProxy = iSystemUiProxy;
        }
    };

    private final Consumer<MotionEvent> mOtherActivityTouchConsumer
            = this::handleTouchDownOnOtherActivity;
    private final Consumer<MotionEvent> mNoOpTouchConsumer = (ev) -> {};

    private ActivityManagerWrapper mAM;
    private RunningTaskInfo mRunningTask;
    private Intent mHomeIntent;
    private ComponentName mLauncher;
    private MotionEventQueue mEventQueue;
    private MainThreadExecutor mMainThreadExecutor;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private float mStartDisplacement;
    private NavBarSwipeInteractionHandler mInteractionHandler;

    private ISystemUiProxy mISystemUiProxy;
    private Consumer<MotionEvent> mCurrentConsumer = mNoOpTouchConsumer;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();

        mHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = getPackageManager().resolveActivity(mHomeIntent, 0);
        mLauncher = new ComponentName(getPackageName(), info.activityInfo.name);
        mHomeIntent.setComponent(mLauncher);

        Resources res = getResources();
        if (sRecentsTaskLoader == null) {
            sRecentsTaskLoader = new RecentsTaskLoader(this,
                    res.getInteger(R.integer.config_recentsMaxThumbnailCacheSize),
                    res.getInteger(R.integer.config_recentsMaxIconCacheSize), 0);
            sRecentsTaskLoader.startLoader(this);
        }

        mMainThreadExecutor = new MainThreadExecutor();
        mEventQueue = new MotionEventQueue(Choreographer.getInstance(), this::handleMotionEvent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Touch service connected");
        return mMyBinder;
    }

    public static RecentsTaskLoader getRecentsTaskLoader() {
        return sRecentsTaskLoader;
    }

    private void handleMotionEvent(MotionEvent ev) {
        if (ev.getActionMasked() == ACTION_DOWN) {
            mRunningTask = mAM.getRunningTask();

            if (mRunningTask == null) {
                mCurrentConsumer = mNoOpTouchConsumer;
            } else if (mRunningTask.topActivity.equals(mLauncher)) {
                mCurrentConsumer = getLauncherConsumer();
            } else {
                mCurrentConsumer = mOtherActivityTouchConsumer;
            }
        }
        mCurrentConsumer.accept(ev);
    }

    private void handleTouchDownOnOtherActivity(MotionEvent ev) {
        if (ev.getActionMasked() != ACTION_DOWN && mVelocityTracker == null) {
            return;
        }
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                TraceHelper.beginSection("TouchInt");
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                } else {
                    mVelocityTracker.clear();
                }
                mVelocityTracker.addMovement(ev);
                if (mInteractionHandler != null) {
                    mInteractionHandler.endTouch(0);
                    mInteractionHandler = null;
                }
                break;
            }
            case ACTION_POINTER_UP: {
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                            ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                            ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                    mVelocityTracker.clear();
                }
                break;
            }
            case ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mVelocityTracker.addMovement(ev);
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                float displacement = ev.getY(pointerIndex) - mDownPos.y;
                if (mInteractionHandler == null) {
                    if (Math.abs(displacement) >= mTouchSlop) {
                        mStartDisplacement = Math.signum(displacement) * mTouchSlop;
                        startTouchTracking();
                    }
                } else {
                    // Move
                    mInteractionHandler.updateDisplacement(displacement - mStartDisplacement);
                }
                break;
            }
            case ACTION_CANCEL:
                // TODO: Should be different than ACTION_UP
            case ACTION_UP: {
                TraceHelper.endSection("TouchInt");

                endInteraction();
                mCurrentConsumer = mNoOpTouchConsumer;
                break;
            }
        }
    }


    private void startTouchTracking() {
        // Create the shared handler
        final NavBarSwipeInteractionHandler handler =
                new NavBarSwipeInteractionHandler(mRunningTask, this);

        // Preload and start the recents activity on a background thread
        final Context context = this;
        final RecentsTaskLoadPlan loadPlan = new RecentsTaskLoadPlan(context);
        final int taskId = mRunningTask.id;
        TraceHelper.partitionSection("TouchInt", "Thershold crossed ");

        BackgroundExecutor.get().submit(() -> {
            // Get the snap shot before
            handler.setTaskSnapshot(getCurrentTaskSnapshot());

            // Start the launcher activity with our custom handler
            Intent homeIntent = handler.addToIntent(new Intent(mHomeIntent));
            startActivity(homeIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            TraceHelper.partitionSection("TouchInt", "Home started");

            /*
            ActivityManagerWrapper.getInstance().startRecentsActivity(null, options,
                    ActivityOptions.makeCustomAnimation(this, 0, 0), UserHandle.myUserId(),
                    null, null);
             */

            // Preload the plan
            RecentsTaskLoader loader = TouchInteractionService.getRecentsTaskLoader();
            PreloadOptions opts = new PreloadOptions();
            opts.loadTitles = false;
            loadPlan.preloadPlan(opts, loader, taskId, UserHandle.myUserId());
            // Set the load plan on UI thread
            mMainThreadExecutor.execute(() -> handler.setRecentsTaskLoadPlan(loadPlan));
        });
        mInteractionHandler = handler;
    }

    private void endInteraction() {
        if (mInteractionHandler != null) {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            mInteractionHandler.endTouch(mVelocityTracker.getYVelocity(mActivePointerId));
            mInteractionHandler = null;
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private Bitmap getCurrentTaskSnapshot() {
        if (mISystemUiProxy == null) {
            Log.e(TAG, "Never received systemUIProxy");
            return null;
        }

        TraceHelper.beginSection("TaskSnapshot");
        // TODO: We are using some hardcoded layers for now, to best approximate the activity layers
        Point displaySize = new Point();
        Display display = getSystemService(WindowManager.class).getDefaultDisplay();
        display.getRealSize(displaySize);
        try {
            return mISystemUiProxy.screenshot(new Rect(), displaySize.x, displaySize.y, 0, 100000,
                    false, display.getRotation()).toBitmap();
        } catch (RemoteException e) {
            Log.e(TAG, "Error capturing snapshot", e);
            return null;
        } finally {
            TraceHelper.endSection("TaskSnapshot");
        }
    }

    private Consumer<MotionEvent> getLauncherConsumer() {

        Launcher launcher = (Launcher) LauncherAppState.getInstance(this).getModel().getCallback();
        if (launcher == null) {
            return mNoOpTouchConsumer;
        }

        View target = launcher.getDragLayer();
        if (!target.getWindowId().isFocused()) {
            return mNoOpTouchConsumer;
        }
        return new LauncherTouchConsumer(target);
    }

    private class LauncherTouchConsumer implements Consumer<MotionEvent> {

        private final View mTarget;
        private final int[] mLocationOnScreen = new int[2];

        private boolean mTrackingStarted = false;

        LauncherTouchConsumer(View target) {
            mTarget = target;
        }

        @Override
        public void accept(MotionEvent ev) {
            int action = ev.getActionMasked();
            if (action == ACTION_DOWN) {
                mTrackingStarted = false;
                mDownPos.set(ev.getX(), ev.getY());
                mTouchSlop = ViewConfiguration.get(mTarget.getContext()).getScaledTouchSlop();
            } else if (!mTrackingStarted) {
                switch (action) {
                    case ACTION_POINTER_UP:
                    case ACTION_POINTER_DOWN:
                        if (!mTrackingStarted) {
                            mCurrentConsumer = mNoOpTouchConsumer;
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
                mCurrentConsumer = mNoOpTouchConsumer;
            }
        }

        private void sendEvent(MotionEvent ev) {
            ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
            mTarget.dispatchTouchEvent(ev);
            ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        }
    }
}
