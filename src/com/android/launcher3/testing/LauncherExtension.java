package com.android.launcher3.testing;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

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
        public void bindAllApplications(ArrayList<AppInfo> apps) {
        }

        @Override
        public boolean startSearch(String initialQuery, boolean selectInitialQuery,
                Bundle appSearchData) {
            return false;
        }

        @Override
        public boolean hasSettings() {
            return false;
        }

        @Override
        public void onAttachedToWindow() {
        }

        @Override
        public void onDetachedFromWindow() {
        }
    }
}
