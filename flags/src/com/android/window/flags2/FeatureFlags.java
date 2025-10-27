package com.android.window.flags2;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {




    boolean actionModeEdgeToEdge();



    boolean activityEmbeddingAnimationCustomizationFlag();



    boolean activityEmbeddingDelayTaskFragmentFinishForActivityLaunch();



    boolean activityEmbeddingInteractiveDividerFlag();



    boolean activityEmbeddingMetrics();



    boolean activityEmbeddingSupportForConnectedDisplays();



    boolean allowDisableActivityRecordInputSink();



    boolean allowHideScmButton();



    boolean allowsScreenSizeDecoupledFromStatusBarAndCutout();



    boolean alwaysDrawMagnificationFullscreenBorder();



    boolean alwaysUpdateWallpaperPermission();



    boolean aodTransition();



    boolean appCompatAsyncRelayout();



    boolean appCompatPropertiesApi();



    boolean appCompatRefactoring();



    boolean appCompatUiFramework();



    boolean appHandleNoRelayoutOnExclusionChange();



    boolean applyLifecycleOnPipChange();



    boolean avoidRebindingIntentionallyDisconnectedWallpaper();



    boolean backupAndRestoreForUserAspectRatioSettings();



    boolean balAdditionalLogging();



    boolean balAdditionalStartModes();



    boolean balClearAllowlistDuration();



    boolean balDontBringExistingBackgroundTaskStackToFg();



    boolean balImproveRealCallerVisibilityCheck();



    boolean balImprovedMetrics();



    boolean balReduceGracePeriod();



    boolean balRequireOptInByPendingIntentCreator();



    boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid();



    boolean balSendIntentWithOptions();



    boolean balShowToastsBlocked();



    boolean balStrictModeGracePeriod();



    boolean balStrictModeRo();



    boolean betterSupportNonMatchParentActivity();



    boolean cacheWindowStyle();



    boolean cameraCompatForFreeform();



    boolean cameraCompatFullscreenPickSameTaskActivity();



    boolean checkDisabledSnapshotsInTaskPersister();



    boolean cleanupDispatchPendingTransactionsRemoteException();



    boolean clearSystemVibrator();



    boolean closeToSquareConfigIncludesStatusBar();



    boolean condenseConfigurationChangeForSimpleMode();



    boolean configurableFontScaleDefault();



    boolean coverDisplayOptIn();



    boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition();



    boolean delegateBackGestureToShell();



    boolean delegateUnhandledDrags();



    boolean deleteCaptureDisplay();



    boolean density390Api();



    boolean disableDesktopLaunchParamsOutsideDesktopBugFix();



    boolean disableNonResizableAppSnapResizing();



    boolean disableOptOutEdgeToEdge();



    boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions();



    boolean earlyLaunchHint();



    boolean edgeToEdgeByDefault();



    boolean enableAccessibleCustomHeaders();



    boolean enableActivityEmbeddingSupportForConnectedDisplays();



    boolean enableAppHeaderWithTaskDensity();



    boolean enableBorderSettings();



    boolean enableBufferTransformHintFromDisplay();



    boolean enableBugFixesForSecondaryDisplay();



    boolean enableCameraCompatForDesktopWindowing();



    boolean enableCameraCompatForDesktopWindowingOptOut();



    boolean enableCameraCompatForDesktopWindowingOptOutApi();



    boolean enableCameraCompatTrackTaskAndAppBugfix();



    boolean enableCaptionCompatInsetConversion();



    boolean enableCaptionCompatInsetForceConsumption();



    boolean enableCaptionCompatInsetForceConsumptionAlways();



    boolean enableCascadingWindows();



    boolean enableCompatUiVisibilityStatus();



    boolean enableCompatuiSysuiLauncher();



    boolean enableConnectedDisplaysDnd();



    boolean enableConnectedDisplaysPip();



    boolean enableConnectedDisplaysWindowDrag();



    boolean enableDesktopAppHandleAnimation();



    boolean enableDesktopAppLaunchAlttabTransitions();



    boolean enableDesktopAppLaunchAlttabTransitionsBugfix();



    boolean enableDesktopAppLaunchTransitions();



    boolean enableDesktopAppLaunchTransitionsBugfix();



    boolean enableDesktopCloseShortcutBugfix();



    boolean enableDesktopCloseTaskAnimationInDtcBugfix();



    boolean enableDesktopImeBugfix();



    boolean enableDesktopImmersiveDragBugfix();



    boolean enableDesktopIndicatorInSeparateThreadBugfix();



    boolean enableDesktopModeThroughDevOption();



    boolean enableDesktopOpeningDeeplinkMinimizeAnimationBugfix();



    boolean enableDesktopRecentsTransitionsCornersBugfix();



    boolean enableDesktopSwipeBackMinimizeAnimationBugfix();



    boolean enableDesktopSystemDialogsTransitions();



    boolean enableDesktopTabTearingMinimizeAnimationBugfix();



    boolean enableDesktopTaskbarOnFreeformDisplays();



    boolean enableDesktopTrampolineCloseAnimationBugfix();



    boolean enableDesktopWallpaperActivityForSystemUser();



    boolean enableDesktopWindowingAppHandleEducation();



    boolean enableDesktopWindowingAppToWeb();



    boolean enableDesktopWindowingAppToWebEducation();



    boolean enableDesktopWindowingAppToWebEducationIntegration();



    boolean enableDesktopWindowingBackNavigation();



    boolean enableDesktopWindowingEnterTransitionBugfix();



    boolean enableDesktopWindowingEnterTransitions();



    boolean enableDesktopWindowingExitByMinimizeTransitionBugfix();



    boolean enableDesktopWindowingExitTransitions();



    boolean enableDesktopWindowingExitTransitionsBugfix();



    boolean enableDesktopWindowingHsum();



    boolean enableDesktopWindowingImmersiveHandleHiding();



    boolean enableDesktopWindowingModalsPolicy();



    boolean enableDesktopWindowingMode();



    boolean enableDesktopWindowingMultiInstanceFeatures();



    boolean enableDesktopWindowingPersistence();



    boolean enableDesktopWindowingPip();



    boolean enableDesktopWindowingQuickSwitch();



    boolean enableDesktopWindowingScvhCacheBugFix();



    boolean enableDesktopWindowingSizeConstraints();



    boolean enableDesktopWindowingTaskLimit();



    boolean enableDesktopWindowingTaskbarRunningApps();



    boolean enableDesktopWindowingTransitions();



    boolean enableDesktopWindowingWallpaperActivity();



    boolean enableDeviceStateAutoRotateSettingLogging();



    boolean enableDeviceStateAutoRotateSettingRefactor();



    boolean enableDisplayDisconnectInteraction();



    boolean enableDisplayFocusInShellTransitions();



    boolean enableDisplayReconnectInteraction();



    boolean enableDisplayWindowingModeSwitching();



    boolean enableDragResizeSetUpInBgThread();



    boolean enableDragToDesktopIncomingTransitionsBugfix();



    boolean enableDragToMaximize();



    boolean enableDynamicRadiusComputationBugfix();



    boolean enableFullScreenWindowOnRemovingSplitScreenStageBugfix();



    boolean enableFullyImmersiveInDesktop();



    boolean enableHandleInputFix();



    boolean enableHoldToDragAppHandle();



    boolean enableInputLayerTransitionFix();



    boolean enableMinimizeButton();



    boolean enableModalsFullscreenWithPermission();



    boolean enableMoveToNextDisplayShortcut();



    boolean enableMultiDisplaySplit();



    boolean enableMultidisplayTrackpadBackGesture();



    boolean enableMultipleDesktopsBackend();



    boolean enableMultipleDesktopsFrontend();



    boolean enableNonDefaultDisplaySplit();



    boolean enableOpaqueBackgroundForTransparentWindows();



    boolean enablePerDisplayDesktopWallpaperActivity();



    boolean enablePerDisplayPackageContextCacheInStatusbarNotif();



    boolean enablePersistingDisplaySizeForConnectedDisplays();



    boolean enablePresentationForConnectedDisplays();



    boolean enableProjectedDisplayDesktopMode();



    boolean enableQuickswitchDesktopSplitBugfix();



    boolean enableRequestFullscreenBugfix();



    boolean enableResizingMetrics();



    boolean enableRestartMenuForConnectedDisplays();



    boolean enableRestoreToPreviousSizeFromDesktopImmersive();



    boolean enableShellInitialBoundsRegressionBugFix();



    boolean enableSizeCompatModeImprovementsForConnectedDisplays();



    boolean enableStartLaunchTransitionFromTaskbarBugfix();



    boolean enableTaskResizingKeyboardShortcuts();



    boolean enableTaskStackObserverInShell();



    boolean enableTaskbarConnectedDisplays();



    boolean enableTaskbarOverflow();



    boolean enableTaskbarRecentsLayoutTransition();



    boolean enableThemedAppHeaders();



    boolean enableTileResizing();



    boolean enableTopVisibleRootTaskPerUserTracking();



    boolean enableVisualIndicatorInTransitionBugfix();



    boolean enableWindowContextResourcesUpdateOnConfigChange();



    boolean enableWindowingDynamicInitialBounds();



    boolean enableWindowingEdgeDragResize();



    boolean enableWindowingScaledResizing();



    boolean enableWindowingTransitionHandlersObservers();



    boolean enforceEdgeToEdge();



    boolean ensureKeyguardDoesTransitionStarting();



    boolean ensureWallpaperInTransitions();



    boolean ensureWallpaperInWearTransitions();



    boolean enterDesktopByDefaultOnFreeformDisplays();



    boolean excludeCaptionFromAppBounds();



    boolean excludeDrawingAppThemeSnapshotFromLock();



    boolean excludeTaskFromRecents();



    boolean fifoPriorityForMajorUiProcesses();



    boolean fixHideOverlayApi();



    boolean fixLayoutExistingTask();



    boolean fixViewRootCallTrace();



    boolean forceCloseTopTransparentFullscreenTask();



    boolean formFactorBasedDesktopFirstSwitch();



    boolean getDimmerOnClosing();



    boolean ignoreAspectRatioRestrictionsForResizeableFreeformActivities();



    boolean ignoreCornerRadiusAndShadows();



    boolean includeTopTransparentFullscreenTaskInDesktopHeuristic();



    boolean inheritTaskBoundsForTrampolineTaskLaunches();



    boolean insetsDecoupledConfiguration();



    boolean jankApi();



    boolean keepAppWindowHideWhileLocked();



    boolean keyboardShortcutsToSwitchDesks();



    boolean keyguardGoingAwayTimeout();



    boolean letterboxBackgroundWallpaper();



    boolean movableCutoutConfiguration();



    boolean moveToExternalDisplayShortcut();



    boolean multiCrop();



    boolean navBarTransparentByDefault();



    boolean nestedTasksWithIndependentBounds();



    boolean noConsecutiveVisibilityEvents();



    boolean noDuplicateSurfaceDestroyedEvents();



    boolean noVisibilityEventOnDisplayStateChange();



    boolean offloadColorExtraction();



    boolean portWindowSizeAnimation();



    boolean predictiveBackDefaultEnableSdk36();



    boolean predictiveBackPrioritySystemNavigationObserver();



    boolean predictiveBackSwipeEdgeNoneApi();



    boolean predictiveBackSystemOverrideCallback();



    boolean predictiveBackThreeButtonNav();



    boolean predictiveBackTimestampApi();



    boolean processPriorityPolicyForMultiWindowMode();



    boolean rearDisplayDisableForceDesktopSystemDecorations();



    boolean recordTaskSnapshotsBeforeShutdown();



    boolean reduceChangedExclusionRectsMsgs();



    boolean reduceKeyguardTransitions();



    boolean reduceTaskSnapshotMemoryUsage();



    boolean reduceUnnecessaryMeasure();



    boolean relativeInsets();



    boolean releaseSnapshotAggressively();



    boolean releaseUserAspectRatioWm();



    boolean removeActivityStarterDreamCallback();



    boolean removeDeferHidingClient();



    boolean removeDepartTargetFromMotion();



    boolean reparentWindowTokenApi();



    boolean respectNonTopVisibleFixedOrientation();



    boolean respectOrientationChangeForUnresizeable();



    boolean safeRegionLetterboxing();



    boolean safeReleaseSnapshotAggressively();



    boolean schedulingForNotificationShade();



    boolean scrambleSnapshotFileName();



    boolean screenRecordingCallbacks();



    boolean scrollingFromLetterbox();



    boolean sdkDesiredPresentTime();



    boolean setScPropertiesInClient();



    boolean showAppHandleLargeScreens();



    boolean showDesktopExperienceDevOption();



    boolean showDesktopWindowingDevOption();



    boolean showHomeBehindDesktop();



    boolean skipCompatUiEducationInDesktopMode();



    boolean skipDecorViewRelayoutWhenClosingBugfix();



    boolean supportWidgetIntentsOnConnectedDisplay();



    boolean supportsDragAssistantToMultiwindow();



    boolean supportsMultiInstanceSystemUi();



    boolean surfaceControlInputReceiver();



    boolean surfaceTrustedOverlay();



    boolean syncScreenCapture();



    boolean systemUiPostAnimationEnd();



    boolean taskFragmentSystemOrganizerFlag();



    boolean touchPassThroughOptIn();



    boolean trackSystemUiContextBeforeWms();



    boolean transitReadyTracking();



    boolean transitTrackerPlumbing();



    boolean trustedPresentationListenerForWindow();



    boolean unifyBackNavigationTransition();



    boolean universalResizableByDefault();



    boolean untrustedEmbeddingAnyAppPermission();



    boolean untrustedEmbeddingStateSharing();



    boolean updateDimsWhenWindowShown();



    boolean useCachedInsetsForDisplaySwitch();



    boolean useRtFrameCallbackForSplashScreenTransfer();



    boolean useTasksDimOnly();



    boolean useVisibleRequestedForProcessTracker();



    boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds();



    boolean vdmForceAppUniversalResizableApi();



    boolean wallpaperOffsetAsync();



    boolean wlinfoOncreate();
}
