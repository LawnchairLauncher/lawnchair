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
import static android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_TASK;
import static android.content.ClipDescription.MIMETYPE_TEXT_INTENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_FULLSCREEN;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_BOTTOM;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_LEFT;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_RIGHT;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_TOP;

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
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.os.RemoteException;
import android.view.DisplayInfo;
import android.view.DragEvent;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.InstanceId;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.draganddrop.DragAndDropPolicy.Target;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.startingsurface.TaskSnapshotWindow;

import org.junit.After;
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
public class DragAndDropPolicyTest extends ShellTestCase {

    @Mock
    private Context mContext;

    @Mock
    private ActivityTaskManager mActivityTaskManager;

    // Both the split-screen and start interface.
    @Mock
    private SplitScreenController mSplitScreenStarter;

    @Mock
    private InstanceId mLoggerSessionId;

    private DisplayLayout mLandscapeDisplayLayout;
    private DisplayLayout mPortraitDisplayLayout;
    private Insets mInsets;
    private DragAndDropPolicy mPolicy;

    private ClipData mActivityClipData;
    private PendingIntent mLaunchableIntentPendingIntent;
    private ClipData mLaunchableIntentClipData;
    private ClipData mNonResizeableActivityClipData;
    private ClipData mTaskClipData;
    private ClipData mShortcutClipData;

    private ActivityManager.RunningTaskInfo mHomeTask;
    private ActivityManager.RunningTaskInfo mFullscreenAppTask;
    private ActivityManager.RunningTaskInfo mNonResizeableFullscreenAppTask;

    private MockitoSession mMockitoSession;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mMockitoSession = mockitoSession()
                .strictness(LENIENT)
                .mockStatic(DragUtils.class)
                .startMocking();
        when(DragUtils.canHandleDrag(any())).thenReturn(true);

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

        mPolicy = spy(new DragAndDropPolicy(mContext, mSplitScreenStarter, mSplitScreenStarter));
        mActivityClipData = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        mLaunchableIntentPendingIntent = mock(PendingIntent.class);
        when(mLaunchableIntentPendingIntent.getCreatorUserHandle())
                .thenReturn(android.os.Process.myUserHandle());
        mLaunchableIntentClipData = createIntentClipData(mLaunchableIntentPendingIntent);
        mNonResizeableActivityClipData = createAppClipData(MIMETYPE_APPLICATION_ACTIVITY);
        setClipDataResizeable(mNonResizeableActivityClipData, false);
        mTaskClipData = createAppClipData(MIMETYPE_APPLICATION_TASK);
        mShortcutClipData = createAppClipData(MIMETYPE_APPLICATION_SHORTCUT);

        mHomeTask = createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME);
        mFullscreenAppTask = createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mNonResizeableFullscreenAppTask =
                createTaskInfo(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mNonResizeableFullscreenAppTask.isResizeable = false;

        setRunningTask(mFullscreenAppTask);
    }

    @After
    public void tearDown() {
        mMockitoSession.finishMocking();
    }

    /**
     * Creates an app-based clip data that is by default resizeable.
     */
    private ClipData createAppClipData(String mimeType) {
        ClipDescription clipDescription = new ClipDescription(mimeType, new String[] { mimeType });
        Intent i = new Intent();
        switch (mimeType) {
            case MIMETYPE_APPLICATION_SHORTCUT:
                i.putExtra(Intent.EXTRA_PACKAGE_NAME, "package");
                i.putExtra(Intent.EXTRA_SHORTCUT_ID, "shortcut_id");
                break;
            case MIMETYPE_APPLICATION_TASK:
                i.putExtra(Intent.EXTRA_TASK_ID, 12345);
                break;
            case MIMETYPE_APPLICATION_ACTIVITY:
                final PendingIntent pi = mock(PendingIntent.class);
                doReturn(android.os.Process.myUserHandle()).when(pi).getCreatorUserHandle();
                i.putExtra(ClipDescription.EXTRA_PENDING_INTENT, pi);
                break;
        }
        i.putExtra(Intent.EXTRA_USER, android.os.Process.myUserHandle());
        ClipData.Item item = new ClipData.Item(i);
        item.setActivityInfo(new ActivityInfo());
        ClipData data = new ClipData(clipDescription, item);
        setClipDataResizeable(data, true);
        return data;
    }

    /**
     * Creates an intent-based clip data that is by default resizeable.
     */
    private ClipData createIntentClipData(PendingIntent intent) {
        ClipDescription clipDescription = new ClipDescription("Intent",
                new String[] { MIMETYPE_TEXT_INTENT });
        ClipData.Item item = new ClipData.Item.Builder()
                .setIntentSender(intent.getIntentSender())
                .build();
        ClipData data = new ClipData(clipDescription, item);
        return data;
    }

    private ActivityManager.RunningTaskInfo createTaskInfo(int winMode, int actType) {
        ActivityManager.RunningTaskInfo info = new ActivityManager.RunningTaskInfo();
        info.configuration.windowConfiguration.setActivityType(actType);
        info.configuration.windowConfiguration.setWindowingMode(winMode);
        info.isResizeable = true;
        info.baseActivity = new ComponentName(getInstrumentation().getContext(),
                ".ActivityWithMode" + winMode);
        info.baseIntent = new Intent();
        info.baseIntent.setComponent(info.baseActivity);
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = info.baseActivity.getPackageName();
        activityInfo.name = info.baseActivity.getClassName();
        info.topActivityInfo = activityInfo;
        return info;
    }

    private void setRunningTask(ActivityManager.RunningTaskInfo task) {
        doReturn(Collections.singletonList(task)).when(mActivityTaskManager)
                .getTasks(anyInt(), anyBoolean());
    }

    private void setClipDataResizeable(ClipData data, boolean resizeable) {
        data.getItemAt(0).getActivityInfo().resizeMode = resizeable
                ? ActivityInfo.RESIZE_MODE_RESIZEABLE
                : ActivityInfo.RESIZE_MODE_UNRESIZEABLE;
    }

    @Test
    public void testDragAppOverFullscreenHome_expectOnlyFullscreenTarget() {
        dragOverFullscreenHome_expectOnlyFullscreenTarget(mActivityClipData);
    }

    @Test
    public void testDragAppOverFullscreenApp_expectSplitScreenTargets() {
        dragOverFullscreenApp_expectSplitScreenTargets(mActivityClipData);
    }

    @Test
    public void testDragAppOverFullscreenAppPhone_expectVerticalSplitScreenTargets() {
        dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(mActivityClipData);
    }

    @Test
    public void testDragIntentOverFullscreenHome_expectOnlyFullscreenTarget() {
        when(DragUtils.getLaunchIntent((ClipData) any(), anyInt())).thenReturn(
                mLaunchableIntentPendingIntent);
        dragOverFullscreenHome_expectOnlyFullscreenTarget(mLaunchableIntentClipData);
    }

    @Test
    public void testDragIntentOverFullscreenApp_expectSplitScreenTargets() {
        when(DragUtils.getLaunchIntent((ClipData) any(), anyInt())).thenReturn(
                mLaunchableIntentPendingIntent);
        dragOverFullscreenApp_expectSplitScreenTargets(mLaunchableIntentClipData);
    }

    @Test
    public void testDragIntentOverFullscreenAppPhone_expectVerticalSplitScreenTargets() {
        when(DragUtils.getLaunchIntent((ClipData) any(), anyInt())).thenReturn(
                mLaunchableIntentPendingIntent);
        dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(mLaunchableIntentClipData);
    }

    private void dragOverFullscreenHome_expectOnlyFullscreenTarget(ClipData data) {
        doReturn(true).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mHomeTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, data, 0 /* dragFlags */);
        dragSession.update();
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_FULLSCREEN);

        mPolicy.handleDrop(filterTargetByType(targets, TYPE_FULLSCREEN));
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_UNDEFINED), any());
    }

    private void dragOverFullscreenApp_expectSplitScreenTargets(ClipData data) {
        doReturn(true).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, data, 0 /* dragFlags */);
        dragSession.update();
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_SPLIT_LEFT, TYPE_SPLIT_RIGHT);

        mPolicy.handleDrop(filterTargetByType(targets, TYPE_SPLIT_LEFT));
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_TOP_OR_LEFT), any());
        reset(mSplitScreenStarter);

        mPolicy.handleDrop(filterTargetByType(targets, TYPE_SPLIT_RIGHT));
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), any());
    }

    private void dragOverFullscreenAppPhone_expectVerticalSplitScreenTargets(ClipData data) {
        doReturn(false).when(mSplitScreenStarter).isLeftRightSplit();
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mPortraitDisplayLayout, data, 0 /* dragFlags */);
        dragSession.update();
        mPolicy.start(dragSession, mLoggerSessionId);
        ArrayList<Target> targets = assertExactTargetTypes(
                mPolicy.getTargets(mInsets), TYPE_SPLIT_TOP, TYPE_SPLIT_BOTTOM);

        mPolicy.handleDrop(filterTargetByType(targets, TYPE_SPLIT_TOP));
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_TOP_OR_LEFT), any());
        reset(mSplitScreenStarter);

        mPolicy.handleDrop(filterTargetByType(targets, TYPE_SPLIT_BOTTOM));
        verify(mSplitScreenStarter).startIntent(any(), anyInt(), any(),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), any());
    }

    @Test
    public void testTargetHitRects() {
        setRunningTask(mFullscreenAppTask);
        DragSession dragSession = new DragSession(mActivityTaskManager,
                mLandscapeDisplayLayout, mActivityClipData, 0 /* dragFlags */);
        dragSession.update();
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
        assertTrue(DragUtils.getLaunchIntent(mLaunchableIntentClipData, 0) == null);
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
