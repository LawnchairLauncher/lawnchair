package com.android.window.flags2;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {

   
    boolean activityEmbeddingAnimationCustomizationFlag();
   
    boolean activityEmbeddingInteractiveDividerFlag();
   
    boolean activityEmbeddingOverlayPresentationFlag();
    
    boolean activitySnapshotByDefault();
    
    boolean activityWindowInfoFlag();
    
    boolean allowDisableActivityRecordInputSink();
   
    boolean allowHideScmButton();
    
    boolean allowsScreenSizeDecoupledFromStatusBarAndCutout();
   
    boolean alwaysDeferTransitionWhenApplyWct();
   
    boolean alwaysDrawMagnificationFullscreenBorder();
   
    boolean alwaysUpdateWallpaperPermission();
    
    boolean appCompatPropertiesApi();
    
    boolean appCompatRefactoring();
   
    boolean balDontBringExistingBackgroundTaskStackToFg();
   
    boolean balImproveRealCallerVisibilityCheck();
   
    boolean balImprovedMetrics();
   
    boolean balRequireOptInByPendingIntentCreator();
   
    boolean balRequireOptInSameUid();
   
    boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid();
   
    boolean balShowToasts();
   
    boolean balShowToastsBlocked();
   
    boolean blastSyncNotificationShadeOnDisplaySwitch();
    
    boolean bundleClientTransactionFlag();
    
    boolean cameraCompatForFreeform();
   
    boolean closeToSquareConfigIncludesStatusBar();
    
    boolean configurableFontScaleDefault();
    
    boolean coverDisplayOptIn();
    
    boolean deferDisplayUpdates();
   
    boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition();
    
    boolean delegateUnhandledDrags();
    
    boolean deleteCaptureDisplay();
    
    boolean density390Api();
    
    boolean disableObjectPool();
   
    boolean disableThinLetterboxingPolicy();
   
    boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions();
    
    boolean drawSnapshotAspectRatioMatch();
   
    boolean edgeToEdgeByDefault();
   
    boolean embeddedActivityBackNavFlag();
   
    boolean enableAdditionalWindowsAboveStatusBar();
   
    boolean enableAppHeaderWithTaskDensity();
    
    boolean enableBufferTransformHintFromDisplay();
   
    boolean enableCameraCompatForDesktopWindowing();
   
    boolean enableCompatuiSysuiLauncher();
   
    boolean enableDesktopWindowingImmersiveHandleHiding();
   
    boolean enableDesktopWindowingModalsPolicy();
   
    boolean enableDesktopWindowingMode();
   
    boolean enableDesktopWindowingQuickSwitch();
   
    boolean enableDesktopWindowingScvhCache();
   
    boolean enableDesktopWindowingSizeConstraints();
   
    boolean enableDesktopWindowingTaskLimit();
   
    boolean enableDesktopWindowingTaskbarRunningApps();
   
    boolean enableDesktopWindowingWallpaperActivity();
    
    boolean enableScaledResizing();
   
    boolean enableTaskStackObserverInShell();
   
    boolean enableThemedAppHeaders();
   
    boolean enableWindowingDynamicInitialBounds();
   
    boolean enableWindowingEdgeDragResize();
    
    boolean enableWmExtensionsForAllFlag();
    
    boolean enforceEdgeToEdge();
   
    boolean ensureWallpaperInTransitions();
    
    boolean explicitRefreshRateHints();
    
    boolean fifoPriorityForMajorUiProcesses();
    
    boolean fixNoContainerUpdateWithoutResize();
   
    boolean fixPipRestoreToOverlay();
   
    boolean fullscreenDimFlag();
    
    boolean getDimmerOnClosing();
   
    boolean immersiveAppRepositioning();
   
    boolean insetsControlChangedItem();
    
    boolean insetsControlSeq();
    
    boolean insetsDecoupledConfiguration();
    
    boolean introduceSmootherDimmer();
    
    boolean keyguardAppearTransition();
   
    boolean letterboxBackgroundWallpaper();
    
    boolean movableCutoutConfiguration();
   
    boolean moveAnimationOptionsToChange();
   
    boolean multiCrop();
   
    boolean navBarTransparentByDefault();
   
    boolean noConsecutiveVisibilityEvents();
   
    boolean noVisibilityEventOnDisplayStateChange();
   
    boolean offloadColorExtraction();
   
    boolean predictiveBackSystemAnims();
    
    boolean rearDisplayDisableForceDesktopSystemDecorations();
    
    boolean releaseSnapshotAggressively();
    
    boolean removePrepareSurfaceInPlacement();
    
    boolean screenRecordingCallbacks();
    
    boolean sdkDesiredPresentTime();
    
    boolean secureWindowState();
    
    boolean setScPropertiesInClient();
    
    boolean skipSleepingWhenSwitchingDisplay();
    
    boolean supportsMultiInstanceSystemUi();
    
    boolean surfaceControlInputReceiver();
    
    boolean surfaceTrustedOverlay();
    
    boolean syncScreenCapture();
   
    boolean taskFragmentSystemOrganizerFlag();
   
    boolean transitReadyTracking();
    
    boolean trustedPresentationListenerForWindow();
    
    boolean untrustedEmbeddingAnyAppPermission();
    
    boolean untrustedEmbeddingStateSharing();
   
    boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds();
   
    boolean userMinAspectRatioAppDefault();
   
    boolean waitForTransitionOnDisplaySwitch();
    
    boolean wallpaperOffsetAsync();
    
    boolean windowSessionRelayoutInfo();
   
    boolean windowTokenConfigThreadSafe();
}
