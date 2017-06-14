package com.android.launcher3;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.allapps.AllAppsSearchBarController;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.util.ComponentKey;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

class SuperLauncherCallbacks implements LauncherCallbacks, SharedPreferences.OnSharedPreferenceChangeListener {
    private Launcher mLauncher;
    private com.android.launcher3.reflectionevents.a cF;
    private com.android.launcher3.reflection.l cD;

    public SuperLauncherCallbacks(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void preOnCreate() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = Utilities.getPrefs(mLauncher);
        this.cD = com.android.launcher3.reflection.l.getInstance(mLauncher);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void preOnResume() {

    }

    @Override
    public void onResume() {
        this.cD.aF(0L);
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {

    }

    @Override
    public void onAttachedToWindow() {

    }

    @Override
    public void onDetachedFromWindow() {

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
        this.cD.aF(1000L);
    }

    @Override
    public void finishBindingItems(boolean upgradePath) {

    }

    @Override
    public void bindAllApplications(ArrayList<AppInfo> apps) {

    }

    @Override
    public void onInteractionBegin() {

    }

    @Override
    public void onInteractionEnd() {

    }

    @Override
    public void onWorkspaceLockedChanged() {

    }

    @Override
    public boolean startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData) {
        return false;
    }

    @Override
    public boolean hasCustomContentToLeft() {
        return false;
    }

    @Override
    public void populateCustomContentContainer() {

    }

    @Override
    public View getQsbBar() {
        return null;
    }

    @Override
    public Bundle getAdditionalSearchWidgetOptions() {
        return null;
    }

    @Override
    public UserEventDispatcher getUserEventDispatcher() {
        if (this.cF == null) {
            this.cF = new com.android.launcher3.reflectionevents.a();
            this.cF.bW(this.cD.aG());
            this.cF.bW(new com.android.launcher3.reflectionevents.c(mLauncher));
        }
        return this.cF;
    }

    @Override
    public boolean shouldMoveToDefaultScreenOnHomeIntent() {
        return true;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public AllAppsSearchBarController getAllAppsSearchBarController() {
        return null;
    }

    @Override
    public List<ComponentKey> getPredictedApps() {
        ArrayList<ComponentKey> list = new ArrayList<>();
        if (mLauncher.getSharedPrefs().getBoolean("pref_show_predictions", true)) {
            final String string = com.android.launcher3.reflection.m.aJ(mLauncher).getString("reflection_last_predictions", null);
            if (!TextUtils.isEmpty(string)) {
                final String[] split = string.split(";");
                for (int i = 0; i < split.length; ++i) {
                    list.add(new ComponentKey(mLauncher, split[i]));
                }
            }
        }
        return list;
    }

    @Override
    public int getSearchBarHeight() {
        return 0;
    }

    @Override
    public void setLauncherSearchCallback(Object callbacks) {

    }

    @Override
    public boolean shouldShowDiscoveryBounce() {
        final SharedPreferences sharedPreferences = mLauncher.getSharedPreferences("com.android.launcher3.device.prefs", 0);
        if (sharedPreferences.getBoolean("pref_show_discovery_bounce", false)) {
            sharedPreferences.edit().remove("pref_show_discovery_bounce").apply();
            return true;
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        mLauncher.tryAndUpdatePredictedApps();
    }
}