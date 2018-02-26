/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import android.graphics.Rect;

import com.android.launcher3.Utilities;

/**
 * Helper class to interpolate the animation between a task view representation and an actual
 * window.
 */
public class RecentsAnimationInterpolator {

    public static class TaskWindowBounds {
        public float taskScale = 1f;
        public float taskX = 0f;
        public float taskY = 0f;

        public float winScale = 1f;
        public float winX = 0f;
        public float winY = 0f;
        public Rect winCrop = new Rect();

        @Override
        public String toString() {
            return "taskScale=" + taskScale + " taskX=" + taskX + " taskY=" + taskY
                    + " winScale=" + winScale + " winX=" + winX + " winY=" + winY
                    + " winCrop=" + winCrop;
        }
    }

    private TaskWindowBounds mTmpTaskWindowBounds = new TaskWindowBounds();
    private Rect mTmpInsets = new Rect();

    private Rect mWindow;
    private Rect mInsetWindow;
    private Rect mInsets;
    private Rect mTask;
    private Rect mTaskInsets;
    private Rect mThumbnail;

    private float mInitialTaskScale;
    private float mInitialTaskTranslationX;
    private float mFinalTaskScale;
    private Rect mScaledTask;
    private Rect mTargetTask;
    private Rect mSrcWindow;

    public RecentsAnimationInterpolator(Rect window, Rect insets, Rect task, Rect taskInsets,
            float taskScale, float taskTranslationX) {
        mWindow = window;
        mInsets = insets;
        mTask = task;
        mTaskInsets = taskInsets;
        mInsetWindow = new Rect(window);
        Utilities.insetRect(mInsetWindow, insets);

        mThumbnail = new Rect(task);
        Utilities.insetRect(mThumbnail, taskInsets);
        mInitialTaskScale = taskScale;
        mInitialTaskTranslationX = taskTranslationX;
        mFinalTaskScale = (float) mInsetWindow.width() / mThumbnail.width();
        mScaledTask = new Rect(task);
        Utilities.scaleRectAboutCenter(mScaledTask, mFinalTaskScale);
        Rect finalScaledTaskInsets = new Rect(taskInsets);
        Utilities.scaleRect(finalScaledTaskInsets, mFinalTaskScale);
        mTargetTask = new Rect(mInsetWindow);
        mTargetTask.offsetTo(window.top + insets.top - finalScaledTaskInsets.top,
                window.left + insets.left - finalScaledTaskInsets.left);

        float initialWinScale = 1f / mFinalTaskScale;
        Rect scaledWindow = new Rect(mInsetWindow);
        Utilities.scaleRectAboutCenter(scaledWindow, initialWinScale);
        Rect scaledInsets = new Rect(insets);
        Utilities.scaleRect(scaledInsets, initialWinScale);
        mSrcWindow = new Rect(scaledWindow);
        mSrcWindow.offsetTo(mThumbnail.left - scaledInsets.left,
                mThumbnail.top - scaledInsets.top);
    }

    public TaskWindowBounds interpolate(float t) {
        mTmpTaskWindowBounds.taskScale = Utilities.mapRange(t,
                mInitialTaskScale, mFinalTaskScale);
        mTmpTaskWindowBounds.taskX = Utilities.mapRange(t,
                mInitialTaskTranslationX, mTargetTask.left - mScaledTask.left);
        mTmpTaskWindowBounds.taskY = Utilities.mapRange(t,
                0, mTargetTask.top - mScaledTask.top);

        float taskScale = Utilities.mapRange(t, 1, mFinalTaskScale);
        mTmpTaskWindowBounds.winScale = taskScale / mFinalTaskScale;
        mTmpTaskWindowBounds.winX = Utilities.mapRange(t,
                mSrcWindow.left, 0);
        mTmpTaskWindowBounds.winY = Utilities.mapRange(t,
                mSrcWindow.top, 0);

        mTmpInsets.set(mInsets);
        Utilities.scaleRect(mTmpInsets, (1f - t));
        mTmpTaskWindowBounds.winCrop.set(mWindow);
        Utilities.insetRect(mTmpTaskWindowBounds.winCrop, mTmpInsets);

        return mTmpTaskWindowBounds;
    }
}
