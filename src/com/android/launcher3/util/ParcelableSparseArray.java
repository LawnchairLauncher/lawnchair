/**
 * Copyright (C) 2015 The Android Open Source Project
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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;

public class ParcelableSparseArray extends SparseArray<Parcelable> implements Parcelable {
    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        final int count = size();
        dest.writeInt(count);
        for (int i = 0; i < count; i++) {
            dest.writeInt(keyAt(i));
            dest.writeParcelable(valueAt(i), 0);
        }
    }

    public static final Parcelable.Creator<ParcelableSparseArray> CREATOR =
            new Parcelable.Creator<ParcelableSparseArray>() {
        public ParcelableSparseArray createFromParcel(Parcel source) {
            final ParcelableSparseArray array = new ParcelableSparseArray();
            final ClassLoader loader = array.getClass().getClassLoader();
            final int count = source.readInt();
            for (int i = 0; i < count; i++) {
                array.put(source.readInt(), source.readParcelable(loader));
            }
            return array;
        }

        public ParcelableSparseArray[] newArray(int size) {
            return new ParcelableSparseArray[size];
        }
    };
}
