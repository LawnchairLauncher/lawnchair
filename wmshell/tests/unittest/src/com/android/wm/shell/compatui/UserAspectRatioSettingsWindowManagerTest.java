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

package com.android.wm.shell.compatui;

import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_LAUNCHER;
import static android.hardware.usb.UsbManager.ACTION_USB_STATE;
import static android.view.WindowInsets.Type.navigationBars;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;
import android.util.Pair;
import android.view.DisplayInfo;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController.CompatUIHintsState;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Tests for {@link UserAspectRatioSettingsWindowManager}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:UserAspectRatioSettingsWindowManagerTest
 */
@RunWith(AndroidTestingRunner.class)
@RunWithLooper
@SmallTest
public class UserAspectRatioSettingsWindowManagerTest extends ShellTestCase {

    private static final int TASK_ID = 1;

    @Mock private SyncTransactionQueue mSyncTransactionQueue;
    @Mock
    private Supplier<Boolean> mUserAspectRatioButtonShownChecker;
    @Mock
    private BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener>
            mOnUserAspectRatioSettingsButtonClicked;
    @Mock private ShellTaskOrganizer.TaskListener mTaskListener;
    @Mock private UserAspectRatioSettingsLayout mLayout;
    @Mock private SurfaceControlViewHost mViewHost;
    @Captor
    private ArgumentCaptor<ShellTaskOrganizer.TaskListener> mUserAspectRatioTaskListenerCaptor;
    @Captor
    private ArgumentCaptor<TaskInfo> mUserAspectRationTaskInfoCaptor;

    private final Set<String> mPackageNameCache = new HashSet<>();

    private UserAspectRatioSettingsWindowManager mWindowManager;
    private TaskInfo mTaskInfo;

    private TestShellExecutor mExecutor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mExecutor = new TestShellExecutor();
        mTaskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                false, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
        final DisplayInfo displayInfo = new DisplayInfo();
        final int displayWidth = 1000;
        final int displayHeight = 1200;
        displayInfo.logicalWidth = displayWidth;
        displayInfo.logicalHeight = displayHeight;
        final DisplayLayout displayLayout = new DisplayLayout(displayInfo,
                mContext.getResources(), /* hasNavigationBar= */ true, /* hasStatusBar= */ false);
        InsetsState insetsState = new InsetsState();
        insetsState.setDisplayFrame(new Rect(0, 0, displayWidth, displayHeight));
        InsetsSource insetsSource = new InsetsSource(
                InsetsSource.createId(null, 0, navigationBars()), navigationBars());
        insetsSource.setFrame(0, displayHeight - 200, displayWidth, displayHeight);
        insetsState.addSource(insetsSource);
        displayLayout.setInsets(mContext.getResources(), insetsState);
        mWindowManager = new UserAspectRatioSettingsWindowManager(mContext, mTaskInfo,
                mSyncTransactionQueue, mTaskListener, displayLayout, new CompatUIHintsState(),
                mOnUserAspectRatioSettingsButtonClicked, mExecutor, flags -> 0,
                mUserAspectRatioButtonShownChecker, s -> {});
        spyOn(mWindowManager);
        doReturn(mLayout).when(mWindowManager).inflateLayout();
        doReturn(mViewHost).when(mWindowManager).createSurfaceViewHost();
        doReturn(false).when(mUserAspectRatioButtonShownChecker).get();
    }

    @Test
    public void testCreateUserAspectRatioButton() {
        // Doesn't create layout if show is false.
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        assertTrue(mWindowManager.createLayout(/* canShow= */ false));

        verify(mWindowManager, never()).inflateLayout();

        // Doesn't create hint popup.
        mWindowManager.mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint = true;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        mExecutor.flushAll();
        verify(mLayout).setUserAspectRatioButtonVisibility(/* show= */ true);
        verify(mLayout, never()).setUserAspectRatioSettingsHintVisibility(/* show= */ true);

        // Creates hint popup.
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        mWindowManager.release();
        mWindowManager.mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint = false;
        assertTrue(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager).inflateLayout();
        assertNotNull(mLayout);
        mExecutor.flushAll();
        verify(mLayout).setUserAspectRatioButtonVisibility(/* show= */ true);
        verify(mLayout).setUserAspectRatioSettingsHintVisibility(/* show= */ true);
        assertTrue(mWindowManager.mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint);

        // Returns false and doesn't create layout if mHasUserAspectRatioSettingsButton is false.
        clearInvocations(mWindowManager);
        mWindowManager.release();
        mWindowManager.mHasUserAspectRatioSettingsButton = false;
        assertFalse(mWindowManager.createLayout(/* canShow= */ true));

        verify(mWindowManager, never()).inflateLayout();
    }

    @Test
    public void testRelease() {
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        mWindowManager.createLayout(/* canShow= */ true);

        verify(mWindowManager).inflateLayout();

        mWindowManager.release();

        verify(mViewHost).release();
    }

    @Test
    public void testUpdateCompatInfo() {
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        mWindowManager.createLayout(/* canShow= */ true);

        // No diff
        clearInvocations(mWindowManager);
        TaskInfo taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
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

        // Change has eligibleForUserAspectRatioButton to false, dispose the component
        clearInvocations(mWindowManager);
        clearInvocations(mLayout);
        taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                false, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
        assertFalse(
                mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));
        verify(mWindowManager).release();

        // Recreate button
        clearInvocations(mWindowManager);
        taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
        assertTrue(mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));

        verify(mWindowManager).release();
        verify(mWindowManager).createLayout(/* canShow= */ true);

        // Change has no launcher category and is not main intent, dispose the component
        clearInvocations(mWindowManager);
        taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_USB_STATE, "");
        assertFalse(
                mWindowManager.updateCompatInfo(taskInfo, newTaskListener, /* canShow= */ true));
        verify(mWindowManager).release();
    }

    @Test
    public void testUpdateCompatInfoLayoutNotInflatedYet() {
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        mWindowManager.createLayout(/* canShow= */ false);

        verify(mWindowManager, never()).inflateLayout();

        // Change topActivityInSizeCompat to false and pass canShow true, layout shouldn't be
        // inflated
        clearInvocations(mWindowManager);
        TaskInfo taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                false, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager, never()).inflateLayout();

        // Change topActivityInSizeCompat to true and pass canShow true, layout should be inflated.
        clearInvocations(mWindowManager);
        taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager).inflateLayout();
    }

    @Test
    public void testEligibleButtonHiddenIfLetterboxBoundsEqualToStableBounds() {
        TaskInfo taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);

        final Rect stableBounds = mWindowManager.getTaskStableBounds();
        final int stableHeight = stableBounds.height();

        // Letterboxed activity bounds equal to stable bounds, layout shouldn't be inflated
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = stableHeight;
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = stableBounds.width();

        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager, never()).inflateLayout();

        // Letterboxed activity bounds smaller than stable bounds, layout should be inflated
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = stableHeight - 100;

        clearInvocations(mWindowManager);
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager).inflateLayout();
    }

    @Test
    public void testUserFullscreenOverrideEnabled_buttonAlwaysShown() {
        TaskInfo taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);

        final Rect stableBounds = mWindowManager.getTaskStableBounds();

        // Letterboxed activity that has user fullscreen override should always show button,
        // layout should be inflated
        taskInfo.appCompatTaskInfo.topActivityLetterboxHeight = stableBounds.height();
        taskInfo.appCompatTaskInfo.topActivityLetterboxWidth = stableBounds.width();
        taskInfo.appCompatTaskInfo.isUserFullscreenOverrideEnabled = true;

        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);

        verify(mWindowManager).inflateLayout();
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
        InsetsState insetsState = new InsetsState();
        insetsState.setDisplayFrame(new Rect(0, 0, 1000, 2000));
        InsetsSource insetsSource = new InsetsSource(
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
        mWindowManager.removeLayout();
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
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
    public void testLayoutHasUserAspectRatioSettingsButton() {
        clearInvocations(mWindowManager);
        spyOn(mWindowManager);
        TaskInfo taskInfo = createTaskInfo(/* eligibleForUserAspectRatioButton= */
                true, /* topActivityBoundsLetterboxed */ true, ACTION_MAIN, CATEGORY_LAUNCHER);

        // User aspect ratio settings button has not yet been shown.
        doReturn(false).when(mUserAspectRatioButtonShownChecker).get();

        // Check the layout has the user aspect ratio settings button.
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);
        assertTrue(mWindowManager.mHasUserAspectRatioSettingsButton);

        // User aspect ratio settings button has been shown and is still visible.
        spyOn(mWindowManager);
        doReturn(true).when(mWindowManager).isShowingButton();
        doReturn(true).when(mUserAspectRatioButtonShownChecker).get();

        // Check the layout still has the user aspect ratio settings button.
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);
        assertTrue(mWindowManager.mHasUserAspectRatioSettingsButton);

        // User aspect ratio settings button has been shown and has timed out so is no longer
        // visible.
        doReturn(false).when(mWindowManager).isShowingButton();
        doReturn(true).when(mUserAspectRatioButtonShownChecker).get();

        // Check the layout no longer has the user aspect ratio button.
        mWindowManager.updateCompatInfo(taskInfo, mTaskListener, /* canShow= */ true);
        assertFalse(mWindowManager.mHasUserAspectRatioSettingsButton);
    }

    @Test
    public void testAttachToParentSurface() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        mWindowManager.attachToParentSurface(b);

        verify(mTaskListener).attachChildSurfaceToTask(TASK_ID, b);
    }

    @Test
    public void testOnUserAspectRatioButtonClicked() {
        mWindowManager.onUserAspectRatioSettingsButtonClicked();

        verify(mOnUserAspectRatioSettingsButtonClicked).accept(
                mUserAspectRationTaskInfoCaptor.capture(),
                mUserAspectRatioTaskListenerCaptor.capture());
        final Pair<TaskInfo, ShellTaskOrganizer.TaskListener> result =
                new Pair<>(mUserAspectRationTaskInfoCaptor.getValue(),
                        mUserAspectRatioTaskListenerCaptor.getValue());
        Assert.assertEquals(mTaskInfo, result.first);
        Assert.assertEquals(mTaskListener, result.second);
    }

    @Test
    public void testOnUserAspectRatioButtonLongClicked_showHint() {
       // Not create hint popup.
        mWindowManager.mHasUserAspectRatioSettingsButton = true;
        mWindowManager.mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint = true;
        mWindowManager.createLayout(/* canShow= */ true);

        verify(mWindowManager).inflateLayout();
        verify(mLayout, never()).setUserAspectRatioSettingsHintVisibility(/* show= */ true);

        mWindowManager.onUserAspectRatioSettingsButtonLongClicked();

        verify(mLayout).setUserAspectRatioSettingsHintVisibility(/* show= */ true);
    }

    @Test
    public void testWhenDockedStateHasChanged_needsToBeRecreated() {
        ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        newTaskInfo.configuration.uiMode |= Configuration.UI_MODE_TYPE_DESK;

        Assert.assertTrue(mWindowManager.needsToBeRecreated(newTaskInfo, mTaskListener));
    }

    private static TaskInfo createTaskInfo(boolean eligibleForUserAspectRatioButton,
            boolean topActivityBoundsLetterboxed, String action, String category) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = TASK_ID;
        taskInfo.appCompatTaskInfo.topActivityEligibleForUserAspectRatioButton =
                eligibleForUserAspectRatioButton;
        taskInfo.appCompatTaskInfo.topActivityBoundsLetterboxed = topActivityBoundsLetterboxed;
        taskInfo.configuration.uiMode &= ~Configuration.UI_MODE_TYPE_DESK;
        taskInfo.realActivity = new ComponentName("com.mypackage.test", "TestActivity");
        taskInfo.baseIntent = new Intent(action).addCategory(category);
        return taskInfo;
    }
}
