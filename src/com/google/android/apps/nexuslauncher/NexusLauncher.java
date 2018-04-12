package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.graphics.ColorUtils;
import android.view.Menu;
import android.view.View;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherCallbacks;
import com.android.launcher3.LauncherExterns;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.Themes;
import com.google.android.apps.nexuslauncher.search.ItemInfoUpdateReceiver;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceView;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClientService;
import com.google.android.libraries.gsa.launcherclient.StaticInteger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class NexusLauncher {
    private final Launcher mLauncher;
    final LauncherCallbacks mCallbacks;
    private boolean mFeedRunning;
    private final LauncherExterns mExterns;
    private boolean mRunning;
    LauncherClient mClient;
    private NexusLauncherOverlay mOverlay;
    private boolean mStarted;
    private final Bundle mUiInformation = new Bundle();
    private ItemInfoUpdateReceiver mItemInfoUpdateReceiver;

    public NexusLauncher(NexusLauncherActivity activity) {
        mLauncher = activity;
        mExterns = activity;
        mCallbacks = new NexusLauncherCallbacks();
        mExterns.setLauncherCallbacks(mCallbacks);
    }

    class NexusLauncherCallbacks implements LauncherCallbacks, SharedPreferences.OnSharedPreferenceChangeListener, WallpaperColorInfo.OnChangeListener {
        private SmartspaceView mSmartspace;
        private final FeedReconnector mFeedReconnector = new FeedReconnector();

        private ItemInfoUpdateReceiver getUpdateReceiver() {
            if (mItemInfoUpdateReceiver == null) {
                mItemInfoUpdateReceiver = new ItemInfoUpdateReceiver(mLauncher, mCallbacks);
            }
            return mItemInfoUpdateReceiver;
        }

        public void bindAllApplications(final ArrayList<AppInfo> list) {
            getUpdateReceiver().di();
        }

        public void dump(final String s, final FileDescriptor fileDescriptor, final PrintWriter printWriter, final String[] array) {
            SmartspaceController.get(mLauncher).cX(s, printWriter);
        }

        public void finishBindingItems(final boolean b) {
        }

        public List<ComponentKeyMapper<AppInfo>> getPredictedApps() {
            return ((CustomAppPredictor) mLauncher.getUserEventDispatcher()).getPredictions();
        }

        @Override
        public int getSearchBarHeight() {
            return LauncherCallbacks.SEARCH_BAR_HEIGHT_NORMAL;
        }

        public boolean handleBackPressed() {
            return false;
        }

        public boolean hasCustomContentToLeft() {
            return false;
        }

        public boolean hasSettings() {
            return true;
        }

        public void onActivityResult(final int n, final int n2, final Intent intent) {
        }

        public void onAttachedToWindow() {
            mClient.onAttachedToWindow();
            mFeedReconnector.start();
        }

        public void onCreate(final Bundle bundle) {
            SharedPreferences prefs = Utilities.getPrefs(mLauncher);
            mOverlay = new NexusLauncherOverlay(mLauncher);
            mClient = new LauncherClient(mLauncher, mOverlay, new StaticInteger(
                    (prefs.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) ? 1 : 0) | 2 | 4 | 8));
            mOverlay.setClient(mClient);

            prefs.registerOnSharedPreferenceChangeListener(this);

            SmartspaceController.get(mLauncher).cW();
            mSmartspace = mLauncher.findViewById(R.id.search_container_workspace);

            mUiInformation.putInt("system_ui_visibility", mLauncher.getWindow().getDecorView().getSystemUiVisibility());
            WallpaperColorInfo instance = WallpaperColorInfo.getInstance(mLauncher);
            instance.addOnChangeListener(this);
            onExtractedColorsChanged(instance);

            getUpdateReceiver().onCreate();
        }

        public void onDestroy() {
            LauncherClient launcherClient = mClient;
            if (!launcherClient.mDestroyed) {
                launcherClient.mActivity.unregisterReceiver(launcherClient.googleInstallListener);
            }

            launcherClient.mDestroyed = true;
            launcherClient.mBaseService.disconnect();

            if (launcherClient.mOverlayCallback != null) {
                launcherClient.mOverlayCallback.mClient = null;
                launcherClient.mOverlayCallback.mWindowManager = null;
                launcherClient.mOverlayCallback.mWindow = null;
                launcherClient.mOverlayCallback = null;
            }

            LauncherClientService service = launcherClient.mLauncherService;
            LauncherClient client = service.getClient();
            if (client != null && client.equals(launcherClient)) {
                service.mClient = null;
                if (!launcherClient.mActivity.isChangingConfigurations()) {
                    service.disconnect();
                    if (LauncherClientService.sInstance == service) {
                        LauncherClientService.sInstance = null;
                    }
                }
            }

            Utilities.getPrefs(mLauncher).unregisterOnSharedPreferenceChangeListener(this);
            WallpaperColorInfo.getInstance(mLauncher).removeOnChangeListener(this);

            getUpdateReceiver().onDestroy();
        }

        public void onDetachedFromWindow() {
            mFeedReconnector.stop();
            mClient.onDetachedFromWindow();
        }

        public void onHomeIntent() {
            mClient.hideOverlay(mFeedRunning);
        }

        public void onInteractionBegin() {
        }

        public void onInteractionEnd() {
        }

        public void onLauncherProviderChange() {
        }

        public void onNewIntent(final Intent intent) {
        }

        public void onPause() {
            mRunning = false;
            mClient.onPause();

            if (mSmartspace != null) {
                mSmartspace.onPause();
            }
        }

        public void onPostCreate(final Bundle bundle) {
        }

        public boolean onPrepareOptionsMenu(final Menu menu) {
            return false;
        }

        public void onRequestPermissionsResult(final int n, final String[] array, final int[] array2) {
        }

        public void onResume() {
            mRunning = true;
            if (mStarted) {
                mFeedRunning = true;
            }

            mClient.onResume();

            if (mSmartspace != null) {
                mSmartspace.onResume();
            }
        }

        public void onSaveInstanceState(final Bundle bundle) {
        }

        public void onStart() {
            mStarted = true;
            mClient.onStart();
        }

        public void onStop() {
            mStarted = false;
            mClient.onStop();
            if (!mRunning) {
                mFeedRunning = false;
            }
            if (mOverlay.mFlagsChanged) {
                mOverlay.mLauncher.recreate();
            }
        }

        public void onTrimMemory(int n) {
        }

        public void onWindowFocusChanged(boolean hasFocus) {
        }

        public void onWorkspaceLockedChanged() {
        }

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

        public void preOnCreate() {
            DrawableFactory.get(mLauncher);
        }

        public void preOnResume() {
        }

        public boolean shouldMoveToDefaultScreenOnHomeIntent() {
            return true;
        }

        public boolean startSearch(String s, boolean b, Bundle bundle) {
            View gIcon = mLauncher.findViewById(R.id.g_icon);
            while (gIcon != null && !gIcon.isClickable()) {
                if (gIcon.getParent() instanceof View) {
                    gIcon = (View)gIcon.getParent();
                } else {
                    gIcon = null;
                }
            }
            if (gIcon != null && gIcon.performClick()) {
                mExterns.clearTypedText();
                return true;
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (SettingsActivity.ENABLE_MINUS_ONE_PREF.equals(key)) {
                LauncherClient launcherClient = mClient;
                StaticInteger i = new StaticInteger(
                        (sharedPreferences.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) ? 1 : 0) | 2 | 4 | 8);
                if (i.mData != launcherClient.mFlags) {
                    launcherClient.mFlags = i.mData;
                    if (launcherClient.mLayoutParams != null) {
                        launcherClient.exchangeConfig();
                    }
                }
            }
        }

        @Override
        public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
            int alpha = mLauncher.getResources().getInteger(R.integer.extracted_color_gradient_alpha);

            mUiInformation.putInt("background_color_hint", primaryColor(wallpaperColorInfo, mLauncher, alpha));
            mUiInformation.putInt("background_secondary_color_hint", secondaryColor(wallpaperColorInfo, mLauncher, alpha));
            mUiInformation.putBoolean("is_background_dark", Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark));

            mClient.redraw(mUiInformation);
        }

        class FeedReconnector implements Runnable {
            private final static int MAX_RETRIES = 10;
            private final static int RETRY_DELAY_MS = 500;

            private final Handler mHandler = new Handler();
            private int mFeedConnectionTries;

            void start() {
                stop();
                mFeedConnectionTries = 0;
                mHandler.post(this);
            }

            void stop() {
                mHandler.removeCallbacks(this);
            }

            @Override
            public void run() {
                if (Utilities.getPrefs(mLauncher).getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) &&
                        !mClient.mDestroyed &&
                        mClient.mLayoutParams != null &&
                        !mOverlay.mAttached &&
                        mFeedConnectionTries++ < MAX_RETRIES) {
                    mClient.exchangeConfig();
                    mHandler.postDelayed(this, RETRY_DELAY_MS);
                }
            }
        }
    }

    public static int primaryColor(WallpaperColorInfo wallpaperColorInfo, Context context, int alpha) {
        return compositeAllApps(ColorUtils.setAlphaComponent(wallpaperColorInfo.getMainColor(), alpha), context);
    }

    public static int secondaryColor(WallpaperColorInfo wallpaperColorInfo, Context context, int alpha) {
        return compositeAllApps(ColorUtils.setAlphaComponent(wallpaperColorInfo.getSecondaryColor(), alpha), context);
    }

    private static int compositeAllApps(int color, Context context) {
        return ColorUtils.compositeColors(Themes.getAttrColor(context, R.attr.allAppsScrimColor), color);
    }
}
