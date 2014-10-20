package com.android.launcher3;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

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
    public void onWindowFocusChanged(boolean hasFocus);
    public boolean onPrepareOptionsMenu(Menu menu);
    public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args);
    public void onHomeIntent();
    public boolean handleBackPressed();

    /*
     * Extension points for providing custom behavior on certain user interactions.
     */
    public void onLauncherProviderChange();
    public void finishBindingItems(final boolean upgradePath);
    public void onClickAllAppsButton(View v);
    public void bindAllApplications(ArrayList<AppInfo> apps);
    public void onClickFolderIcon(View v);
    public void onClickAppShortcut(View v);
    public void onClickPagedViewIcon(View v);
    public void onClickWallpaperPicker(View v);
    public void onClickSettingsButton(View v);
    public void onClickAddWidgetButton(View v);
    public void onPageSwitch(View newPage, int newPageIndex);
    public void onWorkspaceLockedChanged();
    public void onDragStarted(View view);
    public void onInteractionBegin();
    public void onInteractionEnd();

    /*
     * Extension points for replacing the search experience
     */
    public boolean forceDisableVoiceButtonProxy();
    public boolean providesSearch();
    public boolean startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, Rect sourceBounds);
    public void startVoice();
    public boolean hasCustomContentToLeft();
    public void populateCustomContentContainer();
    public View getQsbBar();

    /*
     * Extensions points for adding / replacing some other aspects of the Launcher experience.
     */
    public Intent getFirstRunActivity();
    public boolean hasFirstRunActivity();
    public boolean hasDismissableIntroScreen();
    public View getIntroScreen();
    public boolean shouldMoveToDefaultScreenOnHomeIntent();
    public boolean hasSettings();
    public ComponentName getWallpaperPickerComponent();
    public boolean overrideWallpaperDimensions();
    public boolean isLauncherPreinstalled();

}
