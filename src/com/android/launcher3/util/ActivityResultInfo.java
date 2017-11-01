/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.util;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Utility class which stores information from onActivityResult
 */
public class ActivityResultInfo implements Parcelable {

    public final int requestCode;
    public final int resultCode;
    public final Intent data;

    public ActivityResultInfo(int requestCode, int resultCode, Intent data) {
        this.requestCode = requestCode;
        this.resultCode = resultCode;
        this.data = data;
    }

    private ActivityResultInfo(Parcel parcel) {
        requestCode = parcel.readInt();
        resultCode = parcel.readInt();
        data = parcel.readInt() != 0 ? Intent.CREATOR.createFromParcel(parcel) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(requestCode);
        dest.writeInt(resultCode);
        if (data != null) {
            dest.writeInt(1);
            data.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
    }

    public static final Parcelable.Creator<ActivityResultInfo> CREATOR =
            new Parcelable.Creator<ActivityResultInfo>() {
                public ActivityResultInfo createFromParcel(Parcel source) {
                    return new ActivityResultInfo(source);
                }

                public ActivityResultInfo[] newArray(int size) {
                    return new ActivityResultInfo[size];
                }
            };
}
