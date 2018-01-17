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
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.IOverviewProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Consumer;

/**
 * Service connected by system-UI for handling touch interaction.
 */
@TargetApi(Build.VERSION_CODES.O)
public class TouchInteractionService extends Service {

    public static final int EDGE_NAV_BAR = 1 << 8;

    private static final String TAG = "TouchInteractionService";

    @IntDef(flag = true, value = {
            INTERACTION_NORMAL,
            INTERACTION_QUICK_SWITCH,
            INTERACTION_QUICK_SCRUB
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InteractionType {}
    public static final int INTERACTION_NORMAL = 0;
    public static final int INTERACTION_QUICK_SWITCH = 1;
    public static final int INTERACTION_QUICK_SCRUB = 2;

    private final IBinder mMyBinder = new IOverviewProxy.Stub() {

        @Override
        public void onMotionEvent(MotionEvent ev) {
            mEventQueue.queue(ev);
        }

        @Override
        public void onBind(ISystemUiProxy iSystemUiProxy) throws RemoteException {
            mISystemUiProxy = iSystemUiProxy;
        }

        @Override
        public void onQuickSwitch() {
            startTouchTracking(INTERACTION_QUICK_SWITCH);
        }

        @Override
        public void onQuickScrubStart() {
            startTouchTracking(INTERACTION_QUICK_SCRUB);
        }

        @Override
        public void onQuickScrubEnd() {
            if (mInteractionHandler != null) {
                mInteractionHandler.onQuickScrubEnd();
            }
        }

        @Override
        public void onQuickScrubProgress(float progress) {
            if (mInteractionHandler != null) {
                mInteractionHandler.onQuickScrubProgress(progress);
            }
        }
    };

    private final Consumer<MotionEvent> mOtherActivityTouchConsumer
            = this::handleTouchDownOnOtherActivity;
    private final Consumer<MotionEvent> mNoOpTouchConsumer = (ev) -> {};

    private static boolean sConnected = false;

    public static boolean isConnected() {
        return sConnected;
    }

    private ActivityManagerWrapper mAM;
    private RunningTaskInfo mRunningTask;
    private RecentsModel mRecentsModel;
    private Intent mHomeIntent;
    private ComponentName mLauncher;
    private MotionEventQueue mEventQueue;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private float mStartDisplacement;
    private NavBarSwipeInteractionHandler mInteractionHandler;
    private int mDisplayRotation;
    private Rect mStableInsets = new Rect();

    private ISystemUiProxy mISystemUiProxy;
    private Consumer<MotionEvent> mCurrentConsumer = mNoOpTouchConsumer;

    @Override
    public void onCreate() {
        super.onCreate();
        mAM = ActivityManagerWrapper.getInstance();
        mRecentsModel = RecentsModel.getInstance(this);

        mHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo info = getPackageManager().resolveActivity(mHomeIntent, 0);
        mLauncher = new ComponentName(getPackageName(), info.activityInfo.name);
        // Clear the packageName as system can fail to dedupe it b/64108432
        mHomeIntent.setComponent(mLauncher).setPackage(null);

        mEventQueue = new MotionEventQueue(Choreographer.getInstance(), this::handleMotionEvent);
        sConnected = true;
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

                Display display = getSystemService(WindowManager.class).getDefaultDisplay();
                mDisplayRotation = display.getRotation();
                WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
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
                if (isNavBarOnRight()) {
                    displacement = ev.getX(pointerIndex) - mDownPos.x;
                } else if (isNavBarOnLeft()) {
                    displacement = mDownPos.x - ev.getX(pointerIndex);
                }
                if (mInteractionHandler == null) {
                    if (Math.abs(displacement) >= mTouchSlop) {
                        mStartDisplacement = Math.signum(displacement) * mTouchSlop;
                        startTouchTracking(INTERACTION_NORMAL);
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

    private boolean isNavBarOnRight() {
        return mDisplayRotation == Surface.ROTATION_90 && mStableInsets.right > 0;
    }

    private boolean isNavBarOnLeft() {
        return mDisplayRotation == Surface.ROTATION_270 && mStableInsets.left > 0;
    }

    private void startTouchTracking(@InteractionType int interactionType) {
        // Create the shared handler
        final NavBarSwipeInteractionHandler handler =
                new NavBarSwipeInteractionHandler(mRunningTask, this, interactionType);

        TraceHelper.partitionSection("TouchInt", "Thershold crossed ");

        // Start the recents activity on a background thread
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
        });

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
    }

    private void endInteraction() {
        if (mInteractionHandler != null) {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            float velocity = isNavBarOnRight() ? mVelocityTracker.getXVelocity(mActivePointerId)
                    : isNavBarOnLeft() ? -mVelocityTracker.getXVelocity(mActivePointerId)
                    : mVelocityTracker.getYVelocity(mActivePointerId);
            mInteractionHandler.endTouch(velocity);
            mInteractionHandler = null;
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private Bitmap getCurrentTaskSnapshot() {
        TraceHelper.beginSection("TaskSnapshot");
        // TODO: We are using some hardcoded layers for now, to best approximate the activity layers
        Point displaySize = new Point();
        Display display = getSystemService(WindowManager.class).getDefaultDisplay();
        display.getRealSize(displaySize);
        int rotation = display.getRotation();
        // The rotation is backwards in landscape, so flip it.
        if (rotation == Surface.ROTATION_270) {
            rotation = Surface.ROTATION_90;
        } else if (rotation == Surface.ROTATION_90) {
            rotation = Surface.ROTATION_270;
        }
        try {
            return mISystemUiProxy.screenshot(new Rect(), displaySize.x, displaySize.y, 0, 100000,
                    false, rotation).toBitmap();
        } catch (Exception e) {
            Log.e(TAG, "Error capturing snapshot", e);

            // Return a dummy bitmap
            Bitmap bitmap = Bitmap.createBitmap(displaySize.x, displaySize.y, Config.RGB_565);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
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
            int flags = ev.getEdgeFlags();
            ev.setEdgeFlags(flags | EDGE_NAV_BAR);
            ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
            mTarget.dispatchTouchEvent(ev);
            ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
            ev.setEdgeFlags(flags);
        }
    }
}
