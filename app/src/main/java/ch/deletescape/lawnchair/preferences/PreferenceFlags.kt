package ch.deletescape.lawnchair.preferences

object PreferenceFlags {

    // TODO: maybe put all strings into resources? This way the resource string can be used in preference xml as well => more safety

    // Strings representing Integer or the string "default"
    const val KEY_NUM_ROWS = "pref_numRows"
    const val KEY_NUM_COLS = "pref_numCols"
    const val KEY_NUM_COLS_DRAWER = "pref_numColsDrawer"
    const val KEY_HOTSEAT_ICONS = "pref_numHotseatIcons"
    const val KEY_PREF_NUM_ROWS_DRAWER = "pref_numRowsDrawer"
    const val KEY_PREF_SNOWFLAKES_NUM = "pref_snowflakesNum"

    // Strings
    const val KEY_WEATHER_PROVIDER = "pref_weatherProvider"
    const val KEY_OVERRIDE_ICON_SHAPE = "pref_override_icon_shape"
    const val KEY_BACKPORT_ADAPTIVE_ICONS = "pref_backportAdaptiveIcons"
    const val KEY_ICON_PACK_PACKAGE = "pref_iconPackPackage"
    const val KEY_WEATHER_UNITS = "pref_weather_units"
    const val KEY_WEATHER_CITY = "pref_weather_city"
    const val KEY_WEATHER_API_KEY = "pref_weatherApiKey"

    // Floats
    const val KEY_ICON_SCALE_SB = "pref_iconScaleSB"
    const val KEY_ICON_TEXT_SCALE_SB = "pref_iconTextScaleSB"
    const val KEY_BLUR_RADIUS = "pref_blurRadius"
    const val KEY_PREF_HOTSEAT_ICON_SCALE = "pref_hotseatIconScale"
    const val KEY_PREF_HOTSEAT_HEIGHT_SCALE = "pref_hotseatHeightScale"
    const val KEY_PREF_ALL_APPS_ICON_SCALE = "pref_allAppsIconScale"
    const val KEY_PREF_ALL_APPS_ICON_TEXT_SCALE = "pref_allAppsIconTextScale"
    const val KEY_PREF_ALL_APPS_ICON_PADDING_SCALE = "pref_allAppsIconPaddingScale"
    const val KEY_PREF_SNOWFLAKE_SIZE_SCALE = "pref_snowflakeSizeScale"

    //Ints
    const val KEY_BLUR_MODE = "pref_blurMode"

    // Boolean
    const val KEY_SHOW_HIDDEN = "pref_showHidden"
    const val KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors"
    const val KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS_CACHE = KEY_HOTSEAT_SHOULD_USE_EXTRACTED_COLORS + "_cache"
    const val KEY_LIGHT_STATUS_BAR = "pref_lightStatusBar"
    const val KEY_CENTER_WALLPAPER = "pref_centerWallpaper"
    const val KEY_POPUP_CARD_THEME = "pref_popupCardTheme"
    const val KEY_ICON_LABELS_IN_TWO_LINES = "pref_iconLabelsInTwoLines"
    const val KEY_ANIMATED_CLOCK_ICON_ALTERNATIVE_CLOCK_APPS = "pref_animatedClockIconAlternativeClockApps"
    const val KEY_ENABLE_PHYSICS = "pref_enablePhysics"

    // Various
    const val KEY_PREF_WS_LABEL_COLOR = "pref_workspaceLabelColor"
    const val KEY_PREF_ALL_APPS_LABEL_COLOR = "pref_workspaceLabelColor"
    const val KEY_EXTRACTED_COLORS_PREFERENCE = "pref_extractedColors"
    const val KEY_WALLPAPER_ID_PREFERENCE = "pref_wallpaperId"

    const val KEY_PREF_FORCE_LIGHT_STATUS_BAR = "pref_forceLightStatusBar"
    const val KEY_PREF_PINCH_TO_OVERVIEW = "pref_pinchToOverview"
    const val KEY_PREF_PULLDOWN_ACTION = "pref_pulldownAction"
    const val KEY_PREF_HOTSEAT_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors"
    const val KEY_PREF_HOTSEAT_SHOW_ARROW = "pref_hotseatShowArrow"
    const val KEY_PREF_HOTSEAT_SHOW_PAGE_INDICATOR = "pref_hotseatShowPageIndicator"
    const val KEY_PREF_HAPTIC_FEEDBACK = "pref_enableHapticFeedback"
    const val KEY_PREF_KEEP_SCROLL_STATE = "pref_keepScrollState"
    const val KEY_FULL_WIDTH_SEARCHBAR = "pref_fullWidthSearchbar"
    const val KEY_SHOW_PIXEL_BAR = "pref_showPixelBar"
    const val KEY_SHOW_VOICE_SEARCH_BUTTON = "pref_showMic"
    const val KEY_PREF_ALL_APPS_OPACITY = "pref_allAppsOpacitySB"
    const val KEY_PREF_SHOW_HIDDEN_APPS = "pref_showHidden"
    const val KEY_PREF_NUM_COLS = "pref_numCols"
    const val KEY_PREF_NUM_COLS_DRAWER = "pref_numColsDrawer"
    const val KEY_PREF_NUM_ROWS = "pref_numRows"
    const val KEY_PREF_NUM_HOTSEAT_ICONS = "pref_numHotseatIcons"
    const val KEY_PREF_ICON_SCALE = "pref_iconScaleSB"
    const val KEY_PREF_ICON_TEXT_SCALE = "pref_iconTextScaleSB"
    const val KEY_PREF_ICON_PACK_PACKAGE = "pref_iconPackPackage"
    const val KEY_PREF_PIXEL_STYLE_ICONS = "pref_pixelStyleIcons"
    const val KEY_PREF_HIDE_APP_LABELS = "pref_hideAppLabels"
    const val KEY_PREF_HIDE_ALL_APPS_APP_LABELS = "pref_hideAllAppsAppLabels"
    const val KEY_PREF_ENABLE_SCREEN_ROTATION = "pref_enableScreenRotation"
    const val KEY_PREF_FULL_WIDTH_WIDGETS = "pref_fullWidthWidgets"
    const val KEY_PREF_SHOW_NOW_TAB = "pref_showGoogleNowTab"
    const val KEY_PREF_TRANSPARENT_HOTSEAT = "pref_isHotseatTransparent"
    const val KEY_PREF_ENABLE_DYNAMIC_UI = "pref_enableDynamicUi"
    const val KEY_PREF_ENABLE_BLUR = "pref_enableBlur"
    const val KEY_PREF_BLUR_MODE = "pref_blurMode"
    const val KEY_PREF_BLUR_RADIUS = "pref_blurRadius"
    const val KEY_PREF_WHITE_GOOGLE_ICON = "pref_enableWhiteGoogleIcon"
    const val KEY_PREF_ROUND_SEARCH_BAR = "pref_useRoundSearchBar"
    const val KEY_PREF_ENABLE_BACKPORT_SHORTCUTS = "pref_enableBackportShortcuts"
    const val KEY_PREF_SHOW_TOP_SHADOW = "pref_showTopShadow"
    const val KEY_PREF_THEME = "pref_theme"
    const val KEY_PREF_THEME_MODE = "pref_themeMode"
    const val KEY_PREF_HIDE_HOTSEAT = "pref_hideHotseat"
    const val KEY_PREF_PLANE = "pref_plane"
    const val KEY_PREF_WEATHER = "pref_weather"
    const val KEY_PREF_ENABLE_EDITING = "pref_enableEditing"
    const val KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR = "pref_allAppsCustomLabelColor"
    const val KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR_HUE = "pref_allAppsCustomLabelColorHue"
    const val KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR_VARITATION = "pref_allAppsCustomLabelColorVariation"
    const val KEY_PREF_DRAWER_VERTICAL_LAYOUT = "pref_verticalDrawerLayout"
    const val KEY_PREF_SNOWFALL = "pref_snowfall"

    const val KEY_APP_VISIBILITY_PREFIX = "visibility_"
    const val KEY_PREVIOUS_BUILD_NUMBER = "previousBuildNumber"

    const val KEY_ALTERNATE_ICON_PREFIX = "alternateIcon_"
    const val KEY_ITEM_ALIAS_PREFIX = "alias_"
    const val KEY_USER_CREATION_TIME_KEY_PREFIX = "user_creation_time_"
    const val KEY_HIDDEN_APPS_SET = "hidden-app-set"
    const val KEY_HIDDEN_APPS = "hidden-app"
    const val KEY_TWO_ROW_DOCK = "pref_twoRowDock"

    const val EMPTY_DATABASE_CREATED = "EMPTY_DATABASE_CREATED"

    // STATES
    // The set of shortcuts that are pending install
    const val APPS_PENDING_INSTALL = "apps_to_install"
    const val RESTORE_TASK_PENDING = "restore_task_pending"
    const val KEY_MIGRATION_SRC_WORKSPACE_SIZE = "migration_src_workspace_size"
    const val KEY_MIGRATION_SRC_HOTSEAT_COUNT = "migration_src_hotseat_count"

    // Launcher
    const val APPS_VIEW_SHOWN = "launcher.apps_view_shown"

    // CONSTANTS
    const val PREF_WEATHER_PROVIDER_AWARENESS = "1"
    const val PREF_WEATHER_DEFAULT_CITY = "Lucerne, CH"
    const val PREF_WEATHER_UNIT_METRIC = "metric"
    const val PREF_DEFAULT_WEATHER_API_KEY = "17a6438b1d63d5b05f7039e7cb52cde7"
    const val PREF_DEFAULT_STRING = "default"
}