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
package com.android.launcher3.taskbar;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS;
import static com.android.launcher3.taskbar.TaskbarHoverToolTipController.HOVER_TOOL_TIP_REVEAL_START_DELAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Display;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.util.ActivityContextWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

/**
 * Tests for TaskbarHoverToolTipController.
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskbarHoverToolTipControllerTest extends TaskbarBaseTestCase {

    private TaskbarHoverToolTipController mTaskbarHoverToolTipController;
    private TestableLooper mTestableLooper;

    @Mock private TaskbarView mTaskbarView;
    @Mock private MotionEvent mMotionEvent;
    @Mock private BubbleTextView mHoverBubbleTextView;
    @Mock private FolderIcon mHoverFolderIcon;
    @Mock private Display mDisplay;
    @Mock private TaskbarDragLayer mTaskbarDragLayer;
    private Folder mSpyFolderView;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        Context context = getApplicationContext();

        doAnswer((Answer<Object>) invocation -> context.getSystemService(
                (String) invocation.getArgument(0)))
                .when(taskbarActivityContext).getSystemService(anyString());
        when(taskbarActivityContext.getResources()).thenReturn(context.getResources());
        when(taskbarActivityContext.getApplicationInfo()).thenReturn(
                context.getApplicationInfo());
        when(taskbarActivityContext.getDragLayer()).thenReturn(mTaskbarDragLayer);
        when(taskbarActivityContext.getMainLooper()).thenReturn(context.getMainLooper());
        when(taskbarActivityContext.getDisplay()).thenReturn(mDisplay);

        when(mTaskbarDragLayer.getChildCount()).thenReturn(1);
        mSpyFolderView = spy(new Folder(new ActivityContextWrapper(context), null));
        when(mTaskbarDragLayer.getChildAt(anyInt())).thenReturn(mSpyFolderView);
        doReturn(false).when(mSpyFolderView).isOpen();

        when(mHoverBubbleTextView.getText()).thenReturn("tooltip");
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            ((int[]) args[0])[0] = 0;
            ((int[]) args[0])[1] = 0;
            return null;
        }).when(mHoverBubbleTextView).getLocationOnScreen(any(int[].class));
        when(mHoverBubbleTextView.getWidth()).thenReturn(100);
        when(mHoverBubbleTextView.getHeight()).thenReturn(100);

        mHoverFolderIcon.mInfo = new FolderInfo();
        mHoverFolderIcon.mInfo.title = "tooltip";
        doAnswer((Answer<Void>) invocation -> {
            Object[] args = invocation.getArguments();
            ((int[]) args[0])[0] = 0;
            ((int[]) args[0])[1] = 0;
            return null;
        }).when(mHoverFolderIcon).getLocationOnScreen(any(int[].class));
        when(mHoverFolderIcon.getWidth()).thenReturn(100);
        when(mHoverFolderIcon.getHeight()).thenReturn(100);

        when(mTaskbarView.getTop()).thenReturn(200);

        mTaskbarHoverToolTipController = new TaskbarHoverToolTipController(
                taskbarActivityContext, mTaskbarView, mHoverBubbleTextView);
        mTestableLooper = TestableLooper.get(this);
    }

    @Test
    public void onHover_hoverEnterIcon_revealToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverBubbleTextView, mMotionEvent);

        // Verify fullscreen is not set until the delayed runnable to reveal the tooltip has run
        verify(taskbarActivityContext, never()).setTaskbarWindowFullscreen(true);
        waitForIdleSync();
        assertThat(hoverHandled).isTrue();
        verify(taskbarActivityContext).setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
                true);
        verify(taskbarActivityContext).setTaskbarWindowFullscreen(true);
    }

    @Test
    public void onHover_hoverExitIcon_closeToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverBubbleTextView, mMotionEvent);
        waitForIdleSync();

        assertThat(hoverHandled).isTrue();
        verify(taskbarActivityContext).setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
                false);
    }

    @Test
    public void onHover_hoverEnterFolderIcon_revealToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverFolderIcon, mMotionEvent);

        // Verify fullscreen is not set until the delayed runnable to reveal the tooltip has run
        verify(taskbarActivityContext, never()).setTaskbarWindowFullscreen(true);
        waitForIdleSync();
        assertThat(hoverHandled).isTrue();
        verify(taskbarActivityContext).setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
                true);
        verify(taskbarActivityContext).setTaskbarWindowFullscreen(true);
    }

    @Test
    public void onHover_hoverExitFolderIcon_closeToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverFolderIcon, mMotionEvent);
        waitForIdleSync();

        assertThat(hoverHandled).isTrue();
        verify(taskbarActivityContext).setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
                false);
    }

    @Test
    public void onHover_hoverExitFolderOpen_closeToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_EXIT);
        doReturn(true).when(mSpyFolderView).isOpen();

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverFolderIcon, mMotionEvent);
        waitForIdleSync();

        assertThat(hoverHandled).isTrue();
        verify(taskbarActivityContext).setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS,
                false);
    }

    @Test
    public void onHover_hoverEnterFolderOpen_noToolTip() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_ENTER);
        doReturn(true).when(mSpyFolderView).isOpen();

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverFolderIcon, mMotionEvent);

        assertThat(hoverHandled).isFalse();
    }

    @Test
    public void onHover_hoverMove_noUpdate() {
        when(mMotionEvent.getAction()).thenReturn(MotionEvent.ACTION_HOVER_MOVE);
        when(mMotionEvent.getActionMasked()).thenReturn(MotionEvent.ACTION_HOVER_MOVE);

        boolean hoverHandled =
                mTaskbarHoverToolTipController.onHover(mHoverFolderIcon, mMotionEvent);

        assertThat(hoverHandled).isFalse();
    }

    private void waitForIdleSync() {
        mTestableLooper.moveTimeForward(HOVER_TOOL_TIP_REVEAL_START_DELAY + 1);
        mTestableLooper.processAllMessages();
    }
}
