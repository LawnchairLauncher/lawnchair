package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.taskbar.TaskbarThresholdUtils.getFromNavThreshold;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.WindowInsets;

import androidx.annotation.Nullable;

import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    protected final Context mContext;

    public QuickstepTestInformationHandler(Context context) {
        mContext = context;
    }

    @Override
    public Bundle call(String method, String arg, @Nullable Bundle extras) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_RECENT_TASKS_LIST: {
                ArrayList<String> taskBaseIntentComponents = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);
                RecentsModel.INSTANCE.get(mContext).getTasks((taskGroups) -> {
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
                final float swipeHeight = mDeviceProfile.heightPx / 2f;
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
                        mDeviceProfile.overviewPageSpacing);
                return response;
            }

            case TestProtocol.REQUEST_GET_OVERVIEW_CURRENT_PAGE_INDEX: {
                return getLauncherUIProperty(Bundle::putInt,
                        launcher -> launcher.<RecentsView>getOverviewPanel().getCurrentPage());
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
                        SystemUiProxy.INSTANCE.get(mContext).isDragAndDropReady());
                return response;

            case TestProtocol.REQUEST_REFRESH_OVERVIEW_TARGET:
                runOnTISBinder(TouchInteractionService.TISBinder::refreshOverviewTarget);
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
            case TestProtocol.REQUEST_INJECT_FAKE_TRACKPAD:
                runOnTISBinder(tisBinder -> tisBinder.injectFakeTrackpadForTesting());
                return response;
            case TestProtocol.REQUEST_EJECT_FAKE_TRACKPAD:
                runOnTISBinder(tisBinder -> tisBinder.ejectFakeTrackpadForTesting());
                return response;
        }

        return super.call(method, arg, extras);
    }

    @Override
    protected WindowInsets getWindowInsets() {
        RecentsViewContainer container = getRecentsViewContainer();
        WindowInsets insets = container == null
                ? null : container.getRootView().getRootWindowInsets();
        return insets == null ? super.getWindowInsets() : insets;
    }

    private RecentsViewContainer getRecentsViewContainer() {
        // TODO (b/400647896): support per-display container in e2e tests
        return OverviewComponentObserver.INSTANCE.get(mContext)
                .getContainerInterface(DEFAULT_DISPLAY).getCreatedContainer();
    }

    @Override
    protected boolean isLauncherInitialized() {
        return super.isLauncherInitialized() && SystemUiProxy.INSTANCE.get(mContext).isActive();
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
        DisplayController.INSTANCE.get(mContext).enableTransientTaskbarForTests(enable);
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
