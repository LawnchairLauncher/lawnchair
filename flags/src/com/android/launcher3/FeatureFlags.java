package com.android.launcher3;
/** @hide */
public interface FeatureFlags {


    boolean enableAddAppWidgetViaConfigActivityV2();

    boolean enableAdditionalHomeAnimations();

    boolean enableCategorizedWidgetSuggestions();

    boolean enableCursorHoverStates();

    boolean enableExpandingPauseWorkButton();

    boolean enableFallbackOverviewInWindow();

    boolean enableFirstScreenBroadcastArchivingExtras();

    boolean enableFocusOutline();

    boolean enableGeneratedPreviews();

    boolean enableGridMigrationFix();

    boolean enableGridOnlyOverview();

    boolean enableHandleDelayedGestureCallbacks();

    boolean enableHomeTransitionListener();

    boolean enableLauncherBrMetricsFixed();

    boolean enableNarrowGridRestore();

    boolean enableOverviewIconMenu();

    boolean enablePredictiveBackGesture();

    boolean enablePrivateSpace();

    boolean enablePrivateSpaceInstallShortcut();

    boolean enableRebootUnlockAnimation();

    boolean enableRecentsInTaskbar();

    boolean enableRefactorTaskThumbnail();

    boolean enableResponsiveWorkspace();

    boolean enableScalingRevealHomeAnimation();

    boolean enableShortcutDontSuggestApp();

    boolean enableSmartspaceAsAWidget();

    boolean enableSmartspaceRemovalToggle();

    boolean enableSupportForArchiving();

    boolean enableTabletTwoPanePickerV2();

    boolean enableTaskbarCustomization();

    boolean enableTaskbarNoRecreate();

    boolean enableTaskbarPinning();

    boolean enableTwoPaneLauncherSettings();

    boolean enableTwolineAllapps();

    boolean enableTwolineToggle();

    boolean enableUnfoldStateAnimation();

    boolean enableUnfoldedTwoPanePicker();

    boolean enableWidgetTapToAdd();

    boolean enableWorkspaceInflation();

    boolean enabledFoldersInAllApps();

    boolean floatingSearchBar();

    boolean forceMonochromeAppIcons();

    boolean privateSpaceAddFloatingMaskView();

    boolean privateSpaceAnimation();

    boolean privateSpaceAppInstallerButton();

    boolean privateSpaceRestrictAccessibilityDrag();

    boolean privateSpaceRestrictItemDrag();

    boolean privateSpaceSysAppsSeparation();

    boolean useActivityOverlay();
}