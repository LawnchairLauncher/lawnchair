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

import static android.os.Build.IS_USER;

import static com.android.wm.shell.nano.WmShellTransitionTraceProto.MAGIC_NUMBER_H;
import static com.android.wm.shell.nano.WmShellTransitionTraceProto.MAGIC_NUMBER_L;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;

import com.android.internal.util.TraceBuffer;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.transition.Transitions;

import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Helper class to collect and dump transition traces.
 */
public class LegacyTransitionTracer
        implements ShellCommandHandler.ShellCommandActionHandler, TransitionTracer {
    private static final int ALWAYS_ON_TRACING_CAPACITY = 15 * 1024; // 15 KB
    private static final int ACTIVE_TRACING_BUFFER_CAPACITY = 5000 * 1024; // 5 MB

    private static final long MAGIC_NUMBER_VALUE = ((long) MAGIC_NUMBER_H << 32) | MAGIC_NUMBER_L;

    static final String WINSCOPE_EXT = ".winscope";
    private static final String TRACE_FILE =
            "/data/misc/wmtrace/shell_transition_trace" + WINSCOPE_EXT;

    private final Object mEnabledLock = new Object();
    private boolean mActiveTracingEnabled = false;

    private final TraceBuffer.ProtoProvider mProtoProvider =
            new TraceBuffer.ProtoProvider<MessageNano,
                    com.android.wm.shell.nano.WmShellTransitionTraceProto,
                    com.android.wm.shell.nano.Transition>() {
                @Override
                public int getItemSize(MessageNano proto) {
                    return proto.getCachedSize();
                }

                @Override
                public byte[] getBytes(MessageNano proto) {
                    return MessageNano.toByteArray(proto);
                }

                @Override
                public void write(
                        com.android.wm.shell.nano.WmShellTransitionTraceProto encapsulatingProto,
                        Queue<com.android.wm.shell.nano.Transition> buffer, OutputStream os)
                        throws IOException {
                    encapsulatingProto.transitions = buffer.toArray(
                            new com.android.wm.shell.nano.Transition[0]);
                    os.write(getBytes(encapsulatingProto));
                }
            };
    private final TraceBuffer<MessageNano,
            com.android.wm.shell.nano.WmShellTransitionTraceProto,
            com.android.wm.shell.nano.Transition> mTraceBuffer =
                new TraceBuffer(ALWAYS_ON_TRACING_CAPACITY, mProtoProvider,
                        this::handleOnEntryRemovedFromTrace);
    private final Map<Object, Runnable> mRemovedFromTraceCallbacks = new HashMap<>();

    private final Map<Transitions.TransitionHandler, Integer> mHandlerIds = new HashMap<>();
    private final Map<Transitions.TransitionHandler, Integer> mHandlerUseCountInTrace =
            new HashMap<>();

    /**
     * Adds an entry in the trace to log that a transition has been dispatched to a handler.
     *
     * @param transitionId The id of the transition being dispatched.
     * @param handler The handler the transition is being dispatched to.
     */
    @Override
    public void logDispatched(int transitionId, Transitions.TransitionHandler handler) {
        final int handlerId;
        if (mHandlerIds.containsKey(handler)) {
            handlerId = mHandlerIds.get(handler);
        } else {
            // + 1 to avoid 0 ids which can be confused with missing value when dumped to proto
            handlerId = mHandlerIds.size() + 1;
            mHandlerIds.put(handler, handlerId);
        }

        com.android.wm.shell.nano.Transition proto = new com.android.wm.shell.nano.Transition();
        proto.id = transitionId;
        proto.dispatchTimeNs = SystemClock.elapsedRealtimeNanos();
        proto.handler = handlerId;

        final int useCountAfterAdd = mHandlerUseCountInTrace.getOrDefault(handler, 0) + 1;
        mHandlerUseCountInTrace.put(handler, useCountAfterAdd);

        mRemovedFromTraceCallbacks.put(proto, () -> {
            final int useCountAfterRemove = mHandlerUseCountInTrace.get(handler) - 1;
            mHandlerUseCountInTrace.put(handler, useCountAfterRemove);
        });

        mTraceBuffer.add(proto);
    }

    /**
     * Adds an entry in the trace to log that a request to merge a transition was made.
     *
     * @param mergeRequestedTransitionId The id of the transition we are requesting to be merged.
     */
    @Override
    public void logMergeRequested(int mergeRequestedTransitionId, int playingTransitionId) {
        com.android.wm.shell.nano.Transition proto = new com.android.wm.shell.nano.Transition();
        proto.id = mergeRequestedTransitionId;
        proto.mergeRequestTimeNs = SystemClock.elapsedRealtimeNanos();
        proto.mergeTarget = playingTransitionId;

        mTraceBuffer.add(proto);
    }

    /**
     * Adds an entry in the trace to log that a transition was merged by the handler.
     *
     * @param mergedTransitionId The id of the transition that was merged.
     * @param playingTransitionId The id of the transition the transition was merged into.
     */
    @Override
    public void logMerged(int mergedTransitionId, int playingTransitionId) {
        com.android.wm.shell.nano.Transition proto = new com.android.wm.shell.nano.Transition();
        proto.id = mergedTransitionId;
        proto.mergeTimeNs = SystemClock.elapsedRealtimeNanos();
        proto.mergeTarget = playingTransitionId;

        mTraceBuffer.add(proto);
    }

    /**
     * Adds an entry in the trace to log that a transition was aborted.
     *
     * @param transitionId The id of the transition that was aborted.
     */
    @Override
    public void logAborted(int transitionId) {
        com.android.wm.shell.nano.Transition proto = new com.android.wm.shell.nano.Transition();
        proto.id = transitionId;
        proto.abortTimeNs = SystemClock.elapsedRealtimeNanos();

        mTraceBuffer.add(proto);
    }

    /**
     * Starts collecting transitions for the trace.
     * If called while a trace is already running, this will reset the trace.
     */
    public void startTrace(@Nullable PrintWriter pw) {
        if (IS_USER) {
            LogAndPrintln.e(pw, "Tracing is not supported on user builds.");
            return;
        }
        Trace.beginSection("Tracer#startTrace");
        LogAndPrintln.i(pw, "Starting shell transition trace.");
        synchronized (mEnabledLock) {
            mActiveTracingEnabled = true;
            mTraceBuffer.resetBuffer();
            mTraceBuffer.setCapacity(ACTIVE_TRACING_BUFFER_CAPACITY);
        }
        Trace.endSection();
    }

    /**
     * Stops collecting the transition trace and dump to trace to file.
     *
     * Dumps the trace to @link{TRACE_FILE}.
     */
    public void stopTrace(@Nullable PrintWriter pw) {
        stopTrace(pw, new File(TRACE_FILE));
    }

    /**
     * Stops collecting the transition trace and dump to trace to file.
     * @param outputFile The file to dump the transition trace to.
     */
    public void stopTrace(@Nullable PrintWriter pw, File outputFile) {
        if (IS_USER) {
            LogAndPrintln.e(pw, "Tracing is not supported on user builds.");
            return;
        }
        Trace.beginSection("Tracer#stopTrace");
        LogAndPrintln.i(pw, "Stopping shell transition trace.");
        synchronized (mEnabledLock) {
            mActiveTracingEnabled = false;
            writeTraceToFileLocked(pw, outputFile);
            mTraceBuffer.resetBuffer();
            mTraceBuffer.setCapacity(ALWAYS_ON_TRACING_CAPACITY);
        }
        Trace.endSection();
    }

    /**
     * Being called while taking a bugreport so that tracing files can be included in the bugreport.
     *
     * @param pw Print writer
     */
    public void saveForBugreport(@Nullable PrintWriter pw) {
        if (IS_USER) {
            LogAndPrintln.e(pw, "Tracing is not supported on user builds.");
            return;
        }
        Trace.beginSection("TransitionTracer#saveForBugreport");
        synchronized (mEnabledLock) {
            final File outputFile = new File(TRACE_FILE);
            writeTraceToFileLocked(pw, outputFile);
        }
        Trace.endSection();
    }

    private void writeTraceToFileLocked(@Nullable PrintWriter pw, File file) {
        Trace.beginSection("TransitionTracer#writeTraceToFileLocked");
        try {
            com.android.wm.shell.nano.WmShellTransitionTraceProto proto =
                    new com.android.wm.shell.nano.WmShellTransitionTraceProto();
            proto.magicNumber = MAGIC_NUMBER_VALUE;
            writeHandlerMappingToProto(proto);
            long timeOffsetNs =
                    TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis())
                            - SystemClock.elapsedRealtimeNanos();
            proto.realToElapsedTimeOffsetNanos = timeOffsetNs;
            int pid = android.os.Process.myPid();
            LogAndPrintln.i(pw, "Writing file to " + file.getAbsolutePath()
                    + " from process " + pid);
            mTraceBuffer.writeTraceToFile(file, proto);
        } catch (IOException e) {
            LogAndPrintln.e(pw, "Unable to write buffer to file", e);
        }
        Trace.endSection();
    }

    private void writeHandlerMappingToProto(
            com.android.wm.shell.nano.WmShellTransitionTraceProto proto) {
        ArrayList<com.android.wm.shell.nano.HandlerMapping> handlerMappings = new ArrayList<>();
        for (Transitions.TransitionHandler handler : mHandlerUseCountInTrace.keySet()) {
            final int count = mHandlerUseCountInTrace.get(handler);
            if (count > 0) {
                com.android.wm.shell.nano.HandlerMapping mapping =
                        new com.android.wm.shell.nano.HandlerMapping();
                mapping.id = mHandlerIds.get(handler);
                mapping.name = handler.getClass().getName();
                handlerMappings.add(mapping);
            }
        }
        proto.handlerMappings = handlerMappings.toArray(
                new com.android.wm.shell.nano.HandlerMapping[0]);
    }

    private void handleOnEntryRemovedFromTrace(Object proto) {
        if (mRemovedFromTraceCallbacks.containsKey(proto)) {
            mRemovedFromTraceCallbacks.get(proto).run();
            mRemovedFromTraceCallbacks.remove(proto);
        }
    }

    @Override
    public boolean onShellCommand(String[] args, PrintWriter pw) {
        switch (args[0]) {
            case "start": {
                startTrace(pw);
                return true;
            }
            case "stop": {
                stopTrace(pw);
                return true;
            }
            case "save-for-bugreport": {
                saveForBugreport(pw);
                return true;
            }
            default: {
                pw.println("Invalid command: " + args[0]);
                printShellCommandHelp(pw, "");
                return false;
            }
        }
    }

    @Override
    public void printShellCommandHelp(PrintWriter pw, String prefix) {
        pw.println(prefix + "start");
        pw.println(prefix + "  Start tracing the transitions.");
        pw.println(prefix + "stop");
        pw.println(prefix + "  Stop tracing the transitions.");
        pw.println(prefix + "save-for-bugreport");
        pw.println(prefix + "  Flush in memory transition trace to file.");
    }

    private static class LogAndPrintln {
        private static final String LOG_TAG = "ShellTransitionTracer";

        private static void i(@Nullable PrintWriter pw, String msg) {
            Log.i(LOG_TAG, msg);
            if (pw != null) {
                pw.println(msg);
                pw.flush();
            }
        }

        private static void e(@Nullable PrintWriter pw, String msg) {
            Log.e(LOG_TAG, msg);
            if (pw != null) {
                pw.println("ERROR: " + msg);
                pw.flush();
            }
        }

        private static void e(@Nullable PrintWriter pw, String msg, @NonNull Exception e) {
            Log.e(LOG_TAG, msg, e);
            if (pw != null) {
                pw.println("ERROR: " + msg + " ::\n " + e);
                pw.flush();
            }
        }
    }
}
