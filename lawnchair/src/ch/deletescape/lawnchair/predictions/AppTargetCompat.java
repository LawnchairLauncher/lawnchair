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

import android.app.prediction.AppTarget;
import android.content.pm.ShortcutInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;

/**
 * A representation of a launchable target.
 */
public final class AppTargetCompat implements Parcelable {

    private final AppTargetIdCompat mId;
    private final String mPackageName;
    private final String mClassName;
    private final UserHandle mUser;

    private final ShortcutInfo mShortcutInfo;

    private final int mRank;

    /**
     * @deprecated use the Builder class
     */
    @Deprecated
    public AppTargetCompat(@NonNull AppTargetIdCompat id, @NonNull String packageName,
            @Nullable String className, @NonNull UserHandle user) {
        mId = id;
        mShortcutInfo = null;

        mPackageName = Preconditions.checkNotNull(packageName);
        mClassName = className;
        mUser = Preconditions.checkNotNull(user);
        mRank = 0;
    }

    /**
     * @deprecated use the Builder class
     */
    @Deprecated
    public AppTargetCompat(@NonNull AppTargetIdCompat id, @NonNull ShortcutInfo shortcutInfo,
            @Nullable String className) {
        mId = id;
        mShortcutInfo = Preconditions.checkNotNull(shortcutInfo);

        mPackageName = mShortcutInfo.getPackage();
        mUser = mShortcutInfo.getUserHandle();
        mClassName = className;
        mRank = 0;
    }

    public AppTargetCompat(AppTarget target) {
        mId = new AppTargetIdCompat(target.getId());
        mShortcutInfo = target.getShortcutInfo();
        mPackageName = target.getPackageName();
        mClassName = target.getClassName();
        mUser = target.getUser();
        mRank = target.getRank();
    }

    private AppTargetCompat(AppTargetIdCompat id, String packageName, UserHandle user,
            ShortcutInfo shortcutInfo, String className, int rank) {
        mId = id;
        mShortcutInfo = shortcutInfo;
        mPackageName = packageName;
        mClassName = className;
        mUser = user;
        mRank = rank;
    }

    private AppTargetCompat(Parcel parcel) {
        mId = parcel.readTypedObject(AppTargetIdCompat.CREATOR);
        mShortcutInfo = parcel.readTypedObject(ShortcutInfo.CREATOR);
        if (mShortcutInfo == null) {
            mPackageName = parcel.readString();
            mUser = UserHandle.of(parcel.readInt());
        } else {
            mPackageName = mShortcutInfo.getPackage();
            mUser = mShortcutInfo.getUserHandle();
        }
        mClassName = parcel.readString();
        mRank = parcel.readInt();
    }

    /**
     * Returns the target id.
     */
    @NonNull
    public AppTargetIdCompat getId() {
        return mId;
    }

    /**
     * Returns the class name for the app target.
     */
    @Nullable
    public String getClassName() {
        return mClassName;
    }

    /**
     * Returns the package name for the app target.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Returns the user for the app target.
     */
    @NonNull
    public UserHandle getUser() {
        return mUser;
    }

    /**
     * Returns the shortcut info for the target.
     */
    @Nullable
    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    /**
     * Returns the rank for the target. Rank of an AppTarget is a non-negative integer that
     * represents the importance of this target compared to other candidate targets. A smaller value
     * means higher importance in the list.
     */
    public @IntRange(from = 0) int getRank() {
        return mRank;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (!getClass().equals(o != null ? o.getClass() : null)) return false;

        AppTargetCompat other = (AppTargetCompat) o;
        boolean sameClassName = (mClassName == null && other.mClassName == null)
                || (mClassName != null && mClassName.equals(other.mClassName));
        boolean sameShortcutInfo = (mShortcutInfo == null && other.mShortcutInfo == null)
                || (mShortcutInfo != null && other.mShortcutInfo != null
                && mShortcutInfo.getId() == other.mShortcutInfo.getId());
        return mId.equals(other.mId)
                && mPackageName.equals(other.mPackageName)
                && sameClassName
                && mUser.equals(other.mUser)
                && sameShortcutInfo
                && mRank == other.mRank;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mId, flags);
        dest.writeTypedObject(mShortcutInfo, flags);
        if (mShortcutInfo == null) {
            dest.writeString(mPackageName);
            dest.writeInt(mUser.getIdentifier());
        }
        dest.writeString(mClassName);
        dest.writeInt(mRank);
    }

    public AppTarget toPlatformType() {
        Parcel parcel = Parcel.obtain();
        int flags = 0;
        parcel.writeTypedObject(mId.toPlatformType(), flags);
        parcel.writeTypedObject(mShortcutInfo, flags);
        if (mShortcutInfo == null) {
            parcel.writeString(mPackageName);
            parcel.writeInt(mUser.getIdentifier());
        }
        parcel.writeString(mClassName);
        parcel.writeInt(mRank);
        return AppTarget.CREATOR.createFromParcel(parcel);
    }

    /**
     * A builder for app targets.
     */
    public static final class Builder {

        @NonNull
        private final AppTargetIdCompat mId;

        private String mPackageName;
        private UserHandle mUser;
        private ShortcutInfo mShortcutInfo;

        private String mClassName;
        private int mRank;

        /**
         * @deprecated Use the other Builder constructors.
         * @removed
         */
        @Deprecated
        public Builder(@NonNull AppTargetIdCompat id) {
            mId = id;
        }

        /**
         * @param id A unique id for this launchable target.
         * @param packageName PackageName of the target.
         * @param user The UserHandle of the user which this target belongs to.
         */
        public Builder(@NonNull AppTargetIdCompat id, @NonNull String packageName,
                @NonNull UserHandle user) {
            mId = Preconditions.checkNotNull(id);
            mPackageName = Preconditions.checkNotNull(packageName);
            mUser = Preconditions.checkNotNull(user);
        }

        /**
         * @param id A unique id for this launchable target.
         * @param info The ShortcutInfo that represents this launchable target.
         */
        public Builder(@NonNull AppTargetIdCompat id, @NonNull ShortcutInfo info) {
            mId = Preconditions.checkNotNull(id);
            mShortcutInfo = Preconditions.checkNotNull(info);
            mPackageName = info.getPackage();
            mUser = info.getUserHandle();
        }

        /**
         * @deprecated Use the appropriate constructor.
         * @removed
         */
        @NonNull
        @Deprecated
        public Builder setTarget(@NonNull String packageName, @NonNull UserHandle user) {
            if (mPackageName != null) {
                throw new IllegalArgumentException("Target is already set");
            }
            mPackageName = Preconditions.checkNotNull(packageName);
            mUser = Preconditions.checkNotNull(user);
            return this;
        }

        /**
         * @deprecated Use the appropriate constructor.
         * @removed
         */
        @NonNull
        @Deprecated
        public Builder setTarget(@NonNull ShortcutInfo info) {
            setTarget(info.getPackage(), info.getUserHandle());
            mShortcutInfo = Preconditions.checkNotNull(info);
            return this;
        }

        /**
         * Sets the className for the target.
         */
        @NonNull
        public Builder setClassName(@NonNull String className) {
            mClassName = Preconditions.checkNotNull(className);
            return this;
        }

        /**
         * Sets the rank of the target.
         */
        @NonNull
        public Builder setRank(@IntRange(from = 0) int rank) {
            if (rank < 0) {
                throw new IllegalArgumentException("rank cannot be a negative value");
            }
            mRank = rank;
            return this;
        }

        /**
         * Builds a new AppTarget instance.
         *
         * @throws IllegalStateException if no target is set
         * @see #setTarget(ShortcutInfo)
         * @see #setTarget(String, UserHandle)
         */
        @NonNull
        public AppTargetCompat build() {
            if (mPackageName == null) {
                throw new IllegalStateException("No target is set");
            }
            return new AppTargetCompat(mId, mPackageName, mUser, mShortcutInfo, mClassName, mRank);
        }
    }

    public static final @NonNull Parcelable.Creator<AppTargetCompat> CREATOR =
            new Parcelable.Creator<AppTargetCompat>() {
                public AppTargetCompat createFromParcel(Parcel parcel) {
                    return new AppTargetCompat(parcel);
                }

                public AppTargetCompat[] newArray(int size) {
                    return new AppTargetCompat[size];
                }
            };
}
