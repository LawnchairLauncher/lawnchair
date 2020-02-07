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

import static com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;

import androidx.annotation.Keep;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Class to handle requests from tests
 */
@TargetApi(Build.VERSION_CODES.Q)
public class TestInformationHandler implements ResourceBasedOverride {

    public static TestInformationHandler newInstance(Context context) {
        return Overrides.getObject(TestInformationHandler.class,
                context, R.string.test_information_handler_class);
    }

    protected Context mContext;
    protected DeviceProfile mDeviceProfile;
    protected LauncherAppState mLauncherAppState;
    private static LinkedList mLeaks;

    public void init(Context context) {
        mContext = context;
        mDeviceProfile = InvariantDeviceProfile.INSTANCE.
                get(context).getDeviceProfile(context);
        mLauncherAppState = LauncherAppState.getInstanceNoCreate();
    }

    public Bundle call(String method) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_ALL_APPS_TO_OVERVIEW_SWIPE_HEIGHT: {
                return getLauncherUIProperty(Bundle::putInt, l -> {
                    final float progress = LauncherState.OVERVIEW.getVerticalProgress(l)
                            - LauncherState.ALL_APPS.getVerticalProgress(l);
                    final float distance = l.getAllAppsController().getShiftRange() * progress;
                    return (int) distance;
                });
            }

            case TestProtocol.REQUEST_HOME_TO_ALL_APPS_SWIPE_HEIGHT: {
                return getLauncherUIProperty(Bundle::putInt, l -> {
                    final float progress = LauncherState.NORMAL.getVerticalProgress(l)
                            - LauncherState.ALL_APPS.getVerticalProgress(l);
                    final float distance = l.getAllAppsController().getShiftRange() * progress;
                    return (int) distance;
                });
            }

            case TestProtocol.REQUEST_IS_LAUNCHER_INITIALIZED: {
                return getUIProperty(Bundle::putBoolean, t -> isLauncherInitialized(), () -> true);
            }

            case TestProtocol.REQUEST_ENABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = true;
                break;

            case TestProtocol.REQUEST_DISABLE_DEBUG_TRACING:
                TestProtocol.sDebugTracing = false;
                break;

            case TestProtocol.REQUEST_FREEZE_APP_LIST:
                return getLauncherUIProperty(Bundle::putBoolean, l -> {
                    l.getAppsView().getAppsStore().enableDeferUpdates(DEFER_UPDATES_TEST);
                    return true;
                });
            case TestProtocol.REQUEST_UNFREEZE_APP_LIST:
                return getLauncherUIProperty(Bundle::putBoolean, l -> {
                    l.getAppsView().getAppsStore().disableDeferUpdates(DEFER_UPDATES_TEST);
                    return true;
                });

            case TestProtocol.REQUEST_APP_LIST_FREEZE_FLAGS: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getAppsStore().getDeferUpdatesFlags());
            }

            case TestProtocol.REQUEST_APPS_LIST_SCROLL_Y: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getActiveRecyclerView().getCurrentScrollY());
            }

            case TestProtocol.REQUEST_WINDOW_INSETS: {
                return getUIProperty(Bundle::putParcelable, a -> {
                    WindowInsets insets = a.getWindow()
                            .getDecorView().getRootWindowInsets();
                    return Insets.max(
                            insets.getSystemGestureInsets(), insets.getSystemWindowInsets());
                }, this::getCurrentActivity);
            }

            case TestProtocol.REQUEST_PID: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, Os.getpid());
                break;
            }

            case TestProtocol.REQUEST_TOTAL_PSS_KB: {
                runGcAndFinalizersSync();
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

            case TestProtocol.REQUEST_VIEW_LEAK: {
                if (mLeaks == null) mLeaks = new LinkedList();
                mLeaks.add(new View(mContext));
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
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.LAUNCHER_DIDNT_INITIALIZE,
                    "isLauncherInitialized " + Launcher.ACTIVITY_TRACKER.getCreatedActivity() + ", "
                            + LauncherAppState.getInstance(mContext).getModel().isModelLoaded());
        }
        return Launcher.ACTIVITY_TRACKER.getCreatedActivity() == null
                || LauncherAppState.getInstance(mContext).getModel().isModelLoaded();
    }

    protected Activity getCurrentActivity() {
        return Launcher.ACTIVITY_TRACKER.getCreatedActivity();
    }

    private static void runGcAndFinalizersSync() {
        Runtime.getRuntime().gc();
        Runtime.getRuntime().runFinalization();

        final CountDownLatch fence = new CountDownLatch(1);
        createFinalizationObserver(fence);
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Returns the result by getting a Launcher property on UI thread
     */
    public static <T> Bundle getLauncherUIProperty(
            BundleSetter<T> bundleSetter, Function<Launcher, T> provider) {
        return getUIProperty(bundleSetter, provider, Launcher.ACTIVITY_TRACKER::getCreatedActivity);
    }

    /**
     * Returns the result by getting a generic property on UI thread
     */
    private static <S, T> Bundle getUIProperty(
            BundleSetter<T> bundleSetter, Function<S, T> provider, Supplier<S> targetSupplier) {
        try {
            return MAIN_EXECUTOR.submit(() -> {
                S target = targetSupplier.get();
                if (target == null) {
                    return null;
                }
                T value = provider.apply(target);
                Bundle response = new Bundle();
                bundleSetter.set(response, TestProtocol.TEST_INFO_RESPONSE_FIELD, value);
                return response;
            }).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generic interface for setting a fiend in bundle
     * @param <T> the type of value being set
     */
    public interface BundleSetter<T> {

        /**
         * Sets any generic property to the bundle
         */
        void set(Bundle b, String key, T value);
    }

    // Create the observer in the scope of a method to minimize the chance that
    // it remains live in a DEX/machine register at the point of the fence guard.
    // This must be kept to avoid R8 inlining it.
    @Keep
    private static void createFinalizationObserver(CountDownLatch fence) {
        new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        };
    }
}
