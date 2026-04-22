/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.window;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.IApplicationThread;
import android.os.IBinder;
import android.os.Parcelable;

/**
 * Represents a remote transition animation and information required to run it (eg. the app thread
 * that needs to be boosted).
 * @hide
 */
public final class RemoteTransition implements Parcelable {

    /** The actual remote-transition interface used to run the transition animation. */
    private @NonNull IRemoteTransition mRemoteTransition;

    /** The application thread that will be running the remote transition. */
    private @Nullable IApplicationThread mAppThread;

    /** A name for this that can be used for debugging. */
    private @Nullable String mDebugName;

    /**
     * Constructs with no app thread (animation runs in shell).
     * @hide
     */
    public RemoteTransition(@NonNull IRemoteTransition remoteTransition) {
        this(remoteTransition, null /* appThread */, null /* debugName */);
    }

    /**
     * Constructs with no app thread (animation runs in shell).
     * @hide
     */
    public RemoteTransition(@NonNull IRemoteTransition remoteTransition,
            @Nullable String debugName) {
        this(remoteTransition, null /* appThread */, debugName);
    }

    /** Get the IBinder associated with the underlying IRemoteTransition. */
    public @Nullable IBinder asBinder() {
        return mRemoteTransition.asBinder();
    }

    /**
     * Creates a new RemoteTransition.
     *
     * @param remoteTransition
     *   The actual remote-transition interface used to run the transition animation.
     * @param appThread
     *   The application thread that will be running the remote transition.
     * @param debugName
     *   A name for this that can be used for debugging.
     * @hide
     */
    public RemoteTransition(
            @NonNull IRemoteTransition remoteTransition,
            @Nullable IApplicationThread appThread,
            @Nullable String debugName) {
        this.mRemoteTransition = remoteTransition;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mRemoteTransition);
        this.mAppThread = appThread;
        this.mDebugName = debugName;

        // onConstructed(); // You can define this method to get a callback
    }

    /**
     * The actual remote-transition interface used to run the transition animation.
     * @hide
     */
    public @NonNull IRemoteTransition getRemoteTransition() {
        return mRemoteTransition;
    }

    /**
     * The application thread that will be running the remote transition.
     * @hide
     */
    public @Nullable IApplicationThread getAppThread() {
        return mAppThread;
    }

    /**
     * A name for this that can be used for debugging.
     */
    public @Nullable String getDebugName() {
        return mDebugName;
    }

    /**
     * The actual remote-transition interface used to run the transition animation.
     * @hide
     */
    public @NonNull RemoteTransition setRemoteTransition(@NonNull IRemoteTransition value) {
        mRemoteTransition = value;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mRemoteTransition);
        return this;
    }

    /**
     * The application thread that will be running the remote transition.
     * @hide
     */
    public @NonNull RemoteTransition setAppThread(@NonNull IApplicationThread value) {
        mAppThread = value;
        return this;
    }

    /**
     * A name for this that can be used for debugging.
     */
    public @NonNull RemoteTransition setDebugName(@NonNull String value) {
        mDebugName = value;
        return this;
    }

    @Override
    public String toString() {
        // You can override field toString logic by defining methods like:
        // String fieldNameToString() { ... }

        return "RemoteTransition { " +
                "remoteTransition = " + mRemoteTransition + ", " +
                "appThread = " + mAppThread + ", " +
                "debugName = " + mDebugName +
        " }";
    }

    @Override
    public void writeToParcel(@NonNull android.os.Parcel dest, int flags) {
        // You can override field parcelling by defining methods like:
        // void parcelFieldName(Parcel dest, int flags) { ... }

        byte flg = 0;
        if (mAppThread != null) flg |= 0x2;
        if (mDebugName != null) flg |= 0x4;
        dest.writeByte(flg);
        dest.writeStrongInterface(mRemoteTransition);
        if (mAppThread != null) dest.writeStrongInterface(mAppThread);
        if (mDebugName != null) dest.writeString(mDebugName);
    }

    @Override
    public int describeContents() { return 0; }

    /** @hide */
    @SuppressWarnings({"unchecked", "RedundantCast"})
    protected RemoteTransition(@NonNull android.os.Parcel in) {
        // You can override field unparcelling by defining methods like:
        // static FieldType unparcelFieldName(Parcel in) { ... }

        byte flg = in.readByte();
        IRemoteTransition remoteTransition = IRemoteTransition.Stub.asInterface(in.readStrongBinder());
        IApplicationThread appThread = (flg & 0x2) == 0 ? null : IApplicationThread.Stub.asInterface(in.readStrongBinder());
        String debugName = (flg & 0x4) == 0 ? null : in.readString();

        this.mRemoteTransition = remoteTransition;
        com.android.internal.util.AnnotationValidations.validate(
                NonNull.class, null, mRemoteTransition);
        this.mAppThread = appThread;
        this.mDebugName = debugName;
    }

    public static final @NonNull Parcelable.Creator<RemoteTransition> CREATOR
            = new Parcelable.Creator<RemoteTransition>() {
        @Override
        public RemoteTransition[] newArray(int size) {
            return new RemoteTransition[size];
        }

        @Override
        public RemoteTransition createFromParcel(@NonNull android.os.Parcel in) {
            return new RemoteTransition(in);
        }
    };
}
