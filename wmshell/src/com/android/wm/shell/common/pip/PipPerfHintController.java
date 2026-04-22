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

package com.android.wm.shell.common.pip;

import static android.window.SystemPerformanceHinter.HINT_SF;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.window.SystemPerformanceHinter;
import android.window.SystemPerformanceHinter.HighPerfSession;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.io.PrintWriter;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Manages system performance hints for PiP CUJs and interactions.
 */
public class PipPerfHintController {
    private static final String TAG = PipPerfHintController.class.getSimpleName();

    // Delay until signal about a session cleanup is sent.
    private static final int SESSION_TIMEOUT_DELAY = 20_000;

    // Maximum number of possible high perf session.
    private static final int SESSION_POOL_SIZE = 20;

    private final SystemPerformanceHinter mSystemPerformanceHinter;
    @NonNull
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    @NonNull
    private final ShellExecutor mMainExecutor;


    public PipPerfHintController(@NonNull PipDisplayLayoutState pipDisplayLayoutState,
            @ShellMainThread ShellExecutor mainExecutor,
            @NonNull SystemPerformanceHinter systemPerformanceHinter) {
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mMainExecutor = mainExecutor;
        mSystemPerformanceHinter = systemPerformanceHinter;
    }

    /**
     * Starts a high perf session.
     *
     * @param timeoutCallback an optional callback to be executed upon session timeout.
     * @return a wrapper around the session to allow for early closing; null if no free sessions
     * left available in the pool.
     */
    @Nullable
    public PipHighPerfSession startSession(@Nullable Consumer<PipHighPerfSession> timeoutCallback,
            String reason) {
        if (PipHighPerfSession.getActiveSessionsCount() == SESSION_POOL_SIZE) {
            return null;
        }

        HighPerfSession highPerfSession = mSystemPerformanceHinter.startSession(HINT_SF,
                mPipDisplayLayoutState.getDisplayId(), "pip-high-perf-session");
        PipHighPerfSession pipHighPerfSession = new PipHighPerfSession(highPerfSession, reason);

        if (timeoutCallback != null) {
            mMainExecutor.executeDelayed(() -> {
                if (PipHighPerfSession.hasClosedOrFinalized(pipHighPerfSession)) {
                    // If the session is either directly closed or GC collected before timeout
                    // was reached, do not send the timeout callback.
                    return;
                }
                // The session hasn't been closed yet, so do that now, along with any cleanup.
                pipHighPerfSession.close();
                ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE, "%s: high perf session %s timed out", TAG,
                        pipHighPerfSession.toString());
                timeoutCallback.accept(pipHighPerfSession);
            }, SESSION_TIMEOUT_DELAY);
        }
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE, "%s: high perf session %s is started",
                TAG, pipHighPerfSession.toString());
        return pipHighPerfSession;
    }

    /**
     * Dumps the inner state.
     */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "activeSessionCount="
                + PipHighPerfSession.getActiveSessionsCount());
    }

    /**
     * A wrapper around {@link HighPerfSession} to keep track of some extra metadata about
     * the session's status.
     */
    public class PipHighPerfSession implements AutoCloseable{

        // THe actual HighPerfSession we wrap around.
        private final HighPerfSession mSession;

        private final String mReason;

        /**
         * Keeps track of all active sessions using weakly referenced keys.
         * This makes sure that that sessions do not get accidentally leaked if not closed.
         */
        private static Map<PipHighPerfSession, Boolean> sActiveSessions = new WeakHashMap<>();

        private PipHighPerfSession(HighPerfSession session, String reason) {
            mSession = session;
            mReason = reason;
            sActiveSessions.put(this, true);
        }

        /**
         * Closes a high perf session.
         */
        @Override
        public void close() {
            sActiveSessions.remove(this);
            mSession.close();
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: high perf session %s is closed",
                    TAG, toString());
        }

        @Override
        public void finalize() {
            // The entry should be removed from the weak hash map as well by default.
            mSession.close();
        }

        @Override
        public String toString() {
            return "[" + super.toString() + "] initially started due to: " + mReason;
        }

        private static boolean hasClosedOrFinalized(PipHighPerfSession pipHighPerfSession) {
            return !sActiveSessions.containsKey(pipHighPerfSession);
        }

        private static int getActiveSessionsCount() {
            return sActiveSessions.size();
        }
    }
}
