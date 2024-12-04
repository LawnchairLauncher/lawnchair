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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_DISMISSED;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_HIDDEN;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.CameraCompatTaskInfo;
import android.app.TaskInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.util.Pair;
import android.view.DisplayInfo;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController.CompatUIHintsState;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

/**
 * Tests for {@link CompatUIWindowManager}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:CompatUIWindowManagerTest
 */
@RunWith(AndroidTestingRunner.class)
@SmallTest
public class CompatUIWindowManagerTest extends ShellTestCase {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);

    private static final int TASK_ID = 1;
    private static final int TASK_WIDTH = 2000;
    private static final int TASK_HEIGHT = 2000;

    @Mock private SyncTransactionQueue mSyncTransactionQueue;
    @Mock private CompatUIController.CompatUICallback mCallback;
    @Mock private Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> mOnRestartButtonClicked;
    @Mock private ShellTaskOrganizer.TaskListener mTaskListener;
    @Mock private CompatUILayout mLayout;
    @Mock private SurfaceControlViewHost mViewHost;
    @Mock private CompatUIConfiguration mCompatUIConfiguration;

    private CompatUIWindowManager mWindowManager;
    private TaskInfo mTaskInfo;
    private DisplayLayout mDisplayLayout;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(100).when(mCompatUIConfiguration).getHideSizeCompatRestartButtonTolerance();
        mTaskInfo = createTaskInfo(/* hasSizeCompat= */ false, CAMERA_COMPAT_CONTROL_HIDDEN);

        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = TASK_WIDTH;
        displayInfo.logicalHeight = TASK_HEIGHT;
        mDisplayLayout = new DisplayLayout(displayInfo,
                mContext.getResources(), /* hasNavigationBar= */ true, /* hasStatusBar= */ false);
        final InsetsState insetsState = new InsetsState();
        insetsState.setDisplayFrame(new Rect(0, 0, TASK_WIDTH, TASK_HEIGHT));
        final InsetsSource insetsSource = new InsetsSource(
                InsetsSource.createId(null, 0, navigationBars()), navigationBars());
        insetsSource.setFrame(0, TASK_HEIGHT - 200, TASK_WIDTH, TASK_HEIGHT);
        insetsState.addSource(insetsSource);
        mDisplayLayout.setInsets(mContext.getResources(), insetsState);
        mWindowManager = new CompatUIWindowManager(mContext, mTaskInfo, mSyncTransactionQueue,
                mCallback, mTaskListener, mDisplayLayout, new CompatUIHintsState(),
                mCompatUIConfiguration, mOnRestartButtonClicked);

        spyOn(mWindowManager);
        doReturn(mLayout).when(mWindowManager).inflateLayout();
        doReturn(mViewHost).when(mWindowManager).createSurfaceViewHost();
    }

    @Test
    public void testCreateSizeCompatButton() {
        // Doesn't create layout if show is false.
        mWindowManager.mHasSizeCompat = true;
        assertTrue(mWindowManager.createLayout(/* canShow= */ false));

        verify(mWindowManager, never()).inflateLayout();

        // Doesn't create hint popup.
        mWindowManager.mCompatUIHintsState.mHasShownSizeCompatHint = true;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        verify(mLayout).setRestartButtonVisibility(/* show= */ true);
        verify(mLayout, never()).setSizeCompatHintVisibility(/* show= */ true);

        // Creates hint popup.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        mWindowManager.release();
        mWindowManager.mCompatUIHintsState.mHasShownSizeCompatHint = false;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        assertNotNull(mLayout);
        verify(mLayout).setRestartButtonVisibility(/* show= */ true);
        verify(mLayout).setSizeCompatHintVisibility(/* show= */ true);
        assertTrue(mWindowManager.mCompatUIHintsState.mHasShownSizeCompatHint);

        // Returns false and doesn't create layout if has Size Compat is false.
        clearInvocations(mWindowManager);
        mWindowManager.release();
        mWindowManager.mHasSizeCompat = false;
        assertFalse(mWindowManager.createLayout(/* canShow= */ true));

        // Returns false and doesn't create layout if restart button should be hidden.
        clearInvocations(mWindowManager);
        mWindowManager.mHasSizeCompat = true;
        mTaskInfo.appCompatTaskInfo.topActivityLetterboxWidth = TASK_WIDTH;
        mTaskInfo.appCompatTaskInfo.topActivityLetterboxHeight = TASK_HEIGHT;
        assertFalse(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager, never()).inflateLayout();
    }

    @Test
    public void testCreateCameraCompatControl() {
        // Doesn't create layout if show is false.
        mWindowManager.mCameraCompatControlState = CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        assertTrue(mWindowManager.createLayout(/* canShow= */ false));

        verify(mWindowManager, never()).inflateLayout();

        // Doesn't create hint popup.
        mWindowManager.mCompatUIHintsState.mHasShownCameraCompatHint = true;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        verify(mLayout).setCameraControlVisibility(/* show= */ true);
        verify(mLayout, never()).setCameraCompatHintVisibility(/* show= */ true);

        // Creates hint popup.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        mWindowManager.release();
        mWindowManager.mCompatUIHintsState.mHasShownCameraCompatHint = false;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        assertNotNull(mLayout);
        verify(mLayout).setCameraControlVisibility(/* show= */ true);
        verify(mLayout).setCameraCompatHintVisibility(/* show= */ true);
        assertTrue(mWindowManager.mCompatUIHintsState.mHasShownCameraCompatHint);

        // Returns false and doesn't create layout if Camera Compat state is hidden
        clearInvocations(mWindowManager);
        mWindowManager.release();
        mWindowManager.mCameraCompatControlState = CAMERA_COMPAT_CONTROL_HIDDEN;
        assertFalse(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager, never()).inflateLayout();
    }

    @Test
    public void testRelease() {
        mWindowManager.mHasSizeCompat = true;
        mWindowManager.createLayout(/* canShow= */ true);

        verify(mWindowManager).inflateLayout();

        mWindowManager.release();

        verify(mViewHost).release();
    }

    @Test
    public void testUpdateCompatInfo() {
        mWindowManager.mHasSizeCompat = true;
        mWindowManager.createLayout(/* canShow= */ true);

        // No diff
        clearInvocations(mWindowManager);
        TaskInfo taskInfo = createTaskInfo(/* hasSizeCompat= */ true, CAMERA_COMPAT_CONTROL_HIDDEN);
        doReturn(true).when(mWindowManager).shouldShowSizeCompatRestartButton(any());
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true));

        verify(mWindowManager, never()).updateSurfacePosition();
        verify(mWindowManager, never()).release();
        verify(mWindowManager, never()).createLayout(anyBoolean());

        // Change task listener, recreate button.
        clearInvocations(mWindowManager);
        final ShellTaskOrganizer.TaskListener newTaskListener = mock(
                ShellTaskOrganizer.TaskListener.class);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mWindowManager).release();
        verify(mWindowManager).createLayout(/* canShow= */ true);

        // Change Camera Compat state, show a control.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true,
                CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mLayout).setCameraControlVisibility(/* show= */ true);
        verify(mLayout).updateCameraTreatmentButton(
                CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED);

        // Change Camera Compat state, update a control.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true,
                CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mLayout).setCameraControlVisibility(/* show= */ true);
        verify(mLayout).updateCameraTreatmentButton(
                CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);

        // Change has Size Compat to false, hides restart button.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ false,
                CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mLayout).setRestartButtonVisibility(/* show= */ false);

        // Change has Size Compat to true, shows restart button.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true,
                CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mLayout).setRestartButtonVisibility(/* show= */ true);

        // Change Camera Compat state to dismissed, hide a control.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true, CAMERA_COMPAT_CONTROL_DISMISSED);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mLayout).setCameraControlVisibility(/* show= */ false);

        // Change task bounds, update position.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true, CAMERA_COMPAT_CONTROL_HIDDEN);
        taskInfo.configuration.windowConfiguration.setBounds(new Rect(0, 1000, 0, 2000));
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mWindowManager).updateSurfacePosition();

        // Change has Size Compat to false, release layout.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ false, CAMERA_COMPAT_CONTROL_HIDDEN);
        assertFalse(
                mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mWindowManager).release();
    }

    @Test
    public void testUpdateCompatInfoLayoutNotInflatedYet() {
        mWindowManager.createLayout(/* canShow= */ false);

        verify(mWindowManager, never()).inflateLayout();

        // Change topActivityInSizeCompat to false and pass canShow true, layout shouldn't be
        // inflated
        clearInvocations(mWindowManager);
        TaskInfo taskInfo = createTaskInfo(/* hasSizeCompat= */ false,
                CAMERA_COMPAT_CONTROL_HIDDEN);
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager, never()).inflateLayout();

        // Change topActivityInSizeCompat to true and pass canShow true, layout should be inflated.
        clearInvocations(mWindowManager);
        taskInfo = createTaskInfo(/* hasSizeCompat= */ true, CAMERA_COMPAT_CONTROL_HIDDEN);
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager).inflateLayout();

        // Change shouldShowSizeCompatRestartButton to false and pass canShow true, layout
        // shouldn't be inflated
        clearInvocations(mWindowManager);
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = TASK_WIDTH;
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = TASK_HEIGHT;
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager, never()).inflateLayout();
    }

    @Test
    public void testUpdateDisplayLayout() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = 1000;
        displayInfo.logicalHeight = 2000;
        final DisplayLayout displayLayout1 = new DisplayLayout(displayInfo,
                mContext.getResources(), /* hasNavigationBar= */ false, /* hasStatusBar= */ false);

        mWindowManager.updateDisplayLayout(displayLayout1);
        verify(mWindowManager).updateSurfacePosition();

        // No update if the display bounds is the same.
        clearInvocations(mWindowManager);
        final DisplayLayout displayLayout2 = new DisplayLayout(displayInfo,
                mContext.getResources(), /* hasNavigationBar= */ false, /* hasStatusBar= */ false);
        mWindowManager.updateDisplayLayout(displayLayout2);
        verify(mWindowManager, never()).updateSurfacePosition();
    }

    @Test
    public void testUpdateDisplayLayoutInsets() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = 1000;
        displayInfo.logicalHeight = 2000;
        final DisplayLayout displayLayout = new DisplayLayout(displayInfo,
                mContext.getResources(), /* hasNavigationBar= */ true, /* hasStatusBar= */ false);

        mWindowManager.updateDisplayLayout(displayLayout);
        verify(mWindowManager).updateSurfacePosition();

        // Update if the insets change on the existing display layout
        clearInvocations(mWindowManager);
        final InsetsState insetsState = new InsetsState();
        insetsState.setDisplayFrame(new Rect(0, 0, 1000, 2000));
        final InsetsSource insetsSource = new InsetsSource(
                InsetsSource.createId(null, 0, navigationBars()), navigationBars());
        insetsSource.setFrame(0, 1800, 1000, 2000);
        insetsState.addSource(insetsSource);
        displayLayout.setInsets(mContext.getResources(), insetsState);
        mWindowManager.updateDisplayLayout(displayLayout);
        verify(mWindowManager).updateSurfacePosition();
    }

    @Test
    public void testUpdateVisibility() {
        // Create button if it is not created.
        mWindowManager.mLayout = null;
        mWindowManager.mHasSizeCompat = true;
        mWindowManager.updateVisibility(/* canShow= */ true);

        verify(mWindowManager).createLayout(/* canShow= */ true);

        // Hide button.
        clearInvocations(mWindowManager);
        doReturn(View.VISIBLE).when(mLayout).getVisibility();
        mWindowManager.updateVisibility(/* canShow= */ false);

        verify(mWindowManager, never()).createLayout(anyBoolean());
        verify(mLayout).setVisibility(View.GONE);

        // Show button.
        doReturn(View.GONE).when(mLayout).getVisibility();
        mWindowManager.updateVisibility(/* canShow= */ true);

        verify(mWindowManager, never()).createLayout(anyBoolean());
        verify(mLayout).setVisibility(View.VISIBLE);
    }

    @Test
    public void testAttachToParentSurface() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        mWindowManager.attachToParentSurface(b);

        verify(mTaskListener).attachChildSurfaceToTask(TASK_ID, b);
    }

    @Test
    public void testOnCameraDismissButtonClicked() {
        mWindowManager.mCameraCompatControlState = CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mWindowManager.createLayout(/* canShow= */ true);
        clearInvocations(mLayout);
        mWindowManager.onCameraDismissButtonClicked();

        verify(mCallback).onCameraControlStateUpdated(TASK_ID, CAMERA_COMPAT_CONTROL_DISMISSED);
        verify(mLayout).setCameraControlVisibility(/* show= */ false);
    }

    @Test
    public void testOnCameraTreatmentButtonClicked() {
        mWindowManager.mCameraCompatControlState = CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mWindowManager.createLayout(/* canShow= */ true);
        clearInvocations(mLayout);
        mWindowManager.onCameraTreatmentButtonClicked();

        verify(mCallback).onCameraControlStateUpdated(
                TASK_ID, CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED);
        verify(mLayout).updateCameraTreatmentButton(
                CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED);

        mWindowManager.onCameraTreatmentButtonClicked();

        verify(mCallback).onCameraControlStateUpdated(
                TASK_ID, CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);
        verify(mLayout).updateCameraTreatmentButton(
                CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED);
    }

    @Test
    public void testOnRestartButtonClicked() {
        mWindowManager.onRestartButtonClicked();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> restartCaptor =
                ArgumentCaptor.forClass(Pair.class);

        verify(mOnRestartButtonClicked).accept(restartCaptor.capture());
        final Pair<TaskInfo, ShellTaskOrganizer.TaskListener> result = restartCaptor.getValue();
        Assert.assertEquals(mTaskInfo, result.first);
        Assert.assertEquals(mTaskListener, result.second);
    }

    @Test
    public void testOnRestartButtonLongClicked_showHint() {
       // Not create hint popup.
        mWindowManager.mHasSizeCompat = true;
        mWindowManager.mCompatUIHintsState.mHasShownSizeCompatHint = true;
        mWindowManager.createLayout(/* canShow= */ true);

        verify(mWindowManager).inflateLayout();
        verify(mLayout, never()).setSizeCompatHintVisibility(/* show= */ true);

        mWindowManager.onRestartButtonLongClicked();

        verify(mLayout).setSizeCompatHintVisibility(/* show= */ true);
    }

    @Test
    public void testOnCameraControlLongClicked_showHint() {
       // Not create hint popup.
        mWindowManager.mCameraCompatControlState = CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mWindowManager.mCompatUIHintsState.mHasShownCameraCompatHint = true;
        mWindowManager.createLayout(/* canShow= */ true);

        verify(mWindowManager).inflateLayout();
        verify(mLayout, never()).setCameraCompatHintVisibility(/* show= */ true);

        mWindowManager.onCameraButtonLongClicked();

        verify(mLayout).setCameraCompatHintVisibility(/* show= */ true);
    }

    @Test
    public void testWhenDockedStateHasChanged_needsToBeRecreated() {
        ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        newTaskInfo.configuration.uiMode |= Configuration.UI_MODE_TYPE_DESK;

        Assert.assertTrue(mWindowManager.needsToBeRecreated(newTaskInfo, mTaskListener));
    }

    @Test
    public void testShouldShowSizeCompatRestartButton() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ALLOW_HIDE_SCM_BUTTON);
        doReturn(85).when(mCompatUIConfiguration).getHideSizeCompatRestartButtonTolerance();
        mWindowManager = new CompatUIWindowManager(mContext, mTaskInfo, mSyncTransactionQueue,
                mCallback, mTaskListener, mDisplayLayout, new CompatUIHintsState(),
                mCompatUIConfiguration, mOnRestartButtonClicked);

        // Simulate rotation of activity in square display
        TaskInfo taskInfo = createTaskInfo(true, CAMERA_COMPAT_CONTROL_HIDDEN);
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = TASK_HEIGHT;
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = 1850;

        assertFalse(mWindowManager.shouldShowSizeCompatRestartButton(taskInfo));

        // Simulate exiting split screen/folding
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = 1000;
        assertTrue(mWindowManager.shouldShowSizeCompatRestartButton(taskInfo));

        // Simulate folding
        final InsetsState insetsState = new InsetsState();
        insetsState.setDisplayFrame(new Rect(0, 0, 1000, TASK_HEIGHT));
        final InsetsSource insetsSource = new InsetsSource(
                InsetsSource.createId(null, 0, navigationBars()), navigationBars());
        insetsSource.setFrame(0, TASK_HEIGHT - 200, 1000, TASK_HEIGHT);
        insetsState.addSource(insetsSource);
        mDisplayLayout.setInsets(mContext.getResources(), insetsState);
        mWindowManager.updateDisplayLayout(mDisplayLayout);
        taskInfo.configuration.smallestScreenWidthDp = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP - 100;
        assertTrue(mWindowManager.shouldShowSizeCompatRestartButton(taskInfo));

        // Simulate floating app with 90& area, more than tolerance
        taskInfo.configuration.smallestScreenWidthDp = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = 950;
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = 1900;
        assertTrue(mWindowManager.shouldShowSizeCompatRestartButton(taskInfo));
    }

    private static TaskInfo createTaskInfo(boolean hasSizeCompat,
            @CameraCompatTaskInfo.CameraCompatControlState int cameraCompatControlState) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = TASK_ID;
        taskInfo.appCompatTaskInfo.topActivityInSizeCompat = hasSizeCompat;
        taskInfo.appCompatTaskInfo.cameraCompatTaskInfo.cameraCompatControlState =
                cameraCompatControlState;
        taskInfo.configuration.uiMode &= ~Configuration.UI_MODE_TYPE_DESK;
        // Letterboxed activity that takes half the screen should show size compat restart button
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = 1000;
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = 1000;
        // Screen width dp larger than a normal phone.
        taskInfo.configuration.smallestScreenWidthDp = LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
        return taskInfo;
    }
}
