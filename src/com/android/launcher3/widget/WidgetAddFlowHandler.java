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
package com.android.launcher3.widget;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.util.PendingRequestArgs;

/**
 * Utility class to handle app widget add flow.
 */
public class WidgetAddFlowHandler implements Parcelable {

    private final AppWidgetProviderInfo mProviderInfo;

    public WidgetAddFlowHandler(AppWidgetProviderInfo providerInfo) {
        mProviderInfo = providerInfo;
    }

    protected WidgetAddFlowHandler(Parcel parcel) {
        mProviderInfo = AppWidgetProviderInfo.CREATOR.createFromParcel(parcel);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        mProviderInfo.writeToParcel(parcel, i);
    }

    public void startBindFlow(Launcher launcher, int appWidgetId, ItemInfo info, int requestCode) {
        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, this, info));
        launcher.getAppWidgetHost()
                .startBindFlow(launcher, appWidgetId, mProviderInfo, requestCode);
    }

    /**
     * @see #startConfigActivity(Launcher, int, ItemInfo, int)
     */
    public boolean startConfigActivity(Launcher launcher, LauncherAppWidgetInfo info,
            int requestCode) {
        return startConfigActivity(launcher, info.appWidgetId, info, requestCode);
    }

    /**
     * Starts the widget configuration flow if needed.
     * @return true if the configuration flow was started, false otherwise.
     */
    public boolean startConfigActivity(Launcher launcher, int appWidgetId, ItemInfo info,
            int requestCode) {
        if (!needsConfigure()) {
            return false;
        }
        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, this, info));
        launcher.getAppWidgetHost().startConfigActivity(launcher, appWidgetId, requestCode);
        return true;
    }

    public boolean needsConfigure() {
        return mProviderInfo.configure != null;
    }

    public LauncherAppWidgetProviderInfo getProviderInfo(Context context) {
        return LauncherAppWidgetProviderInfo.fromProviderInfo(context, mProviderInfo);
    }

    public static final Parcelable.Creator<WidgetAddFlowHandler> CREATOR =
            new Parcelable.Creator<WidgetAddFlowHandler>() {
                public WidgetAddFlowHandler createFromParcel(Parcel source) {
                    return new WidgetAddFlowHandler(source);
                }

                public WidgetAddFlowHandler[] newArray(int size) {
                    return new WidgetAddFlowHandler[size];
                }
            };
}
