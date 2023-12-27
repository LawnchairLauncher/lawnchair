/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.testing.shared;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Request object for querying a hotseat cell region in Rect.
 */
public class HotseatCellCenterRequest implements TestInformationRequest {
    public final int cellInd;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(cellInd);
    }

    @Override
    public String getRequestName() {
        return TestProtocol.REQUEST_HOTSEAT_CELL_CENTER;
    }

    public static final Parcelable.Creator<HotseatCellCenterRequest> CREATOR =
            new Parcelable.Creator<HotseatCellCenterRequest>() {

                @Override
                public HotseatCellCenterRequest createFromParcel(Parcel source) {
                    return new HotseatCellCenterRequest(source);
                }

                @Override
                public HotseatCellCenterRequest[] newArray(int size) {
                    return new HotseatCellCenterRequest[size];
                }
            };

    private HotseatCellCenterRequest(int cellInd) {
        this.cellInd = cellInd;
    }

    private HotseatCellCenterRequest(Parcel in) {
        this(in.readInt());
    }

    /**
     * Create a builder for HotseatCellCenterRequest.
     *
     * @return HotseatCellCenterRequest builder.
     */
    public static HotseatCellCenterRequest.Builder builder() {
        return new HotseatCellCenterRequest.Builder();
    }

    /**
     * HotseatCellCenterRequest Builder.
     */
    public static final class Builder {
        private int mCellInd;

        private Builder() {
            mCellInd = 0;
        }

        /**
         * Set the index of hotseat cells.
         */
        public HotseatCellCenterRequest.Builder setCellInd(int i) {
            this.mCellInd = i;
            return this;
        }

        /**
         * build the HotseatCellCenterRequest.
         */
        public HotseatCellCenterRequest build() {
            return new HotseatCellCenterRequest(mCellInd);
        }
    }
}
