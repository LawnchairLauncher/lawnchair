/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;
import static android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.wm.shell.MockSurfaceControlHelper.createMockSurfaceControlTransaction;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.SystemProperties;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.view.AttachedSurfaceControl;
import android.view.Choreographer;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InsetsState;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.internal.R;
import com.android.window.flags.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.windowdecor.WindowDecoration.RelayoutParams;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

/**
 * Tests for {@link DesktopModeWindowDecoration}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DesktopModeWindowDecorationTests
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
public class DesktopModeWindowDecorationTests extends ShellTestCase {
    private static final String USE_WINDOW_SHADOWS_SYSPROP_KEY =
            "persist.wm.debug.desktop_use_window_shadows";
    private static final String FOCUSED_USE_WINDOW_SHADOWS_SYSPROP_KEY =
            "persist.wm.debug.desktop_use_window_shadows_focused_window";
    private static final String USE_ROUNDED_CORNERS_SYSPROP_KEY =
            "persist.wm.debug.desktop_use_rounded_corners";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    @Mock
    private DisplayController mMockDisplayController;
    @Mock
    private ShellTaskOrganizer mMockShellTaskOrganizer;
    @Mock
    private Handler mMockHandler;
    @Mock
    private Choreographer mMockChoreographer;
    @Mock
    private SyncTransactionQueue mMockSyncQueue;
    @Mock
    private RootTaskDisplayAreaOrganizer mMockRootTaskDisplayAreaOrganizer;
    @Mock
    private Supplier<SurfaceControl.Transaction> mMockTransactionSupplier;
    @Mock
    private SurfaceControl mMockSurfaceControl;
    @Mock
    private SurfaceControlViewHost mMockSurfaceControlViewHost;
    @Mock
    private AttachedSurfaceControl mMockRootSurfaceControl;
    @Mock
    private WindowDecoration.SurfaceControlViewHostFactory mMockSurfaceControlViewHostFactory;
    @Mock
    private TypedArray mMockRoundedCornersRadiusArray;

    @Mock
    private TestTouchEventListener mMockTouchEventListener;
    @Mock
    private DesktopModeWindowDecoration.ExclusionRegionListener mMockExclusionRegionListener;
    @Mock
    private PackageManager mMockPackageManager;

    private final InsetsState mInsetsState = new InsetsState();
    private SurfaceControl.Transaction mMockTransaction;
    private StaticMockitoSession mMockitoSession;
    private TestableContext mTestableContext;

    /** Set up run before test class. */
    @BeforeClass
    public static void setUpClass() {
        // Reset the sysprop settings before running the test.
        SystemProperties.set(USE_WINDOW_SHADOWS_SYSPROP_KEY, "");
        SystemProperties.set(FOCUSED_USE_WINDOW_SHADOWS_SYSPROP_KEY, "");
        SystemProperties.set(USE_ROUNDED_CORNERS_SYSPROP_KEY, "");
    }

    @Before
    public void setUp() {
        mMockitoSession = mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(DesktopModeStatus.class)
                .startMocking();
        when(DesktopModeStatus.useDesktopOverrideDensity()).thenReturn(false);
        doReturn(mMockSurfaceControlViewHost).when(mMockSurfaceControlViewHostFactory).create(
                any(), any(), any());
        when(mMockSurfaceControlViewHost.getRootSurfaceControl())
                .thenReturn(mMockRootSurfaceControl);
        mMockTransaction = createMockSurfaceControlTransaction();
        doReturn(mMockTransaction).when(mMockTransactionSupplier).get();
        mTestableContext = new TestableContext(mContext);
        mTestableContext.ensureTestableResources();
        mContext.setMockPackageManager(mMockPackageManager);
        when(mMockPackageManager.getApplicationLabel(any())).thenReturn("applicationLabel");
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController).getDisplay(Display.DEFAULT_DISPLAY);
        doReturn(mInsetsState).when(mMockDisplayController).getInsetsState(anyInt());
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testMenusClosedWhenTaskIsInvisible() {
        doReturn(mMockTransaction).when(mMockTransaction).hide(any());

        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(false /* visible */);
        final DesktopModeWindowDecoration spyWindowDecor =
                spy(createWindowDecoration(taskInfo));

        spyWindowDecor.relayout(taskInfo);

        // Menus should close if open before the task being invisible causes relayout to return.
        verify(spyWindowDecor).closeHandleMenu();
        verify(spyWindowDecor).closeMaximizeMenu();

    }

    @Test
    public void updateRelayoutParams_noSysPropFlagsSet_windowShadowsAreEnabled() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams, mContext, taskInfo, /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.mShadowRadiusId).isNotEqualTo(Resources.ID_NULL);
    }

    @Test
    public void updateRelayoutParams_noSysPropFlagsSet_roundedCornersAreEnabled() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        fillRoundedCornersResources(/* fillValue= */ 30);
        RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.mCornerRadius).isGreaterThan(0);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY)
    public void updateRelayoutParams_appHeader_usesTaskDensity() {
        final int systemDensity = mTestableContext.getOrCreateTestableResources().getResources()
                .getConfiguration().densityDpi;
        final int customTaskDensity = systemDensity + 300;
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        taskInfo.configuration.densityDpi = customTaskDensity;
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.mWindowDecorConfig.densityDpi).isEqualTo(customTaskDensity);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY)
    public void updateRelayoutParams_appHeader_usesSystemDensity() {
        when(DesktopModeStatus.useDesktopOverrideDensity()).thenReturn(true);
        final int systemDensity = mTestableContext.getOrCreateTestableResources().getResources()
                .getConfiguration().densityDpi;
        final int customTaskDensity = systemDensity + 300;
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        taskInfo.configuration.densityDpi = customTaskDensity;
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.mWindowDecorConfig.densityDpi).isEqualTo(systemDensity);
    }

    @Test
    public void updateRelayoutParams_freeformAndTransparentAppearance_allowsInputFallthrough() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        taskInfo.taskDescription.setTopOpaqueSystemBarsAppearance(
                APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.hasInputFeatureSpy()).isTrue();
    }

    @Test
    public void updateRelayoutParams_freeformButOpaqueAppearance_disallowsInputFallthrough() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        taskInfo.taskDescription.setTopOpaqueSystemBarsAppearance(0);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.hasInputFeatureSpy()).isFalse();
    }

    @Test
    public void updateRelayoutParams_fullscreen_disallowsInputFallthrough() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(relayoutParams.hasInputFeatureSpy()).isFalse();
    }

    @Test
    public void updateRelayoutParams_freeform_inputChannelNeeded() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(hasNoInputChannelFeature(relayoutParams)).isFalse();
    }

    @Test
    public void updateRelayoutParams_fullscreen_inputChannelNotNeeded() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(hasNoInputChannelFeature(relayoutParams)).isTrue();
    }

    @Test
    public void updateRelayoutParams_multiwindow_inputChannelNotNeeded() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        final RelayoutParams relayoutParams = new RelayoutParams();

        DesktopModeWindowDecoration.updateRelayoutParams(
                relayoutParams,
                mTestableContext,
                taskInfo,
                /* applyStartTransactionOnDraw= */ true,
                /* shouldSetTaskPositionAndCrop */ false);

        assertThat(hasNoInputChannelFeature(relayoutParams)).isTrue();
    }

    @Test
    public void relayout_fullscreenTask_appliesTransactionImmediately() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        spyWindowDecor.relayout(taskInfo);

        verify(mMockTransaction).apply();
        verify(mMockRootSurfaceControl, never()).applyTransactionOnDraw(any());
    }

    @Test
    public void relayout_freeformTask_appliesTransactionOnDraw() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        // Make non-resizable to avoid dealing with input-permissions (MONITOR_INPUT)
        taskInfo.isResizeable = false;

        spyWindowDecor.relayout(taskInfo);

        verify(mMockTransaction, never()).apply();
        verify(mMockRootSurfaceControl).applyTransactionOnDraw(mMockTransaction);
    }

    @Test
    public void relayout_fullscreenTask_doesNotCreateViewHostImmediately() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        spyWindowDecor.relayout(taskInfo);

        verify(mMockSurfaceControlViewHostFactory, never()).create(any(), any(), any());
    }

    @Test
    public void relayout_fullscreenTask_postsViewHostCreation() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        ArgumentCaptor<Runnable> runnableArgument = ArgumentCaptor.forClass(Runnable.class);
        spyWindowDecor.relayout(taskInfo);

        verify(mMockHandler).post(runnableArgument.capture());
        runnableArgument.getValue().run();
        verify(mMockSurfaceControlViewHostFactory).create(any(), any(), any());
    }

    @Test
    public void relayout_freeformTask_createsViewHostImmediately() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FREEFORM);
        // Make non-resizable to avoid dealing with input-permissions (MONITOR_INPUT)
        taskInfo.isResizeable = false;

        spyWindowDecor.relayout(taskInfo);

        verify(mMockSurfaceControlViewHostFactory).create(any(), any(), any());
        verify(mMockHandler, never()).post(any());
    }

    @Test
    public void relayout_removesExistingHandlerCallback() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        ArgumentCaptor<Runnable> runnableArgument = ArgumentCaptor.forClass(Runnable.class);
        spyWindowDecor.relayout(taskInfo);
        verify(mMockHandler).post(runnableArgument.capture());

        spyWindowDecor.relayout(taskInfo);

        verify(mMockHandler).removeCallbacks(runnableArgument.getValue());
    }

    @Test
    public void close_removesExistingHandlerCallback() {
        final ActivityManager.RunningTaskInfo taskInfo = createTaskInfo(/* visible= */ true);
        final DesktopModeWindowDecoration spyWindowDecor = spy(createWindowDecoration(taskInfo));
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        ArgumentCaptor<Runnable> runnableArgument = ArgumentCaptor.forClass(Runnable.class);
        spyWindowDecor.relayout(taskInfo);
        verify(mMockHandler).post(runnableArgument.capture());

        spyWindowDecor.close();

        verify(mMockHandler).removeCallbacks(runnableArgument.getValue());
    }

    private void fillRoundedCornersResources(int fillValue) {
        when(mMockRoundedCornersRadiusArray.getDimensionPixelSize(anyInt(), anyInt()))
                .thenReturn(fillValue);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.array.config_roundedCornerRadiusArray, mMockRoundedCornersRadiusArray);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.dimen.rounded_corner_radius, fillValue);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.array.config_roundedCornerTopRadiusArray, mMockRoundedCornersRadiusArray);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.dimen.rounded_corner_radius_top, fillValue);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.array.config_roundedCornerBottomRadiusArray, mMockRoundedCornersRadiusArray);
        mTestableContext.getOrCreateTestableResources().addOverride(
                R.dimen.rounded_corner_radius_bottom, fillValue);
    }


    private DesktopModeWindowDecoration createWindowDecoration(
            ActivityManager.RunningTaskInfo taskInfo) {
        DesktopModeWindowDecoration windowDecor = new DesktopModeWindowDecoration(mContext,
                mMockDisplayController, mMockShellTaskOrganizer, taskInfo, mMockSurfaceControl,
                mMockHandler, mMockChoreographer, mMockSyncQueue, mMockRootTaskDisplayAreaOrganizer,
                SurfaceControl.Builder::new, mMockTransactionSupplier,
                WindowContainerTransaction::new, SurfaceControl::new,
                mMockSurfaceControlViewHostFactory);
        windowDecor.setCaptionListeners(mMockTouchEventListener, mMockTouchEventListener,
                mMockTouchEventListener, mMockTouchEventListener);
        windowDecor.setExclusionRegionListener(mMockExclusionRegionListener);
        return windowDecor;
    }

    private ActivityManager.RunningTaskInfo createTaskInfo(boolean visible) {
        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder();
        ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setVisible(visible)
                .build();
        taskInfo.topActivityInfo = new ActivityInfo();
        taskInfo.topActivityInfo.applicationInfo = new ApplicationInfo();
        taskInfo.realActivity = new ComponentName("com.android.wm.shell.windowdecor",
                "DesktopModeWindowDecorationTests");
        taskInfo.baseActivity = new ComponentName("com.android.wm.shell.windowdecor",
                "DesktopModeWindowDecorationTests");
        return taskInfo;

    }

    private static boolean hasNoInputChannelFeature(RelayoutParams params) {
        return (params.mInputFeatures & WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL)
                != 0;
    }

    private static class TestTouchEventListener extends GestureDetector.SimpleOnGestureListener
            implements View.OnClickListener, View.OnTouchListener, View.OnLongClickListener,
            View.OnGenericMotionListener, DragDetector.MotionEventHandler {

        @Override
        public void onClick(View v) {}

        @Override
        public boolean onGenericMotion(View v, MotionEvent event) {
            return false;
        }

        @Override
        public boolean onLongClick(View v) {
            return false;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return false;
        }

        @Override
        public boolean handleMotionEvent(@Nullable View v, MotionEvent ev) {
            return false;
        }
    }
}
