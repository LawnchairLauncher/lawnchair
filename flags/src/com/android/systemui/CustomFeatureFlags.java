package com.android.systemui;

// TODO(b/303773055): Remove the annotation after access issue is resolved.
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
    public boolean activityTransitionUseLargestWindow() {
        return getValue(Flags.FLAG_ACTIVITY_TRANSITION_USE_LARGEST_WINDOW,
                FeatureFlags::activityTransitionUseLargestWindow);
    }

    @Override
    public boolean ambientTouchMonitorListenToDisplayChanges() {
        return getValue(Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES,
                FeatureFlags::ambientTouchMonitorListenToDisplayChanges);
    }

    @Override
    public boolean appClipsBacklinks() {
        return getValue(Flags.FLAG_APP_CLIPS_BACKLINKS,
                FeatureFlags::appClipsBacklinks);
    }

    @Override
    public boolean bindKeyguardMediaVisibility() {
        return getValue(Flags.FLAG_BIND_KEYGUARD_MEDIA_VISIBILITY,
                FeatureFlags::bindKeyguardMediaVisibility);
    }

    @Override
    public boolean bpTalkback() {
        return getValue(Flags.FLAG_BP_TALKBACK,
                FeatureFlags::bpTalkback);
    }

    @Override
    public boolean brightnessSliderFocusState() {
        return getValue(Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE,
                FeatureFlags::brightnessSliderFocusState);
    }

    @Override
    public boolean centralizedStatusBarHeightFix() {
        return getValue(Flags.FLAG_CENTRALIZED_STATUS_BAR_HEIGHT_FIX,
                FeatureFlags::centralizedStatusBarHeightFix);
    }

    @Override
    public boolean clipboardNoninteractiveOnLockscreen() {
        return getValue(Flags.FLAG_CLIPBOARD_NONINTERACTIVE_ON_LOCKSCREEN,
                FeatureFlags::clipboardNoninteractiveOnLockscreen);
    }

    @Override
    public boolean clockReactiveVariants() {
        return getValue(Flags.FLAG_CLOCK_REACTIVE_VARIANTS,
                FeatureFlags::clockReactiveVariants);
    }

    @Override
    public boolean communalBouncerDoNotModifyPluginOpen() {
        return getValue(Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN,
                FeatureFlags::communalBouncerDoNotModifyPluginOpen);
    }

    @Override
    public boolean communalHub() {
        return getValue(Flags.FLAG_COMMUNAL_HUB,
                FeatureFlags::communalHub);
    }

    @Override
    public boolean composeBouncer() {
        return getValue(Flags.FLAG_COMPOSE_BOUNCER,
                FeatureFlags::composeBouncer);
    }

    @Override
    public boolean composeLockscreen() {
        return getValue(Flags.FLAG_COMPOSE_LOCKSCREEN,
                FeatureFlags::composeLockscreen);
    }

    @Override
    public boolean confineNotificationTouchToViewWidth() {
        return getValue(Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH,
                FeatureFlags::confineNotificationTouchToViewWidth);
    }

    @Override
    public boolean constraintBp() {
        return getValue(Flags.FLAG_CONSTRAINT_BP,
                FeatureFlags::constraintBp);
    }

    @Override
    public boolean contextualTipsAssistantDismissFix() {
        return getValue(Flags.FLAG_CONTEXTUAL_TIPS_ASSISTANT_DISMISS_FIX,
                FeatureFlags::contextualTipsAssistantDismissFix);
    }

    @Override
    public boolean coroutineTracing() {
        return getValue(Flags.FLAG_COROUTINE_TRACING,
                FeatureFlags::coroutineTracing);
    }

    @Override
    public boolean createWindowlessWindowMagnifier() {
        return getValue(Flags.FLAG_CREATE_WINDOWLESS_WINDOW_MAGNIFIER,
                FeatureFlags::createWindowlessWindowMagnifier);
    }

    @Override
    public boolean dedicatedNotifInflationThread() {
        return getValue(Flags.FLAG_DEDICATED_NOTIF_INFLATION_THREAD,
                FeatureFlags::dedicatedNotifInflationThread);
    }

    @Override
    public boolean delayShowMagnificationButton() {
        return getValue(Flags.FLAG_DELAY_SHOW_MAGNIFICATION_BUTTON,
                FeatureFlags::delayShowMagnificationButton);
    }

    @Override
    public boolean delayedWakelockReleaseOnBackgroundThread() {
        return getValue(Flags.FLAG_DELAYED_WAKELOCK_RELEASE_ON_BACKGROUND_THREAD,
                FeatureFlags::delayedWakelockReleaseOnBackgroundThread);
    }

    @Override
    public boolean deviceEntryUdfpsRefactor() {
        return getValue(Flags.FLAG_DEVICE_ENTRY_UDFPS_REFACTOR,
                FeatureFlags::deviceEntryUdfpsRefactor);
    }

    @Override
    public boolean disableContextualTipsFrequencyCheck() {
        return getValue(Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_FREQUENCY_CHECK,
                FeatureFlags::disableContextualTipsFrequencyCheck);
    }

    @Override
    public boolean disableContextualTipsIosSwitcherCheck() {
        return getValue(Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_IOS_SWITCHER_CHECK,
                FeatureFlags::disableContextualTipsIosSwitcherCheck);
    }

    @Override
    public boolean dozeuiSchedulingAlarmsBackgroundExecution() {
        return getValue(Flags.FLAG_DOZEUI_SCHEDULING_ALARMS_BACKGROUND_EXECUTION,
                FeatureFlags::dozeuiSchedulingAlarmsBackgroundExecution);
    }

    @Override
    public boolean dreamInputSessionPilferOnce() {
        return getValue(Flags.FLAG_DREAM_INPUT_SESSION_PILFER_ONCE,
                FeatureFlags::dreamInputSessionPilferOnce);
    }

    @Override
    public boolean dreamOverlayBouncerSwipeDirectionFiltering() {
        return getValue(Flags.FLAG_DREAM_OVERLAY_BOUNCER_SWIPE_DIRECTION_FILTERING,
                FeatureFlags::dreamOverlayBouncerSwipeDirectionFiltering);
    }

    @Override
    public boolean dualShade() {
        return getValue(Flags.FLAG_DUAL_SHADE,
                FeatureFlags::dualShade);
    }

    @Override
    public boolean edgeBackGestureHandlerThread() {
        return getValue(Flags.FLAG_EDGE_BACK_GESTURE_HANDLER_THREAD,
                FeatureFlags::edgeBackGestureHandlerThread);
    }

    @Override
    public boolean edgebackGestureHandlerGetRunningTasksBackground() {
        return getValue(Flags.FLAG_EDGEBACK_GESTURE_HANDLER_GET_RUNNING_TASKS_BACKGROUND,
                FeatureFlags::edgebackGestureHandlerGetRunningTasksBackground);
    }

    @Override
    public boolean enableBackgroundKeyguardOndrawnCallback() {
        return getValue(Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
                FeatureFlags::enableBackgroundKeyguardOndrawnCallback);
    }

    @Override
    public boolean enableContextualTipForMuteVolume() {
        return getValue(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_MUTE_VOLUME,
                FeatureFlags::enableContextualTipForMuteVolume);
    }

    @Override
    public boolean enableContextualTipForPowerOff() {
        return getValue(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_POWER_OFF,
                FeatureFlags::enableContextualTipForPowerOff);
    }

    @Override
    public boolean enableContextualTipForTakeScreenshot() {
        return getValue(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_TAKE_SCREENSHOT,
                FeatureFlags::enableContextualTipForTakeScreenshot);
    }

    @Override
    public boolean enableContextualTips() {
        return getValue(Flags.FLAG_ENABLE_CONTEXTUAL_TIPS,
                FeatureFlags::enableContextualTips);
    }

    @Override
    public boolean enableEfficientDisplayRepository() {
        return getValue(Flags.FLAG_ENABLE_EFFICIENT_DISPLAY_REPOSITORY,
                FeatureFlags::enableEfficientDisplayRepository);
    }

    @Override
    public boolean enableLayoutTracing() {
        return getValue(Flags.FLAG_ENABLE_LAYOUT_TRACING,
                FeatureFlags::enableLayoutTracing);
    }

    @Override
    public boolean enableViewCaptureTracing() {
        return getValue(Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING,
                FeatureFlags::enableViewCaptureTracing);
    }

    @Override
    public boolean enableWidgetPickerSizeFilter() {
        return getValue(Flags.FLAG_ENABLE_WIDGET_PICKER_SIZE_FILTER,
                FeatureFlags::enableWidgetPickerSizeFilter);
    }

    @Override
    public boolean enforceBrightnessBaseUserRestriction() {
        return getValue(Flags.FLAG_ENFORCE_BRIGHTNESS_BASE_USER_RESTRICTION,
                FeatureFlags::enforceBrightnessBaseUserRestriction);
    }

    @Override
    public boolean exampleFlag() {
        return getValue(Flags.FLAG_EXAMPLE_FLAG,
                FeatureFlags::exampleFlag);
    }

    @Override
    public boolean fastUnlockTransition() {
        return getValue(Flags.FLAG_FAST_UNLOCK_TRANSITION,
                FeatureFlags::fastUnlockTransition);
    }

    @Override
    public boolean fixImageWallpaperCrashSurfaceAlreadyReleased() {
        return getValue(Flags.FLAG_FIX_IMAGE_WALLPAPER_CRASH_SURFACE_ALREADY_RELEASED,
                FeatureFlags::fixImageWallpaperCrashSurfaceAlreadyReleased);
    }

    @Override
    public boolean fixScreenshotActionDismissSystemWindows() {
        return getValue(Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
                FeatureFlags::fixScreenshotActionDismissSystemWindows);
    }

    @Override
    public boolean floatingMenuAnimatedTuck() {
        return getValue(Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK,
                FeatureFlags::floatingMenuAnimatedTuck);
    }

    @Override
    public boolean floatingMenuDragToEdit() {
        return getValue(Flags.FLAG_FLOATING_MENU_DRAG_TO_EDIT,
                FeatureFlags::floatingMenuDragToEdit);
    }

    @Override
    public boolean floatingMenuDragToHide() {
        return getValue(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE,
                FeatureFlags::floatingMenuDragToHide);
    }

    @Override
    public boolean floatingMenuImeDisplacementAnimation() {
        return getValue(Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION,
                FeatureFlags::floatingMenuImeDisplacementAnimation);
    }

    @Override
    public boolean floatingMenuNarrowTargetContentObserver() {
        return getValue(Flags.FLAG_FLOATING_MENU_NARROW_TARGET_CONTENT_OBSERVER,
                FeatureFlags::floatingMenuNarrowTargetContentObserver);
    }

    @Override
    public boolean floatingMenuOverlapsNavBarsFlag() {
        return getValue(Flags.FLAG_FLOATING_MENU_OVERLAPS_NAV_BARS_FLAG,
                FeatureFlags::floatingMenuOverlapsNavBarsFlag);
    }

    @Override
    public boolean floatingMenuRadiiAnimation() {
        return getValue(Flags.FLAG_FLOATING_MENU_RADII_ANIMATION,
                FeatureFlags::floatingMenuRadiiAnimation);
    }
    
    @Override
    public boolean generatedPreviews() {
        return getValue(Flags.FLAG_GENERATED_PREVIEWS,
            FeatureFlags::generatedPreviews);
    }

    @Override
    public boolean getConnectedDeviceNameUnsynchronized() {
        return getValue(Flags.FLAG_GET_CONNECTED_DEVICE_NAME_UNSYNCHRONIZED,
                FeatureFlags::getConnectedDeviceNameUnsynchronized);
    }

    @Override
    public boolean glanceableHubAllowKeyguardWhenDreaming() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_ALLOW_KEYGUARD_WHEN_DREAMING,
                FeatureFlags::glanceableHubAllowKeyguardWhenDreaming);
    }

    @Override
    public boolean glanceableHubFullscreenSwipe() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_FULLSCREEN_SWIPE,
                FeatureFlags::glanceableHubFullscreenSwipe);
    }

    @Override
    public boolean glanceableHubGestureHandle() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_GESTURE_HANDLE,
                FeatureFlags::glanceableHubGestureHandle);
    }

    @Override
    public boolean glanceableHubShortcutButton() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_SHORTCUT_BUTTON,
                FeatureFlags::glanceableHubShortcutButton);
    }

    @Override
    public boolean hapticBrightnessSlider() {
        return getValue(Flags.FLAG_HAPTIC_BRIGHTNESS_SLIDER,
                FeatureFlags::hapticBrightnessSlider);
    }

    @Override
    public boolean hapticVolumeSlider() {
        return getValue(Flags.FLAG_HAPTIC_VOLUME_SLIDER,
                FeatureFlags::hapticVolumeSlider);
    }

    @Override
    public boolean hearingAidsQsTileDialog() {
        return getValue(Flags.FLAG_HEARING_AIDS_QS_TILE_DIALOG,
                FeatureFlags::hearingAidsQsTileDialog);
    }

    @Override
    public boolean hearingDevicesDialogRelatedTools() {
        return getValue(Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS,
                FeatureFlags::hearingDevicesDialogRelatedTools);
    }

    @Override
    public boolean keyboardDockingIndicator() {
        return getValue(Flags.FLAG_KEYBOARD_DOCKING_INDICATOR,
                FeatureFlags::keyboardDockingIndicator);
    }

    @Override
    public boolean keyboardShortcutHelperRewrite() {
        return getValue(Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_REWRITE,
                FeatureFlags::keyboardShortcutHelperRewrite);
    }

    @Override
    public boolean keyguardBottomAreaRefactor() {
        return getValue(Flags.FLAG_KEYGUARD_BOTTOM_AREA_REFACTOR,
                FeatureFlags::keyguardBottomAreaRefactor);
    }

    @Override
    public boolean keyguardWmStateRefactor() {
        return getValue(Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR,
                FeatureFlags::keyguardWmStateRefactor);
    }

    @Override
    public boolean lightRevealMigration() {
        return getValue(Flags.FLAG_LIGHT_REVEAL_MIGRATION,
                FeatureFlags::lightRevealMigration);
    }

    @Override
    public boolean mediaControlsLockscreenShadeBugFix() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_LOCKSCREEN_SHADE_BUG_FIX,
                FeatureFlags::mediaControlsLockscreenShadeBugFix);
    }

    @Override
    public boolean mediaControlsRefactor() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_REFACTOR,
                FeatureFlags::mediaControlsRefactor);
    }

    @Override
    public boolean mediaControlsUserInitiatedDeleteintent() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_USER_INITIATED_DELETEINTENT,
                FeatureFlags::mediaControlsUserInitiatedDeleteintent);
    }

    @Override
    public boolean migrateClocksToBlueprint() {
        return getValue(Flags.FLAG_MIGRATE_CLOCKS_TO_BLUEPRINT,
                FeatureFlags::migrateClocksToBlueprint);
    }

    @Override
    public boolean newAodTransition() {
        return getValue(Flags.FLAG_NEW_AOD_TRANSITION,
                FeatureFlags::newAodTransition);
    }

    @Override
    public boolean newTouchpadGesturesTutorial() {
        return getValue(Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL,
                FeatureFlags::newTouchpadGesturesTutorial);
    }

    @Override
    public boolean newVolumePanel() {
        return getValue(Flags.FLAG_NEW_VOLUME_PANEL,
                FeatureFlags::newVolumePanel);
    }

    @Override
    public boolean notificationAsyncGroupHeaderInflation() {
        return getValue(Flags.FLAG_NOTIFICATION_ASYNC_GROUP_HEADER_INFLATION,
                FeatureFlags::notificationAsyncGroupHeaderInflation);
    }

    @Override
    public boolean notificationAsyncHybridViewInflation() {
        return getValue(Flags.FLAG_NOTIFICATION_ASYNC_HYBRID_VIEW_INFLATION,
                FeatureFlags::notificationAsyncHybridViewInflation);
    }

    @Override
    public boolean notificationAvalancheSuppression() {
        return getValue(Flags.FLAG_NOTIFICATION_AVALANCHE_SUPPRESSION,
                FeatureFlags::notificationAvalancheSuppression);
    }

    @Override
    public boolean notificationAvalancheThrottleHun() {
        return getValue(Flags.FLAG_NOTIFICATION_AVALANCHE_THROTTLE_HUN,
                FeatureFlags::notificationAvalancheThrottleHun);
    }

    @Override
    public boolean notificationBackgroundTintOptimization() {
        return getValue(Flags.FLAG_NOTIFICATION_BACKGROUND_TINT_OPTIMIZATION,
                FeatureFlags::notificationBackgroundTintOptimization);
    }

    @Override
    public boolean notificationColorUpdateLogger() {
        return getValue(Flags.FLAG_NOTIFICATION_COLOR_UPDATE_LOGGER,
                FeatureFlags::notificationColorUpdateLogger);
    }

    @Override
    public boolean notificationContentAlphaOptimization() {
        return getValue(Flags.FLAG_NOTIFICATION_CONTENT_ALPHA_OPTIMIZATION,
                FeatureFlags::notificationContentAlphaOptimization);
    }

    @Override
    public boolean notificationFooterBackgroundTintOptimization() {
        return getValue(Flags.FLAG_NOTIFICATION_FOOTER_BACKGROUND_TINT_OPTIMIZATION,
                FeatureFlags::notificationFooterBackgroundTintOptimization);
    }

    @Override
    public boolean notificationMediaManagerBackgroundExecution() {
        return getValue(Flags.FLAG_NOTIFICATION_MEDIA_MANAGER_BACKGROUND_EXECUTION,
                FeatureFlags::notificationMediaManagerBackgroundExecution);
    }

    @Override
    public boolean notificationMinimalismPrototype() {
        return getValue(Flags.FLAG_NOTIFICATION_MINIMALISM_PROTOTYPE,
                FeatureFlags::notificationMinimalismPrototype);
    }

    @Override
    public boolean notificationOverExpansionClippingFix() {
        return getValue(Flags.FLAG_NOTIFICATION_OVER_EXPANSION_CLIPPING_FIX,
                FeatureFlags::notificationOverExpansionClippingFix);
    }

    @Override
    public boolean notificationPulsingFix() {
        return getValue(Flags.FLAG_NOTIFICATION_PULSING_FIX,
                FeatureFlags::notificationPulsingFix);
    }

    @Override
    public boolean notificationRowContentBinderRefactor() {
        return getValue(Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR,
                FeatureFlags::notificationRowContentBinderRefactor);
    }

    @Override
    public boolean notificationRowUserContext() {
        return getValue(Flags.FLAG_NOTIFICATION_ROW_USER_CONTEXT,
                FeatureFlags::notificationRowUserContext);
    }

    @Override
    public boolean notificationViewFlipperPausingV2() {
        return getValue(Flags.FLAG_NOTIFICATION_VIEW_FLIPPER_PAUSING_V2,
                FeatureFlags::notificationViewFlipperPausingV2);
    }

    @Override
    public boolean notificationsBackgroundIcons() {
        return getValue(Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS,
                FeatureFlags::notificationsBackgroundIcons);
    }

    @Override
    public boolean notificationsFooterViewRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_FOOTER_VIEW_REFACTOR,
                FeatureFlags::notificationsFooterViewRefactor);
    }

    @Override
    public boolean notificationsHeadsUpRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_HEADS_UP_REFACTOR,
                FeatureFlags::notificationsHeadsUpRefactor);
    }

    @Override
    public boolean notificationsHideOnDisplaySwitch() {
        return getValue(Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH,
                FeatureFlags::notificationsHideOnDisplaySwitch);
    }

    @Override
    public boolean notificationsIconContainerRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR,
                FeatureFlags::notificationsIconContainerRefactor);
    }

    @Override
    public boolean notificationsImprovedHunAnimation() {
        return getValue(Flags.FLAG_NOTIFICATIONS_IMPROVED_HUN_ANIMATION,
                FeatureFlags::notificationsImprovedHunAnimation);
    }

    @Override
    public boolean notificationsLiveDataStoreRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR,
                FeatureFlags::notificationsLiveDataStoreRefactor);
    }

    @Override
    public boolean notifyPowerManagerUserActivityBackground() {
        return getValue(Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND,
                FeatureFlags::notifyPowerManagerUserActivityBackground);
    }

    @Override
    public boolean pinInputFieldStyledFocusState() {
        return getValue(Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE,
                FeatureFlags::pinInputFieldStyledFocusState);
    }

    @Override
    public boolean predictiveBackAnimateBouncer() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_BOUNCER,
                FeatureFlags::predictiveBackAnimateBouncer);
    }

    @Override
    public boolean predictiveBackAnimateDialogs() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_DIALOGS,
                FeatureFlags::predictiveBackAnimateDialogs);
    }

    @Override
    public boolean predictiveBackAnimateShade() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE,
                FeatureFlags::predictiveBackAnimateShade);
    }

    @Override
    public boolean predictiveBackSysui() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_SYSUI,
                FeatureFlags::predictiveBackSysui);
    }

    @Override
    public boolean priorityPeopleSection() {
        return getValue(Flags.FLAG_PRIORITY_PEOPLE_SECTION,
                FeatureFlags::priorityPeopleSection);
    }

    @Override
    public boolean privacyDotUnfoldWrongCornerFix() {
        return getValue(Flags.FLAG_PRIVACY_DOT_UNFOLD_WRONG_CORNER_FIX,
                FeatureFlags::privacyDotUnfoldWrongCornerFix);
    }

    @Override
    public boolean pssAppSelectorAbruptExitFix() {
        return getValue(Flags.FLAG_PSS_APP_SELECTOR_ABRUPT_EXIT_FIX,
                FeatureFlags::pssAppSelectorAbruptExitFix);
    }

    @Override
    public boolean pssAppSelectorRecentsSplitScreen() {
        return getValue(Flags.FLAG_PSS_APP_SELECTOR_RECENTS_SPLIT_SCREEN,
                FeatureFlags::pssAppSelectorRecentsSplitScreen);
    }

    @Override
    public boolean pssTaskSwitcher() {
        return getValue(Flags.FLAG_PSS_TASK_SWITCHER,
                FeatureFlags::pssTaskSwitcher);
    }

    @Override
    public boolean qsCustomTileClickGuaranteedBugFix() {
        return getValue(Flags.FLAG_QS_CUSTOM_TILE_CLICK_GUARANTEED_BUG_FIX,
                FeatureFlags::qsCustomTileClickGuaranteedBugFix);
    }

    @Override
    public boolean qsNewPipeline() {
        return getValue(Flags.FLAG_QS_NEW_PIPELINE,
                FeatureFlags::qsNewPipeline);
    }

    @Override
    public boolean qsNewTiles() {
        return getValue(Flags.FLAG_QS_NEW_TILES,
                FeatureFlags::qsNewTiles);
    }

    @Override
    public boolean qsNewTilesFuture() {
        return getValue(Flags.FLAG_QS_NEW_TILES_FUTURE,
                FeatureFlags::qsNewTilesFuture);
    }

    @Override
    public boolean qsTileFocusState() {
        return getValue(Flags.FLAG_QS_TILE_FOCUS_STATE,
                FeatureFlags::qsTileFocusState);
    }

    @Override
    public boolean qsUiRefactor() {
        return getValue(Flags.FLAG_QS_UI_REFACTOR,
                FeatureFlags::qsUiRefactor);
    }

    @Override
    public boolean quickSettingsVisualHapticsLongpress() {
        return getValue(Flags.FLAG_QUICK_SETTINGS_VISUAL_HAPTICS_LONGPRESS,
                FeatureFlags::quickSettingsVisualHapticsLongpress);
    }

    @Override
    public boolean recordIssueQsTile() {
        return getValue(Flags.FLAG_RECORD_ISSUE_QS_TILE,
                FeatureFlags::recordIssueQsTile);
    }

    @Override
    public boolean refactorGetCurrentUser() {
        return getValue(Flags.FLAG_REFACTOR_GET_CURRENT_USER,
                FeatureFlags::refactorGetCurrentUser);
    }

    @Override
    public boolean registerBatteryControllerReceiversInCorestartable() {
        return getValue(Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE,
                FeatureFlags::registerBatteryControllerReceiversInCorestartable);
    }

    @Override
    public boolean registerNewWalletCardInBackground() {
        return getValue(Flags.FLAG_REGISTER_NEW_WALLET_CARD_IN_BACKGROUND,
                FeatureFlags::registerNewWalletCardInBackground);
    }

    @Override
    public boolean registerWallpaperNotifierBackground() {
        return getValue(Flags.FLAG_REGISTER_WALLPAPER_NOTIFIER_BACKGROUND,
                FeatureFlags::registerWallpaperNotifierBackground);
    }

    @Override
    public boolean registerZenModeContentObserverBackground() {
        return getValue(Flags.FLAG_REGISTER_ZEN_MODE_CONTENT_OBSERVER_BACKGROUND,
                FeatureFlags::registerZenModeContentObserverBackground);
    }

    @Override
    public boolean removeDreamOverlayHideOnTouch() {
        return getValue(Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH,
                FeatureFlags::removeDreamOverlayHideOnTouch);
    }

    @Override
    public boolean restToUnlock() {
        return getValue(Flags.FLAG_REST_TO_UNLOCK,
                FeatureFlags::restToUnlock);
    }

    @Override
    public boolean restartDreamOnUnocclude() {
        return getValue(Flags.FLAG_RESTART_DREAM_ON_UNOCCLUDE,
                FeatureFlags::restartDreamOnUnocclude);
    }

    @Override
    public boolean revampedBouncerMessages() {
        return getValue(Flags.FLAG_REVAMPED_BOUNCER_MESSAGES,
                FeatureFlags::revampedBouncerMessages);
    }

    @Override
    public boolean runFingerprintDetectOnDismissibleKeyguard() {
        return getValue(Flags.FLAG_RUN_FINGERPRINT_DETECT_ON_DISMISSIBLE_KEYGUARD,
                FeatureFlags::runFingerprintDetectOnDismissibleKeyguard);
    }

    @Override
    public boolean saveAndRestoreMagnificationSettingsButtons() {
        return getValue(Flags.FLAG_SAVE_AND_RESTORE_MAGNIFICATION_SETTINGS_BUTTONS,
                FeatureFlags::saveAndRestoreMagnificationSettingsButtons);
    }

    @Override
    public boolean sceneContainer() {
        return getValue(Flags.FLAG_SCENE_CONTAINER,
                FeatureFlags::sceneContainer);
    }

    @Override
    public boolean screenshareNotificationHidingBugFix() {
        return getValue(Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX,
                FeatureFlags::screenshareNotificationHidingBugFix);
    }

    @Override
    public boolean screenshotActionDismissSystemWindows() {
        return getValue(Flags.FLAG_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
                FeatureFlags::screenshotActionDismissSystemWindows);
    }

    @Override
    public boolean screenshotPrivateProfileAccessibilityAnnouncementFix() {
        return getValue(Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_ACCESSIBILITY_ANNOUNCEMENT_FIX,
                FeatureFlags::screenshotPrivateProfileAccessibilityAnnouncementFix);
    }

    @Override
    public boolean screenshotPrivateProfileBehaviorFix() {
        return getValue(Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_BEHAVIOR_FIX,
                FeatureFlags::screenshotPrivateProfileBehaviorFix);
    }

    @Override
    public boolean screenshotScrollCropViewCrashFix() {
        return getValue(Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX,
                FeatureFlags::screenshotScrollCropViewCrashFix);
    }

    @Override
    public boolean screenshotShelfUi2() {
        return getValue(Flags.FLAG_SCREENSHOT_SHELF_UI2,
                FeatureFlags::screenshotShelfUi2);
    }

    @Override
    public boolean shadeCollapseActivityLaunchFix() {
        return getValue(Flags.FLAG_SHADE_COLLAPSE_ACTIVITY_LAUNCH_FIX,
                FeatureFlags::shadeCollapseActivityLaunchFix);
    }

    @Override
    public boolean shaderlibLoadingEffectRefactor() {
        return getValue(Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR,
                FeatureFlags::shaderlibLoadingEffectRefactor);
    }

    @Override
    public boolean sliceBroadcastRelayInBackground() {
        return getValue(Flags.FLAG_SLICE_BROADCAST_RELAY_IN_BACKGROUND,
                FeatureFlags::sliceBroadcastRelayInBackground);
    }

    @Override
    public boolean sliceManagerBinderCallBackground() {
        return getValue(Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND,
                FeatureFlags::sliceManagerBinderCallBackground);
    }

    @Override
    public boolean smartspaceLockscreenViewmodel() {
        return getValue(Flags.FLAG_SMARTSPACE_LOCKSCREEN_VIEWMODEL,
                FeatureFlags::smartspaceLockscreenViewmodel);
    }

    @Override
    public boolean smartspaceRelocateToBottom() {
        return getValue(Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM,
                FeatureFlags::smartspaceRelocateToBottom);
    }

    @Override
    public boolean smartspaceRemoteviewsRendering() {
        return getValue(Flags.FLAG_SMARTSPACE_REMOTEVIEWS_RENDERING,
                FeatureFlags::smartspaceRemoteviewsRendering);
    }

    @Override
    public boolean statusBarMonochromeIconsFix() {
        return getValue(Flags.FLAG_STATUS_BAR_MONOCHROME_ICONS_FIX,
                FeatureFlags::statusBarMonochromeIconsFix);
    }

    @Override
    public boolean statusBarScreenSharingChips() {
        return getValue(Flags.FLAG_STATUS_BAR_SCREEN_SHARING_CHIPS,
                FeatureFlags::statusBarScreenSharingChips);
    }

    @Override
    public boolean statusBarStaticInoutIndicators() {
        return getValue(Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS,
                FeatureFlags::statusBarStaticInoutIndicators);
    }

    @Override
    public boolean switchUserOnBg() {
        return getValue(Flags.FLAG_SWITCH_USER_ON_BG,
                FeatureFlags::switchUserOnBg);
    }

    @Override
    public boolean sysuiTeamfood() {
        return getValue(Flags.FLAG_SYSUI_TEAMFOOD,
                FeatureFlags::sysuiTeamfood);
    }

    @Override
    public boolean themeOverlayControllerWakefulnessDeprecation() {
        return getValue(Flags.FLAG_THEME_OVERLAY_CONTROLLER_WAKEFULNESS_DEPRECATION,
                FeatureFlags::themeOverlayControllerWakefulnessDeprecation);
    }

    @Override
    public boolean translucentOccludingActivityFix() {
        return getValue(Flags.FLAG_TRANSLUCENT_OCCLUDING_ACTIVITY_FIX,
                FeatureFlags::translucentOccludingActivityFix);
    }

    @Override
    public boolean truncatedStatusBarIconsFix() {
        return getValue(Flags.FLAG_TRUNCATED_STATUS_BAR_ICONS_FIX,
                FeatureFlags::truncatedStatusBarIconsFix);
    }

    @Override
    public boolean udfpsViewPerformance() {
        return getValue(Flags.FLAG_UDFPS_VIEW_PERFORMANCE,
                FeatureFlags::udfpsViewPerformance);
    }

    @Override
    public boolean unfoldAnimationBackgroundProgress() {
        return getValue(Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS,
                FeatureFlags::unfoldAnimationBackgroundProgress);
    }

    @Override
    public boolean updateUserSwitcherBackground() {
        return getValue(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND,
                FeatureFlags::updateUserSwitcherBackground);
    }

    @Override
    public boolean validateKeyboardShortcutHelperIconUri() {
        return getValue(Flags.FLAG_VALIDATE_KEYBOARD_SHORTCUT_HELPER_ICON_URI,
                FeatureFlags::validateKeyboardShortcutHelperIconUri);
    }

    @Override
    public boolean visualInterruptionsRefactor() {
        return getValue(Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR,
                FeatureFlags::visualInterruptionsRefactor);
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
                Flags.FLAG_ACTIVITY_TRANSITION_USE_LARGEST_WINDOW,
                Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES,
                Flags.FLAG_APP_CLIPS_BACKLINKS,
                Flags.FLAG_BIND_KEYGUARD_MEDIA_VISIBILITY,
                Flags.FLAG_BP_TALKBACK,
                Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE,
                Flags.FLAG_CENTRALIZED_STATUS_BAR_HEIGHT_FIX,
                Flags.FLAG_CLIPBOARD_NONINTERACTIVE_ON_LOCKSCREEN,
                Flags.FLAG_CLOCK_REACTIVE_VARIANTS,
                Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN,
                Flags.FLAG_COMMUNAL_HUB,
                Flags.FLAG_COMPOSE_BOUNCER,
                Flags.FLAG_COMPOSE_LOCKSCREEN,
                Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH,
                Flags.FLAG_CONSTRAINT_BP,
                Flags.FLAG_CONTEXTUAL_TIPS_ASSISTANT_DISMISS_FIX,
                Flags.FLAG_COROUTINE_TRACING,
                Flags.FLAG_CREATE_WINDOWLESS_WINDOW_MAGNIFIER,
                Flags.FLAG_DEDICATED_NOTIF_INFLATION_THREAD,
                Flags.FLAG_DELAY_SHOW_MAGNIFICATION_BUTTON,
                Flags.FLAG_DELAYED_WAKELOCK_RELEASE_ON_BACKGROUND_THREAD,
                Flags.FLAG_DEVICE_ENTRY_UDFPS_REFACTOR,
                Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_FREQUENCY_CHECK,
                Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_IOS_SWITCHER_CHECK,
                Flags.FLAG_DOZEUI_SCHEDULING_ALARMS_BACKGROUND_EXECUTION,
                Flags.FLAG_DREAM_INPUT_SESSION_PILFER_ONCE,
                Flags.FLAG_DREAM_OVERLAY_BOUNCER_SWIPE_DIRECTION_FILTERING,
                Flags.FLAG_DUAL_SHADE,
                Flags.FLAG_EDGE_BACK_GESTURE_HANDLER_THREAD,
                Flags.FLAG_EDGEBACK_GESTURE_HANDLER_GET_RUNNING_TASKS_BACKGROUND,
                Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
                Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_MUTE_VOLUME,
                Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_POWER_OFF,
                Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_TAKE_SCREENSHOT,
                Flags.FLAG_ENABLE_CONTEXTUAL_TIPS,
                Flags.FLAG_ENABLE_EFFICIENT_DISPLAY_REPOSITORY,
                Flags.FLAG_ENABLE_LAYOUT_TRACING,
                Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING,
                Flags.FLAG_ENABLE_WIDGET_PICKER_SIZE_FILTER,
                Flags.FLAG_ENFORCE_BRIGHTNESS_BASE_USER_RESTRICTION,
                Flags.FLAG_EXAMPLE_FLAG,
                Flags.FLAG_FAST_UNLOCK_TRANSITION,
                Flags.FLAG_FIX_IMAGE_WALLPAPER_CRASH_SURFACE_ALREADY_RELEASED,
                Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
                Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK,
                Flags.FLAG_FLOATING_MENU_DRAG_TO_EDIT,
                Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE,
                Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION,
                Flags.FLAG_FLOATING_MENU_NARROW_TARGET_CONTENT_OBSERVER,
                Flags.FLAG_FLOATING_MENU_OVERLAPS_NAV_BARS_FLAG,
                Flags.FLAG_FLOATING_MENU_RADII_ANIMATION,
                Flags.FLAG_GET_CONNECTED_DEVICE_NAME_UNSYNCHRONIZED,
                Flags.FLAG_GLANCEABLE_HUB_ALLOW_KEYGUARD_WHEN_DREAMING,
                Flags.FLAG_GLANCEABLE_HUB_FULLSCREEN_SWIPE,
                Flags.FLAG_GLANCEABLE_HUB_GESTURE_HANDLE,
                Flags.FLAG_GLANCEABLE_HUB_SHORTCUT_BUTTON,
                Flags.FLAG_HAPTIC_BRIGHTNESS_SLIDER,
                Flags.FLAG_HAPTIC_VOLUME_SLIDER,
                Flags.FLAG_HEARING_AIDS_QS_TILE_DIALOG,
                Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS,
                Flags.FLAG_KEYBOARD_DOCKING_INDICATOR,
                Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_REWRITE,
                Flags.FLAG_KEYGUARD_BOTTOM_AREA_REFACTOR,
                Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR,
                Flags.FLAG_LIGHT_REVEAL_MIGRATION,
                Flags.FLAG_MEDIA_CONTROLS_LOCKSCREEN_SHADE_BUG_FIX,
                Flags.FLAG_MEDIA_CONTROLS_REFACTOR,
                Flags.FLAG_MEDIA_CONTROLS_USER_INITIATED_DELETEINTENT,
                Flags.FLAG_MIGRATE_CLOCKS_TO_BLUEPRINT,
                Flags.FLAG_NEW_AOD_TRANSITION,
                Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL,
                Flags.FLAG_NEW_VOLUME_PANEL,
                Flags.FLAG_NOTIFICATION_ASYNC_GROUP_HEADER_INFLATION,
                Flags.FLAG_NOTIFICATION_ASYNC_HYBRID_VIEW_INFLATION,
                Flags.FLAG_NOTIFICATION_AVALANCHE_SUPPRESSION,
                Flags.FLAG_NOTIFICATION_AVALANCHE_THROTTLE_HUN,
                Flags.FLAG_NOTIFICATION_BACKGROUND_TINT_OPTIMIZATION,
                Flags.FLAG_NOTIFICATION_COLOR_UPDATE_LOGGER,
                Flags.FLAG_NOTIFICATION_CONTENT_ALPHA_OPTIMIZATION,
                Flags.FLAG_NOTIFICATION_FOOTER_BACKGROUND_TINT_OPTIMIZATION,
                Flags.FLAG_NOTIFICATION_MEDIA_MANAGER_BACKGROUND_EXECUTION,
                Flags.FLAG_NOTIFICATION_MINIMALISM_PROTOTYPE,
                Flags.FLAG_NOTIFICATION_OVER_EXPANSION_CLIPPING_FIX,
                Flags.FLAG_NOTIFICATION_PULSING_FIX,
                Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR,
                Flags.FLAG_NOTIFICATION_ROW_USER_CONTEXT,
                Flags.FLAG_NOTIFICATION_VIEW_FLIPPER_PAUSING_V2,
                Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS,
                Flags.FLAG_NOTIFICATIONS_FOOTER_VIEW_REFACTOR,
                Flags.FLAG_NOTIFICATIONS_HEADS_UP_REFACTOR,
                Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH,
                Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR,
                Flags.FLAG_NOTIFICATIONS_IMPROVED_HUN_ANIMATION,
                Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR,
                Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND,
                Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE,
                Flags.FLAG_PREDICTIVE_BACK_ANIMATE_BOUNCER,
                Flags.FLAG_PREDICTIVE_BACK_ANIMATE_DIALOGS,
                Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE,
                Flags.FLAG_PREDICTIVE_BACK_SYSUI,
                Flags.FLAG_PRIORITY_PEOPLE_SECTION,
                Flags.FLAG_PRIVACY_DOT_UNFOLD_WRONG_CORNER_FIX,
                Flags.FLAG_PSS_APP_SELECTOR_ABRUPT_EXIT_FIX,
                Flags.FLAG_PSS_APP_SELECTOR_RECENTS_SPLIT_SCREEN,
                Flags.FLAG_PSS_TASK_SWITCHER,
                Flags.FLAG_QS_CUSTOM_TILE_CLICK_GUARANTEED_BUG_FIX,
                Flags.FLAG_QS_NEW_PIPELINE,
                Flags.FLAG_QS_NEW_TILES,
                Flags.FLAG_QS_NEW_TILES_FUTURE,
                Flags.FLAG_QS_TILE_FOCUS_STATE,
                Flags.FLAG_QS_UI_REFACTOR,
                Flags.FLAG_QUICK_SETTINGS_VISUAL_HAPTICS_LONGPRESS,
                Flags.FLAG_RECORD_ISSUE_QS_TILE,
                Flags.FLAG_REFACTOR_GET_CURRENT_USER,
                Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE,
                Flags.FLAG_REGISTER_NEW_WALLET_CARD_IN_BACKGROUND,
                Flags.FLAG_REGISTER_WALLPAPER_NOTIFIER_BACKGROUND,
                Flags.FLAG_REGISTER_ZEN_MODE_CONTENT_OBSERVER_BACKGROUND,
                Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH,
                Flags.FLAG_REST_TO_UNLOCK,
                Flags.FLAG_RESTART_DREAM_ON_UNOCCLUDE,
                Flags.FLAG_REVAMPED_BOUNCER_MESSAGES,
                Flags.FLAG_RUN_FINGERPRINT_DETECT_ON_DISMISSIBLE_KEYGUARD,
                Flags.FLAG_SAVE_AND_RESTORE_MAGNIFICATION_SETTINGS_BUTTONS,
                Flags.FLAG_SCENE_CONTAINER,
                Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX,
                Flags.FLAG_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
                Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_ACCESSIBILITY_ANNOUNCEMENT_FIX,
                Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_BEHAVIOR_FIX,
                Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX,
                Flags.FLAG_SCREENSHOT_SHELF_UI2,
                Flags.FLAG_SHADE_COLLAPSE_ACTIVITY_LAUNCH_FIX,
                Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR,
                Flags.FLAG_SLICE_BROADCAST_RELAY_IN_BACKGROUND,
                Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND,
                Flags.FLAG_SMARTSPACE_LOCKSCREEN_VIEWMODEL,
                Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM,
                Flags.FLAG_SMARTSPACE_REMOTEVIEWS_RENDERING,
                Flags.FLAG_STATUS_BAR_MONOCHROME_ICONS_FIX,
                Flags.FLAG_STATUS_BAR_SCREEN_SHARING_CHIPS,
                Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS,
                Flags.FLAG_SWITCH_USER_ON_BG,
                Flags.FLAG_SYSUI_TEAMFOOD,
                Flags.FLAG_THEME_OVERLAY_CONTROLLER_WAKEFULNESS_DEPRECATION,
                Flags.FLAG_TRANSLUCENT_OCCLUDING_ACTIVITY_FIX,
                Flags.FLAG_TRUNCATED_STATUS_BAR_ICONS_FIX,
                Flags.FLAG_UDFPS_VIEW_PERFORMANCE,
                Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS,
                Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND,
                Flags.FLAG_VALIDATE_KEYBOARD_SHORTCUT_HELPER_ICON_URI,
                Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
            Arrays.asList(
                    ""
            )
    );
}
