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

import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.launcher3.states.InternalStateHandler.EXTRA_STATE_HANDLER;

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
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan.Options;
import com.android.systemui.shared.recents.model.RecentsTaskLoader;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;

import java.util.concurrent.Future;

/**
 * Service connected by system-UI for handling touch interaction.
 */
public class TouchInteractionService extends Service {

    private static final String TAG = "TouchInteractionService";

    private static RecentsTaskLoader sRecentsTaskLoader;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onMotionEvent(MotionEvent ev) {
            handleMotionEvent(ev);
        }

        @Override
        public void onBind(ISystemUiProxy iSystemUiProxy) throws RemoteException {
            mISystemUiProxy = iSystemUiProxy;
        }
    };

    private ActivityManagerWrapper mAM;
    private RunningTaskInfo mRunningTask;
    private Intent mHomeIntent;
    private ComponentName mLauncher;


    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private NavBarSwipeInteractionHandler mInteractionHandler;

    private ISystemUiProxy mISystemUiProxy;

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
        if (ev.getActionMasked() != MotionEvent.ACTION_DOWN && mVelocityTracker == null) {
            return;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

                mRunningTask = mAM.getRunningTask();
                if (mRunningTask == null || mRunningTask.topActivity.equals(mLauncher)) {
                    // TODO: We could drive all-apps in this case. For now just ignore swipe.
                    break;
                }

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
            case MotionEvent.ACTION_POINTER_UP: {
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
            case MotionEvent.ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mVelocityTracker.addMovement(ev);
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                float displacement = ev.getY(pointerIndex) - mDownPos.y;
                if (mInteractionHandler == null) {
                    if (Math.abs(displacement) >= mTouchSlop) {
                        startTouchTracking();
                    }
                } else {
                    // Move
                    mInteractionHandler.updateDisplacement(displacement);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                // TODO: Should be different than ACTION_UP
            case MotionEvent.ACTION_UP: {

                endInteraction();
                break;
            }
        }
    }

    private void startTouchTracking() {
        // Create the shared handler
        mInteractionHandler = new NavBarSwipeInteractionHandler(getCurrentTaskSnapshot(),
                mRunningTask);

        // Preload and start the recents activity on a background thread
        final Context context = this;
        final int runningTaskId = ActivityManagerWrapper.getInstance().getRunningTask().id;
        final RecentsTaskLoadPlan loadPlan = new RecentsTaskLoadPlan(context);
        Future<RecentsTaskLoadPlan> loadPlanFuture = BackgroundExecutor.get().submit(() -> {
            // Preload the plan
            RecentsTaskLoader loader = TouchInteractionService.getRecentsTaskLoader();
            loadPlan.preloadPlan(loader, runningTaskId, UserHandle.myUserId());

            // Pass the
            Bundle extras = new Bundle();
            extras.putBinder(EXTRA_STATE_HANDLER, mInteractionHandler);

            // Start the activity
            Intent homeIntent = new Intent(mHomeIntent);
            homeIntent.putExtras(extras);
            startActivity(homeIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            /*
            ActivityManagerWrapper.getInstance().startRecentsActivity(null, options,
                    ActivityOptions.makeCustomAnimation(this, 0, 0), UserHandle.myUserId(),
                    null, null);
             */

            // Kick off loading of the plan while the activity is starting
            Options loadOpts = new Options();
            loadOpts.runningTaskId = runningTaskId;
            loadOpts.loadIcons = true;
            loadOpts.loadThumbnails = true;
            loadOpts.numVisibleTasks = 2;
            loadOpts.numVisibleTaskThumbnails = 2;
            loadOpts.onlyLoadForCache = false;
            loadOpts.onlyLoadPausedActivities = false;
            loader.loadTasks(loadPlan, loadOpts);
        }, loadPlan);

        mInteractionHandler.setLastLoadPlan(loadPlanFuture);
    }

    private void endInteraction() {
        if (mInteractionHandler != null) {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            mInteractionHandler.endTouch(mVelocityTracker.getXVelocity(mActivePointerId));
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
        Display display = getSystemService(WindowManager.class).getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        // TODO: We are using some hardcoded layers for now, to best approximate the activity layers
        try {
            return mISystemUiProxy.screenshot(new Rect(), size.x, size.y, 0, 100000, false,
                    display.getRotation());
        } catch (RemoteException e) {
            Log.e(TAG, "Error capturing snapshot", e);
            return null;
        }
    }
}
