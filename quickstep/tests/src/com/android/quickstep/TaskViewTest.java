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

package com.android.quickstep;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;

import com.android.launcher3.statemanager.StatefulActivity;
import com.android.quickstep.util.BorderAnimator;
import com.android.quickstep.views.TaskView;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class TaskViewTest {

    @Mock
    private StatefulActivity mContext;
    @Mock
    private Resources mResource;
    @Mock
    private BorderAnimator mHoverAnimator;
    @Mock
    private BorderAnimator mFocusAnimator;
    private TaskView mTaskView;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mResource.getDisplayMetrics()).thenReturn(mock(DisplayMetrics.class));
        when(mResource.getConfiguration()).thenReturn(new Configuration());

        when(mContext.getResources()).thenReturn(mResource);
        when(mContext.getTheme()).thenReturn(mock(Resources.Theme.class));
        when(mContext.getApplicationInfo()).thenReturn(mock(ApplicationInfo.class));
        when(mContext.obtainStyledAttributes(any(), any(), anyInt(), anyInt())).thenReturn(
                mock(TypedArray.class));

        mTaskView = new TaskView(mContext, null, 0, 0, mFocusAnimator, mHoverAnimator);
    }

    @Test
    public void notShowBorderOnBorderDisabled() {
        mTaskView.setBorderEnabled(/* enabled= */ false);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */ true, /* animated= */
                true);

        mTaskView.onFocusChanged(false, 0, new Rect());
        verify(mFocusAnimator, never()).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
    }

    @Test
    public void showBorderOnBorderEnabled() {
        mTaskView.setBorderEnabled(/* enabled= */ true);
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        verify(mHoverAnimator, times(1)).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
        mTaskView.onFocusChanged(true, 0, new Rect());
        verify(mFocusAnimator, times(1)).setBorderVisibility(/* visible= */ true, /* animated= */
                true);
    }

    @Test
    public void hideBorderOnBorderDisabled() {
        mTaskView.setBorderEnabled(/* enabled= */ false);
        verify(mHoverAnimator, times(1)).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
        verify(mFocusAnimator, times(1)).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
    }

    @Test
    public void notShowBorderByDefault() {
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_ENTER, 0.0f, 0.0f, 0);
        mTaskView.onHoverEvent(MotionEvent.obtain(event));
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
        mTaskView.onFocusChanged(true, 0, new Rect());
        verify(mHoverAnimator, never()).setBorderVisibility(/* visible= */ false, /* animated= */
                true);
    }
}
