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

import android.app.prediction.AppTargetEvent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * A representation of an app target event.
 */
public final class AppTargetEventCompat implements Parcelable {

    /**
     * @hide
     */
    @IntDef({ACTION_LAUNCH, ACTION_DISMISS, ACTION_PIN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionType {}

    /**
     * Event type constant indicating an app target has been launched.
     */
    public static final int ACTION_LAUNCH = 1;

    /**
     * Event type constant indicating an app target has been dismissed.
     */
    public static final int ACTION_DISMISS = 2;

    /**
     * Event type constant indicating an app target has been pinned.
     */
    public static final int ACTION_PIN = 3;

    private final AppTargetCompat mTarget;
    private final String mLocation;
    private final int mAction;

    private AppTargetEventCompat(@Nullable AppTargetCompat target, @Nullable String location,
            @ActionType int actionType) {
        mTarget = target;
        mLocation = location;
        mAction = actionType;
    }

    private AppTargetEventCompat(Parcel parcel) {
        mTarget = parcel.readParcelable(null);
        mLocation = parcel.readString();
        mAction = parcel.readInt();
    }

    /**
     * Returns the app target.
     */
    @Nullable
    public AppTargetCompat getTarget() {
        return mTarget;
    }

    /**
     * Returns the launch location.
     */
    @Nullable
    public String getLaunchLocation() {
        return mLocation;
    }

    /**
     * Returns the action type.
     */
    public @ActionType int getAction() {
        return mAction;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!getClass().equals(o != null ? o.getClass() : null)) return false;

        AppTargetEventCompat other = (AppTargetEventCompat) o;
        return mTarget.equals(other.mTarget)
                && mLocation.equals(other.mLocation)
                && mAction == other.mAction;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mTarget, 0);
        dest.writeString(mLocation);
        dest.writeInt(mAction);
    }

    public AppTargetEvent toPlatformType() {
        return new AppTargetEvent.Builder(mTarget.toPlatformType(), mAction)
                .setLaunchLocation(mLocation)
                .build();
    }

    public static final @NonNull
    Creator<AppTargetEventCompat> CREATOR =
            new Creator<AppTargetEventCompat>() {
                public AppTargetEventCompat createFromParcel(Parcel parcel) {
                    return new AppTargetEventCompat(parcel);
                }

                public AppTargetEventCompat[] newArray(int size) {
                    return new AppTargetEventCompat[size];
                }
            };

    /**
     * A builder for app target events.
     */
    public static final class Builder {
        private AppTargetCompat mTarget;
        private String mLocation;
        private @ActionType int mAction;

        /**
         * @param target The app target that is associated with this event.
         * @param actionType The event type, which is one of the values in {@link ActionType}.
         */
        public Builder(@Nullable AppTargetCompat target, @ActionType int actionType) {
            mTarget = target;
            mAction = actionType;
        }

        /**
         * Sets the launch location.
         */
        @NonNull
        public Builder setLaunchLocation(@Nullable String location) {
            mLocation = location;
            return this;
        }

        /**
         * Builds a new event instance.
         */
        @NonNull
        public AppTargetEventCompat build() {
            return new AppTargetEventCompat(mTarget, mLocation, mAction);
        }
    }
}