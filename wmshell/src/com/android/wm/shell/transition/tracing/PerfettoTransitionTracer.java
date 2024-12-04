/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.transition.tracing;

import static android.tracing.perfetto.DataSourceParams.PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_DROP;

import android.internal.perfetto.protos.ShellTransitionOuterClass.ShellHandlerMapping;
import android.internal.perfetto.protos.ShellTransitionOuterClass.ShellHandlerMappings;
import android.internal.perfetto.protos.ShellTransitionOuterClass.ShellTransition;
import android.internal.perfetto.protos.TracePacketOuterClass.TracePacket;
import android.os.SystemClock;
import android.os.Trace;
import android.tracing.perfetto.DataSourceParams;
import android.tracing.perfetto.InitArguments;
import android.tracing.perfetto.Producer;
import android.tracing.transition.TransitionDataSource;
import android.util.proto.ProtoOutputStream;

import com.android.wm.shell.transition.Transitions;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class to collect and dump transition traces.
 */
public class PerfettoTransitionTracer implements TransitionTracer {
    private final AtomicInteger mActiveTraces = new AtomicInteger(0);
    private final TransitionDataSource mDataSource = new TransitionDataSource(
            mActiveTraces::incrementAndGet,
            this::onFlush,
            mActiveTraces::decrementAndGet);
    private final Map<String, Integer> mHandlerMapping = new HashMap<>();

    public PerfettoTransitionTracer() {
        Producer.init(InitArguments.DEFAULTS);
        DataSourceParams params =
                new DataSourceParams.Builder()
                        .setBufferExhaustedPolicy(
                                PERFETTO_DS_BUFFER_EXHAUSTED_POLICY_DROP)
                        .build();
        mDataSource.register(params);
    }

    /**
     * Adds an entry in the trace to log that a transition has been dispatched to a handler.
     *
     * @param transitionId The id of the transition being dispatched.
     * @param handler The handler the transition is being dispatched to.
     */
    @Override
    public void logDispatched(int transitionId, Transitions.TransitionHandler handler) {
        if (!isTracing()) {
            return;
        }

        Trace.traceBegin(Trace.TRACE_TAG_WINDOW_MANAGER, "logDispatched");
        try {
            doLogDispatched(transitionId, handler);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
        }
    }

    private void doLogDispatched(int transitionId, Transitions.TransitionHandler handler) {
        mDataSource.trace(ctx -> {
            final int handlerId = getHandlerId(handler);

            final ProtoOutputStream os = ctx.newTracePacket();
            final long token = os.start(TracePacket.SHELL_TRANSITION);
            os.write(ShellTransition.ID, transitionId);
            os.write(ShellTransition.DISPATCH_TIME_NS,
                    SystemClock.elapsedRealtimeNanos());
            os.write(ShellTransition.HANDLER, handlerId);
            os.end(token);
        });
    }

    private int getHandlerId(Transitions.TransitionHandler handler) {
        final int handlerId;
        synchronized (mHandlerMapping) {
            if (mHandlerMapping.containsKey(handler.getClass().getName())) {
                handlerId = mHandlerMapping.get(handler.getClass().getName());
            } else {
                // + 1 to avoid 0 ids which can be confused with missing value when dumped to proto
                handlerId = mHandlerMapping.size() + 1;
                mHandlerMapping.put(handler.getClass().getName(), handlerId);
            }
        }
        return handlerId;
    }

    /**
     * Adds an entry in the trace to log that a request to merge a transition was made.
     *
     * @param mergeRequestedTransitionId The id of the transition we are requesting to be merged.
     */
    @Override
    public void logMergeRequested(int mergeRequestedTransitionId, int playingTransitionId) {
        if (!isTracing()) {
            return;
        }

        Trace.traceBegin(Trace.TRACE_TAG_WINDOW_MANAGER, "logMergeRequested");
        try {
            doLogMergeRequested(mergeRequestedTransitionId, playingTransitionId);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
        }
    }

    private void doLogMergeRequested(int mergeRequestedTransitionId, int playingTransitionId) {
        mDataSource.trace(ctx -> {
            final ProtoOutputStream os = ctx.newTracePacket();
            final long token = os.start(TracePacket.SHELL_TRANSITION);
            os.write(ShellTransition.ID, mergeRequestedTransitionId);
            os.write(ShellTransition.MERGE_REQUEST_TIME_NS,
                    SystemClock.elapsedRealtimeNanos());
            os.write(ShellTransition.MERGE_TARGET, playingTransitionId);
            os.end(token);
        });
    }

    /**
     * Adds an entry in the trace to log that a transition was merged by the handler.
     *
     * @param mergedTransitionId The id of the transition that was merged.
     * @param playingTransitionId The id of the transition the transition was merged into.
     */
    @Override
    public void logMerged(int mergedTransitionId, int playingTransitionId) {
        if (!isTracing()) {
            return;
        }

        Trace.traceBegin(Trace.TRACE_TAG_WINDOW_MANAGER, "logMerged");
        try {
            doLogMerged(mergedTransitionId, playingTransitionId);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
        }
    }

    private void doLogMerged(int mergeRequestedTransitionId, int playingTransitionId) {
        mDataSource.trace(ctx -> {
            final ProtoOutputStream os = ctx.newTracePacket();
            final long token = os.start(TracePacket.SHELL_TRANSITION);
            os.write(ShellTransition.ID, mergeRequestedTransitionId);
            os.write(ShellTransition.MERGE_TIME_NS,
                    SystemClock.elapsedRealtimeNanos());
            os.write(ShellTransition.MERGE_TARGET, playingTransitionId);
            os.end(token);
        });
    }

    /**
     * Adds an entry in the trace to log that a transition was aborted.
     *
     * @param transitionId The id of the transition that was aborted.
     */
    @Override
    public void logAborted(int transitionId) {
        if (!isTracing()) {
            return;
        }

        Trace.traceBegin(Trace.TRACE_TAG_WINDOW_MANAGER, "logAborted");
        try {
            doLogAborted(transitionId);
        } finally {
            Trace.traceEnd(Trace.TRACE_TAG_WINDOW_MANAGER);
        }
    }

    private void doLogAborted(int transitionId) {
        mDataSource.trace(ctx -> {
            final ProtoOutputStream os = ctx.newTracePacket();
            final long token = os.start(TracePacket.SHELL_TRANSITION);
            os.write(ShellTransition.ID, transitionId);
            os.write(ShellTransition.SHELL_ABORT_TIME_NS,
                    SystemClock.elapsedRealtimeNanos());
            os.end(token);
        });
    }

    private boolean isTracing() {
        return mActiveTraces.get() > 0;
    }

    private void onFlush() {
        mDataSource.trace(ctx -> {
            final ProtoOutputStream os = ctx.newTracePacket();

            final long mappingsToken = os.start(TracePacket.SHELL_HANDLER_MAPPINGS);
            for (Map.Entry<String, Integer> entry : mHandlerMapping.entrySet()) {
                final String handler = entry.getKey();
                final int handlerId = entry.getValue();

                final long mappingEntryToken = os.start(ShellHandlerMappings.MAPPING);
                os.write(ShellHandlerMapping.ID, handlerId);
                os.write(ShellHandlerMapping.NAME, handler);
                os.end(mappingEntryToken);

            }
            os.end(mappingsToken);
        });
    }
}
