package com.android.launcher3;

import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;

/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static boolean launcher_is_cached = false;
    private static boolean launcher_search_is_cached = false;
    private static boolean enableCursorHoverStates = true;
    private static boolean enableExpandingPauseWorkButton = false;
    private static boolean enableGridOnlyOverview = true;
    private static boolean enableHomeTransitionListener = true;
    private static boolean enableLauncherBrMetrics = false;
    private static boolean enableOverviewIconMenu = true;
    private static boolean enablePrivateSpace = true;
    private static boolean enableResponsiveWorkspace = true;
    private static boolean enableSplitFromFullscreenWithKeyboardShortcuts = false;
    private static boolean enableTabletTwoPanePickerV2 = false;
    private static boolean enableTaskbarNoRecreate = false;
    private static boolean enableTaskbarPinning = false;
    private static boolean enableTwolineAllapps = false;
    private static boolean enableUnfoldedTwoPanePicker = true;
    private static boolean privateSpaceAnimation = false;

    private void load_overrides_launcher() {
        try {
            Properties properties = DeviceConfig.getProperties("launcher");
            enableCursorHoverStates =
                    properties.getBoolean("com.android.launcher3.enable_cursor_hover_states", true);
            enableExpandingPauseWorkButton =
                    properties.getBoolean("com.android.launcher3.enable_expanding_pause_work_button", false);
            enableGridOnlyOverview =
                    properties.getBoolean("com.android.launcher3.enable_grid_only_overview", true);
            enableHomeTransitionListener =
                    properties.getBoolean("com.android.launcher3.enable_home_transition_listener", true);
            enableLauncherBrMetrics =
                    properties.getBoolean("com.android.launcher3.enable_launcher_br_metrics", false);
            enableOverviewIconMenu =
                    properties.getBoolean("com.android.launcher3.enable_overview_icon_menu", true);
            enableResponsiveWorkspace =
                    properties.getBoolean("com.android.launcher3.enable_responsive_workspace", true);
            enableSplitFromFullscreenWithKeyboardShortcuts =
                    properties.getBoolean("com.android.launcher3.enable_split_from_fullscreen_with_keyboard_shortcuts", false);
            enableTabletTwoPanePickerV2 =
                    properties.getBoolean("com.android.launcher3.enable_tablet_two_pane_picker_v2", false);
            enableTaskbarNoRecreate =
                    properties.getBoolean("com.android.launcher3.enable_taskbar_no_recreate", false);
            enableTaskbarPinning =
                    properties.getBoolean("com.android.launcher3.enable_taskbar_pinning", false);
            enableTwolineAllapps =
                    properties.getBoolean("com.android.launcher3.enable_twoline_allapps", false);
            enableUnfoldedTwoPanePicker =
                    properties.getBoolean("com.android.launcher3.enable_unfolded_two_pane_picker", true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace launcher "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        launcher_is_cached = true;
    }

    private void load_overrides_launcher_search() {
        try {
            Properties properties = DeviceConfig.getProperties("launcher_search");
            enablePrivateSpace =
                    properties.getBoolean("com.android.launcher3.enable_private_space", true);
            privateSpaceAnimation =
                    properties.getBoolean("com.android.launcher3.private_space_animation", false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace launcher_search "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        launcher_search_is_cached = true;
    }

    @Override
    public boolean enableCursorHoverStates() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableCursorHoverStates;
    }

    @Override
    public boolean enableExpandingPauseWorkButton() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableExpandingPauseWorkButton;
    }

    @Override
    public boolean enableGridOnlyOverview() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableGridOnlyOverview;
    }

    @Override
    public boolean enableHomeTransitionListener() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableHomeTransitionListener;
    }

    @Override
    public boolean enableLauncherBrMetrics() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableLauncherBrMetrics;
    }

    @Override
    public boolean enableOverviewIconMenu() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableOverviewIconMenu;
    }

    @Override
    public boolean enablePrivateSpace() {
        if (!launcher_search_is_cached) {
            load_overrides_launcher_search();
        }
        return enablePrivateSpace;
    }

    @Override
    public boolean enableResponsiveWorkspace() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableResponsiveWorkspace;
    }

    @Override
    public boolean enableSplitFromFullscreenWithKeyboardShortcuts() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableSplitFromFullscreenWithKeyboardShortcuts;
    }

    @Override
    public boolean enableTabletTwoPanePickerV2() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableTabletTwoPanePickerV2;
    }

    @Override
    public boolean enableTaskbarNoRecreate() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableTaskbarNoRecreate;
    }

    @Override
    public boolean enableTaskbarPinning() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableTaskbarPinning;
    }

    @Override
    public boolean enableTwolineAllapps() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableTwolineAllapps;
    }

    @Override
    public boolean enableUnfoldedTwoPanePicker() {
        if (!launcher_is_cached) {
            load_overrides_launcher();
        }
        return enableUnfoldedTwoPanePicker;
    }

    @Override
    public boolean privateSpaceAnimation() {
        if (!launcher_search_is_cached) {
            load_overrides_launcher_search();
        }
        return privateSpaceAnimation;
    }

}

