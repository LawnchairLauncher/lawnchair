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
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.util.ComponentKeyMapper;

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
    void preOnCreate();
    void onCreate(Bundle savedInstanceState);
    void preOnResume();
    void onResume();
    void onStart();
    void onStop();
    void onPause();
    void onDestroy();
    void onSaveInstanceState(Bundle outState);
    void onPostCreate(Bundle savedInstanceState);
    void onNewIntent(Intent intent);
    void onActivityResult(int requestCode, int resultCode, Intent data);
    void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults);
    void onWindowFocusChanged(boolean hasFocus);
    void onAttachedToWindow();
    void onDetachedFromWindow();
    boolean onPrepareOptionsMenu(Menu menu);
    void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args);
    void onHomeIntent();
    boolean handleBackPressed();
    void onTrimMemory(int level);

    /*
     * Extension points for providing custom behavior on certain user interactions.
     */
    void onLauncherProviderChange();
    void finishBindingItems(final boolean upgradePath);
    void bindAllApplications(ArrayList<AppInfo> apps);
    void onInteractionBegin();
    void onInteractionEnd();

    @Deprecated
    void onWorkspaceLockedChanged();

    /**
     * Starts a search with {@param initialQuery}. Return false if search was not started.
     */
    boolean startSearch(
            String initialQuery, boolean selectInitialQuery, Bundle appSearchData);
    boolean hasCustomContentToLeft();
    void populateCustomContentContainer();
    View getQsbBar();
    Bundle getAdditionalSearchWidgetOptions();

    /*
     * Extensions points for adding / replacing some other aspects of the Launcher experience.
     */
    boolean shouldMoveToDefaultScreenOnHomeIntent();
    boolean hasSettings();
    List<ComponentKeyMapper<AppInfo>> getPredictedApps();
    int SEARCH_BAR_HEIGHT_NORMAL = 0, SEARCH_BAR_HEIGHT_TALL = 1;
    /** Must return one of {@link #SEARCH_BAR_HEIGHT_NORMAL} or {@link #SEARCH_BAR_HEIGHT_TALL} */
    int getSearchBarHeight();
}
