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
package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_APP_EDU;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;

import java.io.PrintWriter;

/** Handles the Taskbar Education flow. */
public class TaskbarEduController implements TaskbarControllers.LoggableTaskbarController {

    private final TaskbarActivityContext mActivity;

    // Initialized in init.
    TaskbarControllers mControllers;

    private TaskbarEduView mTaskbarEduView;

    public TaskbarEduController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    void showEdu() {
        TaskbarOverlayController overlayController = mControllers.taskbarOverlayController;
        TaskbarOverlayContext overlayContext = overlayController.requestWindow();
        mTaskbarEduView = (TaskbarEduView) overlayContext.getLayoutInflater().inflate(
                R.layout.taskbar_edu, overlayContext.getDragLayer(), false);
        mTaskbarEduView.init(new TaskbarEduCallbacks());
        mControllers.navbarButtonsViewController.setSlideInViewVisible(true);

        TaskbarStashController stashController = mControllers.taskbarStashController;
        stashController.updateStateForFlag(FLAG_STASHED_IN_APP_EDU, true);
        stashController.applyState(overlayController.getOpenDuration());

        mTaskbarEduView.setOnCloseBeginListener(() -> {
            mControllers.navbarButtonsViewController.setSlideInViewVisible(false);
            // Post in case view is closing due to gesture navigation. If a gesture is in progress,
            // wait to unstash until after the gesture is finished.
            MAIN_EXECUTOR.post(() -> stashController.resetFlagIfNoGestureInProgress(
                    FLAG_STASHED_IN_APP_EDU));
        });
        mTaskbarEduView.addOnCloseListener(() -> mTaskbarEduView = null);
        mTaskbarEduView.show();
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarEduController:");
        pw.println(prefix + "\tisShowingEdu=" + (mTaskbarEduView != null));
    }

    /**
     * Callbacks for {@link TaskbarEduView} to interact with its controller.
     */
    class TaskbarEduCallbacks {
        void onPageChanged(int currentPage, int pageCount) {
            if (currentPage == 0) {
                mTaskbarEduView.updateStartButton(R.string.taskbar_edu_close,
                        v -> mTaskbarEduView.close(true /* animate */));
            } else {
                mTaskbarEduView.updateStartButton(R.string.taskbar_edu_previous,
                        v -> mTaskbarEduView.snapToPage(currentPage - 1));
            }
            if (currentPage == pageCount - 1) {
                mTaskbarEduView.updateEndButton(R.string.taskbar_edu_done,
                        v -> mTaskbarEduView.close(true /* animate */));
            } else {
                mTaskbarEduView.updateEndButton(R.string.taskbar_edu_next,
                        v -> mTaskbarEduView.snapToPage(currentPage + 1));
            }
        }

        int getIconLayoutBoundsWidth() {
            return mControllers.taskbarViewController.getIconLayoutWidth();
        }

        int getOpenDuration() {
            return mControllers.taskbarOverlayController.getOpenDuration();
        }

        int getCloseDuration() {
            return mControllers.taskbarOverlayController.getCloseDuration();
        }
    }
}
