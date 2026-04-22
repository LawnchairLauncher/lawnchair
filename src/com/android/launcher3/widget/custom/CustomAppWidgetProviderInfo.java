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

package com.android.launcher3.widget.custom;

import android.content.ComponentName;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;

/**
 * Custom app widget provider info that can be used as a widget, but provide extra functionality
 * by allowing custom code and views.
 */
public class CustomAppWidgetProviderInfo extends LauncherAppWidgetProviderInfo
        implements Parcelable {

    protected CustomAppWidgetProviderInfo(Parcel parcel, boolean readSelf) {
        super(parcel);
        if (readSelf) {
            provider = new ComponentName(parcel.readString(),
                    CLS_CUSTOM_WIDGET_PREFIX + parcel.readString());

            label = parcel.readString();
            initialLayout = parcel.readInt();
            icon = parcel.readInt();
            previewImage = parcel.readInt();

            resizeMode = parcel.readInt();
            spanX = parcel.readInt();
            spanY = parcel.readInt();
            minSpanX = parcel.readInt();
            minSpanY = parcel.readInt();
        }
    }

    @VisibleForTesting
    CustomAppWidgetProviderInfo() {}

    @Override
    public void initSpans(Context context, InvariantDeviceProfile idp) {
        mIsMinSizeFulfilled = Math.min(spanX, minSpanX) <= idp.numColumns
                && Math.min(spanY, minSpanY) <= idp.numRows;
    }

    @Override
    public CharSequence getLabel() {
        return Utilities.trim(label);
    }

    @Override
    public String toString() {
        return "WidgetProviderInfo(" + provider + ")";
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeString(provider.getPackageName());
        out.writeString(provider.getClassName());

        out.writeString(label);
        out.writeInt(initialLayout);
        out.writeInt(icon);
        out.writeInt(previewImage);

        out.writeInt(resizeMode);
        out.writeInt(spanX);
        out.writeInt(spanY);
        out.writeInt(minSpanX);
        out.writeInt(minSpanY);
    }

    public static final Parcelable.Creator<CustomAppWidgetProviderInfo> CREATOR =
            new Parcelable.Creator<>() {

        @Override
        public CustomAppWidgetProviderInfo createFromParcel(Parcel parcel) {
            return new CustomAppWidgetProviderInfo(parcel, true);
        }

        @Override
        public CustomAppWidgetProviderInfo[] newArray(int size) {
            return new CustomAppWidgetProviderInfo[size];
        }
    };
}
