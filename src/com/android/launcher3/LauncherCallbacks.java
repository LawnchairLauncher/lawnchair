package com.android.launcher3;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import com.android.launcher3.allapps.AllAppsSearchBarController;
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
    public void onClickAllAppsButton(View v);
    public void bindAllApplications(ArrayList<AppInfo> apps);
    public void onClickFolderIcon(View v);
    public void onClickAppShortcut(View v);
    @Deprecated
    public void onClickPagedViewIcon(View v);
    public void onClickWallpaperPicker(View v);
    public void onClickSettingsButton(View v);
    public void onClickAddWidgetButton(View v);
    public void onPageSwitch(View newPage, int newPageIndex);
    public void onWorkspaceLockedChanged();
    public void onDragStarted(View view);
    public void onInteractionBegin();
    public void onInteractionEnd();

    public boolean providesSearch();
    public boolean startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, Rect sourceBounds);
    @Deprecated
    public boolean startSearchFromAllApps(String query);
    public boolean hasCustomContentToLeft();
    public void populateCustomContentContainer();
    public View getQsbBar();
    public Bundle getAdditionalSearchWidgetOptions();

    /*
     * Extensions points for adding / replacing some other aspects of the Launcher experience.
     */
    public Intent getFirstRunActivity();
    public boolean hasFirstRunActivity();
    public boolean hasDismissableIntroScreen();
    public View getIntroScreen();
    public boolean shouldMoveToDefaultScreenOnHomeIntent();
    public boolean hasSettings();
    public boolean overrideWallpaperDimensions();
    public boolean isLauncherPreinstalled();
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
}
