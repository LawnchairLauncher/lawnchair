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

package com.android.wm.shell.shared.bubbles;

import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents a removed bubble, defining the key and reason the bubble was removed.
 */
public class RemovedBubble implements Parcelable {

    private final String mKey;
    private final int mRemovalReason;

    public RemovedBubble(String key, int removalReason) {
        mKey = key;
        mRemovalReason = removalReason;
    }

    public RemovedBubble(Parcel parcel) {
        mKey = parcel.readString();
        mRemovalReason = parcel.readInt();
    }

    public String getKey() {
        return mKey;
    }

    public int getRemovalReason() {
        return mRemovalReason;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mKey);
        dest.writeInt(mRemovalReason);
    }

    @NonNull
    public static final Creator<RemovedBubble> CREATOR =
            new Creator<RemovedBubble>() {
                public RemovedBubble createFromParcel(Parcel source) {
                    return new RemovedBubble(source);
                }
                public RemovedBubble[] newArray(int size) {
                    return new RemovedBubble[size];
                }
            };
}
