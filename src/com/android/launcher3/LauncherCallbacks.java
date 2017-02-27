/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.allapps.AllAppsSearchBarController;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.util.ComponentKey;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * LauncherCallbacks is an interface used to extend the Launcher activity. It includes many hooks
 * in order to add additional functionality. Some of these are very general, and give extending
 * classes the ability to react to Activity life-cycle or specific user interactions. Others
 * are more specific and relate to replacing parts of the application, for example, the search
 * interface or the wallpaper picker.
 */
public interface LauncherCallbacks {

    /*
     * Activity life-cycle methods. These methods are triggered after
     * the code in the corresponding Launcher method is executed.
     */
    public void preOnCreate();
    public void onCreate(Bundle savedInstanceState);
    public void preOnResume();
    public void onResume();
    public void onStart();
    public void onStop();
    public void onPause();
    public void onDestroy();
    public void onSaveInstanceState(Bundle outState);
    public void onPostCreate(Bundle savedInstanceState);
    public void onNewIntent(Intent intent);
    public void onActivityResult(int requestCode, int resultCode, Intent data);
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults);
    public void onWindowFocusChanged(boolean hasFocus);
    public void onAttachedToWindow();
    public void onDetachedFromWindow();
    public boolean onPrepareOptionsMenu(Menu menu);
    public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args);
    public void onHomeIntent();
    public boolean handleBackPressed();
    public void onTrimMemory(int level);

    /*
     * Extension points for providing custom behavior on certain user interactions.
     */
    public void onLauncherProviderChange();
    public void finishBindingItems(final boolean upgradePath);
    public void bindAllApplications(ArrayList<AppInfo> apps);
    public void onInteractionBegin();
    public void onInteractionEnd();

    @Deprecated
    public void onWorkspaceLockedChanged();

    /**
     * Starts a search with {@param initialQuery}. Return false if search was not started.
     */
    public boolean startSearch(
            String initialQuery, boolean selectInitialQuery, Bundle appSearchData);
    public boolean hasCustomContentToLeft();
    public void populateCustomContentContainer();
    public View getQsbBar();
    public Bundle getAdditionalSearchWidgetOptions();

    /*
     * Extensions points for adding / replacing some other aspects of the Launcher experience.
     */
    public boolean shouldMoveToDefaultScreenOnHomeIntent();
    public boolean hasSettings();
    public AllAppsSearchBarController getAllAppsSearchBarController();
    public List<ComponentKey> getPredictedApps();
    public static final int SEARCH_BAR_HEIGHT_NORMAL = 0, SEARCH_BAR_HEIGHT_TALL = 1;
    /** Must return one of {@link #SEARCH_BAR_HEIGHT_NORMAL} or {@link #SEARCH_BAR_HEIGHT_TALL} */
    public int getSearchBarHeight();

    /**
     * Sets the callbacks to allow reacting the actions of search overlays of the launcher.
     *
     * @param callbacks A set of callbacks to the Launcher, is actually a LauncherSearchCallback,
     *                  but for implementation purposes is passed around as an object.
     */
    public void setLauncherSearchCallback(Object callbacks);

    public boolean shouldShowDiscoveryBounce();
}
