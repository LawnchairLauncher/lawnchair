package com.android.quickstep;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.testing.TestInformationHandler;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class QuickstepTestInformationHandler extends TestInformationHandler {

    private final Context mContext;
    public QuickstepTestInformationHandler(Context context) {
        mContext = context;
    }

    @Override
    public Bundle call(String method) {
        final Bundle response = new Bundle();
        switch (method) {
            case TestProtocol.REQUEST_HOME_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight =
                        LayoutUtils.getDefaultSwipeHeight(mContext, mDeviceProfile);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_BACKGROUND_TO_OVERVIEW_SWIPE_HEIGHT: {
                final float swipeHeight =
                        LayoutUtils.getShelfTrackingDistance(mContext, mDeviceProfile);
                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, (int) swipeHeight);
                return response;
            }

            case TestProtocol.REQUEST_HOTSEAT_TOP: {
                if (mLauncher == null) return null;

                response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD,
                        PortraitStatesTouchController.getHotseatTop(mLauncher));
                return response;
            }

            case TestProtocol.REQUEST_OVERVIEW_LEFT_GESTURE_MARGIN: {
                try {
                    final int leftMargin = MAIN_EXECUTOR.submit(() ->
                            getRecentsView().getLeftGestureMargin()).get();
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, leftMargin);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return response;
            }

            case TestProtocol.REQUEST_OVERVIEW_RIGHT_GESTURE_MARGIN: {
                try {
                    final int rightMargin = MAIN_EXECUTOR.submit(() ->
                            getRecentsView().getRightGestureMargin()).get();
                    response.putInt(TestProtocol.TEST_INFO_RESPONSE_FIELD, rightMargin);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return response;
            }

            case TestProtocol.REQUEST_RECENT_TASKS_LIST: {
                ArrayList<String> taskBaseIntentComponents = new ArrayList<>();
                CountDownLatch latch = new CountDownLatch(1);
                RecentsModel.INSTANCE.get(mContext).getTasks((tasks) -> {
                    for (Task t : tasks) {
                        taskBaseIntentComponents.add(
                                t.key.baseIntent.getComponent().flattenToString());
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
        }

        return super.call(method);
    }

    private RecentsView getRecentsView() {
        OverviewComponentObserver observer = new OverviewComponentObserver(mContext,
                new RecentsAnimationDeviceState(mContext));
        try {
            return observer.getActivityInterface().getCreatedActivity().getOverviewPanel();
        } finally {
            observer.onDestroy();
        }
    }

    @Override
    protected boolean isLauncherInitialized() {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.LAUNCHER_DIDNT_INITIALIZE,
                    "isLauncherInitialized.TouchInteractionService.isInitialized=" +
                            TouchInteractionService.isInitialized());
        }
        return super.isLauncherInitialized() && TouchInteractionService.isInitialized();
    }
}
