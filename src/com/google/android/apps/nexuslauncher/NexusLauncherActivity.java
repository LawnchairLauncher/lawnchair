package com.google.android.apps.nexuslauncher;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;

import com.android.launcher3.AppInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.WallpaperManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.google.android.libraries.gsa.launcherclient.LauncherClient;

import java.util.List;

public class NexusLauncherActivity extends Launcher {
    private final static String PREF_IS_RELOAD = "pref_reload_workspace";
    private NexusLauncher mLauncher;
    private boolean mIsReload;
    private String mThemeHints;

    public NexusLauncherActivity() {
        mLauncher = new NexusLauncher(this);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        FeatureFlags.QSB_ON_FIRST_SCREEN = showSmartspace();
        mThemeHints = themeHints();

        SharedPreferences prefs = Utilities.getPrefs(this);
        if (!PixelBridge.isInstalled(this)) {
            prefs.edit().putBoolean(SettingsActivity.ENABLE_MINUS_ONE_PREF, false).apply();
        }

        super.onCreate(savedInstanceState);

        if (mIsReload = prefs.getBoolean(PREF_IS_RELOAD, false)) {
            prefs.edit().remove(PREF_IS_RELOAD).apply();

            // Go back to overview after a reload
            showOverviewMode(false);

            // Fix for long press not working
            // This is overwritten in Launcher.onResume
            setWorkspaceLoading(false);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        boolean themeChanged = !mThemeHints.equals(themeHints());
        if (FeatureFlags.QSB_ON_FIRST_SCREEN != showSmartspace() || themeChanged) {
            if (themeChanged) {
                WallpaperManagerCompat.getInstance(this).updateAllListeners();
            }
            Utilities.getPrefs(this).edit().putBoolean(PREF_IS_RELOAD, true).apply();
            recreate();
        }
    }

    @Override
    public void recreate() {
        if (Utilities.ATLEAST_NOUGAT) {
            super.recreate();
        } else {
            finish();
            startActivity(getIntent());
        }
    }

    @Override
    public void clearPendingExecutor(ViewOnDrawExecutor executor) {
        super.clearPendingExecutor(executor);
        if (mIsReload) {
            mIsReload = false;

            // Call again after the launcher has loaded for proper states
            showOverviewMode(false);

            // Strip empty At A Glance page
            getWorkspace().stripEmptyScreens();
        }
    }

    private boolean showSmartspace() {
        return Utilities.getPrefs(this).getBoolean(SettingsActivity.SMARTSPACE_PREF, true);
    }

    private String themeHints() {
        return Utilities.getPrefs(this).getString(Utilities.THEME_OVERRIDE_KEY, "");
    }

    @Override
    public void overrideTheme(boolean isDark, boolean supportsDarkText, boolean isTransparent) {
        int flags = Utilities.getDevicePrefs(this).getInt(NexusLauncherOverlay.PREF_PERSIST_FLAGS, 0);
        int orientFlag = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 16 : 8;
        boolean useGoogleInOrientation = (orientFlag & flags) != 0;
        supportsDarkText &= Utilities.ATLEAST_NOUGAT;
        if (useGoogleInOrientation && isDark) {
            setTheme(R.style.GoogleSearchLauncherThemeDark);
        } else if (useGoogleInOrientation && supportsDarkText) {
            setTheme(R.style.GoogleSearchLauncherThemeDarkText);
        } else if (useGoogleInOrientation && isTransparent) {
            setTheme(R.style.GoogleSearchLauncherThemeTransparent);
        } else if (useGoogleInOrientation) {
            setTheme(R.style.GoogleSearchLauncherTheme);
        } else {
            super.overrideTheme(isDark, supportsDarkText, isTransparent);
        }
    }

    public List<ComponentKeyMapper<AppInfo>> getPredictedApps() {
        return mLauncher.mCallbacks.getPredictedApps();
    }

    public LauncherClient getGoogleNow() {
        return mLauncher.mClient;
    }
}
