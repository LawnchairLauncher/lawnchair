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
package com.android.wm.shell.shared.split;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;

import android.annotation.NonNull;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.wm.shell.shared.split.SplitScreenConstants.PersistentSnapPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Container of various information needed to display split screen
 * tasks/leashes/etc in Launcher
 */
public class SplitBounds implements Parcelable {
    public static final String KEY_EXTRA_SPLIT_BOUNDS = "key_SplitBounds";

    public final Rect leftTopBounds;
    public final Rect rightBottomBounds;
    /** This rect represents the actual gap between the two apps */
    public final Rect visualDividerBounds;
    // This class is orientation-agnostic, so we compute both for later use
    public final float topTaskPercent;
    public final float leftTaskPercent;
    public final float dividerWidthPercent;
    public final float dividerHeightPercent;
    public final @PersistentSnapPosition int snapPosition;
    /**
     * If {@code true}, that means at the time of creation of this object, the
     * split-screened apps were vertically stacked. This is useful in scenarios like
     * rotation where the bounds won't change, but this variable can indicate what orientation
     * the bounds were originally in
     */
    public final boolean appsStackedVertically;
    /**
     * If {@code true}, that means at the time of creation of this object, the phone was in
     * seascape orientation. This is important on devices with insets, because they do not split
     * evenly -- one of the insets must be slightly larger to account for the inset.
     * From landscape, it is the leftTop task that expands slightly.
     * From seascape, it is the rightBottom task that expands slightly.
     */
    public final boolean initiatedFromSeascape;
    /** @deprecated Use {@link #leftTopTaskIds} instead. */
    @Deprecated
    public final int leftTopTaskId;
    /** @deprecated Use {@link #rightBottomTaskIds} instead. */
    @Deprecated
    public final int rightBottomTaskId;
    @NonNull
    public final List<Integer> leftTopTaskIds;
    @NonNull
    public final List<Integer> rightBottomTaskIds;

    public SplitBounds(Rect leftTopBounds, Rect rightBottomBounds,
            int leftTopTaskId, int rightBottomTaskId,
            @NonNull List<Integer> leftTopTaskIds, @NonNull List<Integer> rightBottomTaskIds,
            @PersistentSnapPosition int snapPosition) {
        if (leftTopTaskId == INVALID_TASK_ID || rightBottomTaskId == INVALID_TASK_ID
                || leftTopTaskId == rightBottomTaskId
                || leftTopTaskIds.isEmpty() || rightBottomTaskIds.isEmpty()) {
            throw new IllegalArgumentException("The Split task ids are invalid:"
                    + " leftTopTaskId: " + leftTopTaskId
                    + " rightBottomTaskId: " + rightBottomTaskId
                    + " leftTopTaskId size: "  + leftTopTaskIds.size()
                    + " rightBottomTaskId size: " + rightBottomTaskIds.size());
        }
        this.leftTopBounds = leftTopBounds;
        this.rightBottomBounds = rightBottomBounds;
        this.leftTopTaskIds = List.copyOf(leftTopTaskIds);
        this.rightBottomTaskIds = List.copyOf(rightBottomTaskIds);
        this.leftTopTaskId = leftTopTaskId;
        this.rightBottomTaskId = rightBottomTaskId;
        this.snapPosition = snapPosition;

        if (rightBottomBounds.top > leftTopBounds.top) {
            // vertical apps, horizontal divider
            this.visualDividerBounds = new Rect(leftTopBounds.left, leftTopBounds.bottom,
                    leftTopBounds.right, rightBottomBounds.top);
            appsStackedVertically = true;
            initiatedFromSeascape = false;
        } else {
            // horizontal apps, vertical divider
            this.visualDividerBounds = new Rect(leftTopBounds.right, leftTopBounds.top,
                    rightBottomBounds.left, leftTopBounds.bottom);
            appsStackedVertically = false;
            // The following check is unreliable on devices without insets
            // (initiatedFromSeascape will always be set to false.) This happens to be OK for
            // all our current uses, but should be refactored.
            // TODO: Create a more reliable check, or refactor how splitting works on devices
            //  with insets.
            initiatedFromSeascape = rightBottomBounds.width() > leftTopBounds.width();
        }

        float totalWidth = rightBottomBounds.right - leftTopBounds.left;
        float totalHeight = rightBottomBounds.bottom - leftTopBounds.top;
        leftTaskPercent = leftTopBounds.width() / totalWidth;
        topTaskPercent = leftTopBounds.height() / totalHeight;
        dividerWidthPercent = visualDividerBounds.width() / totalWidth;
        dividerHeightPercent = visualDividerBounds.height() / totalHeight;
    }

    public SplitBounds(Rect leftTopBounds, Rect rightBottomBounds, int leftTopTaskId,
            int rightBottomTaskId, @PersistentSnapPosition int snapPosition) {
        this(leftTopBounds, rightBottomBounds, leftTopTaskId, rightBottomTaskId,
                Collections.singletonList(leftTopTaskId),
                Collections.singletonList(rightBottomTaskId), snapPosition);
    }

    /**
     * Returns the percentage size of the left/top task (compared to the full width/height of
     * the split pair). E.g. if the left task is 4 units wide, the divider is 2 units, and the
     * right task is 4 units, this method will return 0.4f.
     */
    public float getLeftTopTaskPercent() {
        // topTaskPercent and leftTaskPercent are defined at creation time, and are not updated
        // on device rotate, so we have to check appsStackedVertically to return the right
        // creation-time measurements.
        return appsStackedVertically ? topTaskPercent : leftTaskPercent;
    }

    /**
     * Returns the percentage size of the divider's thickness (compared to the full width/height
     * of the split pair). E.g. if the left task is 4 units wide, the divider is 2 units, and
     * the right task is 4 units, this method will return 0.2f.
     */
    public float getDividerPercent() {
        // dividerHeightPercent and dividerWidthPercent are defined at creation time, and are
        // not updated on device rotate, so we have to check appsStackedVertically to return
        // the right creation-time measurements.
        return appsStackedVertically ? dividerHeightPercent : dividerWidthPercent;
    }

    /**
     * Returns the percentage size of the right/bottom task (compared to the full width/height
     * of the split pair). E.g. if the left task is 4 units wide, the divider is 2 units, and
     * the right task is 4 units, this method will return 0.4f.
     */
    public float getRightBottomTaskPercent() {
        return 1 - (getLeftTopTaskPercent() + getDividerPercent());
    }

    public SplitBounds(Parcel parcel) {
        leftTopBounds = parcel.readTypedObject(Rect.CREATOR);
        rightBottomBounds = parcel.readTypedObject(Rect.CREATOR);
        visualDividerBounds = parcel.readTypedObject(Rect.CREATOR);
        topTaskPercent = parcel.readFloat();
        leftTaskPercent = parcel.readFloat();
        appsStackedVertically = parcel.readBoolean();
        initiatedFromSeascape = parcel.readBoolean();
        dividerWidthPercent = parcel.readFloat();
        dividerHeightPercent = parcel.readFloat();
        snapPosition = parcel.readInt();
        leftTopTaskId = parcel.readInt();
        rightBottomTaskId = parcel.readInt();
        int size = parcel.readInt();
        leftTopTaskIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            leftTopTaskIds.add(parcel.readInt());
        }
        size = parcel.readInt();
        rightBottomTaskIds = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            rightBottomTaskIds.add(parcel.readInt());
        }
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(leftTopBounds, flags);
        parcel.writeTypedObject(rightBottomBounds, flags);
        parcel.writeTypedObject(visualDividerBounds, flags);
        parcel.writeFloat(topTaskPercent);
        parcel.writeFloat(leftTaskPercent);
        parcel.writeBoolean(appsStackedVertically);
        parcel.writeBoolean(initiatedFromSeascape);
        parcel.writeFloat(dividerWidthPercent);
        parcel.writeFloat(dividerHeightPercent);
        parcel.writeInt(snapPosition);
        parcel.writeInt(leftTopTaskId);
        parcel.writeInt(rightBottomTaskId);
        parcel.writeInt(leftTopTaskIds.size());
        for (Integer id : leftTopTaskIds) {
            parcel.writeInt(id); // Write each Integer in the List
        }
        parcel.writeInt(rightBottomTaskIds.size());
        for (Integer id : rightBottomTaskIds) {
            parcel.writeInt(id); // Write each Integer in the List
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SplitBounds)) {
            return false;
        }
        // Only need to check the base fields (the other fields are derived from these)
        final SplitBounds other = (SplitBounds) obj;
        return Objects.equals(leftTopBounds, other.leftTopBounds)
                && Objects.equals(rightBottomBounds, other.rightBottomBounds)
                && leftTopTaskIds.equals(other.leftTopTaskIds)
                && rightBottomTaskIds.equals(other.rightBottomTaskIds)
                && snapPosition == other.snapPosition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftTopBounds, rightBottomBounds,
                leftTopTaskId, rightBottomTaskId, leftTopTaskIds, rightBottomTaskIds);
    }

    @Override
    public String toString() {
        return "LeftTop: " + leftTopBounds + " taskId: " + leftTopTaskId
                + ", taskIds: " + leftTopTaskIds + "\n"
                + "RightBottom: " + rightBottomBounds + " taskId: " + rightBottomTaskId
                + ", taskIds: " + rightBottomTaskIds +  "\n"
                + "Divider: " + visualDividerBounds + "\n"
                + "AppsVertical? " + appsStackedVertically + "\n"
                + "snapPosition: " + snapPosition;
    }

    public static final Creator<SplitBounds> CREATOR = new Creator<SplitBounds>() {
        @Override
        public SplitBounds createFromParcel(Parcel in) {
            return new SplitBounds(in);
        }

        @Override
        public SplitBounds[] newArray(int size) {
            return new SplitBounds[size];
        }
    };
}
