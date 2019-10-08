/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.systemui.shared.recents.model.ThumbnailData;
import java.util.ArrayList;

/**
 * Manages the state for an active system gesture, listens for events from the system and Launcher,
 * and fires events when the states change.
 */
public class GestureState implements RecentsAnimationCallbacks.RecentsAnimationListener {

    /**
     * Defines the end targets of a gesture and the associated state.
     */
    public enum GestureEndTarget {
        HOME(true, ContainerType.WORKSPACE, false),

        RECENTS(true, ContainerType.TASKSWITCHER, true),

        NEW_TASK(false, ContainerType.APP, true),

        LAST_TASK(false, ContainerType.APP, false);

        GestureEndTarget(boolean isLauncher, int containerType,
                boolean recentsAttachedToAppWindow) {
            this.isLauncher = isLauncher;
            this.containerType = containerType;
            this.recentsAttachedToAppWindow = recentsAttachedToAppWindow;
        }

        /** Whether the target is in the launcher activity. Implicitly, if the end target is going
         to Launcher, then we can not interrupt the animation to start another gesture. */
        public final boolean isLauncher;
        /** Used to log where the user ended up after the gesture ends */
        public final int containerType;
        /** Whether RecentsView should be attached to the window as we animate to this target */
        public final boolean recentsAttachedToAppWindow;
    }

    private static final ArrayList<String> STATE_NAMES = new ArrayList<>();
    private static int FLAG_COUNT = 0;
    private static int getFlagForIndex(String name) {
        if (DEBUG_STATES) {
            STATE_NAMES.add(name);
        }
        int index = 1 << FLAG_COUNT;
        FLAG_COUNT++;
        return index;
    }

    // Called when the end target as been set
    public static final int STATE_END_TARGET_SET =
            getFlagForIndex("STATE_END_TARGET_SET");

    // Called when the end target animation has finished
    public static final int STATE_END_TARGET_ANIMATION_FINISHED =
            getFlagForIndex("STATE_END_TARGET_ANIMATION_FINISHED");

    // Called when the recents animation has been requested to start
    public static final int STATE_RECENTS_ANIMATION_INITIALIZED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_INITIALIZED");

    // Called when the recents animation is started and the TaskAnimationManager has been updated
    // with the controller and targets
    public static final int STATE_RECENTS_ANIMATION_STARTED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_STARTED");

    // Called when the recents animation is canceled
    public static final int STATE_RECENTS_ANIMATION_CANCELED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_CANCELED");

    // Called when the recents animation finishes
    public static final int STATE_RECENTS_ANIMATION_FINISHED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_FINISHED");

    // Always called when the recents animation ends (regardless of cancel or finish)
    public static final int STATE_RECENTS_ANIMATION_ENDED =
            getFlagForIndex("STATE_RECENTS_ANIMATION_ENDED");


    // Needed to interact with the current activity
    private final BaseActivityInterface mActivityInterface;
    private final MultiStateCallback mStateCallback;
    private final int mGestureId;

    private GestureEndTarget mEndTarget;
    // TODO: This can be removed once we stop finishing the animation when starting a new task
    private int mFinishingRecentsAnimationTaskId = -1;

    public GestureState(BaseActivityInterface activityInterface, int gestureId) {
        mActivityInterface = activityInterface;
        mGestureId = gestureId;
        mStateCallback = new MultiStateCallback(STATE_NAMES.toArray(new String[0]));
    }

    public GestureState() {
        // Do nothing, only used for initializing the gesture state prior to user unlock
        mActivityInterface = null;
        mGestureId = -1;
        mStateCallback = new MultiStateCallback(STATE_NAMES.toArray(new String[0]));
    }

    /**
     * Sets the given {@param stateFlag}s.
     */
    public void setState(int stateFlag) {
        mStateCallback.setState(stateFlag);
    }

    /**
     * Adds a callback for when the states matching the given {@param stateMask} is set.
     */
    public void runOnceAtState(int stateMask, Runnable callback) {
        mStateCallback.runOnceAtState(stateMask, callback);
    }

    /**
     * @return the interface to the activity handing the UI updates for this gesture.
     */
    public <T extends BaseDraggingActivity> BaseActivityInterface<T> getActivityInterface() {
        return mActivityInterface;
    }

    /**
     * @return the id for this particular gesture.
     */
    public int getGestureId() {
        return mGestureId;
    }

    /**
     * @return the end target for this gesture (if known).
     */
    public GestureEndTarget getEndTarget() {
        return mEndTarget;
    }

    /**
     * @return whether the current gesture is still running a recents animation to a state in the
     *         Launcher or Recents activity.
     */
    public boolean isRunningAnimationToLauncher() {
        return isRecentsAnimationRunning() && mEndTarget != null && mEndTarget.isLauncher;
    }

    /**
     * Sets the end target of this gesture and immediately notifies the state changes.
     */
    public void setEndTarget(GestureEndTarget target) {
        setEndTarget(target, true /* isAtomic */);
    }

    /**
     * Sets the end target of this gesture, but if {@param isAtomic} is {@code false}, then the
     * caller must explicitly set {@link #STATE_END_TARGET_ANIMATION_FINISHED} themselves.
     */
    public void setEndTarget(GestureEndTarget target, boolean isAtomic) {
        mEndTarget = target;
        mStateCallback.setState(STATE_END_TARGET_SET);
        if (isAtomic) {
            mStateCallback.setState(STATE_END_TARGET_ANIMATION_FINISHED);
        }
    }

    /**
     * @return the id for the task that was about to be launched following the finish of the recents
     * animation.  Only defined between when the finish-recents call was made and the launch
     * activity call is made.
     */
    public int getFinishingRecentsAnimationTaskId() {
        return mFinishingRecentsAnimationTaskId;
    }

    /**
     * Sets the id for the task will be launched after the recents animation is finished. Once the
     * animation has finished then the id will be reset to -1.
     */
    public void setFinishingRecentsAnimationTaskId(int taskId) {
        mFinishingRecentsAnimationTaskId = taskId;
        mStateCallback.runOnceAtState(STATE_RECENTS_ANIMATION_FINISHED, () -> {
            mFinishingRecentsAnimationTaskId = -1;
        });
    }

    /**
     * @return whether the recents animation is started but not yet ended
     */
    public boolean isRecentsAnimationRunning() {
        return mStateCallback.hasStates(STATE_RECENTS_ANIMATION_INITIALIZED) &&
                !mStateCallback.hasStates(STATE_RECENTS_ANIMATION_ENDED);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mStateCallback.setState(STATE_RECENTS_ANIMATION_STARTED);
    }

    @Override
    public void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
        mStateCallback.setState(STATE_RECENTS_ANIMATION_CANCELED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
    }

    @Override
    public void onRecentsAnimationFinished(RecentsAnimationController controller) {
        mStateCallback.setState(STATE_RECENTS_ANIMATION_FINISHED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
    }
}
