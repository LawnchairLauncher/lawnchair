package ch.deletescape.lawnchair;

import android.content.SharedPreferences;
import android.os.UserHandle;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.compat.UserManagerCompat;
import com.google.android.apps.nexuslauncher.CustomAppFilter;

public class LawnchairPreferencesChangeHandler implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Launcher mLauncher;

    public LawnchairPreferencesChangeHandler(Launcher launcher) {
        mLauncher = launcher;
    }

    private void recreate() {
        mLauncher.recreate();
    }

    private void reloadApps() {
        LauncherModel model = Launcher.getLauncher(mLauncher).getModel();
        for (UserHandle user : UserManagerCompat.getInstance(mLauncher).getUserProfiles()) {
            model.onPackagesReload(user);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        switch (key) {
            case CustomAppFilter.HIDE_APPS_PREF:
                reloadApps();
                break;
            case "pref_hideDockGradient":
                recreate();
                break;
        }
    }
}
