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

    public boolean accessibilityScrollOnAllapps() {
        return getValue(Flags.FLAG_ACCESSIBILITY_SCROLL_ON_ALLAPPS,
            FeatureFlags::accessibilityScrollOnAllapps);
    }

    @Override

    public boolean allAppsBlur() {
        return getValue(Flags.FLAG_ALL_APPS_BLUR,
            FeatureFlags::allAppsBlur);
    }

    @Override

    public boolean allAppsSheetForHandheld() {
        return getValue(Flags.FLAG_ALL_APPS_SHEET_FOR_HANDHELD,
            FeatureFlags::allAppsSheetForHandheld);
    }

    @Override

    public boolean enableAddAppWidgetViaConfigActivityV2() {
        return getValue(Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2,
            FeatureFlags::enableAddAppWidgetViaConfigActivityV2);
    }

    @Override

    public boolean enableAllAppsButtonInHotseat() {
        return getValue(Flags.FLAG_ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT,
            FeatureFlags::enableAllAppsButtonInHotseat);
    }

    @Override

    public boolean enableAltTabKqsFlatenning() {
        return getValue(Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
            FeatureFlags::enableAltTabKqsFlatenning);
    }

    @Override

    public boolean enableAltTabKqsOnConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS,
            FeatureFlags::enableAltTabKqsOnConnectedDisplays);
    }

    @Override

    public boolean enableCategorizedWidgetSuggestions() {
        return getValue(Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS,
            FeatureFlags::enableCategorizedWidgetSuggestions);
    }

    @Override

    public boolean enableContainerReturnAnimations() {
        return getValue(Flags.FLAG_ENABLE_CONTAINER_RETURN_ANIMATIONS,
            FeatureFlags::enableContainerReturnAnimations);
    }

    @Override

    public boolean enableContrastTiles() {
        return getValue(Flags.FLAG_ENABLE_CONTRAST_TILES,
            FeatureFlags::enableContrastTiles);
    }

    @Override

    public boolean enableCoroutineThreadingImprovements() {
        return getValue(Flags.FLAG_ENABLE_COROUTINE_THREADING_IMPROVEMENTS,
            FeatureFlags::enableCoroutineThreadingImprovements);
    }

    @Override

    public boolean enableCursorHoverStates() {
        return getValue(Flags.FLAG_ENABLE_CURSOR_HOVER_STATES,
            FeatureFlags::enableCursorHoverStates);
    }

    @Override

    public boolean enableDesktopExplodedView() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW,
            FeatureFlags::enableDesktopExplodedView);
    }

    @Override

    public boolean enableDesktopMenuOnSecondaryDisplayBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_MENU_ON_SECONDARY_DISPLAY_BUGFIX,
            FeatureFlags::enableDesktopMenuOnSecondaryDisplayBugfix);
    }

    @Override

    public boolean enableDismissPredictionUndo() {
        return getValue(Flags.FLAG_ENABLE_DISMISS_PREDICTION_UNDO,
            FeatureFlags::enableDismissPredictionUndo);
    }

    @Override

    public boolean enableExpandingPauseWorkButton() {
        return getValue(Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON,
            FeatureFlags::enableExpandingPauseWorkButton);
    }

    @Override

    public boolean enableExpressiveDismissTaskMotion() {
        return getValue(Flags.FLAG_ENABLE_EXPRESSIVE_DISMISS_TASK_MOTION,
            FeatureFlags::enableExpressiveDismissTaskMotion);
    }

    @Override

    public boolean enableExpressiveFolderExpansion() {
        return getValue(Flags.FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION,
            FeatureFlags::enableExpressiveFolderExpansion);
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

    public boolean enableGestureNavHorizontalTouchSlop() {
        return getValue(Flags.FLAG_ENABLE_GESTURE_NAV_HORIZONTAL_TOUCH_SLOP,
            FeatureFlags::enableGestureNavHorizontalTouchSlop);
    }

    @Override

    public boolean enableGestureNavOnConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS,
            FeatureFlags::enableGestureNavOnConnectedDisplays);
    }

    @Override

    public boolean enableGridOnlyOverview() {
        return getValue(Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
            FeatureFlags::enableGridOnlyOverview);
    }

    @Override

    public boolean enableGrowthNudge() {
        return getValue(Flags.FLAG_ENABLE_GROWTH_NUDGE,
            FeatureFlags::enableGrowthNudge);
    }

    @Override

    public boolean enableHomeTransitionListener() {
        return getValue(Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER,
            FeatureFlags::enableHomeTransitionListener);
    }

    @Override

    public boolean enableLargeDesktopWindowingTile() {
        return getValue(Flags.FLAG_ENABLE_LARGE_DESKTOP_WINDOWING_TILE,
            FeatureFlags::enableLargeDesktopWindowingTile);
    }

    @Override

    public boolean enableLauncherBrMetricsFixed() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
            FeatureFlags::enableLauncherBrMetricsFixed);
    }

    @Override

    public boolean enableLauncherIconShapes() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_ICON_SHAPES,
            FeatureFlags::enableLauncherIconShapes);
    }

    @Override

    public boolean enableLauncherOverviewInWindow() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_OVERVIEW_IN_WINDOW,
            FeatureFlags::enableLauncherOverviewInWindow);
    }

    @Override

    public boolean enableLauncherVisualRefresh() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_VISUAL_REFRESH,
            FeatureFlags::enableLauncherVisualRefresh);
    }

    @Override

    public boolean enableLongPressRemoveShortcut() {
        return getValue(Flags.FLAG_ENABLE_LONG_PRESS_REMOVE_SHORTCUT,
            FeatureFlags::enableLongPressRemoveShortcut);
    }

    @Override

    public boolean enableMouseInteractionChanges() {
        return getValue(Flags.FLAG_ENABLE_MOUSE_INTERACTION_CHANGES,
            FeatureFlags::enableMouseInteractionChanges);
    }

    @Override

    public boolean enableMultiInstanceMenuTaskbar() {
        return getValue(Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR,
            FeatureFlags::enableMultiInstanceMenuTaskbar);
    }

    @Override

    public boolean enableNarrowGridRestore() {
        return getValue(Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
            FeatureFlags::enableNarrowGridRestore);
    }

    @Override

    public boolean enableOverviewBackgroundWallpaperBlur() {
        return getValue(Flags.FLAG_ENABLE_OVERVIEW_BACKGROUND_WALLPAPER_BLUR,
            FeatureFlags::enableOverviewBackgroundWallpaperBlur);
    }

    @Override

    public boolean enableOverviewDesktopTileWallpaperBackground() {
        return getValue(Flags.FLAG_ENABLE_OVERVIEW_DESKTOP_TILE_WALLPAPER_BACKGROUND,
            FeatureFlags::enableOverviewDesktopTileWallpaperBackground);
    }

    @Override

    public boolean enableOverviewIconMenu() {
        return getValue(Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU,
            FeatureFlags::enableOverviewIconMenu);
    }

    @Override

    public boolean enableOverviewOnConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_OVERVIEW_ON_CONNECTED_DISPLAYS,
            FeatureFlags::enableOverviewOnConnectedDisplays);
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

    public boolean enableQsbOnHotseat() {
        return getValue(Flags.FLAG_ENABLE_QSB_ON_HOTSEAT,
            FeatureFlags::enableQsbOnHotseat);
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

    public boolean enableRecentsWindowProtoLog() {
        return getValue(Flags.FLAG_ENABLE_RECENTS_WINDOW_PROTO_LOG,
            FeatureFlags::enableRecentsWindowProtoLog);
    }

    @Override

    public boolean enableRefactorDigitalWellbeingToast() {
        return getValue(Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST,
            FeatureFlags::enableRefactorDigitalWellbeingToast);
    }

    @Override

    public boolean enableRefactorTaskContentView() {
        return getValue(Flags.FLAG_ENABLE_REFACTOR_TASK_CONTENT_VIEW,
            FeatureFlags::enableRefactorTaskContentView);
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

    public boolean enableReversibleHomeActionCorner() {
        return getValue(Flags.FLAG_ENABLE_REVERSIBLE_HOME_ACTION_CORNER,
            FeatureFlags::enableReversibleHomeActionCorner);
    }

    @Override

    public boolean enableScalabilityForDesktopExperience() {
        return getValue(Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE,
            FeatureFlags::enableScalabilityForDesktopExperience);
    }

    @Override

    public boolean enableScalingRevealHomeAnimation() {
        return getValue(Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION,
            FeatureFlags::enableScalingRevealHomeAnimation);
    }

    @Override

    public boolean enableSimultaneousOverviewTriggerOnExtendedDesktop() {
        return getValue(Flags.FLAG_ENABLE_SIMULTANEOUS_OVERVIEW_TRIGGER_ON_EXTENDED_DESKTOP,
            FeatureFlags::enableSimultaneousOverviewTriggerOnExtendedDesktop);
    }

    @Override

    public boolean enableStateManagerProtoLog() {
        return getValue(Flags.FLAG_ENABLE_STATE_MANAGER_PROTO_LOG,
            FeatureFlags::enableStateManagerProtoLog);
    }

    @Override

    public boolean enableStrictMode() {
        return getValue(Flags.FLAG_ENABLE_STRICT_MODE,
            FeatureFlags::enableStrictMode);
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

    public boolean enableTaskbarBehindShade() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_BEHIND_SHADE,
            FeatureFlags::enableTaskbarBehindShade);
    }

    @Override

    public boolean enableTaskbarCustomization() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION,
            FeatureFlags::enableTaskbarCustomization);
    }

    @Override

    public boolean enableTaskbarForDirectBoot() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_FOR_DIRECT_BOOT,
            FeatureFlags::enableTaskbarForDirectBoot);
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

    public boolean enableTaskbarUiThread() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_UI_THREAD,
            FeatureFlags::enableTaskbarUiThread);
    }

    @Override

    public boolean enableTieredWidgetsByDefaultInPicker() {
        return getValue(Flags.FLAG_ENABLE_TIERED_WIDGETS_BY_DEFAULT_IN_PICKER,
            FeatureFlags::enableTieredWidgetsByDefaultInPicker);
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

    public boolean enableWidgetPickerRefactor() {
        return getValue(Flags.FLAG_ENABLE_WIDGET_PICKER_REFACTOR,
            FeatureFlags::enableWidgetPickerRefactor);
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

    public boolean expressiveThemeInTaskbarAndNavigation() {
        return getValue(Flags.FLAG_EXPRESSIVE_THEME_IN_TASKBAR_AND_NAVIGATION,
            FeatureFlags::expressiveThemeInTaskbarAndNavigation);
    }

    @Override

    public boolean externalDataAccess() {
        return getValue(Flags.FLAG_EXTERNAL_DATA_ACCESS,
            FeatureFlags::externalDataAccess);
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

    public boolean gridMigrationRefactor() {
        return getValue(Flags.FLAG_GRID_MIGRATION_REFACTOR,
            FeatureFlags::gridMigrationRefactor);
    }

    @Override

    public boolean gsfRes() {
        return getValue(Flags.FLAG_GSF_RES,
            FeatureFlags::gsfRes);
    }

    @Override

    public boolean homeScreenEditImprovements() {
        return getValue(Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS,
            FeatureFlags::homeScreenEditImprovements);
    }

    @Override

    public boolean ignoreThreeFingerTrackpadForNavHandleLongPress() {
        return getValue(Flags.FLAG_IGNORE_THREE_FINGER_TRACKPAD_FOR_NAV_HANDLE_LONG_PRESS,
            FeatureFlags::ignoreThreeFingerTrackpadForNavHandleLongPress);
    }

    @Override

    public boolean letterFastScroller() {
        return getValue(Flags.FLAG_LETTER_FAST_SCROLLER,
            FeatureFlags::letterFastScroller);
    }

    @Override

    public boolean modelRepository() {
        return getValue(Flags.FLAG_MODEL_REPOSITORY,
            FeatureFlags::modelRepository);
    }

    @Override

    public boolean msdlFeedback() {
        return getValue(Flags.FLAG_MSDL_FEEDBACK,
            FeatureFlags::msdlFeedback);
    }

    @Override

    public boolean nudgePill() {
        return getValue(Flags.FLAG_NUDGE_PILL,
            FeatureFlags::nudgePill);
    }

    @Override

    public boolean oneGridMountedMode() {
        return getValue(Flags.FLAG_ONE_GRID_MOUNTED_MODE,
            FeatureFlags::oneGridMountedMode);
    }

    @Override

    public boolean oneGridRotationHandling() {
        return getValue(Flags.FLAG_ONE_GRID_ROTATION_HANDLING,
            FeatureFlags::oneGridRotationHandling);
    }

    @Override

    public boolean oneGridSpecs() {
        return getValue(Flags.FLAG_ONE_GRID_SPECS,
            FeatureFlags::oneGridSpecs);
    }

    @Override

    public boolean predictiveBackToHomeBlur() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_TO_HOME_BLUR,
            FeatureFlags::predictiveBackToHomeBlur);
    }

    @Override

    public boolean predictiveBackToHomePolish() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_TO_HOME_POLISH,
            FeatureFlags::predictiveBackToHomePolish);
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

    public boolean removeAppsRefreshOnRightClick() {
        return getValue(Flags.FLAG_REMOVE_APPS_REFRESH_ON_RIGHT_CLICK,
            FeatureFlags::removeAppsRefreshOnRightClick);
    }

    @Override

    public boolean restoreArchivedAppIconsFromDb() {
        return getValue(Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB,
            FeatureFlags::restoreArchivedAppIconsFromDb);
    }

    @Override

    public boolean restoreArchivedShortcuts() {
        return getValue(Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS,
            FeatureFlags::restoreArchivedShortcuts);
    }

    @Override

    public boolean showTaskbarPinningPopupFromAnywhere() {
        return getValue(Flags.FLAG_SHOW_TASKBAR_PINNING_POPUP_FROM_ANYWHERE,
            FeatureFlags::showTaskbarPinningPopupFromAnywhere);
    }

    @Override

    public boolean syncAppLaunchWithTaskbarStash() {
        return getValue(Flags.FLAG_SYNC_APP_LAUNCH_WITH_TASKBAR_STASH,
            FeatureFlags::syncAppLaunchWithTaskbarStash);
    }

    @Override

    public boolean taskbarQuietModeChangeSupport() {
        return getValue(Flags.FLAG_TASKBAR_QUIET_MODE_CHANGE_SUPPORT,
            FeatureFlags::taskbarQuietModeChangeSupport);
    }

    @Override

    public boolean useNewIconForArchivedApps() {
        return getValue(Flags.FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS,
            FeatureFlags::useNewIconForArchivedApps);
    }

    @Override

    public boolean useSystemRadiusForAppWidgets() {
        return getValue(Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS,
            FeatureFlags::useSystemRadiusForAppWidgets);
    }

    @Override

    public boolean workSchedulerInWorkProfile() {
        return getValue(Flags.FLAG_WORK_SCHEDULER_IN_WORK_PROFILE,
            FeatureFlags::workSchedulerInWorkProfile);
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
            Flags.FLAG_ACCESSIBILITY_SCROLL_ON_ALLAPPS,
            Flags.FLAG_ALL_APPS_BLUR,
            Flags.FLAG_ALL_APPS_SHEET_FOR_HANDHELD,
            Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2,
            Flags.FLAG_ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS,
            Flags.FLAG_ENABLE_CONTAINER_RETURN_ANIMATIONS,
            Flags.FLAG_ENABLE_CONTRAST_TILES,
            Flags.FLAG_ENABLE_COROUTINE_THREADING_IMPROVEMENTS,
            Flags.FLAG_ENABLE_CURSOR_HOVER_STATES,
            Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW,
            Flags.FLAG_ENABLE_DESKTOP_MENU_ON_SECONDARY_DISPLAY_BUGFIX,
            Flags.FLAG_ENABLE_DISMISS_PREDICTION_UNDO,
            Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON,
            Flags.FLAG_ENABLE_EXPRESSIVE_DISMISS_TASK_MOTION,
            Flags.FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION,
            Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW,
            Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS,
            Flags.FLAG_ENABLE_FOCUS_OUTLINE,
            Flags.FLAG_ENABLE_GENERATED_PREVIEWS,
            Flags.FLAG_ENABLE_GESTURE_NAV_HORIZONTAL_TOUCH_SLOP,
            Flags.FLAG_ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
            Flags.FLAG_ENABLE_GROWTH_NUDGE,
            Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER,
            Flags.FLAG_ENABLE_LARGE_DESKTOP_WINDOWING_TILE,
            Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
            Flags.FLAG_ENABLE_LAUNCHER_ICON_SHAPES,
            Flags.FLAG_ENABLE_LAUNCHER_OVERVIEW_IN_WINDOW,
            Flags.FLAG_ENABLE_LAUNCHER_VISUAL_REFRESH,
            Flags.FLAG_ENABLE_LONG_PRESS_REMOVE_SHORTCUT,
            Flags.FLAG_ENABLE_MOUSE_INTERACTION_CHANGES,
            Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR,
            Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
            Flags.FLAG_ENABLE_OVERVIEW_BACKGROUND_WALLPAPER_BLUR,
            Flags.FLAG_ENABLE_OVERVIEW_DESKTOP_TILE_WALLPAPER_BACKGROUND,
            Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU,
            Flags.FLAG_ENABLE_OVERVIEW_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PREDICTIVE_BACK_GESTURE,
            Flags.FLAG_ENABLE_PRIVATE_SPACE,
            Flags.FLAG_ENABLE_PRIVATE_SPACE_INSTALL_SHORTCUT,
            Flags.FLAG_ENABLE_QSB_ON_HOTSEAT,
            Flags.FLAG_ENABLE_REBOOT_UNLOCK_ANIMATION,
            Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR,
            Flags.FLAG_ENABLE_RECENTS_WINDOW_PROTO_LOG,
            Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST,
            Flags.FLAG_ENABLE_REFACTOR_TASK_CONTENT_VIEW,
            Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL,
            Flags.FLAG_ENABLE_RESPONSIVE_WORKSPACE,
            Flags.FLAG_ENABLE_REVERSIBLE_HOME_ACTION_CORNER,
            Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE,
            Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION,
            Flags.FLAG_ENABLE_SIMULTANEOUS_OVERVIEW_TRIGGER_ON_EXTENDED_DESKTOP,
            Flags.FLAG_ENABLE_STATE_MANAGER_PROTO_LOG,
            Flags.FLAG_ENABLE_STRICT_MODE,
            Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING,
            Flags.FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2,
            Flags.FLAG_ENABLE_TASKBAR_BEHIND_SHADE,
            Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION,
            Flags.FLAG_ENABLE_TASKBAR_FOR_DIRECT_BOOT,
            Flags.FLAG_ENABLE_TASKBAR_NO_RECREATE,
            Flags.FLAG_ENABLE_TASKBAR_PINNING,
            Flags.FLAG_ENABLE_TASKBAR_UI_THREAD,
            Flags.FLAG_ENABLE_TIERED_WIDGETS_BY_DEFAULT_IN_PICKER,
            Flags.FLAG_ENABLE_TWO_PANE_LAUNCHER_SETTINGS,
            Flags.FLAG_ENABLE_TWOLINE_ALLAPPS,
            Flags.FLAG_ENABLE_TWOLINE_TOGGLE,
            Flags.FLAG_ENABLE_UNFOLD_STATE_ANIMATION,
            Flags.FLAG_ENABLE_WIDGET_PICKER_REFACTOR,
            Flags.FLAG_ENABLE_WIDGET_TAP_TO_ADD,
            Flags.FLAG_ENABLE_WORKSPACE_INFLATION,
            Flags.FLAG_ENABLED_FOLDERS_IN_ALL_APPS,
            Flags.FLAG_EXPRESSIVE_THEME_IN_TASKBAR_AND_NAVIGATION,
            Flags.FLAG_EXTERNAL_DATA_ACCESS,
            Flags.FLAG_FLOATING_SEARCH_BAR,
            Flags.FLAG_FORCE_MONOCHROME_APP_ICONS,
            Flags.FLAG_GRID_MIGRATION_REFACTOR,
            Flags.FLAG_GSF_RES,
            Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS,
            Flags.FLAG_IGNORE_THREE_FINGER_TRACKPAD_FOR_NAV_HANDLE_LONG_PRESS,
            Flags.FLAG_LETTER_FAST_SCROLLER,
            Flags.FLAG_MODEL_REPOSITORY,
            Flags.FLAG_MSDL_FEEDBACK,
            Flags.FLAG_NUDGE_PILL,
            Flags.FLAG_ONE_GRID_MOUNTED_MODE,
            Flags.FLAG_ONE_GRID_ROTATION_HANDLING,
            Flags.FLAG_ONE_GRID_SPECS,
            Flags.FLAG_PREDICTIVE_BACK_TO_HOME_BLUR,
            Flags.FLAG_PREDICTIVE_BACK_TO_HOME_POLISH,
            Flags.FLAG_PRIVATE_SPACE_ADD_FLOATING_MASK_VIEW,
            Flags.FLAG_PRIVATE_SPACE_ANIMATION,
            Flags.FLAG_PRIVATE_SPACE_RESTRICT_ACCESSIBILITY_DRAG,
            Flags.FLAG_PRIVATE_SPACE_RESTRICT_ITEM_DRAG,
            Flags.FLAG_PRIVATE_SPACE_SYS_APPS_SEPARATION,
            Flags.FLAG_REMOVE_APPS_REFRESH_ON_RIGHT_CLICK,
            Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB,
            Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS,
            Flags.FLAG_SHOW_TASKBAR_PINNING_POPUP_FROM_ANYWHERE,
            Flags.FLAG_SYNC_APP_LAUNCH_WITH_TASKBAR_STASH,
            Flags.FLAG_TASKBAR_QUIET_MODE_CHANGE_SUPPORT,
            Flags.FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS,
            Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS,
            Flags.FLAG_WORK_SCHEDULER_IN_WORK_PROFILE
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_ACCESSIBILITY_SCROLL_ON_ALLAPPS,
            Flags.FLAG_ALL_APPS_BLUR,
            Flags.FLAG_ALL_APPS_SHEET_FOR_HANDHELD,
            Flags.FLAG_ENABLE_ADD_APP_WIDGET_VIA_CONFIG_ACTIVITY_V2,
            Flags.FLAG_ENABLE_ALL_APPS_BUTTON_IN_HOTSEAT,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_FLATENNING,
            Flags.FLAG_ENABLE_ALT_TAB_KQS_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_CATEGORIZED_WIDGET_SUGGESTIONS,
            Flags.FLAG_ENABLE_CONTAINER_RETURN_ANIMATIONS,
            Flags.FLAG_ENABLE_CONTRAST_TILES,
            Flags.FLAG_ENABLE_COROUTINE_THREADING_IMPROVEMENTS,
            Flags.FLAG_ENABLE_CURSOR_HOVER_STATES,
            Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW,
            Flags.FLAG_ENABLE_DESKTOP_MENU_ON_SECONDARY_DISPLAY_BUGFIX,
            Flags.FLAG_ENABLE_DISMISS_PREDICTION_UNDO,
            Flags.FLAG_ENABLE_EXPANDING_PAUSE_WORK_BUTTON,
            Flags.FLAG_ENABLE_EXPRESSIVE_DISMISS_TASK_MOTION,
            Flags.FLAG_ENABLE_EXPRESSIVE_FOLDER_EXPANSION,
            Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW,
            Flags.FLAG_ENABLE_FIRST_SCREEN_BROADCAST_ARCHIVING_EXTRAS,
            Flags.FLAG_ENABLE_FOCUS_OUTLINE,
            Flags.FLAG_ENABLE_GENERATED_PREVIEWS,
            Flags.FLAG_ENABLE_GESTURE_NAV_HORIZONTAL_TOUCH_SLOP,
            Flags.FLAG_ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_GRID_ONLY_OVERVIEW,
            Flags.FLAG_ENABLE_GROWTH_NUDGE,
            Flags.FLAG_ENABLE_HOME_TRANSITION_LISTENER,
            Flags.FLAG_ENABLE_LARGE_DESKTOP_WINDOWING_TILE,
            Flags.FLAG_ENABLE_LAUNCHER_BR_METRICS_FIXED,
            Flags.FLAG_ENABLE_LAUNCHER_ICON_SHAPES,
            Flags.FLAG_ENABLE_LAUNCHER_OVERVIEW_IN_WINDOW,
            Flags.FLAG_ENABLE_LAUNCHER_VISUAL_REFRESH,
            Flags.FLAG_ENABLE_LONG_PRESS_REMOVE_SHORTCUT,
            Flags.FLAG_ENABLE_MOUSE_INTERACTION_CHANGES,
            Flags.FLAG_ENABLE_MULTI_INSTANCE_MENU_TASKBAR,
            Flags.FLAG_ENABLE_NARROW_GRID_RESTORE,
            Flags.FLAG_ENABLE_OVERVIEW_BACKGROUND_WALLPAPER_BLUR,
            Flags.FLAG_ENABLE_OVERVIEW_DESKTOP_TILE_WALLPAPER_BACKGROUND,
            Flags.FLAG_ENABLE_OVERVIEW_ICON_MENU,
            Flags.FLAG_ENABLE_OVERVIEW_ON_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PREDICTIVE_BACK_GESTURE,
            Flags.FLAG_ENABLE_PRIVATE_SPACE,
            Flags.FLAG_ENABLE_PRIVATE_SPACE_INSTALL_SHORTCUT,
            Flags.FLAG_ENABLE_QSB_ON_HOTSEAT,
            Flags.FLAG_ENABLE_REBOOT_UNLOCK_ANIMATION,
            Flags.FLAG_ENABLE_RECENTS_IN_TASKBAR,
            Flags.FLAG_ENABLE_RECENTS_WINDOW_PROTO_LOG,
            Flags.FLAG_ENABLE_REFACTOR_DIGITAL_WELLBEING_TOAST,
            Flags.FLAG_ENABLE_REFACTOR_TASK_CONTENT_VIEW,
            Flags.FLAG_ENABLE_REFACTOR_TASK_THUMBNAIL,
            Flags.FLAG_ENABLE_RESPONSIVE_WORKSPACE,
            Flags.FLAG_ENABLE_REVERSIBLE_HOME_ACTION_CORNER,
            Flags.FLAG_ENABLE_SCALABILITY_FOR_DESKTOP_EXPERIENCE,
            Flags.FLAG_ENABLE_SCALING_REVEAL_HOME_ANIMATION,
            Flags.FLAG_ENABLE_SIMULTANEOUS_OVERVIEW_TRIGGER_ON_EXTENDED_DESKTOP,
            Flags.FLAG_ENABLE_STATE_MANAGER_PROTO_LOG,
            Flags.FLAG_ENABLE_STRICT_MODE,
            Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING,
            Flags.FLAG_ENABLE_TABLET_TWO_PANE_PICKER_V2,
            Flags.FLAG_ENABLE_TASKBAR_BEHIND_SHADE,
            Flags.FLAG_ENABLE_TASKBAR_CUSTOMIZATION,
            Flags.FLAG_ENABLE_TASKBAR_FOR_DIRECT_BOOT,
            Flags.FLAG_ENABLE_TASKBAR_NO_RECREATE,
            Flags.FLAG_ENABLE_TASKBAR_PINNING,
            Flags.FLAG_ENABLE_TASKBAR_UI_THREAD,
            Flags.FLAG_ENABLE_TIERED_WIDGETS_BY_DEFAULT_IN_PICKER,
            Flags.FLAG_ENABLE_TWO_PANE_LAUNCHER_SETTINGS,
            Flags.FLAG_ENABLE_TWOLINE_ALLAPPS,
            Flags.FLAG_ENABLE_TWOLINE_TOGGLE,
            Flags.FLAG_ENABLE_UNFOLD_STATE_ANIMATION,
            Flags.FLAG_ENABLE_WIDGET_PICKER_REFACTOR,
            Flags.FLAG_ENABLE_WIDGET_TAP_TO_ADD,
            Flags.FLAG_ENABLE_WORKSPACE_INFLATION,
            Flags.FLAG_ENABLED_FOLDERS_IN_ALL_APPS,
            Flags.FLAG_EXPRESSIVE_THEME_IN_TASKBAR_AND_NAVIGATION,
            Flags.FLAG_EXTERNAL_DATA_ACCESS,
            Flags.FLAG_FLOATING_SEARCH_BAR,
            Flags.FLAG_FORCE_MONOCHROME_APP_ICONS,
            Flags.FLAG_GRID_MIGRATION_REFACTOR,
            Flags.FLAG_GSF_RES,
            Flags.FLAG_HOME_SCREEN_EDIT_IMPROVEMENTS,
            Flags.FLAG_IGNORE_THREE_FINGER_TRACKPAD_FOR_NAV_HANDLE_LONG_PRESS,
            Flags.FLAG_LETTER_FAST_SCROLLER,
            Flags.FLAG_MODEL_REPOSITORY,
            Flags.FLAG_MSDL_FEEDBACK,
            Flags.FLAG_NUDGE_PILL,
            Flags.FLAG_ONE_GRID_MOUNTED_MODE,
            Flags.FLAG_ONE_GRID_ROTATION_HANDLING,
            Flags.FLAG_ONE_GRID_SPECS,
            Flags.FLAG_PREDICTIVE_BACK_TO_HOME_BLUR,
            Flags.FLAG_PREDICTIVE_BACK_TO_HOME_POLISH,
            Flags.FLAG_PRIVATE_SPACE_ADD_FLOATING_MASK_VIEW,
            Flags.FLAG_PRIVATE_SPACE_ANIMATION,
            Flags.FLAG_PRIVATE_SPACE_RESTRICT_ACCESSIBILITY_DRAG,
            Flags.FLAG_PRIVATE_SPACE_RESTRICT_ITEM_DRAG,
            Flags.FLAG_PRIVATE_SPACE_SYS_APPS_SEPARATION,
            Flags.FLAG_REMOVE_APPS_REFRESH_ON_RIGHT_CLICK,
            Flags.FLAG_RESTORE_ARCHIVED_APP_ICONS_FROM_DB,
            Flags.FLAG_RESTORE_ARCHIVED_SHORTCUTS,
            Flags.FLAG_SHOW_TASKBAR_PINNING_POPUP_FROM_ANYWHERE,
            Flags.FLAG_SYNC_APP_LAUNCH_WITH_TASKBAR_STASH,
            Flags.FLAG_TASKBAR_QUIET_MODE_CHANGE_SUPPORT,
            Flags.FLAG_USE_NEW_ICON_FOR_ARCHIVED_APPS,
            Flags.FLAG_USE_SYSTEM_RADIUS_FOR_APP_WIDGETS,
            Flags.FLAG_WORK_SCHEDULER_IN_WORK_PROFILE,
            ""
        )
    );
}
