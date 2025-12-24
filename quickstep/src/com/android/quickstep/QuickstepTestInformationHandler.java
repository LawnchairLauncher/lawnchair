package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.taskbar.TaskbarThresholdUtils.getFromNavThreshold;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.WindowInsets;

import androidx.annotation.Nullable;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.wm.shell.shared.bubbles.DeviceConfig;
import com.android.wm.shell.shared.desktopmode.DesktopState;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.inject.Inject;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    protected final Context mContext;
    private final RecentsModel mRecentsModel;
    private final SystemUiProxy mSystemUiProxy;
    private final  OverviewComponentObserver mOverviewComponentObserver;
    private final DesktopVisibilityController mDesktopVisibilityController;


    @Inject
    public QuickstepTestInformationHandler(@ApplicationContext Context context,
            RecentsModel recentsModel,
            SystemUiProxy systemUiProxy,
            OverviewComponentObserver overviewComponentObserver,
            DesktopVisibilityController desktopVisibilityController) {
        mContext = context;
        mRecentsModel = recentsModel;
        mSystemUiProxy = systemUiProxy;
        mOverviewComponentObserver = overviewComponentObserver;
        mDesktopVisibilityController = desktopVisibilityController;
    }

    @Override
    public Bundle call(String method, String arg, @Nullable Bundle extras) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_RECENT_TASKS_LIST: {
                ArrayList<String> taskBaseIntentComponents = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);
                mRecentsModel.getTasks((taskGroups) -> {
                    for (GroupTask group : taskGroups) {
                        for (Task t : group.getTasks()) {
                            taskBaseIntentComponents.add(
                                    t.key.baseIntent.getComponent().flattenToString());
                        }
                    }
                    latch.countDown();
                });
                try {
                    latch.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                response.putStringArrayList(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        taskBaseIntentComponents);
                return response;
            }

            case TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight =
                        LayoutUtils.getDefaultSwipeHeight(mContext, mDeviceProfile);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight = mDeviceProfile.getDeviceProperties().getHeightPx() / 2f;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_TASK_SIZE: {
                return getUIProperty(Bundle::putParcelable,
                        recentsViewContainer ->
                                recentsViewContainer.<RecentsView<?, ?>>getOverviewPanel()
                                        .getLastComputedTaskSize(),
                        this::getRecentsViewContainer);
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_GRID_TASK_SIZE: {
                return getUIProperty(Bundle::putParcelable,
                        recentsViewContainer ->
                                recentsViewContainer.<RecentsView<?, ?>>getOverviewPanel()
                                        .getLastComputedGridTaskSize(),
                        this::getRecentsViewContainer);
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_PAGE_SPACING: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDeviceProfile.getOverviewProfile().getPageSpacing());
                return response;
            }

            case TestProtocol.REQUEST_GET_BUBBLE_BAR_DROP_TARGET_SIZE: {
                int dimenResId = DeviceConfig.isSmallTablet(mContext)
                        ? com.android.wm.shell.shared.R.dimen.drag_zone_bubble_fold : com.android.wm.shell.shared.R.dimen.drag_zone_bubble_tablet;
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mContext.getResources().getDimensionPixelSize(dimenResId));
                return response;
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_CURRENT_PAGE_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher -> launcher.<RecentsView>getOverviewPanel().getCurrentPage());
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_FIRST_TASKVIEW_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher ->
                                launcher.<RecentsView<?, ?>>getOverviewPanel()
                                        .getFirstTaskViewIndex());
            }

            case TestProtocol.REQUEST_HAS_TIS: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD, true);
                return response;
            }

            case TestProtocol.REQUEST_UNSTASH_TASKBAR_IF_STASHED:
                runOnTISBinder(tisBinder -> {
                    // Allow null-pointer to catch illegal states.
                    tisBinder.getTaskbarManager().getCurrentActivityContext()
                            .unstashTaskbarIfStashed();
                });
                return response;

            case TestProtocol.REQUEST_COLLAPSE_BUBBLE_BAR:
                runOnTISBinder(tisBinder -> {
                    // Allow null-pointer to catch illegal states.
                    tisBinder.getTaskbarManager().getCurrentActivityContext().removeAllBubbles();
                });
                return response;

            case TestProtocol.REQUEST_TASKBAR_FROM_NAV_THRESHOLD: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        getFromNavThreshold(resources, mDeviceProfile));
                return response;
            }

            case TestProtocol.REQUEST_STASHED_TASKBAR_SCALE: {
                runOnTISBinder(tisBinder -> {
                    response.putFloat(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                            tisBinder.getTaskbarManager()
                                    .getCurrentActivityContext()
                                    .getStashedTaskbarScale());
                });
                return response;
            }

            case TestProtocol.REQUEST_TASKBAR_ALL_APPS_TOP_PADDING: {
                return getTISBinderUIProperty(Bundle::putInt, tisBinder ->
                        tisBinder.getTaskbarManager()
                                .getCurrentActivityContext()
                                .getTaskbarAllAppsTopPadding());
            }

            case TestProtocol.REQUEST_TASKBAR_APPS_LIST_SCROLL_Y: {
                return getTISBinderUIProperty(Bundle::putInt, tisBinder ->
                        tisBinder.getTaskbarManager()
                                .getCurrentActivityContext()
                                .getTaskbarAllAppsScroll());
            }

            case TestProtocol.REQUEST_LIMIT_MAX_TASKBAR_ICON_NUMBER: {
                runOnTISBinder(tisBinder ->
                        tisBinder.getTaskbarManager()
                                .getCurrentActivityContext()
                                .limitMaxTaskbarIconsNum(Integer.parseInt(arg)));
                return response;
            }

            case TestProtocol.REQUEST_ENABLE_BLOCK_TIMEOUT:
                runOnTISBinder(tisBinder -> {
                    enableBlockingTimeout(tisBinder, true);
                });
                return response;

            case TestProtocol.REQUEST_DISABLE_BLOCK_TIMEOUT:
                runOnTISBinder(tisBinder -> {
                    enableBlockingTimeout(tisBinder, false);
                });
                return response;

            case TestProtocol.REQUEST_ENABLE_TRANSIENT_TASKBAR:
                enableTransientTaskbar(true);
                return response;

            case TestProtocol.REQUEST_DISABLE_TRANSIENT_TASKBAR:
                enableTransientTaskbar(false);
                return response;

            case TestProtocol.REQUEST_SHELL_DRAG_READY:
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mSystemUiProxy.isDragAndDropReady());
                return response;

            case TestProtocol.REQUEST_REFRESH_OVERVIEW_TARGET:
                runOnTISBinder(TouchInteractionService.TISBinder::refreshOverviewTargetForTest);
                return response;

            case TestProtocol.REQUEST_RECREATE_TASKBAR:
                // Allow null-pointer to catch illegal states.
                runOnTISBinder(tisBinder -> tisBinder.getTaskbarManager().recreateTaskbars());
                return response;
            case TestProtocol.REQUEST_TASKBAR_IME_DOCKED:
                return getTISBinderUIProperty(Bundle::putBoolean, tisBinder ->
                        tisBinder.getTaskbarManager()
                                .getCurrentActivityContext().isImeDocked());
            case TestProtocol.REQUEST_UNSTASH_BUBBLE_BAR_IF_STASHED:
                runOnTISBinder(tisBinder -> {
                    // Allow null-pointer to catch illegal states.
                    tisBinder.getTaskbarManager().getCurrentActivityContext()
                            .unstashBubbleBarIfStashed();
                });
                return response;
            case TestProtocol.REQUEST_REMOVE_ALL_BUBBLES:
                runOnTISBinder(tisBinder -> {
                    // Allow null-pointer to catch illegal states.
                    Context context = tisBinder.getTaskbarManager().getCurrentActivityContext();
                    SystemUiProxy.INSTANCE.get(context).removeAllBubbles();
                });
                return response;
            case TestProtocol.REQUEST_INJECT_FAKE_TRACKPAD:
                runOnTISBinder(tisBinder -> tisBinder.injectFakeTrackpadForTesting());
                return response;
            case TestProtocol.REQUEST_EJECT_FAKE_TRACKPAD:
                runOnTISBinder(tisBinder -> tisBinder.ejectFakeTrackpadForTesting());
                return response;

            case TestProtocol.REQUEST_DISMISS_MAGNETIC_DETACH_THRESHOLD: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(R.dimen.task_dismiss_detach_threshold));
                return response;
            }

            case TestProtocol.REQUEST_TASKBAR_ACTION_CORNER_PADDING: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(
                                R.dimen.transient_taskbar_action_corner_padding));
                return response;
            }
            case TestProtocol.REQUEST_TASKBAR_UNSTASHED_INPUT_AREA: {
                final Resources resources = mContext.getResources();
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        resources.getDimensionPixelSize(
                                R.dimen.taskbar_unstash_input_area));
                return response;
            }
            case TestProtocol.REQUEST_IS_TRANSIENT_TASKBAR:
                return getTISBinderUIProperty(Bundle::putBoolean, tisBinder ->
                        tisBinder.getTaskbarManager()
                                .getCurrentActivityContext()
                                .getTaskbarFeatureEvaluator().isTransient());
            case TestProtocol.REQUEST_FLAG_ENABLE_MULTIPLE_DESKTOPS: {
                response.putBoolean(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        DesktopState.fromContext(mContext)
                                .isMultipleDesktopFrontendEnabledOnDisplay(
                                        Integer.parseInt(arg)));
                return response;
            }
            case TestProtocol.REQUEST_GET_ACTIVE_DESK_ID: {
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        mDesktopVisibilityController.getActiveDeskId(Integer.parseInt(arg)));
                return response;
            }
            case TestProtocol.REQUEST_GET_DESK_ID: {
                final Rect taskBounds = extras.getParcelable(TestProtocol.TEST_INFO_RESPONSE_FIELD);
                return getUIProperty(Bundle::putInt,
                        recentsViewContainer -> {
                            RecentsView recentsView = recentsViewContainer.getOverviewPanel();
                            for (TaskView taskView : recentsView.getTaskViews()) {
                                Rect bounds = new Rect();
                                taskView.getGlobalVisibleRect(bounds);
                                if (bounds.equals(taskBounds)
                                        && taskView instanceof DesktopTaskView) {
                                    return ((DesktopTaskView) taskView).getDeskId();
                                }
                            }
                            return -1;
                        },
                        this::getRecentsViewContainer);
            }
        }

        return super.call(method, arg, extras);
    }

    @Override
    protected WindowInsets getWindowInsets() {
        RecentsViewContainer container = getRecentsViewContainer();
        WindowInsets insets = container == null || container.getRootView() == null
                ? null : container.getRootView().getRootWindowInsets();
        return insets == null ? super.getWindowInsets() : insets;
    }

    @Nullable
    private RecentsViewContainer getRecentsViewContainer() {
        // TODO (b/400647896): support per-display container in e2e tests
        BaseContainerInterface<?, ?> containerInterface =
                mOverviewComponentObserver.getContainerInterface(DEFAULT_DISPLAY);
        if (containerInterface != null) {
            return containerInterface.getCreatedContainer();
        } else {
            return null;
        }
    }

    @Override
    protected boolean isLauncherInitialized() {
        return super.isLauncherInitialized() && mSystemUiProxy.isActive();
    }

    private void enableBlockingTimeout(
            TouchInteractionService.TISBinder tisBinder, boolean enable) {
        TaskbarActivityContext context = tisBinder.getTaskbarManager().getCurrentActivityContext();
        if (context == null) {
            return;
        }
        context.enableBlockingTimeoutDuringTests(enable);
    }

    private void enableTransientTaskbar(boolean enable) {
        LauncherPrefs.get(mContext).put(LauncherPrefs.TASKBAR_PINNING, !enable);
    }

    /**
     * Runs the given command on the UI thread, after ensuring we are connected to
     * TouchInteractionService.
     */
    protected void runOnTISBinder(Consumer<TouchInteractionService.TISBinder> connectionCallback) {
        try {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            TISBindHelper helper = MAIN_EXECUTOR.submit(() ->
                    new TISBindHelper(mContext, tisBinder -> {
                        connectionCallback.accept(tisBinder);
                        countDownLatch.countDown();
                    })).get();
            countDownLatch.await();
            MAIN_EXECUTOR.execute(helper::onDestroy);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> Bundle getTISBinderUIProperty(
            BundleSetter<T> bundleSetter, Function<TouchInteractionService.TISBinder, T> provider) {
        Bundle response = new Bundle();

        runOnTISBinder(tisBinder -> bundleSetter.set(
                response,
                TestProtocol.TEST_INFO_RESPONSE_FIELD,
                provider.apply(tisBinder)));

        return response;
    }
}
