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

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL;
import static com.android.launcher3.AbstractFloatingView.TYPE_TASKBAR_EDUCATION_DIALOG;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.pageindicators.PageIndicatorDots;
import com.android.launcher3.taskbar.TaskbarEduController.TaskbarEduCallbacks;
import com.android.launcher3.views.ActivityContext;

/** Horizontal carousel of tutorial screens for Taskbar Edu. */
public class TaskbarEduPagedView extends PagedView<PageIndicatorDots> {

    private TaskbarEduView mTaskbarEduView;
    private TaskbarEduCallbacks mControllerCallbacks;

    public TaskbarEduPagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void setTaskbarEduView(TaskbarEduView taskbarEduView) {
        mTaskbarEduView = taskbarEduView;
        mPageIndicator = taskbarEduView.findViewById(R.id.content_page_indicator);
        initParentViews(taskbarEduView);
    }

    void setControllerCallbacks(TaskbarEduCallbacks controllerCallbacks) {
        mControllerCallbacks = controllerCallbacks;
        mControllerCallbacks.onPageChanged(getCurrentPage(), getCurrentPage(), getPageCount());
    }

    @Override
    protected int getChildGap(int fromIndex, int toIndex) {
        return mTaskbarEduView.getPaddingLeft() + mTaskbarEduView.getPaddingRight();
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mMaxScroll > 0) {
            mPageIndicator.setScroll(l, mMaxScroll);
        }
    }

    @Override
    protected void notifyPageSwitchListener(int prevPage) {
        super.notifyPageSwitchListener(prevPage);
        mControllerCallbacks.onPageChanged(prevPage, getCurrentPage(), getPageCount());
    }

    @Override
    protected boolean canScroll(float absVScroll, float absHScroll) {
        return AbstractFloatingView.getTopOpenViewWithType(
                ActivityContext.lookupContext(getContext()),
                TYPE_ALL & ~TYPE_TASKBAR_EDUCATION_DIALOG) == null;
    }
}
