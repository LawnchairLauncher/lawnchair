package com.android.systemui.shared;


/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_AMBIENT_AOD = "com.android.systemui.shared.ambient_aod";
    /** @hide */
    public static final String FLAG_BOUNCER_AREA_EXCLUSION = "com.android.systemui.shared.bouncer_area_exclusion";
    /** @hide */
    public static final String FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT = "com.android.systemui.shared.clock_reactive_smartspace_layout";
    /** @hide */
    public static final String FLAG_CLOCK_REACTIVE_VARIANTS = "com.android.systemui.shared.clock_reactive_variants";
    /** @hide */
    public static final String FLAG_CURSOR_HOT_CORNER = "com.android.systemui.shared.cursor_hot_corner";
    /** @hide */
    public static final String FLAG_ENABLE_HOME_DELAY = "com.android.systemui.shared.enable_home_delay";
    /** @hide */
    public static final String FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT = "com.android.systemui.shared.enable_lpp_assist_invocation_effect";
    /** @hide */
    public static final String FLAG_ENABLE_LPP_ASSIST_INVOCATION_HAPTIC_EFFECT = "com.android.systemui.shared.enable_lpp_assist_invocation_haptic_effect";
    /** @hide */
    public static final String FLAG_EXAMPLE_SHARED_FLAG = "com.android.systemui.shared.example_shared_flag";
    /** @hide */
    public static final String FLAG_EXTENDED_WALLPAPER_EFFECTS = "com.android.systemui.shared.extended_wallpaper_effects";
    /** @hide */
    public static final String FLAG_EXTENDIBLE_THEME_MANAGER = "com.android.systemui.shared.extendible_theme_manager";
    /** @hide */
    public static final String FLAG_LOCKSCREEN_CUSTOM_CLOCKS = "com.android.systemui.shared.lockscreen_custom_clocks";
    /** @hide */
    public static final String FLAG_NEW_CUSTOMIZATION_PICKER_UI = "com.android.systemui.shared.new_customization_picker_ui";
    /** @hide */
    public static final String FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL = "com.android.systemui.shared.new_touchpad_gestures_tutorial";
    /** @hide */
    public static final String FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY = "com.android.systemui.shared.return_animation_framework_library";
    /** @hide */
    public static final String FLAG_RETURN_ANIMATION_FRAMEWORK_LONG_LIVED = "com.android.systemui.shared.return_animation_framework_long_lived";
    /** @hide */
    public static final String FLAG_SCREENSHOT_CONTEXT_URL = "com.android.systemui.shared.screenshot_context_url";
    /** @hide */
    public static final String FLAG_SHADE_ALLOW_BACK_GESTURE = "com.android.systemui.shared.shade_allow_back_gesture";
    /** @hide */
    public static final String FLAG_SIDEFPS_CONTROLLER_REFACTOR = "com.android.systemui.shared.sidefps_controller_refactor";
    /** @hide */
    public static final String FLAG_SMARTSPACE_SPORTS_CARD_BACKGROUND = "com.android.systemui.shared.smartspace_sports_card_background";
    /** @hide */
    public static final String FLAG_SMARTSPACE_UI_UPDATE = "com.android.systemui.shared.smartspace_ui_update";
    /** @hide */
    public static final String FLAG_SMARTSPACE_UI_UPDATE_RESOURCES = "com.android.systemui.shared.smartspace_ui_update_resources";
    /** @hide */
    public static final String FLAG_SMARTSPACE_WEATHER_USE_MONOCHROME_FONT_ICONS = "com.android.systemui.shared.smartspace_weather_use_monochrome_font_icons";
    /** @hide */
    public static final String FLAG_STATUS_BAR_CONNECTED_DISPLAYS = "com.android.systemui.shared.status_bar_connected_displays";
    /** @hide */
    public static final String FLAG_THREE_BUTTON_CORNER_SWIPE = "com.android.systemui.shared.three_button_corner_swipe";
    /** @hide */
    public static final String FLAG_USE_PREFERRED_IMAGE_EDITOR = "com.android.systemui.shared.use_preferred_image_editor";


    public static boolean ambientAod() {
        
        return FEATURE_FLAGS.ambientAod();
    }


    public static boolean bouncerAreaExclusion() {
        
        return FEATURE_FLAGS.bouncerAreaExclusion();
    }


    public static boolean clockReactiveSmartspaceLayout() {
        
        return FEATURE_FLAGS.clockReactiveSmartspaceLayout();
    }


    public static boolean clockReactiveVariants() {
        
        return FEATURE_FLAGS.clockReactiveVariants();
    }


    public static boolean cursorHotCorner() {
        
        return FEATURE_FLAGS.cursorHotCorner();
    }


    public static boolean enableHomeDelay() {
        
        return FEATURE_FLAGS.enableHomeDelay();
    }


    public static boolean enableLppAssistInvocationEffect() {
        
        return FEATURE_FLAGS.enableLppAssistInvocationEffect();
    }


    public static boolean enableLppAssistInvocationHapticEffect() {
        
        return FEATURE_FLAGS.enableLppAssistInvocationHapticEffect();
    }


    public static boolean exampleSharedFlag() {
        
        return FEATURE_FLAGS.exampleSharedFlag();
    }


    public static boolean extendedWallpaperEffects() {
        
        return FEATURE_FLAGS.extendedWallpaperEffects();
    }


    public static boolean extendibleThemeManager() {
        
        return FEATURE_FLAGS.extendibleThemeManager();
    }


    public static boolean lockscreenCustomClocks() {
        
        return FEATURE_FLAGS.lockscreenCustomClocks();
    }


    public static boolean newCustomizationPickerUi() {
        
        return FEATURE_FLAGS.newCustomizationPickerUi();
    }


    public static boolean newTouchpadGesturesTutorial() {
        
        return FEATURE_FLAGS.newTouchpadGesturesTutorial();
    }


    public static boolean returnAnimationFrameworkLibrary() {
        
        return FEATURE_FLAGS.returnAnimationFrameworkLibrary();
    }


    public static boolean returnAnimationFrameworkLongLived() {
        
        return FEATURE_FLAGS.returnAnimationFrameworkLongLived();
    }


    public static boolean screenshotContextUrl() {
        
        return FEATURE_FLAGS.screenshotContextUrl();
    }


    public static boolean shadeAllowBackGesture() {
        
        return FEATURE_FLAGS.shadeAllowBackGesture();
    }


    public static boolean sidefpsControllerRefactor() {
        
        return FEATURE_FLAGS.sidefpsControllerRefactor();
    }


    public static boolean smartspaceSportsCardBackground() {
        
        return FEATURE_FLAGS.smartspaceSportsCardBackground();
    }


    public static boolean smartspaceUiUpdate() {
        
        return FEATURE_FLAGS.smartspaceUiUpdate();
    }


    public static boolean smartspaceUiUpdateResources() {
        
        return FEATURE_FLAGS.smartspaceUiUpdateResources();
    }


    public static boolean smartspaceWeatherUseMonochromeFontIcons() {
        
        return FEATURE_FLAGS.smartspaceWeatherUseMonochromeFontIcons();
    }


    public static boolean statusBarConnectedDisplays() {
        
        return FEATURE_FLAGS.statusBarConnectedDisplays();
    }


    public static boolean threeButtonCornerSwipe() {
        
        return FEATURE_FLAGS.threeButtonCornerSwipe();
    }


    public static boolean usePreferredImageEditor() {
        
        return FEATURE_FLAGS.usePreferredImageEditor();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
