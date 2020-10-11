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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.plugins.shared.LauncherOverlayManager.LauncherOverlay;

import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClientCallbacks;

import java.io.PrintWriter;

/**
 * Implements {@link LauncherOverlay} and passes all the corresponding events to {@link
 * LauncherClient}. {@see setClient}
 *
 * <p>Implements {@link LauncherClientCallbacks} and sends all the corresponding callbacks to {@link
 * Launcher}.
 */
public class OverlayCallbackImpl
        implements LauncherOverlay, LauncherClientCallbacks, LauncherOverlayManager,
        OnSharedPreferenceChangeListener {

    public static final String KEY_ENABLE_MINUS_ONE = "pref_enable_minus_one";

    private final Launcher mLauncher;
    private final LauncherClient mClient;

    private LauncherOverlayCallbacks mLauncherOverlayCallbacks;
    private boolean mWasOverlayAttached = false;

    public OverlayCallbackImpl(Launcher launcher) {
        SharedPreferences prefs = Utilities.getPrefs(launcher);

        mLauncher = launcher;
        mClient = new LauncherClient(mLauncher, this, getClientOptions(prefs));
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDeviceProvideChanged() {
        mClient.reattachOverlay();
    }

    @Override
    public void onAttachedToWindow() {
        mClient.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mClient.onDetachedFromWindow();
    }

    @Override
    public void dump(String prefix, PrintWriter w) {
        mClient.dump(prefix, w);
    }

    @Override
    public void openOverlay() {
        mClient.showOverlay(true);
    }

    @Override
    public void hideOverlay(boolean animate) {
        mClient.hideOverlay(animate);
    }

    @Override
    public void hideOverlay(int duration) {
        mClient.hideOverlay(duration);
    }

    @Override
    public boolean startSearch(byte[] config, Bundle extras) {
        return false;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        // Not called
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mClient.onStart();
    }

    @Override
    public void onActivityResumed(Activity activity) {
        mClient.onResume();
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mClient.onPause();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        mClient.onStop();
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) { }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mClient.onDestroy();
        mLauncher.getSharedPrefs().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_ENABLE_MINUS_ONE.equals(key)) {
            mClient.setClientOptions(getClientOptions(prefs));
        }
    }

    @Override
    public void onServiceStateChanged(boolean overlayAttached, boolean hotwordActive) {
        if (overlayAttached != mWasOverlayAttached) {
            mWasOverlayAttached = overlayAttached;
            mLauncher.setLauncherOverlay(overlayAttached ? this : null);
        }
    }

    @Override
    public void onOverlayScrollChanged(float progress) {
        if (mLauncherOverlayCallbacks != null) {
            mLauncherOverlayCallbacks.onScrollChanged(progress);
        }
    }

    @Override
    public void onScrollInteractionBegin() {
        mClient.startMove();
    }

    @Override
    public void onScrollInteractionEnd() {
        mClient.endMove();
    }

    @Override
    public void onScrollChange(float progress, boolean rtl) {
        mClient.updateMove(progress);
    }

    @Override
    public void setOverlayCallbacks(LauncherOverlayCallbacks callbacks) {
        mLauncherOverlayCallbacks = callbacks;
    }


    private LauncherClient.ClientOptions getClientOptions(SharedPreferences prefs) {
        return new LauncherClient.ClientOptions(
                prefs.getBoolean(KEY_ENABLE_MINUS_ONE, true),
                true, /* enableHotword */
                true /* enablePrewarming */
        );
    }
}
