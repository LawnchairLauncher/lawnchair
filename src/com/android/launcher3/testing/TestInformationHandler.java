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

import static android.graphics.Bitmap.Config.ARGB_8888;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Debug;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.LinkedList;
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
    private static LinkedList mLeaks;

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
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, isLauncherInitialized());
                break;
            }

            case TestProtocol.REQUEST_ENABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = true;
                break;

            case TestProtocol.REQUEST_DISABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = false;
                break;

            case TestProtocol.REQUEST_FREEZE_APP_LIST:
                MAIN_EXECUTOR.execute(() ->
                        mLauncher.getAppsView().getAppsStore().enableDeferUpdates(
                                AllAppsStore.DEFER_UPDATES_TEST));
                break;

            case TestProtocol.REQUEST_UNFREEZE_APP_LIST:
                MAIN_EXECUTOR.execute(() ->
                        mLauncher.getAppsView().getAppsStore().disableDeferUpdates(
                                AllAppsStore.DEFER_UPDATES_TEST));
                break;

            case TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS: {
                try {
                    final int deferUpdatesFlags = MAIN_EXECUTOR.submit(() ->
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

            case TestProtocol.REQUEST_TOTAL_PSS_KB: {
                Debug.MemoryInfo mem = new Debug.MemoryInfo();
                Debug.getMemoryInfo(mem);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, mem.getTotalPss());
                break;
            }

            case TestProtocol.REQUEST_JAVA_LEAK: {
                if (mLeaks == null) mLeaks = new LinkedList();

                // Allocate and dirty the memory.
                final int leakSize = 1024 * 1024;
                final byte[] bytes = new byte[leakSize];
                for (int i = 0; i < leakSize; i += 239) {
                    bytes[i] = (byte) (i % 256);
                }
                mLeaks.add(bytes);
                break;
            }

            case TestProtocol.REQUEST_NATIVE_LEAK: {
                if (mLeaks == null) mLeaks = new LinkedList();

                // Allocate and dirty a bitmap.
                final Bitmap bitmap = Bitmap.createBitmap(512, 512, ARGB_8888);
                bitmap.eraseColor(Color.RED);
                mLeaks.add(bitmap);
                break;
            }

            case TestProtocol.REQUEST_ICON_HEIGHT: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDeviceProfile.allAppsCellHeightPx);
                break;
            }
        }
        return response;
    }

    protected boolean isLauncherInitialized() {
        final LauncherModel model = LauncherAppState.getInstance(mContext).getModel();
        return model.getCallback() == null || model.isModelLoaded();
    }
}

