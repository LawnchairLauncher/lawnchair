/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.systemui.shared.system;

import android.graphics.Bitmap;
import android.graphics.GraphicBuffer;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Wraps the internal graphic buffer.
 */
public class GraphicBufferCompat implements Parcelable {

    private GraphicBuffer mBuffer;

    public GraphicBufferCompat(GraphicBuffer buffer) {
        mBuffer = buffer;
    }

    public GraphicBufferCompat(Parcel in) {
        mBuffer = GraphicBuffer.CREATOR.createFromParcel(in);
    }

    public Bitmap toBitmap() {
        return mBuffer != null
                ? Bitmap.createHardwareBitmap(mBuffer)
                : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        mBuffer.writeToParcel(dest, flags);
    }

    public static final Parcelable.Creator<GraphicBufferCompat> CREATOR
            = new Parcelable.Creator<GraphicBufferCompat>() {
        public GraphicBufferCompat createFromParcel(Parcel in) {
            return new GraphicBufferCompat(in);
        }

        public GraphicBufferCompat[] newArray(int size) {
            return new GraphicBufferCompat[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
