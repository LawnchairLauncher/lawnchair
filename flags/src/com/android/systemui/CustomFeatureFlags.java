package com.android.systemui;


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

    public boolean alwaysComposeQsUiFragment() {
        return getValue(Flags.FLAG_ALWAYS_COMPOSE_QS_UI_FRAGMENT,
            FeatureFlags::alwaysComposeQsUiFragment);
    }

    @Override

    public boolean ambientTouchMonitorListenToDisplayChanges() {
        return getValue(Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES,
            FeatureFlags::ambientTouchMonitorListenToDisplayChanges);
    }

    @Override

    public boolean animationLibraryDelayLeashCleanup() {
        return getValue(Flags.FLAG_ANIMATION_LIBRARY_DELAY_LEASH_CLEANUP,
            FeatureFlags::animationLibraryDelayLeashCleanup);
    }

    @Override

    public boolean appClipsBacklinks() {
        return getValue(Flags.FLAG_APP_CLIPS_BACKLINKS,
            FeatureFlags::appClipsBacklinks);
    }

    @Override

    public boolean avalancheReplaceHunWhenCritical() {
        return getValue(Flags.FLAG_AVALANCHE_REPLACE_HUN_WHEN_CRITICAL,
            FeatureFlags::avalancheReplaceHunWhenCritical);
    }

    @Override

    public boolean backButtonOnBouncer() {
        return getValue(Flags.FLAG_BACK_BUTTON_ON_BOUNCER,
            FeatureFlags::backButtonOnBouncer);
    }

    @Override

    public boolean bouncerLifecycleFix() {
        return getValue(Flags.FLAG_BOUNCER_LIFECYCLE_FIX,
            FeatureFlags::bouncerLifecycleFix);
    }

    @Override

    public boolean bouncerUiRevamp() {
        return getValue(Flags.FLAG_BOUNCER_UI_REVAMP,
            FeatureFlags::bouncerUiRevamp);
    }

    @Override

    public boolean bouncerUiRevamp2() {
        return getValue(Flags.FLAG_BOUNCER_UI_REVAMP_2,
            FeatureFlags::bouncerUiRevamp2);
    }

    @Override

    public boolean bpColors() {
        return getValue(Flags.FLAG_BP_COLORS,
            FeatureFlags::bpColors);
    }

    @Override

    public boolean brightnessSliderFocusState() {
        return getValue(Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE,
            FeatureFlags::brightnessSliderFocusState);
    }

    @Override

    public boolean classicFlagsMultiUser() {
        return getValue(Flags.FLAG_CLASSIC_FLAGS_MULTI_USER,
            FeatureFlags::classicFlagsMultiUser);
    }

    @Override

    public boolean clipboardAnnounceLiveRegion() {
        return getValue(Flags.FLAG_CLIPBOARD_ANNOUNCE_LIVE_REGION,
            FeatureFlags::clipboardAnnounceLiveRegion);
    }

    @Override

    public boolean clipboardOverlayMultiuser() {
        return getValue(Flags.FLAG_CLIPBOARD_OVERLAY_MULTIUSER,
            FeatureFlags::clipboardOverlayMultiuser);
    }

    @Override

    public boolean clipboardUseDescriptionMimetype() {
        return getValue(Flags.FLAG_CLIPBOARD_USE_DESCRIPTION_MIMETYPE,
            FeatureFlags::clipboardUseDescriptionMimetype);
    }

    @Override

    public boolean clockFidgetAnimation() {
        return getValue(Flags.FLAG_CLOCK_FIDGET_ANIMATION,
            FeatureFlags::clockFidgetAnimation);
    }

    @Override

    public boolean clockModernization() {
        return getValue(Flags.FLAG_CLOCK_MODERNIZATION,
            FeatureFlags::clockModernization);
    }

    @Override

    public boolean communalBouncerDoNotModifyPluginOpen() {
        return getValue(Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN,
            FeatureFlags::communalBouncerDoNotModifyPluginOpen);
    }

    @Override

    public boolean communalEditWidgetsActivityFinishFix() {
        return getValue(Flags.FLAG_COMMUNAL_EDIT_WIDGETS_ACTIVITY_FINISH_FIX,
            FeatureFlags::communalEditWidgetsActivityFinishFix);
    }

    @Override

    public boolean communalHub() {
        return getValue(Flags.FLAG_COMMUNAL_HUB,
            FeatureFlags::communalHub);
    }

    @Override

    public boolean communalHubUseThreadPoolForWidgets() {
        return getValue(Flags.FLAG_COMMUNAL_HUB_USE_THREAD_POOL_FOR_WIDGETS,
            FeatureFlags::communalHubUseThreadPoolForWidgets);
    }

    @Override

    public boolean communalResponsiveGrid() {
        return getValue(Flags.FLAG_COMMUNAL_RESPONSIVE_GRID,
            FeatureFlags::communalResponsiveGrid);
    }

    @Override

    public boolean communalShadeTouchHandlingFixes() {
        return getValue(Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES,
            FeatureFlags::communalShadeTouchHandlingFixes);
    }

    @Override

    public boolean communalStandaloneSupport() {
        return getValue(Flags.FLAG_COMMUNAL_STANDALONE_SUPPORT,
            FeatureFlags::communalStandaloneSupport);
    }

    @Override

    public boolean communalTimerFlickerFix() {
        return getValue(Flags.FLAG_COMMUNAL_TIMER_FLICKER_FIX,
            FeatureFlags::communalTimerFlickerFix);
    }

    @Override

    public boolean communalWidgetResizing() {
        return getValue(Flags.FLAG_COMMUNAL_WIDGET_RESIZING,
            FeatureFlags::communalWidgetResizing);
    }

    @Override

    public boolean communalWidgetTrampolineFix() {
        return getValue(Flags.FLAG_COMMUNAL_WIDGET_TRAMPOLINE_FIX,
            FeatureFlags::communalWidgetTrampolineFix);
    }

    @Override

    public boolean composeBouncer() {
        return getValue(Flags.FLAG_COMPOSE_BOUNCER,
            FeatureFlags::composeBouncer);
    }

    @Override

    public boolean confineNotificationTouchToViewWidth() {
        return getValue(Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH,
            FeatureFlags::confineNotificationTouchToViewWidth);
    }

    @Override

    public boolean contAuthPlugin() {
        return getValue(Flags.FLAG_CONT_AUTH_PLUGIN,
            FeatureFlags::contAuthPlugin);
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

    public boolean decoupleViewControllerInAnimlib() {
        return getValue(Flags.FLAG_DECOUPLE_VIEW_CONTROLLER_IN_ANIMLIB,
            FeatureFlags::decoupleViewControllerInAnimlib);
    }

    @Override

    public boolean desktopEffectsQsTile() {
        return getValue(Flags.FLAG_DESKTOP_EFFECTS_QS_TILE,
            FeatureFlags::desktopEffectsQsTile);
    }

    @Override

    public boolean desktopScreenCapture() {
        return getValue(Flags.FLAG_DESKTOP_SCREEN_CAPTURE,
            FeatureFlags::desktopScreenCapture);
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

    public boolean disableDoubleClickSwapOnBouncer() {
        return getValue(Flags.FLAG_DISABLE_DOUBLE_CLICK_SWAP_ON_BOUNCER,
            FeatureFlags::disableDoubleClickSwapOnBouncer);
    }

    @Override

    public boolean doNotUseImmediateCoroutineDispatcher() {
        return getValue(Flags.FLAG_DO_NOT_USE_IMMEDIATE_COROUTINE_DISPATCHER,
            FeatureFlags::doNotUseImmediateCoroutineDispatcher);
    }

    @Override

    public boolean doubleTapToSleep() {
        return getValue(Flags.FLAG_DOUBLE_TAP_TO_SLEEP,
            FeatureFlags::doubleTapToSleep);
    }

    @Override

    public boolean dreamBiometricPromptFixes() {
        return getValue(Flags.FLAG_DREAM_BIOMETRIC_PROMPT_FIXES,
            FeatureFlags::dreamBiometricPromptFixes);
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

    public boolean dreamOverlayUpdatedUi() {
        return getValue(Flags.FLAG_DREAM_OVERLAY_UPDATED_UI,
            FeatureFlags::dreamOverlayUpdatedUi);
    }

    @Override

    public boolean dreamPreviewTapDismiss() {
        return getValue(Flags.FLAG_DREAM_PREVIEW_TAP_DISMISS,
            FeatureFlags::dreamPreviewTapDismiss);
    }

    @Override

    public boolean dreamTransitionFixes() {
        return getValue(Flags.FLAG_DREAM_TRANSITION_FIXES,
            FeatureFlags::dreamTransitionFixes);
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

    public boolean enableConstraintLayoutLockscreenOnExternalDisplay() {
        return getValue(Flags.FLAG_ENABLE_CONSTRAINT_LAYOUT_LOCKSCREEN_ON_EXTERNAL_DISPLAY,
            FeatureFlags::enableConstraintLayoutLockscreenOnExternalDisplay);
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

    public boolean enableDesktopGrowth() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_GROWTH,
            FeatureFlags::enableDesktopGrowth);
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

    public boolean enableMinmode() {
        return getValue(Flags.FLAG_ENABLE_MINMODE,
            FeatureFlags::enableMinmode);
    }

    @Override

    public boolean enableSuggestedDeviceUi() {
        return getValue(Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI,
            FeatureFlags::enableSuggestedDeviceUi);
    }

    @Override

    public boolean enableTopUiController() {
        return getValue(Flags.FLAG_ENABLE_TOP_UI_CONTROLLER,
            FeatureFlags::enableTopUiController);
    }

    @Override

    public boolean enableUnderlay() {
        return getValue(Flags.FLAG_ENABLE_UNDERLAY,
            FeatureFlags::enableUnderlay);
    }

    @Override

    public boolean enableViewCaptureTracing() {
        return getValue(Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING,
            FeatureFlags::enableViewCaptureTracing);
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

    public boolean expandCollapsePrivacyDialog() {
        return getValue(Flags.FLAG_EXPAND_COLLAPSE_PRIVACY_DIALOG,
            FeatureFlags::expandCollapsePrivacyDialog);
    }

    @Override

    public boolean expandHeadsUpOnInlineReply() {
        return getValue(Flags.FLAG_EXPAND_HEADS_UP_ON_INLINE_REPLY,
            FeatureFlags::expandHeadsUpOnInlineReply);
    }

    @Override

    public boolean expandedPrivacyIndicatorsOnLargeScreen() {
        return getValue(Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN,
            FeatureFlags::expandedPrivacyIndicatorsOnLargeScreen);
    }

    @Override

    public boolean extendedAppsShortcutCategory() {
        return getValue(Flags.FLAG_EXTENDED_APPS_SHORTCUT_CATEGORY,
            FeatureFlags::extendedAppsShortcutCategory);
    }

    @Override

    public boolean faceScanningAnimationNpeFix() {
        return getValue(Flags.FLAG_FACE_SCANNING_ANIMATION_NPE_FIX,
            FeatureFlags::faceScanningAnimationNpeFix);
    }

    @Override

    public boolean fetchBookmarksXmlKeyboardShortcuts() {
        return getValue(Flags.FLAG_FETCH_BOOKMARKS_XML_KEYBOARD_SHORTCUTS,
            FeatureFlags::fetchBookmarksXmlKeyboardShortcuts);
    }

    @Override

    public boolean fixDialogLaunchAnimationJankLogging() {
        return getValue(Flags.FLAG_FIX_DIALOG_LAUNCH_ANIMATION_JANK_LOGGING,
            FeatureFlags::fixDialogLaunchAnimationJankLogging);
    }

    @Override

    public boolean fixScreenshotActionDismissSystemWindows() {
        return getValue(Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
            FeatureFlags::fixScreenshotActionDismissSystemWindows);
    }

    @Override

    public boolean flashlightStrength() {
        return getValue(Flags.FLAG_FLASHLIGHT_STRENGTH,
            FeatureFlags::flashlightStrength);
    }

    @Override

    public boolean floatingMenuAnimatedTuck() {
        return getValue(Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK,
            FeatureFlags::floatingMenuAnimatedTuck);
    }

    @Override

    public boolean floatingMenuDragToHide() {
        return getValue(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE,
            FeatureFlags::floatingMenuDragToHide);
    }

    @Override

    public boolean floatingMenuHearingDeviceStatusIcon() {
        return getValue(Flags.FLAG_FLOATING_MENU_HEARING_DEVICE_STATUS_ICON,
            FeatureFlags::floatingMenuHearingDeviceStatusIcon);
    }

    @Override

    public boolean floatingMenuImeDisplacementAnimation() {
        return getValue(Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION,
            FeatureFlags::floatingMenuImeDisplacementAnimation);
    }

    @Override

    public boolean floatingMenuNotifyTargetsChangedOnStrictDiff() {
        return getValue(Flags.FLAG_FLOATING_MENU_NOTIFY_TARGETS_CHANGED_ON_STRICT_DIFF,
            FeatureFlags::floatingMenuNotifyTargetsChangedOnStrictDiff);
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

    public boolean floatingMenuRemoveFullscreenTaps() {
        return getValue(Flags.FLAG_FLOATING_MENU_REMOVE_FULLSCREEN_TAPS,
            FeatureFlags::floatingMenuRemoveFullscreenTaps);
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

    public boolean glanceableHubBlurredBackground() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND,
            FeatureFlags::glanceableHubBlurredBackground);
    }

    @Override

    public boolean glanceableHubDirectEditMode() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE,
            FeatureFlags::glanceableHubDirectEditMode);
    }

    @Override

    public boolean glanceableHubV2() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_V2,
            FeatureFlags::glanceableHubV2);
    }

    @Override

    public boolean glanceableHubV2Resources() {
        return getValue(Flags.FLAG_GLANCEABLE_HUB_V2_RESOURCES,
            FeatureFlags::glanceableHubV2Resources);
    }

    @Override

    public boolean hardwareColorStyles() {
        return getValue(Flags.FLAG_HARDWARE_COLOR_STYLES,
            FeatureFlags::hardwareColorStyles);
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

    public boolean hideRingerButtonInSingleVolumeMode() {
        return getValue(Flags.FLAG_HIDE_RINGER_BUTTON_IN_SINGLE_VOLUME_MODE,
            FeatureFlags::hideRingerButtonInSingleVolumeMode);
    }

    @Override

    public boolean homeControlsDreamHsum() {
        return getValue(Flags.FLAG_HOME_CONTROLS_DREAM_HSUM,
            FeatureFlags::homeControlsDreamHsum);
    }

    @Override

    public boolean hsuBehaviorChanges() {
        return getValue(Flags.FLAG_HSU_BEHAVIOR_CHANGES,
            FeatureFlags::hsuBehaviorChanges);
    }

    @Override

    public boolean hubBlurredByShadeFix() {
        return getValue(Flags.FLAG_HUB_BLURRED_BY_SHADE_FIX,
            FeatureFlags::hubBlurredByShadeFix);
    }

    @Override

    public boolean hubEditModeTouchAdjustments() {
        return getValue(Flags.FLAG_HUB_EDIT_MODE_TOUCH_ADJUSTMENTS,
            FeatureFlags::hubEditModeTouchAdjustments);
    }

    @Override

    public boolean hubEditModeTransition() {
        return getValue(Flags.FLAG_HUB_EDIT_MODE_TRANSITION,
            FeatureFlags::hubEditModeTransition);
    }

    @Override

    public boolean iconRefresh2025() {
        return getValue(Flags.FLAG_ICON_REFRESH_2025,
            FeatureFlags::iconRefresh2025);
    }

    @Override

    public boolean indicationTextA11yFix() {
        return getValue(Flags.FLAG_INDICATION_TEXT_A11Y_FIX,
            FeatureFlags::indicationTextA11yFix);
    }

    @Override

    public boolean instantHideShade() {
        return getValue(Flags.FLAG_INSTANT_HIDE_SHADE,
            FeatureFlags::instantHideShade);
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

    public boolean keyboardShortcutHelperShortcutCustomizer() {
        return getValue(Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_SHORTCUT_CUSTOMIZER,
            FeatureFlags::keyboardShortcutHelperShortcutCustomizer);
    }

    @Override

    public boolean keyboardTouchpadContextualEducation() {
        return getValue(Flags.FLAG_KEYBOARD_TOUCHPAD_CONTEXTUAL_EDUCATION,
            FeatureFlags::keyboardTouchpadContextualEducation);
    }

    @Override

    public boolean keyguardTransitionForceFinishOnScreenOff() {
        return getValue(Flags.FLAG_KEYGUARD_TRANSITION_FORCE_FINISH_ON_SCREEN_OFF,
            FeatureFlags::keyguardTransitionForceFinishOnScreenOff);
    }

    @Override

    public boolean keyguardWmStateRefactor() {
        return getValue(Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR,
            FeatureFlags::keyguardWmStateRefactor);
    }

    @Override

    public boolean lockscreenFont() {
        return getValue(Flags.FLAG_LOCKSCREEN_FONT,
            FeatureFlags::lockscreenFont);
    }

    @Override

    public boolean lowLightClockDream() {
        return getValue(Flags.FLAG_LOW_LIGHT_CLOCK_DREAM,
            FeatureFlags::lowLightClockDream);
    }

    @Override

    public boolean lowlightClockSetBrightness() {
        return getValue(Flags.FLAG_LOWLIGHT_CLOCK_SET_BRIGHTNESS,
            FeatureFlags::lowlightClockSetBrightness);
    }

    @Override

    public boolean lowlightClockUsesKeyguardChargingStatus() {
        return getValue(Flags.FLAG_LOWLIGHT_CLOCK_USES_KEYGUARD_CHARGING_STATUS,
            FeatureFlags::lowlightClockUsesKeyguardChargingStatus);
    }

    @Override

    public boolean magneticNotificationSwipes() {
        return getValue(Flags.FLAG_MAGNETIC_NOTIFICATION_SWIPES,
            FeatureFlags::magneticNotificationSwipes);
    }

    @Override

    public boolean mediaControlsButtonMedia3() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3,
            FeatureFlags::mediaControlsButtonMedia3);
    }

    @Override

    public boolean mediaControlsButtonMedia3Placement() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3_PLACEMENT,
            FeatureFlags::mediaControlsButtonMedia3Placement);
    }

    @Override

    public boolean mediaControlsInCompose() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE,
            FeatureFlags::mediaControlsInCompose);
    }

    @Override

    public boolean mediaControlsUiUpdate() {
        return getValue(Flags.FLAG_MEDIA_CONTROLS_UI_UPDATE,
            FeatureFlags::mediaControlsUiUpdate);
    }

    @Override

    public boolean mediaProjectionDialogBehindLockscreen() {
        return getValue(Flags.FLAG_MEDIA_PROJECTION_DIALOG_BEHIND_LOCKSCREEN,
            FeatureFlags::mediaProjectionDialogBehindLockscreen);
    }

    @Override

    public boolean mediaProjectionGreyErrorText() {
        return getValue(Flags.FLAG_MEDIA_PROJECTION_GREY_ERROR_TEXT,
            FeatureFlags::mediaProjectionGreyErrorText);
    }

    @Override

    public boolean mediaProjectionRequestAttributionFix() {
        return getValue(Flags.FLAG_MEDIA_PROJECTION_REQUEST_ATTRIBUTION_FIX,
            FeatureFlags::mediaProjectionRequestAttributionFix);
    }

    @Override

    public boolean modesUiDialogPaging() {
        return getValue(Flags.FLAG_MODES_UI_DIALOG_PAGING,
            FeatureFlags::modesUiDialogPaging);
    }

    @Override

    public boolean moveTransitionAnimationLayer() {
        return getValue(Flags.FLAG_MOVE_TRANSITION_ANIMATION_LAYER,
            FeatureFlags::moveTransitionAnimationLayer);
    }

    @Override

    public boolean msdlFeedback() {
        return getValue(Flags.FLAG_MSDL_FEEDBACK,
            FeatureFlags::msdlFeedback);
    }

    @Override

    public boolean multiuserWifiPickerTrackerSupport() {
        return getValue(Flags.FLAG_MULTIUSER_WIFI_PICKER_TRACKER_SUPPORT,
            FeatureFlags::multiuserWifiPickerTrackerSupport);
    }

    @Override

    public boolean newAodTransition() {
        return getValue(Flags.FLAG_NEW_AOD_TRANSITION,
            FeatureFlags::newAodTransition);
    }

    @Override

    public boolean newDozingKeyguardStates() {
        return getValue(Flags.FLAG_NEW_DOZING_KEYGUARD_STATES,
            FeatureFlags::newDozingKeyguardStates);
    }

    @Override

    public boolean newVolumePanel() {
        return getValue(Flags.FLAG_NEW_VOLUME_PANEL,
            FeatureFlags::newVolumePanel);
    }

    @Override

    public boolean nonTouchscreenDevicesBypassFalsing() {
        return getValue(Flags.FLAG_NON_TOUCHSCREEN_DEVICES_BYPASS_FALSING,
            FeatureFlags::nonTouchscreenDevicesBypassFalsing);
    }

    @Override

    public boolean notesRoleQsTile() {
        return getValue(Flags.FLAG_NOTES_ROLE_QS_TILE,
            FeatureFlags::notesRoleQsTile);
    }

    @Override

    public boolean notificationAddXOnHoverToDismiss() {
        return getValue(Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS,
            FeatureFlags::notificationAddXOnHoverToDismiss);
    }

    @Override

    public boolean notificationAmbientSuppressionAfterInflation() {
        return getValue(Flags.FLAG_NOTIFICATION_AMBIENT_SUPPRESSION_AFTER_INFLATION,
            FeatureFlags::notificationAmbientSuppressionAfterInflation);
    }

    @Override

    public boolean notificationAnimatedActionsTreatment() {
        return getValue(Flags.FLAG_NOTIFICATION_ANIMATED_ACTIONS_TREATMENT,
            FeatureFlags::notificationAnimatedActionsTreatment);
    }

    @Override

    public boolean notificationAppearNonlinear() {
        return getValue(Flags.FLAG_NOTIFICATION_APPEAR_NONLINEAR,
            FeatureFlags::notificationAppearNonlinear);
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

    public boolean notificationBundleUi() {
        return getValue(Flags.FLAG_NOTIFICATION_BUNDLE_UI,
            FeatureFlags::notificationBundleUi);
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

    public boolean notificationRowAccessibilityExpanded() {
        return getValue(Flags.FLAG_NOTIFICATION_ROW_ACCESSIBILITY_EXPANDED,
            FeatureFlags::notificationRowAccessibilityExpanded);
    }

    @Override

    public boolean notificationRowContentBinderRefactor() {
        return getValue(Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR,
            FeatureFlags::notificationRowContentBinderRefactor);
    }

    @Override

    public boolean notificationRowTransparency() {
        return getValue(Flags.FLAG_NOTIFICATION_ROW_TRANSPARENCY,
            FeatureFlags::notificationRowTransparency);
    }

    @Override

    public boolean notificationShadeBlur() {
        return getValue(Flags.FLAG_NOTIFICATION_SHADE_BLUR,
            FeatureFlags::notificationShadeBlur);
    }

    @Override

    public boolean notificationShadeCloseWaitsForChildAnimations() {
        return getValue(Flags.FLAG_NOTIFICATION_SHADE_CLOSE_WAITS_FOR_CHILD_ANIMATIONS,
            FeatureFlags::notificationShadeCloseWaitsForChildAnimations);
    }

    @Override

    public boolean notificationShadeUiThread() {
        return getValue(Flags.FLAG_NOTIFICATION_SHADE_UI_THREAD,
            FeatureFlags::notificationShadeUiThread);
    }

    @Override

    public boolean notificationSkipSilentUpdates() {
        return getValue(Flags.FLAG_NOTIFICATION_SKIP_SILENT_UPDATES,
            FeatureFlags::notificationSkipSilentUpdates);
    }

    @Override

    public boolean notificationTransparentHeaderFix() {
        return getValue(Flags.FLAG_NOTIFICATION_TRANSPARENT_HEADER_FIX,
            FeatureFlags::notificationTransparentHeaderFix);
    }

    @Override

    public boolean notificationsBackgroundIcons() {
        return getValue(Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS,
            FeatureFlags::notificationsBackgroundIcons);
    }

    @Override

    public boolean notificationsFooterVisibilityFix() {
        return getValue(Flags.FLAG_NOTIFICATIONS_FOOTER_VISIBILITY_FIX,
            FeatureFlags::notificationsFooterVisibilityFix);
    }

    @Override

    public boolean notificationsHideOnDisplaySwitch() {
        return getValue(Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH,
            FeatureFlags::notificationsHideOnDisplaySwitch);
    }

    @Override

    public boolean notificationsHunAccessibilityRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_HUN_ACCESSIBILITY_REFACTOR,
            FeatureFlags::notificationsHunAccessibilityRefactor);
    }

    @Override

    public boolean notificationsHunSharedAnimationValues() {
        return getValue(Flags.FLAG_NOTIFICATIONS_HUN_SHARED_ANIMATION_VALUES,
            FeatureFlags::notificationsHunSharedAnimationValues);
    }

    @Override

    public boolean notificationsIconContainerRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR,
            FeatureFlags::notificationsIconContainerRefactor);
    }

    @Override

    public boolean notificationsLaunchRadius() {
        return getValue(Flags.FLAG_NOTIFICATIONS_LAUNCH_RADIUS,
            FeatureFlags::notificationsLaunchRadius);
    }

    @Override

    public boolean notificationsLiveDataStoreRefactor() {
        return getValue(Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR,
            FeatureFlags::notificationsLiveDataStoreRefactor);
    }

    @Override

    public boolean notificationsPinnedHunInShade() {
        return getValue(Flags.FLAG_NOTIFICATIONS_PINNED_HUN_IN_SHADE,
            FeatureFlags::notificationsPinnedHunInShade);
    }

    @Override

    public boolean notificationsRedesignFooterView() {
        return getValue(Flags.FLAG_NOTIFICATIONS_REDESIGN_FOOTER_VIEW,
            FeatureFlags::notificationsRedesignFooterView);
    }

    @Override

    public boolean notifyPasswordTextViewUserActivityInBackground() {
        return getValue(Flags.FLAG_NOTIFY_PASSWORD_TEXT_VIEW_USER_ACTIVITY_IN_BACKGROUND,
            FeatureFlags::notifyPasswordTextViewUserActivityInBackground);
    }

    @Override

    public boolean notifyPowerManagerUserActivityBackground() {
        return getValue(Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND,
            FeatureFlags::notifyPowerManagerUserActivityBackground);
    }

    @Override

    public boolean ongoingActivityChipsOnDream() {
        return getValue(Flags.FLAG_ONGOING_ACTIVITY_CHIPS_ON_DREAM,
            FeatureFlags::ongoingActivityChipsOnDream);
    }

    @Override

    public boolean overrideSuppressOverlayCondition() {
        return getValue(Flags.FLAG_OVERRIDE_SUPPRESS_OVERLAY_CONDITION,
            FeatureFlags::overrideSuppressOverlayCondition);
    }

    @Override

    public boolean permissionHelperInlineUiRichOngoing() {
        return getValue(Flags.FLAG_PERMISSION_HELPER_INLINE_UI_RICH_ONGOING,
            FeatureFlags::permissionHelperInlineUiRichOngoing);
    }

    @Override

    public boolean permissionHelperUiRichOngoing() {
        return getValue(Flags.FLAG_PERMISSION_HELPER_UI_RICH_ONGOING,
            FeatureFlags::permissionHelperUiRichOngoing);
    }

    @Override

    public boolean physicalNotificationMovement() {
        return getValue(Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            FeatureFlags::physicalNotificationMovement);
    }

    @Override

    public boolean pinInputFieldStyledFocusState() {
        return getValue(Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE,
            FeatureFlags::pinInputFieldStyledFocusState);
    }

    @Override

    public boolean predictiveBackAnimateShade() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE,
            FeatureFlags::predictiveBackAnimateShade);
    }

    @Override

    public boolean predictiveBackDelayWmTransition() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            FeatureFlags::predictiveBackDelayWmTransition);
    }

    @Override

    public boolean privacyDotLiveRegion() {
        return getValue(Flags.FLAG_PRIVACY_DOT_LIVE_REGION,
            FeatureFlags::privacyDotLiveRegion);
    }

    @Override

    public boolean promoteNotificationsAutomatically() {
        return getValue(Flags.FLAG_PROMOTE_NOTIFICATIONS_AUTOMATICALLY,
            FeatureFlags::promoteNotificationsAutomatically);
    }

    @Override

    public boolean pssTaskSwitcher() {
        return getValue(Flags.FLAG_PSS_TASK_SWITCHER,
            FeatureFlags::pssTaskSwitcher);
    }

    @Override

    public boolean qsComposeFragmentEarlyExpansion() {
        return getValue(Flags.FLAG_QS_COMPOSE_FRAGMENT_EARLY_EXPANSION,
            FeatureFlags::qsComposeFragmentEarlyExpansion);
    }

    @Override

    public boolean qsEditModeTabs() {
        return getValue(Flags.FLAG_QS_EDIT_MODE_TABS,
            FeatureFlags::qsEditModeTabs);
    }

    @Override

    public boolean qsEditModeTooltip() {
        return getValue(Flags.FLAG_QS_EDIT_MODE_TOOLTIP,
            FeatureFlags::qsEditModeTooltip);
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

    public boolean qsTileDetailedView() {
        return getValue(Flags.FLAG_QS_TILE_DETAILED_VIEW,
            FeatureFlags::qsTileDetailedView);
    }

    @Override

    public boolean qsTileFocusState() {
        return getValue(Flags.FLAG_QS_TILE_FOCUS_STATE,
            FeatureFlags::qsTileFocusState);
    }

    @Override

    public boolean qsTileTransitionInteractionRefinement() {
        return getValue(Flags.FLAG_QS_TILE_TRANSITION_INTERACTION_REFINEMENT,
            FeatureFlags::qsTileTransitionInteractionRefinement);
    }

    @Override

    public boolean qsUiRefactor() {
        return getValue(Flags.FLAG_QS_UI_REFACTOR,
            FeatureFlags::qsUiRefactor);
    }

    @Override

    public boolean qsUiRefactorComposeFragment() {
        return getValue(Flags.FLAG_QS_UI_REFACTOR_COMPOSE_FRAGMENT,
            FeatureFlags::qsUiRefactorComposeFragment);
    }

    @Override

    public boolean qsWifiConfig() {
        return getValue(Flags.FLAG_QS_WIFI_CONFIG,
            FeatureFlags::qsWifiConfig);
    }

    @Override

    public boolean recordIssueQsTile() {
        return getValue(Flags.FLAG_RECORD_ISSUE_QS_TILE,
            FeatureFlags::recordIssueQsTile);
    }

    @Override

    public boolean redesignMagnificationWindowSize() {
        return getValue(Flags.FLAG_REDESIGN_MAGNIFICATION_WINDOW_SIZE,
            FeatureFlags::redesignMagnificationWindowSize);
    }

    @Override

    public boolean registerBatteryControllerReceiversInCorestartable() {
        return getValue(Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE,
            FeatureFlags::registerBatteryControllerReceiversInCorestartable);
    }

    @Override

    public boolean registerContentObserversAsync() {
        return getValue(Flags.FLAG_REGISTER_CONTENT_OBSERVERS_ASYNC,
            FeatureFlags::registerContentObserversAsync);
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

    public boolean rememberViewModelOffMainThread() {
        return getValue(Flags.FLAG_REMEMBER_VIEW_MODEL_OFF_MAIN_THREAD,
            FeatureFlags::rememberViewModelOffMainThread);
    }

    @Override

    public boolean removeAodCarMode() {
        return getValue(Flags.FLAG_REMOVE_AOD_CAR_MODE,
            FeatureFlags::removeAodCarMode);
    }

    @Override

    public boolean removeDreamOverlayHideOnTouch() {
        return getValue(Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH,
            FeatureFlags::removeDreamOverlayHideOnTouch);
    }

    @Override

    public boolean removeNearbyShareTileAnimation() {
        return getValue(Flags.FLAG_REMOVE_NEARBY_SHARE_TILE_ANIMATION,
            FeatureFlags::removeNearbyShareTileAnimation);
    }

    @Override

    public boolean removeUpdateListenerInQsIconViewImpl() {
        return getValue(Flags.FLAG_REMOVE_UPDATE_LISTENER_IN_QS_ICON_VIEW_IMPL,
            FeatureFlags::removeUpdateListenerInQsIconViewImpl);
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

    public boolean restrictCommunalAppWidgetHostListening() {
        return getValue(Flags.FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING,
            FeatureFlags::restrictCommunalAppWidgetHostListening);
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

    public boolean screenReactions() {
        return getValue(Flags.FLAG_SCREEN_REACTIONS,
            FeatureFlags::screenReactions);
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

    public boolean screenshotAnnounceLiveRegion() {
        return getValue(Flags.FLAG_SCREENSHOT_ANNOUNCE_LIVE_REGION,
            FeatureFlags::screenshotAnnounceLiveRegion);
    }

    @Override

    public boolean screenshotMultidisplayFocusChange() {
        return getValue(Flags.FLAG_SCREENSHOT_MULTIDISPLAY_FOCUS_CHANGE,
            FeatureFlags::screenshotMultidisplayFocusChange);
    }

    @Override

    public boolean screenshotPolicySplitAndDesktopMode() {
        return getValue(Flags.FLAG_SCREENSHOT_POLICY_SPLIT_AND_DESKTOP_MODE,
            FeatureFlags::screenshotPolicySplitAndDesktopMode);
    }

    @Override

    public boolean screenshotScrollCropViewCrashFix() {
        return getValue(Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX,
            FeatureFlags::screenshotScrollCropViewCrashFix);
    }

    @Override

    public boolean secondaryUserWidgetHost() {
        return getValue(Flags.FLAG_SECONDARY_USER_WIDGET_HOST,
            FeatureFlags::secondaryUserWidgetHost);
    }

    @Override

    public boolean settingsExtRegisterContentObserverOnBgThread() {
        return getValue(Flags.FLAG_SETTINGS_EXT_REGISTER_CONTENT_OBSERVER_ON_BG_THREAD,
            FeatureFlags::settingsExtRegisterContentObserverOnBgThread);
    }

    @Override

    public boolean shadeExpandsOnStatusBarLongPress() {
        return getValue(Flags.FLAG_SHADE_EXPANDS_ON_STATUS_BAR_LONG_PRESS,
            FeatureFlags::shadeExpandsOnStatusBarLongPress);
    }

    @Override

    public boolean shadeHeaderBlurFontColor() {
        return getValue(Flags.FLAG_SHADE_HEADER_BLUR_FONT_COLOR,
            FeatureFlags::shadeHeaderBlurFontColor);
    }

    @Override

    public boolean shadeHeaderFontUpdate() {
        return getValue(Flags.FLAG_SHADE_HEADER_FONT_UPDATE,
            FeatureFlags::shadeHeaderFontUpdate);
    }

    @Override

    public boolean shadeQsvisibleLogic() {
        return getValue(Flags.FLAG_SHADE_QSVISIBLE_LOGIC,
            FeatureFlags::shadeQsvisibleLogic);
    }

    @Override

    public boolean shadeWindowGoesAround() {
        return getValue(Flags.FLAG_SHADE_WINDOW_GOES_AROUND,
            FeatureFlags::shadeWindowGoesAround);
    }

    @Override

    public boolean shaderlibLoadingEffectRefactor() {
        return getValue(Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR,
            FeatureFlags::shaderlibLoadingEffectRefactor);
    }

    @Override

    public boolean shortcutHelperKeyGlyph() {
        return getValue(Flags.FLAG_SHORTCUT_HELPER_KEY_GLYPH,
            FeatureFlags::shortcutHelperKeyGlyph);
    }

    @Override

    public boolean showAudioSharingSliderInVolumePanel() {
        return getValue(Flags.FLAG_SHOW_AUDIO_SHARING_SLIDER_IN_VOLUME_PANEL,
            FeatureFlags::showAudioSharingSliderInVolumePanel);
    }

    @Override

    public boolean showClipboardIndication() {
        return getValue(Flags.FLAG_SHOW_CLIPBOARD_INDICATION,
            FeatureFlags::showClipboardIndication);
    }

    @Override

    public boolean showLockedByYourWatchKeyguardIndicator() {
        return getValue(Flags.FLAG_SHOW_LOCKED_BY_YOUR_WATCH_KEYGUARD_INDICATOR,
            FeatureFlags::showLockedByYourWatchKeyguardIndicator);
    }

    @Override

    public boolean simPinBouncerReset() {
        return getValue(Flags.FLAG_SIM_PIN_BOUNCER_RESET,
            FeatureFlags::simPinBouncerReset);
    }

    @Override

    public boolean skipHideSensitiveNotifAnimation() {
        return getValue(Flags.FLAG_SKIP_HIDE_SENSITIVE_NOTIF_ANIMATION,
            FeatureFlags::skipHideSensitiveNotifAnimation);
    }

    @Override

    public boolean sliceManagerBinderCallBackground() {
        return getValue(Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND,
            FeatureFlags::sliceManagerBinderCallBackground);
    }

    @Override

    public boolean smartspaceRelocateToBottom() {
        return getValue(Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM,
            FeatureFlags::smartspaceRelocateToBottom);
    }

    @Override

    public boolean smartspaceSwipeEventLoggingFix() {
        return getValue(Flags.FLAG_SMARTSPACE_SWIPE_EVENT_LOGGING_FIX,
            FeatureFlags::smartspaceSwipeEventLoggingFix);
    }

    @Override

    public boolean smartspaceViewpager2() {
        return getValue(Flags.FLAG_SMARTSPACE_VIEWPAGER2,
            FeatureFlags::smartspaceViewpager2);
    }

    @Override

    public boolean sounddoseCustomization() {
        return getValue(Flags.FLAG_SOUNDDOSE_CUSTOMIZATION,
            FeatureFlags::sounddoseCustomization);
    }

    @Override

    public boolean spatialModelAppPushback() {
        return getValue(Flags.FLAG_SPATIAL_MODEL_APP_PUSHBACK,
            FeatureFlags::spatialModelAppPushback);
    }

    @Override

    public boolean spatialModelBouncerPushback() {
        return getValue(Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK,
            FeatureFlags::spatialModelBouncerPushback);
    }

    @Override

    public boolean spatialModelPushbackInShader() {
        return getValue(Flags.FLAG_SPATIAL_MODEL_PUSHBACK_IN_SHADER,
            FeatureFlags::spatialModelPushbackInShader);
    }

    @Override

    public boolean stabilizeHeadsUpGroupV2() {
        return getValue(Flags.FLAG_STABILIZE_HEADS_UP_GROUP_V2,
            FeatureFlags::stabilizeHeadsUpGroupV2);
    }

    @Override

    public boolean statusBarAlwaysCheckUnderlyingNetworks() {
        return getValue(Flags.FLAG_STATUS_BAR_ALWAYS_CHECK_UNDERLYING_NETWORKS,
            FeatureFlags::statusBarAlwaysCheckUnderlyingNetworks);
    }

    @Override

    public boolean statusBarAppHandleTracking() {
        return getValue(Flags.FLAG_STATUS_BAR_APP_HANDLE_TRACKING,
            FeatureFlags::statusBarAppHandleTracking);
    }

    @Override

    public boolean statusBarChipToHunAnimation() {
        return getValue(Flags.FLAG_STATUS_BAR_CHIP_TO_HUN_ANIMATION,
            FeatureFlags::statusBarChipToHunAnimation);
    }

    @Override

    public boolean statusBarChipsModernization() {
        return getValue(Flags.FLAG_STATUS_BAR_CHIPS_MODERNIZATION,
            FeatureFlags::statusBarChipsModernization);
    }

    @Override

    public boolean statusBarChipsReturnAnimations() {
        return getValue(Flags.FLAG_STATUS_BAR_CHIPS_RETURN_ANIMATIONS,
            FeatureFlags::statusBarChipsReturnAnimations);
    }

    @Override

    public boolean statusBarFontUpdates() {
        return getValue(Flags.FLAG_STATUS_BAR_FONT_UPDATES,
            FeatureFlags::statusBarFontUpdates);
    }

    @Override

    public boolean statusBarMobileIconKairos() {
        return getValue(Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS,
            FeatureFlags::statusBarMobileIconKairos);
    }

    @Override

    public boolean statusBarNoHunBehavior() {
        return getValue(Flags.FLAG_STATUS_BAR_NO_HUN_BEHAVIOR,
            FeatureFlags::statusBarNoHunBehavior);
    }

    @Override

    public boolean statusBarPopupChips() {
        return getValue(Flags.FLAG_STATUS_BAR_POPUP_CHIPS,
            FeatureFlags::statusBarPopupChips);
    }

    @Override

    public boolean statusBarPrivacyChipAnimationExemption() {
        return getValue(Flags.FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION,
            FeatureFlags::statusBarPrivacyChipAnimationExemption);
    }

    @Override

    public boolean statusBarRootModernization() {
        return getValue(Flags.FLAG_STATUS_BAR_ROOT_MODERNIZATION,
            FeatureFlags::statusBarRootModernization);
    }

    @Override

    public boolean statusBarRudimentaryBattery() {
        return getValue(Flags.FLAG_STATUS_BAR_RUDIMENTARY_BATTERY,
            FeatureFlags::statusBarRudimentaryBattery);
    }

    @Override

    public boolean statusBarSignalPolicyRefactor() {
        return getValue(Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR,
            FeatureFlags::statusBarSignalPolicyRefactor);
    }

    @Override

    public boolean statusBarSignalPolicyRefactorEthernet() {
        return getValue(Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR_ETHERNET,
            FeatureFlags::statusBarSignalPolicyRefactorEthernet);
    }

    @Override

    public boolean statusBarStaticInoutIndicators() {
        return getValue(Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS,
            FeatureFlags::statusBarStaticInoutIndicators);
    }

    @Override

    public boolean statusBarSwipeOverChip() {
        return getValue(Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP,
            FeatureFlags::statusBarSwipeOverChip);
    }

    @Override

    public boolean statusBarSwitchToSpnFromDataSpn() {
        return getValue(Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN,
            FeatureFlags::statusBarSwitchToSpnFromDataSpn);
    }

    @Override

    public boolean statusBarSystemStatusIconsInCompose() {
        return getValue(Flags.FLAG_STATUS_BAR_SYSTEM_STATUS_ICONS_IN_COMPOSE,
            FeatureFlags::statusBarSystemStatusIconsInCompose);
    }

    @Override

    public boolean statusBarUiThread() {
        return getValue(Flags.FLAG_STATUS_BAR_UI_THREAD,
            FeatureFlags::statusBarUiThread);
    }

    @Override

    public boolean statusBarWindowNoCustomTouch() {
        return getValue(Flags.FLAG_STATUS_BAR_WINDOW_NO_CUSTOM_TOUCH,
            FeatureFlags::statusBarWindowNoCustomTouch);
    }

    @Override

    public boolean stuckHearingDevicesQsTileFix() {
        return getValue(Flags.FLAG_STUCK_HEARING_DEVICES_QS_TILE_FIX,
            FeatureFlags::stuckHearingDevicesQsTileFix);
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

    public boolean thinScreenRecordingService() {
        return getValue(Flags.FLAG_THIN_SCREEN_RECORDING_SERVICE,
            FeatureFlags::thinScreenRecordingService);
    }

    @Override

    public boolean transitionRaceConditionPart2() {
        return getValue(Flags.FLAG_TRANSITION_RACE_CONDITION_PART2,
            FeatureFlags::transitionRaceConditionPart2);
    }

    @Override

    public boolean tvGlobalActionsFocus() {
        return getValue(Flags.FLAG_TV_GLOBAL_ACTIONS_FOCUS,
            FeatureFlags::tvGlobalActionsFocus);
    }

    @Override

    public boolean udfpsScreenOffUnlockFlicker() {
        return getValue(Flags.FLAG_UDFPS_SCREEN_OFF_UNLOCK_FLICKER,
            FeatureFlags::udfpsScreenOffUnlockFlicker);
    }

    @Override

    public boolean uiRichOngoingAodSkeletonBgInflation() {
        return getValue(Flags.FLAG_UI_RICH_ONGOING_AOD_SKELETON_BG_INFLATION,
            FeatureFlags::uiRichOngoingAodSkeletonBgInflation);
    }

    @Override

    public boolean unfoldAnimationBackgroundProgress() {
        return getValue(Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS,
            FeatureFlags::unfoldAnimationBackgroundProgress);
    }

    @Override

    public boolean updateCornerRadiusOnDisplayChanged() {
        return getValue(Flags.FLAG_UPDATE_CORNER_RADIUS_ON_DISPLAY_CHANGED,
            FeatureFlags::updateCornerRadiusOnDisplayChanged);
    }

    @Override

    public boolean updateUserSwitcherBackground() {
        return getValue(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND,
            FeatureFlags::updateUserSwitcherBackground);
    }

    @Override

    public boolean updateWindowMagnifierBottomBoundary() {
        return getValue(Flags.FLAG_UPDATE_WINDOW_MAGNIFIER_BOTTOM_BOUNDARY,
            FeatureFlags::updateWindowMagnifierBottomBoundary);
    }

    @Override

    public boolean useAadProxSensorIfPresent() {
        return getValue(Flags.FLAG_USE_AAD_PROX_SENSOR_IF_PRESENT,
            FeatureFlags::useAadProxSensorIfPresent);
    }

    @Override

    public boolean userAwareSettingsRepositories() {
        return getValue(Flags.FLAG_USER_AWARE_SETTINGS_REPOSITORIES,
            FeatureFlags::userAwareSettingsRepositories);
    }

    @Override

    public boolean userEncryptedSource() {
        return getValue(Flags.FLAG_USER_ENCRYPTED_SOURCE,
            FeatureFlags::userEncryptedSource);
    }

    @Override

    public boolean userSwitcherAddSignOutOption() {
        return getValue(Flags.FLAG_USER_SWITCHER_ADD_SIGN_OUT_OPTION,
            FeatureFlags::userSwitcherAddSignOutOption);
    }

    @Override

    public boolean visualInterruptionsRefactor() {
        return getValue(Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR,
            FeatureFlags::visualInterruptionsRefactor);
    }

    @Override

    public boolean volumeRedesign() {
        return getValue(Flags.FLAG_VOLUME_REDESIGN,
            FeatureFlags::volumeRedesign);
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
            Flags.FLAG_ALWAYS_COMPOSE_QS_UI_FRAGMENT,
            Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES,
            Flags.FLAG_ANIMATION_LIBRARY_DELAY_LEASH_CLEANUP,
            Flags.FLAG_APP_CLIPS_BACKLINKS,
            Flags.FLAG_AVALANCHE_REPLACE_HUN_WHEN_CRITICAL,
            Flags.FLAG_BACK_BUTTON_ON_BOUNCER,
            Flags.FLAG_BOUNCER_LIFECYCLE_FIX,
            Flags.FLAG_BOUNCER_UI_REVAMP,
            Flags.FLAG_BOUNCER_UI_REVAMP_2,
            Flags.FLAG_BP_COLORS,
            Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE,
            Flags.FLAG_CLASSIC_FLAGS_MULTI_USER,
            Flags.FLAG_CLIPBOARD_ANNOUNCE_LIVE_REGION,
            Flags.FLAG_CLIPBOARD_OVERLAY_MULTIUSER,
            Flags.FLAG_CLIPBOARD_USE_DESCRIPTION_MIMETYPE,
            Flags.FLAG_CLOCK_FIDGET_ANIMATION,
            Flags.FLAG_CLOCK_MODERNIZATION,
            Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN,
            Flags.FLAG_COMMUNAL_EDIT_WIDGETS_ACTIVITY_FINISH_FIX,
            Flags.FLAG_COMMUNAL_HUB,
            Flags.FLAG_COMMUNAL_HUB_USE_THREAD_POOL_FOR_WIDGETS,
            Flags.FLAG_COMMUNAL_RESPONSIVE_GRID,
            Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES,
            Flags.FLAG_COMMUNAL_STANDALONE_SUPPORT,
            Flags.FLAG_COMMUNAL_TIMER_FLICKER_FIX,
            Flags.FLAG_COMMUNAL_WIDGET_RESIZING,
            Flags.FLAG_COMMUNAL_WIDGET_TRAMPOLINE_FIX,
            Flags.FLAG_COMPOSE_BOUNCER,
            Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH,
            Flags.FLAG_CONT_AUTH_PLUGIN,
            Flags.FLAG_CONTEXTUAL_TIPS_ASSISTANT_DISMISS_FIX,
            Flags.FLAG_COROUTINE_TRACING,
            Flags.FLAG_DECOUPLE_VIEW_CONTROLLER_IN_ANIMLIB,
            Flags.FLAG_DESKTOP_EFFECTS_QS_TILE,
            Flags.FLAG_DESKTOP_SCREEN_CAPTURE,
            Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_FREQUENCY_CHECK,
            Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_IOS_SWITCHER_CHECK,
            Flags.FLAG_DISABLE_DOUBLE_CLICK_SWAP_ON_BOUNCER,
            Flags.FLAG_DO_NOT_USE_IMMEDIATE_COROUTINE_DISPATCHER,
            Flags.FLAG_DOUBLE_TAP_TO_SLEEP,
            Flags.FLAG_DREAM_BIOMETRIC_PROMPT_FIXES,
            Flags.FLAG_DREAM_INPUT_SESSION_PILFER_ONCE,
            Flags.FLAG_DREAM_OVERLAY_BOUNCER_SWIPE_DIRECTION_FILTERING,
            Flags.FLAG_DREAM_OVERLAY_UPDATED_UI,
            Flags.FLAG_DREAM_PREVIEW_TAP_DISMISS,
            Flags.FLAG_DREAM_TRANSITION_FIXES,
            Flags.FLAG_EDGE_BACK_GESTURE_HANDLER_THREAD,
            Flags.FLAG_EDGEBACK_GESTURE_HANDLER_GET_RUNNING_TASKS_BACKGROUND,
            Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
            Flags.FLAG_ENABLE_CONSTRAINT_LAYOUT_LOCKSCREEN_ON_EXTERNAL_DISPLAY,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_MUTE_VOLUME,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_POWER_OFF,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_TAKE_SCREENSHOT,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIPS,
            Flags.FLAG_ENABLE_DESKTOP_GROWTH,
            Flags.FLAG_ENABLE_EFFICIENT_DISPLAY_REPOSITORY,
            Flags.FLAG_ENABLE_LAYOUT_TRACING,
            Flags.FLAG_ENABLE_MINMODE,
            Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI,
            Flags.FLAG_ENABLE_TOP_UI_CONTROLLER,
            Flags.FLAG_ENABLE_UNDERLAY,
            Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING,
            Flags.FLAG_ENFORCE_BRIGHTNESS_BASE_USER_RESTRICTION,
            Flags.FLAG_EXAMPLE_FLAG,
            Flags.FLAG_EXPAND_COLLAPSE_PRIVACY_DIALOG,
            Flags.FLAG_EXPAND_HEADS_UP_ON_INLINE_REPLY,
            Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN,
            Flags.FLAG_EXTENDED_APPS_SHORTCUT_CATEGORY,
            Flags.FLAG_FACE_SCANNING_ANIMATION_NPE_FIX,
            Flags.FLAG_FETCH_BOOKMARKS_XML_KEYBOARD_SHORTCUTS,
            Flags.FLAG_FIX_DIALOG_LAUNCH_ANIMATION_JANK_LOGGING,
            Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
            Flags.FLAG_FLASHLIGHT_STRENGTH,
            Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK,
            Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE,
            Flags.FLAG_FLOATING_MENU_HEARING_DEVICE_STATUS_ICON,
            Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION,
            Flags.FLAG_FLOATING_MENU_NOTIFY_TARGETS_CHANGED_ON_STRICT_DIFF,
            Flags.FLAG_FLOATING_MENU_OVERLAPS_NAV_BARS_FLAG,
            Flags.FLAG_FLOATING_MENU_RADII_ANIMATION,
            Flags.FLAG_FLOATING_MENU_REMOVE_FULLSCREEN_TAPS,
            Flags.FLAG_GET_CONNECTED_DEVICE_NAME_UNSYNCHRONIZED,
            Flags.FLAG_GLANCEABLE_HUB_ALLOW_KEYGUARD_WHEN_DREAMING,
            Flags.FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND,
            Flags.FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE,
            Flags.FLAG_GLANCEABLE_HUB_V2,
            Flags.FLAG_GLANCEABLE_HUB_V2_RESOURCES,
            Flags.FLAG_HARDWARE_COLOR_STYLES,
            Flags.FLAG_HEARING_AIDS_QS_TILE_DIALOG,
            Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS,
            Flags.FLAG_HIDE_RINGER_BUTTON_IN_SINGLE_VOLUME_MODE,
            Flags.FLAG_HOME_CONTROLS_DREAM_HSUM,
            Flags.FLAG_HSU_BEHAVIOR_CHANGES,
            Flags.FLAG_HUB_BLURRED_BY_SHADE_FIX,
            Flags.FLAG_HUB_EDIT_MODE_TOUCH_ADJUSTMENTS,
            Flags.FLAG_HUB_EDIT_MODE_TRANSITION,
            Flags.FLAG_ICON_REFRESH_2025,
            Flags.FLAG_INDICATION_TEXT_A11Y_FIX,
            Flags.FLAG_INSTANT_HIDE_SHADE,
            Flags.FLAG_KEYBOARD_DOCKING_INDICATOR,
            Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_REWRITE,
            Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_SHORTCUT_CUSTOMIZER,
            Flags.FLAG_KEYBOARD_TOUCHPAD_CONTEXTUAL_EDUCATION,
            Flags.FLAG_KEYGUARD_TRANSITION_FORCE_FINISH_ON_SCREEN_OFF,
            Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR,
            Flags.FLAG_LOCKSCREEN_FONT,
            Flags.FLAG_LOW_LIGHT_CLOCK_DREAM,
            Flags.FLAG_LOWLIGHT_CLOCK_SET_BRIGHTNESS,
            Flags.FLAG_LOWLIGHT_CLOCK_USES_KEYGUARD_CHARGING_STATUS,
            Flags.FLAG_MAGNETIC_NOTIFICATION_SWIPES,
            Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3,
            Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3_PLACEMENT,
            Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE,
            Flags.FLAG_MEDIA_CONTROLS_UI_UPDATE,
            Flags.FLAG_MEDIA_PROJECTION_DIALOG_BEHIND_LOCKSCREEN,
            Flags.FLAG_MEDIA_PROJECTION_GREY_ERROR_TEXT,
            Flags.FLAG_MEDIA_PROJECTION_REQUEST_ATTRIBUTION_FIX,
            Flags.FLAG_MODES_UI_DIALOG_PAGING,
            Flags.FLAG_MOVE_TRANSITION_ANIMATION_LAYER,
            Flags.FLAG_MSDL_FEEDBACK,
            Flags.FLAG_MULTIUSER_WIFI_PICKER_TRACKER_SUPPORT,
            Flags.FLAG_NEW_AOD_TRANSITION,
            Flags.FLAG_NEW_DOZING_KEYGUARD_STATES,
            Flags.FLAG_NEW_VOLUME_PANEL,
            Flags.FLAG_NON_TOUCHSCREEN_DEVICES_BYPASS_FALSING,
            Flags.FLAG_NOTES_ROLE_QS_TILE,
            Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS,
            Flags.FLAG_NOTIFICATION_AMBIENT_SUPPRESSION_AFTER_INFLATION,
            Flags.FLAG_NOTIFICATION_ANIMATED_ACTIONS_TREATMENT,
            Flags.FLAG_NOTIFICATION_APPEAR_NONLINEAR,
            Flags.FLAG_NOTIFICATION_ASYNC_GROUP_HEADER_INFLATION,
            Flags.FLAG_NOTIFICATION_ASYNC_HYBRID_VIEW_INFLATION,
            Flags.FLAG_NOTIFICATION_AVALANCHE_SUPPRESSION,
            Flags.FLAG_NOTIFICATION_AVALANCHE_THROTTLE_HUN,
            Flags.FLAG_NOTIFICATION_BACKGROUND_TINT_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_BUNDLE_UI,
            Flags.FLAG_NOTIFICATION_COLOR_UPDATE_LOGGER,
            Flags.FLAG_NOTIFICATION_CONTENT_ALPHA_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_FOOTER_BACKGROUND_TINT_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_ROW_ACCESSIBILITY_EXPANDED,
            Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR,
            Flags.FLAG_NOTIFICATION_ROW_TRANSPARENCY,
            Flags.FLAG_NOTIFICATION_SHADE_BLUR,
            Flags.FLAG_NOTIFICATION_SHADE_CLOSE_WAITS_FOR_CHILD_ANIMATIONS,
            Flags.FLAG_NOTIFICATION_SHADE_UI_THREAD,
            Flags.FLAG_NOTIFICATION_SKIP_SILENT_UPDATES,
            Flags.FLAG_NOTIFICATION_TRANSPARENT_HEADER_FIX,
            Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS,
            Flags.FLAG_NOTIFICATIONS_FOOTER_VISIBILITY_FIX,
            Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH,
            Flags.FLAG_NOTIFICATIONS_HUN_ACCESSIBILITY_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_HUN_SHARED_ANIMATION_VALUES,
            Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_LAUNCH_RADIUS,
            Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_PINNED_HUN_IN_SHADE,
            Flags.FLAG_NOTIFICATIONS_REDESIGN_FOOTER_VIEW,
            Flags.FLAG_NOTIFY_PASSWORD_TEXT_VIEW_USER_ACTIVITY_IN_BACKGROUND,
            Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND,
            Flags.FLAG_ONGOING_ACTIVITY_CHIPS_ON_DREAM,
            Flags.FLAG_OVERRIDE_SUPPRESS_OVERLAY_CONDITION,
            Flags.FLAG_PERMISSION_HELPER_INLINE_UI_RICH_ONGOING,
            Flags.FLAG_PERMISSION_HELPER_UI_RICH_ONGOING,
            Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE,
            Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE,
            Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            Flags.FLAG_PRIVACY_DOT_LIVE_REGION,
            Flags.FLAG_PROMOTE_NOTIFICATIONS_AUTOMATICALLY,
            Flags.FLAG_PSS_TASK_SWITCHER,
            Flags.FLAG_QS_COMPOSE_FRAGMENT_EARLY_EXPANSION,
            Flags.FLAG_QS_EDIT_MODE_TABS,
            Flags.FLAG_QS_EDIT_MODE_TOOLTIP,
            Flags.FLAG_QS_NEW_TILES,
            Flags.FLAG_QS_NEW_TILES_FUTURE,
            Flags.FLAG_QS_TILE_DETAILED_VIEW,
            Flags.FLAG_QS_TILE_FOCUS_STATE,
            Flags.FLAG_QS_TILE_TRANSITION_INTERACTION_REFINEMENT,
            Flags.FLAG_QS_UI_REFACTOR,
            Flags.FLAG_QS_UI_REFACTOR_COMPOSE_FRAGMENT,
            Flags.FLAG_QS_WIFI_CONFIG,
            Flags.FLAG_RECORD_ISSUE_QS_TILE,
            Flags.FLAG_REDESIGN_MAGNIFICATION_WINDOW_SIZE,
            Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE,
            Flags.FLAG_REGISTER_CONTENT_OBSERVERS_ASYNC,
            Flags.FLAG_REGISTER_NEW_WALLET_CARD_IN_BACKGROUND,
            Flags.FLAG_REGISTER_WALLPAPER_NOTIFIER_BACKGROUND,
            Flags.FLAG_REMEMBER_VIEW_MODEL_OFF_MAIN_THREAD,
            Flags.FLAG_REMOVE_AOD_CAR_MODE,
            Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH,
            Flags.FLAG_REMOVE_NEARBY_SHARE_TILE_ANIMATION,
            Flags.FLAG_REMOVE_UPDATE_LISTENER_IN_QS_ICON_VIEW_IMPL,
            Flags.FLAG_REST_TO_UNLOCK,
            Flags.FLAG_RESTART_DREAM_ON_UNOCCLUDE,
            Flags.FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING,
            Flags.FLAG_REVAMPED_BOUNCER_MESSAGES,
            Flags.FLAG_RUN_FINGERPRINT_DETECT_ON_DISMISSIBLE_KEYGUARD,
            Flags.FLAG_SAVE_AND_RESTORE_MAGNIFICATION_SETTINGS_BUTTONS,
            Flags.FLAG_SCENE_CONTAINER,
            Flags.FLAG_SCREEN_REACTIONS,
            Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX,
            Flags.FLAG_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
            Flags.FLAG_SCREENSHOT_ANNOUNCE_LIVE_REGION,
            Flags.FLAG_SCREENSHOT_MULTIDISPLAY_FOCUS_CHANGE,
            Flags.FLAG_SCREENSHOT_POLICY_SPLIT_AND_DESKTOP_MODE,
            Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX,
            Flags.FLAG_SECONDARY_USER_WIDGET_HOST,
            Flags.FLAG_SETTINGS_EXT_REGISTER_CONTENT_OBSERVER_ON_BG_THREAD,
            Flags.FLAG_SHADE_EXPANDS_ON_STATUS_BAR_LONG_PRESS,
            Flags.FLAG_SHADE_HEADER_BLUR_FONT_COLOR,
            Flags.FLAG_SHADE_HEADER_FONT_UPDATE,
            Flags.FLAG_SHADE_QSVISIBLE_LOGIC,
            Flags.FLAG_SHADE_WINDOW_GOES_AROUND,
            Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR,
            Flags.FLAG_SHORTCUT_HELPER_KEY_GLYPH,
            Flags.FLAG_SHOW_AUDIO_SHARING_SLIDER_IN_VOLUME_PANEL,
            Flags.FLAG_SHOW_CLIPBOARD_INDICATION,
            Flags.FLAG_SHOW_LOCKED_BY_YOUR_WATCH_KEYGUARD_INDICATOR,
            Flags.FLAG_SIM_PIN_BOUNCER_RESET,
            Flags.FLAG_SKIP_HIDE_SENSITIVE_NOTIF_ANIMATION,
            Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND,
            Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM,
            Flags.FLAG_SMARTSPACE_SWIPE_EVENT_LOGGING_FIX,
            Flags.FLAG_SMARTSPACE_VIEWPAGER2,
            Flags.FLAG_SOUNDDOSE_CUSTOMIZATION,
            Flags.FLAG_SPATIAL_MODEL_APP_PUSHBACK,
            Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK,
            Flags.FLAG_SPATIAL_MODEL_PUSHBACK_IN_SHADER,
            Flags.FLAG_STABILIZE_HEADS_UP_GROUP_V2,
            Flags.FLAG_STATUS_BAR_ALWAYS_CHECK_UNDERLYING_NETWORKS,
            Flags.FLAG_STATUS_BAR_APP_HANDLE_TRACKING,
            Flags.FLAG_STATUS_BAR_CHIP_TO_HUN_ANIMATION,
            Flags.FLAG_STATUS_BAR_CHIPS_MODERNIZATION,
            Flags.FLAG_STATUS_BAR_CHIPS_RETURN_ANIMATIONS,
            Flags.FLAG_STATUS_BAR_FONT_UPDATES,
            Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS,
            Flags.FLAG_STATUS_BAR_NO_HUN_BEHAVIOR,
            Flags.FLAG_STATUS_BAR_POPUP_CHIPS,
            Flags.FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION,
            Flags.FLAG_STATUS_BAR_ROOT_MODERNIZATION,
            Flags.FLAG_STATUS_BAR_RUDIMENTARY_BATTERY,
            Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR,
            Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR_ETHERNET,
            Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS,
            Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP,
            Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN,
            Flags.FLAG_STATUS_BAR_SYSTEM_STATUS_ICONS_IN_COMPOSE,
            Flags.FLAG_STATUS_BAR_UI_THREAD,
            Flags.FLAG_STATUS_BAR_WINDOW_NO_CUSTOM_TOUCH,
            Flags.FLAG_STUCK_HEARING_DEVICES_QS_TILE_FIX,
            Flags.FLAG_SWITCH_USER_ON_BG,
            Flags.FLAG_SYSUI_TEAMFOOD,
            Flags.FLAG_THEME_OVERLAY_CONTROLLER_WAKEFULNESS_DEPRECATION,
            Flags.FLAG_THIN_SCREEN_RECORDING_SERVICE,
            Flags.FLAG_TRANSITION_RACE_CONDITION_PART2,
            Flags.FLAG_TV_GLOBAL_ACTIONS_FOCUS,
            Flags.FLAG_UDFPS_SCREEN_OFF_UNLOCK_FLICKER,
            Flags.FLAG_UI_RICH_ONGOING_AOD_SKELETON_BG_INFLATION,
            Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS,
            Flags.FLAG_UPDATE_CORNER_RADIUS_ON_DISPLAY_CHANGED,
            Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND,
            Flags.FLAG_UPDATE_WINDOW_MAGNIFIER_BOTTOM_BOUNDARY,
            Flags.FLAG_USE_AAD_PROX_SENSOR_IF_PRESENT,
            Flags.FLAG_USER_AWARE_SETTINGS_REPOSITORIES,
            Flags.FLAG_USER_ENCRYPTED_SOURCE,
            Flags.FLAG_USER_SWITCHER_ADD_SIGN_OUT_OPTION,
            Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR,
            Flags.FLAG_VOLUME_REDESIGN
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_ACTIVITY_TRANSITION_USE_LARGEST_WINDOW,
            Flags.FLAG_ALWAYS_COMPOSE_QS_UI_FRAGMENT,
            Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES,
            Flags.FLAG_ANIMATION_LIBRARY_DELAY_LEASH_CLEANUP,
            Flags.FLAG_APP_CLIPS_BACKLINKS,
            Flags.FLAG_AVALANCHE_REPLACE_HUN_WHEN_CRITICAL,
            Flags.FLAG_BACK_BUTTON_ON_BOUNCER,
            Flags.FLAG_BOUNCER_LIFECYCLE_FIX,
            Flags.FLAG_BOUNCER_UI_REVAMP,
            Flags.FLAG_BOUNCER_UI_REVAMP_2,
            Flags.FLAG_BP_COLORS,
            Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE,
            Flags.FLAG_CLASSIC_FLAGS_MULTI_USER,
            Flags.FLAG_CLIPBOARD_ANNOUNCE_LIVE_REGION,
            Flags.FLAG_CLIPBOARD_OVERLAY_MULTIUSER,
            Flags.FLAG_CLIPBOARD_USE_DESCRIPTION_MIMETYPE,
            Flags.FLAG_CLOCK_FIDGET_ANIMATION,
            Flags.FLAG_CLOCK_MODERNIZATION,
            Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN,
            Flags.FLAG_COMMUNAL_EDIT_WIDGETS_ACTIVITY_FINISH_FIX,
            Flags.FLAG_COMMUNAL_HUB,
            Flags.FLAG_COMMUNAL_HUB_USE_THREAD_POOL_FOR_WIDGETS,
            Flags.FLAG_COMMUNAL_RESPONSIVE_GRID,
            Flags.FLAG_COMMUNAL_SHADE_TOUCH_HANDLING_FIXES,
            Flags.FLAG_COMMUNAL_STANDALONE_SUPPORT,
            Flags.FLAG_COMMUNAL_TIMER_FLICKER_FIX,
            Flags.FLAG_COMMUNAL_WIDGET_RESIZING,
            Flags.FLAG_COMMUNAL_WIDGET_TRAMPOLINE_FIX,
            Flags.FLAG_COMPOSE_BOUNCER,
            Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH,
            Flags.FLAG_CONT_AUTH_PLUGIN,
            Flags.FLAG_CONTEXTUAL_TIPS_ASSISTANT_DISMISS_FIX,
            Flags.FLAG_COROUTINE_TRACING,
            Flags.FLAG_DECOUPLE_VIEW_CONTROLLER_IN_ANIMLIB,
            Flags.FLAG_DESKTOP_EFFECTS_QS_TILE,
            Flags.FLAG_DESKTOP_SCREEN_CAPTURE,
            Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_FREQUENCY_CHECK,
            Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_IOS_SWITCHER_CHECK,
            Flags.FLAG_DISABLE_DOUBLE_CLICK_SWAP_ON_BOUNCER,
            Flags.FLAG_DO_NOT_USE_IMMEDIATE_COROUTINE_DISPATCHER,
            Flags.FLAG_DOUBLE_TAP_TO_SLEEP,
            Flags.FLAG_DREAM_BIOMETRIC_PROMPT_FIXES,
            Flags.FLAG_DREAM_INPUT_SESSION_PILFER_ONCE,
            Flags.FLAG_DREAM_OVERLAY_BOUNCER_SWIPE_DIRECTION_FILTERING,
            Flags.FLAG_DREAM_OVERLAY_UPDATED_UI,
            Flags.FLAG_DREAM_PREVIEW_TAP_DISMISS,
            Flags.FLAG_DREAM_TRANSITION_FIXES,
            Flags.FLAG_EDGE_BACK_GESTURE_HANDLER_THREAD,
            Flags.FLAG_EDGEBACK_GESTURE_HANDLER_GET_RUNNING_TASKS_BACKGROUND,
            Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK,
            Flags.FLAG_ENABLE_CONSTRAINT_LAYOUT_LOCKSCREEN_ON_EXTERNAL_DISPLAY,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_MUTE_VOLUME,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_POWER_OFF,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_TAKE_SCREENSHOT,
            Flags.FLAG_ENABLE_CONTEXTUAL_TIPS,
            Flags.FLAG_ENABLE_DESKTOP_GROWTH,
            Flags.FLAG_ENABLE_EFFICIENT_DISPLAY_REPOSITORY,
            Flags.FLAG_ENABLE_LAYOUT_TRACING,
            Flags.FLAG_ENABLE_MINMODE,
            Flags.FLAG_ENABLE_SUGGESTED_DEVICE_UI,
            Flags.FLAG_ENABLE_TOP_UI_CONTROLLER,
            Flags.FLAG_ENABLE_UNDERLAY,
            Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING,
            Flags.FLAG_ENFORCE_BRIGHTNESS_BASE_USER_RESTRICTION,
            Flags.FLAG_EXAMPLE_FLAG,
            Flags.FLAG_EXPAND_COLLAPSE_PRIVACY_DIALOG,
            Flags.FLAG_EXPAND_HEADS_UP_ON_INLINE_REPLY,
            Flags.FLAG_EXPANDED_PRIVACY_INDICATORS_ON_LARGE_SCREEN,
            Flags.FLAG_EXTENDED_APPS_SHORTCUT_CATEGORY,
            Flags.FLAG_FACE_SCANNING_ANIMATION_NPE_FIX,
            Flags.FLAG_FETCH_BOOKMARKS_XML_KEYBOARD_SHORTCUTS,
            Flags.FLAG_FIX_DIALOG_LAUNCH_ANIMATION_JANK_LOGGING,
            Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
            Flags.FLAG_FLASHLIGHT_STRENGTH,
            Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK,
            Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE,
            Flags.FLAG_FLOATING_MENU_HEARING_DEVICE_STATUS_ICON,
            Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION,
            Flags.FLAG_FLOATING_MENU_NOTIFY_TARGETS_CHANGED_ON_STRICT_DIFF,
            Flags.FLAG_FLOATING_MENU_OVERLAPS_NAV_BARS_FLAG,
            Flags.FLAG_FLOATING_MENU_RADII_ANIMATION,
            Flags.FLAG_FLOATING_MENU_REMOVE_FULLSCREEN_TAPS,
            Flags.FLAG_GET_CONNECTED_DEVICE_NAME_UNSYNCHRONIZED,
            Flags.FLAG_GLANCEABLE_HUB_ALLOW_KEYGUARD_WHEN_DREAMING,
            Flags.FLAG_GLANCEABLE_HUB_BLURRED_BACKGROUND,
            Flags.FLAG_GLANCEABLE_HUB_DIRECT_EDIT_MODE,
            Flags.FLAG_GLANCEABLE_HUB_V2,
            Flags.FLAG_GLANCEABLE_HUB_V2_RESOURCES,
            Flags.FLAG_HARDWARE_COLOR_STYLES,
            Flags.FLAG_HEARING_AIDS_QS_TILE_DIALOG,
            Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS,
            Flags.FLAG_HIDE_RINGER_BUTTON_IN_SINGLE_VOLUME_MODE,
            Flags.FLAG_HOME_CONTROLS_DREAM_HSUM,
            Flags.FLAG_HSU_BEHAVIOR_CHANGES,
            Flags.FLAG_HUB_BLURRED_BY_SHADE_FIX,
            Flags.FLAG_HUB_EDIT_MODE_TOUCH_ADJUSTMENTS,
            Flags.FLAG_HUB_EDIT_MODE_TRANSITION,
            Flags.FLAG_ICON_REFRESH_2025,
            Flags.FLAG_INDICATION_TEXT_A11Y_FIX,
            Flags.FLAG_INSTANT_HIDE_SHADE,
            Flags.FLAG_KEYBOARD_DOCKING_INDICATOR,
            Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_REWRITE,
            Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_SHORTCUT_CUSTOMIZER,
            Flags.FLAG_KEYBOARD_TOUCHPAD_CONTEXTUAL_EDUCATION,
            Flags.FLAG_KEYGUARD_TRANSITION_FORCE_FINISH_ON_SCREEN_OFF,
            Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR,
            Flags.FLAG_LOCKSCREEN_FONT,
            Flags.FLAG_LOW_LIGHT_CLOCK_DREAM,
            Flags.FLAG_LOWLIGHT_CLOCK_SET_BRIGHTNESS,
            Flags.FLAG_LOWLIGHT_CLOCK_USES_KEYGUARD_CHARGING_STATUS,
            Flags.FLAG_MAGNETIC_NOTIFICATION_SWIPES,
            Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3,
            Flags.FLAG_MEDIA_CONTROLS_BUTTON_MEDIA3_PLACEMENT,
            Flags.FLAG_MEDIA_CONTROLS_IN_COMPOSE,
            Flags.FLAG_MEDIA_CONTROLS_UI_UPDATE,
            Flags.FLAG_MEDIA_PROJECTION_DIALOG_BEHIND_LOCKSCREEN,
            Flags.FLAG_MEDIA_PROJECTION_GREY_ERROR_TEXT,
            Flags.FLAG_MEDIA_PROJECTION_REQUEST_ATTRIBUTION_FIX,
            Flags.FLAG_MODES_UI_DIALOG_PAGING,
            Flags.FLAG_MOVE_TRANSITION_ANIMATION_LAYER,
            Flags.FLAG_MSDL_FEEDBACK,
            Flags.FLAG_MULTIUSER_WIFI_PICKER_TRACKER_SUPPORT,
            Flags.FLAG_NEW_AOD_TRANSITION,
            Flags.FLAG_NEW_DOZING_KEYGUARD_STATES,
            Flags.FLAG_NEW_VOLUME_PANEL,
            Flags.FLAG_NON_TOUCHSCREEN_DEVICES_BYPASS_FALSING,
            Flags.FLAG_NOTES_ROLE_QS_TILE,
            Flags.FLAG_NOTIFICATION_ADD_X_ON_HOVER_TO_DISMISS,
            Flags.FLAG_NOTIFICATION_AMBIENT_SUPPRESSION_AFTER_INFLATION,
            Flags.FLAG_NOTIFICATION_ANIMATED_ACTIONS_TREATMENT,
            Flags.FLAG_NOTIFICATION_APPEAR_NONLINEAR,
            Flags.FLAG_NOTIFICATION_ASYNC_GROUP_HEADER_INFLATION,
            Flags.FLAG_NOTIFICATION_ASYNC_HYBRID_VIEW_INFLATION,
            Flags.FLAG_NOTIFICATION_AVALANCHE_SUPPRESSION,
            Flags.FLAG_NOTIFICATION_AVALANCHE_THROTTLE_HUN,
            Flags.FLAG_NOTIFICATION_BACKGROUND_TINT_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_BUNDLE_UI,
            Flags.FLAG_NOTIFICATION_COLOR_UPDATE_LOGGER,
            Flags.FLAG_NOTIFICATION_CONTENT_ALPHA_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_FOOTER_BACKGROUND_TINT_OPTIMIZATION,
            Flags.FLAG_NOTIFICATION_ROW_ACCESSIBILITY_EXPANDED,
            Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR,
            Flags.FLAG_NOTIFICATION_ROW_TRANSPARENCY,
            Flags.FLAG_NOTIFICATION_SHADE_BLUR,
            Flags.FLAG_NOTIFICATION_SHADE_CLOSE_WAITS_FOR_CHILD_ANIMATIONS,
            Flags.FLAG_NOTIFICATION_SHADE_UI_THREAD,
            Flags.FLAG_NOTIFICATION_SKIP_SILENT_UPDATES,
            Flags.FLAG_NOTIFICATION_TRANSPARENT_HEADER_FIX,
            Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS,
            Flags.FLAG_NOTIFICATIONS_FOOTER_VISIBILITY_FIX,
            Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH,
            Flags.FLAG_NOTIFICATIONS_HUN_ACCESSIBILITY_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_HUN_SHARED_ANIMATION_VALUES,
            Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_LAUNCH_RADIUS,
            Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR,
            Flags.FLAG_NOTIFICATIONS_PINNED_HUN_IN_SHADE,
            Flags.FLAG_NOTIFICATIONS_REDESIGN_FOOTER_VIEW,
            Flags.FLAG_NOTIFY_PASSWORD_TEXT_VIEW_USER_ACTIVITY_IN_BACKGROUND,
            Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND,
            Flags.FLAG_ONGOING_ACTIVITY_CHIPS_ON_DREAM,
            Flags.FLAG_OVERRIDE_SUPPRESS_OVERLAY_CONDITION,
            Flags.FLAG_PERMISSION_HELPER_INLINE_UI_RICH_ONGOING,
            Flags.FLAG_PERMISSION_HELPER_UI_RICH_ONGOING,
            Flags.FLAG_PHYSICAL_NOTIFICATION_MOVEMENT,
            Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE,
            Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE,
            Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            Flags.FLAG_PRIVACY_DOT_LIVE_REGION,
            Flags.FLAG_PROMOTE_NOTIFICATIONS_AUTOMATICALLY,
            Flags.FLAG_PSS_TASK_SWITCHER,
            Flags.FLAG_QS_COMPOSE_FRAGMENT_EARLY_EXPANSION,
            Flags.FLAG_QS_EDIT_MODE_TABS,
            Flags.FLAG_QS_EDIT_MODE_TOOLTIP,
            Flags.FLAG_QS_NEW_TILES,
            Flags.FLAG_QS_NEW_TILES_FUTURE,
            Flags.FLAG_QS_TILE_DETAILED_VIEW,
            Flags.FLAG_QS_TILE_FOCUS_STATE,
            Flags.FLAG_QS_TILE_TRANSITION_INTERACTION_REFINEMENT,
            Flags.FLAG_QS_UI_REFACTOR,
            Flags.FLAG_QS_UI_REFACTOR_COMPOSE_FRAGMENT,
            Flags.FLAG_QS_WIFI_CONFIG,
            Flags.FLAG_RECORD_ISSUE_QS_TILE,
            Flags.FLAG_REDESIGN_MAGNIFICATION_WINDOW_SIZE,
            Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE,
            Flags.FLAG_REGISTER_CONTENT_OBSERVERS_ASYNC,
            Flags.FLAG_REGISTER_NEW_WALLET_CARD_IN_BACKGROUND,
            Flags.FLAG_REGISTER_WALLPAPER_NOTIFIER_BACKGROUND,
            Flags.FLAG_REMEMBER_VIEW_MODEL_OFF_MAIN_THREAD,
            Flags.FLAG_REMOVE_AOD_CAR_MODE,
            Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH,
            Flags.FLAG_REMOVE_NEARBY_SHARE_TILE_ANIMATION,
            Flags.FLAG_REMOVE_UPDATE_LISTENER_IN_QS_ICON_VIEW_IMPL,
            Flags.FLAG_REST_TO_UNLOCK,
            Flags.FLAG_RESTART_DREAM_ON_UNOCCLUDE,
            Flags.FLAG_RESTRICT_COMMUNAL_APP_WIDGET_HOST_LISTENING,
            Flags.FLAG_REVAMPED_BOUNCER_MESSAGES,
            Flags.FLAG_RUN_FINGERPRINT_DETECT_ON_DISMISSIBLE_KEYGUARD,
            Flags.FLAG_SAVE_AND_RESTORE_MAGNIFICATION_SETTINGS_BUTTONS,
            Flags.FLAG_SCENE_CONTAINER,
            Flags.FLAG_SCREEN_REACTIONS,
            Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX,
            Flags.FLAG_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS,
            Flags.FLAG_SCREENSHOT_ANNOUNCE_LIVE_REGION,
            Flags.FLAG_SCREENSHOT_MULTIDISPLAY_FOCUS_CHANGE,
            Flags.FLAG_SCREENSHOT_POLICY_SPLIT_AND_DESKTOP_MODE,
            Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX,
            Flags.FLAG_SECONDARY_USER_WIDGET_HOST,
            Flags.FLAG_SETTINGS_EXT_REGISTER_CONTENT_OBSERVER_ON_BG_THREAD,
            Flags.FLAG_SHADE_EXPANDS_ON_STATUS_BAR_LONG_PRESS,
            Flags.FLAG_SHADE_HEADER_BLUR_FONT_COLOR,
            Flags.FLAG_SHADE_HEADER_FONT_UPDATE,
            Flags.FLAG_SHADE_QSVISIBLE_LOGIC,
            Flags.FLAG_SHADE_WINDOW_GOES_AROUND,
            Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR,
            Flags.FLAG_SHORTCUT_HELPER_KEY_GLYPH,
            Flags.FLAG_SHOW_AUDIO_SHARING_SLIDER_IN_VOLUME_PANEL,
            Flags.FLAG_SHOW_CLIPBOARD_INDICATION,
            Flags.FLAG_SHOW_LOCKED_BY_YOUR_WATCH_KEYGUARD_INDICATOR,
            Flags.FLAG_SIM_PIN_BOUNCER_RESET,
            Flags.FLAG_SKIP_HIDE_SENSITIVE_NOTIF_ANIMATION,
            Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND,
            Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM,
            Flags.FLAG_SMARTSPACE_SWIPE_EVENT_LOGGING_FIX,
            Flags.FLAG_SMARTSPACE_VIEWPAGER2,
            Flags.FLAG_SOUNDDOSE_CUSTOMIZATION,
            Flags.FLAG_SPATIAL_MODEL_APP_PUSHBACK,
            Flags.FLAG_SPATIAL_MODEL_BOUNCER_PUSHBACK,
            Flags.FLAG_SPATIAL_MODEL_PUSHBACK_IN_SHADER,
            Flags.FLAG_STABILIZE_HEADS_UP_GROUP_V2,
            Flags.FLAG_STATUS_BAR_ALWAYS_CHECK_UNDERLYING_NETWORKS,
            Flags.FLAG_STATUS_BAR_APP_HANDLE_TRACKING,
            Flags.FLAG_STATUS_BAR_CHIP_TO_HUN_ANIMATION,
            Flags.FLAG_STATUS_BAR_CHIPS_MODERNIZATION,
            Flags.FLAG_STATUS_BAR_CHIPS_RETURN_ANIMATIONS,
            Flags.FLAG_STATUS_BAR_FONT_UPDATES,
            Flags.FLAG_STATUS_BAR_MOBILE_ICON_KAIROS,
            Flags.FLAG_STATUS_BAR_NO_HUN_BEHAVIOR,
            Flags.FLAG_STATUS_BAR_POPUP_CHIPS,
            Flags.FLAG_STATUS_BAR_PRIVACY_CHIP_ANIMATION_EXEMPTION,
            Flags.FLAG_STATUS_BAR_ROOT_MODERNIZATION,
            Flags.FLAG_STATUS_BAR_RUDIMENTARY_BATTERY,
            Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR,
            Flags.FLAG_STATUS_BAR_SIGNAL_POLICY_REFACTOR_ETHERNET,
            Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS,
            Flags.FLAG_STATUS_BAR_SWIPE_OVER_CHIP,
            Flags.FLAG_STATUS_BAR_SWITCH_TO_SPN_FROM_DATA_SPN,
            Flags.FLAG_STATUS_BAR_SYSTEM_STATUS_ICONS_IN_COMPOSE,
            Flags.FLAG_STATUS_BAR_UI_THREAD,
            Flags.FLAG_STATUS_BAR_WINDOW_NO_CUSTOM_TOUCH,
            Flags.FLAG_STUCK_HEARING_DEVICES_QS_TILE_FIX,
            Flags.FLAG_SWITCH_USER_ON_BG,
            Flags.FLAG_SYSUI_TEAMFOOD,
            Flags.FLAG_THEME_OVERLAY_CONTROLLER_WAKEFULNESS_DEPRECATION,
            Flags.FLAG_THIN_SCREEN_RECORDING_SERVICE,
            Flags.FLAG_TRANSITION_RACE_CONDITION_PART2,
            Flags.FLAG_TV_GLOBAL_ACTIONS_FOCUS,
            Flags.FLAG_UDFPS_SCREEN_OFF_UNLOCK_FLICKER,
            Flags.FLAG_UI_RICH_ONGOING_AOD_SKELETON_BG_INFLATION,
            Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS,
            Flags.FLAG_UPDATE_CORNER_RADIUS_ON_DISPLAY_CHANGED,
            Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND,
            Flags.FLAG_UPDATE_WINDOW_MAGNIFIER_BOTTOM_BOUNDARY,
            Flags.FLAG_USE_AAD_PROX_SENSOR_IF_PRESENT,
            Flags.FLAG_USER_AWARE_SETTINGS_REPOSITORIES,
            Flags.FLAG_USER_ENCRYPTED_SOURCE,
            Flags.FLAG_USER_SWITCHER_ADD_SIGN_OUT_OPTION,
            Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR,
            Flags.FLAG_VOLUME_REDESIGN,
            ""
        )
    );
}
