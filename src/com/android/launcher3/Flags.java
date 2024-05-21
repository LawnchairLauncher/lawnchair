package com.android.launcher3;
/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_ENABLE_CURSOR_HOVER_STATES = "com.android.launcher3.enable_cursor_hover_states";
    /** @hide */
    public static final String FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON = "com.android.launcher3.enable_expanding_pause_work_button";
    /** @hide */
    public static final String FLAG_ENABLE_GRID_ONLY_OVERVIEW = "com.android.launcher3.enable_grid_only_overview";
    /** @hide */
    public static final String FLAG_ENABLE_HOME_TRANSITION_LISTENER = "com.android.launcher3.enable_home_transition_listener";
    /** @hide */
    public static final String FLAG_ENABLE_LAUNCHER_BR_METRICS = "com.android.launcher3.enable_launcher_br_metrics";
    /** @hide */
    public static final String FLAG_ENABLE_OVERVIEW_ICON_MENU = "com.android.launcher3.enable_overview_icon_menu";
    /** @hide */
    public static final String FLAG_ENABLE_PRIVATE_SPACE = "com.android.launcher3.enable_private_space";
    /** @hide */
    public static final String FLAG_ENABLE_RESPONSIVE_WORKSPACE = "com.android.launcher3.enable_responsive_workspace";
    /** @hide */
    public static final String FLAG_ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS = "com.android.launcher3.enable_split_from_fullscreen_with_keyboard_shortcuts";
    /** @hide */
    public static final String FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2 = "com.android.launcher3.enable_tablet_two_pane_picker_v2";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_NO_RECREATE = "com.android.launcher3.enable_taskbar_no_recreate";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_PINNING = "com.android.launcher3.enable_taskbar_pinning";
    /** @hide */
    public static final String FLAG_ENABLE_TWOLINE_ALLAPPS = "com.android.launcher3.enable_twoline_allapps";
    /** @hide */
    public static final String FLAG_ENABLE_UNFOLDED_TWO_PANE_PICKER = "com.android.launcher3.enable_unfolded_two_pane_picker";
    /** @hide */
    public static final String FLAG_PRIVATE_SPACE_ANIMATION = "com.android.launcher3.private_space_animation";
    public static boolean enableCursorHoverStates() {
        return FEATURE_FLAGS.enableCursorHoverStates();
    }

    public static boolean enableExpandingPauseWorkButton() {
        return FEATURE_FLAGS.enableExpandingPauseWorkButton();
    }

    public static boolean enableGridOnlyOverview() {
        return FEATURE_FLAGS.enableGridOnlyOverview();
    }

    public static boolean enableHomeTransitionListener() {
        return FEATURE_FLAGS.enableHomeTransitionListener();
    }

    public static boolean enableLauncherBrMetrics() {
        return FEATURE_FLAGS.enableLauncherBrMetrics();
    }

    public static boolean enableOverviewIconMenu() {
        return FEATURE_FLAGS.enableOverviewIconMenu();
    }

    public static boolean enablePrivateSpace() {
        return FEATURE_FLAGS.enablePrivateSpace();
    }

    public static boolean enableResponsiveWorkspace() {
        return FEATURE_FLAGS.enableResponsiveWorkspace();
    }

    public static boolean enableSplitFromFullscreenWithKeyboardShortcuts() {
        return FEATURE_FLAGS.enableSplitFromFullscreenWithKeyboardShortcuts();
    }

    public static boolean enableTabletTwoPanePickerV2() {
        return FEATURE_FLAGS.enableTabletTwoPanePickerV2();
    }

    public static boolean enableTaskbarNoRecreate() {
        return FEATURE_FLAGS.enableTaskbarNoRecreate();
    }

    public static boolean enableTaskbarPinning() {
        return FEATURE_FLAGS.enableTaskbarPinning();
    }

    public static boolean enableTwolineAllapps() {
        return FEATURE_FLAGS.enableTwolineAllapps();
    }

    public static boolean enableUnfoldedTwoPanePicker() {
        return FEATURE_FLAGS.enableUnfoldedTwoPanePicker();
    }

    public static boolean privateSpaceAnimation() {
        return FEATURE_FLAGS.privateSpaceAnimation();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
