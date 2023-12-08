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
package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.MotionEvent.ACTION_DOWN;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.launcher3.tapl.LauncherInstrumentation;
import com.android.launcher3.ui.AbstractLauncherUiTest;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SecondaryDisplayLauncher}.
 * TODO (b/242776943): Remove anti-patterns & migrate prediction row tests to Quickstep directory
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class TaplSecondaryDisplayLauncherTest extends AbstractLauncherUiTest {
    private static final int WAIT_TIME_MS = 5000;
    private static final int LONG_PRESS_DURATION_MS = 1000;
    private static final int DRAG_TIME_MS = 160;

    private static final String PINNED_APPS_KEY = "pinned_apps";

    // Variables required to coordinate drag steps.
    private Point mStartPoint;
    private Point mEndPoint;
    private long mDownTime;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        setDragNDropFlag(true);
    }

    @After
    public void tearDown() {
        mTargetContext.getSharedPreferences(PINNED_APPS_KEY, MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    @Ignore
    public void initializeSecondaryDisplayLauncher_allAppsButtonVisible() {
        assertThat(findObjectByResourceName("all_apps_button")).isNotNull();
    }

    @Test
    @Ignore
    public void allAppsButtonTap_opensAppDrawer() {
        openAppDrawer();
        assertThat(findObjectByResourceName("search_container_all_apps")).isNotNull();
    }

    @Test
    @Ignore("Launcher3 without quickstep doesn't have a predictions row.")
    public void appDrawerOpened_predictionRowAppDividerVisible() {
        openAppDrawer();
        assertThat(findObjectByResourceName("apps_divider_view")).isNotNull();
    }

    @Test
    @Ignore
    public void dragNDropDisabled_pinIconAddsToWorkspace() {
        setDragNDropFlag(false);
        openAppDrawer();
        UiObject2 app = findDescendantByResourceName(
                findObjectByResourceName("apps_list_view"), "icon");
        app.click(LONG_PRESS_DURATION_MS);
        UiObject2 popupContainer = findObjectByResourceName("popup_container");
        assertThat(popupContainer).isNotNull();
        UiObject2 pinIcon = findDescendantByTextOrDesc(popupContainer, "Add to home screen");
        assertThat(pinIcon).isNotNull();
        pinIcon.click();
        String appName = app.getContentDescription();
        assertThat(findAppInWorkspace(appName)).isNotNull();
    }

    @Test
    @Ignore
    public void pressBackFromAllApps_popupMenuOpen_returnsToWorkspace() {
        openAppDrawer();
        assertThat(findObjectByResourceName("search_container_all_apps")).isNotNull();

        findDescendantByResourceName(findObjectByResourceName("apps_list_view"), "icon")
                .click(LONG_PRESS_DURATION_MS);
        assertThat(findObjectByResourceName("popup_container")).isNotNull();

        // First back press should close only popup menu.
        mDevice.pressBack();
        assertThat(findObjectByResourceName("search_container_all_apps")).isNotNull();
        assertThat(findObjectByResourceName("popup_container")).isNull();

        // Second back press should close app drawer.
        mDevice.pressBack();
        assertThat(findObjectByResourceName("popup_container")).isNull();
        assertThat(findObjectByResourceName("search_container_all_apps")).isNull();
    }

    @Test
    @Ignore("Launcher3 without quickstep doesn't have a predictions row.")
    public void dragNDropFromPredictionsRow_pinToGrid() {
        openAppDrawer();
        assertThat(findObjectByResourceName("prediction_row")).isNotNull();
        String appName = startDragFromPredictionRow();
        moveAppToCenterOfScreen();
        dropApp();

        // Ensure app was added.
        assertThat(findAppInWorkspace(appName)).isNotNull();
    }

    @Test
    @Ignore
    public void dragNDropFromAppDrawer_pinToGrid() {
        openAppDrawer();
        String draggedAppName = startDragFromAllApps();
        moveAppToCenterOfScreen();
        dropApp();

        // Ensure app was added.
        assertThat(findAppInWorkspace(draggedAppName)).isNotNull();
    }

    @Test
    @Ignore
    public void tapRemoveButton_unpinApp() {
        openAppDrawer();
        String draggedAppName = startDragFromAllApps();
        moveAppToCenterOfScreen();
        dropApp();
        removeAppByName(draggedAppName);
        assertThat(findAppInWorkspace(draggedAppName)).isNull();
    }

    private void openAppDrawer() {
        UiObject2 allAppsButton = findObjectByResourceName("all_apps_button");
        assertThat(allAppsButton).isNotNull();
        allAppsButton.click();
    }

    private String startDragFromAllApps() {
        // Find app from app drawer.
        UiObject2 allApps = findObjectByResourceName("apps_list_view");
        assertThat(allApps).isNotNull();
        UiObject2 icon = findDescendantByResourceName(allApps, "icon");
        assertThat(icon).isNotNull();
        String appName = icon.getContentDescription();

        // Start drag action.
        mDownTime = SystemClock.uptimeMillis();
        mStartPoint = icon.getVisibleCenter();
        mEndPoint = new Point(mStartPoint.x, mStartPoint.y);
        mLauncher.sendPointer(mDownTime, mDownTime, ACTION_DOWN, mStartPoint,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        assertThat(findObjectByResourceName("popup_container")).isNotNull();
        return appName;
    }

    private String startDragFromPredictionRow() {
        // Find app from predictions.
        UiObject2 predictionRow = findObjectByResourceName("prediction_row");
        assertThat(predictionRow).isNotNull();

        UiObject2 icon = findDescendantByResourceName(predictionRow, "icon");
        assertThat(icon).isNotNull();

        String appName = icon.getContentDescription();
        UiObject2 app = findDescendantByAppName(predictionRow, appName);
        assertThat(app).isNotNull();

        // Start drag action.
        mDownTime = SystemClock.uptimeMillis();
        mStartPoint = icon.getVisibleCenter();
        mEndPoint = new Point(mStartPoint.x, mStartPoint.y);
        mLauncher.sendPointer(mDownTime, mDownTime, ACTION_DOWN, mStartPoint,
                LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
        assertThat(findObjectByResourceName("popup_container")).isNotNull();
        return appName;
    }

    private void moveAppToCenterOfScreen() {
        mEndPoint.set(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2);
        mLauncher.movePointer(mDownTime, SystemClock.uptimeMillis(), DRAG_TIME_MS, true,
                mStartPoint, mEndPoint, LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
    }

    private void dropApp() {
        mLauncher.sendPointer(mDownTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP,
                mEndPoint, LauncherInstrumentation.GestureScope.DONT_EXPECT_PILFER);
    }

    private void removeAppByName(String appName) {
        // Find app within home screen.
        UiObject2 app = findDescendantByAppName(findObjectByResourceName("workspace_grid"),
                appName);
        if (app == null) return;

        // Open app's popup container.
        app.click(LONG_PRESS_DURATION_MS);
        UiObject2 popupContainer = findObjectByResourceName("popup_container");
        assertThat(popupContainer).isNotNull();

        // Grab & click remove button.
        UiObject2 removeButton = findDescendantByTextOrDesc(popupContainer, "Remove");
        assertThat(removeButton).isNotNull();
        removeButton.click();
    }

    private UiObject2 findAppInWorkspace(String appName) {
        UiObject2 workspace = findObjectByResourceName("workspace_grid");
        return findDescendantByAppName(workspace, appName);
    }

    private UiObject2 findObjectByResourceName(String resourceName) {
        return mDevice.wait(Until.findObject(By.res(mTargetPackage, resourceName)), WAIT_TIME_MS);
    }

    private UiObject2 findDescendantByResourceName(UiObject2 outerObject,
            String resourceName) {
        assertThat(outerObject).isNotNull();
        return outerObject.findObject(By.res(mTargetPackage, resourceName));
    }

    private UiObject2 findDescendantByAppName(UiObject2 outerObject, String appName) {
        assertThat(outerObject).isNotNull();
        return outerObject.findObject(By.clazz(TextView.class).text(appName)
                .pkg(mDevice.getLauncherPackageName()));
    }

    private UiObject2 findDescendantByTextOrDesc(UiObject2 outerObject, String content) {
        assertThat(outerObject).isNotNull();
        UiObject2 innerObject = outerObject.findObject(By.desc(content));
        if (innerObject == null) innerObject = outerObject.findObject(By.text(content));
        return innerObject;
    }

    private void startSecondaryDisplayActivity() {
        mTargetContext.startActivity((
                new Intent(mTargetContext, SecondaryDisplayLauncher.class).addFlags(
                        FLAG_ACTIVITY_NEW_TASK)));
    }

    private void setDragNDropFlag(Boolean status) {
        startSecondaryDisplayActivity();
    }
}
