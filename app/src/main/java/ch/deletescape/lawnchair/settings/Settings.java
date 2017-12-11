package ch.deletescape.lawnchair.settings;

import android.content.SharedPreferences;

import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.dragndrop.DragLayer;
import ch.deletescape.lawnchair.dynamicui.ExtractedColors;
import ch.deletescape.lawnchair.preferences.IPreferenceProvider;
import ch.deletescape.lawnchair.preferences.PreferenceFlags;

public class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static Settings instance;
    private Launcher mLauncher;
    private IPreferenceProvider preferences;

    private Settings(Launcher launcher) {
        mLauncher = launcher;
        preferences = Utilities.getPrefs(launcher);
        preferences.registerOnSharedPreferenceChangeListener(this);
        init(preferences);
    }

    public static void init(Launcher launcher) {
        instance = new Settings(launcher);
    }

    public static Settings getInstance() {
        return instance;
    }

    private void init(IPreferenceProvider prefs) {
        applyAllAppsOpacity(prefs);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.startsWith("pref_")) {
            LauncherAppState las = LauncherAppState.getInstance();
            switch (key) {
                case PreferenceFlags.KEY_PREF_FORCE_LIGHT_STATUS_BAR:
                    mLauncher.getAllAppsController().updateLightStatusBar(mLauncher);
                    break;
                case PreferenceFlags.KEY_PREF_PINCH_TO_OVERVIEW:
                    DragLayer dragLayer = mLauncher.getDragLayer();
                    dragLayer.onAccessibilityStateChanged(dragLayer.mIsAccesibilityEnabled);
                    break;
                case PreferenceFlags.KEY_PREF_PULLDOWN_ACTION:
                    mLauncher.getAllAppsController().initPullDown(mLauncher);
                    break;
                case PreferenceFlags.KEY_PREF_HOTSEAT_EXTRACTED_COLORS:
                    ExtractedColors ec = mLauncher.getExtractedColors();
                    mLauncher.getHotseat().updateColor(ec, true);
                    mLauncher.getWorkspace().getPageIndicator().updateColor(ec);
                    break;
                case PreferenceFlags.KEY_PREF_HAPTIC_FEEDBACK:
                    mLauncher.getWorkspace().setHapticFeedbackEnabled(prefs.getBoolean(key, false));
                    break;
                case PreferenceFlags.KEY_PREF_ALL_APPS_OPACITY:
                    applyAllAppsOpacity(preferences);
                    break;
                case PreferenceFlags.KEY_PREF_SHOW_HIDDEN_APPS:
                case PreferenceFlags.KEY_PREF_HIDE_ALL_APPS_APP_LABELS:
                    las.reloadAllApps();
                    break;

                case PreferenceFlags.KEY_PREF_NUM_COLS_DRAWER:
                case PreferenceFlags.KEY_PREF_NUM_COLS:
                case PreferenceFlags.KEY_PREF_NUM_ROWS:
                    las.getInvariantDeviceProfile().refresh(mLauncher);
                    mLauncher.getWorkspace().refreshChildren();
                    break;
                case PreferenceFlags.KEY_PREF_NUM_HOTSEAT_ICONS:
                    las.getInvariantDeviceProfile().refresh(mLauncher);
                    mLauncher.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    mLauncher.getHotseat().refresh();
                                }
                            }
                    );
                    break;
                case PreferenceFlags.KEY_PREF_KEEP_SCROLL_STATE:
                case PreferenceFlags.KEY_SHOW_VOICE_SEARCH_BUTTON:
                case PreferenceFlags.KEY_PREF_WHITE_GOOGLE_ICON:
                    // Ignoring those as we do not need to apply anything special
                    break;
                case PreferenceFlags.KEY_PREF_ENABLE_BLUR:
                case PreferenceFlags.KEY_PREF_BLUR_MODE:
                case PreferenceFlags.KEY_PREF_BLUR_RADIUS:
                    mLauncher.scheduleUpdateWallpaper();
                    break;
                case PreferenceFlags.KEY_FULL_WIDTH_SEARCHBAR:
                case PreferenceFlags.KEY_PREF_FULL_WIDTH_WIDGETS:
                case PreferenceFlags.KEY_PREF_ENABLE_DYNAMIC_UI:
                case PreferenceFlags.KEY_PREF_THEME:
                case PreferenceFlags.KEY_PREF_THEME_MODE:
                case PreferenceFlags.KEY_PREF_TRANSPARENT_HOTSEAT:
                case PreferenceFlags.KEY_PREF_ROUND_SEARCH_BAR:
                case PreferenceFlags.KEY_SHOW_PIXEL_BAR:
                case PreferenceFlags.KEY_PREF_HIDE_HOTSEAT:
                case PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR:
                case PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR_HUE:
                case PreferenceFlags.KEY_PREF_DRAWER_CUSTOM_LABEL_COLOR_VARITATION:
                case PreferenceFlags.KEY_PREF_DRAWER_VERTICAL_LAYOUT:
                case PreferenceFlags.KEY_ENABLE_PHYSICS:
                case PreferenceFlags.KEY_PREF_ICON_SCALE:
                case PreferenceFlags.KEY_PREF_HOTSEAT_ICON_SCALE:
                case PreferenceFlags.KEY_PREF_HOTSEAT_HEIGHT_SCALE:
                case PreferenceFlags.KEY_PREF_ALL_APPS_ICON_SCALE:
                case PreferenceFlags.KEY_PREF_ALL_APPS_ICON_PADDING_SCALE:
                case PreferenceFlags.KEY_PREF_ICON_TEXT_SCALE:
                case PreferenceFlags.KEY_PREF_ALL_APPS_ICON_TEXT_SCALE:
                case PreferenceFlags.KEY_PREF_ENABLE_BACKPORT_SHORTCUTS:
                case PreferenceFlags.KEY_PREF_PLANE:
                case PreferenceFlags.KEY_PREF_WEATHER:
                case PreferenceFlags.KEY_PREF_NUM_ROWS_DRAWER:
                case PreferenceFlags.KEY_PREF_HOTSEAT_SHOW_ARROW:
                case PreferenceFlags.KEY_PREF_HOTSEAT_SHOW_PAGE_INDICATOR:
                case PreferenceFlags.KEY_TWO_ROW_DOCK:
                case PreferenceFlags.KEY_PREF_SNOWFALL:
                case PreferenceFlags.KEY_PREF_SNOWFLAKE_SIZE_SCALE:
                case PreferenceFlags.KEY_PREF_SNOWFLAKES_NUM:
                    mLauncher.scheduleKill();
                case PreferenceFlags.KEY_BACKPORT_ADAPTIVE_ICONS:
                    mLauncher.scheduleReloadIcons();
                    break;
                case PreferenceFlags.KEY_PREF_ICON_PACK_PACKAGE:
                case PreferenceFlags.KEY_PREF_PIXEL_STYLE_ICONS:
                case PreferenceFlags.KEY_ICON_LABELS_IN_TWO_LINES:
                    mLauncher.scheduleReloadIcons();
                    break;
                case PreferenceFlags.KEY_PREF_HIDE_APP_LABELS:
                    las.reloadWorkspace();
                    break;
                case PreferenceFlags.KEY_PREF_SHOW_NOW_TAB:
                    if (!prefs.getBoolean(key, true)) {
                        mLauncher.getClient().remove();
                    } else {
                        mLauncher.scheduleKill();
                    }
                case PreferenceFlags.KEY_PREF_SHOW_TOP_SHADOW:
                    mLauncher.getDragLayer().updateTopShadow();
                    break;
                case PreferenceFlags.KEY_CENTER_WALLPAPER:
                    mLauncher.getWorkspace().getWallpaperOffset().updateCenterWallpaper();
                default:
                    las.reloadAll(false);
            }
        }
    }

    private void applyAllAppsOpacity(IPreferenceProvider prefs) {
        int tmp = (int) (prefs.getAllAppsOpacity() * 255);
        mLauncher.getAllAppsController().setAllAppsAlpha(tmp);
    }
}
