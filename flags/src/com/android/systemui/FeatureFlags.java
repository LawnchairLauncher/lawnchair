package com.android.systemui;

/** @hide */
public interface FeatureFlags {




    boolean activityTransitionUseLargestWindow();



    boolean allowDozeTouchesForLockIcon();



    boolean ambientCuePlugin();



    boolean ambientTouchMonitorListenToDisplayChanges();



    boolean animationLibraryShellMigration();



    boolean appClipsBacklinks();



    boolean avalancheReplaceHunWhenCritical();



    boolean backButtonOnBouncer();



    boolean blockMouseEdgeBackGesture();



    boolean blurOnMoreSurfaces();



    boolean blurSettingsToggle();



    boolean bouncerUiRevamp();



    boolean bouncerUiRevamp2();



    boolean bpColors();



    boolean brightnessSliderFocusState();



    boolean captionsToggleInVolumeDialogV1();



    boolean checkDesktopModeForSpacialModelAppPushback();



    boolean classicFlagsMultiUser();



    boolean cleanupInstancesWhenDisplayRemoved();



    boolean clearShortcutIconTint();



    boolean clipboardOverlayMultiuser();



    boolean clipboardUseDescriptionMimetype();



    boolean clockFidgetAnimation();



    boolean clockModernization();



    boolean communalBouncerDoNotModifyPluginOpen();



    boolean communalEditWidgetsActivityFinishFix();



    boolean communalHub();



    boolean communalHubUseThreadPoolForWidgets();



    boolean communalPowerTransitionFix();



    boolean communalResponsiveGrid();



    boolean communalShadeTouchHandlingFixes();



    boolean communalStandaloneSupport();



    boolean communalTimerFlickerFix();



    boolean communalWidgetResizing();



    boolean communalWidgetTrampolineFix();



    boolean composeBouncer();



    boolean confineNotificationTouchToViewWidth();



    boolean contAuthPlugin();



    boolean coroutineTracing();



    boolean decoupleViewControllerInAnimlib();



    boolean deferDozeTransitionOnShadeDrag();



    boolean desktopAvControlsPopup();



    boolean desktopEffectsQsTile();



    boolean desktopSizing();



    boolean dialogAnimEndStateUpdate();



    boolean disableContextualTipsFrequencyCheck();



    boolean disableContextualTipsIosSwitcherCheck();



    boolean disableDoubleClickSwapOnBouncer();



    boolean disableUserSwitcherDropdownOnBouncer();



    boolean doNotUseImmediateCoroutineDispatcher();



    boolean doNotUseRunBlocking();



    boolean doubleTapToSleep();



    boolean dreamBiometricPromptFixes();



    boolean dreamBouncerTransitionFix();



    boolean dreamInputSessionPilferOnce();



    boolean dreamOverlayBouncerSwipeDirectionFiltering();



    boolean dreamOverlayUpdatedUi();



    boolean dreamSuppression();



    boolean edgeBackGestureHandlerThread();



    boolean edgebackGestureHandlerGetRunningTasksBackground();



    boolean enableBackgroundKeyguardOndrawnCallback();



    boolean enableConstraintLayoutLockscreenOnExternalDisplay();



    boolean enableContextualTipForMuteVolume();



    boolean enableCueBarAnimatedIcon();



    boolean enableDesktopGrowth();



    boolean enableEfficientDisplayRepository();



    boolean enableLayoutTracing();



    boolean enableMinmode();



    boolean enableOutputSwitcherAudioSharingButton();



    boolean enableSuggestedDeviceUi();



    boolean enableUnderlay();



    boolean enableViewCaptureTracing();



    boolean exampleFlag();



    boolean expandCollapsePrivacyDialog();



    boolean expandHeadsUpOnInlineReply();



    boolean expandableUseModifierImplementation();



    boolean expandedPrivacyIndicatorsOnLargeScreen();



    boolean extendedAppsShortcutCategory();



    boolean faceScanningAnimationNpeFix();



    boolean fetchBookmarksXmlKeyboardShortcuts();



    boolean fixShadeHeaderWrongIconSize();



    boolean flashlightStrength();



    boolean floatingMenuDragToHide();



    boolean floatingMenuHearingDeviceStatusIcon();



    boolean floatingMenuRadiiAnimation();



    boolean gestureBetweenHubAndLockscreenMotion();



    boolean getConnectedDeviceNameUnsynchronized();



    boolean glanceableHubAllowKeyguardWhenDreaming();



    boolean glanceableHubBlurredBackground();



    boolean glanceableHubDirectEditMode();



    boolean glanceableHubEnabledByDefault();



    boolean glanceableHubV2();



    boolean glanceableHubV2Resources();



    boolean globalActionsEmphasizedFont();



    boolean hardwareColorStyles();



    boolean hearingAidsQsTileDialog();



    boolean hearingDevicesDialogRelatedTools();



    boolean hideRingerButtonInSingleVolumeMode();



    boolean homeControlsDreamHsum();



    boolean hsuQsChanges();



    boolean hubBlurredByShadeFix();



    boolean hubEditModeTouchAdjustments();



    boolean hubEditModeTransition();



    boolean iconRefresh2025();



    boolean indicationTextA11yFix();



    boolean keyboardDockingIndicator();



    boolean keyboardShortcutHelperRewrite();



    boolean keyboardTouchpadContextualEducation();



    boolean keyguardTransitionForceFinishOnScreenOff();



    boolean keyguardWmStateRefactor();



    boolean largeScreenRecording();



    boolean largeScreenScreencapture();



    boolean largeScreenScreenshotAppWindow();



    boolean largeScreenSharing();



    boolean lockscreenShadeToDreamTransitionFix();



    boolean lowLightClockDream();



    boolean lowlightClockSetBrightness();



    boolean lowlightClockUsesKeyguardChargingStatus();



    boolean magneticNotificationSwipes();



    boolean mediaCarouselArrows();



    boolean mediaControlsButtonMedia3();



    boolean mediaControlsButtonMedia3Placement();



    boolean mediaControlsInCompose();



    boolean mediaControlsTranslationFix();



    boolean mediaFrameDimensionsFix();



    boolean mediaProjectionDialogBehindLockscreen();



    boolean mediaProjectionGreyErrorText();



    boolean modesUiDialogPaging();



    boolean moveTransitionAnimationLayer();



    boolean msdlFeedback();



    boolean multiuserOpenUserSwitcherDialog();



    boolean multiuserWifiPickerTrackerSupport();



    boolean newAodTransition();



    boolean newDozingKeyguardStates();



    boolean newScreenRecordToolbar();



    boolean newVolumePanel();



    boolean noExpansionOnOverscroll();



    boolean noShadeBlurOnDreamStart();



    boolean nonTouchscreenDevicesBypassFalsing();



    boolean notesRoleQsTile();



    boolean notificationAddXOnHoverToDismiss();



    boolean notificationAmbientSuppressionAfterInflation();



    boolean notificationAnimatedActionsTreatment();



    boolean notificationAppearNonlinear();



    boolean notificationAsyncGroupHeaderInflation();



    boolean notificationAvalancheSuppression();



    boolean notificationAvalancheThrottleHun();



    boolean notificationBackgroundTintOptimization();



    boolean notificationBundleUi();



    boolean notificationChildrenContainerMinHeight();



    boolean notificationColorUpdateLogger();



    boolean notificationFixHunShadows();



    boolean notificationFooterBackgroundTintOptimization();



    boolean notificationRowIsRemovedFix();



    boolean notificationRowTransparency();



    boolean notificationShadeBlur();



    boolean notificationShadeUiThread();



    boolean notificationSkipSilentUpdates();



    boolean notificationTransparentHeaderFix();



    boolean notificationsHideOnDisplaySwitch();



    boolean notificationsIconContainerRefactor();



    boolean notificationsRedesignFooterView();



    boolean ongoingActivityChipsOnDream();



    boolean overrideSuppressOverlayCondition();



    boolean permissionHelperInlineUiRichOngoing();



    boolean permissionHelperUiRichOngoing();



    boolean physicalNotificationMovement();



    boolean pinInputFieldStyledFocusState();



    boolean predictiveBackAnimateShade();



    boolean privacyDotLiveRegion();



    boolean promoteNotificationsAutomatically();



    boolean pssTaskSwitcher();



    boolean qsComposeFragmentEarlyExpansion();



    boolean qsEditModeTooltip();



    boolean qsEditModeV2();



    boolean qsMaterialExpressiveTiles();



    boolean qsNewTiles();



    boolean qsNewTilesFuture();



    boolean qsSplitInternetTile();



    boolean qsTileDetailedView();



    boolean qsTileFocusState();



    boolean qsTileTransitionInteractionRefinement();



    boolean qsUiRefactorComposeFragment();



    boolean qsWifiConfig();



    boolean recordIssueQsTile();



    boolean redesignMagnificationWindowSize();



    boolean registerWallpaperNotifierBackground();



    boolean removeDreamOverlayHideOnTouch();



    boolean removeNearbyShareTileAnimation();



    boolean removeUpdateListenerInQsIconViewImpl();



    boolean resetTilesRemovesCustomTiles();



    boolean restToUnlock();



    boolean restartDreamOnUnocclude();



    boolean restoreShowTapsSetting();



    boolean restrictCommunalAppWidgetHostListening();



    boolean restrictCommunalShadeToWhenIdle();



    boolean revampedBouncerMessages();



    boolean runFingerprintDetectOnDismissibleKeyguard();



    boolean sceneContainer();



    boolean screenOffAnimationGuardEnabled();



    boolean screenReactions();



    boolean screenshareNotificationHidingBugFix();



    boolean screenshotAnnounceLiveRegion();



    boolean screenshotDismissalSpring();



    boolean screenshotForceShutterSound();



    boolean screenshotMultidisplayFocusChange();



    boolean screenshotPolicySplitAndDesktopMode();



    boolean screenshotScrollCropViewCrashFix();



    boolean scrimFix();



    boolean secondaryUserWidgetHost();



    boolean settingsExtRegisterContentObserverOnBgThread();



    boolean shadeAppLaunchAnimationSkipInDesktop();



    boolean shadeExpandsOnStatusBarLongPress();



    boolean shadeHeaderBlurFontColor();



    boolean shadeHeaderFontUpdate();



    boolean shadeQsvisibleLogic();



    boolean shadeWindowGoesAround();



    boolean shaderlibLoadingEffectRefactor();



    boolean shortcutHelperKeyGlyph();



    boolean shortcutHelperMultiDisplaySupport();



    boolean showAudioSharingSliderInVolumePanel();



    boolean showClipboardIndication();



    boolean showIconInEmptyShade();



    boolean showLockedByYourWatchKeyguardIndicator();



    boolean signOutButtonOnKeyguardStatusBar();



    boolean simPinBouncerReset();



    boolean sliceManagerBinderCallBackground();



    boolean smartspaceRelocateToBottom();



    boolean smartspaceSwipeEventLoggingFix();



    boolean smartspaceViewpager2();



    boolean sounddoseCustomization();



    boolean spatialModelAppPushback();



    boolean spatialModelBouncerPushback();



    boolean spatialModelPushbackInShader();



    boolean stabilizeHeadsUpGroupV2();



    boolean statusBarAlwaysCheckUnderlyingNetworks();



    boolean statusBarAlwaysScheduleAutoHide();



    boolean statusBarAlwaysUseRegionSampling();



    boolean statusBarAppHandleTracking();



    boolean statusBarBatteryNoConflation();



    boolean statusBarCallChipUseIsHidden();



    boolean statusBarChipToHunAnimation();



    boolean statusBarChipsModernization();



    boolean statusBarChipsReturnAnimations();



    boolean statusBarDarkIconInteractorMixedFix();



    boolean statusBarDate();



    boolean statusBarFontUpdates();



    boolean statusBarForDesktop();



    boolean statusBarMobileIconKairos();



    boolean statusBarNoHunBehavior();



    boolean statusBarPopupChips();



    boolean statusBarPrivacyChipAnimationExemption();



    boolean statusBarRegionSampling();



    boolean statusBarRootModernization();



    boolean statusBarRudimentaryBattery();



    boolean statusBarShareDialogWithAppName();



    boolean statusBarShowIconsInSecureCamera();



    boolean statusBarStaticInoutIndicators();



    boolean statusBarSwitchToSpnFromDataSpn();



    boolean statusBarSystemStatusIconsInCompose();



    boolean statusBarUiThread();



    boolean statusBarUniversalBatteryDataSource();



    boolean stuckHearingDevicesQsTileFix();



    boolean switchUserOnBg();



    boolean sysuiIntrinsicLockDispatcher();



    boolean sysuiTeamfood();



    boolean themeOverlayControllerWakefulnessDeprecation();



    boolean thinScreenRecordingService();



    boolean unfoldAnimationBackgroundProgress();



    boolean updateKeyguardOnWakeAndUnlockEarlier();



    boolean updateUserSwitcherBackground();



    boolean updateWindowMagnifierBottomBoundaryWithMouse();



    boolean useAadProxSensorIfPresent();



    boolean userEncryptedSource();



    boolean userSwitcherAddSignOutOption();



    boolean visualInterruptionsRefactor();



    boolean volumeRedesign();



    boolean windowMagnificationMoveWithMouseOnEdge();
}
