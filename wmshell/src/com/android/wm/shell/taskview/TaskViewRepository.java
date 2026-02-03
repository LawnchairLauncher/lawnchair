/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.taskview;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;

import android.annotation.Nullable;
import android.graphics.Rect;
import android.window.WindowContainerToken;

import com.android.internal.protolog.ProtoLog;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Keeps track of all TaskViews known by Shell. This is separated into its own object so that
 * different TaskView managers can share state.
 */
public class TaskViewRepository {
    /**
     * The latest visibility and bounds state that has been requested for
     * a {@link TaskViewTaskController}.
     */
    public static class TaskViewState {
        final WeakReference<TaskViewTaskController> mTaskView;
        public boolean mVisible;
        public Rect mBounds = new Rect();

        TaskViewState(TaskViewTaskController taskView) {
            mTaskView = new WeakReference<>(taskView);
        }

        @Nullable public TaskViewTaskController getTaskView() {
            return mTaskView.get();
        }
    }

    /**
     * List of tracked TaskViews
     */
    private final ArrayList<TaskViewState> mTaskViews = new ArrayList<>();

    private int findAndPrune(TaskViewTaskController tv) {
        for (int i = mTaskViews.size() - 1; i >= 0; --i) {
            final TaskViewTaskController key = mTaskViews.get(i).mTaskView.get();
            if (key == null) {
                mTaskViews.remove(i);
                continue;
            }
            if (key != tv) continue;
            return i;
        }
        return -1;
    }

    /** @return if the repository is tracking {@param tv}. */
    public boolean contains(TaskViewTaskController tv) {
        return findAndPrune(tv) >= 0;
    }

    /** Start tracking {@param tv}. */
    public void add(TaskViewTaskController tv) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Repo.add(): taskView=%d", tv.hashCode());
        if (contains(tv)) return;
        mTaskViews.add(new TaskViewState(tv));
    }

    /** Remove {@param tv} from tracking. */
    public void remove(TaskViewTaskController tv) {
        ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Repo.remove(): taskView=%d", tv.hashCode());
        int idx = findAndPrune(tv);
        if (idx < 0) return;
        mTaskViews.remove(idx);
    }

    /** @return whether there are any TaskViews */
    public boolean isEmpty() {
        if (mTaskViews.isEmpty()) return true;
        for (int i = mTaskViews.size() - 1; i >= 0; --i) {
            if (mTaskViews.get(i).mTaskView.get() != null) continue;
            mTaskViews.remove(i);
        }
        return mTaskViews.isEmpty();
    }

    /** @return the state of {@param tv} if tracked, {@code null} otherwise. */
    @Nullable
    public TaskViewState byTaskView(TaskViewTaskController tv) {
        int idx = findAndPrune(tv);
        if (idx < 0) return null;
        return mTaskViews.get(idx);
    }

    /**
     * @return the state of the taskview containing {@param token} if tracked,
     *         {@code null} otherwise.
     */
    @Nullable
    public TaskViewState byToken(WindowContainerToken token) {
        for (int i = mTaskViews.size() - 1; i >= 0; --i) {
            final TaskViewTaskController key = mTaskViews.get(i).mTaskView.get();
            if (key == null) {
                mTaskViews.remove(i);
                continue;
            }
            if (key.getTaskInfo() == null) continue;
            if (key.getTaskInfo().token.equals(token)) {
                return mTaskViews.get(i);
            }
        }
        return null;
    }

    /** Dumps TaskViewRepository state. */
    public void dump(PrintWriter pw, String prefix) {
        pw.print(prefix); pw.println("TaskViewRepository state:");
        pw.print(prefix); pw.println("  task view count: " + mTaskViews.size());
        for (TaskViewState taskViewState : mTaskViews) {
            pw.print(prefix); pw.println("    task view: " + taskViewState.getTaskView());
            pw.print(prefix); pw.println("      is visible: " + taskViewState.mVisible);
            pw.print(prefix); pw.println("      bounds: " + taskViewState.mBounds);
        }
    }
}
