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

import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.quickstep.util.ActiveGestureErrorDetector;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Utility class to help manage multiple callbacks based on different states.
 */
public class MultiStateCallback {

    private static final String TAG = "MultiStateCallback";
    public static final boolean DEBUG_STATES = false;

    private final SparseArray<LinkedList<Runnable>> mCallbacks = new SparseArray<>();
    private final SparseArray<ArrayList<Consumer<Boolean>>> mStateChangeListeners =
            new SparseArray<>();

    @NonNull private final TrackedEventsMapper mTrackedEventsMapper;

    private final String[] mStateNames;

    private int mState = 0;

    public MultiStateCallback(String[] stateNames) {
        this(stateNames, stateFlag -> null);
    }

    public MultiStateCallback(
            String[] stateNames,
            @NonNull TrackedEventsMapper trackedEventsMapper) {
        mStateNames = DEBUG_STATES ? stateNames : null;
        mTrackedEventsMapper = trackedEventsMapper;
    }

    /**
     * Adds the provided state flags to the global state on the UI thread and executes any callbacks
     * as a result.
     *
     * Also tracks the provided gesture events for error detection. Each provided event must be
     * associated with one provided state flag.
     */
    public void setStateOnUiThread(int stateFlag) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            setState(stateFlag);
        } else {
            postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> setState(stateFlag));
        }
    }

    /**
     * Adds the provided state flags to the global state and executes any callbacks as a result.
     */
    public void setState(int stateFlag) {
        if (DEBUG_STATES) {
            Log.d(TAG, "[" + System.identityHashCode(this) + "] Adding "
                    + convertToFlagNames(stateFlag) + " to " + convertToFlagNames(mState));
        }
        trackGestureEvents(stateFlag);
        final int oldState = mState;
        mState = mState | stateFlag;

        int count = mCallbacks.size();
        for (int i = 0; i < count; i++) {
            int state = mCallbacks.keyAt(i);

            if ((mState & state) == state) {
                LinkedList<Runnable> callbacks = mCallbacks.valueAt(i);
                while (!callbacks.isEmpty()) {
                    callbacks.pollFirst().run();
                }
            }
        }
        notifyStateChangeListeners(oldState);
    }

    private void trackGestureEvents(int stateFlags) {
        for (int index = 0; (stateFlags >> index) != 0; index++) {
            if ((stateFlags & (1 << index)) == 0) {
                continue;
            }
            ActiveGestureErrorDetector.GestureEvent gestureEvent =
                    mTrackedEventsMapper.getTrackedEventForState(1 << index);
            if (gestureEvent == null) {
                continue;
            }
            if (gestureEvent.mLogEvent) {
                ActiveGestureProtoLogProxy.logDynamicString(
                        gestureEvent.name(), gestureEvent.mTrackEvent ? gestureEvent : null);
            } else if (gestureEvent.mTrackEvent) {
                ActiveGestureLog.INSTANCE.trackEvent(gestureEvent);
            }
        }
    }

    /**
     * Adds the provided state flags to the global state and executes any change handlers
     * as a result.
     */
    public void clearState(int stateFlag) {
        if (DEBUG_STATES) {
            Log.d(TAG, "[" + System.identityHashCode(this) + "] Removing "
                    + convertToFlagNames(stateFlag) + " from " + convertToFlagNames(mState));
        }

        int oldState = mState;
        mState = mState & ~stateFlag;
        notifyStateChangeListeners(oldState);
    }

    private void notifyStateChangeListeners(int oldState) {
        int count = mStateChangeListeners.size();
        for (int i = 0; i < count; i++) {
            int state = mStateChangeListeners.keyAt(i);
            boolean wasOn = (state & oldState) == state;
            boolean isOn = (state & mState) == state;

            if (wasOn != isOn) {
                ArrayList<Consumer<Boolean>> listeners = mStateChangeListeners.valueAt(i);
                for (Consumer<Boolean> listener : listeners) {
                    listener.accept(isOn);
                }
            }
        }
    }

    /**
     * Sets a callback to be run when the provided states in the given {@param stateMask} is
     * enabled. The callback is only run *once*, and if the states are already set at the time of
     * this call then the callback will be made immediately.
     */
    public void runOnceAtState(int stateMask, Runnable callback) {
        if ((mState & stateMask) == stateMask) {
            callback.run();
        } else {
            final LinkedList<Runnable> callbacks;
            if (mCallbacks.indexOfKey(stateMask) >= 0) {
                callbacks = mCallbacks.get(stateMask);
                if (FeatureFlags.IS_STUDIO_BUILD && callbacks.contains(callback)) {
                    throw new IllegalStateException("Existing callback for state found");
                }
            } else {
                callbacks = new LinkedList<>();
                mCallbacks.put(stateMask, callbacks);
            }
            callbacks.add(callback);
        }
    }

    /**
     * Adds a persistent listener to be called states in the given {@param stateMask} are enabled
     * or disabled.
     */
    public void addChangeListener(int stateMask, Consumer<Boolean> listener) {
        final ArrayList<Consumer<Boolean>> listeners;
        if (mStateChangeListeners.indexOfKey(stateMask) >= 0) {
            listeners = mStateChangeListeners.get(stateMask);
        } else {
            listeners = new ArrayList<>();
            mStateChangeListeners.put(stateMask, listeners);
        }
        listeners.add(listener);
    }

    public int getState() {
        return mState;
    }

    public boolean hasStates(int stateMask) {
        return (mState & stateMask) == stateMask;
    }

    private String convertToFlagNames(int flags) {
        StringJoiner joiner = new StringJoiner(", ", "[", " (" + flags + ")]");
        for (int i = 0; i < mStateNames.length; i++) {
            if ((flags & (1 << i)) != 0) {
                joiner.add(mStateNames[i]);
            }
        }
        return joiner.toString();
    }

    public interface TrackedEventsMapper {
        @Nullable ActiveGestureErrorDetector.GestureEvent getTrackedEventForState(int stateflag);
    }
}
