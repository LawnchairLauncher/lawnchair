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

package com.android.quickstep;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.TestProtocol;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.quickstep.util.LayoutUtils;

public class TestInformationProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        return 0;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Cursor query(Uri uri, String[] strings, String s, String[] strings1, String s1) {
        return null;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            final Bundle response = new Bundle();
            final Context context = getContext();
            final DeviceProfile deviceProfile = InvariantDeviceProfile.INSTANCE.
                    get(context).getDeviceProfile(context);
            final LauncherAppState launcherAppState = LauncherAppState.getInstanceNoCreate();
            final Launcher launcher = launcherAppState != null ?
                    (Launcher) launcherAppState.getModel().getCallback() : null;

            switch (method) {
                case TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT: {
                    final float swipeHeight =
                            OverviewState.getDefaultSwipeHeight(deviceProfile);
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                    break;
                }

                case TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT: {
                    final float swipeHeight =
                            LayoutUtils.getShelfTrackingDistance(context, deviceProfile);
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                    break;
                }

                case TestProtocol.REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT: {
                    if (launcher == null) return null;

                    final float progress = LauncherState.OVERVIEW.getVerticalProgress(launcher)
                            - LauncherState.ALL_APPS.getVerticalProgress(launcher);
                    final float distance =
                            launcher.getAllAppsController().getShiftRange() * progress;
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) distance);
                    break;
                }

                case TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT: {
                    if (launcher == null) return null;

                    final float progress = LauncherState.NORMAL.getVerticalProgress(launcher)
                            - LauncherState.ALL_APPS.getVerticalProgress(launcher);
                    final float distance =
                            launcher.getAllAppsController().getShiftRange() * progress;
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) distance);
                    break;
                }
            }
            return response;
        }
        return null;
    }
}
