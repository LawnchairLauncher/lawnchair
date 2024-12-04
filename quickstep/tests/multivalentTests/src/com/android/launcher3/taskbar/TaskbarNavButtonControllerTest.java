package com.android.launcher3.taskbar;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_A11Y;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.SCREEN_PIN_LONG_PRESS_THRESHOLD;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_SCREEN_PINNING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.AssistUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class TaskbarNavButtonControllerTest {

    private final static int DISPLAY_ID = 2;

    @Mock
    SystemUiProxy mockSystemUiProxy;
    @Mock
    TouchInteractionService mockService;
    @Mock
    Handler mockHandler;
    @Mock
    AssistUtils mockAssistUtils;
    @Mock
    StatsLogManager mockStatsLogManager;
    @Mock
    StatsLogManager.StatsLogger mockStatsLogger;
    @Mock
    TaskbarControllers mockTaskbarControllers;
    @Mock
    TaskbarActivityContext mockTaskbarActivityContext;
    @Mock
    View mockView;

    private int mHomePressCount;
    private int mOverviewToggleCount;
    private final TaskbarNavButtonCallbacks mCallbacks = new TaskbarNavButtonCallbacks() {
        @Override
        public void onNavigateHome() {
            mHomePressCount++;
        }

        @Override
        public void onToggleOverview() {
            mOverviewToggleCount++;
        }
    };

    private TaskbarNavButtonController mNavButtonController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mockService.getDisplayId()).thenReturn(DISPLAY_ID);
        when(mockService.getApplicationContext())
                .thenReturn(InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getApplicationContext());
        when(mockStatsLogManager.logger()).thenReturn(mockStatsLogger);
        when(mockTaskbarControllers.getTaskbarActivityContext())
                .thenReturn(mockTaskbarActivityContext);
        doReturn(mockStatsLogManager).when(mockTaskbarActivityContext).getStatsLogManager();
        mNavButtonController = new TaskbarNavButtonController(
                mockService,
                mCallbacks,
                mockSystemUiProxy,
                mockHandler,
                mockAssistUtils);
    }

    @Test
    public void testPressBack() {
        mNavButtonController.onButtonClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(1)).onBackPressed();
    }

    @Test
    public void testPressImeSwitcher() {
        mNavButtonController.onButtonClick(BUTTON_IME_SWITCH, mockView);
        verify(mockSystemUiProxy, times(1)).onImeSwitcherPressed();
    }

    @Test
    public void testPressA11yShortClick() {
        mNavButtonController.onButtonClick(BUTTON_A11Y, mockView);
        verify(mockSystemUiProxy, times(1))
                .notifyAccessibilityButtonClicked(DISPLAY_ID);
    }

    @Test
    public void testPressA11yLongClick() {
        mNavButtonController.onButtonLongClick(BUTTON_A11Y, mockView);
        verify(mockSystemUiProxy, times(1)).notifyAccessibilityButtonLongClicked();
    }

    @Test
    public void testLongPressHome_enabled_withoutOverride() {
        mNavButtonController.setAssistantLongPressEnabled(true /*assistantLongPressEnabled*/);
        when(mockAssistUtils.tryStartAssistOverride(anyInt())).thenReturn(false);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockAssistUtils, times(1)).tryStartAssistOverride(anyInt());
        verify(mockSystemUiProxy, times(1)).startAssistant(any());
    }

    @Test
    public void testLongPressHome_enabled_withOverride() {
        mNavButtonController.setAssistantLongPressEnabled(true /*assistantLongPressEnabled*/);
        when(mockAssistUtils.tryStartAssistOverride(anyInt())).thenReturn(true);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockAssistUtils, times(1)).tryStartAssistOverride(anyInt());
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testLongPressHome_disabled_withoutOverride() {
        mNavButtonController.setAssistantLongPressEnabled(false /*assistantLongPressEnabled*/);
        when(mockAssistUtils.tryStartAssistOverride(anyInt())).thenReturn(false);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockAssistUtils, never()).tryStartAssistOverride(anyInt());
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testLongPressHome_disabled_withOverride() {
        mNavButtonController.setAssistantLongPressEnabled(false /*assistantLongPressEnabled*/);
        when(mockAssistUtils.tryStartAssistOverride(anyInt())).thenReturn(true);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockAssistUtils, never()).tryStartAssistOverride(anyInt());
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testPressHome() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        assertThat(mHomePressCount).isEqualTo(1);
    }

    @Test
    public void testPressRecents() {
        mNavButtonController.onButtonClick(BUTTON_RECENTS, mockView);
        assertThat(mOverviewToggleCount).isEqualTo(1);
    }

    @Test
    public void testPressRecentsWithScreenPinned_noNavigationToOverview() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonClick(BUTTON_RECENTS, mockView);
        assertThat(mOverviewToggleCount).isEqualTo(0);
    }

    @Test
    public void testLongPressBackRecentsNotPinned() {
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(1)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsTooLongPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        try {
            Thread.sleep(SCREEN_PIN_LONG_PRESS_THRESHOLD + 5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();
    }

    @Test
    public void testLongPressBackRecentsMultipleAttemptPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        try {
            Thread.sleep(SCREEN_PIN_LONG_PRESS_THRESHOLD + 5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(0)).stopScreenPinning();

        // Try again w/in threshold
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(1)).stopScreenPinning();
    }

    @Test
    public void testLongPressHomeScreenPinned() {
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockSystemUiProxy, times(0)).startAssistant(any());
    }

    @Test
    public void testNoCallsToNullLogger() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogManager, times(0)).logger();
        verify(mockStatsLogger, times(0)).log(any());
    }

    @Test
    public void testNoCallsAfterNullingOut() {
        mNavButtonController.init(mockTaskbarControllers);
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        mNavButtonController.onDestroy();
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
    }

    @Test
    public void testLogOnTap() {
        mNavButtonController.init(mockTaskbarControllers);
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
    }

    @Test
    public void testLogOnLongpress() {
        mNavButtonController.init(mockTaskbarControllers);
        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
    }

    @Test
    public void testBackOverviewLogOnLongpress() {
        mNavButtonController.init(mockTaskbarControllers);
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP);

        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_BACK_BUTTON_TAP);
    }
}
