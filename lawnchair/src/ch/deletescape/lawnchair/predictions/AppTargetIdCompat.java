/*
 * Copyright (C) 2018 The Android Open Source Project
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
package ch.deletescape.lawnchair.predictions;

import android.app.prediction.AppTargetId;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The id for a prediction target. See {@link AppTargetCompat}.
 */
public final class AppTargetIdCompat implements Parcelable {

    @NonNull
    private final String mId;

    /**
     * Creates a new id for a prediction target.
     */
    public AppTargetIdCompat(@NonNull String id) {
        mId = id;
    }

    public AppTargetIdCompat(AppTargetId targetId) {
        mId = targetId.getId();
    }

    private AppTargetIdCompat(Parcel parcel) {
        mId = parcel.readString();
    }

    /**
     * Returns the id.
     *
     * @hide
     */
    @NonNull
    public String getId() {
        return mId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!getClass().equals(o != null ? o.getClass() : null)) return false;

        AppTargetIdCompat other = (AppTargetIdCompat) o;
        return mId.equals(other.mId);
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mId);
    }

    public AppTargetId toPlatformType() {
        return new AppTargetId(mId);
    }

    public static final @NonNull Creator<AppTargetIdCompat> CREATOR =
            new Creator<AppTargetIdCompat>() {
                public AppTargetIdCompat createFromParcel(Parcel parcel) {
                    return new AppTargetIdCompat(parcel);
                }

                public AppTargetIdCompat[] newArray(int size) {
                    return new AppTargetIdCompat[size];
                }
            };
}
