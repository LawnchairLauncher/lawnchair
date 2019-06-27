/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.ui;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;

import java.util.concurrent.Callable;
import java.util.function.Function;

public class TestViewHelpers {
    private static final String TAG = "TestViewHelpers";

    private static UiDevice getDevice() {
        return UiDevice.getInstance(getInstrumentation());
    }

    public static UiObject2 findViewById(int id) {
        return getDevice().wait(Until.findObject(getSelectorForId(id)),
                AbstractLauncherUiTest.DEFAULT_UI_TIMEOUT);
    }

    public static BySelector getSelectorForId(int id) {
        final Context targetContext = getTargetContext();
        String name = targetContext.getResources().getResourceEntryName(id);
        return By.res(targetContext.getPackageName(), name);
    }

    /**
     * Finds a widget provider which can fit on the home screen.
     *
     * @param test               test suite.
     * @param hasConfigureScreen if true, a provider with a config screen is returned.
     */
    public static LauncherAppWidgetProviderInfo findWidgetProvider(AbstractLauncherUiTest test,
            final boolean hasConfigureScreen) {
        LauncherAppWidgetProviderInfo info =
                test.getOnUiThread(new Callable<LauncherAppWidgetProviderInfo>() {
                    @Override
                    public LauncherAppWidgetProviderInfo call() throws Exception {
                        ComponentName cn = new ComponentName(getInstrumentation().getContext(),
                                hasConfigureScreen ? AppWidgetWithConfig.class
                                        : AppWidgetNoConfig.class);
                        Log.d(TAG, "findWidgetProvider componentName=" + cn.flattenToString());
                        return AppWidgetManagerCompat.getInstance(getTargetContext())
                                .findProvider(cn, Process.myUserHandle());
                    }
                });
        if (info == null) {
            throw new IllegalArgumentException("No valid widget provider");
        }
        return info;
    }

    /**
     * Drags an icon to the center of homescreen.
     *
     * @param icon object that is either app icon or shortcut icon
     */
    public static void dragToWorkspace(UiObject2 icon, boolean expectedToShowShortcuts) {
        Point center = icon.getVisibleCenter();

        // Action Down
        final long downTime = SystemClock.uptimeMillis();
        sendPointer(downTime, MotionEvent.ACTION_DOWN, center);

        UiObject2 dragLayer = findViewById(R.id.drag_layer);

        if (expectedToShowShortcuts) {
            // Make sure shortcuts show up, and then move a bit to hide them.
            assertNotNull(findViewById(R.id.deep_shortcuts_container));

            Point moveLocation = new Point(center);
            int distanceToMove =
                    getTargetContext().getResources().getDimensionPixelSize(
                            R.dimen.deep_shortcuts_start_drag_threshold) + 50;
            if (moveLocation.y - distanceToMove >= dragLayer.getVisibleBounds().top) {
                moveLocation.y -= distanceToMove;
            } else {
                moveLocation.y += distanceToMove;
            }
            movePointer(downTime, center, moveLocation);

            assertNull(findViewById(R.id.deep_shortcuts_container));
        }

        // Wait until Remove/Delete target is visible
        assertNotNull(findViewById(R.id.delete_target_text));

        Point moveLocation = dragLayer.getVisibleCenter();

        // Move to center
        movePointer(downTime, center, moveLocation);
        sendPointer(downTime, MotionEvent.ACTION_UP, moveLocation);

        // Wait until remove target is gone.
        getDevice().wait(Until.gone(getSelectorForId(R.id.delete_target_text)),
                AbstractLauncherUiTest.DEFAULT_UI_TIMEOUT);
    }

    private static void movePointer(long downTime, Point from, Point to) {
        while (!from.equals(to)) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            from.x = getNextMoveValue(to.x, from.x);
            from.y = getNextMoveValue(to.y, from.y);
            sendPointer(downTime, MotionEvent.ACTION_MOVE, from);
        }
    }

    private static int getNextMoveValue(int targetValue, int oldValue) {
        if (targetValue - oldValue > 10) {
            return oldValue + 10;
        } else if (targetValue - oldValue < -10) {
            return oldValue - 10;
        } else {
            return targetValue;
        }
    }

    public static void sendPointer(long downTime, int action, Point point) {
        MotionEvent event = MotionEvent.obtain(downTime,
                SystemClock.uptimeMillis(), action, point.x, point.y, 0);
        getInstrumentation().sendPointerSync(event);
        event.recycle();
    }

    /**
     * Opens widget tray and returns the recycler view.
     */
    public static UiObject2 openWidgetsTray() {
        final UiDevice device = getDevice();
        device.pressKeyCode(KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON);
        device.waitForIdle();
        return findViewById(R.id.widgets_list_view);
    }

    public static View findChildView(ViewGroup parent, Function<View, Boolean> condition) {
        for (int i = 0; i < parent.getChildCount(); ++i) {
            final View child = parent.getChildAt(i);
            if (condition.apply(child)) return child;
        }
        return null;
    }
}
