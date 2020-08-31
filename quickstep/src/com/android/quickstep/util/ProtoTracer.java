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

package com.android.quickstep.util;

import static com.android.launcher3.tracing.LauncherTraceFileProto.MagicNumber.MAGIC_NUMBER_H_VALUE;
import static com.android.launcher3.tracing.LauncherTraceFileProto.MagicNumber.MAGIC_NUMBER_L_VALUE;

import android.content.Context;
import android.os.SystemClock;

import android.os.Trace;
import com.android.launcher3.tracing.LauncherTraceProto;
import com.android.launcher3.tracing.LauncherTraceEntryProto;
import com.android.launcher3.tracing.LauncherTraceFileProto;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.shared.tracing.FrameProtoTracer;
import com.android.systemui.shared.tracing.FrameProtoTracer.ProtoTraceParams;
import com.android.systemui.shared.tracing.ProtoTraceable;
import com.google.protobuf.MessageLite;

import java.io.File;
import java.util.ArrayList;
import java.util.Queue;


/**
 * Controller for coordinating winscope proto tracing.
 */
public class ProtoTracer implements ProtoTraceParams<MessageLite.Builder,
        LauncherTraceFileProto.Builder, LauncherTraceEntryProto.Builder,
                LauncherTraceProto.Builder> {

    public static final MainThreadInitializedObject<ProtoTracer> INSTANCE =
            new MainThreadInitializedObject<>(ProtoTracer::new);

    private static final String TAG = "ProtoTracer";
    private static final long MAGIC_NUMBER_VALUE =
            ((long) MAGIC_NUMBER_H_VALUE << 32) | MAGIC_NUMBER_L_VALUE;

    private final Context mContext;
    private final FrameProtoTracer<MessageLite.Builder, LauncherTraceFileProto.Builder,
        LauncherTraceEntryProto.Builder, LauncherTraceProto.Builder> mProtoTracer;

    public ProtoTracer(Context context) {
        mContext = context;
        mProtoTracer = new FrameProtoTracer<>(this);
    }

    @Override
    public File getTraceFile() {
        return new File(mContext.getFilesDir(), "launcher_trace.pb");
    }

    @Override
    public LauncherTraceFileProto.Builder getEncapsulatingTraceProto() {
        return LauncherTraceFileProto.newBuilder();
    }

    @Override
    public LauncherTraceEntryProto.Builder updateBufferProto(
            LauncherTraceEntryProto.Builder reuseObj,
            ArrayList<ProtoTraceable<LauncherTraceProto.Builder>> traceables) {
        Trace.beginSection("ProtoTracer.updateBufferProto");
        LauncherTraceEntryProto.Builder proto = LauncherTraceEntryProto.newBuilder();
        proto.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        LauncherTraceProto.Builder launcherProto = LauncherTraceProto.newBuilder();
        for (ProtoTraceable t : traceables) {
            t.writeToProto(launcherProto);
        }
        proto.setLauncher(launcherProto);
        Trace.endSection();
        return proto;
    }

    @Override
    public byte[] serializeEncapsulatingProto(LauncherTraceFileProto.Builder encapsulatingProto,
            Queue<LauncherTraceEntryProto.Builder> buffer) {
        Trace.beginSection("ProtoTracer.serializeEncapsulatingProto");
        encapsulatingProto.setMagicNumber(MAGIC_NUMBER_VALUE);
        for (LauncherTraceEntryProto.Builder entry : buffer) {
            encapsulatingProto.addEntry(entry);
        }
        byte[] bytes = encapsulatingProto.build().toByteArray();
        Trace.endSection();
        return bytes;
    }

    @Override
    public byte[] getProtoBytes(MessageLite.Builder proto) {
        return proto.build().toByteArray();
    }

    @Override
    public int getProtoSize(MessageLite.Builder proto) {
        return proto.build().getSerializedSize();
    }

    public void start() {
        mProtoTracer.start();
    }

    public void stop() {
        mProtoTracer.stop();
    }

    public void add(ProtoTraceable<LauncherTraceProto.Builder> traceable) {
        mProtoTracer.add(traceable);
    }

    public void remove(ProtoTraceable<LauncherTraceProto.Builder> traceable) {
        mProtoTracer.remove(traceable);
    }

    public void scheduleFrameUpdate() {
        mProtoTracer.scheduleFrameUpdate();
    }

    public void update() {
        mProtoTracer.update();
    }
}
