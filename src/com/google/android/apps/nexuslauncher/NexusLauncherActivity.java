package com.google.android.apps.nexuslauncher;

import android.content.res.Configuration;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

public class NexusLauncherActivity extends Launcher {
    public void overrideTheme(boolean isDark, boolean supportsDarkText) {
        int flags = Utilities.getDevicePrefs(this).getInt("pref_persistent_flags", 0);
        int orientFlag = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 16 : 8;
        boolean useGoogleInOrientation = (orientFlag & flags) != 0;
        if (useGoogleInOrientation && isDark) {
            setTheme(R.style.GoogleSearchLauncherThemeDark);
        } else if (useGoogleInOrientation && supportsDarkText && Utilities.ATLEAST_NOUGAT) {
            setTheme(R.style.GoogleSearchLauncherThemeDarkText);
        } else if (useGoogleInOrientation) {
            setTheme(R.style.GoogleSearchLauncherTheme);
        } else {
            super.overrideTheme(isDark, supportsDarkText);
        }
    }
}
