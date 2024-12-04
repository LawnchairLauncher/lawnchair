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
package com.android.wm.shell.startingsurface;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.wm.shell.startingsurface.SplashscreenContentDrawer.MAX_ANIMATION_DURATION;
import static com.android.wm.shell.startingsurface.SplashscreenContentDrawer.MINIMAL_ANIMATION_DURATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.ColorSpace;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.view.IWindowSession;
import android.view.InsetsState;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.view.WindowMetrics;
import android.window.StartingWindowInfo;
import android.window.StartingWindowRemovalInfo;
import android.window.TaskSnapshot;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TransactionPool;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.function.IntSupplier;

/**
 * Tests for the starting surface drawer.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StartingSurfaceDrawerTests extends ShellTestCase {
    @Mock
    private IBinder mBinder;
    @Mock
    private WindowManager mMockWindowManager;
    @Mock
    private IconProvider mIconProvider;
    @Mock
    private TransactionPool mTransactionPool;

    private final Handler mTestHandler = new Handler(Looper.getMainLooper());
    private ShellExecutor mTestExecutor;
    private final TestableContext mTestContext = new TestContext(
            InstrumentationRegistry.getInstrumentation().getTargetContext());
    StartingSurfaceDrawer mStartingSurfaceDrawer;

    private static class TestContext extends TestableContext {
        TestContext(Context context) {
            super(context);
        }

        @Override
        public Context createPackageContextAsUser(String packageName, int flags, UserHandle user)
                throws PackageManager.NameNotFoundException {
            return this;
        }

        @Override
        public Intent registerReceiverAsUser(BroadcastReceiver receiver, UserHandle user,
                IntentFilter filter, String broadcastPermission, Handler scheduler) {
            return null;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final WindowManager realWindowManager = mTestContext.getSystemService(WindowManager.class);
        final WindowMetrics metrics = realWindowManager.getMaximumWindowMetrics();
        mTestContext.addMockSystemService(WindowManager.class, mMockWindowManager);

        doReturn(metrics).when(mMockWindowManager).getMaximumWindowMetrics();
        doNothing().when(mMockWindowManager).addView(any(), any());
        mTestExecutor = new HandlerExecutor(mTestHandler);
        mStartingSurfaceDrawer = new StartingSurfaceDrawer(mTestContext, mTestExecutor,
                mIconProvider, mTransactionPool);
        mStartingSurfaceDrawer = spy(
                new StartingSurfaceDrawer(mTestContext, mTestExecutor, mIconProvider,
                        mTransactionPool));
        spyOn(mStartingSurfaceDrawer.mSplashscreenWindowCreator);
        spyOn(mStartingSurfaceDrawer.mWindowRecords);
        spyOn(mStartingSurfaceDrawer.mWindowlessRecords);
    }

    @Test
    public void testAddSplashScreenSurface() {
        final int taskId = 1;
        final StartingWindowInfo windowInfo =
                createWindowInfo(taskId, android.R.style.Theme, mBinder);
        mStartingSurfaceDrawer.addSplashScreenStartingWindow(windowInfo,
                STARTING_WINDOW_TYPE_SPLASH_SCREEN);
        waitHandlerIdle(mTestHandler);
        verify(mStartingSurfaceDrawer.mSplashscreenWindowCreator).addWindow(
                eq(taskId), eq(mBinder), any(), any(), any(),
                eq(STARTING_WINDOW_TYPE_SPLASH_SCREEN));

        StartingWindowRemovalInfo removalInfo = new StartingWindowRemovalInfo();
        removalInfo.taskId = windowInfo.taskInfo.taskId;
        mStartingSurfaceDrawer.removeStartingWindow(removalInfo);
        waitHandlerIdle(mTestHandler);
        verify(mStartingSurfaceDrawer.mWindowRecords).removeWindow(any(), eq(false));
        assertEquals(mStartingSurfaceDrawer.mWindowRecords.recordSize(), 0);
    }

    @Test
    public void testFallbackDefaultTheme() {
        final int taskId = 1;
        final StartingWindowInfo windowInfo =
                createWindowInfo(taskId, 0, mBinder);
        final int[] theme = new int[1];
        doAnswer(invocation -> theme[0] = (Integer) invocation.callRealMethod())
                .when(mStartingSurfaceDrawer.mSplashscreenWindowCreator)
                .getSplashScreenTheme(eq(0), any());

        mStartingSurfaceDrawer.addSplashScreenStartingWindow(windowInfo,
                STARTING_WINDOW_TYPE_SPLASH_SCREEN);
        waitHandlerIdle(mTestHandler);
        verify(mStartingSurfaceDrawer.mSplashscreenWindowCreator)
                .getSplashScreenTheme(eq(0), any());
        assertNotEquals(theme[0], 0);
    }

    @Test
    public void testColorCache() {
        final String packageName = mTestContext.getPackageName();
        final int configHash = 1;
        final int windowBgColor = 0xff000000;
        final int windowBgResId = 1;
        final IntSupplier windowBgColorSupplier = () -> windowBgColor;
        final SplashscreenContentDrawer.ColorCache colorCache =
                mStartingSurfaceDrawer.mSplashscreenContentDrawer.mColorCache;
        final SplashscreenContentDrawer.ColorCache.WindowColor windowColor1 =
                colorCache.getWindowColor(packageName, configHash, windowBgColor, windowBgResId,
                        windowBgColorSupplier);
        assertEquals(windowBgColor, windowColor1.mBgColor);
        assertEquals(0, windowColor1.mReuseCount);

        final SplashscreenContentDrawer.ColorCache.WindowColor windowColor2 =
                colorCache.getWindowColor(packageName, configHash, windowBgColor, windowBgResId,
                        windowBgColorSupplier);
        assertEquals(windowColor1, windowColor2);
        assertEquals(1, windowColor1.mReuseCount);

        final Intent packageRemoved = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        packageRemoved.setData(Uri.parse("package:" + packageName));
        colorCache.onReceive(mTestContext, packageRemoved);

        final SplashscreenContentDrawer.ColorCache.WindowColor windowColor3 =
                colorCache.getWindowColor(packageName, configHash, windowBgColor, windowBgResId,
                        windowBgColorSupplier);
        assertEquals(0, windowColor3.mReuseCount);
    }

    @Test
    public void testRemoveTaskSnapshotWithImeSurfaceWhenOnImeDrawn() throws Exception {
        final int taskId = 1;
        final StartingWindowInfo windowInfo =
                createWindowInfo(taskId, android.R.style.Theme, mBinder);
        TaskSnapshot snapshot = createTaskSnapshot(100, 100, new Point(100, 100),
                new Rect(0, 0, 0, 50), true /* hasImeSurface */);
        final IWindowSession session = WindowManagerGlobal.getWindowSession();
        spyOn(session);
        doReturn(WindowManagerGlobal.ADD_OKAY).when(session).addToDisplay(
                any() /* window */, any() /* attrs */,
                anyInt() /* viewVisibility */, anyInt() /* displayId */,
                anyInt() /* requestedVisibleTypes */, any() /* outInputChannel */,
                any() /* outInsetsState */, any() /* outActiveControls */,
                any() /* outAttachedFrame */, any() /* outSizeCompatScale */);
        TaskSnapshotWindow mockSnapshotWindow = TaskSnapshotWindow.create(windowInfo,
                mBinder,
                snapshot, mTestExecutor, () -> {
                });
        spyOn(mockSnapshotWindow);
        try (AutoCloseable mockTaskSnapshotSession = new AutoCloseable() {
            MockitoSession mockSession = mockitoSession()
                    .initMocks(this)
                    .mockStatic(TaskSnapshotWindow.class)
                    .startMocking();
            @Override
            public void close() {
                mockSession.finishMocking();
            }
        }) {
            when(TaskSnapshotWindow.create(eq(windowInfo), eq(mBinder), eq(snapshot), any(),
                    any())).thenReturn(mockSnapshotWindow);
            // Simulate a task snapshot window created with IME snapshot shown.
            mStartingSurfaceDrawer.makeTaskSnapshotWindow(windowInfo, snapshot);
            waitHandlerIdle(mTestHandler);

            // Verify the task snapshot with IME snapshot will be removed when received the real IME
            // drawn callback.
            // makeTaskSnapshotWindow shall call removeWindowSynced before there add a new
            // StartingWindowRecord for the task.
            mStartingSurfaceDrawer.onImeDrawnOnTask(1);
            verify(mStartingSurfaceDrawer.mWindowRecords, times(2))
                    .removeWindow(any(), eq(true));
        }
    }

    @Test
    public void testClearAllWindows() {
        final int taskId = 1;
        mStartingSurfaceDrawer.mWindowRecords.addRecord(taskId,
                new StartingSurfaceDrawer.StartingWindowRecord() {
                    @Override
                    public boolean removeIfPossible(StartingWindowRemovalInfo info,
                            boolean immediately) {
                        return true;
                    }
                });
        mStartingSurfaceDrawer.mWindowlessRecords.addRecord(taskId,
                new StartingSurfaceDrawer.StartingWindowRecord() {
                    @Override
                    public boolean removeIfPossible(StartingWindowRemovalInfo info,
                            boolean immediately) {
                        return true;
                    }
                });
        mStartingSurfaceDrawer.clearAllWindows();
        waitHandlerIdle(mTestHandler);
        verify(mStartingSurfaceDrawer.mWindowRecords).removeWindow(any(), eq(true));
        assertEquals(mStartingSurfaceDrawer.mWindowRecords.recordSize(), 0);
        verify(mStartingSurfaceDrawer.mWindowlessRecords).removeWindow(any(), eq(true));
        assertEquals(mStartingSurfaceDrawer.mWindowlessRecords.recordSize(), 0);
    }

    @Test
    public void testMinimumAnimationDuration() {
        final long maxDuration = MAX_ANIMATION_DURATION;
        final long minDuration = MINIMAL_ANIMATION_DURATION;

        final long shortDuration = minDuration - 1;
        final long medianShortDuration = minDuration + 1;
        final long medianLongDuration = maxDuration - 1;
        final long longAppDuration = maxDuration + 1;

        // static icon
        assertEquals(shortDuration, SplashscreenContentDrawer.getShowingDuration(
                0, shortDuration));
        // median launch + static icon
        assertEquals(medianShortDuration, SplashscreenContentDrawer.getShowingDuration(
                0, medianShortDuration));
        // long launch + static icon
        assertEquals(longAppDuration, SplashscreenContentDrawer.getShowingDuration(
                0, longAppDuration));

        // fast launch + animatable icon
        assertEquals(shortDuration, SplashscreenContentDrawer.getShowingDuration(
                shortDuration, shortDuration));
        assertEquals(minDuration, SplashscreenContentDrawer.getShowingDuration(
                medianShortDuration, shortDuration));
        assertEquals(minDuration, SplashscreenContentDrawer.getShowingDuration(
                longAppDuration, shortDuration));

        // median launch + animatable icon
        assertEquals(medianShortDuration, SplashscreenContentDrawer.getShowingDuration(
                shortDuration, medianShortDuration));
        assertEquals(medianShortDuration, SplashscreenContentDrawer.getShowingDuration(
                medianShortDuration, medianShortDuration));
        assertEquals(minDuration, SplashscreenContentDrawer.getShowingDuration(
                longAppDuration, medianShortDuration));
        // between min < max launch + animatable icon
        assertEquals(medianLongDuration, SplashscreenContentDrawer.getShowingDuration(
                medianShortDuration, medianLongDuration));
        assertEquals(maxDuration, SplashscreenContentDrawer.getShowingDuration(
                medianLongDuration, medianShortDuration));

        // long launch + animatable icon
        assertEquals(longAppDuration, SplashscreenContentDrawer.getShowingDuration(
                shortDuration, longAppDuration));
        assertEquals(longAppDuration, SplashscreenContentDrawer.getShowingDuration(
                medianShortDuration, longAppDuration));
        assertEquals(longAppDuration, SplashscreenContentDrawer.getShowingDuration(
                longAppDuration, longAppDuration));
    }

    private StartingWindowInfo createWindowInfo(int taskId, int themeResId, IBinder appToken) {
        StartingWindowInfo windowInfo = new StartingWindowInfo();
        final ActivityInfo info = new ActivityInfo();
        info.applicationInfo = new ApplicationInfo();
        info.packageName = "test";
        info.theme = themeResId;
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.topActivityInfo = info;
        taskInfo.taskId = taskId;
        windowInfo.appToken = appToken;
        windowInfo.targetActivityInfo = info;
        windowInfo.taskInfo = taskInfo;
        windowInfo.topOpaqueWindowInsetsState = new InsetsState();
        windowInfo.mainWindowLayoutParams = new WindowManager.LayoutParams();
        windowInfo.topOpaqueWindowLayoutParams = new WindowManager.LayoutParams();
        return windowInfo;
    }

    private static void waitHandlerIdle(Handler handler) {
        handler.runWithScissors(() -> { }, 0 /* timeout */);
    }

    private TaskSnapshot createTaskSnapshot(int width, int height, Point taskSize,
            Rect contentInsets, boolean hasImeSurface) {
        final HardwareBuffer buffer = HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888,
                1, HardwareBuffer.USAGE_CPU_READ_RARELY);
        return new TaskSnapshot(
                System.currentTimeMillis(),
                0 /* captureTime */,
                new ComponentName("", ""), buffer,
                ColorSpace.get(ColorSpace.Named.SRGB), ORIENTATION_PORTRAIT,
                Surface.ROTATION_0, taskSize, contentInsets, new Rect() /* letterboxInsets */,
                false, true /* isRealSnapshot */, WINDOWING_MODE_FULLSCREEN,
                0 /* systemUiVisibility */, false /* isTranslucent */,
                hasImeSurface /* hasImeSurface */);
    }
}
