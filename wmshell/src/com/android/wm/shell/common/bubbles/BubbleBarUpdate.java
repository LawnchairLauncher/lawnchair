/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.common.bubbles;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Point;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an update to bubbles state. This is passed through
 * {@link com.android.wm.shell.bubbles.IBubblesListener} to launcher so that taskbar may render
 * bubbles. This should be kept this as minimal as possible in terms of data.
 */
public class BubbleBarUpdate implements Parcelable {

    public static final String BUNDLE_KEY = "update";

    public final boolean initialState;
    public boolean expandedChanged;
    public boolean expanded;
    public boolean shouldShowEducation;
    @Nullable
    public String selectedBubbleKey;
    @Nullable
    public BubbleInfo addedBubble;
    @Nullable
    public BubbleInfo updatedBubble;
    @Nullable
    public String suppressedBubbleKey;
    @Nullable
    public String unsupressedBubbleKey;
    @Nullable
    public BubbleBarLocation bubbleBarLocation;
    @Nullable
    public Point expandedViewDropTargetSize;
    public boolean showOverflowChanged;
    public boolean showOverflow;

    // This is only populated if bubbles have been removed.
    public List<RemovedBubble> removedBubbles = new ArrayList<>();

    // This is only populated if the order of the bubbles has changed.
    public List<String> bubbleKeysInOrder = new ArrayList<>();

    // This is only populated the first time a listener is connected so it gets the current state.
    public List<BubbleInfo> currentBubbleList = new ArrayList<>();


    public BubbleBarUpdate() {
        this(false);
    }

    private BubbleBarUpdate(boolean initialState) {
        this.initialState = initialState;
    }

    public BubbleBarUpdate(Parcel parcel) {
        initialState = parcel.readBoolean();
        expandedChanged = parcel.readBoolean();
        expanded = parcel.readBoolean();
        shouldShowEducation = parcel.readBoolean();
        selectedBubbleKey = parcel.readString();
        addedBubble = parcel.readParcelable(BubbleInfo.class.getClassLoader(),
                BubbleInfo.class);
        updatedBubble = parcel.readParcelable(BubbleInfo.class.getClassLoader(),
                BubbleInfo.class);
        suppressedBubbleKey = parcel.readString();
        unsupressedBubbleKey = parcel.readString();
        removedBubbles = parcel.readParcelableList(new ArrayList<>(),
                RemovedBubble.class.getClassLoader(), RemovedBubble.class);
        parcel.readStringList(bubbleKeysInOrder);
        currentBubbleList = parcel.readParcelableList(new ArrayList<>(),
                BubbleInfo.class.getClassLoader(), BubbleInfo.class);
        bubbleBarLocation = parcel.readParcelable(BubbleBarLocation.class.getClassLoader(),
                BubbleBarLocation.class);
        expandedViewDropTargetSize = parcel.readParcelable(Point.class.getClassLoader(),
                Point.class);
        showOverflowChanged = parcel.readBoolean();
        showOverflow = parcel.readBoolean();
    }

    /**
     * Returns whether anything has changed in this update.
     */
    public boolean anythingChanged() {
        return expandedChanged
                || selectedBubbleKey != null
                || addedBubble != null
                || updatedBubble != null
                || !removedBubbles.isEmpty()
                || !bubbleKeysInOrder.isEmpty()
                || suppressedBubbleKey != null
                || unsupressedBubbleKey != null
                || !currentBubbleList.isEmpty()
                || bubbleBarLocation != null
                || showOverflowChanged;
    }

    @NonNull
    @Override
    public String toString() {
        return "BubbleBarUpdate{"
                + " initialState=" + initialState
                + " expandedChanged=" + expandedChanged
                + " expanded=" + expanded
                + " selectedBubbleKey=" + selectedBubbleKey
                + " shouldShowEducation=" + shouldShowEducation
                + " addedBubble=" + addedBubble
                + " updatedBubble=" + updatedBubble
                + " suppressedBubbleKey=" + suppressedBubbleKey
                + " unsuppressedBubbleKey=" + unsupressedBubbleKey
                + " removedBubbles=" + removedBubbles
                + " bubbles=" + bubbleKeysInOrder
                + " currentBubbleList=" + currentBubbleList
                + " bubbleBarLocation=" + bubbleBarLocation
                + " expandedViewDropTargetSize=" + expandedViewDropTargetSize
                + " showOverflowChanged=" + showOverflowChanged
                + " showOverflow=" + showOverflow
                + " }";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeBoolean(initialState);
        parcel.writeBoolean(expandedChanged);
        parcel.writeBoolean(expanded);
        parcel.writeBoolean(shouldShowEducation);
        parcel.writeString(selectedBubbleKey);
        parcel.writeParcelable(addedBubble, flags);
        parcel.writeParcelable(updatedBubble, flags);
        parcel.writeString(suppressedBubbleKey);
        parcel.writeString(unsupressedBubbleKey);
        parcel.writeParcelableList(removedBubbles, flags);
        parcel.writeStringList(bubbleKeysInOrder);
        parcel.writeParcelableList(currentBubbleList, flags);
        parcel.writeParcelable(bubbleBarLocation, flags);
        parcel.writeParcelable(expandedViewDropTargetSize, flags);
        parcel.writeBoolean(showOverflowChanged);
        parcel.writeBoolean(showOverflow);
    }

    /**
     * Create update for initial set of values.
     * <p>
     * Used when bubble bar is newly created.
     */
    public static BubbleBarUpdate createInitialState() {
        return new BubbleBarUpdate(true);
    }

    @NonNull
    public static final Creator<BubbleBarUpdate> CREATOR =
            new Creator<>() {
                public BubbleBarUpdate createFromParcel(Parcel source) {
                    return new BubbleBarUpdate(source);
                }

                public BubbleBarUpdate[] newArray(int size) {
                    return new BubbleBarUpdate[size];
                }
            };
}
