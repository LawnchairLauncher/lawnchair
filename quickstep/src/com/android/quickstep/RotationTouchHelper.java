/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.Surface.ROTATION_0;

import static com.android.launcher3.util.DefaultDisplay.CHANGE_ALL;
import static com.android.launcher3.util.DefaultDisplay.CHANGE_FRAME_DELAY;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.SysUINavigationMode.Mode.THREE_BUTTONS;

import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.OrientationEventListener;

import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.TaskStackChangeListener;

import java.io.PrintWriter;
import java.util.ArrayList;

public class RotationTouchHelper implements
        SysUINavigationMode.NavigationModeChangeListener,
        DefaultDisplay.DisplayInfoChangeListener {

    private final OrientationTouchTransformer mOrientationTouchTransformer;
    private final DefaultDisplay mDefaultDisplay;
    private final SysUINavigationMode mSysUiNavMode;
    private final int mDisplayId;
    private int mDisplayRotation;

    private final ArrayList<Runnable> mOnDestroyActions = new ArrayList<>();

    private SysUINavigationMode.Mode mMode = THREE_BUTTONS;

    private TaskStackChangeListener mFrozenTaskListener = new TaskStackChangeListener() {
        @Override
        public void onRecentTaskListFrozenChanged(boolean frozen) {
            mTaskListFrozen = frozen;
            if (frozen || mInOverview) {
                return;
            }
            enableMultipleRegions(false);
        }

        @Override
        public void onActivityRotation(int displayId) {
            super.onActivityRotation(displayId);
            // This always gets called before onDisplayInfoChanged() so we know how to process
            // the rotation in that method. This is done to avoid having a race condition between
            // the sensor readings and onDisplayInfoChanged() call
            if (displayId != mDisplayId) {
                return;
            }

            mPrioritizeDeviceRotation = true;
            if (mInOverview) {
                // reset, launcher must be rotating
                mExitOverviewRunnable.run();
            }
        }
    };

    private Runnable mExitOverviewRunnable = new Runnable() {
        @Override
        public void run() {
            mInOverview = false;
            enableMultipleRegions(false);
        }
    };

    /**
     * Used to listen for when the device rotates into the orientation of the current foreground
     * app. For example, if a user quickswitches from a portrait to a fixed landscape app and then
     * rotates rotates the device to match that orientation, this triggers calls to sysui to adjust
     * the navbar.
     */
    private OrientationEventListener mOrientationListener;
    private int mSensorRotation = ROTATION_0;
    /**
     * This is the configuration of the foreground app or the app that will be in the foreground
     * once a quickstep gesture finishes.
     */
    private int mCurrentAppRotation = -1;
    /**
     * This flag is set to true when the device physically changes orientations. When true, we will
     * always report the current rotation of the foreground app whenever the display changes, as it
     * would indicate the user's intention to rotate the foreground app.
     */
    private boolean mPrioritizeDeviceRotation = false;
    private Runnable mOnDestroyFrozenTaskRunnable;
    /**
     * Set to true when user swipes to recents. In recents, we ignore the state of the recents
     * task list being frozen or not to allow the user to keep interacting with nav bar rotation
     * they went into recents with as opposed to defaulting to the default display rotation.
     * TODO: (b/156984037) For when user rotates after entering overview
     */
    private boolean mInOverview;
    private boolean mTaskListFrozen;


    private final Context mContext;

    public RotationTouchHelper(Context context) {
        mContext = context;
        Resources resources = mContext.getResources();
        mSysUiNavMode = SysUINavigationMode.INSTANCE.get(context);
        mDefaultDisplay = DefaultDisplay.INSTANCE.get(context);
        mDisplayId = mDefaultDisplay.getInfo().id;

        mOrientationTouchTransformer = new OrientationTouchTransformer(resources, mMode,
                () -> QuickStepContract.getWindowCornerRadius(resources));

        // Register for navigation mode changes
        onNavigationModeChanged(mSysUiNavMode.addModeChangeListener(this));
        runOnDestroy(() -> mSysUiNavMode.removeModeChangeListener(this));

        mOrientationListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int degrees) {
                int newRotation = RecentsOrientedState.getRotationForUserDegreesRotated(degrees,
                        mSensorRotation);
                if (newRotation == mSensorRotation) {
                    return;
                }

                mSensorRotation = newRotation;
                mPrioritizeDeviceRotation = true;

                if (newRotation == mCurrentAppRotation) {
                    // When user rotates device to the orientation of the foreground app after
                    // quickstepping
                    toggleSecondaryNavBarsForRotation();
                }
            }
        };
    }

    private void setupOrientationSwipeHandler() {
        ActivityManagerWrapper.getInstance().registerTaskStackListener(mFrozenTaskListener);
        mOnDestroyFrozenTaskRunnable = () -> ActivityManagerWrapper.getInstance()
                .unregisterTaskStackListener(mFrozenTaskListener);
        runOnDestroy(mOnDestroyFrozenTaskRunnable);
    }

    private void destroyOrientationSwipeHandlerCallback() {
        ActivityManagerWrapper.getInstance().unregisterTaskStackListener(mFrozenTaskListener);
        mOnDestroyActions.remove(mOnDestroyFrozenTaskRunnable);
    }

    private void runOnDestroy(Runnable action) {
        mOnDestroyActions.add(action);
    }

    /**
     * Cleans up all the registered listeners and receivers.
     */
    public void destroy() {
        for (Runnable r : mOnDestroyActions) {
            r.run();
        }
    }

    public boolean isTaskListFrozen() {
        return mTaskListFrozen;
    }

    public boolean touchInAssistantRegion(MotionEvent ev) {
        return mOrientationTouchTransformer.touchInAssistantRegion(ev);
    }

    /**
     * Updates the regions for detecting the swipe up/quickswitch and assistant gestures.
     */
    public void updateGestureTouchRegions() {
        if (!mMode.hasGestures) {
            return;
        }

        mOrientationTouchTransformer.createOrAddTouchRegion(mDefaultDisplay.getInfo());
    }

    /**
     * @return whether the coordinates of the {@param event} is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event) {
        return mOrientationTouchTransformer.touchInValidSwipeRegions(event.getX(), event.getY());
    }

    /**
     * @return whether the coordinates of the {@param event} with the given {@param pointerIndex}
     *         is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event, int pointerIndex) {
        return mOrientationTouchTransformer.touchInValidSwipeRegions(event.getX(pointerIndex),
                event.getY(pointerIndex));
    }


    @Override
    public void onNavigationModeChanged(SysUINavigationMode.Mode newMode) {
        mDefaultDisplay.removeChangeListener(this);
        mDefaultDisplay.addChangeListener(this);
        onDisplayInfoChanged(mDefaultDisplay.getInfo(), CHANGE_ALL);

        mOrientationTouchTransformer.setNavigationMode(newMode, mDefaultDisplay.getInfo());
        if (!mMode.hasGestures && newMode.hasGestures) {
            setupOrientationSwipeHandler();
        } else if (mMode.hasGestures && !newMode.hasGestures){
            destroyOrientationSwipeHandlerCallback();
        }

        mMode = newMode;
    }

    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    @Override
    public void onDisplayInfoChanged(DefaultDisplay.Info info, int flags) {
        if (info.id != mDisplayId|| flags == CHANGE_FRAME_DELAY) {
            // ignore displays that aren't running launcher and frame refresh rate changes
            return;
        }

        mDisplayRotation = info.rotation;

        if (!mMode.hasGestures) {
            return;
        }
        updateGestureTouchRegions();
        mOrientationTouchTransformer.createOrAddTouchRegion(info);
        mCurrentAppRotation = mDisplayRotation;

        /* Update nav bars on the following:
         * a) if this is coming from an activity rotation OR
         *   aa) we launch an app in the orientation that user is already in
         * b) We're not in overview, since overview will always be portrait (w/o home rotation)
         * c) We're actively in quickswitch mode
         */
        if ((mPrioritizeDeviceRotation
                || mCurrentAppRotation == mSensorRotation) // switch to an app of orientation user is in
                && !mInOverview
                && mTaskListFrozen) {
            toggleSecondaryNavBarsForRotation();
        }
    }

    /**
     * *May* apply a transform on the motion event if it lies in the nav bar region for another
     * orientation that is currently being tracked as a part of quickstep
     */
    void setOrientationTransformIfNeeded(MotionEvent event) {
        // negative coordinates bug b/143901881
        if (event.getX() < 0 || event.getY() < 0) {
            event.setLocation(Math.max(0, event.getX()), Math.max(0, event.getY()));
        }
        mOrientationTouchTransformer.transform(event);
    }

    private void enableMultipleRegions(boolean enable) {
        mOrientationTouchTransformer.enableMultipleRegions(enable, mDefaultDisplay.getInfo());
        notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getQuickStepStartingRotation());
        if (enable && !mInOverview && !TestProtocol.sDisableSensorRotation) {
            // Clear any previous state from sensor manager
            mSensorRotation = mCurrentAppRotation;
            mOrientationListener.enable();
        } else {
            mOrientationListener.disable();
        }
    }

    public void onStartGesture() {
        if (mTaskListFrozen) {
            // Prioritize whatever nav bar user touches once in quickstep
            // This case is specifically when user changes what nav bar they are using mid
            // quickswitch session before tasks list is unfrozen
            notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
        }
    }

    void onEndTargetCalculated(GestureState.GestureEndTarget endTarget,
            BaseActivityInterface activityInterface) {
        if (endTarget == GestureState.GestureEndTarget.RECENTS) {
            mInOverview = true;
            if (!mTaskListFrozen) {
                // If we're in landscape w/o ever quickswitching, show the navbar in landscape
                enableMultipleRegions(true);
            }
            activityInterface.onExitOverview(this, mExitOverviewRunnable);
        } else if (endTarget == GestureState.GestureEndTarget.HOME) {
            enableMultipleRegions(false);
        } else if (endTarget == GestureState.GestureEndTarget.NEW_TASK) {
            if (mOrientationTouchTransformer.getQuickStepStartingRotation() == -1) {
                // First gesture to start quickswitch
                enableMultipleRegions(true);
            } else {
                notifySysuiOfCurrentRotation(
                        mOrientationTouchTransformer.getCurrentActiveRotation());
            }

            // A new gesture is starting, reset the current device rotation
            // This is done under the assumption that the user won't rotate the phone and then
            // quickswitch in the old orientation.
            mPrioritizeDeviceRotation = false;
        } else if (endTarget == GestureState.GestureEndTarget.LAST_TASK) {
            if (!mTaskListFrozen) {
                // touched nav bar but didn't go anywhere and not quickswitching, do nothing
                return;
            }
            notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
        }
    }

    private void notifySysuiOfCurrentRotation(int rotation) {
        UI_HELPER_EXECUTOR.execute(() -> SystemUiProxy.INSTANCE.get(mContext)
                .onQuickSwitchToNewTask(rotation));
    }

    /**
     * Disables/Enables multiple nav bars on {@link OrientationTouchTransformer} and then
     * notifies system UI of the primary rotation the user is interacting with
     */
    private void toggleSecondaryNavBarsForRotation() {
        mOrientationTouchTransformer.setSingleActiveRegion(mDefaultDisplay.getInfo());
        notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
    }

    public int getCurrentActiveRotation() {
        if (!mMode.hasGestures) {
            // touch rotation should always match that of display for 3 button
            return mDisplayRotation;
        }
        return mOrientationTouchTransformer.getCurrentActiveRotation();
    }

    public void dump(PrintWriter pw) {
        pw.println("RotationTouchHelper:");
        pw.println("  currentActiveRotation=" + getCurrentActiveRotation());
        pw.println("  displayRotation=" + getDisplayRotation());
        mOrientationTouchTransformer.dump(pw);
    }
}
