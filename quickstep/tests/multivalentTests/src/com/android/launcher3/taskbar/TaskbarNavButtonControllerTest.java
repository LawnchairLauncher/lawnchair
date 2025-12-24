package com.android.launcher3.taskbar;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_BACK_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_HOME_BUTTON_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_LONGPRESS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.contextualsearch.ContextualSearchConfig;
import android.graphics.Rect;
import android.os.Handler;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.KeyEvent;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TouchInteractionService;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.systemui.contextualeducation.GestureType;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
public class TaskbarNavButtonControllerTest {

    private final static int DISPLAY_ID = 2;


    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    SystemUiProxy mockSystemUiProxy;

    @Mock
    TouchInteractionService mockService;
    @Mock
    Handler mockHandler;
    @Mock
    ContextualSearchInvoker mockContextualSearchInvoker;
    @Mock
    StatsLogManager mockStatsLogManager;
    @Mock
    StatsLogManager.StatsLogger mockStatsLogger;
    @Mock
    TaskbarControllers mockTaskbarControllers;
    @Mock
    TaskbarActivityContext mockTaskbarActivityContext;
    @Mock
    TaskbarSharedState mockSharedState;
    @Mock
    View mockView;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private int mHomePressCount;
    private int mOverviewToggleCount;
    private final TaskbarNavButtonCallbacks mCallbacks = new TaskbarNavButtonCallbacks() {
        @Override
        public void onNavigateHome(int displayId) {
            mHomePressCount++;
        }

        @Override
        public void onToggleOverview(int displayId) {
            mOverviewToggleCount++;
        }
    };

    private TaskbarNavButtonController mNavButtonController;

    @Before
    public void setup() {
        when(mockService.getDisplayId()).thenReturn(DISPLAY_ID);
        when(mockService.getApplicationContext())
                .thenReturn(InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getApplicationContext());
        when(mockStatsLogManager.logger()).thenReturn(mockStatsLogger);
        when(mockTaskbarControllers.getTaskbarActivityContext())
                .thenReturn(mockTaskbarActivityContext);
        when(mockTaskbarControllers.getSharedState()).thenReturn(mockSharedState);
        doReturn(mockStatsLogManager).when(mockTaskbarActivityContext).getStatsLogManager();
        when(mockTaskbarActivityContext.getDisplayId()).thenReturn(DISPLAY_ID);
        mNavButtonController = new TaskbarNavButtonController(
                DISPLAY_ID,
                mCallbacks,
                mockSystemUiProxy,
                mockHandler,
                mockContextualSearchInvoker);
        mNavButtonController.init(mockTaskbarControllers);
    }

    @Test
    public void testPressBack() {
        mNavButtonController.onButtonClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(1)).onBackEvent(null, DISPLAY_ID);
    }

    @Test
    public void testPressBack_updateContextualEduData() {
        mNavButtonController.onButtonClick(BUTTON_BACK, mockView);
        verify(mockSystemUiProxy, times(1))
                .updateContextualEduStats(/* isTrackpad= */ eq(false), eq(GestureType.BACK));
    }

    @Test
    public void testPressImeSwitcher() {
        mNavButtonController.onButtonClick(BUTTON_IME_SWITCH, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP);
        verify(mockStatsLogger, never()).log(LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_LONGPRESS);
        verify(mockSystemUiProxy, times(1)).onImeSwitcherPressed();
        verify(mockSystemUiProxy, never()).onImeSwitcherLongPress();
    }

    @Test
    public void testLongPressImeSwitcher() {
        mNavButtonController.onButtonLongClick(BUTTON_IME_SWITCH, mockView);
        verify(mockStatsLogger, never()).log(LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_TAP);
        verify(mockSystemUiProxy, never()).onImeSwitcherPressed();
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_IME_SWITCHER_BUTTON_LONGPRESS);
        verify(mockSystemUiProxy, times(1)).onImeSwitcherLongPress();
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
        mockSharedState.assistantLongPressEnabled = true;
        when(mockContextualSearchInvoker.tryStartAssistOverride(anyInt(),
                any(ContextualSearchConfig.class))).thenReturn(false);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockContextualSearchInvoker, times(1))
                .tryStartAssistOverride(anyInt(), any(ContextualSearchConfig.class));
        verify(mockSystemUiProxy, times(1)).startAssistant(any());
    }

    @Test
    public void testLongPressHome_enabled_withOverride() {
        mockSharedState.assistantLongPressEnabled = true;
        when(mockContextualSearchInvoker.tryStartAssistOverride(anyInt(),
                any(ContextualSearchConfig.class))).thenReturn(true);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockContextualSearchInvoker, times(1))
                .tryStartAssistOverride(anyInt(), any(ContextualSearchConfig.class));
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testLongPressHome_disabled_withoutOverride() {
        mockSharedState.assistantLongPressEnabled = false;
        when(mockContextualSearchInvoker.tryStartAssistOverride(anyInt(),
                any(ContextualSearchConfig.class))).thenReturn(false);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockContextualSearchInvoker, never())
                .tryStartAssistOverride(anyInt(), any(ContextualSearchConfig.class));
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testLongPressHome_disabled_withOverride() {
        mockSharedState.assistantLongPressEnabled = false;
        when(mockContextualSearchInvoker.tryStartAssistOverride(anyInt(),
                any(ContextualSearchConfig.class))).thenReturn(true);

        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockContextualSearchInvoker, never())
                .tryStartAssistOverride(anyInt(), any(ContextualSearchConfig.class));
        verify(mockSystemUiProxy, never()).startAssistant(any());
    }

    @Test
    public void testPressHome() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        assertThat(mHomePressCount).isEqualTo(1);
    }

    @Test
    public void testPressHome_updateContextualEduData() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockSystemUiProxy, times(1))
                .updateContextualEduStats(/* isTrackpad= */ eq(false), eq(GestureType.HOME));
    }

    @Test
    public void testPressRecents() {
        mNavButtonController.onButtonClick(BUTTON_RECENTS, mockView);
        assertThat(mOverviewToggleCount).isEqualTo(1);
    }

    @Test
    public void testPressRecents_updateContextualEduData() {
        mNavButtonController.onButtonClick(BUTTON_RECENTS, mockView);
        verify(mockSystemUiProxy, times(1))
                .updateContextualEduStats(/* isTrackpad= */ eq(false), eq(GestureType.OVERVIEW));
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
        mockSharedState.assistantLongPressEnabled = true;
        mNavButtonController.updateSysuiFlags(SYSUI_STATE_SCREEN_PINNING);
        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockSystemUiProxy, times(0)).startAssistant(any());
    }

    @Test
    public void testNoCallsToNullLogger() {
        doReturn(null).when(mockTaskbarActivityContext).getStatsLogManager();
        mNavButtonController.init(mockTaskbarControllers);
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogManager, times(0)).logger();
        verify(mockStatsLogger, times(0)).log(any());
    }

    @Test
    public void testNoCallsAfterNullingOut() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        mNavButtonController.onDestroy();
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
    }

    @Test
    public void testLogOnTap() {
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
    }

    @Test
    public void testLogOnLongpress() {
        mockSharedState.assistantLongPressEnabled = true;
        mNavButtonController.onButtonLongClick(BUTTON_HOME, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_HOME_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_HOME_BUTTON_TAP);
    }

    @Test
    public void testBackOverviewLogOnLongpress() {
        mNavButtonController.onButtonLongClick(BUTTON_RECENTS, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_OVERVIEW_BUTTON_TAP);

        mNavButtonController.onButtonLongClick(BUTTON_BACK, mockView);
        verify(mockStatsLogger, times(1)).log(LAUNCHER_TASKBAR_BACK_BUTTON_LONGPRESS);
        verify(mockStatsLogger, times(0)).log(LAUNCHER_TASKBAR_BACK_BUTTON_TAP);
    }

    @Test
    public void testPredictiveBackInvoked() {
        ArgumentCaptor<KeyEvent> keyEventCaptor = ArgumentCaptor.forClass(KeyEvent.class);
        ArgumentCaptor<Integer> displayIdCaptor = ArgumentCaptor.forClass(Integer.class);
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN, false);
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_UP, false);
        verify(mockSystemUiProxy, times(2)).onBackEvent(keyEventCaptor.capture(),
                displayIdCaptor.capture());
        verifyKeyEvent(keyEventCaptor.getAllValues().getFirst(), KeyEvent.ACTION_DOWN, false);
        verifyKeyEvent(keyEventCaptor.getAllValues().getLast(), KeyEvent.ACTION_UP, false);
        assertTrue(displayIdCaptor.getAllValues().stream().allMatch(v -> v == DISPLAY_ID));
    }

    @Test
    public void testPredictiveBackCancelled() {
        ArgumentCaptor<KeyEvent> keyEventCaptor = ArgumentCaptor.forClass(KeyEvent.class);
        ArgumentCaptor<Integer> displayIdCaptor = ArgumentCaptor.forClass(Integer.class);
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN, false);
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_UP, true);
        verify(mockSystemUiProxy, times(2)).onBackEvent(keyEventCaptor.capture(),
                displayIdCaptor.capture());
        verifyKeyEvent(keyEventCaptor.getAllValues().getFirst(), KeyEvent.ACTION_DOWN, false);
        verifyKeyEvent(keyEventCaptor.getAllValues().getLast(), KeyEvent.ACTION_UP, true);
        assertTrue(displayIdCaptor.getAllValues().stream().allMatch(v -> v == DISPLAY_ID));
    }

    @Test
    public void testButtonsDisabledWhileBackPressed() {
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_DOWN, false);
        mNavButtonController.onButtonClick(BUTTON_HOME, mockView);
        mNavButtonController.onButtonClick(BUTTON_RECENTS, mockView);
        mNavButtonController.onButtonLongClick(BUTTON_A11Y, mockView);
        mNavButtonController.onButtonClick(BUTTON_IME_SWITCH, mockView);
        mNavButtonController.sendBackKeyEvent(KeyEvent.ACTION_UP, false);
        assertThat(mHomePressCount).isEqualTo(0);
        verify(mockSystemUiProxy, never()).notifyAccessibilityButtonLongClicked();
        assertThat(mOverviewToggleCount).isEqualTo(0);
        verify(mockSystemUiProxy, never()).onImeSwitcherPressed();
    }

    @Test
    public void testOnRecentsButtonLayoutChanged() {
        Rect rect = new Rect(10, 20, 30, 40);
        mNavButtonController.onRecentsButtonLayoutChanged(rect);
        verify(mockSystemUiProxy).notifyRecentsButtonPositionChanged(eq(rect));
    }

    private void verifyKeyEvent(KeyEvent keyEvent, int action, boolean isCancelled) {
        assertEquals(isCancelled, keyEvent.isCanceled());
        assertEquals(action, keyEvent.getAction());
    }
}
