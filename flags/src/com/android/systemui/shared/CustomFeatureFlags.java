package com.android.systemui.shared;


import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
/** @hide */
public class CustomFeatureFlags implements FeatureFlags {

    private BiPredicate<String, Predicate<FeatureFlags>> mGetValueImpl;

    public CustomFeatureFlags(BiPredicate<String, Predicate<FeatureFlags>> getValueImpl) {
        mGetValueImpl = getValueImpl;
    }
    @Override

    public boolean ambientAod() {
        return getValue(Flags.FLAG_AMBIENT_AOD,
            FeatureFlags::ambientAod);
    }

    @Override

    public boolean bouncerAreaExclusion() {
        return getValue(Flags.FLAG_BOUNCER_AREA_EXCLUSION,
            FeatureFlags::bouncerAreaExclusion);
    }

    @Override

    public boolean clockReactiveSmartspaceLayout() {
        return getValue(Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT,
            FeatureFlags::clockReactiveSmartspaceLayout);
    }

    @Override

    public boolean clockReactiveVariants() {
        return getValue(Flags.FLAG_CLOCK_REACTIVE_VARIANTS,
            FeatureFlags::clockReactiveVariants);
    }

    @Override

    public boolean cursorHotCorner() {
        return getValue(Flags.FLAG_CURSOR_HOT_CORNER,
            FeatureFlags::cursorHotCorner);
    }

    @Override

    public boolean enableHomeDelay() {
        return getValue(Flags.FLAG_ENABLE_HOME_DELAY,
            FeatureFlags::enableHomeDelay);
    }

    @Override

    public boolean enableLppAssistInvocationEffect() {
        return getValue(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT,
            FeatureFlags::enableLppAssistInvocationEffect);
    }

    @Override

    public boolean enableLppAssistInvocationHapticEffect() {
        return getValue(Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_HAPTIC_EFFECT,
            FeatureFlags::enableLppAssistInvocationHapticEffect);
    }

    @Override

    public boolean exampleSharedFlag() {
        return getValue(Flags.FLAG_EXAMPLE_SHARED_FLAG,
            FeatureFlags::exampleSharedFlag);
    }

    @Override

    public boolean extendedWallpaperEffects() {
        return getValue(Flags.FLAG_EXTENDED_WALLPAPER_EFFECTS,
            FeatureFlags::extendedWallpaperEffects);
    }

    @Override

    public boolean extendibleThemeManager() {
        return getValue(Flags.FLAG_EXTENDIBLE_THEME_MANAGER,
            FeatureFlags::extendibleThemeManager);
    }

    @Override

    public boolean lockscreenCustomClocks() {
        return getValue(Flags.FLAG_LOCKSCREEN_CUSTOM_CLOCKS,
            FeatureFlags::lockscreenCustomClocks);
    }

    @Override

    public boolean newCustomizationPickerUi() {
        return getValue(Flags.FLAG_NEW_CUSTOMIZATION_PICKER_UI,
            FeatureFlags::newCustomizationPickerUi);
    }

    @Override

    public boolean newTouchpadGesturesTutorial() {
        return getValue(Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL,
            FeatureFlags::newTouchpadGesturesTutorial);
    }

    @Override

    public boolean returnAnimationFrameworkLibrary() {
        return getValue(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
            FeatureFlags::returnAnimationFrameworkLibrary);
    }

    @Override

    public boolean returnAnimationFrameworkLongLived() {
        return getValue(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LONG_LIVED,
            FeatureFlags::returnAnimationFrameworkLongLived);
    }

    @Override

    public boolean screenshotContextUrl() {
        return getValue(Flags.FLAG_SCREENSHOT_CONTEXT_URL,
            FeatureFlags::screenshotContextUrl);
    }

    @Override

    public boolean shadeAllowBackGesture() {
        return getValue(Flags.FLAG_SHADE_ALLOW_BACK_GESTURE,
            FeatureFlags::shadeAllowBackGesture);
    }

    @Override

    public boolean sidefpsControllerRefactor() {
        return getValue(Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR,
            FeatureFlags::sidefpsControllerRefactor);
    }

    @Override

    public boolean smartspaceSportsCardBackground() {
        return getValue(Flags.FLAG_SMARTSPACE_SPORTS_CARD_BACKGROUND,
            FeatureFlags::smartspaceSportsCardBackground);
    }

    @Override

    public boolean smartspaceUiUpdate() {
        return getValue(Flags.FLAG_SMARTSPACE_UI_UPDATE,
            FeatureFlags::smartspaceUiUpdate);
    }

    @Override

    public boolean smartspaceUiUpdateResources() {
        return getValue(Flags.FLAG_SMARTSPACE_UI_UPDATE_RESOURCES,
            FeatureFlags::smartspaceUiUpdateResources);
    }

    @Override

    public boolean smartspaceWeatherUseMonochromeFontIcons() {
        return getValue(Flags.FLAG_SMARTSPACE_WEATHER_USE_MONOCHROME_FONT_ICONS,
            FeatureFlags::smartspaceWeatherUseMonochromeFontIcons);
    }

    @Override

    public boolean statusBarConnectedDisplays() {
        return getValue(Flags.FLAG_STATUS_BAR_CONNECTED_DISPLAYS,
            FeatureFlags::statusBarConnectedDisplays);
    }

    @Override

    public boolean threeButtonCornerSwipe() {
        return getValue(Flags.FLAG_THREE_BUTTON_CORNER_SWIPE,
            FeatureFlags::threeButtonCornerSwipe);
    }

    @Override

    public boolean usePreferredImageEditor() {
        return getValue(Flags.FLAG_USE_PREFERRED_IMAGE_EDITOR,
            FeatureFlags::usePreferredImageEditor);
    }

    public boolean isFlagReadOnlyOptimized(String flagName) {
        if (mReadOnlyFlagsSet.contains(flagName) &&
            isOptimizationEnabled()) {
                return true;
        }
        return false;
    }


    private boolean isOptimizationEnabled() {
        return false;
    }

    protected boolean getValue(String flagName, Predicate<FeatureFlags> getter) {
        return mGetValueImpl.test(flagName, getter);
    }

    public List<String> getFlagNames() {
        return Arrays.asList(
            Flags.FLAG_AMBIENT_AOD,
            Flags.FLAG_BOUNCER_AREA_EXCLUSION,
            Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT,
            Flags.FLAG_CLOCK_REACTIVE_VARIANTS,
            Flags.FLAG_CURSOR_HOT_CORNER,
            Flags.FLAG_ENABLE_HOME_DELAY,
            Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT,
            Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_HAPTIC_EFFECT,
            Flags.FLAG_EXAMPLE_SHARED_FLAG,
            Flags.FLAG_EXTENDED_WALLPAPER_EFFECTS,
            Flags.FLAG_EXTENDIBLE_THEME_MANAGER,
            Flags.FLAG_LOCKSCREEN_CUSTOM_CLOCKS,
            Flags.FLAG_NEW_CUSTOMIZATION_PICKER_UI,
            Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL,
            Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
            Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LONG_LIVED,
            Flags.FLAG_SCREENSHOT_CONTEXT_URL,
            Flags.FLAG_SHADE_ALLOW_BACK_GESTURE,
            Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR,
            Flags.FLAG_SMARTSPACE_SPORTS_CARD_BACKGROUND,
            Flags.FLAG_SMARTSPACE_UI_UPDATE,
            Flags.FLAG_SMARTSPACE_UI_UPDATE_RESOURCES,
            Flags.FLAG_SMARTSPACE_WEATHER_USE_MONOCHROME_FONT_ICONS,
            Flags.FLAG_STATUS_BAR_CONNECTED_DISPLAYS,
            Flags.FLAG_THREE_BUTTON_CORNER_SWIPE,
            Flags.FLAG_USE_PREFERRED_IMAGE_EDITOR
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_AMBIENT_AOD,
            Flags.FLAG_BOUNCER_AREA_EXCLUSION,
            Flags.FLAG_CLOCK_REACTIVE_SMARTSPACE_LAYOUT,
            Flags.FLAG_CLOCK_REACTIVE_VARIANTS,
            Flags.FLAG_CURSOR_HOT_CORNER,
            Flags.FLAG_ENABLE_HOME_DELAY,
            Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_EFFECT,
            Flags.FLAG_ENABLE_LPP_ASSIST_INVOCATION_HAPTIC_EFFECT,
            Flags.FLAG_EXAMPLE_SHARED_FLAG,
            Flags.FLAG_EXTENDED_WALLPAPER_EFFECTS,
            Flags.FLAG_EXTENDIBLE_THEME_MANAGER,
            Flags.FLAG_LOCKSCREEN_CUSTOM_CLOCKS,
            Flags.FLAG_NEW_CUSTOMIZATION_PICKER_UI,
            Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL,
            Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
            Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LONG_LIVED,
            Flags.FLAG_SCREENSHOT_CONTEXT_URL,
            Flags.FLAG_SHADE_ALLOW_BACK_GESTURE,
            Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR,
            Flags.FLAG_SMARTSPACE_SPORTS_CARD_BACKGROUND,
            Flags.FLAG_SMARTSPACE_UI_UPDATE,
            Flags.FLAG_SMARTSPACE_UI_UPDATE_RESOURCES,
            Flags.FLAG_SMARTSPACE_WEATHER_USE_MONOCHROME_FONT_ICONS,
            Flags.FLAG_STATUS_BAR_CONNECTED_DISPLAYS,
            Flags.FLAG_THREE_BUTTON_CORNER_SWIPE,
            Flags.FLAG_USE_PREFERRED_IMAGE_EDITOR,
            ""
        )
    );
}
