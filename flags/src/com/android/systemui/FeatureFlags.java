package com.android.systemui;

/** @hide */
public interface FeatureFlags {



    boolean activityTransitionUseLargestWindow();


    boolean alwaysComposeQsUiFragment();


    boolean ambientTouchMonitorListenToDisplayChanges();


    boolean animationLibraryDelayLeashCleanup();


    boolean appClipsBacklinks();


    boolean avalancheReplaceHunWhenCritical();



    boolean backButtonOnBouncer();



    boolean bouncerLifecycleFix();


    boolean bouncerUiRevamp();


    boolean bouncerUiRevamp2();


    boolean bpColors();



    boolean brightnessSliderFocusState();


    boolean classicFlagsMultiUser();


    boolean clipboardAnnounceLiveRegion();



    boolean clipboardOverlayMultiuser();


    boolean clipboardUseDescriptionMimetype();



    boolean clockFidgetAnimation();



    boolean clockModernization();


    boolean communalBouncerDoNotModifyPluginOpen();


    boolean communalEditWidgetsActivityFinishFix();


    boolean communalHub();


    boolean communalHubUseThreadPoolForWidgets();



    boolean communalResponsiveGrid();


    boolean communalShadeTouchHandlingFixes();



    boolean communalStandaloneSupport();


    boolean communalTimerFlickerFix();


    boolean communalWidgetResizing();


    boolean communalWidgetTrampolineFix();



    boolean composeBouncer();



    boolean confineNotificationTouchToViewWidth();


    boolean contAuthPlugin();


    boolean contextualTipsAssistantDismissFix();



    boolean coroutineTracing();



    boolean decoupleViewControllerInAnimlib();



    boolean desktopEffectsQsTile();



    boolean desktopScreenCapture();



    boolean disableContextualTipsFrequencyCheck();



    boolean disableContextualTipsIosSwitcherCheck();



    boolean disableDoubleClickSwapOnBouncer();



    boolean doNotUseImmediateCoroutineDispatcher();



    boolean doubleTapToSleep();


    boolean dreamBiometricPromptFixes();


    boolean dreamInputSessionPilferOnce();


    boolean dreamOverlayBouncerSwipeDirectionFiltering();


    boolean dreamOverlayUpdatedUi();


    boolean dreamPreviewTapDismiss();



    boolean dreamTransitionFixes();



    boolean edgeBackGestureHandlerThread();


    boolean edgebackGestureHandlerGetRunningTasksBackground();


    boolean enableBackgroundKeyguardOndrawnCallback();



    boolean enableConstraintLayoutLockscreenOnExternalDisplay();


    boolean enableContextualTipForMuteVolume();


    boolean enableContextualTipForPowerOff();


    boolean enableContextualTipForTakeScreenshot();


    boolean enableContextualTips();



    boolean enableDesktopGrowth();


    boolean enableEfficientDisplayRepository();



    boolean enableLayoutTracing();



    boolean enableMinmode();



    boolean enableSuggestedDeviceUi();


    boolean enableTopUiController();



    boolean enableUnderlay();



    boolean enableViewCaptureTracing();


    boolean enforceBrightnessBaseUserRestriction();



    boolean exampleFlag();


    boolean expandCollapsePrivacyDialog();


    boolean expandHeadsUpOnInlineReply();



    boolean expandedPrivacyIndicatorsOnLargeScreen();



    boolean extendedAppsShortcutCategory();


    boolean faceScanningAnimationNpeFix();


    boolean fetchBookmarksXmlKeyboardShortcuts();


    boolean fixDialogLaunchAnimationJankLogging();


    boolean fixScreenshotActionDismissSystemWindows();



    boolean flashlightStrength();



    boolean floatingMenuAnimatedTuck();



    boolean floatingMenuDragToHide();



    boolean floatingMenuHearingDeviceStatusIcon();



    boolean floatingMenuImeDisplacementAnimation();


    boolean floatingMenuNotifyTargetsChangedOnStrictDiff();



    boolean floatingMenuOverlapsNavBarsFlag();



    boolean floatingMenuRadiiAnimation();



    boolean floatingMenuRemoveFullscreenTaps();


    boolean getConnectedDeviceNameUnsynchronized();



    boolean glanceableHubAllowKeyguardWhenDreaming();



    boolean glanceableHubBlurredBackground();



    boolean glanceableHubDirectEditMode();



    boolean glanceableHubV2();



    boolean glanceableHubV2Resources();


    boolean hardwareColorStyles();


    boolean hearingAidsQsTileDialog();


    boolean hearingDevicesDialogRelatedTools();



    boolean hideRingerButtonInSingleVolumeMode();


    boolean homeControlsDreamHsum();



    boolean hsuBehaviorChanges();


    boolean hubBlurredByShadeFix();



    boolean hubEditModeTouchAdjustments();



    boolean hubEditModeTransition();


    boolean iconRefresh2025();


    boolean indicationTextA11yFix();


    boolean instantHideShade();



    boolean keyboardDockingIndicator();


    boolean keyboardShortcutHelperRewrite();


    boolean keyboardShortcutHelperShortcutCustomizer();


    boolean keyboardTouchpadContextualEducation();



    boolean keyguardTransitionForceFinishOnScreenOff();



    boolean keyguardWmStateRefactor();


    boolean lockscreenFont();



    boolean lowLightClockDream();


    boolean lowlightClockSetBrightness();


    boolean lowlightClockUsesKeyguardChargingStatus();


    boolean magneticNotificationSwipes();



    boolean mediaControlsButtonMedia3();



    boolean mediaControlsButtonMedia3Placement();



    boolean mediaControlsInCompose();


    boolean mediaControlsUiUpdate();


    boolean mediaProjectionDialogBehindLockscreen();


    boolean mediaProjectionGreyErrorText();



    boolean mediaProjectionRequestAttributionFix();



    boolean modesUiDialogPaging();


    boolean moveTransitionAnimationLayer();


    boolean msdlFeedback();



    boolean multiuserWifiPickerTrackerSupport();


    boolean newAodTransition();



    boolean newDozingKeyguardStates();


    boolean newVolumePanel();



    boolean nonTouchscreenDevicesBypassFalsing();



    boolean notesRoleQsTile();



    boolean notificationAddXOnHoverToDismiss();



    boolean notificationAmbientSuppressionAfterInflation();


    boolean notificationAnimatedActionsTreatment();


    boolean notificationAppearNonlinear();


    boolean notificationAsyncGroupHeaderInflation();


    boolean notificationAsyncHybridViewInflation();


    boolean notificationAvalancheSuppression();


    boolean notificationAvalancheThrottleHun();



    boolean notificationBackgroundTintOptimization();



    boolean notificationBundleUi();



    boolean notificationColorUpdateLogger();


    boolean notificationContentAlphaOptimization();



    boolean notificationFooterBackgroundTintOptimization();


    boolean notificationRowAccessibilityExpanded();


    boolean notificationRowContentBinderRefactor();


    boolean notificationRowTransparency();


    boolean notificationShadeBlur();


    boolean notificationShadeCloseWaitsForChildAnimations();



    boolean notificationShadeUiThread();



    boolean notificationSkipSilentUpdates();


    boolean notificationTransparentHeaderFix();


    boolean notificationsBackgroundIcons();


    boolean notificationsFooterVisibilityFix();



    boolean notificationsHideOnDisplaySwitch();



    boolean notificationsHunAccessibilityRefactor();


    boolean notificationsHunSharedAnimationValues();


    boolean notificationsIconContainerRefactor();


    boolean notificationsLaunchRadius();


    boolean notificationsLiveDataStoreRefactor();


    boolean notificationsPinnedHunInShade();


    boolean notificationsRedesignFooterView();


    boolean notifyPasswordTextViewUserActivityInBackground();


    boolean notifyPowerManagerUserActivityBackground();



    boolean ongoingActivityChipsOnDream();



    boolean overrideSuppressOverlayCondition();



    boolean permissionHelperInlineUiRichOngoing();


    boolean permissionHelperUiRichOngoing();


    boolean physicalNotificationMovement();


    boolean pinInputFieldStyledFocusState();



    boolean predictiveBackAnimateShade();



    boolean predictiveBackDelayWmTransition();



    boolean privacyDotLiveRegion();



    boolean promoteNotificationsAutomatically();



    boolean pssTaskSwitcher();



    boolean qsComposeFragmentEarlyExpansion();



    boolean qsEditModeTabs();


    boolean qsEditModeTooltip();



    boolean qsNewTiles();



    boolean qsNewTilesFuture();



    boolean qsTileDetailedView();


    boolean qsTileFocusState();



    boolean qsTileTransitionInteractionRefinement();



    boolean qsUiRefactor();


    boolean qsUiRefactorComposeFragment();



    boolean qsWifiConfig();


    boolean recordIssueQsTile();



    boolean redesignMagnificationWindowSize();



    boolean registerBatteryControllerReceiversInCorestartable();


    boolean registerContentObserversAsync();


    boolean registerNewWalletCardInBackground();


    boolean registerWallpaperNotifierBackground();



    boolean rememberViewModelOffMainThread();



    boolean removeAodCarMode();


    boolean removeDreamOverlayHideOnTouch();



    boolean removeNearbyShareTileAnimation();


    boolean removeUpdateListenerInQsIconViewImpl();



    boolean restToUnlock();



    boolean restartDreamOnUnocclude();


    boolean restrictCommunalAppWidgetHostListening();


    boolean revampedBouncerMessages();



    boolean runFingerprintDetectOnDismissibleKeyguard();


    boolean saveAndRestoreMagnificationSettingsButtons();



    boolean sceneContainer();



    boolean screenReactions();


    boolean screenshareNotificationHidingBugFix();



    boolean screenshotActionDismissSystemWindows();


    boolean screenshotAnnounceLiveRegion();



    boolean screenshotMultidisplayFocusChange();


    boolean screenshotPolicySplitAndDesktopMode();


    boolean screenshotScrollCropViewCrashFix();



    boolean secondaryUserWidgetHost();


    boolean settingsExtRegisterContentObserverOnBgThread();


    boolean shadeExpandsOnStatusBarLongPress();


    boolean shadeHeaderBlurFontColor();


    boolean shadeHeaderFontUpdate();



    boolean shadeQsvisibleLogic();



    boolean shadeWindowGoesAround();


    boolean shaderlibLoadingEffectRefactor();


    boolean shortcutHelperKeyGlyph();


    boolean showAudioSharingSliderInVolumePanel();



    boolean showClipboardIndication();



    boolean showLockedByYourWatchKeyguardIndicator();


    boolean simPinBouncerReset();


    boolean skipHideSensitiveNotifAnimation();


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



    boolean statusBarAppHandleTracking();



    boolean statusBarChipToHunAnimation();


    boolean statusBarChipsModernization();



    boolean statusBarChipsReturnAnimations();


    boolean statusBarFontUpdates();



    boolean statusBarMobileIconKairos();


    boolean statusBarNoHunBehavior();



    boolean statusBarPopupChips();


    boolean statusBarPrivacyChipAnimationExemption();


    boolean statusBarRootModernization();



    boolean statusBarRudimentaryBattery();


    boolean statusBarSignalPolicyRefactor();


    boolean statusBarSignalPolicyRefactorEthernet();



    boolean statusBarStaticInoutIndicators();


    boolean statusBarSwipeOverChip();


    boolean statusBarSwitchToSpnFromDataSpn();



    boolean statusBarSystemStatusIconsInCompose();



    boolean statusBarUiThread();


    boolean statusBarWindowNoCustomTouch();


    boolean stuckHearingDevicesQsTileFix();


    boolean switchUserOnBg();



    boolean sysuiTeamfood();



    boolean themeOverlayControllerWakefulnessDeprecation();



    boolean thinScreenRecordingService();


    boolean transitionRaceConditionPart2();


    boolean tvGlobalActionsFocus();


    boolean udfpsScreenOffUnlockFlicker();


    boolean uiRichOngoingAodSkeletonBgInflation();


    boolean unfoldAnimationBackgroundProgress();


    boolean updateCornerRadiusOnDisplayChanged();


    boolean updateUserSwitcherBackground();


    boolean updateWindowMagnifierBottomBoundary();



    boolean useAadProxSensorIfPresent();


    boolean userAwareSettingsRepositories();


    boolean userEncryptedSource();



    boolean userSwitcherAddSignOutOption();


    boolean visualInterruptionsRefactor();


    boolean volumeRedesign();
}
