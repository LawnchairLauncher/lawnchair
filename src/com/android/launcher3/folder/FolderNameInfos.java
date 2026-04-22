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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Information about a  label suggestions of a Folder.
 */

public class FolderNameInfos {
    public static final int SUCCESS = 1;
    public static final int HAS_PRIMARY = 1 << 1;
    public static final int HAS_SUGGESTIONS = 1 << 2;
    public static final int ERROR_NO_PROVIDER = 1 << 3;
    public static final int ERROR_APP_LOOKUP_FAILED = 1 << 4;
    public static final int ERROR_ALL_APP_LOOKUP_FAILED = 1 << 5;
    public static final int ERROR_NO_LABELS_GENERATED = 1 << 6;
    public static final int ERROR_LABEL_LOOKUP_FAILED = 1 << 7;
    public static final int ERROR_ALL_LABEL_LOOKUP_FAILED = 1 << 8;
    public static final int ERROR_NO_PACKAGES = 1 << 9;

    private int mStatus;
    private final CharSequence[] mLabels;
    private final Float[] mScores;

    public FolderNameInfos() {
        mStatus = 0;
        mLabels = new CharSequence[FolderNameProvider.SUGGEST_MAX];
        mScores = new Float[FolderNameProvider.SUGGEST_MAX];
    }

    /**
     * set the status of FolderNameInfos.
     */
    public void setStatus(int statusBit) {
        mStatus = mStatus | statusBit;
    }

    /**
     * returns status of FolderNameInfos generations.
     */
    public int status() {
        return mStatus;
    }

    /**
     * return true if the first suggestion is a Primary suggestion.
     */
    public boolean hasPrimary() {
        return (mStatus & HAS_PRIMARY) > 0 && (mLabels[0] != null);
    }

    /**
     * return true if there is at least one valid suggestion.
     */
    public boolean hasSuggestions() {
        for (CharSequence l : mLabels) {
            if (l != null && !TextUtils.isEmpty(l)) return true;
        }
        return false;
    }

    /**
     * assign label and score in the specified index.
     */
    public void setLabel(int index, CharSequence label, Float score) {
        if (index < mLabels.length) {
            mLabels[index] = label;
            mScores[index] = score;
        }
    }

    /**
     * returns true if the label is found in label suggestions/
     */
    public boolean contains(CharSequence label) {
        return Arrays.stream(mLabels)
                .filter(Objects::nonNull)
                .anyMatch(l -> l.toString().equalsIgnoreCase(label.toString()));
    }


    public CharSequence[] getLabels() {
        return mLabels;
    }

    public Float[] getScores() {
        return mScores;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format("status=%s, labels=%s", Integer.toBinaryString(mStatus),
                Arrays.toString(mLabels));
    }
}

