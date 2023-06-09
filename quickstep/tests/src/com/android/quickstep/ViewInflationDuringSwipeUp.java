/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.launcher3.WorkspaceLayoutManager.FIRST_SCREEN_ID;
import static com.android.launcher3.testcomponent.TestCommandReceiver.EXTRA_VALUE;
import static com.android.launcher3.testcomponent.TestCommandReceiver.SET_LIST_VIEW_SERVICE_BINDER;
import static com.android.launcher3.util.WidgetUtils.createWidgetInfo;
import static com.android.quickstep.NavigationModeSwitchRule.Mode.ZERO_BUTTON;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.RemoteViews;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.celllayout.FavoriteItemsTransaction;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.tapl.LaunchedAppState;
import com.android.launcher3.testcomponent.ListViewService;
import com.android.launcher3.testcomponent.ListViewService.SimpleViewsFactory;
import com.android.launcher3.testcomponent.TestCommandReceiver;
import com.android.launcher3.ui.TaplTestsLauncher3;
import com.android.launcher3.ui.TestViewHelpers;
import com.android.launcher3.util.Executors;
import com.android.launcher3.widget.LauncherAppWidgetProviderInfo;
import com.android.quickstep.NavigationModeSwitchRule.NavigationModeSwitch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Test to verify view inflation does not happen during swipe up.
 * To verify view inflation, we setup a stub ViewConfiguration and check if any call to that class
 * does from a View.init method or not.
 *
 * Alternative approaches considered:
 *    Overriding LayoutInflater: This does not cover views initialized
 *        directly (ex: new LinearLayout)
 *    Using ExtendedMockito: Mocking static methods from platform classes (loaded in zygote) makes
 *        the main thread extremely slow and untestable
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ViewInflationDuringSwipeUp extends AbstractQuickStepTest {

    private SparseArray<ViewConfiguration> mConfigMap;
    private InitTracker mInitTracker;
    private LauncherModel mModel;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        // Workaround for b/142351228, when there are no activities, the system may not destroy the
        // activity correctly for activities under instrumentation, which can leave two concurrent
        // activities, which changes the order in which the activities are cleaned up (overlapping
        // stop and start) leading to all sort of issues. To workaround this, ensure that the test
        // is started only after starting another app.
        startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));

        TaplTestsLauncher3.initialize(this);

        mModel = LauncherAppState.getInstance(mTargetContext).getModel();
        Executors.MODEL_EXECUTOR.submit(mModel.getModelDbController()::createEmptyDB).get();

        // Get static configuration map
        Field field = ViewConfiguration.class.getDeclaredField("sConfigurations");
        field.setAccessible(true);
        mConfigMap = (SparseArray<ViewConfiguration>) field.get(null);

        mInitTracker = new InitTracker();
    }

    @Test
    @NavigationModeSwitch(mode = ZERO_BUTTON)
    @Suppress // until b/190618549 is fixed
    public void testSwipeUpFromApp() throws Exception {
        try {
            // Go to overview once so that all views are initialized and cached
            startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
            mLauncher.getLaunchedAppState().switchToOverview().dismissAllTasks();

            // Track view creations
            mInitTracker.startTracking();

            startTestActivity(2);
            mLauncher.getLaunchedAppState().switchToOverview();

            assertEquals("Views inflated during swipe up", 0, mInitTracker.viewInitCount);
        } finally {
            mConfigMap.clear();
        }
    }

    @Test
    @NavigationModeSwitch(mode = ZERO_BUTTON)
    @Suppress // until b/190729479 is fixed
    public void testSwipeUpFromApp_widget_update() {
        String stubText = "Some random stub text";

        executeSwipeUpTestWithWidget(
                widgetId -> { },
                widgetId -> AppWidgetManager.getInstance(getContext())
                        .updateAppWidget(widgetId, createMainWidgetViews(stubText)),
                stubText);
    }

    @Test
    @NavigationModeSwitch(mode = ZERO_BUTTON)
    @Suppress // until b/190729479 is fixed
    public void testSwipeUp_with_list_widgets() {
        SimpleViewsFactory viewFactory = new SimpleViewsFactory();
        viewFactory.viewCount = 1;
        Bundle args = new Bundle();
        args.putBinder(EXTRA_VALUE, viewFactory.toBinder());
        TestCommandReceiver.callCommand(SET_LIST_VIEW_SERVICE_BINDER, null, args);

        try {
            executeSwipeUpTestWithWidget(
                    widgetId -> {
                        // Initialize widget
                        RemoteViews views = createMainWidgetViews("List widget title");
                        views.setRemoteAdapter(android.R.id.list,
                                new Intent(getContext(), ListViewService.class));
                        AppWidgetManager.getInstance(getContext()).updateAppWidget(widgetId, views);
                        verifyWidget(viewFactory.getLabel(0));
                    },
                    widgetId -> {
                        // Update widget
                        viewFactory.viewCount = 2;
                        AppWidgetManager.getInstance(getContext())
                                .notifyAppWidgetViewDataChanged(widgetId, android.R.id.list);
                    },
                    viewFactory.getLabel(1)
            );
        } finally {
            TestCommandReceiver.callCommand(SET_LIST_VIEW_SERVICE_BINDER, null, new Bundle());
        }
    }

    private void executeSwipeUpTestWithWidget(IntConsumer widgetIdCreationCallback,
            IntConsumer updateBeforeSwipeUp, String finalWidgetText) {
        try {
            LauncherAppWidgetProviderInfo info = TestViewHelpers.findWidgetProvider(false);

            // Make sure the widget is big enough to show a list of items
            info.minSpanX = 2;
            info.minSpanY = 2;
            info.spanX = 2;
            info.spanY = 2;
            AtomicInteger widgetId = new AtomicInteger();
            new FavoriteItemsTransaction(mTargetContext)
                    .addItem(() -> {
                        LauncherAppWidgetInfo item = createWidgetInfo(info, mTargetContext, true);
                        item.screenId = FIRST_SCREEN_ID;
                        widgetId.set(item.appWidgetId);
                        return item;
                    })
                    .commitAndLoadHome(mLauncher);



            assertTrue("Widget is not present",
                    mLauncher.goHome().tryGetWidget(info.label, DEFAULT_UI_TIMEOUT) != null);

            // Verify widget id
            widgetIdCreationCallback.accept(widgetId.get());

            // Go to overview once so that all views are initialized and cached
            startAppFast(resolveSystemApp(Intent.CATEGORY_APP_CALCULATOR));
            mLauncher.getLaunchedAppState().switchToOverview().dismissAllTasks();

            // Track view creations
            mInitTracker.startTracking();

            startTestActivity(2);
            LaunchedAppState launchedAppState = mLauncher.getLaunchedAppState();

            // Update widget
            updateBeforeSwipeUp.accept(widgetId.get());

            launchedAppState.switchToOverview();
            assertEquals("Views inflated during swipe up", 0, mInitTracker.viewInitCount);

            // Widget is updated when going home
            mInitTracker.disableLog();
            mLauncher.goHome();
            verifyWidget(finalWidgetText);
            assertNotEquals(1, mInitTracker.viewInitCount);
        } finally {
            mConfigMap.clear();
        }
    }

    private void verifyWidget(String text) {
        assertNotNull("Widget not updated",
                UiDevice.getInstance(getInstrumentation())
                        .wait(Until.findObject(By.text(text)), DEFAULT_UI_TIMEOUT));
    }

    private RemoteViews createMainWidgetViews(String title) {
        Context c = getContext();
        int layoutId = c.getResources().getIdentifier(
                "test_layout_widget_list", "layout", c.getPackageName());
        RemoteViews views = new RemoteViews(c.getPackageName(), layoutId);
        views.setTextViewText(android.R.id.text1, title);
        return views;
    }

    private class InitTracker implements Answer {

        public int viewInitCount = 0;

        public boolean log = true;

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            Exception ex = new Exception();

            boolean found = false;
            for (StackTraceElement ste : ex.getStackTrace()) {
                if ("<init>".equals(ste.getMethodName())
                        && View.class.getName().equals(ste.getClassName())) {
                    found = true;
                    break;
                }
            }
            if (found) {
                viewInitCount++;
                if (log) {
                    Log.d("InitTracker", "New view inflated", ex);
                }

            }
            return invocation.callRealMethod();
        }

        public void disableLog() {
            log = false;
        }

        public void startTracking() {
            ViewConfiguration vc = ViewConfiguration.get(mTargetContext);
            ViewConfiguration spyVC = spy(vc);
            mConfigMap.put(mConfigMap.keyAt(mConfigMap.indexOfValue(vc)), spyVC);
            doAnswer(this).when(spyVC).getScaledTouchSlop();
        }
    }
}
