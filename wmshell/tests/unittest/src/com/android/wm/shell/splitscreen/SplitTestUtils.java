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

package com.android.wm.shell.splitscreen;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.app.IActivityTaskManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.SurfaceControl;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.desktopmode.DesktopTasksController;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import com.google.android.msdl.domain.MSDLPlayer;

import java.util.Objects;
import java.util.Optional;

public class SplitTestUtils {

    static SplitLayout createMockSplitLayout() {
        final Rect dividerBounds = new Rect(48, 0, 52, 100);
        final Rect bounds1 = new Rect(0, 0, 40, 100);
        final Rect bounds2 = new Rect(60, 0, 100, 100);
        final Rect rootBounds = new Rect(0, 0, 100, 100);
        final SurfaceControl leash = createMockSurface();
        SplitLayout out = mock(SplitLayout.class);
        doReturn(dividerBounds).when(out).getDividerBounds();
        doReturn(dividerBounds).when(out).getRefDividerBounds();
        doReturn(leash).when(out).getDividerLeash();
        doReturn(bounds1).when(out).getTopLeftBounds();
        doReturn(bounds2).when(out).getBottomRightBounds();
        doReturn(rootBounds).when(out).getRootBounds();
        doReturn(SNAP_TO_2_50_50).when(out).calculateCurrentSnapPosition();
        return out;
    }

    static SurfaceControl createMockSurface() {
        return createMockSurface(true);
    }

    static SurfaceControl createMockSurface(boolean valid) {
        SurfaceControl sc = mock(SurfaceControl.class);
        ExtendedMockito.doReturn(valid).when(sc).isValid();
        return sc;
    }

    static class TestStageCoordinator extends StageCoordinator {
        final SurfaceControl mRootLeash;
        final SplitMultiDisplayHelper mMultiDisplayHelper;

        TestStageCoordinator(Context context, int displayId, SyncTransactionQueue syncQueue,
                ShellTaskOrganizer taskOrganizer, StageTaskListener mainStage,
                StageTaskListener sideStage, DisplayController displayController,
                DisplayImeController imeController, DisplayInsetsController insetsController,
                SplitLayout splitLayout, Transitions transitions, TransactionPool transactionPool,
                ShellExecutor mainExecutor, Handler mainHandler,
                Optional<RecentTasksController> recentTasks,
                LaunchAdjacentController launchAdjacentController,
                Optional<WindowDecorViewModel> windowDecorViewModel, SplitState splitState,
                Optional<DesktopTasksController> desktopTasksController,
                Optional<DesktopUserRepositories> desktopUserRepositories,
                RootTaskDisplayAreaOrganizer rootTDAOrganizer,
                RootDisplayAreaOrganizer rootDisplayAreaOrganizer, DesktopState desktopState,
                IActivityTaskManager activityTaskManager, MSDLPlayer msdlPlayer) {
            super(context, displayId, syncQueue, taskOrganizer, mainStage,
                    sideStage, displayController, imeController, insetsController, splitLayout,
                    transitions, transactionPool, mainExecutor, mainHandler, recentTasks,
                    launchAdjacentController, windowDecorViewModel, splitState,
                    desktopTasksController, desktopUserRepositories, rootTDAOrganizer,
                    rootDisplayAreaOrganizer,
                    desktopState, activityTaskManager, msdlPlayer);

            // Prepare root task for testing.
            mRootLeash = new SurfaceControl.Builder().setName("test").build();
            DisplayManager displayManager = context.getSystemService(DisplayManager.class);
            mMultiDisplayHelper = new SplitMultiDisplayHelper(
                    Objects.requireNonNull(displayManager));
            mMultiDisplayHelper.setDisplayRootTaskInfo(
                    DEFAULT_DISPLAY, new TestRunningTaskInfoBuilder().build());
            onTaskAppeared(mMultiDisplayHelper.getDisplayRootTaskInfo(
                    DEFAULT_DISPLAY), mRootLeash);
        }
    }
}
