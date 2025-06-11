package com.android.systemui;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {

    
    boolean activityTransitionUseLargestWindow();
    
    boolean ambientTouchMonitorListenToDisplayChanges();
    
    boolean appClipsBacklinks();
    
    boolean bindKeyguardMediaVisibility();
    
    boolean bpTalkback();
    
    boolean brightnessSliderFocusState();
    
    boolean centralizedStatusBarHeightFix();
    
    boolean clipboardNoninteractiveOnLockscreen();
    
    boolean clockReactiveVariants();
    
    boolean communalBouncerDoNotModifyPluginOpen();
    
    boolean communalHub();
    
    boolean composeBouncer();
    
    boolean composeLockscreen();
    
    boolean confineNotificationTouchToViewWidth();
    
    boolean constraintBp();
    
    boolean contextualTipsAssistantDismissFix();
    
    boolean coroutineTracing();
    
    boolean createWindowlessWindowMagnifier();
    
    boolean dedicatedNotifInflationThread();
    
    boolean delayShowMagnificationButton();
    
    boolean delayedWakelockReleaseOnBackgroundThread();
    
    boolean deviceEntryUdfpsRefactor();
    
    boolean disableContextualTipsFrequencyCheck();
    
    boolean disableContextualTipsIosSwitcherCheck();
    
    boolean dozeuiSchedulingAlarmsBackgroundExecution();
    
    boolean dreamInputSessionPilferOnce();
    
    boolean dreamOverlayBouncerSwipeDirectionFiltering();
    
    boolean dualShade();
    
    boolean edgeBackGestureHandlerThread();
    
    boolean edgebackGestureHandlerGetRunningTasksBackground();
    
    boolean enableBackgroundKeyguardOndrawnCallback();
    
    boolean enableContextualTipForMuteVolume();
    
    boolean enableContextualTipForPowerOff();
    
    boolean enableContextualTipForTakeScreenshot();
    
    boolean enableContextualTips();
    
    boolean enableEfficientDisplayRepository();
    
    boolean enableLayoutTracing();
    
    boolean enableViewCaptureTracing();
    
    boolean enableWidgetPickerSizeFilter();
    
    boolean enforceBrightnessBaseUserRestriction();
    
    boolean exampleFlag();
    
    boolean fastUnlockTransition();
    
    boolean fixImageWallpaperCrashSurfaceAlreadyReleased();
    
    boolean fixScreenshotActionDismissSystemWindows();
    
    boolean floatingMenuAnimatedTuck();
    
    boolean floatingMenuDragToEdit();
    
    boolean floatingMenuDragToHide();
    
    boolean floatingMenuImeDisplacementAnimation();
    
    boolean floatingMenuNarrowTargetContentObserver();
    
    boolean floatingMenuOverlapsNavBarsFlag();
    
    boolean floatingMenuRadiiAnimation();
    
    boolean generatedPreviews();
    
    boolean getConnectedDeviceNameUnsynchronized();
    
    boolean glanceableHubAllowKeyguardWhenDreaming();
    
    boolean glanceableHubFullscreenSwipe();
    
    boolean glanceableHubGestureHandle();
    
    boolean glanceableHubShortcutButton();
    
    boolean hapticBrightnessSlider();
    
    boolean hapticVolumeSlider();
    
    boolean hearingAidsQsTileDialog();
    
    boolean hearingDevicesDialogRelatedTools();
    
    boolean keyboardDockingIndicator();
    
    boolean keyboardShortcutHelperRewrite();
    
    boolean keyguardBottomAreaRefactor();
    
    boolean keyguardWmStateRefactor();
    
    boolean lightRevealMigration();
    
    boolean mediaControlsLockscreenShadeBugFix();
    
    boolean mediaControlsRefactor();
    
    boolean mediaControlsUserInitiatedDeleteintent();
    
    boolean migrateClocksToBlueprint();
    
    boolean newAodTransition();
    
    boolean newTouchpadGesturesTutorial();
    
    boolean newVolumePanel();
    
    boolean notificationAsyncGroupHeaderInflation();
    
    boolean notificationAsyncHybridViewInflation();
    
    boolean notificationAvalancheSuppression();
    
    boolean notificationAvalancheThrottleHun();
    
    boolean notificationBackgroundTintOptimization();
    
    boolean notificationColorUpdateLogger();
    
    boolean notificationContentAlphaOptimization();
    
    boolean notificationFooterBackgroundTintOptimization();
    
    boolean notificationMediaManagerBackgroundExecution();
    
    boolean notificationMinimalismPrototype();
    
    boolean notificationOverExpansionClippingFix();
    
    boolean notificationPulsingFix();
    
    boolean notificationRowContentBinderRefactor();
    
    boolean notificationRowUserContext();
    
    boolean notificationViewFlipperPausingV2();
    
    boolean notificationsBackgroundIcons();
    
    boolean notificationsFooterViewRefactor();
    
    boolean notificationsHeadsUpRefactor();
    
    boolean notificationsHideOnDisplaySwitch();
    
    boolean notificationsIconContainerRefactor();
    
    boolean notificationsImprovedHunAnimation();
    
    boolean notificationsLiveDataStoreRefactor();
    
    boolean notifyPowerManagerUserActivityBackground();
    
    boolean pinInputFieldStyledFocusState();
    
    boolean predictiveBackAnimateBouncer();
    
    boolean predictiveBackAnimateDialogs();
    
    boolean predictiveBackAnimateShade();
    
    boolean predictiveBackSysui();
    
    boolean priorityPeopleSection();
    
    boolean privacyDotUnfoldWrongCornerFix();
    
    boolean pssAppSelectorAbruptExitFix();
    
    boolean pssAppSelectorRecentsSplitScreen();
    
    boolean pssTaskSwitcher();
    
    boolean qsCustomTileClickGuaranteedBugFix();
    
    boolean qsNewPipeline();
    
    boolean qsNewTiles();
    
    boolean qsNewTilesFuture();
    
    boolean qsTileFocusState();
    
    boolean qsUiRefactor();
    
    boolean quickSettingsVisualHapticsLongpress();
    
    boolean recordIssueQsTile();
    
    boolean refactorGetCurrentUser();
    
    boolean registerBatteryControllerReceiversInCorestartable();
    
    boolean registerNewWalletCardInBackground();
    
    boolean registerWallpaperNotifierBackground();
    
    boolean registerZenModeContentObserverBackground();
    
    boolean removeDreamOverlayHideOnTouch();
    
    boolean restToUnlock();
    
    boolean restartDreamOnUnocclude();
    
    boolean revampedBouncerMessages();
    
    boolean runFingerprintDetectOnDismissibleKeyguard();
    
    boolean saveAndRestoreMagnificationSettingsButtons();
    
    boolean sceneContainer();
    
    boolean screenshareNotificationHidingBugFix();
    
    boolean screenshotActionDismissSystemWindows();
    
    boolean screenshotPrivateProfileAccessibilityAnnouncementFix();
    
    boolean screenshotPrivateProfileBehaviorFix();
    
    boolean screenshotScrollCropViewCrashFix();
    
    boolean screenshotShelfUi2();
    
    boolean shadeCollapseActivityLaunchFix();
    
    boolean shaderlibLoadingEffectRefactor();
    
    boolean sliceBroadcastRelayInBackground();
    
    boolean sliceManagerBinderCallBackground();
    
    boolean smartspaceLockscreenViewmodel();
    
    boolean smartspaceRelocateToBottom();
    
    boolean smartspaceRemoteviewsRendering();
    
    boolean statusBarMonochromeIconsFix();
    
    boolean statusBarScreenSharingChips();
    
    boolean statusBarStaticInoutIndicators();
    
    boolean switchUserOnBg();
    
    boolean sysuiTeamfood();
    
    boolean themeOverlayControllerWakefulnessDeprecation();
    
    boolean translucentOccludingActivityFix();
    
    boolean truncatedStatusBarIconsFix();
    
    boolean udfpsViewPerformance();
    
    boolean unfoldAnimationBackgroundProgress();
    
    boolean updateUserSwitcherBackground();
    
    boolean validateKeyboardShortcutHelperIconUri();
    
    boolean visualInterruptionsRefactor();
}
