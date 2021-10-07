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

package com.android.systemui.shared.tracing;

import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;

import com.android.internal.util.TraceBuffer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * A proto tracer implementation that can be updated directly (upon state change), or on the next
 * scheduled frame.
 *
 * @param <P> The class type of the proto provider
 * @param <S> The proto class type of the encapsulating proto
 * @param <T> The proto class type of the individual proto entries in the buffer
 * @param <R> The proto class type of the entry root proto in the buffer
 */
public class FrameProtoTracer<P, S extends P, T extends P, R>
        implements Choreographer.FrameCallback {

    private static final String TAG = "FrameProtoTracer";
    private static final int BUFFER_CAPACITY = 1024 * 1024;

    private final Object mLock = new Object();
    private final TraceBuffer<P, S, T> mBuffer;
    private final File mTraceFile;
    private final ProtoTraceParams<P, S, T, R> mParams;
    private Choreographer mChoreographer;
    private final Queue<T> mPool = new LinkedList<>();
    private final ArrayList<ProtoTraceable<R>> mTraceables = new ArrayList<>();
    private final ArrayList<ProtoTraceable<R>> mTmpTraceables = new ArrayList<>();

    private volatile boolean mEnabled;
    private boolean mFrameScheduled;

    private final TraceBuffer.ProtoProvider<P, S, T> mProvider =
            new TraceBuffer.ProtoProvider<P, S, T>() {
        @Override
        public int getItemSize(P proto) {
            return mParams.getProtoSize(proto);
        }

        @Override
        public byte[] getBytes(P proto) {
            return mParams.getProtoBytes(proto);
        }

        @Override
        public void write(S encapsulatingProto, Queue<T> buffer, OutputStream os)
                throws IOException {
            os.write(mParams.serializeEncapsulatingProto(encapsulatingProto, buffer));
        }
    };

    public interface ProtoTraceParams<P, S, T, R> {
        File getTraceFile();
        S getEncapsulatingTraceProto();
        T updateBufferProto(T reuseObj, ArrayList<ProtoTraceable<R>> traceables);
        byte[] serializeEncapsulatingProto(S encapsulatingProto, Queue<T> buffer);
        byte[] getProtoBytes(P proto);
        int getProtoSize(P proto);
    }

    public FrameProtoTracer(ProtoTraceParams<P, S, T, R> params) {
        mParams = params;
        mBuffer = new TraceBuffer<>(BUFFER_CAPACITY, mProvider, new Consumer<T>() {
            @Override
            public void accept(T t) {
                onProtoDequeued(t);
            }
        });
        mTraceFile = params.getTraceFile();
    }

    public void start() {
        synchronized (mLock) {
            if (mEnabled) {
                return;
            }
            mBuffer.resetBuffer();
            mEnabled = true;
        }
        logState();
    }

    public void stop() {
        synchronized (mLock) {
            if (!mEnabled) {
                return;
            }
            mEnabled = false;
        }
        writeToFile();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    public void add(ProtoTraceable<R> traceable) {
        synchronized (mLock) {
            mTraceables.add(traceable);
        }
    }

    public void remove(ProtoTraceable<R> traceable) {
        synchronized (mLock) {
            mTraceables.remove(traceable);
        }
    }

    public void scheduleFrameUpdate() {
        if (!mEnabled || mFrameScheduled) {
            return;
        }

        // Schedule an update on the next frame
        if (mChoreographer == null) {
            mChoreographer = Choreographer.getMainThreadInstance();
        }
        mChoreographer.postFrameCallback(this);
        mFrameScheduled = true;
    }

    public void update() {
        if (!mEnabled) {
            return;
        }

        logState();
    }

    public float getBufferUsagePct() {
        return (float) mBuffer.getBufferSize() / BUFFER_CAPACITY;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        logState();
    }

    private void onProtoDequeued(T proto) {
        mPool.add(proto);
    }

    private void logState() {
        synchronized (mLock) {
            mTmpTraceables.addAll(mTraceables);
        }

        mBuffer.add(mParams.updateBufferProto(mPool.poll(), mTmpTraceables));
        mTmpTraceables.clear();
        mFrameScheduled = false;
    }

    private void writeToFile() {
        try {
            Trace.beginSection("ProtoTracer.writeToFile");
            mBuffer.writeTraceToFile(mTraceFile, mParams.getEncapsulatingTraceProto());
        } catch (IOException e) {
            Log.e(TAG, "Unable to write buffer to file", e);
        } finally {
            Trace.endSection();
        }
    }
}


