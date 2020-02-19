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
package com.android.launcher3.folder;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;

/**
 * Information about a single label suggestions of the Folder.
 */

public final class FolderNameInfo implements Parcelable {
    private final double mScore;
    private final CharSequence mLabel;

    /**
     * Create a simple completion with label.
     *
     * @param label The text that should be inserted into the editor and pushed to
     *              InputMethodManager suggestions.
     * @param score The score for the label between 0.0 and 1.0.
     */
    public FolderNameInfo(CharSequence label, double score) {
        mScore = score;
        mLabel = label;
    }

    private FolderNameInfo(Parcel source) {
        mScore = source.readDouble();
        mLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    /**
     * Used to package this object into a {@link Parcel}.
     *
     * @param dest  The {@link Parcel} to be written.
     * @param flags The flags used for parceling.
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeDouble(mScore);
        TextUtils.writeToParcel(mLabel, dest, flags);
    }

    /**
     * Used to make this class parcelable.
     */
    @NonNull
    public static final Parcelable.Creator<FolderNameInfo> CREATOR =
            new Parcelable.Creator<FolderNameInfo>() {
                public FolderNameInfo createFromParcel(Parcel source) {
                    return new FolderNameInfo(source);
                }

                public FolderNameInfo[] newArray(int size) {
                    return new FolderNameInfo[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return mLabel.toString() + ":" + mScore;
    }
}
