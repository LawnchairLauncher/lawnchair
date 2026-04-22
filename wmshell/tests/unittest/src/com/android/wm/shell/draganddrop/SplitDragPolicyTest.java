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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_ACTIVITY;
import static android.view.View.DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG;

import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.draganddrop.DragTestUtils.createAppClipData;
import static com.android.wm.shell.draganddrop.DragTestUtils.createTaskInfo;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_FULLSCREEN;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_BOTTOM;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_LEFT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_RIGHT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_TOP;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.RemoteException;
import android.view.DisplayInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.InstanceId;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.draganddrop.SplitDragPolicy.Target;
import com.android.wm.shell.splitscreen.SplitScreenController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/**
 * Tests for the drag and drop policy.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SplitDragPolicyTest extends ShellTestCase {

    @Mock
    private Context mContext;

    @Mock
    private ActivityTaskManager mActivityTaskManager;

    // Both the split-screen and start interface.
    @Mock
    private SplitScreenController mSplitScreenStarter;
    @Mock
    private SplitDragPolicy.Starter mFullscreenStarter;

    @Mock
    private InstanceId mLoggerSessionId;

    private DisplayLayout mLandscapeDisplayLayout;
    private DisplayLayout mPortraitDisplayLayout;
    private Insets mInsets;
    private SplitDragPolicy mPolicy;

    private ActivityManager.RunningTaskInfo mHomeTask;
    private ActivityManager.RunningTaskInfo mFullscreenAppTask;
    private ActivityManager.RunningTaskInfo mNonResizeableFullscreenAppTask;

    private MockitoSession mMockitoSession;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);

        Resources res = mock(Resources.class);
        Configuration config = new Configuration();
        doReturn(config).when(res).getConfiguration();
        doReturn(res).when(mContext).getResources();
        DisplayInfo info = new DisplayInfo();
        info.logicalWidth = 200;
        info.logicalHeight = 100;
        mLandscapeDisplayLayout = new DisplayLayout(info, res, false, false);
        DisplayInfo info2 = new DisplayInfo();
        info.logicalWidth = 100;
        info.logicalHeight = 200;
        mPortraitDisplayLayout = new DisplayLayout(info2, res, false, false);
        mInsets = Insets.of(0, 0, 0, 0);

        mPolicy = spy(new SplitDragPolicy(mContext, mSplitScreenStarter, mFullscreenStarter,
                mock(DragZoneAnimator.class)));

        mHomeTask = createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME);
        mFullscreenAppTask = createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mNonResizeableFullscreenAppTask =
                createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mNonResizeableFullscreenAppTask.isResizeable = false;

        setRunningTask(mFullscreenAppTask);
    }

    private void setRunningTask(ActivityManager.RunningTaskInfo task) {
        doReturn(Collections.singletonList(task)).when(mActivityTaskManager)
                .getTasks(anyInt(), anyBoolean());
    }

    @Test
    public void testDragAppOverFullscreenHome_expectOnlyFullscreenTarget() {
        final ClipData data = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        dragOverFullscreenHome_expectOnlyFullscreenTarget(data);
    }

    @Test
    public void testDragAppOverFullscreenApp_expectSplitScreenTargets() {
        final ClipData data = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        dragOverFullscreenApp_expectSplitScreenTargets(data);
    }

    @Test
    public void testDragAppOverFullscreenAppPhone_expectVerticalSplitScreenTargets() {
        final ClipData data = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(data);
    }

    @Test
    public void testDragIntentOverFullscreenHome_expectOnlyFullscreenTarget() {
        final PendingIntent pendingIntent = DragTestUtils.createLaunchableIntent(super.mContext);
        final ClipData data = DragTestUtils.createIntentClipData(pendingIntent);
        dragOverFullscreenHome_expectOnlyFullscreenTarget(data);
    }

    @Test
    public void testDragIntentOverFullscreenApp_expectSplitScreenTargets() {
        final PendingIntent pendingIntent = DragTestUtils.createLaunchableIntent(super.mContext);
        final ClipData data = DragTestUtils.createIntentClipData(pendingIntent);
        dragOverFullscreenApp_expectSplitScreenTargets(data);
    }

    @Test
    public void testDragIntentOverFullscreenAppPhone_expectVerticalSplitScreenTargets() {
        final PendingIntent pendingIntent = DragTestUtils.createLaunchableIntent(super.mContext);
        final ClipData data = DragTestUtils.createIntentClipData(pendingIntent);
        dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(data);
    }

    private void dragOverFullscreenHome_expectOnlyFullscreenTarget(ClipData data) {
        doReturn(true).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mHomeTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, data, DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG);
        dragSession.initialize(false /* skipUpdateRunningTask */);
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_FULLSCREEN);

        mPolicy.onDropped(filterTargetByType(targets, TYPE_FULLSCREEN), null /* hideTaskToken */);
        verify(mFullscreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_UNDEFINED), any(), any(), eq(SPLIT_INDEX_UNDEFINED));
    }

    private void dragOverFullscreenApp_expectSplitScreenTargets(ClipData data) {
        doReturn(true).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, data, DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG);
        dragSession.initialize(false /* skipUpdateRunningTask */);
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_SPLIT_LEFT, TYPE_SPLIT_RIGHT);

        mPolicy.onDropped(filterTargetByType(targets, TYPE_SPLIT_LEFT), null /* hideTaskToken */);
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_TOP_OR_LEFT), any(), any(), eq(SPLIT_INDEX_UNDEFINED));
        reset(mSplitScreenStarter);

        mPolicy.onDropped(filterTargetByType(targets, TYPE_SPLIT_RIGHT), null /* hideTaskToken */);
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), any(), any(), eq(SPLIT_INDEX_UNDEFINED));
    }

    private void dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(ClipData data) {
        doReturn(false).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mPortraitDisplayLayout, data, DRAG_FLAG_START_INTENT_SENDER_ON_UNHANDLED_DRAG);
        dragSession.initialize(false /* skipUpdateRunningTask */);
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_SPLIT_TOP, TYPE_SPLIT_BOTTOM);

        mPolicy.onDropped(filterTargetByType(targets, TYPE_SPLIT_TOP), null /* hideTaskToken */);
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_TOP_OR_LEFT), any(), any(), eq(SPLIT_INDEX_UNDEFINED));
        reset(mSplitScreenStarter);

        mPolicy.onDropped(filterTargetByType(targets, TYPE_SPLIT_BOTTOM),
                null /* hideTaskToken */);
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), any(), any(), eq(SPLIT_INDEX_UNDEFINED));
    }

    @Test
    public void testTargetHitRects() {
        final ClipData data = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, data, 0 /* dragFlags */);
        dragSession.initialize(false /* skipUpdateRunningTask */);
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = mPolicy.getTargets(mInsets);
        for (Target t : targets) {
            assertTrue(mPolicy.getTargetAtLocation(t.hitRegion.left, t.hitRegion.top) == t);
            assertTrue(mPolicy.getTargetAtLocation(t.hitRegion.right - 1, t.hitRegion.top) == t);
            assertTrue(mPolicy.getTargetAtLocation(t.hitRegion.right - 1, t.hitRegion.bottom - 1)
                    == t);
            assertTrue(mPolicy.getTargetAtLocation(t.hitRegion.left, t.hitRegion.bottom - 1)
                    == t);
        }
    }

    @Test
    public void testDisallowLaunchIntentWithoutDelegationFlag() {
        final PendingIntent pendingIntent = DragTestUtils.createLaunchableIntent(super.mContext);
        final ClipData data = DragTestUtils.createIntentClipData(pendingIntent);
        assertTrue(DragUtils.getLaunchIntent(data, 0) == null);
    }

    private Target filterTargetByType(ArrayList<Target> targets, int type) {
        for (Target t : targets) {
            if (type == t.type) {
                return t;
            }
        }
        fail("Target with type: " + type + " not found");
        return null;
    }

    private ArrayList<Target> assertExactTargetTypes(ArrayList<Target> targets,
            int... expectedTargetTypes) {
        HashSet<Integer> expected = new HashSet<>();
        for (int t : expectedTargetTypes) {
            expected.add(t);
        }
        for (Target t : targets) {
            if (!expected.contains(t.type)) {
                fail("Found unexpected target type: " + t.type);
            }
            expected.remove(t.type);
        }
        assertTrue(expected.isEmpty());
        return targets;
    }
}
