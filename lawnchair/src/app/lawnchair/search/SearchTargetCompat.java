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
package app.lawnchair.search;

import android.app.search.SearchTarget;
import android.app.slice.SliceManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.search.LayoutType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * A representation of a search result. Search result can be expressed in one of the following:
 * app icon, shortcut, slice, widget, or a custom object using {@link SearchActionCompat}. While
 * app icon ({@link PackageManager}, shortcut {@link ShortcutManager}, slice {@link SliceManager},
 * or widget (@link AppWidgetManager} are published content backed by the system service,
 * {@link SearchActionCompat} is a custom object that the service can use to send search result to the
 * client.
 *
 * These various types of Android primitives could be defined as {@link SearchResultType}. Some
 * times, the result type can define the layout type that that this object can be rendered in.
 * (e.g., app widget). Most times, {@link #getLayoutType()} assigned by the service
 * can recommend which layout this target should be rendered in.
 *
 * The service can also use fields such as {@link #getScore()} to indicate
 * how confidence the search result is and {@link #isHidden()} to indicate
 * whether it is recommended to be shown by default.
 *
 * Finally, {@link #getId()} is the unique identifier of this search target and a single
 * search target is defined by being able to express a single launcheable item. In case the
 * service want to recommend how to combine multiple search target objects to render in a group
 * (e.g., same row), {@link #getParentId()} can be assigned on the sub targets of the group
 * using the primary search target's identifier.
 *
 * @hide
 */
public final class SearchTargetCompat implements Parcelable {

    public static final int RESULT_TYPE_APPLICATION = 1;
    public static final int RESULT_TYPE_SHORTCUT = 1 << 1;
    public static final int RESULT_TYPE_SLICE = 1 << 2;
    public static final int RESULT_TYPE_WIDGETS = 1 << 3;

    //     ------
    //    | icon |
    //     ------
    //      text
    public static final String LAYOUT_TYPE_ICON = "icon";

    //     ------                            ------   ------
    //    |      | title                    |(opt)|  |(opt)|
    //    | icon | subtitle (optional)      | icon|  | icon|
    //     ------                            ------  ------
    public static final String LAYOUT_TYPE_ICON_ROW = "icon_row";

    //     ------
    //    | icon | title / subtitle (optional)
    //     ------
    public static final String LAYOUT_TYPE_SHORT_ICON_ROW = "short_icon_row";

    /**
     * @hide
     */
    @IntDef(value = {
            RESULT_TYPE_APPLICATION,
            RESULT_TYPE_SHORTCUT,
            RESULT_TYPE_SLICE,
            RESULT_TYPE_WIDGETS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchResultType {}
    private final int mResultType;

    /**
     * Constant to express how the group of {@link SearchTargetCompat} should be rendered on
     * the client side. (e.g., "icon", "icon_row", "short_icon_row")
     */
    @NonNull
    private final String mLayoutType;

    @NonNull
    private final String mId;

    @Nullable
    private final String mParentId;

    private final float mScore;

    private final boolean mHidden;

    @NonNull
    private final String mPackageName;
    @NonNull
    private final UserHandle mUserHandle;
    @Nullable
    private final SearchActionCompat mSearchAction;
    @Nullable
    private final ShortcutInfo mShortcutInfo;
    @Nullable
    private final AppWidgetProviderInfo mAppWidgetProviderInfo;
    @Nullable
    private final Uri mSliceUri;

    @NonNull
    private final Bundle mExtras;

    private SearchTargetCompat(Parcel parcel) {
        mResultType = parcel.readInt();
        mLayoutType = parcel.readString();
        mId = parcel.readString();
        mParentId = parcel.readString();
        mScore = parcel.readFloat();
        mHidden = parcel.readBoolean();

        mPackageName = parcel.readString();
        mUserHandle = UserHandle.of(parcel.readInt());
        mSearchAction = parcel.readTypedObject(SearchActionCompat.CREATOR);
        mShortcutInfo = parcel.readTypedObject(ShortcutInfo.CREATOR);
        mAppWidgetProviderInfo = parcel.readTypedObject(AppWidgetProviderInfo.CREATOR);
        mSliceUri = parcel.readTypedObject(Uri.CREATOR);
        mExtras = parcel.readBundle(getClass().getClassLoader());
    }

    private SearchTargetCompat(
            int resultType,
            @NonNull String layoutType,
            @NonNull String id,
            @Nullable String parentId,
            float score, boolean hidden,
            @NonNull String packageName,
            @NonNull UserHandle userHandle,
            @Nullable SearchActionCompat action,
            @Nullable ShortcutInfo shortcutInfo,
            @Nullable Uri sliceUri,
            @Nullable AppWidgetProviderInfo appWidgetProviderInfo,
            @NonNull Bundle extras) {
        mResultType = resultType;
        mLayoutType = Objects.requireNonNull(layoutType);
        mId = Objects.requireNonNull(id);
        mParentId = parentId;
        mScore = score;
        mHidden = hidden;
        mPackageName = Objects.requireNonNull(packageName);
        mUserHandle = Objects.requireNonNull(userHandle);
        mSearchAction = action;
        mShortcutInfo = shortcutInfo;
        mAppWidgetProviderInfo = appWidgetProviderInfo;
        mSliceUri = sliceUri;
        mExtras = extras;

        int published = 0;
        if (mSearchAction != null) published++;
        if (mShortcutInfo != null) published++;
        if (mAppWidgetProviderInfo != null) published++;
        if (mSliceUri != null) published++;
        if (published > 1) {
            throw new IllegalStateException("Only one of SearchAction, ShortcutInfo,"
                    + " AppWidgetProviderInfo, SliceUri can be assigned in a SearchTargetCompat.");
        }
    }

    private SearchTargetCompat(SearchTarget from) {
        this(
                from.getResultType(),
                from.getLayoutType(),
                from.getId(),
                from.getParentId(),
                from.getScore(), from.isHidden(),
                from.getPackageName(),
                from.getUserHandle(),
                SearchActionCompat.wrap(from.getSearchAction()),
                from.getShortcutInfo(),
                from.getSliceUri(),
                from.getAppWidgetProviderInfo(),
                from.getExtras()
        );
    }

    /**
     * Retrieves the result type {@see SearchResultType}.
     */
    public @SearchResultType int getResultType() {
        return mResultType;
    }

    /**
     * Retrieves the layout type.
     */
    @NonNull
    public @LayoutType.SearchLayoutType String getLayoutType() {
        return mLayoutType;
    }

    /**
     * Retrieves the id of the target.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Retrieves the parent id of the target.
     */
    @NonNull
    public String getParentId() {
        return mParentId;
    }

    /**
     * Retrieves the score of the target.
     */
    public float getScore() {
        return mScore;
    }

    /**
     * Indicates whether this object should be hidden and shown only on demand.
     *
     * @deprecated will be removed once SDK drops
     * @removed
     */
    @Deprecated
    public boolean shouldHide() {
        return mHidden;
    }

    /**
     * Indicates whether this object should be hidden and shown only on demand.
     */
    public boolean isHidden() {
        return mHidden;
    }

    /**
     * Retrieves the package name of the target.
     */
    @NonNull
    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Retrieves the user handle of the target.
     */
    @NonNull
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    /**
     * Retrieves the shortcut info of the target.
     */
    @Nullable
    public ShortcutInfo getShortcutInfo() {
        return mShortcutInfo;
    }

    /**
     * Return a widget provider info.
     */
    @Nullable
    public AppWidgetProviderInfo getAppWidgetProviderInfo() {
        return mAppWidgetProviderInfo;
    }

    /**
     * Returns a slice uri.
     */
    @Nullable
    public Uri getSliceUri() {
        return mSliceUri;
    }

    /**
     * Returns a search action.
     */
    @Nullable
    public SearchActionCompat getSearchAction() {
        return mSearchAction;
    }

    /**
     * Return extra bundle.
     */
    @NonNull
    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeInt(mResultType);
        parcel.writeString(mLayoutType);
        parcel.writeString(mId);
        parcel.writeString(mParentId);
        parcel.writeFloat(mScore);
        parcel.writeBoolean(mHidden);
        parcel.writeString(mPackageName);
        parcel.writeInt(mUserHandle.getIdentifier());
        parcel.writeTypedObject(mSearchAction, flags);
        parcel.writeTypedObject(mShortcutInfo, flags);
        parcel.writeTypedObject(mAppWidgetProviderInfo, flags);
        parcel.writeTypedObject(mSliceUri, flags);
        parcel.writeBundle(mExtras);
    }

    public static SearchTargetCompat wrap(SearchTarget target) {
        return new SearchTargetCompat(target);
    }

    /**
     * @see Parcelable.Creator
     */
    @NonNull
    public static final Parcelable.Creator<SearchTargetCompat> CREATOR =
            new Parcelable.Creator<SearchTargetCompat>() {
                public SearchTargetCompat createFromParcel(Parcel parcel) {
                    return new SearchTargetCompat(parcel);
                }

                public SearchTargetCompat[] newArray(int size) {
                    return new SearchTargetCompat[size];
                }
            };

    /**
     * A builder for search target object.
     *
     * @hide
     */
    public static final class Builder {
        private final int mResultType;
        @NonNull
        private final String mLayoutType;
        @NonNull
        private final String mId;
        @Nullable
        private String mParentId;
        private float mScore;
        private boolean mHidden;
        @NonNull
        private String mPackageName;
        @NonNull
        private UserHandle mUserHandle;
        @Nullable
        private SearchActionCompat mSearchAction;
        @Nullable
        private ShortcutInfo mShortcutInfo;
        @Nullable
        private Uri mSliceUri;
        @Nullable
        private AppWidgetProviderInfo mAppWidgetProviderInfo;
        @NonNull
        private Bundle mExtras;

        public Builder(@SearchResultType int resultType,
                       @LayoutType.SearchLayoutType @NonNull String layoutType,
                       @NonNull String id) {
            mId = id;
            mLayoutType = Objects.requireNonNull(layoutType);
            mResultType = resultType;
            mScore = 1f;
            mHidden = false;
        }

        /**
         * Sets the parent id.
         */
        @NonNull
        public Builder setParentId(@NonNull String parentId) {
            mParentId = Objects.requireNonNull(parentId);
            return this;
        }

        /**
         * Sets the package name.
         */
        @NonNull
        public Builder setPackageName(@NonNull String packageName) {
            mPackageName = Objects.requireNonNull(packageName);
            return this;
        }

        /**
         * Sets the user handle.
         */
        @NonNull
        public Builder setUserHandle(@NonNull UserHandle userHandle) {
            mUserHandle = Objects.requireNonNull(userHandle);
            return this;
        }

        /**
         * Sets the shortcut info.
         */
        @NonNull
        public Builder setShortcutInfo(@NonNull ShortcutInfo shortcutInfo) {
            mShortcutInfo = Objects.requireNonNull(shortcutInfo);
            if (mPackageName != null && !mPackageName.equals(shortcutInfo.getPackage())) {
                throw new IllegalStateException("SearchTargetCompat packageName is different from "
                        + "shortcut's packageName");
            }
            mPackageName = shortcutInfo.getPackage();
            return this;
        }

        /**
         * Sets the app widget provider info.
         */
        @NonNull
        public Builder setAppWidgetProviderInfo(
                @NonNull AppWidgetProviderInfo appWidgetProviderInfo) {
            mAppWidgetProviderInfo = Objects.requireNonNull(appWidgetProviderInfo);
            if (mPackageName != null
                    && !mPackageName.equals(appWidgetProviderInfo.provider.getPackageName())) {
                throw new IllegalStateException("SearchTargetCompat packageName is different from "
                        + "appWidgetProviderInfo's packageName");
            }
            return this;
        }

        /**
         * Sets the slice URI.
         */
        @NonNull
        public Builder setSliceUri(@NonNull Uri sliceUri) {
            mSliceUri = sliceUri;
            return this;
        }

        /**
         * Set the {@link SearchActionCompat} object to this target.
         */
        @NonNull
        public Builder setSearchAction(@Nullable SearchActionCompat searchAction) {
            mSearchAction = searchAction;
            return this;
        }

        /**
         * Set any extra information that needs to be shared between service and the client.
         */
        @NonNull
        public Builder setExtras(@NonNull Bundle extras) {
            mExtras = Objects.requireNonNull(extras);
            return this;
        }

        /**
         * Sets the score of the object.
         */
        @NonNull
        public Builder setScore(@FloatRange(from = 0.0f, to = 1.0f) float score) {
            mScore = score;
            return this;
        }

        /**
         * Sets whether the result should be hidden (e.g. not visible) by default inside client.
         */
        @NonNull
        public Builder setHidden(boolean hidden) {
            mHidden = hidden;
            return this;
        }

        /**
         * Sets whether the result should be hidden by default inside client.
         * @deprecated will be removed once SDK drops
         * @removed
         */
        @NonNull
        @Deprecated
        public Builder setShouldHide(boolean shouldHide) {
            mHidden = shouldHide;
            return this;
        }

        /**
         * Builds a new SearchTargetCompat instance.
         *
         * @throws IllegalStateException if no target is set
         */
        @NonNull
        public SearchTargetCompat build() {
            return new SearchTargetCompat(mResultType, mLayoutType, mId, mParentId, mScore, mHidden,
                    mPackageName, mUserHandle,
                    mSearchAction, mShortcutInfo, mSliceUri, mAppWidgetProviderInfo,
                    mExtras);
        }
    }
}
