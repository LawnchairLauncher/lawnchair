package com.android.launcher3;

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
    public boolean enableAddAppWidgetViaConfigActivityV2() {
        return getValue(Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2,
                FeatureFlags::enableAddAppWidgetViaConfigActivityV2);
    }

    @Override
    public boolean enableAdditionalHomeAnimations() {
        return getValue(Flags.FLAG_ENABLE_ADDITIONAL_HOME_ANIMATIONS,
                FeatureFlags::enableAdditionalHomeAnimations);
    }

    @Override
    public boolean enableCategorizedWidgetSuggestions() {
        return getValue(Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS,
                FeatureFlags::enableCategorizedWidgetSuggestions);
    }

    @Override
    public boolean enableCursorHoverStates() {
        return getValue(Flags.FLAG_ENABLE_CURSOR_HOVER_STATES,
                FeatureFlags::enableCursorHoverStates);
    }

    @Override
    public boolean enableExpandingPauseWorkButton() {
        return getValue(Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON,
                FeatureFlags::enableExpandingPauseWorkButton);
    }

    @Override
    public boolean enableFallbackOverviewInWindow() {
        return getValue(Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW,
                FeatureFlags::enableFallbackOverviewInWindow);
    }

    @Override
    public boolean enableFirstScreenBroadcastArchivingExtras() {
        return getValue(Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS,
                FeatureFlags::enableFirstScreenBroadcastArchivingExtras);
    }

    @Override
    public boolean enableFocusOutline() {
        return getValue(Flags.FLAG_ENABLE_FOCUS_OUTLINE,
                FeatureFlags::enableFocusOutline);
    }

    @Override
    public boolean enableGeneratedPreviews() {
        return getValue(Flags.FLAG_ENABLE_GENERATED_PREVIEWS,
                FeatureFlags::enableGeneratedPreviews);
    }

    @Override
    public boolean enableGridMigrationFix() {
        return getValue(Flags.FLAG_ENABLE_GRID_MIGRATION_FIX,
                FeatureFlags::enableGridMigrationFix);
    }

    @Override
    public boolean enableGridOnlyOverview() {
        return getValue(Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
                FeatureFlags::enableGridOnlyOverview);
    }

    @Override
    public boolean enableHandleDelayedGestureCallbacks() {
        return getValue(Flags.FLAG_ENABLE_HANDLE_DELAYED_GESTURE_CALLBACKS,
                FeatureFlags::enableHandleDelayedGestureCallbacks);
    }

    @Override
    public boolean enableHomeTransitionListener() {
        return getValue(Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER,
                FeatureFlags::enableHomeTransitionListener);
    }

    @Override
    public boolean enableLauncherBrMetricsFixed() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
                FeatureFlags::enableLauncherBrMetricsFixed);
    }

    @Override
    public boolean enableNarrowGridRestore() {
        return getValue(Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
                FeatureFlags::enableNarrowGridRestore);
    }

    @Override
    public boolean enableOverviewIconMenu() {
        return getValue(Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU,
                FeatureFlags::enableOverviewIconMenu);
    }

    @Override
    public boolean enablePredictiveBackGesture() {
        return getValue(Flags.FLAG_ENABLE_PREDICTIVE_BACK_GESTURE,
                FeatureFlags::enablePredictiveBackGesture);
    }

    @Override
    public boolean enablePrivateSpace() {
        return getValue(Flags.FLAG_ENABLE_PRIVATE_SPACE,
                FeatureFlags::enablePrivateSpace);
    }

    @Override
    public boolean enablePrivateSpaceInstallShortcut() {
        return getValue(Flags.FLAG_ENABLE_PRIVATE_SPACE_INSTALL_SHORTCUT,
                FeatureFlags::enablePrivateSpaceInstallShortcut);
    }

    @Override
    public boolean enableRebootUnlockAnimation() {
        return getValue(Flags.FLAG_ENABLE_REBOOT_UNLOCK_ANIMATION,
                FeatureFlags::enableRebootUnlockAnimation);
    }

    @Override
    public boolean enableRecentsInTaskbar() {
        return getValue(Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR,
                FeatureFlags::enableRecentsInTaskbar);
    }

    @Override
    public boolean enableRefactorTaskThumbnail() {
        return getValue(Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL,
                FeatureFlags::enableRefactorTaskThumbnail);
    }

    @Override
    public boolean enableResponsiveWorkspace() {
        return getValue(Flags.FLAG_ENABLE_RESPONSIVE_WORKSPACE,
                FeatureFlags::enableResponsiveWorkspace);
    }

    @Override
    public boolean enableScalingRevealHomeAnimation() {
        return getValue(Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION,
                FeatureFlags::enableScalingRevealHomeAnimation);
    }

    @Override
    public boolean enableShortcutDontSuggestApp() {
        return getValue(Flags.FLAG_ENABLE_SHORTCUT_DONT_SUGGEST_APP,
                FeatureFlags::enableShortcutDontSuggestApp);
    }

    @Override
    public boolean enableSmartspaceAsAWidget() {
        return getValue(Flags.FLAG_ENABLE_SMARTSPACE_AS_A_WIDGET,
                FeatureFlags::enableSmartspaceAsAWidget);
    }

    @Override
    public boolean enableSmartspaceRemovalToggle() {
        return getValue(Flags.FLAG_ENABLE_SMARTSPACE_REMOVAL_TOGGLE,
                FeatureFlags::enableSmartspaceRemovalToggle);
    }

    @Override
    public boolean enableSupportForArchiving() {
        return getValue(Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING,
                FeatureFlags::enableSupportForArchiving);
    }

    @Override
    public boolean enableTabletTwoPanePickerV2() {
        return getValue(Flags.FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2,
                FeatureFlags::enableTabletTwoPanePickerV2);
    }

    @Override
    public boolean enableTaskbarCustomization() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION,
                FeatureFlags::enableTaskbarCustomization);
    }

    @Override
    public boolean enableTaskbarNoRecreate() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_NO_RECREATE,
                FeatureFlags::enableTaskbarNoRecreate);
    }

    @Override
    public boolean enableTaskbarPinning() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_PINNING,
                FeatureFlags::enableTaskbarPinning);
    }

    @Override
    public boolean enableTwoPaneLauncherSettings() {
        return getValue(Flags.FLAG_ENABLE_TWO_PANE_LAUNCHER_SETTINGS,
                FeatureFlags::enableTwoPaneLauncherSettings);
    }

    @Override
    public boolean enableTwolineAllapps() {
        return getValue(Flags.FLAG_ENABLE_TWOLINE_ALLAPPS,
                FeatureFlags::enableTwolineAllapps);
    }

    @Override
    public boolean enableTwolineToggle() {
        return getValue(Flags.FLAG_ENABLE_TWOLINE_TOGGLE,
                FeatureFlags::enableTwolineToggle);
    }

    @Override
    public boolean enableUnfoldStateAnimation() {
        return getValue(Flags.FLAG_ENABLE_UNFOLD_STATE_ANIMATION,
                FeatureFlags::enableUnfoldStateAnimation);
    }

    @Override
    public boolean enableUnfoldedTwoPanePicker() {
        return getValue(Flags.FLAG_ENABLE_UNFOLDED_TWO_PANE_PICKER,
                FeatureFlags::enableUnfoldedTwoPanePicker);
    }

    @Override
    public boolean enableWidgetTapToAdd() {
        return getValue(Flags.FLAG_ENABLE_WIDGET_TAP_TO_ADD,
                FeatureFlags::enableWidgetTapToAdd);
    }

    @Override
    public boolean enableWorkspaceInflation() {
        return getValue(Flags.FLAG_ENABLE_WORKSPACE_INFLATION,
                FeatureFlags::enableWorkspaceInflation);
    }

    @Override
    public boolean enabledFoldersInAllApps() {
        return getValue(Flags.FLAG_ENABLED_FOLDERS_IN_ALL_APPS,
                FeatureFlags::enabledFoldersInAllApps);
    }

    @Override
    public boolean floatingSearchBar() {
        return getValue(Flags.FLAG_FLOATING_SEARCH_BAR,
                FeatureFlags::floatingSearchBar);
    }

    @Override
    public boolean forceMonochromeAppIcons() {
        return getValue(Flags.FLAG_FORCE_MONOCHROME_APP_ICONS,
                FeatureFlags::forceMonochromeAppIcons);
    }

    @Override
    public boolean privateSpaceAddFloatingMaskView() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_ADD_FLOATING_MASK_VIEW,
                FeatureFlags::privateSpaceAddFloatingMaskView);
    }

    @Override
    public boolean privateSpaceAnimation() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_ANIMATION,
                FeatureFlags::privateSpaceAnimation);
    }

    @Override
    public boolean privateSpaceAppInstallerButton() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_APP_INSTALLER_BUTTON,
                FeatureFlags::privateSpaceAppInstallerButton);
    }

    @Override
    public boolean privateSpaceRestrictAccessibilityDrag() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_RESTRICT_ACCESSIBILITY_DRAG,
                FeatureFlags::privateSpaceRestrictAccessibilityDrag);
    }

    @Override
    public boolean privateSpaceRestrictItemDrag() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_RESTRICT_ITEM_DRAG,
                FeatureFlags::privateSpaceRestrictItemDrag);
    }

    @Override
    public boolean privateSpaceSysAppsSeparation() {
        return getValue(Flags.FLAG_PRIVATE_SPACE_SYS_APPS_SEPARATION,
                FeatureFlags::privateSpaceSysAppsSeparation);
    }

    @Override
    public boolean useActivityOverlay() {
        return getValue(Flags.FLAG_USE_ACTIVITY_OVERLAY,
                FeatureFlags::useActivityOverlay);
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
                Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2,
                Flags.FLAG_ENABLE_ADDITIONAL_HOME_ANIMATIONS,
                Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS,
                Flags.FLAG_ENABLE_CURSOR_HOVER_STATES,
                Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON,
                Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW,
                Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS,
                Flags.FLAG_ENABLE_FOCUS_OUTLINE,
                Flags.FLAG_ENABLE_GENERATED_PREVIEWS,
                Flags.FLAG_ENABLE_GRID_MIGRATION_FIX,
                Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
                Flags.FLAG_ENABLE_HANDLE_DELAYED_GESTURE_CALLBACKS,
                Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER,
                Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
                Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
                Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU,
                Flags.FLAG_ENABLE_PREDICTIVE_BACK_GESTURE,
                Flags.FLAG_ENABLE_PRIVATE_SPACE,
                Flags.FLAG_ENABLE_PRIVATE_SPACE_INSTALL_SHORTCUT,
                Flags.FLAG_ENABLE_REBOOT_UNLOCK_ANIMATION,
                Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR,
                Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL,
                Flags.FLAG_ENABLE_RESPONSIVE_WORKSPACE,
                Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION,
                Flags.FLAG_ENABLE_SHORTCUT_DONT_SUGGEST_APP,
                Flags.FLAG_ENABLE_SMARTSPACE_AS_A_WIDGET,
                Flags.FLAG_ENABLE_SMARTSPACE_REMOVAL_TOGGLE,
                Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING,
                Flags.FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2,
                Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION,
                Flags.FLAG_ENABLE_TASKBAR_NO_RECREATE,
                Flags.FLAG_ENABLE_TASKBAR_PINNING,
                Flags.FLAG_ENABLE_TWO_PANE_LAUNCHER_SETTINGS,
                Flags.FLAG_ENABLE_TWOLINE_ALLAPPS,
                Flags.FLAG_ENABLE_TWOLINE_TOGGLE,
                Flags.FLAG_ENABLE_UNFOLD_STATE_ANIMATION,
                Flags.FLAG_ENABLE_UNFOLDED_TWO_PANE_PICKER,
                Flags.FLAG_ENABLE_WIDGET_TAP_TO_ADD,
                Flags.FLAG_ENABLE_WORKSPACE_INFLATION,
                Flags.FLAG_ENABLED_FOLDERS_IN_ALL_APPS,
                Flags.FLAG_FLOATING_SEARCH_BAR,
                Flags.FLAG_FORCE_MONOCHROME_APP_ICONS,
                Flags.FLAG_PRIVATE_SPACE_ADD_FLOATING_MASK_VIEW,
                Flags.FLAG_PRIVATE_SPACE_ANIMATION,
                Flags.FLAG_PRIVATE_SPACE_APP_INSTALLER_BUTTON,
                Flags.FLAG_PRIVATE_SPACE_RESTRICT_ACCESSIBILITY_DRAG,
                Flags.FLAG_PRIVATE_SPACE_RESTRICT_ITEM_DRAG,
                Flags.FLAG_PRIVATE_SPACE_SYS_APPS_SEPARATION,
                Flags.FLAG_USE_ACTIVITY_OVERLAY
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
            Arrays.asList(
                    Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS,
                    Flags.FLAG_ENABLE_GRID_MIGRATION_FIX,
                    Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
                    Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
                    ""
            )
    );
}
