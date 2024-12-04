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
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceControl;
import android.window.WindowContainerToken;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.util.Preconditions;
import com.android.wm.shell.shared.annotations.ShellMainThread;

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
    public static final int UNDEFINED = 0;

    // State for Launcher animating the swipe PiP to home animation.
    public static final int SWIPING_TO_PIP = 1;

    // State for Shell animating enter PiP or jump-cutting to PiP mode after Launcher animation.
    public static final int ENTERING_PIP = 2;

    // State for app finishing drawing in PiP mode as a final step in enter PiP flow.
    public static final int ENTERED_PIP = 3;

    // State to indicate we have scheduled a PiP bounds change transition.
    public static final int SCHEDULED_BOUNDS_CHANGE = 4;

    // State for the start of playing a transition to change PiP bounds. At this point, WM Core
    // is aware of the new PiP bounds, but Shell might still be continuing animating.
    public static final int CHANGING_PIP_BOUNDS = 5;

    // State for finishing animating into new PiP bounds after resize is complete.
    public static final int CHANGED_PIP_BOUNDS = 6;

    // State for starting exiting PiP.
    public static final int EXITING_PIP = 7;

    // State for finishing exit PiP flow.
    public static final int EXITED_PIP = 8;

    private static final int FIRST_CUSTOM_STATE = 1000;

    private int mPrevCustomState = FIRST_CUSTOM_STATE;

    @IntDef(prefix = { "TRANSITION_STATE_" }, value =  {
            UNDEFINED,
            SWIPING_TO_PIP,
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
    // Tokens and leashes
    //

    // pinned PiP task's WC token
    @Nullable
    WindowContainerToken mPipTaskToken;

    // pinned PiP task's leash
    @Nullable
    SurfaceControl mPinnedTaskLeash;

    // Overlay leash potentially used during swipe PiP to home transition;
    // if null while mInSwipePipToHomeTransition is true, then srcRectHint was invalid.
    @Nullable
    private SurfaceControl mSwipePipToHomeOverlay;

    /**
     * An interface to track state updates as we progress through PiP transitions.
     */
    public interface PipTransitionStateChangedListener {

        /** Reports changes in PiP transition state. */
        void onPipTransitionStateChanged(@TransitionState int oldState,
                @TransitionState int newState, @Nullable Bundle extra);
    }

    private final List<PipTransitionStateChangedListener> mCallbacks = new ArrayList<>();

    public PipTransitionState(@ShellMainThread Handler handler) {
        mMainHandler = handler;
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
        if (mState != state) {
            dispatchPipTransitionStateChanged(mState, state, extra);
            mState = state;
        }
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

    private static String stateToString(int state) {
        switch (state) {
            case UNDEFINED: return "undefined";
            case SWIPING_TO_PIP: return "swiping_to_pip";
            case ENTERING_PIP: return "entering-pip";
            case ENTERED_PIP: return "entered-pip";
            case SCHEDULED_BOUNDS_CHANGE: return "scheduled_bounds_change";
            case CHANGING_PIP_BOUNDS: return "changing-bounds";
            case CHANGED_PIP_BOUNDS: return "changed-bounds";
            case EXITING_PIP: return "exiting-pip";
            case EXITED_PIP: return "exited-pip";
        }
        throw new IllegalStateException("Unknown state: " + state);
    }

    @Override
    public String toString() {
        return String.format("PipTransitionState(mState=%s, mInSwipePipToHomeTransition=%b)",
                stateToString(mState), mInSwipePipToHomeTransition);
    }
}
