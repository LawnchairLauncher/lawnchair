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

    public static final String SHARED_PREFS = "com.android.launcher3.prefs.xml";
    public static final String LAUNCHER_DB = "launcher.db";
    public static final String LAUNCHER_PREFS = "launcher.preferences";
    public static final String WALLPAPER_IMAGES_DB = "saved_wallpaper_images.db";
    public static final String WIDGET_PREVIEWS_DB = "widgetpreviews.db";

    public static final List<String> ALL_FILES = Collections.unmodifiableList(Arrays.asList(
            SHARED_PREFS,
            LAUNCHER_DB,
            LAUNCHER_PREFS,
            WALLPAPER_IMAGES_DB,
            WIDGET_PREVIEWS_DB));
}
