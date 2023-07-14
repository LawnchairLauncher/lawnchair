/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Wrapper class for parameters to start an activity.
 */
public class StartActivityParams implements Parcelable {

    private static final String TAG = "StartActivityParams";

    private final PendingIntent mPICallback;
    public final int requestCode;

    public Intent intent;

    public IntentSender intentSender;
    public Intent fillInIntent;
    public int flagsMask;
    public int flagsValues;
    public int extraFlags;
    public Bundle options;

    public StartActivityParams(Activity activity, int requestCode) {
        this(activity.createPendingResult(requestCode, new Intent(),
                FLAG_ONE_SHOT | FLAG_UPDATE_CURRENT | FLAG_MUTABLE), requestCode);
    }

    public StartActivityParams(PendingIntent pendingIntent, int requestCode) {
        this.mPICallback = pendingIntent;
        this.requestCode = requestCode;
    }

    private StartActivityParams(Parcel parcel) {
        mPICallback = parcel.readTypedObject(PendingIntent.CREATOR);
        requestCode = parcel.readInt();
        intent = parcel.readTypedObject(Intent.CREATOR);

        intentSender = parcel.readTypedObject(IntentSender.CREATOR);
        fillInIntent = parcel.readTypedObject(Intent.CREATOR);
        flagsMask = parcel.readInt();
        flagsValues = parcel.readInt();
        extraFlags = parcel.readInt();
        options = parcel.readBundle();
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeTypedObject(mPICallback, flags);
        parcel.writeInt(requestCode);
        parcel.writeTypedObject(intent, flags);

        parcel.writeTypedObject(intentSender, flags);
        parcel.writeTypedObject(fillInIntent, flags);
        parcel.writeInt(flagsMask);
        parcel.writeInt(flagsValues);
        parcel.writeInt(extraFlags);
        parcel.writeBundle(options);
    }

    /** Perform the operation on the pendingIntent. */
    public void deliverResult(Context context, int resultCode, Intent data) {
        try {
            if (mPICallback != null) {
                mPICallback.send(context, resultCode, data);
            }
        } catch (CanceledException e) {
            Log.e(TAG, "Unable to send back result", e);
        }
    }

    public static final Parcelable.Creator<StartActivityParams> CREATOR =
            new Parcelable.Creator<>() {
                public StartActivityParams createFromParcel(Parcel source) {
                    return new StartActivityParams(source);
                }

                public StartActivityParams[] newArray(int size) {
                    return new StartActivityParams[size];
                }
            };
}
