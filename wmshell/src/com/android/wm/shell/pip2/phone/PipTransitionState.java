/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone;

import android.annotation.IntDef;
import android.app.TaskInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Contains the state relevant to carry out or probe the status of PiP transitions.
 *
 * <p>Existing and new PiP components can subscribe to PiP transition related state changes
 * via <code>PipTransitionStateChangedListener</code>.</p>
 *
 * <p><code>PipTransitionState</code> users shouldn't rely on listener execution ordering.
 * For example, if a class <code>Foo</code> wants to change some arbitrary state A that belongs
 * to some other class <code>Bar</code>, a special care must be given when manipulating state A in
 * <code>Foo#onPipTransitionStateChanged()</code>, since that's the responsibility of
 * the class <code>Bar</code>.</p>
 *
 * <p>Hence, the recommended usage for classes who want to subscribe to
 * <code>PipTransitionState</code> changes is to manipulate only their own internal state or
 * <code>PipTransitionState</code> state.</p>
 *
 * <p>If there is some state that must be manipulated in another class <code>Bar</code>, it should
 * just be moved to <code>PipTransitionState</code> and become a shared state
 * between Foo and Bar.</p>
 *
 * <p>Moreover, <code>onPipTransitionStateChanged(oldState, newState, extra)</code>
 * receives a <code>Bundle</code> extra object that can be optionally set via
 * <code>setState(state, extra)</code>. This can be used to resolve extra information to update
 * relevant internal or <code>PipTransitionState</code> state. However, each listener
 * needs to check for whether the extra passed is correct for a particular state,
 * and throw an <code>IllegalStateException</code> otherwise.</p>
 */
public class PipTransitionState {
    private static final String TAG = PipTransitionState.class.getSimpleName();

    public static final int UNDEFINED = 0;

    // State for Launcher animating the swipe PiP to home animation.
    public static final int SWIPING_TO_PIP = 1;

    // State for scheduling enter PiP transition; could be after SWIPING_TO_PIP
    public static final int SCHEDULED_ENTER_PIP = 2;

    // State for Shell animating enter PiP or jump-cutting to PiP mode after Launcher animation.
    public static final int ENTERING_PIP = 3;

    // State for app finishing drawing in PiP mode as a final step in enter PiP flow.
    public static final int ENTERED_PIP = 4;

    // State to indicate we have scheduled a PiP bounds change transition.
    public static final int SCHEDULED_BOUNDS_CHANGE = 5;

    // State for the start of playing a transition to change PiP bounds. At this point, WM Core
    // is aware of the new PiP bounds, but Shell might still be continuing animating.
    public static final int CHANGING_PIP_BOUNDS = 6;

    // State for finishing animating into new PiP bounds after resize is complete.
    public static final int CHANGED_PIP_BOUNDS = 7;

    // State for starting exiting PiP.
    public static final int EXITING_PIP = 8;

    // State for finishing exit PiP flow.
    public static final int EXITED_PIP = 9;

    private static final int FIRST_CUSTOM_STATE = 1000;

    private int mPrevCustomState = FIRST_CUSTOM_STATE;

    @IntDef(prefix = { "TRANSITION_STATE_" }, value =  {
            UNDEFINED,
            SWIPING_TO_PIP,
            SCHEDULED_ENTER_PIP,
            ENTERING_PIP,
            ENTERED_PIP,
            SCHEDULED_BOUNDS_CHANGE,
            CHANGING_PIP_BOUNDS,
            CHANGED_PIP_BOUNDS,
            EXITING_PIP,
            EXITED_PIP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface TransitionState {}

    @TransitionState
    private int mState;

    //
    // Dependencies
    //

    @ShellMainThread
    private final Handler mMainHandler;

    private final PipDesktopState mPipDesktopState;

    //
    // Swipe up to enter PiP related state
    //

    // true if Launcher has started swipe PiP to home animation
    private boolean mInSwipePipToHomeTransition;

    // App bounds used when as a starting point to swipe PiP to home animation in Launcher;
    // these are also used to calculate the app icon overlay buffer size.
    @NonNull
    private final Rect mSwipePipToHomeAppBounds = new Rect();

    //
    // Task related caches
    //

    // pinned PiP task's leash
    @Nullable
    private SurfaceControl mPinnedTaskLeash;

    // pinned PiP task info
    @Nullable
    private TaskInfo mPipTaskInfo;

    // PiP candidate task info sent to Shell during transition request;
    // this might not be the same as mPipTaskInfo, if in multi-activity case for example.
    @Nullable
    private TaskInfo mPipCandidateTaskInfo;

    // Overlay leash potentially used during swipe PiP to home transition;
    // if null while mInSwipePipToHomeTransition is true, then srcRectHint was invalid.
    @Nullable
    private SurfaceControl mSwipePipToHomeOverlay;

    //
    // Scheduling-related state
    //
    @Nullable
    private Runnable mOnIdlePipTransitionStateRunnable;

    private boolean mInFixedRotation = false;

    /*
     * A flag to keep track of the period between a display change being requested and the point
     * when display change transition starts playing.
     * We keep track of this separately from bounds change states, since display change transition
     * request can come in at any point while a PiP resize is still scheduled or running.
     */
    private boolean mIsDisplayChangeScheduled = false;

    /**
     * An interface to track state updates as we progress through PiP transitions.
     */
    public interface PipTransitionStateChangedListener {

        /** Reports changes in PiP transition state. */
        void onPipTransitionStateChanged(@TransitionState int oldState,
                @TransitionState int newState, @Nullable Bundle extra);
    }

    private final List<PipTransitionStateChangedListener> mCallbacks = new ArrayList<>();

    public PipTransitionState(@ShellMainThread Handler handler, PipDesktopState pipDesktopState) {
        mMainHandler = handler;
        mPipDesktopState = pipDesktopState;
    }

    /**
     * @return the state of PiP in the context of transitions.
     */
    @TransitionState
    public int getState() {
        return mState;
    }

    /**
     * Sets the state of PiP in the context of transitions.
     */
    public void setState(@TransitionState int state) {
        setState(state, null /* extra */);
    }

    /**
     * Sets the state of PiP in the context of transitions
     *
     * @param extra a bundle passed to the subscribed listeners to resolve/cache extra info.
     */
    public void setState(@TransitionState int state, @Nullable Bundle extra) {
        if (state == ENTERING_PIP || state == SWIPING_TO_PIP
                || state == SCHEDULED_BOUNDS_CHANGE || state == CHANGING_PIP_BOUNDS) {
            // States listed above require extra bundles to be provided.
            Preconditions.checkArgument(extra != null && !extra.isEmpty(),
                    "No extra bundle for " + stateToString(state) + " state.");
        }
        if (!shouldTransitionToState(state)) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Attempted to transition to an invalid state=%s, while in %s",
                    TAG, stateToString(state), this);
            return;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s setState from=%s to=%s",
                TAG, stateToString(mState), stateToString(state));
        if (mState != state) {
            final int prevState = mState;
            mState = state;
            dispatchPipTransitionStateChanged(prevState, mState, extra);
        }

        maybeRunOnIdlePipTransitionStateCallback();
    }

    /**
     * Posts the state update for PiP in the context of transitions onto the main handler.
     *
     * <p>This is done to guarantee that any callback dispatches for the present state are
     * complete. This is relevant for states that have multiple listeners, such as
     * <code>SCHEDULED_BOUNDS_CHANGE</code> that helps turn off touch interactions along with
     * the actual transition scheduling.</p>
     */
    public void postState(@TransitionState int state) {
        postState(state, null /* extra */);
    }

    /**
     * Posts the state update for PiP in the context of transitions onto the main handler.
     *
     * <p>This is done to guarantee that any callback dispatches for the present state are
     * complete. This is relevant for states that have multiple listeners, such as
     * <code>SCHEDULED_BOUNDS_CHANGE</code> that helps turn off touch interactions along with
     * the actual transition scheduling.</p>
     *
     * @param extra a bundle passed to the subscribed listeners to resolve/cache extra info.
     */
    public void postState(@TransitionState int state, @Nullable Bundle extra) {
        mMainHandler.post(() -> setState(state, extra));
    }

    private void dispatchPipTransitionStateChanged(@TransitionState int oldState,
            @TransitionState int newState, @Nullable Bundle extra) {
        mCallbacks.forEach(l -> l.onPipTransitionStateChanged(oldState, newState, extra));
    }

    /**
     * Schedule a callback to run when in a valid idle PiP state.
     *
     * <p>We only allow for one callback to be scheduled to avoid cases with multiple transitions
     * being scheduled. For instance, if user double taps and IME shows, this would
     * schedule a bounds change transition for IME appearing.</p>
     *
     * <p>Only on-idle runnable can be scheduled at a time, so attempting to schedule a new one
     * in quick succession should remove the previous one from the message queue.</p>
     */
    public void setOnIdlePipTransitionStateRunnable(
            @Nullable Runnable onIdlePipTransitionStateRunnable) {
        mMainHandler.removeMessages(PipTransitionState.class.hashCode());
        mOnIdlePipTransitionStateRunnable = onIdlePipTransitionStateRunnable;
        maybeRunOnIdlePipTransitionStateCallback();
    }

    private void maybeRunOnIdlePipTransitionStateCallback() {
        if (mOnIdlePipTransitionStateRunnable != null && isPipStateIdle()) {
            final Message msg = mMainHandler.obtainMessage(PipTransitionState.class.hashCode());
            msg.setCallback(mOnIdlePipTransitionStateRunnable);
            mMainHandler.sendMessage(msg);
            mOnIdlePipTransitionStateRunnable = null;
        }
    }

    @VisibleForTesting
    @Nullable
    Runnable getOnIdlePipTransitionStateRunnable() {
        return mOnIdlePipTransitionStateRunnable;
    }

    /**
     * Adds a {@link PipTransitionStateChangedListener} for future PiP transition state updates.
     */
    public void addPipTransitionStateChangedListener(PipTransitionStateChangedListener listener) {
        if (mCallbacks.contains(listener)) {
            return;
        }
        mCallbacks.add(listener);
    }

    /**
     * @return true if provided {@link PipTransitionStateChangedListener}
     * is registered before removing it.
     */
    public boolean removePipTransitionStateChangedListener(
            PipTransitionStateChangedListener listener) {
        return mCallbacks.remove(listener);
    }

    /**
     * @return true if we have fully entered PiP.
     */
    public boolean isInPip() {
        return mState > ENTERING_PIP && mState < EXITING_PIP;
    }

    /**
     * @return true if we have either scheduled enter PiP or are animating the entering.
     */
    public boolean isEnterPipScheduled() {
        return mState == PipTransitionState.ENTERING_PIP
                || mState == PipTransitionState.SCHEDULED_ENTER_PIP;
    }


    void setSwipePipToHomeState(@Nullable SurfaceControl overlayLeash,
            @NonNull Rect appBounds) {
        mInSwipePipToHomeTransition = true;
        if (overlayLeash != null && !appBounds.isEmpty()) {
            mSwipePipToHomeOverlay = overlayLeash;
            mSwipePipToHomeAppBounds.set(appBounds);
        }
    }

    void resetSwipePipToHomeState() {
        mInSwipePipToHomeTransition = false;
        mSwipePipToHomeOverlay = null;
        mSwipePipToHomeAppBounds.setEmpty();
    }

    @Nullable
    public WindowContainerToken getPipTaskToken() {
        return mPipTaskInfo != null ? mPipTaskInfo.getToken() : null;
    }

    @Nullable SurfaceControl getPinnedTaskLeash() {
        return mPinnedTaskLeash;
    }

    void setPinnedTaskLeash(@Nullable SurfaceControl leash) {
        if (!com.android.window.flags2.Flags.releaseAllTransitionSurfaces()) {
            mPinnedTaskLeash = leash;
            return;
        }
        if (mPinnedTaskLeash != null) {
            if (leash != null && leash.isSameSurface(mPinnedTaskLeash)) {
                return;
            }
            mPinnedTaskLeash.release();
        }
        mPinnedTaskLeash = leash != null
                ? new SurfaceControl(leash, "PipTransitionState") : null;
    }

    @Nullable public TaskInfo getPipTaskInfo() {
        return mPipTaskInfo;
    }

    void setPipTaskInfo(@Nullable TaskInfo pipTaskInfo) {
        mPipTaskInfo = pipTaskInfo;
    }

    @Nullable TaskInfo getPipCandidateTaskInfo() {
        return mPipCandidateTaskInfo;
    }

    void setPipCandidateTaskInfo(@Nullable TaskInfo pipCandidateTaskInfo) {
        mPipCandidateTaskInfo = pipCandidateTaskInfo;
    }

    /**
     * @return true if either in swipe or button-nav fixed rotation.
     */
    public boolean isInFixedRotation() {
        return mInFixedRotation;
    }

    /**
     * Sets the fixed rotation flag.
     */
    public void setInFixedRotation(boolean inFixedRotation) {
        mInFixedRotation = inFixedRotation;
        if (!inFixedRotation) {
            maybeRunOnIdlePipTransitionStateCallback();
        }
    }

    /**
     * @return true if PiP state affecting display change has been requested but hasn't played yet.
     */
    public boolean isDisplayChangeScheduled() {
        return mIsDisplayChangeScheduled;
    }

    /**
     * Sets the display change scheduled flag.
     *
     * The caller is expected to:
     * <ul>
     *   <li>reset this flag once display change transition starts playing,</li>
     *   <li>synchronously set PiP transition state to SCHEDULED_BOUNDS_CHANGE,
     *   putting PiP back into a non-idle state.</li>
     *   <li>progress as usual to CHANGING_PIP_BOUNDS followed by CHANGED_PIP_BOUNDS states
     *   to put PiP back into an idle state</li>
     * </ul>
     *
     * <p>Note: this won't run the on-idle runnable as we are expected to be put into a non-idle
     * state immediately after.</p>
     */
    public void setIsDisplayChangeScheduled(boolean isDisplayChangeScheduled) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: Set mIsDisplayChangeScheduled=%b", TAG, isDisplayChangeScheduled);
        mIsDisplayChangeScheduled = isDisplayChangeScheduled;
    }

    /**
     * @return true if in swipe PiP to home. Note that this is true until overlay fades if used too.
     */
    public boolean isInSwipePipToHomeTransition() {
        return mInSwipePipToHomeTransition;
    }

    /**
     * @return the overlay used during swipe PiP to home for invalid srcRectHints in auto-enter PiP;
     * null if srcRectHint provided is valid.
     */
    @Nullable
    public SurfaceControl getSwipePipToHomeOverlay() {
        return mSwipePipToHomeOverlay;
    }

    /**
     * @return app bounds used to calculate
     */
    @NonNull
    public Rect getSwipePipToHomeAppBounds() {
        return mSwipePipToHomeAppBounds;
    }

    /**
     * @return a custom state solely for internal use by the caller.
     */
    @TransitionState
    public int getCustomState() {
        return ++mPrevCustomState;
    }

    @VisibleForTesting
    boolean shouldTransitionToState(@TransitionState int newState) {
        switch (newState) {
            case SCHEDULED_ENTER_PIP:
                // This state only makes sense when we are not initially in PiP or not entering PiP.
                // PiP can also be replaced upon direct enter, but scheduling like this can happen
                // while an animation is running if PiP is not idle, so we should not
                // disrupt the state machine while an animation is in between its state updates.
                return (!isInPip() && mState != ENTERING_PIP) || isPipStateIdle();
            case SCHEDULED_BOUNDS_CHANGE:
                // Allow scheduling bounds change only when both of these are true:
                // - while in PiP, except for if another bounds change was scheduled but hasn't
                //   started playing yet
                // - there is no drag-to-desktop gesture in progress; otherwise the PiP resize
                //   transition will block the drag-to-desktop transitions from finishing
                return isPipStateIdle() && !mPipDesktopState.isDragToDesktopInProgress();
            default:
                return true;
        }
    }

    private static String stateToString(int state) {
        switch (state) {
            case UNDEFINED: return "undefined";
            case SWIPING_TO_PIP: return "swiping_to_pip";
            case SCHEDULED_ENTER_PIP: return "scheduled_enter_pip";
            case ENTERING_PIP: return "entering-pip";
            case ENTERED_PIP: return "entered-pip";
            case SCHEDULED_BOUNDS_CHANGE: return "scheduled_bounds_change";
            case CHANGING_PIP_BOUNDS: return "changing-bounds";
            case CHANGED_PIP_BOUNDS: return "changed-bounds";
            case EXITING_PIP: return "exiting-pip";
            case EXITED_PIP: return "exited-pip";
            default: return "custom-state(" + state + ")";
        }
    }

    public boolean isPipStateIdle() {
        // This needs to be a valid in-PiP state that isn't a transient state.
        return (mState == ENTERED_PIP || mState == CHANGED_PIP_BOUNDS)
                && !isInFixedRotation() && !isDisplayChangeScheduled();
    }

    @Override
    public String toString() {
        return String.format("PipTransitionState(mState=%s, mInSwipePipToHomeTransition=%b, "
                + "mIsDisplayChangeScheduled=%b, mInFixedRotation=%b",
                stateToString(mState), mInSwipePipToHomeTransition, mIsDisplayChangeScheduled,
                        mInFixedRotation);
    }

    /** Dumps internal state. */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mState=" + stateToString(mState));
        pw.println(innerPrefix + "mInFixedRotation=" + mInFixedRotation);
        pw.println(innerPrefix + "mIsDisplayChangeScheduled="
                + mIsDisplayChangeScheduled);
    }
}
