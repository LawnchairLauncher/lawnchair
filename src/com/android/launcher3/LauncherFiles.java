package com.android.launcher3;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central list of files the Launcher writes to the application data directory.
 *
 * To add a new Launcher file, create a String constant referring to the filename, and add it to
 * ALL_FILES, as shown below.
 */
public class LauncherFiles {

    private static final String XML = ".xml";

    public static final String LAUNCHER_DB = "launcher.db";
    public static final String LAUNCHER_4_BY_5_DB = "launcher_4_by_5.db";
    public static final String LAUNCHER_4_BY_4_DB = "launcher_4_by_4.db";
    public static final String LAUNCHER_3_BY_3_DB = "launcher_3_by_3.db";
    public static final String LAUNCHER_2_BY_2_DB = "launcher_2_by_2.db";
    public static final String BACKUP_DB = "backup.db";
    public static final String SHARED_PREFERENCES_KEY = "com.android.launcher3.prefs";
    public static final String MANAGED_USER_PREFERENCES_KEY =
            "com.android.launcher3.managedusers.prefs";
    // This preference file is not backed up to cloud.
    public static final String DEVICE_PREFERENCES_KEY = "com.android.launcher3.device.prefs";

    public static final String WIDGET_PREVIEWS_DB = "widgetpreviews.db";
    public static final String APP_ICONS_DB = "app_icons.db";

    public static final List<String> ALL_FILES = Collections.unmodifiableList(Arrays.asList(
            LAUNCHER_DB,
            LAUNCHER_4_BY_5_DB,
            LAUNCHER_4_BY_4_DB,
            LAUNCHER_3_BY_3_DB,
            LAUNCHER_2_BY_2_DB,
            BACKUP_DB,
            SHARED_PREFERENCES_KEY + XML,
            WIDGET_PREVIEWS_DB,
            MANAGED_USER_PREFERENCES_KEY + XML,
            DEVICE_PREFERENCES_KEY + XML,
            APP_ICONS_DB));
}
