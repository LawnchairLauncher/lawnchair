package com.android.launcher3;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {



    boolean accessibilityScrollOnAllapps();


    boolean allAppsBlur();


    boolean allAppsSheetForHandheld();


    boolean coordinateWorkspaceScale();


    boolean enableActiveGestureProtoLog();


    boolean enableAddAppWidgetViaConfigActivityV2();


    boolean enableAdditionalHomeAnimations();


    boolean enableAllAppsButtonInHotseat();


    boolean enableAltTabKqsFlatenning();


    boolean enableAltTabKqsOnConnectedDisplays();


    boolean enableCategorizedWidgetSuggestions();


    boolean enableContainerReturnAnimations();


    boolean enableContrastTiles();


    boolean enableCursorHoverStates();


    boolean enableDesktopExplodedView();


    boolean enableDesktopTaskAlphaAnimation();


    boolean enableDesktopWindowingCarouselDetach();


    boolean enableDismissPredictionUndo();


    boolean enableExpandingPauseWorkButton();


    boolean enableExpressiveDismissTaskMotion();


    boolean enableFallbackOverviewInWindow();


    boolean enableFirstScreenBroadcastArchivingExtras();


    boolean enableFocusOutline();


    boolean enableGeneratedPreviews();


    boolean enableGestureNavHorizontalTouchSlop();


    boolean enableGestureNavOnConnectedDisplays();


    boolean enableGridMigrationFix();


    boolean enableGridOnlyOverview();


    boolean enableGrowthNudge();


    boolean enableHandleDelayedGestureCallbacks();


    boolean enableHomeTransitionListener();


    boolean enableHoverOfChildElementsInTaskview();


    boolean enableLargeDesktopWindowingTile();


    boolean enableLauncherBrMetricsFixed();


    boolean enableLauncherIconShapes();


    boolean enableLauncherOverviewInWindow();


    boolean enableLauncherVisualRefresh();


    boolean enableMouseInteractionChanges();


    boolean enableMultiInstanceMenuTaskbar();


    boolean enableNarrowGridRestore();


    boolean enableOverviewBackgroundWallpaperBlur();


    boolean enableOverviewCommandHelperTimeout();


    boolean enableOverviewDesktopTileWallpaperBackground();


    boolean enableOverviewIconMenu();


    boolean enableOverviewOnConnectedDisplays();


    boolean enablePinningAppWithContextMenu();


    boolean enablePredictiveBackGesture();


    boolean enablePrivateSpace();


    boolean enablePrivateSpaceInstallShortcut();


    boolean enableRebootUnlockAnimation();


    boolean enableRecentsInTaskbar();


    boolean enableRecentsWindowProtoLog();


    boolean enableRefactorTaskThumbnail();


    boolean enableResponsiveWorkspace();


    boolean enableScalabilityForDesktopExperience();


    boolean enableScalingRevealHomeAnimation();


    boolean enableSeparateExternalDisplayTasks();


    boolean enableShortcutDontSuggestApp();


    boolean enableShowEnabledShortcutsInAccessibilityMenu();


    boolean enableSmartspaceAsAWidget();


    boolean enableSmartspaceRemovalToggle();


    boolean enableStateManagerProtoLog();


    boolean enableStrictMode();


    boolean enableSupportForArchiving();


    boolean enableTabletTwoPanePickerV2();


    boolean enableTaskbarBehindShade();


    boolean enableTaskbarCustomization();


    boolean enableTaskbarForDirectBoot();


    boolean enableTaskbarNoRecreate();


    boolean enableTaskbarPinning();


    boolean enableTieredWidgetsByDefaultInPicker();


    boolean enableTwoPaneLauncherSettings();


    boolean enableTwolineAllapps();


    boolean enableTwolineToggle();


    boolean enableUnfoldStateAnimation();


    boolean enableUnfoldedTwoPanePicker();


    boolean enableUseTopVisibleActivityForExcludeFromRecentTask();


    boolean enableWidgetTapToAdd();


    boolean enableWorkspaceInflation();


    boolean enabledFoldersInAllApps();


    boolean expressiveThemeInTaskbarAndNavigation();


    boolean extendibleThemeManager();


    boolean floatingSearchBar();


    boolean forceMonochromeAppIcons();


    boolean gridMigrationRefactor();


    boolean gsfRes();


    boolean ignoreThreeFingerTrackpadForNavHandleLongPress();


    boolean letterFastScroller();


    boolean msdlFeedback();


    boolean multilineSearchBar();


    boolean navigateToChildPreference();


    boolean oneGridMountedMode();


    boolean oneGridRotationHandling();


    boolean oneGridSpecs();


    boolean predictiveBackToHomeBlur();


    boolean predictiveBackToHomePolish();


    boolean privateSpaceAddFloatingMaskView();


    boolean privateSpaceAnimation();


    boolean privateSpaceAppInstallerButton();


    boolean privateSpaceRestrictAccessibilityDrag();


    boolean privateSpaceRestrictItemDrag();


    boolean privateSpaceSysAppsSeparation();


    boolean removeAppsRefreshOnRightClick();


    boolean removeExcludeFromScreenMagnificationFlagUsage();


    boolean restoreArchivedAppIconsFromDb();


    boolean restoreArchivedShortcuts();


    boolean showTaskbarPinningPopupFromAnywhere();


    boolean syncAppLaunchWithTaskbarStash();


    boolean taskbarOverflow();


    boolean taskbarQuietModeChangeSupport();


    boolean useActivityOverlay();


    boolean useNewIconForArchivedApps();


    boolean useSystemRadiusForAppWidgets();


    boolean workSchedulerInWorkProfile();
}
