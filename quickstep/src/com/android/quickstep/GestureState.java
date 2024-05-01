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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import static com.android.launcher3.MotionEventsUtils.isTrackpadFourFingerSwipe;
import static com.android.launcher3.MotionEventsUtils.isTrackpadThreeFingerSwipe;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_BACKGROUND;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET_ALL_APPS;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET_HOME;
import static com.android.quickstep.util.ActiveGestureErrorDetector.GestureEvent.SET_END_TARGET_NEW_TASK;

import android.content.Intent;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.statemanager.BaseState;
import com.android.quickstep.TopTaskTracker.CachedTaskInfo;
import com.android.quickstep.util.ActiveGestureErrorDetector;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Manages the state for an active system gesture, listens for events from the system and Launcher,
 * and fires events when the states change.
 */
public class GestureState implements RecentsAnimationCallbacks.RecentsAnimationListener {

    final Predicate<RemoteAnimationTarget> mLastStartedTaskIdPredicate = new Predicate<>() {
        @Override
        public boolean test(RemoteAnimationTarget targetCompat) {
            for (int taskId : mLastStartedTaskId) {
                if (targetCompat.taskId == taskId) {
                    return true;
                }
            }
            return false;
        }
    };

    /**
     * Defines the end targets of a gesture and the associated state.
     */
    public enum GestureEndTarget {
        HOME(true, LAUNCHER_STATE_HOME, false),

        RECENTS(true, LAUNCHER_STATE_OVERVIEW, true),

        NEW_TASK(false, LAUNCHER_STATE_BACKGROUND, true),

        LAST_TASK(false, LAUNCHER_STATE_BACKGROUND, true),

        ALL_APPS(true, LAUNCHER_STATE_ALLAPPS, false);

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

    private static final String TAG = "GestureState";

    private static final List<String> STATE_NAMES = new ArrayList<>();
    public static final GestureState DEFAULT_STATE = new GestureState();

    private static int FLAG_COUNT = 0;
    private static int getNextStateFlag(String name) {
        if (DEBUG_STATES) {
            STATE_NAMES.add(name);
        }
        int index = 1 << FLAG_COUNT;
        FLAG_COUNT++;
        return index;
    }

    // Called when the end target as been set
    public static final int STATE_END_TARGET_SET =
            getNextStateFlag("STATE_END_TARGET_SET");

    // Called when the end target animation has finished
    public static final int STATE_END_TARGET_ANIMATION_FINISHED =
            getNextStateFlag("STATE_END_TARGET_ANIMATION_FINISHED");

    // Called when the recents animation has been requested to start
    public static final int STATE_RECENTS_ANIMATION_INITIALIZED =
            getNextStateFlag("STATE_RECENTS_ANIMATION_INITIALIZED");

    // Called when the recents animation is started and the TaskAnimationManager has been updated
    // with the controller and targets
    public static final int STATE_RECENTS_ANIMATION_STARTED =
            getNextStateFlag("STATE_RECENTS_ANIMATION_STARTED");

    // Called when the recents animation is canceled
    public static final int STATE_RECENTS_ANIMATION_CANCELED =
            getNextStateFlag("STATE_RECENTS_ANIMATION_CANCELED");

    // Called when the recents animation finishes
    public static final int STATE_RECENTS_ANIMATION_FINISHED =
            getNextStateFlag("STATE_RECENTS_ANIMATION_FINISHED");

    // Always called when the recents animation ends (regardless of cancel or finish)
    public static final int STATE_RECENTS_ANIMATION_ENDED =
            getNextStateFlag("STATE_RECENTS_ANIMATION_ENDED");

    // Called when RecentsView stops scrolling and settles on a TaskView.
    public static final int STATE_RECENTS_SCROLLING_FINISHED =
            getNextStateFlag("STATE_RECENTS_SCROLLING_FINISHED");

    // Needed to interact with the current activity
    private final Intent mHomeIntent;
    private final Intent mOverviewIntent;
    private final BaseContainerInterface mContainerInterface;
    private final MultiStateCallback mStateCallback;
    private final int mGestureId;

    public enum TrackpadGestureType {
        NONE,
        THREE_FINGER,
        FOUR_FINGER;

        public static TrackpadGestureType getTrackpadGestureType(MotionEvent event) {
            if (isTrackpadThreeFingerSwipe(event)) {
                return TrackpadGestureType.THREE_FINGER;
            }
            if (isTrackpadFourFingerSwipe(event)) {
                return TrackpadGestureType.FOUR_FINGER;
            }

            return TrackpadGestureType.NONE;
        }
    }

    private TrackpadGestureType mTrackpadGestureType = TrackpadGestureType.NONE;
    private CachedTaskInfo mRunningTask;
    private GestureEndTarget mEndTarget;
    private RemoteAnimationTarget[] mLastAppearedTaskTargets;
    private Set<Integer> mPreviouslyAppearedTaskIds = new HashSet<>();
    private int[] mLastStartedTaskId = new int[]{INVALID_TASK_ID, INVALID_TASK_ID};
    private RecentsAnimationController mRecentsAnimationController;
    private HashMap<Integer, ThumbnailData> mRecentsAnimationCanceledSnapshots;

    /** The time when the swipe up gesture is triggered. */
    private final long mSwipeUpStartTimeMs = SystemClock.uptimeMillis();

    private boolean mHandlingAtomicEvent;
    private boolean mIsInExtendedSlopRegion;

    public GestureState(OverviewComponentObserver componentObserver, int gestureId) {
        mHomeIntent = componentObserver.getHomeIntent();
        mOverviewIntent = componentObserver.getOverviewIntent();
        mContainerInterface = componentObserver.getActivityInterface();
        mStateCallback = new MultiStateCallback(
                STATE_NAMES.toArray(new String[0]), GestureState::getTrackedEventForState);
        mGestureId = gestureId;
    }

    public GestureState(GestureState other) {
        mHomeIntent = other.mHomeIntent;
        mOverviewIntent = other.mOverviewIntent;
        mContainerInterface = other.mContainerInterface;
        mStateCallback = other.mStateCallback;
        mGestureId = other.mGestureId;
        mRunningTask = other.mRunningTask;
        mEndTarget = other.mEndTarget;
        mLastAppearedTaskTargets = other.mLastAppearedTaskTargets;
        mPreviouslyAppearedTaskIds = other.mPreviouslyAppearedTaskIds;
        mLastStartedTaskId = other.mLastStartedTaskId;
    }

    public GestureState() {
        // Do nothing, only used for initializing the gesture state prior to user unlock
        mHomeIntent = new Intent();
        mOverviewIntent = new Intent();
        mContainerInterface = null;
        mStateCallback = new MultiStateCallback(
                STATE_NAMES.toArray(new String[0]), GestureState::getTrackedEventForState);
        mGestureId = -1;
    }

    @Nullable
    private static ActiveGestureErrorDetector.GestureEvent getTrackedEventForState(int stateFlag) {
        if (stateFlag == STATE_END_TARGET_ANIMATION_FINISHED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_END_TARGET_ANIMATION_FINISHED;
        } else if (stateFlag == STATE_RECENTS_SCROLLING_FINISHED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_RECENTS_SCROLLING_FINISHED;
        } else if (stateFlag == STATE_RECENTS_ANIMATION_CANCELED) {
            return ActiveGestureErrorDetector.GestureEvent.STATE_RECENTS_ANIMATION_CANCELED;
        }
        return null;
    }

    /**
     * @return whether the gesture state has the provided {@param stateMask} flags set.
     */
    public boolean hasState(int stateMask) {
        return mStateCallback.hasStates(stateMask);
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
     * @return the intent for the Home component.
     */
    public Intent getHomeIntent() {
        return mHomeIntent;
    }

    /**
     * @return the intent for the Overview component.
     */
    public Intent getOverviewIntent() {
        return mOverviewIntent;
    }

    /**
     * @return the interface to the activity handing the UI updates for this gesture.
     */
    public <S extends BaseState<S>, T extends RecentsViewContainer>
            BaseContainerInterface<S, T> getContainerInterface() {
        return mContainerInterface;
    }

    /**
     * @return the id for this particular gesture.
     */
    public int getGestureId() {
        return mGestureId;
    }

    /**
     * Sets if the gesture is is from the trackpad, if so, whether 3-finger, or 4-finger
     */
    public void setTrackpadGestureType(TrackpadGestureType trackpadGestureType) {
        mTrackpadGestureType = trackpadGestureType;
    }

    public boolean isTrackpadGesture() {
        return mTrackpadGestureType != TrackpadGestureType.NONE;
    }

    public boolean isThreeFingerTrackpadGesture() {
        return mTrackpadGestureType == TrackpadGestureType.THREE_FINGER;
    }

    public boolean isFourFingerTrackpadGesture() {
        return mTrackpadGestureType == TrackpadGestureType.FOUR_FINGER;
    }

    /**
     * @return the running task for this gesture.
     */
    @Nullable
    public CachedTaskInfo getRunningTask() {
        return mRunningTask;
    }

    /**
     * @param getMultipleTasks Whether multiple tasks or not are to be returned (for split)
     * @return the running task ids for this gesture.
     */
    public int[] getRunningTaskIds(boolean getMultipleTasks) {
        if (mRunningTask == null) {
            return new int[]{INVALID_TASK_ID, INVALID_TASK_ID};
        } else {
            int cachedTasksSize = mRunningTask.mAllCachedTasks.size();
            int count = Math.min(cachedTasksSize, getMultipleTasks ? 2 : 1);
            int[] runningTaskIds = new int[count];
            for (int i = 0; i < count; i++) {
                runningTaskIds[i] = mRunningTask.mAllCachedTasks.get(i).taskId;
            }
            return runningTaskIds;
        }
    }

    /**
     * @see #getRunningTaskIds(boolean)
     * @return the single top-most running taskId for this gesture
     */
    public int getTopRunningTaskId() {
        return getRunningTaskIds(false /*getMultipleTasks*/)[0];
    }

    /**
     * Updates the running task for the gesture to be the given {@param runningTask}.
     */
    public void updateRunningTask(@NonNull CachedTaskInfo runningTask) {
        mRunningTask = runningTask;
    }

    /**
     * Updates the last task that appeared during this gesture.
     */
    public void updateLastAppearedTaskTargets(RemoteAnimationTarget[] lastAppearedTaskTargets) {
        mLastAppearedTaskTargets = lastAppearedTaskTargets;
        for (RemoteAnimationTarget target : lastAppearedTaskTargets) {
            if (target == null) {
                continue;
            }
            mPreviouslyAppearedTaskIds.add(target.taskId);
        }
    }

    /**
     * @return The id of the task that appeared during this gesture.
     */
    public int[] getLastAppearedTaskIds() {
        if (mLastAppearedTaskTargets == null) {
            return new int[]{INVALID_TASK_ID, INVALID_TASK_ID};
        } else {
            return Arrays.stream(mLastAppearedTaskTargets)
                    .mapToInt(target -> target != null ? target.taskId : INVALID_TASK_ID).toArray();
        }
    }

    public void updatePreviouslyAppearedTaskIds(Set<Integer> previouslyAppearedTaskIds) {
        mPreviouslyAppearedTaskIds = previouslyAppearedTaskIds;
    }

    public Set<Integer> getPreviouslyAppearedTaskIds() {
        return mPreviouslyAppearedTaskIds;
    }

    /**
     * Updates the last task that we started via startActivityFromRecents() during this gesture.
     */
    public void updateLastStartedTaskIds(int[] lastStartedTaskId) {
        mLastStartedTaskId = lastStartedTaskId;
    }

    /**
     * @return The id of the task that was most recently started during this gesture, or -1 if
     * no task has been started yet (i.e. we haven't settled on a new task).
     */
    public int[] getLastStartedTaskIds() {
        return mLastStartedTaskId;
    }

    /**
     * @return the end target for this gesture (if known).
     */
    public GestureEndTarget getEndTarget() {
        return mEndTarget;
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
        ActiveGestureLog.INSTANCE.addLog(
                new ActiveGestureLog.CompoundString("setEndTarget ")
                        .append(mEndTarget.name()),
                /* gestureEvent= */ SET_END_TARGET);
        switch (mEndTarget) {
            case HOME:
                ActiveGestureLog.INSTANCE.trackEvent(SET_END_TARGET_HOME);
                break;
            case NEW_TASK:
                ActiveGestureLog.INSTANCE.trackEvent(SET_END_TARGET_NEW_TASK);
                break;
            case ALL_APPS:
                ActiveGestureLog.INSTANCE.trackEvent(SET_END_TARGET_ALL_APPS);
                break;
            case LAST_TASK:
            case RECENTS:
            default:
                // No-Op
        }
        if (isAtomic) {
            mStateCallback.setState(STATE_END_TARGET_ANIMATION_FINISHED);
        }
    }

    /**
     * Indicates if the gesture is handling an atomic event like a click and not a
     * user controlled gesture.
     */
    public void setHandlingAtomicEvent(boolean handlingAtomicEvent) {
        mHandlingAtomicEvent = handlingAtomicEvent;
    }

    /**
     * Returns true if the gesture is handling an atomic event like a click and not a
     * user controlled gesture.
     */
    public boolean isHandlingAtomicEvent() {
        return mHandlingAtomicEvent;
    }

    /**
     * @return whether the current gesture is still running a recents animation to a state in the
     *         Launcher or Recents activity.
     */
    public boolean isRunningAnimationToLauncher() {
        return isRecentsAnimationRunning() && mEndTarget != null && mEndTarget.isLauncher;
    }

    /**
     * @return whether the recents animation is started but not yet ended
     */
    public boolean isRecentsAnimationRunning() {
        return mStateCallback.hasStates(STATE_RECENTS_ANIMATION_STARTED)
                && !mStateCallback.hasStates(STATE_RECENTS_ANIMATION_ENDED);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = controller;
        mStateCallback.setState(STATE_RECENTS_ANIMATION_STARTED);
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        mRecentsAnimationCanceledSnapshots = thumbnailDatas;
        mStateCallback.setState(STATE_RECENTS_ANIMATION_CANCELED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
        if (mRecentsAnimationCanceledSnapshots != null) {
            // Clean up the screenshot to finalize the recents animation cancel
            if (mRecentsAnimationController != null) {
                mRecentsAnimationController.cleanupScreenshot();
            }
            mRecentsAnimationCanceledSnapshots = null;
        }
    }

    @Override
    public void onRecentsAnimationFinished(RecentsAnimationController controller) {
        mStateCallback.setState(STATE_RECENTS_ANIMATION_FINISHED);
        mStateCallback.setState(STATE_RECENTS_ANIMATION_ENDED);
    }

    /**
     * Set whether it's in long press nav handle (LPNH)'s extended touch slop region, e.g., second
     * stage region in order to continue respect LPNH and ignore other touch slop logic.
     * This will only be set to true when flag ENABLE_LPNH_TWO_STAGES is turned on.
     */
    public void setIsInExtendedSlopRegion(boolean isInExtendedSlopRegion) {
        if (DeviceConfigWrapper.get().getEnableLpnhTwoStages()) {
            mIsInExtendedSlopRegion = isInExtendedSlopRegion;
        }
    }

    /**
     * Returns whether it's in LPNH's extended touch slop region. This is only valid when flag
     * ENABLE_LPNH_TWO_STAGES is turned on.
     */
    public boolean isInExtendedSlopRegion() {
        return mIsInExtendedSlopRegion;
    }

    /**
     * Returns and clears the canceled animation thumbnail data. This call only returns a value
     * while STATE_RECENTS_ANIMATION_CANCELED state is being set, and the caller is responsible for
     * calling {@link RecentsAnimationController#cleanupScreenshot()}.
     */
    @Nullable
    HashMap<Integer, ThumbnailData> consumeRecentsAnimationCanceledSnapshot() {
        if (mRecentsAnimationCanceledSnapshots != null) {
            HashMap<Integer, ThumbnailData> data =
                    new HashMap<Integer, ThumbnailData>(mRecentsAnimationCanceledSnapshots);
            mRecentsAnimationCanceledSnapshots = null;
            return data;
        }
        return null;
    }

    long getSwipeUpStartTimeMs() {
        return mSwipeUpStartTimeMs;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "GestureState:");
        pw.println(prefix + "\tgestureID=" + mGestureId);
        pw.println(prefix + "\trunningTask=" + mRunningTask);
        pw.println(prefix + "\tendTarget=" + mEndTarget);
        pw.println(prefix + "\tlastAppearedTaskTargetId="
                + Arrays.toString(mLastAppearedTaskTargets));
        pw.println(prefix + "\tlastStartedTaskId=" + Arrays.toString(mLastStartedTaskId));
        pw.println(prefix + "\tisRecentsAnimationRunning=" + isRecentsAnimationRunning());
    }
}
