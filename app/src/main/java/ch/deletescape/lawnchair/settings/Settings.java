package ch.deletescape.lawnchair.settings;

import android.content.SharedPreferences;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.dynamicui.ExtractedColors;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String KEY_PREF_LIGHT_STATUS_BAR = "pref_forceLightStatusBar";
    private static final String KEY_PREF_PINCH_TO_OVERVIEW = "pref_pinchToOverview";
    private static final String KEY_PREF_PULLDOWN_ACTION = "pref_pulldownAction";
    private static final String KEY_PREF_HOTSEAT_EXTRACTED_COLORS = "pref_hotseatShouldUseExtractedColors";
    private static final String KEY_PREF_HAPTIC_FEEDBACK = "pref_enableHapticFeedback";
    private static final String KEY_PREF_KEEP_SCROLL_STATE = "pref_keepScrollState";
    private static final String KEY_FULL_WIDTH_SEARCHBAR = "pref_fullWidthSearchbar";
    private static final String KEY_SHOW_PIXEL_BAR = "pref_showPixelBar";
    private static final String KEY_SHOW_VOICE_SEARCH_BUTTON = "pref_showMic";
    private static final String KEY_PREF_ALL_APPS_OPACITY = "pref_allAppsOpacitySB";
    private static final String KEY_PREF_SHOW_HIDDEN_APPS = "pref_showHidden";
    private static final String KEY_PREF_NUM_COLS = "pref_numCols";
    private static final String KEY_PREF_NUM_ROWS = "pref_numRows";
    private static final String KEY_PREF_NUM_HOTSEAT_ICONS = "pref_numHotseatIcons";
    private static final String KEY_PREF_ICON_SCALE = "pref_iconScaleSB";
    private static final String KEY_PREF_ICON_TEXT_SCALE = "pref_iconTextScaleSB";
    private static final String KEY_PREF_ICON_PACK_PACKAGE = "pref_iconPackPackage";
    private static final String KEY_PREF_PIXEL_STYLE_ICONS = "pref_pixelStyleIcons";
    private static final String KEY_PREF_HIDE_APP_LABELS = "pref_hideAppLabels";
    private static final String KEY_PREF_ENABLE_SCREEN_ROTATION = "pref_enableScreenRotation";
    private static final String KEY_PREF_FULL_WIDTH_WIDGETS = "pref_fullWidthWidgets";
    private static final String KEY_PREF_SHOW_NOW_TAB = "pref_showGoogleNowTab";
    private static final String KEY_PREF_TRANSPARENT_HOTSEAT = "pref_isHotseatTransparent";
    private static final String KEY_PREF_ENABLE_DYNAMIC_UI = "pref_enableDynamicUi";
    private static final String KEY_PREF_ENABLE_BLUR = "pref_enableBlur";
    private static final String KEY_PREF_BLUR_MODE = "pref_blurMode";
    private static final String KEY_PREF_BLUR_RADIUS = "pref_blurRadius";
    private static final String KEY_PREF_WHITE_GOOGLE_ICON = "pref_enableWhiteGoogleIcon";
    private static final String KEY_PREF_ROUND_SEARCH_BAR = "pref_useRoundSearchBar";
    private static final String KEY_PREF_ENABLE_BACKPORT_SHORTCUTS = "pref_enableBackportShortcuts";
    private static final String KEY_PREF_SHOW_TOP_SHADOW = "pref_showTopShadow";
    private static final String KEY_PREF_THEME = "pref_theme";
    private static final String KEY_PREF_THEME_MODE = "pref_themeMode";
    private static final String KEY_PREF_HIDE_HOTSEAT = "pref_hideHotseat";
    private static final String KEY_PREF_PLANE = "pref_plane";
    private static final String KEY_PREF_WEATHER = "pref_weather";
    private static final String KEY_PREF_ENABLE_EDITING = "pref_enableEditing";
    private static Settings instance;
    private Launcher mLauncher;

    private Settings(Launcher launcher) {
        mLauncher = launcher;
        SharedPreferences prefs = Utilities.getPrefs(launcher);
        prefs.registerOnSharedPreferenceChangeListener(this);
        init(prefs);
    }

    public static void init(Launcher launcher) {
        instance = new Settings(launcher);
    }

    public static Settings getInstance() {
        return instance;
    }

    private void init(SharedPreferences prefs) {
        applyAllAppsOpacity(prefs);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.startsWith("pref_")) {
            LauncherAppState las = LauncherAppState.getInstance();
            switch (key) {
                case KEY_PREF_LIGHT_STATUS_BAR:
                    mLauncher.getAllAppsController().updateLightStatusBar(mLauncher);
                    break;
                case KEY_PREF_PINCH_TO_OVERVIEW:
                    DragLayer dragLayer = mLauncher.getDragLayer();
                    dragLayer.onAccessibilityStateChanged(dragLayer.mIsAccesibilityEnabled);
                    break;
                case KEY_PREF_PULLDOWN_ACTION:
                    mLauncher.getWorkspace().initPullDown();
                    break;
                case KEY_PREF_HOTSEAT_EXTRACTED_COLORS:
                    ExtractedColors ec = mLauncher.getExtractedColors();
                    mLauncher.getHotseat().updateColor(ec, true);
                    mLauncher.getWorkspace().getPageIndicator().updateColor(ec);
                    break;
                case KEY_PREF_HAPTIC_FEEDBACK:
                    mLauncher.getWorkspace().setHapticFeedbackEnabled(prefs.getBoolean(key, false));
                    break;
                case KEY_PREF_ALL_APPS_OPACITY:
                    applyAllAppsOpacity(prefs);
                    break;
                case KEY_PREF_SHOW_HIDDEN_APPS:
                    las.reloadAllApps();
                    break;
                case KEY_PREF_NUM_COLS:
                case KEY_PREF_NUM_ROWS:
                    las.getInvariantDeviceProfile().customizationHook(mLauncher);
                    mLauncher.getWorkspace().refreshChildren();
                    break;
                case KEY_PREF_NUM_HOTSEAT_ICONS:
                    las.getInvariantDeviceProfile().customizationHook(mLauncher);
                    mLauncher.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mLauncher.getHotseat().refresh();
                                }
                            }
                    );
                    break;
                case KEY_PREF_KEEP_SCROLL_STATE:
                case KEY_SHOW_VOICE_SEARCH_BUTTON:
                case KEY_PREF_WHITE_GOOGLE_ICON:
                    // Ignoring those as we do not need to apply anything special
                    break;
                case KEY_PREF_ENABLE_BLUR:
                case KEY_PREF_BLUR_MODE:
                case KEY_PREF_BLUR_RADIUS:
                    mLauncher.scheduleUpdateWallpaper();
                    break;
                case KEY_FULL_WIDTH_SEARCHBAR:
                case KEY_PREF_FULL_WIDTH_WIDGETS:
                case KEY_PREF_ENABLE_DYNAMIC_UI:
                case KEY_PREF_THEME:
                case KEY_PREF_THEME_MODE:
                case KEY_PREF_TRANSPARENT_HOTSEAT:
                case KEY_PREF_ROUND_SEARCH_BAR:
                case KEY_SHOW_PIXEL_BAR:
                case KEY_PREF_HIDE_HOTSEAT:
                    mLauncher.scheduleRecreate();
                    break;
                case KEY_PREF_ICON_SCALE:
                case KEY_PREF_ICON_TEXT_SCALE:
                case KEY_PREF_ENABLE_BACKPORT_SHORTCUTS:
                case KEY_PREF_PLANE:
                case KEY_PREF_WEATHER:
                    mLauncher.scheduleKill();
                    break;
                case KEY_PREF_ICON_PACK_PACKAGE:
                case KEY_PREF_PIXEL_STYLE_ICONS:
                    mLauncher.scheduleReloadIcons();
                    break;
                case KEY_PREF_HIDE_APP_LABELS:
                    las.reloadWorkspace();
                    break;
                case KEY_PREF_SHOW_NOW_TAB:
                    if (!prefs.getBoolean(key, true)) {
                        mLauncher.getClient().remove();
                    } else {
                        mLauncher.scheduleKill();
                    }
                case KEY_PREF_SHOW_TOP_SHADOW:
                    mLauncher.getDragLayer().updateTopShadow();
                    break;
                default:
                    las.reloadAll(false);
            }
        }
    }

    private void applyAllAppsOpacity(SharedPreferences prefs) {
        int tmp = (int) (prefs.getFloat(KEY_PREF_ALL_APPS_OPACITY, 1f) * 255);
        mLauncher.getAllAppsController().setAllAppsAlpha(mLauncher, tmp);
    }
}
