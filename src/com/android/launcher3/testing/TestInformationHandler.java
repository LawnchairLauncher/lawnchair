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
package com.android.launcher3.testing;

import android.content.Context;
import android.os.Bundle;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.concurrent.ExecutionException;

public class TestInformationHandler implements ResourceBasedOverride {

    public static TestInformationHandler newInstance(Context context) {
        return Overrides.getObject(TestInformationHandler.class,
                context, R.string.test_information_handler_class);
    }

    protected Context mContext;
    protected DeviceProfile mDeviceProfile;
    protected LauncherAppState mLauncherAppState;
    protected Launcher mLauncher;

    public void init(Context context) {
        mContext = context;
        mDeviceProfile = InvariantDeviceProfile.INSTANCE.
                get(context).getDeviceProfile(context);
        mLauncherAppState = LauncherAppState.getInstanceNoCreate();
        mLauncher = mLauncherAppState != null ?
                (Launcher) mLauncherAppState.getModel().getCallback() : null;
    }

    public Bundle call(String method) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT: {
                if (mLauncher == null) return null;

                final float progress = LauncherState.OVERVIEW.getVerticalProgress(mLauncher)
                        - LauncherState.ALL_APPS.getVerticalProgress(mLauncher);
                final float distance = mLauncher.getAllAppsController().getShiftRange() * progress;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) distance);
                break;
            }

            case TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT: {
                if (mLauncher == null) return null;

                final float progress = LauncherState.NORMAL.getVerticalProgress(mLauncher)
                        - LauncherState.ALL_APPS.getVerticalProgress(mLauncher);
                final float distance = mLauncher.getAllAppsController().getShiftRange() * progress;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) distance);
                break;
            }

            case TestProtocol.REQUEST_IS_LAUNCHER_INITIALIZED: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, true);
                break;
            }

            case TestProtocol.REQUEST_ENABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = true;
                break;

            case TestProtocol.REQUEST_DISABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = false;
                break;

            case TestProtocol.REQUEST_FREEZE_APP_LIST:
                new MainThreadExecutor().execute(() ->
                        mLauncher.getAppsView().getAppsStore().enableDeferUpdates(
                                AllAppsStore.DEFER_UPDATES_TEST));
                break;

            case TestProtocol.REQUEST_UNFREEZE_APP_LIST:
                new MainThreadExecutor().execute(() ->
                        mLauncher.getAppsView().getAppsStore().disableDeferUpdates(
                                AllAppsStore.DEFER_UPDATES_TEST));
                break;

            case TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS: {
                try {
                    final int deferUpdatesFlags = new MainThreadExecutor().submit(() ->
                            mLauncher.getAppsView().getAppsStore().getDeferUpdatesFlags()).get();
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                            deferUpdatesFlags);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            }
        }
        return response;
    }
}
