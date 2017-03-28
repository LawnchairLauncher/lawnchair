package com.android.launcher3.testing;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.allapps.AllAppsSearchBarController;
import com.android.launcher3.util.ComponentKey;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a very trivial LauncherExtension. It primarily serves as a simple
 * class to exercise the LauncherOverlay interface.
 */
public class LauncherExtension extends Launcher {

    //------ Activity methods -------//
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setLauncherCallbacks(new LauncherExtensionCallbacks());
        super.onCreate(savedInstanceState);
    }

    public class LauncherExtensionCallbacks implements LauncherCallbacks {

        @Override
        public void preOnCreate() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
        }

        @Override
        public void preOnResume() {
        }

        @Override
        public void onResume() {
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onStop() {
        }

        @Override
        public void onPause() {
        }

        @Override
        public void onDestroy() {
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
        }

        @Override
        public void onPostCreate(Bundle savedInstanceState) {
        }

        @Override
        public void onNewIntent(Intent intent) {
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions,
                int[] grantResults) {
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
        }

        @Override
        public boolean onPrepareOptionsMenu(Menu menu) {
            return false;
        }

        @Override
        public void dump(String prefix, FileDescriptor fd, PrintWriter w, String[] args) {
        }

        @Override
        public void onHomeIntent() {
        }

        @Override
        public boolean handleBackPressed() {
            return false;
        }

        @Override
        public void onTrimMemory(int level) {
        }

        @Override
        public void onLauncherProviderChange() {
        }

        @Override
        public void finishBindingItems(boolean upgradePath) {
        }

        @Override
        public void bindAllApplications(ArrayList<AppInfo> apps) {
        }

        @Override
        public void onWorkspaceLockedChanged() {
        }

        @Override
        public void onInteractionBegin() {
        }

        @Override
        public void onInteractionEnd() {
        }

        @Override
        public boolean startSearch(String initialQuery, boolean selectInitialQuery,
                Bundle appSearchData) {
            return false;
        }

        CustomContentCallbacks mCustomContentCallbacks = new CustomContentCallbacks() {

            // Custom content is completely shown. {@code fromResume} indicates whether this was caused
            // by a onResume or by scrolling otherwise.
            public void onShow(boolean fromResume) {
            }

            // Custom content is completely hidden
            public void onHide() {
            }

            // Custom content scroll progress changed. From 0 (not showing) to 1 (fully showing).
            public void onScrollProgressChanged(float progress) {

            }

            // Indicates whether the user is allowed to scroll away from the custom content.
            public boolean isScrollingAllowed() {
                return true;
            }

        };

        @Override
        public boolean hasCustomContentToLeft() {
            return true;
        }

        @Override
        public void populateCustomContentContainer() {
            FrameLayout customContent = new FrameLayout(LauncherExtension.this);
            customContent.setBackgroundColor(Color.GRAY);
            addToCustomContentPage(customContent, mCustomContentCallbacks, "");
        }

        @Override
        public View getQsbBar() {
            return null;
        }

        @Override
        public Bundle getAdditionalSearchWidgetOptions() {
            return new Bundle();
        }

        @Override
        public boolean shouldMoveToDefaultScreenOnHomeIntent() {
            return true;
        }

        @Override
        public boolean hasSettings() {
            return false;
        }

        @Override
        public AllAppsSearchBarController getAllAppsSearchBarController() {
            return null;
        }

        @Override
        public List<ComponentKey> getPredictedApps() {
            // To debug app predictions, enable AlphabeticalAppsList#DEBUG_PREDICTIONS
            return new ArrayList<>();
        }

        @Override
        public int getSearchBarHeight() {
            return SEARCH_BAR_HEIGHT_NORMAL;
        }

        @Override
        public void setLauncherSearchCallback(Object callbacks) {
            // Do nothing
        }

        @Override
        public void onAttachedToWindow() {
        }

        @Override
        public void onDetachedFromWindow() {
        }

        @Override
        public boolean shouldShowDiscoveryBounce() {
            return false;
        }
    }
}
