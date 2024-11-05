/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.draganddrop;

import static android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.DragEvent.ACTION_DRAG_STARTED;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.view.Display;
import android.view.DragEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.UiEventLogger;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the drag and drop controller.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DragAndDropControllerTest extends ShellTestCase {

    @Mock
    private Context mContext;
    @Mock
    private ShellInit mShellInit;
    @Mock
    private ShellController mShellController;
    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private UiEventLogger mUiEventLogger;
    @Mock
    private DragAndDropController.DragAndDropListener mDragAndDropListener;
    @Mock
    private IconProvider mIconProvider;
    @Mock
    private ShellExecutor mMainExecutor;
    @Mock
    private Transitions mTransitions;
    @Mock
    private GlobalDragListener mGlobalDragListener;

    private DragAndDropController mController;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mController = new DragAndDropController(mContext, mShellInit, mShellController,
                mShellCommandHandler, mDisplayController, mUiEventLogger, mIconProvider,
                mGlobalDragListener, mTransitions, mMainExecutor);
        mController.onInit();
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void testIgnoreNonDefaultDisplays() {
        final int nonDefaultDisplayId = 12345;
        final View dragLayout = mock(View.class);
        final Display display = mock(Display.class);
        doReturn(nonDefaultDisplayId).when(display).getDisplayId();
        doReturn(display).when(dragLayout).getDisplay();

        // Expect no per-display layout to be added
        mController.onDisplayAdded(nonDefaultDisplayId);
        assertFalse(mController.onDrag(dragLayout, mock(DragEvent.class)));
    }

    @Test
    public void testListenerOnDragStarted() {
        final View dragLayout = mock(View.class);
        final Display display = mock(Display.class);
        doReturn(display).when(dragLayout).getDisplay();
        doReturn(DEFAULT_DISPLAY).when(display).getDisplayId();

        final ClipData clipData = createClipData();
        final DragEvent event = mock(DragEvent.class);
        doReturn(ACTION_DRAG_STARTED).when(event).getAction();
        doReturn(clipData).when(event).getClipData();
        doReturn(clipData.getDescription()).when(event).getClipDescription();

        mController.addListener(mDragAndDropListener);

        // Ensure there's a target so that onDrag will execute
        mController.addDisplayDropTarget(0, mContext, mock(WindowManager.class),
                mock(FrameLayout.class), mock(DragLayout.class));

        // Verify the listener is called on a valid drag action.
        mController.onDrag(dragLayout, event);
        verify(mDragAndDropListener, times(1)).onDragStarted();

        // Verify the listener isn't called after removal.
        reset(mDragAndDropListener);
        mController.removeListener(mDragAndDropListener);
        mController.onDrag(dragLayout, event);
        verify(mDragAndDropListener, never()).onDragStarted();
    }

    private ClipData createClipData() {
        ClipDescription clipDescription = new ClipDescription(MIMETYPE_APPLICATION_SHORTCUT,
                new String[] { MIMETYPE_APPLICATION_SHORTCUT });
        Intent i = new Intent();
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, "pkg");
        i.putExtra(Intent.EXTRA_SHORTCUT_ID, "shortcutId");
        i.putExtra(Intent.EXTRA_USER, android.os.Process.myUserHandle());
        ClipData.Item item = new ClipData.Item(i);
        return new ClipData(clipDescription, item);
    }
}
