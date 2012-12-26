package com.cyanogenmod.trebuchet;

import android.content.ContentValues;

class LauncherActionInfo extends ShortcutInfo {

    /*
     * The launcher action
     */
    LauncherAction.Action action;

    LauncherActionInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_LAUNCHER_ACTION;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String actionText = action != null ? action.name() : null;
        values.put(LauncherSettings.Favorites.LAUNCHER_ACTION, actionText);
    }
}
