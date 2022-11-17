/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.logging;

import static java.lang.Math.max;
import static java.lang.Math.min;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;


/**
 * An opaque identifier used to disambiguate which logs refer to a particular instance of some
 * UI element. Useful when there might be multiple instances simultaneously active.
 * Obtain from InstanceIdSequence.  Clipped to range [0, INSTANCE_ID_MAX].
 *
 * Copy of frameworks/base/core/java/com/android/internal/logging/InstanceId.java.
 */
public final class InstanceId implements Parcelable {
    // At most 20 bits: ~1m possibilities, ~0.5% probability of collision in 100 values
    public static final int INSTANCE_ID_MAX = 1 << 20;

    private final int mId;
    public InstanceId(int id) {
        mId = min(max(0, id), INSTANCE_ID_MAX);
    }

    private InstanceId(Parcel in) {
        this(in.readInt());
    }

    public int getId() {
        return mId;
    }

    @NonNull
    @Override
    public String toString() {
        return mId + "";
    }

    /**
     * Create a fake instance ID for testing purposes.  Not for production use. See also
     * InstanceIdSequenceFake, which is a testing replacement for InstanceIdSequence.
     * @param id The ID you want to assign.
     * @return new InstanceId.
     */
    @VisibleForTesting
    public static InstanceId fakeInstanceId(int id) {
        return new InstanceId(id);
    }

    @Override
    public int hashCode() {
        return mId;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof InstanceId)) {
            return false;
        }
        return mId == ((InstanceId) obj).mId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mId);
    }

    public static final Parcelable.Creator<InstanceId> CREATOR =
            new Parcelable.Creator<InstanceId>() {
                @Override
                public InstanceId createFromParcel(Parcel in) {
                    return new InstanceId(in);
                }

                @Override
                public InstanceId[] newArray(int size) {
                    return new InstanceId[size];
                }
            };

}
