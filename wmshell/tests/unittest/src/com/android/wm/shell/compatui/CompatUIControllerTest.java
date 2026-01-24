/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import static android.view.WindowInsets.Type.navigationBars;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.TaskInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.testing.AndroidTestingRunner;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.window.flags2.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayInsetsController.OnInsetsChangedListener;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.DockStateReader;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.api.CompatUIInfo;
import com.android.wm.shell.compatui.impl.CompatUIRequests;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/**
 * Tests for {@link CompatUIController}.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:CompatUIControllerTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CompatUIControllerTest extends ShellTestCase {
    private static final int DISPLAY_ID = 0;
    private static final int TASK_ID = 12;
    private static final int TASK_ID_2 = 18;

    private CompatUIController mController;
    private ShellInit mShellInit;
    @Mock
    private ShellController mMockShellController;
    @Mock
    private DisplayController mMockDisplayController;
    @Mock
    private DisplayInsetsController mMockDisplayInsetsController;
    @Mock
    private DisplayLayout mMockDisplayLayout;
    @Mock
    private DisplayImeController mMockImeController;
    @Mock
    private ShellTaskOrganizer.TaskListener mMockTaskListener;
    @Mock
    private SyncTransactionQueue mMockSyncQueue;
    @Mock
    private ShellExecutor mMockExecutor;
    @Mock
    private Lazy<Transitions> mMockTransitionsLazy;
    @Mock
    private CompatUIWindowManager mMockCompatLayout;
    @Mock
    private LetterboxEduWindowManager mMockLetterboxEduLayout;
    @Mock
    private RestartDialogWindowManager mMockRestartDialogLayout;
    @Mock
    private DockStateReader mDockStateReader;
    @Mock
    private CompatUIConfiguration mCompatUIConfiguration;
    @Mock
    private CompatUIShellCommandHandler mCompatUIShellCommandHandler;
    @Mock
    private AccessibilityManager mAccessibilityManager;
    @Mock
    private DesktopUserRepositories mDesktopUserRepositories;
    @Mock
    private DesktopRepository mDesktopRepository;

    @Captor
    ArgumentCaptor<OnInsetsChangedListener> mOnInsetsChangedListenerCaptor;

    @NonNull
    private CompatUIStatusManager mCompatUIStatusManager;
    @NonNull
    private FakeDesktopState mDesktopState;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDesktopState = new FakeDesktopState();
        doReturn(mMockDisplayLayout).when(mMockDisplayController).getDisplayLayout(anyInt());
        doReturn(DISPLAY_ID).when(mMockCompatLayout).getDisplayId();
        doReturn(TASK_ID).when(mMockCompatLayout).getTaskId();
        doReturn(true).when(mMockCompatLayout).createLayout(anyBoolean());
        doReturn(true).when(mMockCompatLayout).updateCompatInfo(any(), any(), anyBoolean());
        doReturn(DISPLAY_ID).when(mMockLetterboxEduLayout).getDisplayId();
        doReturn(TASK_ID).when(mMockLetterboxEduLayout).getTaskId();
        doReturn(true).when(mMockLetterboxEduLayout).createLayout(anyBoolean());
        doReturn(true).when(mMockLetterboxEduLayout).updateCompatInfo(any(), any(), anyBoolean());
        doReturn(mDesktopRepository).when(mDesktopUserRepositories).getCurrent();
        doReturn(mDesktopRepository).when(mDesktopUserRepositories).getProfile(anyInt());

        doReturn(DISPLAY_ID).when(mMockRestartDialogLayout).getDisplayId();
        doReturn(TASK_ID).when(mMockRestartDialogLayout).getTaskId();
        doReturn(true).when(mMockRestartDialogLayout).createLayout(anyBoolean());
        doReturn(true).when(mMockRestartDialogLayout).updateCompatInfo(any(), any(), anyBoolean());

        mCompatUIStatusManager = new CompatUIStatusManager();
        mShellInit = spy(new ShellInit(mMockExecutor));
        mController = new CompatUIController(mContext, mShellInit, mMockShellController,
                mMockDisplayController, mMockDisplayInsetsController, mMockImeController,
                mMockSyncQueue, mMockExecutor, mMockTransitionsLazy, mDockStateReader,
                mCompatUIConfiguration, mCompatUIShellCommandHandler, mAccessibilityManager,
                mCompatUIStatusManager, Optional.of(mDesktopUserRepositories),
                mDesktopState) {
            @Override
            CompatUIWindowManager createCompatUiWindowManager(Context context, TaskInfo taskInfo,
                    ShellTaskOrganizer.TaskListener taskListener) {
                return mMockCompatLayout;
            }

            @Override
            LetterboxEduWindowManager createLetterboxEduWindowManager(Context context,
                    TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener) {
                return mMockLetterboxEduLayout;
            }

            @Override
            RestartDialogWindowManager createRestartDialogWindowManager(Context context,
                    TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener) {
                return mMockRestartDialogLayout;
            }
        };
        mShellInit.init();
        spyOn(mController);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void instantiateController_registerKeyguardChangeListener() {
        verify(mMockShellController, times(1)).addKeyguardChangeListener(any());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testListenerRegistered() {
        verify(mMockDisplayController).addDisplayWindowListener(mController);
        verify(mMockImeController).addPositionProcessor(mController);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnCompatInfoChanged() {
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);

        // Verify that the compat controls are added with non-null task listener.
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController).createCompatUiWindowManager(any(), eq(taskInfo), eq(mMockTaskListener));
        verify(mController).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));

        // Verify that the compat controls and letterbox education are updated with new size compat
        // info.
        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mController);
        taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);
        verify(mMockLetterboxEduLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);
        verify(mMockRestartDialogLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);

        // Verify that compat controls and letterbox education are removed with null task listener.
        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mController);
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true),
                /* taskListener= */ null));

        verify(mMockCompatLayout).release();
        verify(mMockLetterboxEduLayout).release();
        verify(mMockRestartDialogLayout).release();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnCompatInfoChanged_createLayoutReturnsFalse() {
        doReturn(false).when(mMockCompatLayout).createLayout(anyBoolean());
        doReturn(false).when(mMockLetterboxEduLayout).createLayout(anyBoolean());
        doReturn(false).when(mMockRestartDialogLayout).createLayout(anyBoolean());

        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController).createCompatUiWindowManager(any(), eq(taskInfo), eq(mMockTaskListener));
        verify(mController).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));

        // Verify that the layout is created again.
        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mController);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mMockLetterboxEduLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mMockRestartDialogLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mController).createCompatUiWindowManager(any(), eq(taskInfo), eq(mMockTaskListener));
        verify(mController).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnCompatInfoChanged_updateCompatInfoReturnsFalse() {
        doReturn(false).when(mMockCompatLayout).updateCompatInfo(any(), any(), anyBoolean());
        doReturn(false).when(mMockLetterboxEduLayout).updateCompatInfo(any(), any(), anyBoolean());
        doReturn(false).when(mMockRestartDialogLayout).updateCompatInfo(any(), any(), anyBoolean());

        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController).createCompatUiWindowManager(any(), eq(taskInfo), eq(mMockTaskListener));
        verify(mController).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));

        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mMockRestartDialogLayout,
                mController);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);
        verify(mMockLetterboxEduLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);
        verify(mMockRestartDialogLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ true);

        // Verify that the layout is created again.
        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mMockRestartDialogLayout,
                mController);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mMockLetterboxEduLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mMockRestartDialogLayout, never()).updateCompatInfo(any(), any(), anyBoolean());
        verify(mController).createCompatUiWindowManager(any(), eq(taskInfo), eq(mMockTaskListener));
        verify(mController).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
    }


    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnDisplayAdded() {
        mController.onDisplayAdded(DISPLAY_ID);
        mController.onDisplayAdded(DISPLAY_ID + 1);

        verify(mMockDisplayInsetsController).addInsetsChangedListener(eq(DISPLAY_ID), any());
        verify(mMockDisplayInsetsController).addInsetsChangedListener(eq(DISPLAY_ID + 1), any());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnDisplayRemoved() {
        mController.onDisplayAdded(DISPLAY_ID);
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        mController.onDisplayRemoved(DISPLAY_ID + 1);

        verify(mMockCompatLayout, never()).release();
        verify(mMockLetterboxEduLayout, never()).release();
        verify(mMockRestartDialogLayout, never()).release();
        verify(mMockDisplayInsetsController, never()).removeInsetsChangedListener(eq(DISPLAY_ID),
                any());

        mController.onDisplayRemoved(DISPLAY_ID);

        verify(mMockDisplayInsetsController).removeInsetsChangedListener(eq(DISPLAY_ID), any());
        verify(mMockCompatLayout).release();
        verify(mMockLetterboxEduLayout).release();
        verify(mMockRestartDialogLayout).release();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testOnDisplayConfigurationChanged() {
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        mController.onDisplayConfigurationChanged(DISPLAY_ID + 1, new Configuration());

        verify(mMockCompatLayout, never()).updateDisplayLayout(any());
        verify(mMockLetterboxEduLayout, never()).updateDisplayLayout(any());
        verify(mMockRestartDialogLayout, never()).updateDisplayLayout(any());

        mController.onDisplayConfigurationChanged(DISPLAY_ID, new Configuration());

        verify(mMockCompatLayout).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockLetterboxEduLayout).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockRestartDialogLayout).updateDisplayLayout(mMockDisplayLayout);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testInsetsChanged() {
        mController.onDisplayAdded(DISPLAY_ID);
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));
        InsetsState insetsState = new InsetsState();
        InsetsSource insetsSource = new InsetsSource(
                InsetsSource.createId(null, 0, navigationBars()), navigationBars());
        insetsSource.setFrame(0, 0, 1000, 1000);
        insetsState.addSource(insetsSource);

        verify(mMockDisplayInsetsController).addInsetsChangedListener(eq(DISPLAY_ID),
                mOnInsetsChangedListenerCaptor.capture());
        mOnInsetsChangedListenerCaptor.getValue().insetsChanged(insetsState);

        verify(mMockCompatLayout).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockLetterboxEduLayout).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockRestartDialogLayout).updateDisplayLayout(mMockDisplayLayout);

        // No update if the insets state is the same.
        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mMockRestartDialogLayout);
        mOnInsetsChangedListenerCaptor.getValue().insetsChanged(new InsetsState(insetsState));
        verify(mMockCompatLayout, never()).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockLetterboxEduLayout, never()).updateDisplayLayout(mMockDisplayLayout);
        verify(mMockRestartDialogLayout, never()).updateDisplayLayout(mMockDisplayLayout);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testChangeLayoutsVisibilityOnImeShowHide() {
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        // Verify that the restart button is hidden after IME is showing.
        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ true);

        verify(mMockCompatLayout).updateVisibility(false);
        verify(mMockLetterboxEduLayout).updateVisibility(false);
        verify(mMockRestartDialogLayout).updateVisibility(false);

        // Verify button remains hidden while IME is showing.
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);
        verify(mMockLetterboxEduLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);
        verify(mMockRestartDialogLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);

        // Verify button is shown after IME is hidden.
        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ false);

        verify(mMockCompatLayout).updateVisibility(true);
        verify(mMockLetterboxEduLayout).updateVisibility(true);
        verify(mMockRestartDialogLayout).updateVisibility(true);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testChangeLayoutsVisibilityOnKeyguardShowingChanged() {
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        // Verify that the restart button is hidden after keyguard becomes showing.
        mController.onKeyguardVisibilityChanged(true, false, false);

        verify(mMockCompatLayout).updateVisibility(false);
        verify(mMockLetterboxEduLayout).updateVisibility(false);
        verify(mMockRestartDialogLayout).updateVisibility(false);

        // Verify button remains hidden while keyguard is showing.
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockCompatLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);
        verify(mMockLetterboxEduLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);
        verify(mMockRestartDialogLayout).updateCompatInfo(taskInfo, mMockTaskListener,
                /* canShow= */ false);

        // Verify button is shown after keyguard becomes not showing.
        mController.onKeyguardVisibilityChanged(false, false, false);

        verify(mMockCompatLayout).updateVisibility(true);
        verify(mMockLetterboxEduLayout).updateVisibility(true);
        verify(mMockRestartDialogLayout).updateVisibility(true);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testLayoutsRemainHiddenOnKeyguardShowingFalseWhenImeIsShowing() {
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ true);
        mController.onKeyguardVisibilityChanged(true, false, false);

        verify(mMockCompatLayout, times(2)).updateVisibility(false);
        verify(mMockLetterboxEduLayout, times(2)).updateVisibility(false);
        verify(mMockRestartDialogLayout, times(2)).updateVisibility(false);

        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mMockRestartDialogLayout);

        // Verify button remains hidden after keyguard becomes not showing since IME is showing.
        mController.onKeyguardVisibilityChanged(false, false, false);

        verify(mMockCompatLayout).updateVisibility(false);
        verify(mMockLetterboxEduLayout).updateVisibility(false);
        verify(mMockRestartDialogLayout).updateVisibility(false);

        // Verify button is shown after IME is not showing.
        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ false);

        verify(mMockCompatLayout).updateVisibility(true);
        verify(mMockLetterboxEduLayout).updateVisibility(true);
        verify(mMockRestartDialogLayout).updateVisibility(true);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testLayoutsRemainHiddenOnImeHideWhenKeyguardIsShowing() {
        mController.onCompatInfoChanged(new CompatUIInfo(createTaskInfo(DISPLAY_ID, TASK_ID,
                /* hasSizeCompat= */ true), mMockTaskListener));

        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ true);
        mController.onKeyguardVisibilityChanged(true, false, false);

        verify(mMockCompatLayout, times(2)).updateVisibility(false);
        verify(mMockLetterboxEduLayout, times(2)).updateVisibility(false);
        verify(mMockRestartDialogLayout, times(2)).updateVisibility(false);

        clearInvocations(mMockCompatLayout, mMockLetterboxEduLayout, mMockRestartDialogLayout);

        // Verify button remains hidden after IME is hidden since keyguard is showing.
        mController.onImeVisibilityChanged(DISPLAY_ID, /* isShowing= */ false);

        verify(mMockCompatLayout).updateVisibility(false);
        verify(mMockLetterboxEduLayout).updateVisibility(false);
        verify(mMockRestartDialogLayout).updateVisibility(false);

        // Verify button is shown after keyguard becomes not showing.
        mController.onKeyguardVisibilityChanged(false, false, false);

        verify(mMockCompatLayout).updateVisibility(true);
        verify(mMockLetterboxEduLayout).updateVisibility(true);
        verify(mMockRestartDialogLayout).updateVisibility(true);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testRestartLayoutRecreatedIfNeeded() {
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        doReturn(true).when(mMockRestartDialogLayout)
                .needsToBeRecreated(any(TaskInfo.class),
                        any(ShellTaskOrganizer.TaskListener.class));

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockRestartDialogLayout, times(2))
                .createLayout(anyBoolean());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testRestartLayoutNotRecreatedIfNotNeeded() {
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        doReturn(false).when(mMockRestartDialogLayout)
                .needsToBeRecreated(any(TaskInfo.class),
                        any(ShellTaskOrganizer.TaskListener.class));

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mMockRestartDialogLayout, times(1))
                .createLayout(anyBoolean());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testUpdateActiveTaskInfo_newTask_visibleAndFocused_updated() {
        // Simulate user aspect ratio button being shown for previous task
        mController.setHasShownUserAspectRatioSettingsButton(true);
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        // Create new task
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo);

        // Check topActivityTaskId is updated to the taskId of the new task and
        // hasShownUserAspectRatioSettingsButton has been reset to false
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertFalse(mController.hasShownUserAspectRatioSettingsButton());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testUpdateActiveTaskInfo_newTask_notVisibleOrFocused_notUpdated() {
        // Create new task
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true);

        // Simulate task being shown
        mController.updateActiveTaskInfo(taskInfo);

        // Check topActivityTaskId is updated to the taskId of the new task and
        // hasShownUserAspectRatioSettingsButton has been reset to false
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertFalse(mController.hasShownUserAspectRatioSettingsButton());

        // Simulate user aspect ratio button being shown
        mController.setHasShownUserAspectRatioSettingsButton(true);
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        final int newTaskId = TASK_ID + 1;

        // Create visible but NOT focused task
        final TaskInfo taskInfo1 = createTaskInfo(DISPLAY_ID, newTaskId, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ false);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo1);

        // Check topActivityTaskId is NOT updated and hasShownUserAspectRatioSettingsButton
        // remains true
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        // Create focused but NOT visible task
        final TaskInfo taskInfo2 = createTaskInfo(DISPLAY_ID, newTaskId, /* hasSizeCompat= */ true,
                /* isVisible */ false, /* isFocused */ true);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo2);

        // Check topActivityTaskId is NOT updated and hasShownUserAspectRatioSettingsButton
        // remains true
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        // Create NOT focused but NOT visible task
        final TaskInfo taskInfo3 = createTaskInfo(DISPLAY_ID, newTaskId, /* hasSizeCompat= */ true,
                /* isVisible */ false, /* isFocused */ false);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo3);

        // Check topActivityTaskId is NOT updated and hasShownUserAspectRatioSettingsButton
        // remains true
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testUpdateActiveTaskInfo_sameTask_notUpdated() {
        // Create new task
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo);

        // Check topActivityTaskId is updated to the taskId of the new task and
        // hasShownUserAspectRatioSettingsButton has been reset to false
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertFalse(mController.hasShownUserAspectRatioSettingsButton());

        // Simulate user aspect ratio button being shown
        mController.setHasShownUserAspectRatioSettingsButton(true);
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        // Simulate same task being re-shown
        mController.updateActiveTaskInfo(taskInfo);

        // Check topActivityTaskId is NOT updated and hasShownUserAspectRatioSettingsButton
        // remains true
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testUpdateActiveTaskInfo_transparentTask_notUpdated() {
        // Create new task
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo);

        // Check topActivityTaskId is updated to the taskId of the new task and
        // hasShownUserAspectRatioSettingsButton has been reset to false
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertFalse(mController.hasShownUserAspectRatioSettingsButton());

        // Simulate user aspect ratio button being shown
        mController.setHasShownUserAspectRatioSettingsButton(true);
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());

        final int newTaskId = TASK_ID + 1;

        // Create transparent task
        final TaskInfo taskInfo1 = createTaskInfo(DISPLAY_ID, newTaskId, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true, /* isTopActivityTransparent */ true,
                /* isRestartMenuEnabledForDisplayMove */ true);

        // Simulate new task being shown
        mController.updateActiveTaskInfo(taskInfo1);

        // Check topActivityTaskId is NOT updated and hasShownUserAspectRatioSettingsButton
        // remains true
        Assert.assertEquals(TASK_ID, mController.getTopActivityTaskId());
        Assert.assertTrue(mController.hasShownUserAspectRatioSettingsButton());
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testLetterboxEduLayout_notCreatedWhenLetterboxEducationIsDisabled() {
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        taskInfo.appCompatTaskInfo.setLetterboxEducationEnabled(false);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController, never()).createLetterboxEduWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    @EnableFlags(Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE)
    public void testUpdateActiveTaskInfo_removeAllComponentWhenInDesktopModeFlagEnabled() {
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);
        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(false);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController, never()).removeLayouts(taskInfo.taskId);

        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(true);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController).removeLayouts(taskInfo.taskId);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    @EnableFlags(Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE)
    public void testUpdateActiveTaskInfo_alwaysRemoveLetterboxEdu() {
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);

        // When not in Desktop Mode the LetterboxEdu is removed only if the taskId is the one used
        // when created.
        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(false);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.removeLetterboxEdu(TASK_ID_2);
        verify(mMockLetterboxEduLayout, never()).release();

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.removeLetterboxEdu(TASK_ID);
        verify(mMockLetterboxEduLayout).release();

        // When in Desktop Mode the LetterboxEdu is always removed
        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(true);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.removeLetterboxEdu(TASK_ID);
        verify(mMockLetterboxEduLayout).release();

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        mController.removeLetterboxEdu(TASK_ID_2);
        verify(mMockLetterboxEduLayout).release();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    @DisableFlags(Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE)
    public void testUpdateActiveTaskInfo_removeAllComponentWhenInDesktopModeFlagDisabled() {
        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(false);
        TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController, never()).removeLayouts(taskInfo.taskId);

        when(mDesktopUserRepositories.getCurrent().isAnyDeskActive(DISPLAY_ID)).thenReturn(true);

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));

        verify(mController, never()).removeLayouts(taskInfo.taskId);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK)
    public void testSendCompatUIRequest_createRestartDialog() {
        final TaskInfo taskInfo = createTaskInfo(DISPLAY_ID, TASK_ID, /* hasSizeCompat= */ true,
                /* isVisible */ true, /* isFocused */ true, /* isTopActivityTransparent */ false,
                /* isRestartMenuEnabledForDisplayMove */ true);
        doReturn(true).when(mCompatUIConfiguration).isRestartDialogEnabled();
        doReturn(true).when(mCompatUIConfiguration).shouldShowRestartDialogAgain(eq(taskInfo));

        mController.onCompatInfoChanged(new CompatUIInfo(taskInfo, mMockTaskListener));
        verify(mController).createRestartDialogWindowManager(any(), eq(taskInfo),
                eq(mMockTaskListener));
        verify(mMockRestartDialogLayout).setRequestRestartDialog(false);

        mController.sendCompatUIRequest(
                new CompatUIRequests.DisplayCompatShowRestartDialog(taskInfo.taskId));
        verify(mMockRestartDialogLayout).setRequestRestartDialog(true);
    }

    private static TaskInfo createTaskInfo(int displayId, int taskId, boolean hasSizeCompat) {
        return createTaskInfo(displayId, taskId, hasSizeCompat, /* isVisible */ false,
                /* isFocused */ false, /* isTopActivityTransparent */ false,
                /* isRestartMenuEnabledForDisplayMove */ false);
    }

    private static TaskInfo createTaskInfo(int displayId, int taskId, boolean hasSizeCompat,
            boolean isVisible, boolean isFocused) {
        return createTaskInfo(displayId, taskId, hasSizeCompat,
                isVisible, isFocused, /* isTopActivityTransparent */ false,
                /* isRestartMenuEnabledForDisplayMove */ false);
    }

    private static TaskInfo createTaskInfo(int displayId, int taskId, boolean hasSizeCompat,
            boolean isVisible, boolean isFocused, boolean isTopActivityTransparent,
            boolean isRestartMenuEnabledForDisplayMove) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.displayId = displayId;
        taskInfo.appCompatTaskInfo.setTopActivityInSizeCompat(hasSizeCompat);
        taskInfo.isVisible = isVisible;
        taskInfo.isFocused = isFocused;
        taskInfo.isTopActivityTransparent = isTopActivityTransparent;
        taskInfo.appCompatTaskInfo.setLetterboxEducationEnabled(true);
        taskInfo.appCompatTaskInfo.setTopActivityLetterboxed(true);
        taskInfo.appCompatTaskInfo.setRestartMenuEnabledForDisplayMove(
                isRestartMenuEnabledForDisplayMove);
        return taskInfo;
    }
}
