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

import static com.android.launcher3.allapps.AllAppsStore.DEFER_UPDATES_TEST;
import static com.android.launcher3.Flags.enableGridOnlyOverview;
import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.testing.shared.HotseatCellCenterRequest;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.testing.shared.WorkspaceCellCenterRequest;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.widget.picker.WidgetsFullSheet;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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

    public void init(Context context) {
        mContext = context;
        mDeviceProfile = InvariantDeviceProfile.INSTANCE.
                get(context).getDeviceProfile(context);
        mLauncherAppState = LauncherAppState.getInstanceNoCreate();
    }

    /**
     * handle a request and return result Bundle.
     *
     * @param method request name.
     * @param arg    optional single string argument.
     * @param extra  extra request payload.
     */
    public Bundle call(String method, String arg, @Nullable Bundle extra) {
        final Bundle response = new Bundle();
        if (extra != null && extra.getClassLoader() == null) {
            extra.setClassLoader(getClass().getClassLoader());
        }
        switch (method) {
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

            case TestProtocol.REQUEST_IS_LAUNCHER_LAUNCHER_ACTIVITY_STARTED: {
                final Bundle bundle = getLauncherUIProperty(Bundle::putBoolean, l -> l.isStarted());
                if (bundle != null) return bundle;

                // If Launcher activity wasn't created, it's not started.
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, false);
                return response;
            }

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

            case TestProtocol.REQUEST_APPS_LIST_SCROLL_Y: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getActiveRecyclerView().computeVerticalScrollOffset());
            }

            case TestProtocol.REQUEST_WIDGETS_SCROLL_Y: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> WidgetsFullSheet.getWidgetsView(l).computeVerticalScrollOffset());
            }

            case TestProtocol.REQUEST_TARGET_INSETS: {
                return getUIProperty(Bundle::putParcelable, activity -> {
                    WindowInsets insets = activity.getWindow()
                            .getDecorView().getRootWindowInsets();
                    return Insets.max(
                            insets.getSystemGestureInsets(),
                            insets.getSystemWindowInsets());
                }, this::getCurrentActivity);
            }

            case TestProtocol.REQUEST_WINDOW_INSETS: {
                return getUIProperty(Bundle::putParcelable, activity -> {
                    WindowInsets insets = activity.getWindow()
                            .getDecorView().getRootWindowInsets();
                    return insets.getSystemWindowInsets();
                }, this::getCurrentActivity);
            }

            case TestProtocol.REQUEST_IME_INSETS: {
                return getUIProperty(Bundle::putParcelable, activity -> {
                    WindowInsetsCompat insets = WindowInsetsCompat.toWindowInsetsCompat(
                            activity.getWindow().getDecorView().getRootWindowInsets());
                    return insets.getInsets(WindowInsetsCompat.Type.ime()).toPlatformInsets();
                }, this::getCurrentActivity);
            }

            case TestProtocol.REQUEST_ICON_HEIGHT: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDeviceProfile.allAppsCellHeightPx);
                return response;
            }

            case TestProtocol.REQUEST_MOCK_SENSOR_ROTATION:
                TestProtocol.sDisableSensorRotation = true;
                return response;

            case TestProtocol.REQUEST_IS_TABLET:
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, mDeviceProfile.isTablet);
                return response;

            case TestProtocol.REQUEST_NUM_ALL_APPS_COLUMNS:
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDeviceProfile.numShownAllAppsColumns);
                return response;

            case TestProtocol.REQUEST_IS_TRANSIENT_TASKBAR:
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        DisplayController.isTransientTaskbar(mContext));
                return response;

            case TestProtocol.REQUEST_IS_TWO_PANELS:
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        FOLDABLE_SINGLE_PAGE.get() ? false : mDeviceProfile.isTwoPanels);
                return response;

            case TestProtocol.REQUEST_GET_HAD_NONTEST_EVENTS:
                response.putBoolean(
                        TestProtocol.TEST_INFO_RESPONSE_FIELD, TestLogging.sHadEventsNotFromTest);
                return response;

            case TestProtocol.REQUEST_START_DRAG_THRESHOLD: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
                                + resources.getDimensionPixelSize(R.dimen.pre_drag_view_scale));
                return response;
            }

            case TestProtocol.REQUEST_ENABLE_ROTATION:
                MAIN_EXECUTOR.submit(() ->
                        Launcher.ACTIVITY_TRACKER.getCreatedActivity().getRotationHelper()
                                .forceAllowRotationForTesting(Boolean.parseBoolean(arg)));
                return response;

            case TestProtocol.REQUEST_WORKSPACE_CELL_LAYOUT_SIZE:
                return getLauncherUIProperty(Bundle::putIntArray, launcher -> {
                    final Workspace<?> workspace = launcher.getWorkspace();
                    final int screenId = workspace.getScreenIdForPageIndex(
                            workspace.getCurrentPage());
                    final CellLayout cellLayout = workspace.getScreenWithId(screenId);
                    return new int[]{cellLayout.getCountX(), cellLayout.getCountY()};
                });

            case TestProtocol.REQUEST_WORKSPACE_CELL_CENTER: {
                final WorkspaceCellCenterRequest request = extra.getParcelable(
                        TestProtocol.TEST_INFO_REQUEST_FIELD);
                return getLauncherUIProperty(Bundle::putParcelable, launcher -> {
                    final Workspace<?> workspace = launcher.getWorkspace();
                    // TODO(b/216387249): allow caller selecting different pages.
                    CellLayout cellLayout = (CellLayout) workspace.getPageAt(
                            workspace.getCurrentPage());
                    final Rect cellRect = getDescendantRectRelativeToDragLayerForCell(launcher,
                            cellLayout, request.cellX, request.cellY, request.spanX, request.spanY);
                    return new Point(cellRect.centerX(), cellRect.centerY());
                });
            }

            case TestProtocol.REQUEST_WORKSPACE_COLUMNS_ROWS: {
                InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mContext);
                return getLauncherUIProperty(Bundle::putParcelable, launcher -> new Point(
                        idp.getDeviceProfile(mContext).getPanelCount() * idp.numColumns,
                        idp.numRows
                ));
            }

            case TestProtocol.REQUEST_WORKSPACE_CURRENT_PAGE_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher -> launcher.getWorkspace().getCurrentPage());
            }

            case TestProtocol.REQUEST_HOTSEAT_CELL_CENTER: {
                final HotseatCellCenterRequest request = extra.getParcelable(
                        TestProtocol.TEST_INFO_REQUEST_FIELD);
                return getLauncherUIProperty(Bundle::putParcelable, launcher -> {
                    final Hotseat hotseat = launcher.getHotseat();
                    final Rect cellRect = getDescendantRectRelativeToDragLayerForCell(launcher,
                            hotseat, request.cellInd, /* cellY= */ 0,
                            /* spanX= */ 1, /* spanY= */ 1);
                    // TODO(b/234322284): return the real center point.
                    return new Point(cellRect.left + (cellRect.right - cellRect.left) / 3,
                            cellRect.top + (cellRect.bottom - cellRect.top) / 3);
                });
            }

            case TestProtocol.REQUEST_HAS_TIS: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, false);
                return response;
            }

            case TestProtocol.REQUEST_ALL_APPS_TOP_PADDING: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getActiveRecyclerView().getClipBounds().top);
            }

            case TestProtocol.REQUEST_ALL_APPS_BOTTOM_PADDING: {
                return getLauncherUIProperty(Bundle::putInt,
                        l -> l.getAppsView().getBottom()
                                - l.getAppsView().getActiveRecyclerView().getBottom()
                                + l.getAppsView().getActiveRecyclerView().getPaddingBottom());
            }

            case TestProtocol.REQUEST_FLAG_ENABLE_GRID_ONLY_OVERVIEW: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        enableGridOnlyOverview());
                return response;
            }

            default:
                return null;
        }
    }

    private static Rect getDescendantRectRelativeToDragLayerForCell(Launcher launcher,
            CellLayout cellLayout, int cellX, int cellY, int spanX, int spanY) {
        final DragLayer dragLayer = launcher.getDragLayer();
        final Rect target = new Rect();

        cellLayout.cellToRect(cellX, cellY, spanX, spanY, target);
        int[] leftTop = {target.left, target.top};
        int[] rightBottom = {target.right, target.bottom};
        dragLayer.getDescendantCoordRelativeToSelf(cellLayout, leftTop);
        dragLayer.getDescendantCoordRelativeToSelf(cellLayout, rightBottom);

        target.set(leftTop[0], leftTop[1], rightBottom[0], rightBottom[1]);
        return target;
    }

    protected boolean isLauncherInitialized() {
        return Launcher.ACTIVITY_TRACKER.getCreatedActivity() == null
                || LauncherAppState.getInstance(mContext).getModel().isModelLoaded();
    }

    protected Activity getCurrentActivity() {
        return Launcher.ACTIVITY_TRACKER.getCreatedActivity();
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
        return getFromExecutorSync(MAIN_EXECUTOR, () -> {
            S target = targetSupplier.get();
            if (target == null) {
                return null;
            }
            T value = provider.apply(target);
            Bundle response = new Bundle();
            bundleSetter.set(response, TestProtocol.TEST_INFO_RESPONSE_FIELD, value);
            return response;
        });
    }

    /**
     * Executes the callback on the executor and waits for the result
     */
    protected static <T> T getFromExecutorSync(ExecutorService executor, Callable<T> callback) {
        try {
            return executor.submit(callback).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generic interface for setting a fiend in bundle
     *
     * @param <T> the type of value being set
     */
    public interface BundleSetter<T> {

        /**
         * Sets any generic property to the bundle
         */
        void set(Bundle b, String key, T value);
    }
}
