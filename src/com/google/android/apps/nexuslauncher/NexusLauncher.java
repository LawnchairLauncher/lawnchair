package com.google.android.apps.nexuslauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.graphics.ColorUtils;
import android.view.View;
import ch.deletescape.lawnchair.settings.ui.SettingsActivity;
import com.android.launcher3.*;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.WallpaperColorInfo;
import com.android.launcher3.util.Themes;
import com.google.android.apps.nexuslauncher.PredictionUiStateManager.Client;
import com.google.android.apps.nexuslauncher.qsb.QsbAnimationController;
import com.google.android.apps.nexuslauncher.reflection.ReflectionClient;
import com.google.android.apps.nexuslauncher.search.ItemInfoUpdateReceiver;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceController;
import com.google.android.apps.nexuslauncher.smartspace.SmartspaceView;
import com.google.android.apps.nexuslauncher.utils.ActionIntentFilter;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;
import com.google.android.libraries.gsa.launcherclient.LauncherClientService;
import com.google.android.libraries.gsa.launcherclient.StaticInteger;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class NexusLauncher {
    private final Launcher mLauncher;
    final NexusLauncherCallbacks mCallbacks;
    private boolean mFeedRunning;
    private final LauncherExterns mExterns;
    private boolean mRunning;
    LauncherClient mClient;
    private NexusLauncherOverlay mOverlay;
    private boolean mStarted;
    private final Bundle mUiInformation = new Bundle();
    private ItemInfoUpdateReceiver mItemInfoUpdateReceiver;
    QsbAnimationController mQsbAnimationController;
    private Handler handler = new Handler(LauncherModel.getUiWorkerLooper());

    public NexusLauncher(NexusLauncherActivity activity) {
        mLauncher = activity;
        mExterns = activity;
        mCallbacks = new NexusLauncherCallbacks();
        mExterns.setLauncherCallbacks(mCallbacks);
        mLauncher.addOnDeviceProfileChangeListener(dp -> mClient.redraw());
    }

    void registerSmartspaceView(SmartspaceView smartspace) {
        mCallbacks.registerSmartspaceView(smartspace);
    }

    class NexusLauncherCallbacks implements LauncherCallbacks, SharedPreferences.OnSharedPreferenceChangeListener, WallpaperColorInfo.OnChangeListener {
        private Set<SmartspaceView> mSmartspaceViews = Collections.newSetFromMap(new WeakHashMap<>());
        private final FeedReconnector mFeedReconnector = new FeedReconnector();

        private final Runnable mUpdatePredictionsIfResumed = this::updatePredictionsIfResumed;

        private ItemInfoUpdateReceiver getUpdateReceiver() {
            if (mItemInfoUpdateReceiver == null) {
                mItemInfoUpdateReceiver = new ItemInfoUpdateReceiver(mLauncher, mCallbacks);
            }
            return mItemInfoUpdateReceiver;
        }

        public void bindAllApplications(final ArrayList<AppInfo> list) {
            getUpdateReceiver().di();
            PredictionUiStateManager.getInstance(mLauncher).dispatchOnChange();
            mLauncher.getUserEventDispatcher().updatePredictions();
        }

        public void dump(final String s, final FileDescriptor fileDescriptor, final PrintWriter printWriter, final String[] array) {
            SmartspaceController.get(mLauncher).cX(s, printWriter);
        }

        public void finishBindingItems(final boolean b) {
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

        void registerSmartspaceView(SmartspaceView smartspace) {
            mSmartspaceViews.add(smartspace);
        }

        public void onCreate(final Bundle bundle) {
            SharedPreferences prefs = Utilities.getPrefs(mLauncher);
            mOverlay = new NexusLauncherOverlay(mLauncher);
            mClient = new LauncherClient(mLauncher, mOverlay, new StaticInteger(
                    (prefs.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) ? 1 : 0) | 2 | 4 | 8));
            mOverlay.setClient(mClient);

            prefs.registerOnSharedPreferenceChangeListener(this);

            SmartspaceController.get(mLauncher).cW();

            mQsbAnimationController = new QsbAnimationController(mLauncher);

            mUiInformation.putInt("system_ui_visibility", mLauncher.getWindow().getDecorView().getSystemUiVisibility());
            applyFeedTheme(false);
            WallpaperColorInfo instance = WallpaperColorInfo.getInstance(mLauncher);
            instance.addOnChangeListener(this);
            onExtractedColorsChanged(instance);

            getUpdateReceiver().onCreate();

            PredictionUiStateManager predictionUiStateManager = PredictionUiStateManager.getInstance(mLauncher);
            predictionUiStateManager.setTargetAppsView(mLauncher.getAppsView());
            if (FeatureFlags.REFLECTION_FORCE_OVERVIEW_MODE) {
                predictionUiStateManager.switchClient(Client.OVERVIEW);
            }
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

            PredictionUiStateManager.getInstance(mLauncher).setTargetAppsView(null);
        }

        public void onDetachedFromWindow() {
            mFeedReconnector.stop();
            mClient.onDetachedFromWindow();
        }

        @Override
        public void onHomeIntent(boolean internalStateHandled) {
            mClient.hideOverlay(mFeedRunning);
        }

        public void onLauncherProviderChange() {
            ReflectionClient.getInstance(mLauncher).onProviderChanged();
        }

        public void onPause() {
            mRunning = false;
            mClient.onPause();

            for (SmartspaceView smartspace : mSmartspaceViews) {
                smartspace.onPause();
            }
        }

        public void onRequestPermissionsResult(final int n, final String[] array, final int[] array2) {
        }

        public void onResume() {
            mRunning = true;
            if (mStarted) {
                mFeedRunning = true;
            }

            mClient.onResume();

            for (SmartspaceView smartspace : mSmartspaceViews) {
                smartspace.onResume();
            }

            Handler handler = mLauncher.getDragLayer().getHandler();
            if (handler != null) {
                handler.removeCallbacks(mUpdatePredictionsIfResumed);
                Utilities.postAsyncCallback(handler, mUpdatePredictionsIfResumed);
            }
        }

        public void onSaveInstanceState(final Bundle bundle) {
        }

        public void onStart() {
            if (!ActionIntentFilter.googleEnabled(mLauncher)) {
                mOverlay.setPersistentFlags(0);
            }

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
//                mExterns.clearTypedText();
                return true;
            }
            return false;
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case SettingsActivity.ENABLE_MINUS_ONE_PREF:
                    LauncherClient launcherClient = mClient;
                    StaticInteger i = new StaticInteger(
                            (sharedPreferences.getBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, true) ? 1 : 0) | 2 | 4 | 8);
                    if (i.mData != launcherClient.mFlags) {
                        launcherClient.mFlags = i.mData;
                        if (launcherClient.mLayoutParams != null) {
                            launcherClient.exchangeConfig();
                        }
                    }
                    break;
                case SettingsActivity.FEED_THEME_PREF:
                    applyFeedTheme(true);
                    break;
            }
        }

        @Override
        public void onExtractedColorsChanged(WallpaperColorInfo wallpaperColorInfo) {
            int alpha = mLauncher.getResources().getInteger(R.integer.extracted_color_gradient_alpha);

            mUiInformation.putInt("background_color_hint", primaryColor(wallpaperColorInfo, mLauncher, alpha));
            mUiInformation.putInt("background_secondary_color_hint", secondaryColor(wallpaperColorInfo, mLauncher, alpha));

            applyFeedTheme(true);
        }

        private void applyFeedTheme(boolean redraw) {
            String prefValue = Utilities.getPrefs(mLauncher).getString(SettingsActivity.FEED_THEME_PREF, null);
            int feedTheme;
            try {
                feedTheme = Integer.valueOf(prefValue == null ? "1" : prefValue);
            } catch (Exception e) {
                feedTheme = 1;
            }
            boolean auto = (feedTheme & 1) != 0;
            boolean preferDark = (feedTheme & 2) != 0;
            boolean isDark = auto ? Themes.getAttrBoolean(mLauncher, R.attr.isMainColorDark) : preferDark;
            mUiInformation.putBoolean("is_background_dark", isDark);

            if (redraw) {
                mClient.redraw(mUiInformation);
            }
        }

        private void updatePredictionsIfResumed() {
            if (mLauncher.hasBeenResumed()) {
                ReflectionClient.getInstance(mLauncher).updatePredictionsNow(
                        FeatureFlags.REFLECTION_FORCE_OVERVIEW_MODE ? Client.OVERVIEW.id : Client.HOME.id);
                handler.post(() -> {
                    mLauncher.getUserEventDispatcher().updatePredictions();
                    mLauncher.getUserEventDispatcher().updateActions();
                });
            }
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
