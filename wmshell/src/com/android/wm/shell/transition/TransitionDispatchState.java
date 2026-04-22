/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.transition;

import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.annotation.NonNull;
import android.os.IBinder;
import android.util.ArrayMap;
import android.view.WindowManager;
import android.window.TransitionInfo;

import androidx.annotation.IntDef;

import java.util.ArrayList;

public class TransitionDispatchState {
    public static final int NONE = 0;
    // Flag-related errors
    public static final int LOST_RELEVANT_FLAG = 1;
    public static final int CAPTURED_UNRELATED_FLAG = 2;
    // Change-related errors
    public static final int LOST_RELEVANT_CHANGE = 3;
    public static final int CAPTURED_UNRELATED_CHANGE = 4;
    public static final int CAPTURED_CHANGE_IN_WRONG_TRANSITION = 5;

    @IntDef(
            value = {
                NONE,
                LOST_RELEVANT_FLAG,
                CAPTURED_UNRELATED_FLAG,
                LOST_RELEVANT_CHANGE,
                CAPTURED_UNRELATED_CHANGE,
                CAPTURED_CHANGE_IN_WRONG_TRANSITION,
            })
    public @interface ErrorCode {}

    static String toString(@ErrorCode int errorCode) {
        return switch (errorCode) {
            case NONE -> "NONE";
            case LOST_RELEVANT_FLAG -> "LOST_RELEVANT_FLAG";
            case CAPTURED_UNRELATED_FLAG -> "CAPTURED_UNRELATED_FLAG";
            case LOST_RELEVANT_CHANGE -> "LOST_RELEVANT_CHANGE";
            case CAPTURED_UNRELATED_CHANGE -> "CAPTURED_UNRELATED_CHANGE";
            case CAPTURED_CHANGE_IN_WRONG_TRANSITION -> "CAPTURED_CHANGE_IN_WRONG_TRANSITION";
            default -> "UNKNOWN";
        };
    }

    final ArrayMap<Transitions.TransitionHandler, HandlerData>
            mHandlersData = new ArrayMap<>();
    final IBinder mTransition;
    public final TransitionInfo mInfo;

    private static DummyTransitionDispatchState sDummyInstance;

    private TransitionDispatchState() {
        // Can only be used only by dummy instance.
        mTransition = null;
        mInfo = null;
    }

    public TransitionDispatchState(@NonNull IBinder transition, @NonNull TransitionInfo info) {
        mTransition = transition;
        mInfo = info;
    }

    /**
     * @return a dummy instance of {@link TransitionDispatchState} that does nothing.
     */
    public static TransitionDispatchState getDummyInstance() {
        if (sDummyInstance == null) {
            sDummyInstance = new DummyTransitionDispatchState();
        }
        return sDummyInstance;
    }

    /**
     * @param handler the handler that is interested in this transition.
     * @param change  If the handler has consumed the transition, this is a change that has not been
     *                animated.
     *                If the handler has not consumed it, this is a change that the handler would
     *                have been interested in animating.
     * @param errorCode an error code to identify the error type.
     */
    public void addError(@NonNull Transitions.TransitionHandler handler,
                         @NonNull TransitionInfo.Change change, @ErrorCode int errorCode) {
        HandlerData handlerData = getDataOrCreate(handler);
        HandlerData.Error error =
                new HandlerData.Error(change, errorCode);
        handlerData.mErrors.add(error);
    }

    /**
     * @param handler the handler that is interested in this transition.
     * @param flag    a flag that the handler finds relevant, either because it should have been
     *                handled by handler (and it didn't) or because it was not relevant for handling
     *                the transition (which was played by handler).
     * @param errorCode an error code to identify the error type.
     */
    public void addError(@NonNull Transitions.TransitionHandler handler,
                         @WindowManager.TransitionFlags int flag, @ErrorCode int errorCode) {
        HandlerData handlerData = getDataOrCreate(handler);
        HandlerData.Error error =
                new HandlerData.Error(flag, errorCode);
        handlerData.mErrors.add(error);
    }

    /**
     * Sets that {@param handler} has played at least part of this transition.
     */
    public void setHasPlayed(@NonNull Transitions.TransitionHandler handler) {
        HandlerData handlerData = getDataOrCreate(handler);
        handlerData.mHasPlayed = true;
    }

    /**
     * @return true if {@param handler} has played at least part of this transition.
     */
    public boolean hasPlayed(@NonNull Transitions.TransitionHandler handler) {
        HandlerData handlerData = mHandlersData.get(handler);
        if (handlerData == null) {
            return false;
        }
        return handlerData.mHasPlayed;
    }

    /**
     * @return true if {@param handler} has found problems with the handling of this transition.
     */
    public boolean hasErrors(@NonNull Transitions.TransitionHandler handler) {
        HandlerData handlerData = mHandlersData.get(handler);
        if (handlerData == null) {
            return false;
        }
        return !handlerData.mErrors.isEmpty();
    }

    /**
     * Report all the errors found by the handlers to an atom.
     */
    public void reportWarnings() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * @return a string containing the analyzed handlers and the information they collected.
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("Transition #").append(mInfo.getDebugId()).append(": {");
        for (int i = 0; i < mHandlersData.size(); i++) {
            HandlerData handlerData = mHandlersData.valueAt(i);
            if (!handlerData.mErrors.isEmpty() || handlerData.mHasPlayed) {
                // Handler + collected info
                sb.append("\n    ");
                sb.append(handlerData.mHandler.getClass().getSimpleName()).append(": [");
                sb.append("hasPlayed=").append(handlerData.mHasPlayed);
                for (int j = 0; j < handlerData.mErrors.size(); j++) {
                    HandlerData.Error err = handlerData.mErrors.get(j);
                    sb.append(" Errors={");
                    if (err.mChange != null) {
                        sb.append("ChangeError(").append(toString(err.mErrorCode));
                        sb.append(" -- ").append(err.mChange);
                        sb.append("), ");
                    } else if (err.mTransitFlag != 0) {
                        sb.append("FlagError(").append(toString(err.mErrorCode));
                        sb.append(" -- ").append(transitTypeToString(err.mTransitFlag));
                        sb.append("}, ");
                    }
                }
                sb.append("]");
            }
        }
        sb.append("\n}");
        return sb.toString();
    }

    private HandlerData getDataOrCreate(
            @NonNull Transitions.TransitionHandler handler) {
        HandlerData handlerData = mHandlersData.get(handler);
        if (handlerData != null) return handlerData;
        handlerData = new HandlerData(handler);
        mHandlersData.put(handler, handlerData);
        return handlerData;
    }


    /**
     * HandlerData collects errors and information relative to a handler
     * in relation to a transition.
     */
    static class HandlerData {
        /**
         * Collects information about errors found by a handler
         */
        static class Error {
            final TransitionInfo.Change mChange;
            final @WindowManager.TransitionFlags int mTransitFlag;
            final @ErrorCode int mErrorCode;

            Error(int flag, @ErrorCode int errorCode) {
                mTransitFlag = flag;
                mErrorCode = errorCode;
                mChange = null;
            }

            Error(@NonNull TransitionInfo.Change change, @ErrorCode int errorCode) {
                mChange = change;
                mErrorCode = errorCode;
                mTransitFlag = 0;
            }
        }

        final Transitions.TransitionHandler mHandler;
        final ArrayList<Error> mErrors = new ArrayList<>();
        boolean mHasPlayed = false;

        HandlerData(Transitions.TransitionHandler handler) {
            mHandler = handler;
        }
    }

    /**
     * Dummy implementation of {@link TransitionDispatchState} that does nothing.
     * This is used temporary until Transitions.TransitionHandler.startAnimation(...) without
     * TransitionDispatchState is deprecated.
     */
    private static class DummyTransitionDispatchState extends TransitionDispatchState {
        @Override
        public void addError(@NonNull Transitions.TransitionHandler handler,
                             @NonNull TransitionInfo.Change change,
                             @ErrorCode int errorCode) {}

        @Override
        public void addError(@NonNull Transitions.TransitionHandler handler,
                             @WindowManager.TransitionFlags int flag,
                             @ErrorCode int errorCode) {}

        @Override
        public void setHasPlayed(@NonNull Transitions.TransitionHandler handler) {}
    }
}
