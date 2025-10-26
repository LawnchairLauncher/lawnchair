package com.android.systemui;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public interface FeatureFlags {




    boolean activityTransitionUseLargestWindow();



    boolean addBlackBackgroundForWindowMagnifier();



    boolean alwaysComposeQsUiFragment();



    boolean ambientTouchMonitorListenToDisplayChanges();



    boolean appClipsBacklinks();



    boolean appShortcutRemovalFix();



    boolean avalancheReplaceHunWhenCritical();



    boolean bindKeyguardMediaVisibility();



    boolean bouncerUiRevamp();



    boolean bouncerUiRevamp2();



    boolean bpColors();



    boolean brightnessSliderFocusState();



    boolean checkLockscreenGoneTransition();



    boolean classicFlagsMultiUser();



    boolean clipboardImageTimeout();



    boolean clipboardNoninteractiveOnLockscreen();



    boolean clipboardOverlayMultiuser();



    boolean clipboardSharedTransitions();



    boolean clipboardUseDescriptionMimetype();



    boolean clockFidgetAnimation();



    boolean communalBouncerDoNotModifyPluginOpen();



    boolean communalEditWidgetsActivityFinishFix();



    boolean communalHub();



    boolean communalHubUseThreadPoolForWidgets();



    boolean communalResponsiveGrid();



    boolean communalSceneKtfRefactor();



    boolean communalStandaloneSupport();



    boolean communalTimerFlickerFix();



    boolean communalWidgetResizing();



    boolean communalWidgetTrampolineFix();



    boolean composeBouncer();



    boolean confineNotificationTouchToViewWidth();



    boolean contAuthPlugin();



    boolean contextualTipsAssistantDismissFix();



    boolean coroutineTracing();



    boolean createWindowlessWindowMagnifier();



    boolean debugLiveUpdatesPromoteAll();



    boolean decoupleViewControllerInAnimlib();



    boolean delayShowMagnificationButton();



    boolean desktopEffectsQsTile();



    boolean deviceEntryUdfpsRefactor();



    boolean disableBlurredShadeVisible();



    boolean disableContextualTipsFrequencyCheck();



    boolean disableContextualTipsIosSwitcherCheck();



    boolean disableShadeTrackpadTwoFingerSwipe();



    boolean doubleTapToSleep();



    boolean dreamInputSessionPilferOnce();



    boolean dreamOverlayBouncerSwipeDirectionFiltering();



    boolean dreamOverlayUpdatedFont();



    boolean edgeBackGestureHandlerThread();



    boolean edgebackGestureHandlerGetRunningTasksBackground();



    boolean enableBackgroundKeyguardOndrawnCallback();



    boolean enableContextualTipForMuteVolume();



    boolean enableContextualTipForPowerOff();



    boolean enableContextualTipForTakeScreenshot();



    boolean enableContextualTips();



    boolean enableEfficientDisplayRepository();



    boolean enableLayoutTracing();



    boolean enableUnderlay();



    boolean enableViewCaptureTracing();



    boolean enforceBrightnessBaseUserRestriction();



    boolean exampleFlag();



    boolean expandCollapsePrivacyDialog();



    boolean expandHeadsUpOnInlineReply();



    boolean expandedPrivacyIndicatorsOnLargeScreen();



    boolean extendedAppsShortcutCategory();



    boolean faceMessageDeferUpdate();



    boolean faceScanningAnimationNpeFix();



    boolean fasterUnlockTransition();



    boolean fetchBookmarksXmlKeyboardShortcuts();



    boolean fixImageWallpaperCrashSurfaceAlreadyReleased();



    boolean fixScreenshotActionDismissSystemWindows();



    boolean floatingMenuAnimatedTuck();



    boolean floatingMenuDisplayCutoutSupport();



    boolean floatingMenuDragToEdit();



    boolean floatingMenuDragToHide();



    boolean floatingMenuHearingDeviceStatusIcon();



    boolean floatingMenuImeDisplacementAnimation();



    boolean floatingMenuNarrowTargetContentObserver();



    boolean floatingMenuNotifyTargetsChangedOnStrictDiff();



    boolean floatingMenuOverlapsNavBarsFlag();



    boolean floatingMenuRadiiAnimation();



    boolean getConnectedDeviceNameUnsynchronized();



    boolean glanceableHubAllowKeyguardWhenDreaming();



    boolean glanceableHubBlurredBackground();



    boolean glanceableHubDirectEditMode();



    boolean glanceableHubV2();



    boolean glanceableHubV2Resources();



    boolean hapticsForComposeSliders();



    boolean hardwareColorStyles();



    boolean hearingAidsQsTileDialog();



    boolean hearingDevicesDialogRelatedTools();



    boolean hideRingerButtonInSingleVolumeMode();



    boolean homeControlsDreamHsum();



    boolean hubEditModeTouchAdjustments();



    boolean hubmodeFullscreenVerticalSwipe();



    boolean hubmodeFullscreenVerticalSwipeFix();



    boolean iconRefresh2025();



    boolean ignoreTouchesNextToNotificationShelf();



    boolean indicationTextA11yFix();



    boolean keyboardDockingIndicator();



    boolean keyboardShortcutHelperRewrite();



    boolean keyboardShortcutHelperShortcutCustomizer();



    boolean keyboardTouchpadContextualEducation();



    boolean keyguardTransitionForceFinishOnScreenOff();



    boolean keyguardWmReorderAtmsCalls();



    boolean keyguardWmStateRefactor();



    boolean lockscreenFont();



    boolean lowLightClockDream();



    boolean magneticNotificationSwipes();



    boolean mediaControlsA11yColors();



    boolean mediaControlsButtonMedia3();



    boolean mediaControlsButtonMedia3Placement();



    boolean mediaControlsDeviceManagerBackgroundExecution();



    boolean mediaControlsDrawablesReuseBugfix();



    boolean mediaControlsLockscreenShadeBugFix();



    boolean mediaControlsUiUpdate();



    boolean mediaControlsUmoInflationInBackground();



    boolean mediaControlsUserInitiatedDeleteintent();



    boolean mediaLoadMetadataViaMediaDataLoader();



    boolean mediaLockscreenLaunchAnimation();



    boolean mediaProjectionDialogBehindLockscreen();



    boolean mediaProjectionGreyErrorText();



    boolean mediaProjectionRequestAttributionFix();



    boolean modesUiDialogPaging();



    boolean moveTransitionAnimationLayer();



    boolean msdlFeedback();



    boolean multiuserWifiPickerTrackerSupport();



    boolean newAodTransition();



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



    boolean notificationOverExpansionClippingFix();



    boolean notificationReentrantDismiss();



    boolean notificationRowAccessibilityExpanded();



    boolean notificationRowContentBinderRefactor();



    boolean notificationRowTransparency();



    boolean notificationRowUserContext();



    boolean notificationShadeBlur();



    boolean notificationShadeUiThread();



    boolean notificationSkipSilentUpdates();



    boolean notificationTransparentHeaderFix();



    boolean notificationViewFlipperPausingV2();



    boolean notificationsBackgroundIcons();



    boolean notificationsFooterVisibilityFix();



    boolean notificationsHideOnDisplaySwitch();



    boolean notificationsHunSharedAnimationValues();



    boolean notificationsIconContainerRefactor();



    boolean notificationsLaunchRadius();



    boolean notificationsLiveDataStoreRefactor();



    boolean notificationsPinnedHunInShade();



    boolean notificationsRedesignFooterView();



    boolean notificationsRedesignGuts();



    boolean notifyPasswordTextViewUserActivityInBackground();



    boolean notifyPowerManagerUserActivityBackground();



    boolean onlyShowMediaStreamSliderInSingleVolumeMode();



    boolean outputSwitcherRedesign();



    boolean overrideSuppressOverlayCondition();



    boolean permissionHelperInlineUiRichOngoing();



    boolean permissionHelperUiRichOngoing();



    boolean physicalNotificationMovement();



    boolean pinInputFieldStyledFocusState();



    boolean predictiveBackAnimateShade();



    boolean predictiveBackDelayWmTransition();



    boolean priorityPeopleSection();



    boolean promoteNotificationsAutomatically();



    boolean pssAppSelectorRecentsSplitScreen();



    boolean pssTaskSwitcher();



    boolean qsCustomTileClickGuaranteedBugFix();



    boolean qsNewTiles();



    boolean qsNewTilesFuture();



    boolean qsQuickRebindActiveTiles();



    boolean qsRegisterSettingObserverOnBgThread();



    boolean qsTileDetailedView();



    boolean qsTileFocusState();



    boolean qsUiRefactor();



    boolean qsUiRefactorComposeFragment();



    boolean recordIssueQsTile();



    boolean redesignMagnificationWindowSize();



    boolean refactorGetCurrentUser();



    boolean registerBatteryControllerReceiversInCorestartable();



    boolean registerContentObserversAsync();



    boolean registerNewWalletCardInBackground();



    boolean registerWallpaperNotifierBackground();



    boolean relockWithPowerButtonImmediately();



    boolean removeDreamOverlayHideOnTouch();



    boolean removeUpdateListenerInQsIconViewImpl();



    boolean restToUnlock();



    boolean restartDreamOnUnocclude();



    boolean revampedBouncerMessages();



    boolean runFingerprintDetectOnDismissibleKeyguard();



    boolean saveAndRestoreMagnificationSettingsButtons();



    boolean sceneContainer();



    boolean screenshareNotificationHidingBugFix();



    boolean screenshotActionDismissSystemWindows();



    boolean screenshotMultidisplayFocusChange();



    boolean screenshotPolicySplitAndDesktopMode();



    boolean screenshotScrollCropViewCrashFix();



    boolean screenshotUiControllerRefactor();



    boolean secondaryUserWidgetHost();



    boolean settingsExtRegisterContentObserverOnBgThread();



    boolean shadeExpandsOnStatusBarLongPress();



    boolean shadeHeaderFontUpdate();



    boolean shadeLaunchAccessibility();



    boolean shadeWindowGoesAround();



    boolean shaderlibLoadingEffectRefactor();



    boolean shortcutHelperKeyGlyph();



    boolean showAudioSharingSliderInVolumePanel();



    boolean showClipboardIndication();



    boolean showLockedByYourWatchKeyguardIndicator();



    boolean showToastWhenAppControlBrightness();



    boolean simPinBouncerReset();



    boolean simPinRaceConditionOnRestart();



    boolean simPinUseSlotId();



    boolean skipHideSensitiveNotifAnimation();



    boolean sliceBroadcastRelayInBackground();



    boolean sliceManagerBinderCallBackground();



    boolean smartspaceLockscreenViewmodel();



    boolean smartspaceRelocateToBottom();



    boolean smartspaceRemoteviewsRenderingFix();



    boolean smartspaceSwipeEventLoggingFix();



    boolean smartspaceViewpager2();



    boolean sounddoseCustomization();



    boolean spatialModelAppPushback();



    boolean stabilizeHeadsUpGroupV2();



    boolean statusBarAlwaysCheckUnderlyingNetworks();



    boolean statusBarAutoStartScreenRecordChip();



    boolean statusBarChipsModernization();



    boolean statusBarChipsReturnAnimations();



    boolean statusBarFontUpdates();



    boolean statusBarMobileIconKairos();



    boolean statusBarMonochromeIconsFix();



    boolean statusBarNoHunBehavior();



    boolean statusBarPopupChips();



    boolean statusBarRootModernization();



    boolean statusBarShowAudioOnlyProjectionChip();



    boolean statusBarSignalPolicyRefactor();



    boolean statusBarSignalPolicyRefactorEthernet();



    boolean statusBarStaticInoutIndicators();



    boolean statusBarStopUpdatingWindowHeight();



    boolean statusBarSwipeOverChip();



    boolean statusBarSwitchToSpnFromDataSpn();



    boolean statusBarUiThread();



    boolean statusBarWindowNoCustomTouch();



    boolean stoppableFgsSystemApp();



    boolean switchUserOnBg();



    boolean sysuiTeamfood();



    boolean themeOverlayControllerWakefulnessDeprecation();



    boolean transitionRaceCondition();



    boolean translucentOccludingActivityFix();



    boolean tvGlobalActionsFocus();



    boolean udfpsViewPerformance();



    boolean unfoldAnimationBackgroundProgress();



    boolean unfoldLatencyTrackingFix();



    boolean updateCornerRadiusOnDisplayChanged();



    boolean updateUserSwitcherBackground();



    boolean updateWindowMagnifierBottomBoundary();



    boolean useAadProxSensor();



    boolean useNotifInflationThreadForFooter();



    boolean useNotifInflationThreadForRow();



    boolean useTransitionsForKeyguardOccluded();



    boolean useVolumeController();



    boolean userAwareSettingsRepositories();



    boolean userEncryptedSource();



    boolean userSwitcherAddSignOutOption();



    boolean visualInterruptionsRefactor();



    boolean volumeRedesign();
}
