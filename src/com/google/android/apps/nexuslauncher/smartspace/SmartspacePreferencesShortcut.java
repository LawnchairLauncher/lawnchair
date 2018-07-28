package com.google.android.apps.nexuslauncher.smartspace;

import android.content.Intent;
import android.view.View;

import ch.deletescape.lawnchair.settings.ui.SettingsActivity;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.popup.SystemShortcut;

public class SmartspacePreferencesShortcut extends SystemShortcut {

    public SmartspacePreferencesShortcut() {
        super(R.drawable.ic_smartspace_preferences, R.string.smartspace_preferences);
    }

    public View.OnClickListener getOnClickListener(final Launcher launcher, ItemInfo itemInfo) {
        return new View.OnClickListener() {
            public void onClick(final View view) {
                Intent intent = new Intent(launcher, SettingsActivity.class);
                intent.putExtra(SettingsActivity.SubSettingsFragment.TITLE, launcher.getString(R.string.home_widget));
                intent.putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID, R.xml.lawnchair_smartspace_preferences);
                intent.putExtra(SettingsActivity.SubSettingsFragment.HAS_PREVIEW, true);
                launcher.startActivitySafely(view, intent, null);
                AbstractFloatingView.closeAllOpenViews(launcher);
            }
        };
    }
}
