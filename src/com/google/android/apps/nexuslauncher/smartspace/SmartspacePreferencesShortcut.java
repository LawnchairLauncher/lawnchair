package com.google.android.apps.nexuslauncher.smartspace;

import android.content.Intent;
import android.view.View;
import ch.deletescape.lawnchair.settings.ui.SettingsActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.views.OptionsPopupView;

public class SmartspacePreferencesShortcut extends OptionsPopupView.OptionItem {

    public SmartspacePreferencesShortcut() {
        super(R.string.customize, R.drawable.ic_smartspace_preferences, LauncherLogProto.ControlType.SETTINGS_BUTTON,
                SmartspacePreferencesShortcut::startSmartspacePreferences);
    }

    private static boolean startSmartspacePreferences(View view) {
        Launcher launcher = Launcher.getLauncher(view.getContext());
        launcher.startActivitySafely(view, new Intent(launcher, SettingsActivity.class)
                .putExtra(SettingsActivity.SubSettingsFragment.TITLE, launcher.getString(R.string.home_widget))
                .putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID, R.xml.lawnchair_smartspace_preferences)
                .putExtra(SettingsActivity.SubSettingsFragment.HAS_PREVIEW, true), null);
        return true;
    }
}
