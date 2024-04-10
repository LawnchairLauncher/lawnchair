package com.android.launcher3.util;

/**
 * This is a utility class that keeps track of all the tag that can be enabled to debug
 * a behavior in runtime.
 *
 * To use any of the strings defined in this class, execute the following command.
 *
 * $ adb shell setprop log.tag.TAGNAME VERBOSE
 */

public class LogConfig {
    // These are list of strings that can be used to replace TAGNAME.

    public static final String STATSLOG = "StatsLog";

    /**
     * After this tag is turned on, whenever there is n user event, debug information is
     * printed out to logcat.
     */
    public static final String USEREVENT = "UserEvent";

    /**
     * When turned on, all icons are kept on the home screen, even if they don't have an active
     * session.
     */
    public static final String KEEP_ALL_ICONS = "KeepAllIcons";

    /**
     * When turned on, icon cache is only fetched from memory and not disk.
     */
    public static final String MEMORY_ONLY_ICON_CACHE = "MemoryOnlyIconCache";

    /**
     * When turned on, we enable doodle related logging.
     */
    public static final String DOODLE_LOGGING = "DoodleLogging";

    /**
     * When turned on, we enable suggest related logging.
     */
    public static final String SEARCH_LOGGING = "SearchLogging";

    /**
     * When turned on, we enable IME related latency related logging.
     */
    public static final String IME_LATENCY_LOGGING = "ImeLatencyLogging";

    /**
     * When turned on, we enable web suggest appSearch related logging.
     */
    public static final String WEB_APP_SEARCH_LOGGING = "WebAppSearchLogging";

    /**
     * When turned on, we enable quick launch v2 related logging.
     */
    public static final String QUICK_LAUNCH_V2 = "QuickLaunchV2";

    /**
     * When turned on, we enable Gms Play related logging.
     */
    public static final String GMS_PLAY = "GmsPlay";

    /**
     * When turned on, we enable AGA related session summary logging.
     */
    public static final String AGA_SESSION_SUMMARY_LOG = "AGASessionSummaryLog";

    /**
     * When turned on, we enable long press nav handle related logging.
     */
    public static final String NAV_HANDLE_LONG_PRESS = "NavHandleLongPress";
}
