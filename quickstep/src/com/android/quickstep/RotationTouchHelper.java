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

import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.MotionEventsUtils.isTrackpadScroll;
import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.DisplayController.CHANGE_ALL;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_ROTATION;
import static com.android.launcher3.util.DisplayController.CHANGE_SUPPORTED_BOUNDS;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.NavigationMode.THREE_BUTTONS;

import android.content.Context;
import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.OrientationEventListener;

import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.dagger.WindowContext;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.NavigationMode;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.systemui.shared.Flags;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.io.PrintWriter;

import javax.inject.Inject;

import app.lawnchair.util.LawnchairUtilsKt;

/**
 * Helper class for transforming touch events
 */
public class RotationTouchHelper implements DisplayInfoChangeListener {

    public static final DaggerSingletonObject<PerDisplayRepository<RotationTouchHelper>>
            REPOSITORY_INSTANCE = new DaggerSingletonObject<>(
            QuickstepBaseAppComponent::getRotationTouchHelperRepository);

    private final OrientationTouchTransformer mOrientationTouchTransformer;
    private final DisplayController mDisplayController;
    private final SystemUiProxy mSystemUiProxy;
    private final int mDisplayId;
    private int mDisplayRotation;

    private NavigationMode mMode = THREE_BUTTONS;

    private final TaskStackChangeListener mFrozenTaskListener = new TaskStackChangeListener() {
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

    private final Runnable mExitOverviewRunnable = new Runnable() {
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
    private final OrientationEventListener mOrientationListener;
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
    /**
     * Set to true when user swipes to recents. In recents, we ignore the state of the recents
     * task list being frozen or not to allow the user to keep interacting with nav bar rotation
     * they went into recents with as opposed to defaulting to the default display rotation.
     * TODO: (b/156984037) For when user rotates after entering overview
     */
    private boolean mInOverview;
    private boolean mTaskListFrozen;
    private final Context mWindowContext;

    @AssistedInject
    RotationTouchHelper(
            @Assisted Context windowContext,
            DisplayController displayController,
            SystemUiProxy systemUiProxy,
            DaggerSingletonTracker lifeCycle) {
        mWindowContext = windowContext;
        mDisplayId = windowContext.getDisplayId();
        mDisplayController = displayController;
        mSystemUiProxy = systemUiProxy;

        Resources resources = mWindowContext.getResources();
        mOrientationTouchTransformer = new OrientationTouchTransformer(resources, mMode,
                () -> LawnchairUtilsKt.getWindowCornerRadius(mWindowContext));

        // Register for navigation mode and rotation changes
        mDisplayController.addChangeListenerForDisplay(this, mDisplayId);
        DisplayController.Info info = mDisplayController.getInfoForDisplay(mDisplayId);
        onDisplayInfoChanged(mWindowContext, info, CHANGE_ALL);

        mOrientationListener = new OrientationEventListener(mWindowContext) {
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

        lifeCycle.addCloseable(() -> {
            mDisplayController.removeChangeListenerForDisplay(this, mDisplayId);
            mOrientationListener.disable();
            TaskStackChangeListeners.getInstance()
                    .unregisterTaskStackListener(mFrozenTaskListener);
        });
    }

    public boolean isTaskListFrozen() {
        return mTaskListFrozen;
    }

    public boolean touchInAssistantRegion(MotionEvent ev) {
        return mOrientationTouchTransformer.touchInAssistantRegion(ev);
    }

    public boolean touchInOneHandedModeRegion(MotionEvent ev) {
        return mOrientationTouchTransformer.touchInOneHandedModeRegion(ev);
    }

    /**
     * Updates the regions for detecting the swipe up/quickswitch and assistant gestures.
     */
    public void updateGestureTouchRegions() {
        if (!hasGestures(mMode)) {
            return;
        }

        mOrientationTouchTransformer.createOrAddTouchRegion(
                mDisplayController.getInfoForDisplay(mDisplayId),
                "RTH.updateGestureTouchRegions");
    }

    /**
     * @return whether the coordinates of the {@param event} is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event) {
        return isInSwipeUpTouchRegion(event, 0);
    }

    /**
     * @return whether the coordinates of the {@param event} with the given {@param pointerIndex}
     *         is in the swipe up gesture region.
     */
    public boolean isInSwipeUpTouchRegion(MotionEvent event, int pointerIndex) {
        if (isTrackpadScroll(event)) {
            return false;
        }
        if (isTrackpadMultiFingerSwipe(event)) {
            return true;
        }
        return mOrientationTouchTransformer.touchInValidSwipeRegions(event.getX(pointerIndex),
                event.getY(pointerIndex));
    }

    @Override
    public void onDisplayInfoChanged(Context context, Info info, int flags) {
        if ((flags & (CHANGE_ROTATION | CHANGE_ACTIVE_SCREEN | CHANGE_NAVIGATION_MODE
                | CHANGE_SUPPORTED_BOUNDS)) != 0) {
            mDisplayRotation = info.rotation;

            if (hasGestures(mMode)) {
                updateGestureTouchRegions();
                mOrientationTouchTransformer.createOrAddTouchRegion(info,
                        "RTH.onDisplayInfoChanged");
                mCurrentAppRotation = mDisplayRotation;

                /* Update nav bars on the following:
                 * a) if this is coming from an activity rotation OR
                 *   aa) we launch an app in the orientation that user is already in
                 * b) We're not in overview, since overview will always be portrait (w/o home
                 *   rotation)
                 * c) We're actively in quickswitch mode
                 */
                if ((mPrioritizeDeviceRotation
                        || mCurrentAppRotation == mSensorRotation)
                        // switch to an app of orientation user is in
                        && !mInOverview
                        && mTaskListFrozen) {
                    toggleSecondaryNavBarsForRotation();
                }
            }
        }

        if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
            NavigationMode newMode = info.getNavigationMode();
            mOrientationTouchTransformer.setNavigationMode(newMode,
                    mDisplayController.getInfoForDisplay(mDisplayId),
                    mWindowContext.getResources());

            TaskStackChangeListeners.getInstance()
                    .unregisterTaskStackListener(mFrozenTaskListener);
            if (hasGestures(newMode)) {
                TaskStackChangeListeners.getInstance()
                        .registerTaskStackListener(mFrozenTaskListener);
            }
            mMode = newMode;
        }
    }

    public int getDisplayRotation() {
        return mDisplayRotation;
    }

    /**
     * Sets the gestural height.
     */
    void setGesturalHeight(int newGesturalHeight) {
        mOrientationTouchTransformer.setGesturalHeight(
                newGesturalHeight, mDisplayController.getInfoForDisplay(mDisplayId),
                mWindowContext.getResources());
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
        mOrientationTouchTransformer.enableMultipleRegions(enable,
                mDisplayController.getInfoForDisplay(mDisplayId));
        notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getQuickStepStartingRotation());
        if (enable && !mInOverview && !TestProtocol.sDisableSensorRotation) {
            // Clear any previous state from sensor manager
            mSensorRotation = mCurrentAppRotation;
            UI_HELPER_EXECUTOR.execute(mOrientationListener::enable);
        } else {
            UI_HELPER_EXECUTOR.execute(mOrientationListener::disable);
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
            BaseContainerInterface containerInterface) {
        if (endTarget == GestureState.GestureEndTarget.RECENTS) {
            mInOverview = true;
            if (!mTaskListFrozen) {
                // If we're in landscape w/o ever quickswitching, show the navbar in landscape
                enableMultipleRegions(true);
            }
            containerInterface.onExitOverview(mExitOverviewRunnable);
        } else if (endTarget == GestureState.GestureEndTarget.HOME
                || endTarget == GestureState.GestureEndTarget.ALL_APPS) {
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
        UI_HELPER_EXECUTOR.execute(() -> mSystemUiProxy.notifyPrioritizedRotation(rotation));
    }

    /**
     * Disables/Enables multiple nav bars on {@link OrientationTouchTransformer} and then
     * notifies system UI of the primary rotation the user is interacting with
     */
    private void toggleSecondaryNavBarsForRotation() {
        mOrientationTouchTransformer.setSingleActiveRegion(
                mDisplayController.getInfoForDisplay(mDisplayId));
        notifySysuiOfCurrentRotation(mOrientationTouchTransformer.getCurrentActiveRotation());
    }

    public int getCurrentActiveRotation() {
        if (!hasGestures(mMode)) {
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

    public OrientationTouchTransformer getOrientationTouchTransformer() {
        return mOrientationTouchTransformer;
    }

    private boolean hasGestures(NavigationMode mode) {
        return mode.hasGestures || (mode == THREE_BUTTONS && Flags.threeButtonCornerSwipe());
    }

    @AssistedFactory
    public interface Factory {
        /** Creates a new instance of [RotationTouchHelper] for a given [context]. */
        RotationTouchHelper create(@WindowContext Context context);
    }
}
