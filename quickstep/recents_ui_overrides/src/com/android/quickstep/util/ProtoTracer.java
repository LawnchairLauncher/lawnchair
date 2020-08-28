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

import static com.android.launcher3.tracing.nano.LauncherTraceFileProto.MagicNumber.MAGIC_NUMBER_H;
import static com.android.launcher3.tracing.nano.LauncherTraceFileProto.MagicNumber.MAGIC_NUMBER_L;

import android.content.Context;
import android.os.SystemClock;

import com.android.launcher3.tracing.nano.LauncherTraceProto;
import com.android.launcher3.tracing.nano.LauncherTraceEntryProto;
import com.android.launcher3.tracing.nano.LauncherTraceFileProto;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.shared.tracing.FrameProtoTracer;
import com.android.systemui.shared.tracing.FrameProtoTracer.ProtoTraceParams;
import com.android.systemui.shared.tracing.ProtoTraceable;
import com.google.protobuf.nano.MessageNano;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Queue;


/**
 * Controller for coordinating winscope proto tracing.
 */
public class ProtoTracer implements ProtoTraceParams<MessageNano,
        LauncherTraceFileProto, LauncherTraceEntryProto, LauncherTraceProto> {

    public static final MainThreadInitializedObject<ProtoTracer> INSTANCE =
            new MainThreadInitializedObject<>(ProtoTracer::new);

    private static final String TAG = "ProtoTracer";
    private static final long MAGIC_NUMBER_VALUE = ((long) MAGIC_NUMBER_H << 32) | MAGIC_NUMBER_L;

    private final Context mContext;
    private final FrameProtoTracer<MessageNano,
            LauncherTraceFileProto, LauncherTraceEntryProto, LauncherTraceProto> mProtoTracer;

    public ProtoTracer(Context context) {
        mContext = context;
        mProtoTracer = new FrameProtoTracer<>(this);
    }

    @Override
    public File getTraceFile() {
        return new File(mContext.getFilesDir(), "launcher_trace.pb");
    }

    @Override
    public LauncherTraceFileProto getEncapsulatingTraceProto() {
        return new LauncherTraceFileProto();
    }

    @Override
    public LauncherTraceEntryProto updateBufferProto(LauncherTraceEntryProto reuseObj,
            ArrayList<ProtoTraceable<LauncherTraceProto>> traceables) {
        LauncherTraceEntryProto proto = reuseObj != null
                ? reuseObj
                : new LauncherTraceEntryProto();
        proto.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
        proto.launcher = proto.launcher != null ? proto.launcher : new LauncherTraceProto();
        for (ProtoTraceable t : traceables) {
            t.writeToProto(proto.launcher);
        }
        return proto;
    }

    @Override
    public byte[] serializeEncapsulatingProto(LauncherTraceFileProto encapsulatingProto,
            Queue<LauncherTraceEntryProto> buffer) {
        encapsulatingProto.magicNumber = MAGIC_NUMBER_VALUE;
        encapsulatingProto.entry = buffer.toArray(new LauncherTraceEntryProto[0]);
        return MessageNano.toByteArray(encapsulatingProto);
    }

    @Override
    public byte[] getProtoBytes(MessageNano proto) {
        return MessageNano.toByteArray(proto);
    }

    @Override
    public int getProtoSize(MessageNano proto) {
        return proto.getCachedSize();
    }

    public void start() {
        mProtoTracer.start();
    }

    public void stop() {
        mProtoTracer.stop();
    }

    public void add(ProtoTraceable<LauncherTraceProto> traceable) {
        mProtoTracer.add(traceable);
    }

    public void remove(ProtoTraceable<LauncherTraceProto> traceable) {
        mProtoTracer.remove(traceable);
    }

    public void scheduleFrameUpdate() {
        mProtoTracer.scheduleFrameUpdate();
    }

    public void update() {
        mProtoTracer.update();
    }
}
