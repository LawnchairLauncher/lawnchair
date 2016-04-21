package com.android.launcher3;

import android.content.Intent;
import android.graphics.Point;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import java.util.List;

/**
 * Add an arbitrary widget from the widget picker very quickly to test potential race conditions.
 */
public class QuickAddWidgetTest extends InstrumentationTestCase {
    // Disabled because it's flaky and not particularly useful. But this class could still be useful
    // as an example if we want other UI tests in the future.
    private static final boolean DISABLED = true;

    private UiDevice mDevice;
    private String mTargetPackage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(getInstrumentation());

        // Set Launcher3 as home.
        mTargetPackage = getInstrumentation().getTargetContext().getPackageName();
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mTargetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getContext().startActivity(homeIntent);
        mDevice.wait(Until.hasObject(By.pkg(mTargetPackage).depth(0)), 3000);
    }

    public void testAddWidgetQuickly() throws Exception {
        if (DISABLED) return;
        mDevice.pressMenu(); // Enter overview mode.
        mDevice.wait(Until.findObject(By.text("Widgets")), 3000).click();
        UiObject2 calendarWidget = getWidgetByName("Clock");
        Point center = calendarWidget.getVisibleCenter();
        // Touch widget just long enough to pick it up (longPressTimeout), then let go immediately.
        getInstrumentation().sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, center.x, center.y, 0));
        Thread.sleep(ViewConfiguration.getLongPressTimeout() + 50);
        getInstrumentation().sendPointerSync(MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, center.x, center.y, 0));

        assertTrue("Drag was never started", isOnHomescreen());
    }

    private UiObject2 getWidgetByName(String name) {
        UiObject2 widgetsList = mDevice.wait(Until.findObject(By.res(mTargetPackage,
                "widgets_list_view")), 3000);
        do {
            UiObject2 widget = getVisibleWidgetByName(name);
            if (widget != null) {
                return widget;
            }
        } while (widgetsList.scroll(Direction.DOWN, 1f));
        return getVisibleWidgetByName(name);
    }

    private UiObject2 getVisibleWidgetByName(String name) {
        List<UiObject2> visibleWidgets = mDevice.wait(Until.findObjects(By.clazz(
                "android.widget.LinearLayout")), 3000);
        for (UiObject2 widget : visibleWidgets) {
            if (widget.hasObject(By.text(name))) {
                return widget;
            }
        }
        return null;
    }

    private boolean isOnHomescreen() {
        return mDevice.wait(Until.hasObject(By.res(mTargetPackage, "hotseat")), 3000);
    }
}
