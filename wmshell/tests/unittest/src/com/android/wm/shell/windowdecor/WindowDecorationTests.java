/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.view.InsetsSource.FLAG_FORCE_CONSUMING;
import static android.view.InsetsSource.FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.statusBars;

import static com.android.wm.shell.MockSurfaceControlHelper.createMockSurfaceControlBuilder;
import static com.android.wm.shell.MockSurfaceControlHelper.createMockSurfaceControlTransaction;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.LocaleList;
import android.os.Looper;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.util.DisplayMetrics;
import android.view.AttachedSurfaceControl;
import android.view.Display;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager.LayoutParams;
import android.window.DesktopExperienceFlags;
import android.window.SurfaceSyncGroup;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;
import com.android.wm.shell.MockToken;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestHandler;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.desktopmode.DesktopModeEventLogger;
import com.android.wm.shell.tests.R;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewContainer;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Tests for {@link WindowDecoration}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:WindowDecorationTests
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@UsesFlags(com.android.window.flags.Flags.class)
public class WindowDecorationTests extends ShellTestCase {
    private static final Rect TASK_BOUNDS = new Rect(100, 300, 400, 400);
    private static final Point TASK_POSITION_IN_PARENT = new Point(40, 60);
    private static final int CORNER_RADIUS = 20;
    private static final int SHADOW_RADIUS = 10;
    private static final int STATUS_BAR_INSET_SOURCE_ID = 0;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
                Flags.FLAG_ENABLE_FREEFORM_BOX_SHADOWS);
    }

    private final WindowDecoration.RelayoutResult<TestView> mRelayoutResult =
            new WindowDecoration.RelayoutResult<>();

    @Mock
    private DisplayController mMockDisplayController;
    @Mock
    private ShellTaskOrganizer mMockShellTaskOrganizer;
    @Mock
    private WindowDecoration.SurfaceControlViewHostFactory mMockSurfaceControlViewHostFactory;
    @Mock
    private WindowDecorViewHostSupplier<WindowDecorViewHost> mMockWindowDecorViewHostSupplier;
    @Mock
    private WindowDecorViewHost mMockWindowDecorViewHost;
    @Mock
    private SurfaceControlViewHost mMockSurfaceControlViewHost;
    @Mock
    private AttachedSurfaceControl mMockRootSurfaceControl;
    @Mock
    private TestView mMockView;
    @Mock
    private WindowContainerTransaction mMockWindowContainerTransaction;
    @Mock
    private SurfaceSyncGroup mMockSurfaceSyncGroup;
    @Mock
    private SurfaceControl mMockTaskSurface;
    @Mock
    private DesktopModeEventLogger mDesktopModeEventLogger;
    @Mock
    private Transitions mTransitions;

    private final List<SurfaceControl.Transaction> mMockSurfaceControlTransactions =
            new ArrayList<>();
    private final List<SurfaceControl.Builder> mMockSurfaceControlBuilders = new ArrayList<>();
    private final InsetsState mInsetsState = new InsetsState();
    private SurfaceControl.Transaction mMockSurfaceControlStartT;
    private SurfaceControl.Transaction mMockSurfaceControlFinishT;
    private SurfaceControl.Transaction mMockSurfaceControlAddWindowT;
    private WindowDecoration.RelayoutParams mRelayoutParams = new WindowDecoration.RelayoutParams();
    private int mCaptionMenuWidthId;
    private final TestHandler mTestHandler = new TestHandler(Looper.getMainLooper());

    public WindowDecorationTests(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        mMockSurfaceControlStartT = createMockSurfaceControlTransaction();
        mMockSurfaceControlFinishT = createMockSurfaceControlTransaction();
        mMockSurfaceControlAddWindowT = createMockSurfaceControlTransaction();

        mRelayoutParams.mLayoutResId = 0;
        mRelayoutParams.mCaptionHeightCalculator =
                (ctx, display) -> WindowDecoration.loadDimensionPixelSize(ctx.getResources(),
                        R.dimen.test_freeform_decor_caption_height);
        mCaptionMenuWidthId = R.dimen.test_freeform_decor_caption_menu_width;
        if (Flags.enableFreeformBoxShadows()) {
            mRelayoutParams.mBoxShadowSettingsIds = new int[]{R.style.BoxShadowParamsKeyFocused};
            mRelayoutParams.mBorderSettingsId = R.style.BorderSettingsFocusedDark;
        } else if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()) {
            mRelayoutParams.mShadowRadiusId = R.dimen.test_freeform_shadow_radius;
            mRelayoutParams.mCornerRadiusId = R.dimen.test_freeform_corner_radius;
        } else {
            mRelayoutParams.mShadowRadius = SHADOW_RADIUS;
            mRelayoutParams.mCornerRadius = CORNER_RADIUS;
        }

        when(mMockDisplayController.getDisplay(Display.DEFAULT_DISPLAY))
                .thenReturn(mock(Display.class));
        doReturn(mMockSurfaceControlViewHost).when(mMockSurfaceControlViewHostFactory)
                .create(any(), any(), any());
        when(mMockSurfaceControlViewHost.getRootSurfaceControl())
                .thenReturn(mMockRootSurfaceControl);
        when(mMockView.findViewById(anyInt())).thenReturn(mMockView);

        // Add status bar inset so that WindowDecoration does not think task is in immersive mode
        mInsetsState.getOrCreateSource(STATUS_BAR_INSET_SOURCE_ID, statusBars()).setVisible(true);
        doReturn(mInsetsState).when(mMockDisplayController).getInsetsState(anyInt());

        when(mMockWindowDecorViewHostSupplier.acquire(any(), any()))
                .thenReturn(mMockWindowDecorViewHost);
        when(mMockWindowDecorViewHost.getSurfaceControl()).thenReturn(mock(SurfaceControl.class));
    }

    @Test
    public void testLayoutResultCalculation_invisibleTask() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);
        final SurfaceControl taskBackgroundSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder taskBackgroundSurfaceBuilder =
                createMockSurfaceControlBuilder(taskBackgroundSurface);
        mMockSurfaceControlBuilders.add(taskBackgroundSurfaceBuilder);
        final SurfaceControl captionContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder captionContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(captionContainerSurface);
        mMockSurfaceControlBuilders.add(captionContainerSurfaceBuilder);

        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(false)
                .build();
        // Density is 2. Shadow radius is 10px. Caption height is 64px.
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;

        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        windowDecor.relayout(taskInfo, false /* hasGlobalFocus */);

        verify(decorContainerSurfaceBuilder, never()).build();
        verify(taskBackgroundSurfaceBuilder, never()).build();
        verify(captionContainerSurfaceBuilder, never()).build();
        verify(mMockSurfaceControlViewHostFactory, never()).create(any(), any(), any());

        assertNull(mRelayoutResult.mRootView);
    }

    @Test
    public void testLayoutResultCalculation_visibleFocusedTask_inSyncWithTransition() {
        testLayoutResultCalculation_visibleFocusedTask(/* inSyncWithTransition= */ true);
    }

    @Test
    public void testLayoutResultCalculation_visibleFocusedTask_NotInSyncWithTransition() {
        testLayoutResultCalculation_visibleFocusedTask(/* inSyncWithTransition= */ false);
    }

    void testLayoutResultCalculation_visibleFocusedTask(boolean inSyncWithTransition) {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build();
        // Density is 2. Shadow radius is 10px. Caption height is 64px.
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mInSyncWithTransition = inSyncWithTransition;

        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(decorContainerSurfaceBuilder).setParent(mMockTaskSurface);
        verify(decorContainerSurfaceBuilder).setContainerLayer();
        verify(mMockSurfaceControlStartT).setTrustedOverlay(decorContainerSurface, true);
        verify(mMockSurfaceControlStartT).setWindowCrop(decorContainerSurface, 300, 100);

        final SurfaceControl captionContainerSurface = mMockWindowDecorViewHost.getSurfaceControl();
        verify(mMockSurfaceControlStartT).reparent(captionContainerSurface, decorContainerSurface);
        verify(mMockSurfaceControlStartT).setWindowCrop(captionContainerSurface, 300, 64);
        verify(mMockSurfaceControlStartT).show(captionContainerSurface);

        verify(mMockWindowDecorViewHost).updateView(
                same(mMockView),
                argThat(lp -> lp.height == 64
                        && lp.width == 300
                        && (lp.flags & LayoutParams.FLAG_NOT_FOCUSABLE) != 0),
                eq(taskInfo.configuration),
                any(),
                eq(null) /* onDrawTransaction */);
        verify(mMockView).setTaskFocusState(true);
        verifyAddedInsets(1, taskInfo.token, 0 /* index */, WindowInsets.Type.captionBar(),
                new Rect(100, 300, 400, 364));

        if (Flags.enableFreeformBoxShadows()) {
            if (inSyncWithTransition) {
                verify(mMockSurfaceControlStartT).setBoxShadowSettings(eq(mMockTaskSurface), any());
                verify(mMockSurfaceControlFinishT).setBoxShadowSettings(eq(mMockTaskSurface),
                        any());
                verify(mMockSurfaceControlStartT).setBorderSettings(eq(mMockTaskSurface), any());
                verify(mMockSurfaceControlFinishT).setBorderSettings(eq(mMockTaskSurface), any());
            } else {
                verify(mMockSurfaceControlStartT, never()).setBoxShadowSettings(
                        eq(mMockTaskSurface), any());
                verify(mMockSurfaceControlFinishT, never()).setBoxShadowSettings(
                        eq(mMockTaskSurface), any());
                verify(mMockSurfaceControlStartT, never()).setBorderSettings(eq(mMockTaskSurface),
                        any());
                verify(mMockSurfaceControlFinishT, never()).setBorderSettings(eq(mMockTaskSurface),
                        any());
            }
        } else if (DesktopExperienceFlags.ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX.isTrue()) {
            if (inSyncWithTransition) {
                final int cornerRadius = WindowDecoration.loadDimensionPixelSize(
                        windowDecor.mDecorWindowContext.getResources(),
                        mRelayoutParams.mCornerRadiusId);
                verify(mMockSurfaceControlStartT).setCornerRadius(mMockTaskSurface, cornerRadius);
                verify(mMockSurfaceControlFinishT).setCornerRadius(mMockTaskSurface, cornerRadius);
                final int shadowRadius = WindowDecoration.loadDimensionPixelSize(
                        windowDecor.mDecorWindowContext.getResources(),
                        mRelayoutParams.mShadowRadiusId);
                verify(mMockSurfaceControlStartT).setShadowRadius(mMockTaskSurface, shadowRadius);
            } else {
                verify(mMockSurfaceControlStartT, never()).setCornerRadius(eq(mMockTaskSurface),
                        anyFloat());
                verify(mMockSurfaceControlFinishT, never()).setCornerRadius(eq(mMockTaskSurface),
                        anyFloat());
                verify(mMockSurfaceControlStartT, never()).setShadowRadius(eq(mMockTaskSurface),
                        anyFloat());
            }
        } else {
            verify(mMockSurfaceControlStartT).setCornerRadius(mMockTaskSurface, CORNER_RADIUS);
            verify(mMockSurfaceControlFinishT).setCornerRadius(mMockTaskSurface, CORNER_RADIUS);
            verify(mMockSurfaceControlStartT).setShadowRadius(mMockTaskSurface, SHADOW_RADIUS);
        }

        assertEquals(300, mRelayoutResult.mWidth);
        assertEquals(100, mRelayoutResult.mHeight);
    }

    @Test
    public void testLayoutResultCalculation_visibleFocusedTaskToInvisible() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);

        final SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mMockSurfaceControlTransactions.add(t);

        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .build();
        // Density is 2. Shadow radius is 10px. Caption height is 64px.
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;

        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = true;

        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockWindowDecorViewHost, never()).release(any());
        verify(t, never()).apply();
        verify(mMockWindowContainerTransaction, never())
                .removeInsetsSource(eq(taskInfo.token), any(), anyInt(), anyInt());

        final SurfaceControl.Transaction t2 = mock(SurfaceControl.Transaction.class);
        mMockSurfaceControlTransactions.add(t2);
        taskInfo.isVisible = false;
        windowDecor.relayout(taskInfo, false /* hasGlobalFocus */);

        final InOrder releaseOrder = inOrder(t2, mMockWindowDecorViewHostSupplier);
        releaseOrder.verify(mMockWindowDecorViewHostSupplier).release(mMockWindowDecorViewHost, t2);
        releaseOrder.verify(t2).remove(decorContainerSurface);
        releaseOrder.verify(t2).apply();
        // Expect to remove two insets sources, the caption insets and the mandatory gesture insets.
        verify(mMockWindowContainerTransaction, Mockito.times(2))
                .removeInsetsSource(eq(taskInfo.token), any(), anyInt(), anyInt());
    }

    @Test
    public void testNotCrashWhenDisplayAppearsAfterTask() {
        doReturn(mock(Display.class)).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final int displayId = Display.DEFAULT_DISPLAY + 1;
        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.BLACK);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(displayId)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setVisible(true)
                .build();

        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        // It shouldn't show the window decoration when it can't obtain the display instance.
        assertThat(mRelayoutResult.mRootView).isNull();

        final ArgumentCaptor<DisplayController.OnDisplaysChangedListener> listenerArgumentCaptor =
                ArgumentCaptor.forClass(DisplayController.OnDisplaysChangedListener.class);
        verify(mMockDisplayController).addDisplayWindowListener(listenerArgumentCaptor.capture());
        final DisplayController.OnDisplaysChangedListener listener =
                listenerArgumentCaptor.getValue();

        // Adding an irrelevant display shouldn't change the result.
        listener.onDisplayAdded(Display.DEFAULT_DISPLAY);
        assertThat(mRelayoutResult.mRootView).isNull();

        final Display mockDisplay = mock(Display.class);
        doReturn(mockDisplay).when(mMockDisplayController).getDisplay(displayId);

        listener.onDisplayAdded(displayId);

        // The listener should be removed when the display shows up.
        verify(mMockDisplayController).removeDisplayWindowListener(same(listener));

        assertThat(mRelayoutResult.mRootView).isSameInstanceAs(mMockView);
        verify(mMockWindowDecorViewHostSupplier).acquire(any(), eq(mockDisplay));
        verify(mMockWindowDecorViewHost).updateView(same(mMockView), any(), any(), any(), any());
    }


    @Test
    public void testReinflateViewsOnFontScaleChange() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        final TestWindowDecoration windowDecor = spy(createWindowDecoration(taskInfo));
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        clearInvocations(windowDecor);
        final ActivityManager.RunningTaskInfo taskInfo2 = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        taskInfo2.configuration.fontScale = taskInfo.configuration.fontScale + 1;
        windowDecor.relayout(taskInfo2, true /* hasGlobalFocus */, Region.obtain());
        // WindowDecoration#releaseViews should be called since the font scale has changed.
        verify(windowDecor).releaseViews(any());
    }

    @Test
    public void testViewNotReinflatedWhenFontScaleNotChanged() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        final TestWindowDecoration windowDecor = spy(createWindowDecoration(taskInfo));
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        clearInvocations(windowDecor);
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        // WindowDecoration#releaseViews should be called since task info (and therefore the
        // fontScale) has not changed.
        verify(windowDecor, never()).releaseViews(any());
    }

    @Test
    public void testAddViewHostViewContainer() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);

        final SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mMockSurfaceControlTransactions.add(t);
        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .build();
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        final SurfaceControl additionalWindowSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder additionalWindowSurfaceBuilder =
                createMockSurfaceControlBuilder(additionalWindowSurface);
        mMockSurfaceControlBuilders.add(additionalWindowSurfaceBuilder);

        windowDecor.addTestViewContainer(defaultDisplay);

        verify(additionalWindowSurfaceBuilder).setContainerLayer();
        verify(additionalWindowSurfaceBuilder).setParent(decorContainerSurface);
        verify(additionalWindowSurfaceBuilder).build();
        verify(mMockSurfaceControlAddWindowT).setPosition(additionalWindowSurface, 0, 0);
        final int width = WindowDecoration.loadDimensionPixelSize(
                windowDecor.mDecorWindowContext.getResources(), mCaptionMenuWidthId);
        final int height = mRelayoutParams.mCaptionHeightCalculator.apply(
                windowDecor.mDecorWindowContext, defaultDisplay);
        verify(mMockSurfaceControlAddWindowT).setWindowCrop(additionalWindowSurface, width, height);
        verify(mMockSurfaceControlAddWindowT).show(additionalWindowSurface);
        verify(mMockSurfaceControlViewHostFactory).create(any(), eq(defaultDisplay), any());
    }

    @Test
    public void testReinflateViewsOnLocaleListChange() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        taskInfo.configuration.setLocales(new LocaleList(Locale.FRANCE, Locale.US));
        final TestWindowDecoration windowDecor = spy(createWindowDecoration(taskInfo));
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        clearInvocations(windowDecor);

        final ActivityManager.RunningTaskInfo taskInfo2 = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        taskInfo2.configuration.setLocales(new LocaleList(Locale.US, Locale.FRANCE));
        windowDecor.relayout(taskInfo2, true /* hasGlobalFocus */, Region.obtain());
        // WindowDecoration#releaseViews should be called since the locale list has changed.
        verify(windowDecor, times(1)).releaseViews(any());
    }

    @Test
    public void testViewNotReinflatedWhenLocaleListNotChanged() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .build();
        taskInfo.configuration.setLocales(new LocaleList(Locale.FRANCE, Locale.US));
        final TestWindowDecoration windowDecor = spy(createWindowDecoration(taskInfo));
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        clearInvocations(windowDecor);
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());
        // WindowDecoration#releaseViews should not be called since nothing has changed.
        verify(windowDecor, never()).releaseViews(any());
    }

    @Test
    public void testLayoutResultCalculation_fullWidthCaption() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);

        final SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mMockSurfaceControlTransactions.add(t);
        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .build();
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        final SurfaceControl captionContainerSurface = mMockWindowDecorViewHost.getSurfaceControl();
        verify(mMockSurfaceControlStartT).reparent(captionContainerSurface, decorContainerSurface);
        // Width of the captionContainerSurface should match the width of TASK_BOUNDS
        verify(mMockSurfaceControlStartT).setWindowCrop(captionContainerSurface, 300, 64);
        verify(mMockSurfaceControlStartT).show(captionContainerSurface);
    }

    @Test
    public void testRelayout_applyTransactionInSyncWithDraw() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);

        final SurfaceControl.Transaction t = mock(SurfaceControl.Transaction.class);
        mMockSurfaceControlTransactions.add(t);
        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .build();
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mApplyStartTransactionOnDraw = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */, Region.obtain());

        verify(mMockWindowDecorViewHost).updateView(any(), any(), any(), any(),
                eq(mMockSurfaceControlStartT));
    }

    @Test
    public void testRelayout_withPadding_setsOnResult() {
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mCaptionTopPadding = 50;

        windowDecor.relayout(taskInfo, false /* applyStartTransactionOnDraw */,
                true /* hasGlobalFocus */, Region.obtain());

        assertEquals(50, mRelayoutResult.mCaptionTopPadding);
    }

    @Test
    public void testRelayout_shouldSetBackground_freeformTask_setTaskSurfaceColor() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);
        final SurfaceControl captionContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder captionContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(captionContainerSurface);
        mMockSurfaceControlBuilders.add(captionContainerSurfaceBuilder);

        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setVisible(true)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mShouldSetBackground = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockSurfaceControlStartT).setColor(mMockTaskSurface, new float[]{1.f, 1.f, 0.f});
    }

    @Test
    public void testInsetsAddedWhenCaptionIsVisible() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder();
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setVisible(true)
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mIsCaptionVisible = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verifyAddedInsets(1 /* times */, taskInfo.token, 0 /* index */, captionBar());
        verifyAddedInsets(1 /* times */, taskInfo.token, 0 /* index */, mandatorySystemGestures());
    }

    @Test
    public void testRelayout_shouldNotSetBackground_fullscreenTask_clearTaskSurfaceColor() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final SurfaceControl decorContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder decorContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(decorContainerSurface);
        mMockSurfaceControlBuilders.add(decorContainerSurfaceBuilder);
        final SurfaceControl captionContainerSurface = mock(SurfaceControl.class);
        final SurfaceControl.Builder captionContainerSurfaceBuilder =
                createMockSurfaceControlBuilder(captionContainerSurface);
        mMockSurfaceControlBuilders.add(captionContainerSurfaceBuilder);

        final ActivityManager.TaskDescription.Builder taskDescriptionBuilder =
                new ActivityManager.TaskDescription.Builder()
                        .setBackgroundColor(Color.YELLOW);
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setTaskDescriptionBuilder(taskDescriptionBuilder)
                .setVisible(true)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = false;

        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockSurfaceControlStartT).unsetColor(mMockTaskSurface);
    }

    @Test
    public void testRelayout_captionHidden_neverWasVisible_insetsNotRemoved() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true)
                .setBounds(new Rect(0, 0, 1000, 1000))
                .build();
        // Hidden from the beginning, so no insets were ever added.
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = false;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        // Never added.
        verifyAddedInsets(0 /* times */, taskInfo.token, 0 /* index */, captionBar());
        verifyAddedInsets(0 /* times */, taskInfo.token, 0 /* index */, mandatorySystemGestures());
        // No need to remove them if they were never added.
        verify(mMockWindowContainerTransaction, never()).removeInsetsSource(eq(taskInfo.token),
                any(), eq(0) /* index */, eq(captionBar()));
        verify(mMockWindowContainerTransaction, never()).removeInsetsSource(eq(taskInfo.token),
                any(), eq(0) /* index */, eq(mandatorySystemGestures()));
    }

    @Test
    public void testRelayout_notAnInsetsSource_doesNotAddInsets() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true)
                .setBounds(new Rect(0, 0, 1000, 1000))
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mIsInsetSource = false;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        // Never added.
        verifyAddedInsets(0 /* times */, taskInfo.token, 0 /* index */, captionBar());
        verifyAddedInsets(0 /* times */, taskInfo.token, 0 /* index */, mandatorySystemGestures());
    }

    @Test
    public void testRelayout_notAnInsetsSource_hadInsetsBefore_removesInsets() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true)
                .setBounds(new Rect(0, 0, 1000, 1000))
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mIsInsetSource = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mIsInsetSource = false;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        // Insets should be removed.
        verify(mMockWindowContainerTransaction).removeInsetsSource(eq(taskInfo.token), any(),
                eq(0) /* index */, eq(captionBar()));
        verify(mMockWindowContainerTransaction).removeInsetsSource(eq(taskInfo.token), any(),
                eq(0) /* index */, eq(mandatorySystemGestures()));
    }

    @Test
    public void testClose_withExistingInsets_insetsRemoved() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true)
                .setBounds(new Rect(0, 0, 1000, 1000))
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        // Relayout will add insets.
        mRelayoutParams.mIsCaptionVisible = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);
        verifyAddedInsets(1 /* times */, taskInfo.token, 0 /* index */, captionBar());
        verifyAddedInsets(1 /* times */, taskInfo.token, 0 /* index */, mandatorySystemGestures());

        windowDecor.close();

        // Insets should be removed.
        verify(mMockWindowContainerTransaction).removeInsetsSource(eq(taskInfo.token), any(),
                eq(0) /* index */, eq(captionBar()));
        verify(mMockWindowContainerTransaction).removeInsetsSource(eq(taskInfo.token), any(),
                eq(0) /* index */, eq(mandatorySystemGestures()));
    }

    @Test
    public void testClose_withoutExistingInsets_insetsNotRemoved() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true)
                .setBounds(new Rect(0, 0, 1000, 1000))
                .build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        windowDecor.close();

        // No need to remove insets.
        verify(mMockWindowContainerTransaction, never()).removeInsetsSource(eq(taskInfo.token),
                any(), eq(0) /* index */, eq(captionBar()));
        verify(mMockWindowContainerTransaction, never()).removeInsetsSource(eq(taskInfo.token),
                any(), eq(0) /* index */, eq(mandatorySystemGestures()));
    }

    @Test
    public void testClose_withTaskDragResizerSet_callResizerClose() {
        final TestWindowDecoration windowDecor = createWindowDecoration(
                new TestRunningTaskInfoBuilder().build());
        final TaskDragResizer taskDragResizer = mock(TaskDragResizer.class);
        windowDecor.setTaskDragResizer(taskDragResizer);

        windowDecor.close();

        verify(taskDragResizer).close();
    }

    @Test
    public void testRelayout_captionFrameChanged_insetsReapplied() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);
        mInsetsState.getOrCreateSource(STATUS_BAR_INSET_SOURCE_ID, captionBar()).setVisible(true);
        final WindowContainerToken token = new MockToken().token();
        final TestRunningTaskInfoBuilder builder = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true);
        mRelayoutParams.mIsCaptionVisible = true;

        // Relayout twice with different bounds.
        final ActivityManager.RunningTaskInfo firstTaskInfo =
                builder.setToken(token).setBounds(new Rect(0, 0, 1000, 1000)).build();
        final TestWindowDecoration windowDecor = createWindowDecoration(firstTaskInfo);
        windowDecor.relayout(firstTaskInfo, true /* hasGlobalFocus */);
        final ActivityManager.RunningTaskInfo secondTaskInfo =
                builder.setToken(token).setBounds(new Rect(50, 50, 1000, 1000)).build();
        windowDecor.relayout(secondTaskInfo, true /* hasGlobalFocus */);

        // Insets should be applied twice.
        verifyAddedInsets(2 /* times */, token, 0 /* index */, captionBar());
        verifyAddedInsets(2 /* times */, token, 0 /* index */, mandatorySystemGestures());
    }

    @Test
    public void testRelayout_captionFrameUnchanged_insetsNotApplied() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);
        mInsetsState.getOrCreateSource(STATUS_BAR_INSET_SOURCE_ID, captionBar()).setVisible(true);
        final WindowContainerToken token = new MockToken().token();
        final TestRunningTaskInfoBuilder builder = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true);
        mRelayoutParams.mIsCaptionVisible = true;

        // Relayout twice with the same bounds.
        final ActivityManager.RunningTaskInfo firstTaskInfo =
                builder.setToken(token).setBounds(new Rect(0, 0, 1000, 1000)).build();
        final TestWindowDecoration windowDecor = createWindowDecoration(firstTaskInfo);
        windowDecor.relayout(firstTaskInfo, true /* hasGlobalFocus */);
        final ActivityManager.RunningTaskInfo secondTaskInfo =
                builder.setToken(token).setBounds(new Rect(0, 0, 1000, 1000)).build();
        windowDecor.relayout(secondTaskInfo, true /* hasGlobalFocus */);

        // Insets should only need to be applied once.
        verifyAddedInsets(1 /* times */, token, 0 /* index */, captionBar());
        verifyAddedInsets(1 /* times */, token, 0 /* index */, mandatorySystemGestures());
    }

    @Test
    public void testRelayout_captionInsetSourceFlags() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);
        final WindowContainerToken token = new MockToken().token();
        final TestRunningTaskInfoBuilder builder = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true);

        final ActivityManager.RunningTaskInfo taskInfo =
                builder.setToken(token).setBounds(new Rect(0, 0, 1000, 1000)).build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mInsetSourceFlags =
                FLAG_FORCE_CONSUMING | FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        // Caption inset source should add params' flags.

        verifyAddedInsets(1 /* times */, token, 0 /* index */, captionBar(),
                FLAG_FORCE_CONSUMING | FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR);
    }

    @Test
    public void testRelayout_setAppBoundsIfNeeded() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController).getDisplay(Display.DEFAULT_DISPLAY);
        final WindowContainerToken token = new MockToken().token();
        final TestRunningTaskInfoBuilder builder = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true);

        final ActivityManager.RunningTaskInfo taskInfo =
                builder.setToken(token).setBounds(TASK_BOUNDS).build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);
        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mShouldSetAppBounds = true;

        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);
        final Rect appBounds = new Rect(TASK_BOUNDS);
        appBounds.top += mRelayoutParams.mCaptionHeightCalculator.apply(
                windowDecor.mDecorWindowContext, defaultDisplay);
        verify(mMockWindowContainerTransaction).setAppBounds(eq(token), eq(appBounds));
    }

    @Test
    public void testRelayout_setAppBoundsIfNeeded_reset() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController).getDisplay(Display.DEFAULT_DISPLAY);
        final WindowContainerToken token = new MockToken().token();
        final TestRunningTaskInfoBuilder builder = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setVisible(true);

        final ActivityManager.RunningTaskInfo taskInfo =
                builder.setToken(token).setBounds(TASK_BOUNDS).build();
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mIsInsetSource = true;
        mRelayoutParams.mShouldSetAppBounds = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        mRelayoutParams.mIsCaptionVisible = true;
        mRelayoutParams.mIsInsetSource = false;
        mRelayoutParams.mShouldSetAppBounds = false;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockWindowContainerTransaction).setAppBounds(eq(token), eq(new Rect()));
    }

    @Test
    public void testTaskPositionAndCropNotSetWhenFalse() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build();
        // Density is 2. Shadow radius is 10px. Caption height is 64px.
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);


        mRelayoutParams.mSetTaskVisibilityPositionAndCrop = false;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockSurfaceControlStartT, never()).setWindowCrop(
                eq(mMockTaskSurface), anyInt(), anyInt());
        verify(mMockSurfaceControlFinishT, never()).setPosition(
                eq(mMockTaskSurface), anyFloat(), anyFloat());
        verify(mMockSurfaceControlFinishT, never()).setWindowCrop(
                eq(mMockTaskSurface), anyInt(), anyInt());
    }

    @Test
    public void testTaskPositionAndCropSetWhenSetTrue() {
        final Display defaultDisplay = mock(Display.class);
        doReturn(defaultDisplay).when(mMockDisplayController)
                .getDisplay(Display.DEFAULT_DISPLAY);

        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(Display.DEFAULT_DISPLAY)
                .setBounds(TASK_BOUNDS)
                .setPositionInParent(TASK_POSITION_IN_PARENT.x, TASK_POSITION_IN_PARENT.y)
                .setVisible(true)
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build();
        // Density is 2. Shadow radius is 10px. Caption height is 64px.
        taskInfo.configuration.densityDpi = DisplayMetrics.DENSITY_DEFAULT * 2;
        final TestWindowDecoration windowDecor = createWindowDecoration(taskInfo);

        mRelayoutParams.mSetTaskVisibilityPositionAndCrop = true;
        windowDecor.relayout(taskInfo, true /* hasGlobalFocus */);

        verify(mMockSurfaceControlStartT).setWindowCrop(
                eq(mMockTaskSurface), anyInt(), anyInt());
        verify(mMockSurfaceControlFinishT).setPosition(
                eq(mMockTaskSurface), anyFloat(), anyFloat());
        verify(mMockSurfaceControlFinishT).setWindowCrop(
                eq(mMockTaskSurface), anyInt(), anyInt());
    }

    @Test
    public void relayout_applyTransactionOnDrawIsTrue_updatesViewWithDrawTransaction() {
        final TestWindowDecoration windowDecor = createWindowDecoration(
                new TestRunningTaskInfoBuilder()
                        .setVisible(true)
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .build());
        mRelayoutParams.mApplyStartTransactionOnDraw = true;
        mRelayoutResult.mRootView = mMockView;

        windowDecor.relayout(
                windowDecor.mTaskInfo,
                /* hasGlobalFocus= */ true,
                Region.obtain());

        verify(mMockWindowDecorViewHost)
                .updateView(
                        eq(mRelayoutResult.mRootView),
                        any(),
                        eq(windowDecor.mTaskInfo.configuration),
                        any(),
                        eq(mMockSurfaceControlStartT));
        windowDecor.close();
    }

    @Test
    public void relayout_applyTransactionOnDrawIsTrue_asyncViewHostRendering_throwsException() {
        final TestWindowDecoration windowDecor = createWindowDecoration(
                new TestRunningTaskInfoBuilder()
                        .setVisible(true)
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                        .build());
        mRelayoutParams.mApplyStartTransactionOnDraw = true;
        mRelayoutParams.mAsyncViewHost = true;
        mRelayoutResult.mRootView = mMockView;

        assertThrows(IllegalArgumentException.class,
                () -> windowDecor.relayout(
                        windowDecor.mTaskInfo,
                        /* hasGlobalFocus= */ true,
                        Region.obtain()));
        windowDecor.close();
    }

    @Test
    public void relayout_asyncViewHostRendering() {
        final TestWindowDecoration windowDecor = createWindowDecoration(
                new TestRunningTaskInfoBuilder()
                        .setVisible(true)
                        .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                        .build());
        mRelayoutParams.mApplyStartTransactionOnDraw = false;
        mRelayoutParams.mAsyncViewHost = true;
        mRelayoutResult.mRootView = mMockView;

        windowDecor.relayout(
                windowDecor.mTaskInfo,
                /* hasGlobalFocus= */ true,
                Region.obtain());

        verify(mMockWindowDecorViewHost)
                .updateViewAsync(eq(mRelayoutResult.mRootView), any(),
                        eq(windowDecor.mTaskInfo.configuration), any());
        windowDecor.close();
    }

    @Test
    public void onStatusBarVisibilityChange() {
        final ActivityManager.RunningTaskInfo task = createTaskInfo();
        task.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        when(mMockDisplayController.getInsetsState(task.displayId))
                .thenReturn(createInsetsState(statusBars(), true /* visible */));
        final TestWindowDecoration decor = spy(createWindowDecoration(task));
        decor.relayout(task, true /* hasGlobalFocus */);
        assertTrue(decor.mIsStatusBarVisible);

        decor.onInsetsStateChanged(createInsetsState(statusBars(), false /* visible */));

        verify(decor, times(2)).relayout(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void onStatusBarVisibilityChange_noChange_doesNotRelayout() {
        final ActivityManager.RunningTaskInfo task = createTaskInfo();
        task.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        when(mMockDisplayController.getInsetsState(task.displayId))
                .thenReturn(createInsetsState(statusBars(), true /* visible */));
        final TestWindowDecoration decor = spy(createWindowDecoration(task));
        decor.relayout(task, true /* hasGlobalFocus */);

        decor.onInsetsStateChanged(createInsetsState(statusBars(), true /* visible */));

        verify(decor, times(1)).relayout(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void onKeyguardStateChange() {
        final ActivityManager.RunningTaskInfo task = createTaskInfo();
        when(mMockDisplayController.getInsetsState(task.displayId))
                .thenReturn(createInsetsState(statusBars(), true /* visible */));
        final TestWindowDecoration decor = spy(createWindowDecoration(task));
        decor.relayout(task, true /* hasGlobalFocus */);
        assertFalse(decor.mIsKeyguardVisibleAndOccluded);

        decor.onKeyguardStateChanged(true /* visible */, true /* occluding */);

        assertTrue(decor.mIsKeyguardVisibleAndOccluded);
        verify(decor, times(2)).relayout(any(), any(), any(), any(), any(), any());
    }

    @Test
    public void onKeyguardStateChange_noChange_doesNotRelayout() {
        final ActivityManager.RunningTaskInfo task = createTaskInfo();
        when(mMockDisplayController.getInsetsState(task.displayId))
                .thenReturn(createInsetsState(statusBars(), true /* visible */));
        final TestWindowDecoration decor = spy(createWindowDecoration(task));
        decor.relayout(task, true /* hasGlobalFocus */);
        assertFalse(decor.mIsKeyguardVisibleAndOccluded);

        decor.onKeyguardStateChanged(false /* visible */, true /* occluding */);

        verify(decor, times(1)).relayout(any(), any(), any(), any(), any(), any());
    }

    private ActivityManager.RunningTaskInfo createTaskInfo() {
        final ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder()
                .setVisible(true)
                .build();
        return taskInfo;
    }

    private InsetsState createInsetsState(@WindowInsets.Type.InsetsType int type, boolean visible) {
        final InsetsState state = new InsetsState();
        final InsetsSource source = new InsetsSource(0, type);
        source.setVisible(visible);
        state.addSource(source);
        return state;
    }

    private TestWindowDecoration createWindowDecoration(ActivityManager.RunningTaskInfo taskInfo) {
        return new TestWindowDecoration(mContext, mContext, mMockDisplayController,
                mMockShellTaskOrganizer, taskInfo, mMockTaskSurface,
                new MockObjectSupplier<>(mMockSurfaceControlBuilders,
                        () -> createMockSurfaceControlBuilder(mock(SurfaceControl.class))),
                new MockObjectSupplier<>(mMockSurfaceControlTransactions,
                        () -> mock(SurfaceControl.Transaction.class)),
                () -> mMockWindowContainerTransaction, () -> mMockTaskSurface,
                mMockSurfaceControlViewHostFactory, mMockWindowDecorViewHostSupplier,
                mDesktopModeEventLogger);
    }

    private class MockObjectSupplier<T> implements Supplier<T> {
        private final List<T> mObjects;
        private final Supplier<T> mDefaultSupplier;
        private int mNumOfCalls = 0;

        private MockObjectSupplier(List<T> objects, Supplier<T> defaultSupplier) {
            mObjects = objects;
            mDefaultSupplier = defaultSupplier;
        }

        @Override
        public T get() {
            final T mock = mNumOfCalls < mObjects.size()
                    ? mObjects.get(mNumOfCalls) : mDefaultSupplier.get();
            ++mNumOfCalls;
            return mock;
        }
    }

    private void verifyAddedInsets(int times, WindowContainerToken token, int index, int type) {
        if (com.android.window.flags.Flags.relativeInsets()) {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), any(Insets.class), any(), anyInt());
        } else {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), any(Rect.class), any(), anyInt());
        }
    }

    private void verifyAddedInsets(int times, WindowContainerToken token, int index, int type,
            int flags) {
        if (com.android.window.flags.Flags.relativeInsets()) {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), any(Insets.class), any(), eq(flags));
        } else {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), any(Rect.class), any(), eq(flags));
        }
    }

    private void verifyAddedInsets(int times, WindowContainerToken token, int index, int type,
            Rect attachedRect) {
        if (com.android.window.flags.Flags.relativeInsets()) {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), eq(Insets.of(0, attachedRect.height(), 0, 0)), any(),
                    anyInt());
        } else {
            verify(mMockWindowContainerTransaction, times(times)).addInsetsSource(eq(token), any(),
                    eq(index), eq(type), eq(attachedRect), any(), anyInt());
        }
    }

    private static class TestView extends View implements TaskFocusStateConsumer {
        private TestView(Context context) {
            super(context);
        }

        @Override
        public void setTaskFocusState(boolean focused) {
        }
    }

    private class TestWindowDecoration extends WindowDecoration<TestView> {
        TestWindowDecoration(Context context, @NonNull Context userContext,
                DisplayController displayController,
                ShellTaskOrganizer taskOrganizer,
                ActivityManager.RunningTaskInfo taskInfo,
                SurfaceControl taskSurface,
                Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
                Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
                Supplier<WindowContainerTransaction> windowContainerTransactionSupplier,
                Supplier<SurfaceControl> surfaceControlSupplier,
                SurfaceControlViewHostFactory surfaceControlViewHostFactory,
                @NonNull WindowDecorViewHostSupplier<WindowDecorViewHost>
                        windowDecorViewHostSupplier,
                DesktopModeEventLogger desktopModeEventLogger) {
            super(context, mTestHandler, mTransitions, userContext, displayController,
                    taskOrganizer, taskInfo, taskSurface, surfaceControlBuilderSupplier,
                    surfaceControlTransactionSupplier, windowContainerTransactionSupplier,
                    surfaceControlSupplier, surfaceControlViewHostFactory,
                    windowDecorViewHostSupplier, desktopModeEventLogger);
        }

        void relayout(ActivityManager.RunningTaskInfo taskInfo, boolean hasGlobalFocus) {
            relayout(taskInfo, false /* applyStartTransactionOnDraw */, hasGlobalFocus,
                    Region.obtain());
        }

        @Override
        void relayout(ActivityManager.RunningTaskInfo taskInfo, boolean hasGlobalFocus,
                @NonNull Region displayExclusionRegion) {
            mRelayoutParams.mRunningTaskInfo = taskInfo;
            mRelayoutParams.mHasGlobalFocus = hasGlobalFocus;
            mRelayoutParams.mDisplayExclusionRegion.set(displayExclusionRegion);
            mRelayoutParams.mLayoutResId = R.layout.caption_layout;
            relayout(mRelayoutParams, mMockSurfaceControlStartT, mMockSurfaceControlFinishT,
                    mMockWindowContainerTransaction, mMockView, mRelayoutResult);
        }

        @Override
        Rect calculateValidDragArea() {
            return null;
        }

        @Override
        int getCaptionViewId() {
            return R.id.caption;
        }

        @Override
        TestView inflateLayout(Context context, int layoutResId) {
            if (layoutResId == R.layout.caption_layout) {
                return mMockView;
            }
            return super.inflateLayout(context, layoutResId);
        }

        void relayout(ActivityManager.RunningTaskInfo taskInfo,
                boolean applyStartTransactionOnDraw, boolean hasGlobalFocus,
                @NonNull Region displayExclusionRegion) {
            mRelayoutParams.mApplyStartTransactionOnDraw = applyStartTransactionOnDraw;
            relayout(taskInfo, hasGlobalFocus, displayExclusionRegion);
        }

        private AdditionalViewContainer addTestViewContainer(Display display) {
            final Resources resources = mDecorWindowContext.getResources();
            final int width = loadDimensionPixelSize(resources, mCaptionMenuWidthId);
            final int height = mRelayoutParams.mCaptionHeightCalculator.apply(mDecorWindowContext,
                    display);
            final String name = "Test Window";
            final AdditionalViewContainer additionalViewContainer =
                    addWindow(R.layout.desktop_mode_window_decor_handle_menu, name,
                            mMockSurfaceControlAddWindowT, mMockSurfaceSyncGroup, 0 /* x */,
                            0 /* y */, width, height);
            return additionalViewContainer;
        }
    }
}
