/*
 * Copyright (C) 2024 Lawnchair Launcher
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

package com.android.wm.shell.pip;

import android.os.Parcel;
import android.os.Parcelable;
import android.graphics.Rect;

public class PipData implements Parcelable {
    private Rect rect;
    private int intValue;

    public PipData(Rect rect) {
        this.rect = rect;
    }

    public PipData(int intValue) {
        this.intValue = intValue;
    }

    protected PipData(Parcel in) {
        rect = in.readParcelable(Rect.class.getClassLoader());
        intValue = in.readInt();
    }

    public static final Creator<PipData> CREATOR = new Creator<>() {
        @Override
        public PipData createFromParcel(Parcel in) {
            return new PipData (in);
        }

        @Override
        public PipData[] newArray(int size) {
            return new PipData[size];
        }
    };

    public Rect getRect() {
        return rect;
    }

    public int getIntValue() {
        return intValue;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(rect, flags);
        dest.writeInt(intValue);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}