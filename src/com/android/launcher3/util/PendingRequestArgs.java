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

import android.content.ContentValues;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.widget.WidgetAddFlowHandler;

/**
 * Utility class to store information regarding a pending request made by launcher. This information
 * can be saved across launcher instances.
 */
public class PendingRequestArgs extends ItemInfo implements Parcelable {

    private static final int TYPE_NONE = 0;
    private static final int TYPE_INTENT = 1;
    private static final int TYPE_APP_WIDGET = 2;

    private final int mArg1;
    private final int mObjectType;
    private final Parcelable mObject;

    public PendingRequestArgs(ItemInfo info) {
        mArg1 = 0;
        mObjectType = TYPE_NONE;
        mObject = null;

        copyFrom(info);
    }

    private PendingRequestArgs(int arg1, int objectType, Parcelable object) {
        mArg1 = arg1;
        mObjectType = objectType;
        mObject = object;
    }

    public PendingRequestArgs(Parcel parcel) {
        readFromValues(ContentValues.CREATOR.createFromParcel(parcel));
        user = parcel.readParcelable(null);

        mArg1 = parcel.readInt();
        mObjectType = parcel.readInt();
        mObject = parcel.readParcelable(getClass().getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        ContentValues itemValues = new ContentValues();
        writeToValues(new ContentWriter(itemValues, null));
        itemValues.writeToParcel(dest, flags);
        dest.writeParcelable(user, flags);

        dest.writeInt(mArg1);
        dest.writeInt(mObjectType);
        dest.writeParcelable(mObject, flags);
    }

    public WidgetAddFlowHandler getWidgetHandler() {
        return mObjectType == TYPE_APP_WIDGET ? (WidgetAddFlowHandler) mObject : null;
    }

    public int getWidgetId() {
        return mObjectType == TYPE_APP_WIDGET ? mArg1 : 0;
    }

    public Intent getPendingIntent() {
        return mObjectType == TYPE_INTENT ? (Intent) mObject : null;
    }

    public int getRequestCode() {
        return mObjectType == TYPE_INTENT ? mArg1 : 0;
    }

    public static PendingRequestArgs forWidgetInfo(
            int appWidgetId, WidgetAddFlowHandler widgetHandler, ItemInfo info) {
        PendingRequestArgs args =
                new PendingRequestArgs(appWidgetId, TYPE_APP_WIDGET, widgetHandler);
        args.copyFrom(info);
        return args;
    }

    public static PendingRequestArgs forIntent(int requestCode, Intent intent, ItemInfo info) {
        PendingRequestArgs args = new PendingRequestArgs(requestCode, TYPE_INTENT, intent);
        args.copyFrom(info);
        return args;
    }

    public static final Parcelable.Creator<PendingRequestArgs> CREATOR =
            new Parcelable.Creator<PendingRequestArgs>() {
                public PendingRequestArgs createFromParcel(Parcel source) {
                    return new PendingRequestArgs(source);
                }

                public PendingRequestArgs[] newArray(int size) {
                    return new PendingRequestArgs[size];
                }
            };
}
