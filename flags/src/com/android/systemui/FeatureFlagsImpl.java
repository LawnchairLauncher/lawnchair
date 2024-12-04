package com.android.systemui;
// TODO(b/303773055): Remove the annotation after access issue is resolved.

import com.android.quickstep.util.DeviceConfigHelper;

import java.nio.file.Files;
import java.nio.file.Paths;
/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static final boolean isReadFromNew = Files.exists(Paths.get("/metadata/aconfig/boot/enable_only_new_storage"));
    private static volatile boolean isCached = false;
    private static volatile boolean accessibility_is_cached = false;
    private static volatile boolean biometrics_framework_is_cached = false;
    private static volatile boolean communal_is_cached = false;
    private static volatile boolean systemui_is_cached = false;
    private static boolean activityTransitionUseLargestWindow = true;
    private static boolean ambientTouchMonitorListenToDisplayChanges = false;
    private static boolean appClipsBacklinks = false;
    private static boolean bindKeyguardMediaVisibility = true;
    private static boolean bpTalkback = true;
    private static boolean brightnessSliderFocusState = false;
    private static boolean centralizedStatusBarHeightFix = true;
    private static boolean clipboardNoninteractiveOnLockscreen = false;
    private static boolean clockReactiveVariants = false;
    private static boolean communalBouncerDoNotModifyPluginOpen = false;
    private static boolean communalHub = true;
    private static boolean composeBouncer = false;
    private static boolean composeLockscreen = false;
    private static boolean confineNotificationTouchToViewWidth = true;
    private static boolean constraintBp = true;
    private static boolean contextualTipsAssistantDismissFix = true;
    private static boolean coroutineTracing = true;
    private static boolean createWindowlessWindowMagnifier = true;
    private static boolean dedicatedNotifInflationThread = true;
    private static boolean delayShowMagnificationButton = true;
    private static boolean delayedWakelockReleaseOnBackgroundThread = true;
    private static boolean deviceEntryUdfpsRefactor = true;
    private static boolean disableContextualTipsFrequencyCheck = true;
    private static boolean disableContextualTipsIosSwitcherCheck = true;
    private static boolean dozeuiSchedulingAlarmsBackgroundExecution = false;
    private static boolean dreamInputSessionPilferOnce = false;
    private static boolean dreamOverlayBouncerSwipeDirectionFiltering = true;
    private static boolean dualShade = false;
    private static boolean edgeBackGestureHandlerThread = false;
    private static boolean edgebackGestureHandlerGetRunningTasksBackground = true;
    private static boolean enableBackgroundKeyguardOndrawnCallback = true;
    private static boolean enableContextualTipForMuteVolume = false;
    private static boolean enableContextualTipForPowerOff = true;
    private static boolean enableContextualTipForTakeScreenshot = true;
    private static boolean enableContextualTips = true;
    private static boolean enableEfficientDisplayRepository = false;
    private static boolean enableLayoutTracing = false;
    private static boolean enableViewCaptureTracing = false;
    private static boolean enableWidgetPickerSizeFilter = false;
    private static boolean enforceBrightnessBaseUserRestriction = true;
    private static boolean exampleFlag = false;
    private static boolean fastUnlockTransition = false;
    private static boolean fixImageWallpaperCrashSurfaceAlreadyReleased = true;
    private static boolean fixScreenshotActionDismissSystemWindows = true;
    private static boolean floatingMenuAnimatedTuck = true;
    private static boolean floatingMenuDragToEdit = true;
    private static boolean floatingMenuDragToHide = false;
    private static boolean floatingMenuImeDisplacementAnimation = true;
    private static boolean floatingMenuNarrowTargetContentObserver = true;
    private static boolean floatingMenuOverlapsNavBarsFlag = true;
    private static boolean floatingMenuRadiiAnimation = true;
    private static boolean getConnectedDeviceNameUnsynchronized = true;
    private static boolean glanceableHubAllowKeyguardWhenDreaming = false;
    private static boolean glanceableHubFullscreenSwipe = false;
    private static boolean glanceableHubGestureHandle = false;
    private static boolean glanceableHubShortcutButton = false;
    private static boolean hapticBrightnessSlider = true;
    private static boolean hapticVolumeSlider = true;
    private static boolean hearingAidsQsTileDialog = true;
    private static boolean hearingDevicesDialogRelatedTools = true;
    private static boolean keyboardDockingIndicator = true;
    private static boolean keyboardShortcutHelperRewrite = false;
    private static boolean keyguardBottomAreaRefactor = true;
    private static boolean keyguardWmStateRefactor = false;
    private static boolean lightRevealMigration = true;
    private static boolean mediaControlsLockscreenShadeBugFix = true;
    private static boolean mediaControlsRefactor = true;
    private static boolean mediaControlsUserInitiatedDeleteintent = true;
    private static boolean migrateClocksToBlueprint = true;
    private static boolean newAodTransition = true;
    private static boolean newTouchpadGesturesTutorial = false;
    private static boolean newVolumePanel = true;
    private static boolean notificationAsyncGroupHeaderInflation = true;
    private static boolean notificationAsyncHybridViewInflation = true;
    private static boolean notificationAvalancheSuppression = true;
    private static boolean notificationAvalancheThrottleHun = true;
    private static boolean notificationBackgroundTintOptimization = true;
    private static boolean notificationColorUpdateLogger = false;
    private static boolean notificationContentAlphaOptimization = true;
    private static boolean notificationFooterBackgroundTintOptimization = false;
    private static boolean notificationMediaManagerBackgroundExecution = true;
    private static boolean notificationMinimalismPrototype = false;
    private static boolean notificationOverExpansionClippingFix = true;
    private static boolean notificationPulsingFix = true;
    private static boolean notificationRowContentBinderRefactor = false;
    private static boolean notificationRowUserContext = true;
    private static boolean notificationViewFlipperPausingV2 = true;
    private static boolean notificationsBackgroundIcons = false;
    private static boolean notificationsFooterViewRefactor = true;
    private static boolean notificationsHeadsUpRefactor = true;
    private static boolean notificationsHideOnDisplaySwitch = false;
    private static boolean notificationsIconContainerRefactor = true;
    private static boolean notificationsImprovedHunAnimation = true;
    private static boolean notificationsLiveDataStoreRefactor = true;
    private static boolean notifyPowerManagerUserActivityBackground = true;
    private static boolean pinInputFieldStyledFocusState = true;
    private static boolean predictiveBackAnimateBouncer = true;
    private static boolean predictiveBackAnimateDialogs = true;
    private static boolean predictiveBackAnimateShade = false;
    private static boolean predictiveBackSysui = true;
    private static boolean priorityPeopleSection = true;
    private static boolean privacyDotUnfoldWrongCornerFix = true;
    private static boolean pssAppSelectorAbruptExitFix = true;
    private static boolean pssAppSelectorRecentsSplitScreen = true;
    private static boolean pssTaskSwitcher = false;
    private static boolean qsCustomTileClickGuaranteedBugFix = true;
    private static boolean qsNewPipeline = true;
    private static boolean qsNewTiles = false;
    private static boolean qsNewTilesFuture = false;
    private static boolean qsTileFocusState = true;
    private static boolean qsUiRefactor = false;
    private static boolean quickSettingsVisualHapticsLongpress = true;
    private static boolean recordIssueQsTile = true;
    private static boolean refactorGetCurrentUser = true;
    private static boolean registerBatteryControllerReceiversInCorestartable = false;
    private static boolean registerNewWalletCardInBackground = true;
    private static boolean registerWallpaperNotifierBackground = true;
    private static boolean registerZenModeContentObserverBackground = true;
    private static boolean removeDreamOverlayHideOnTouch = true;
    private static boolean restToUnlock = false;
    private static boolean restartDreamOnUnocclude = false;
    private static boolean revampedBouncerMessages = true;
    private static boolean runFingerprintDetectOnDismissibleKeyguard = true;
    private static boolean saveAndRestoreMagnificationSettingsButtons = false;
    private static boolean sceneContainer = false;
    private static boolean screenshareNotificationHidingBugFix = true;
    private static boolean screenshotActionDismissSystemWindows = true;
    private static boolean screenshotPrivateProfileAccessibilityAnnouncementFix = true;
    private static boolean screenshotPrivateProfileBehaviorFix = true;
    private static boolean screenshotScrollCropViewCrashFix = true;
    private static boolean screenshotShelfUi2 = true;
    private static boolean shadeCollapseActivityLaunchFix = false;
    private static boolean shaderlibLoadingEffectRefactor = true;
    private static boolean sliceBroadcastRelayInBackground = true;
    private static boolean sliceManagerBinderCallBackground = true;
    private static boolean smartspaceLockscreenViewmodel = true;
    private static boolean smartspaceRelocateToBottom = false;
    private static boolean smartspaceRemoteviewsRendering = false;
    private static boolean statusBarMonochromeIconsFix = true;
    private static boolean statusBarScreenSharingChips = true;
    private static boolean statusBarStaticInoutIndicators = false;
    private static boolean switchUserOnBg = true;
    private static boolean sysuiTeamfood = true;
    private static boolean themeOverlayControllerWakefulnessDeprecation = false;
    private static boolean translucentOccludingActivityFix = false;
    private static boolean truncatedStatusBarIconsFix = true;
    private static boolean udfpsViewPerformance = true;
    private static boolean unfoldAnimationBackgroundProgress = true;
    private static boolean updateUserSwitcherBackground = true;
    private static boolean validateKeyboardShortcutHelperIconUri = true;
    private static boolean visualInterruptionsRefactor = true;


    private void init() {
        boolean foundPackage = true;

        createWindowlessWindowMagnifier = foundPackage;


        delayShowMagnificationButton = foundPackage;


        floatingMenuAnimatedTuck = foundPackage;


        floatingMenuDragToEdit = foundPackage;


        floatingMenuDragToHide = foundPackage;


        floatingMenuImeDisplacementAnimation = foundPackage;


        floatingMenuNarrowTargetContentObserver = foundPackage;


        floatingMenuOverlapsNavBarsFlag = foundPackage;


        floatingMenuRadiiAnimation = foundPackage;


        hearingDevicesDialogRelatedTools = foundPackage;

        saveAndRestoreMagnificationSettingsButtons = foundPackage;
        bpTalkback = foundPackage;
        constraintBp = foundPackage;
        communalHub = foundPackage;
        enableWidgetPickerSizeFilter = foundPackage;
        activityTransitionUseLargestWindow = foundPackage;
        ambientTouchMonitorListenToDisplayChanges = foundPackage;
        appClipsBacklinks = foundPackage;
        bindKeyguardMediaVisibility = foundPackage;
        brightnessSliderFocusState = foundPackage;
        centralizedStatusBarHeightFix = foundPackage;
        clipboardNoninteractiveOnLockscreen = foundPackage;
        clockReactiveVariants = foundPackage;
        communalBouncerDoNotModifyPluginOpen = foundPackage;
        composeBouncer = foundPackage;
        composeLockscreen = foundPackage;
        confineNotificationTouchToViewWidth = foundPackage;
        contextualTipsAssistantDismissFix = foundPackage;
        coroutineTracing = foundPackage;
        dedicatedNotifInflationThread = foundPackage;
        delayedWakelockReleaseOnBackgroundThread = foundPackage;
        deviceEntryUdfpsRefactor = foundPackage;
        disableContextualTipsFrequencyCheck = foundPackage;
        disableContextualTipsIosSwitcherCheck = foundPackage;
        dozeuiSchedulingAlarmsBackgroundExecution = foundPackage;
        dreamInputSessionPilferOnce = foundPackage;
        dreamOverlayBouncerSwipeDirectionFiltering = foundPackage;
        dualShade = foundPackage;
        edgeBackGestureHandlerThread = foundPackage;
        edgebackGestureHandlerGetRunningTasksBackground = foundPackage;
        enableBackgroundKeyguardOndrawnCallback = foundPackage;
        enableContextualTipForMuteVolume = foundPackage;
        enableContextualTipForPowerOff = foundPackage;
        enableContextualTipForTakeScreenshot = foundPackage;
        enableContextualTips = foundPackage;
        enableEfficientDisplayRepository = foundPackage;
        enableLayoutTracing = foundPackage;
        enableViewCaptureTracing = foundPackage;
        enforceBrightnessBaseUserRestriction = foundPackage;
        exampleFlag = foundPackage;
        fastUnlockTransition = foundPackage;
        fixImageWallpaperCrashSurfaceAlreadyReleased = foundPackage;
        fixScreenshotActionDismissSystemWindows = foundPackage;
        getConnectedDeviceNameUnsynchronized = foundPackage;
        glanceableHubAllowKeyguardWhenDreaming = foundPackage;
        glanceableHubFullscreenSwipe = foundPackage;
        glanceableHubGestureHandle = foundPackage;
        glanceableHubShortcutButton = foundPackage;
        hapticBrightnessSlider = foundPackage;
        hapticVolumeSlider = foundPackage;
        hearingAidsQsTileDialog = foundPackage;
        keyboardDockingIndicator = foundPackage;
        keyboardShortcutHelperRewrite = foundPackage;
        keyguardBottomAreaRefactor = foundPackage;
        keyguardWmStateRefactor = foundPackage;
        lightRevealMigration = foundPackage;
        mediaControlsLockscreenShadeBugFix = foundPackage;
        mediaControlsRefactor = foundPackage;
        mediaControlsUserInitiatedDeleteintent = foundPackage;
        migrateClocksToBlueprint = foundPackage;
        newAodTransition = foundPackage;
        newTouchpadGesturesTutorial = foundPackage;
        newVolumePanel = foundPackage;
        notificationAsyncGroupHeaderInflation = foundPackage;
        notificationAsyncHybridViewInflation = foundPackage;
        notificationAvalancheSuppression = foundPackage;
        notificationAvalancheThrottleHun = foundPackage;
        notificationBackgroundTintOptimization = foundPackage;
        notificationColorUpdateLogger = foundPackage;
        notificationContentAlphaOptimization = foundPackage;
        notificationFooterBackgroundTintOptimization = foundPackage;
        notificationMediaManagerBackgroundExecution = foundPackage;
        notificationMinimalismPrototype = foundPackage;
        notificationOverExpansionClippingFix = foundPackage;
        notificationPulsingFix = foundPackage;
        notificationRowContentBinderRefactor = foundPackage;
        notificationRowUserContext = foundPackage;
        notificationViewFlipperPausingV2 = foundPackage;
        notificationsBackgroundIcons = foundPackage;
        notificationsFooterViewRefactor = foundPackage;
        notificationsHeadsUpRefactor = foundPackage;
        notificationsHideOnDisplaySwitch = foundPackage;
        notificationsIconContainerRefactor = foundPackage;
        notificationsImprovedHunAnimation = foundPackage;
        notificationsLiveDataStoreRefactor = foundPackage;
        notifyPowerManagerUserActivityBackground = foundPackage;
        pinInputFieldStyledFocusState = foundPackage;
        predictiveBackAnimateBouncer = foundPackage;
        predictiveBackAnimateDialogs = foundPackage;
        predictiveBackAnimateShade = foundPackage;
        predictiveBackSysui = foundPackage;
        priorityPeopleSection = foundPackage;
        privacyDotUnfoldWrongCornerFix = foundPackage;
        pssAppSelectorAbruptExitFix = foundPackage;
        pssAppSelectorRecentsSplitScreen = foundPackage;
        pssTaskSwitcher = foundPackage;
        qsCustomTileClickGuaranteedBugFix = foundPackage;
        qsNewPipeline = foundPackage;
        qsNewTiles = foundPackage;
        qsNewTilesFuture = foundPackage;
        qsTileFocusState = foundPackage;
        qsUiRefactor = foundPackage;
        quickSettingsVisualHapticsLongpress = foundPackage;
        recordIssueQsTile = foundPackage;
        refactorGetCurrentUser = foundPackage;
        registerBatteryControllerReceiversInCorestartable = foundPackage;
        registerNewWalletCardInBackground = foundPackage;
        registerWallpaperNotifierBackground = foundPackage;
        registerZenModeContentObserverBackground = foundPackage;
        removeDreamOverlayHideOnTouch = foundPackage;
        restToUnlock = foundPackage;
        restartDreamOnUnocclude = foundPackage;
        revampedBouncerMessages = foundPackage;
        runFingerprintDetectOnDismissibleKeyguard = foundPackage;
        sceneContainer = foundPackage;
        screenshareNotificationHidingBugFix = foundPackage;
        screenshotActionDismissSystemWindows = foundPackage;
        screenshotPrivateProfileAccessibilityAnnouncementFix = foundPackage;
        screenshotPrivateProfileBehaviorFix = foundPackage;
        screenshotScrollCropViewCrashFix = foundPackage;
        screenshotShelfUi2 = foundPackage;
        shadeCollapseActivityLaunchFix = foundPackage;
        shaderlibLoadingEffectRefactor = foundPackage;
        sliceBroadcastRelayInBackground = foundPackage;
        sliceManagerBinderCallBackground = foundPackage;
        smartspaceLockscreenViewmodel = foundPackage;
        smartspaceRelocateToBottom = foundPackage;
        smartspaceRemoteviewsRendering = foundPackage;


        statusBarMonochromeIconsFix = foundPackage;


        statusBarScreenSharingChips = foundPackage;


        statusBarStaticInoutIndicators = foundPackage;


        switchUserOnBg = foundPackage;


        sysuiTeamfood = foundPackage;


        themeOverlayControllerWakefulnessDeprecation = foundPackage;


        translucentOccludingActivityFix = foundPackage;


        truncatedStatusBarIconsFix = foundPackage;


        udfpsViewPerformance = foundPackage;


        unfoldAnimationBackgroundProgress = foundPackage;


        updateUserSwitcherBackground = foundPackage;


        validateKeyboardShortcutHelperIconUri = foundPackage;


        visualInterruptionsRefactor = foundPackage;

        isCached = true;
    }




    private void load_overrides_accessibility() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            createWindowlessWindowMagnifier =
                    properties.getBoolean(Flags.FLAG_CREATE_WINDOWLESS_WINDOW_MAGNIFIER, true);
            delayShowMagnificationButton =
                    properties.getBoolean(Flags.FLAG_DELAY_SHOW_MAGNIFICATION_BUTTON, true);
            floatingMenuAnimatedTuck =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_ANIMATED_TUCK, true);
            floatingMenuDragToEdit =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_DRAG_TO_EDIT, true);
            floatingMenuDragToHide =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE, false);
            floatingMenuImeDisplacementAnimation =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_IME_DISPLACEMENT_ANIMATION, true);
            floatingMenuNarrowTargetContentObserver =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_NARROW_TARGET_CONTENT_OBSERVER, true);
            floatingMenuOverlapsNavBarsFlag =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_OVERLAPS_NAV_BARS_FLAG, true);
            floatingMenuRadiiAnimation =
                    properties.getBoolean(Flags.FLAG_FLOATING_MENU_RADII_ANIMATION, true);
            hearingDevicesDialogRelatedTools =
                    properties.getBoolean(Flags.FLAG_HEARING_DEVICES_DIALOG_RELATED_TOOLS, true);
            saveAndRestoreMagnificationSettingsButtons =
                    properties.getBoolean(Flags.FLAG_SAVE_AND_RESTORE_MAGNIFICATION_SETTINGS_BUTTONS, false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace accessibility "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        accessibility_is_cached = true;
    }

    private void load_overrides_biometrics_framework() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            bpTalkback =
                    properties.getBoolean(Flags.FLAG_BP_TALKBACK, true);
            constraintBp =
                    properties.getBoolean(Flags.FLAG_CONSTRAINT_BP, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace biometrics_framework "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        biometrics_framework_is_cached = true;
    }

    private void load_overrides_communal() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            communalHub =
                    properties.getBoolean(Flags.FLAG_COMMUNAL_HUB, true);
            enableWidgetPickerSizeFilter =
                    properties.getBoolean(Flags.FLAG_ENABLE_WIDGET_PICKER_SIZE_FILTER, false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace communal "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        communal_is_cached = true;
    }

    private void load_overrides_systemui() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            activityTransitionUseLargestWindow =
                    properties.getBoolean(Flags.FLAG_ACTIVITY_TRANSITION_USE_LARGEST_WINDOW, true);
            ambientTouchMonitorListenToDisplayChanges =
                    properties.getBoolean(Flags.FLAG_AMBIENT_TOUCH_MONITOR_LISTEN_TO_DISPLAY_CHANGES, false);
            appClipsBacklinks =
                    properties.getBoolean(Flags.FLAG_APP_CLIPS_BACKLINKS, false);
            bindKeyguardMediaVisibility =
                    properties.getBoolean(Flags.FLAG_BIND_KEYGUARD_MEDIA_VISIBILITY, true);
            brightnessSliderFocusState =
                    properties.getBoolean(Flags.FLAG_BRIGHTNESS_SLIDER_FOCUS_STATE, false);
            centralizedStatusBarHeightFix =
                    properties.getBoolean(Flags.FLAG_CENTRALIZED_STATUS_BAR_HEIGHT_FIX, true);
            clipboardNoninteractiveOnLockscreen =
                    properties.getBoolean(Flags.FLAG_CLIPBOARD_NONINTERACTIVE_ON_LOCKSCREEN, false);
            clockReactiveVariants =
                    properties.getBoolean(Flags.FLAG_CLOCK_REACTIVE_VARIANTS, false);
            communalBouncerDoNotModifyPluginOpen =
                    properties.getBoolean(Flags.FLAG_COMMUNAL_BOUNCER_DO_NOT_MODIFY_PLUGIN_OPEN, false);
            composeBouncer =
                    properties.getBoolean(Flags.FLAG_COMPOSE_BOUNCER, false);
            composeLockscreen =
                    properties.getBoolean(Flags.FLAG_COMPOSE_LOCKSCREEN, false);
            confineNotificationTouchToViewWidth =
                    properties.getBoolean(Flags.FLAG_CONFINE_NOTIFICATION_TOUCH_TO_VIEW_WIDTH, true);
            contextualTipsAssistantDismissFix =
                    properties.getBoolean(Flags.FLAG_CONTEXTUAL_TIPS_ASSISTANT_DISMISS_FIX, true);
            coroutineTracing =
                    properties.getBoolean(Flags.FLAG_COROUTINE_TRACING, true);
            dedicatedNotifInflationThread =
                    properties.getBoolean(Flags.FLAG_DEDICATED_NOTIF_INFLATION_THREAD, true);
            delayedWakelockReleaseOnBackgroundThread =
                    properties.getBoolean(Flags.FLAG_DELAYED_WAKELOCK_RELEASE_ON_BACKGROUND_THREAD, true);
            deviceEntryUdfpsRefactor =
                    properties.getBoolean(Flags.FLAG_DEVICE_ENTRY_UDFPS_REFACTOR, true);
            disableContextualTipsFrequencyCheck =
                    properties.getBoolean(Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_FREQUENCY_CHECK, true);
            disableContextualTipsIosSwitcherCheck =
                    properties.getBoolean(Flags.FLAG_DISABLE_CONTEXTUAL_TIPS_IOS_SWITCHER_CHECK, true);
            dozeuiSchedulingAlarmsBackgroundExecution =
                    properties.getBoolean(Flags.FLAG_DOZEUI_SCHEDULING_ALARMS_BACKGROUND_EXECUTION, false);
            dreamInputSessionPilferOnce =
                    properties.getBoolean(Flags.FLAG_DREAM_INPUT_SESSION_PILFER_ONCE, false);
            dreamOverlayBouncerSwipeDirectionFiltering =
                    properties.getBoolean(Flags.FLAG_DREAM_OVERLAY_BOUNCER_SWIPE_DIRECTION_FILTERING, true);
            dualShade =
                    properties.getBoolean(Flags.FLAG_DUAL_SHADE, false);
            edgeBackGestureHandlerThread =
                    properties.getBoolean(Flags.FLAG_EDGE_BACK_GESTURE_HANDLER_THREAD, false);
            edgebackGestureHandlerGetRunningTasksBackground =
                    properties.getBoolean(Flags.FLAG_EDGEBACK_GESTURE_HANDLER_GET_RUNNING_TASKS_BACKGROUND, true);
            enableBackgroundKeyguardOndrawnCallback =
                    properties.getBoolean(Flags.FLAG_ENABLE_BACKGROUND_KEYGUARD_ONDRAWN_CALLBACK, true);
            enableContextualTipForMuteVolume =
                    properties.getBoolean(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_MUTE_VOLUME, false);
            enableContextualTipForPowerOff =
                    properties.getBoolean(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_POWER_OFF, true);
            enableContextualTipForTakeScreenshot =
                    properties.getBoolean(Flags.FLAG_ENABLE_CONTEXTUAL_TIP_FOR_TAKE_SCREENSHOT, true);
            enableContextualTips =
                    properties.getBoolean(Flags.FLAG_ENABLE_CONTEXTUAL_TIPS, true);
            enableEfficientDisplayRepository =
                    properties.getBoolean(Flags.FLAG_ENABLE_EFFICIENT_DISPLAY_REPOSITORY, false);
            enableLayoutTracing =
                    properties.getBoolean(Flags.FLAG_ENABLE_LAYOUT_TRACING, false);
            enableViewCaptureTracing =
                    properties.getBoolean(Flags.FLAG_ENABLE_VIEW_CAPTURE_TRACING, false);
            enforceBrightnessBaseUserRestriction =
                    properties.getBoolean(Flags.FLAG_ENFORCE_BRIGHTNESS_BASE_USER_RESTRICTION, true);
            exampleFlag =
                    properties.getBoolean(Flags.FLAG_EXAMPLE_FLAG, false);
            fastUnlockTransition =
                    properties.getBoolean(Flags.FLAG_FAST_UNLOCK_TRANSITION, false);
            fixImageWallpaperCrashSurfaceAlreadyReleased =
                    properties.getBoolean(Flags.FLAG_FIX_IMAGE_WALLPAPER_CRASH_SURFACE_ALREADY_RELEASED, true);
            fixScreenshotActionDismissSystemWindows =
                    properties.getBoolean(Flags.FLAG_FIX_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS, true);
            getConnectedDeviceNameUnsynchronized =
                    properties.getBoolean(Flags.FLAG_GET_CONNECTED_DEVICE_NAME_UNSYNCHRONIZED, true);
            glanceableHubAllowKeyguardWhenDreaming =
                    properties.getBoolean(Flags.FLAG_GLANCEABLE_HUB_ALLOW_KEYGUARD_WHEN_DREAMING, false);
            glanceableHubFullscreenSwipe =
                    properties.getBoolean(Flags.FLAG_GLANCEABLE_HUB_FULLSCREEN_SWIPE, false);
            glanceableHubGestureHandle =
                    properties.getBoolean(Flags.FLAG_GLANCEABLE_HUB_GESTURE_HANDLE, false);
            glanceableHubShortcutButton =
                    properties.getBoolean(Flags.FLAG_GLANCEABLE_HUB_SHORTCUT_BUTTON, false);
            hapticBrightnessSlider =
                    properties.getBoolean(Flags.FLAG_HAPTIC_BRIGHTNESS_SLIDER, true);
            hapticVolumeSlider =
                    properties.getBoolean(Flags.FLAG_HAPTIC_VOLUME_SLIDER, true);
            hearingAidsQsTileDialog =
                    properties.getBoolean(Flags.FLAG_HEARING_AIDS_QS_TILE_DIALOG, true);
            keyboardDockingIndicator =
                    properties.getBoolean(Flags.FLAG_KEYBOARD_DOCKING_INDICATOR, true);
            keyboardShortcutHelperRewrite =
                    properties.getBoolean(Flags.FLAG_KEYBOARD_SHORTCUT_HELPER_REWRITE, false);
            keyguardBottomAreaRefactor =
                    properties.getBoolean(Flags.FLAG_KEYGUARD_BOTTOM_AREA_REFACTOR, true);
            keyguardWmStateRefactor =
                    properties.getBoolean(Flags.FLAG_KEYGUARD_WM_STATE_REFACTOR, false);
            lightRevealMigration =
                    properties.getBoolean(Flags.FLAG_LIGHT_REVEAL_MIGRATION, true);
            mediaControlsLockscreenShadeBugFix =
                    properties.getBoolean(Flags.FLAG_MEDIA_CONTROLS_LOCKSCREEN_SHADE_BUG_FIX, true);
            mediaControlsRefactor =
                    properties.getBoolean(Flags.FLAG_MEDIA_CONTROLS_REFACTOR, true);
            mediaControlsUserInitiatedDeleteintent =
                    properties.getBoolean(Flags.FLAG_MEDIA_CONTROLS_USER_INITIATED_DELETEINTENT, true);
            migrateClocksToBlueprint =
                    properties.getBoolean(Flags.FLAG_MIGRATE_CLOCKS_TO_BLUEPRINT, true);
            newAodTransition =
                    properties.getBoolean(Flags.FLAG_NEW_AOD_TRANSITION, true);
            newTouchpadGesturesTutorial =
                    properties.getBoolean(Flags.FLAG_NEW_TOUCHPAD_GESTURES_TUTORIAL, false);
            newVolumePanel =
                    properties.getBoolean(Flags.FLAG_NEW_VOLUME_PANEL, true);
            notificationAsyncGroupHeaderInflation =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_ASYNC_GROUP_HEADER_INFLATION, true);
            notificationAsyncHybridViewInflation =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_ASYNC_HYBRID_VIEW_INFLATION, true);
            notificationAvalancheSuppression =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_AVALANCHE_SUPPRESSION, true);
            notificationAvalancheThrottleHun =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_AVALANCHE_THROTTLE_HUN, true);
            notificationBackgroundTintOptimization =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_BACKGROUND_TINT_OPTIMIZATION, true);
            notificationColorUpdateLogger =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_COLOR_UPDATE_LOGGER, false);
            notificationContentAlphaOptimization =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_CONTENT_ALPHA_OPTIMIZATION, true);
            notificationFooterBackgroundTintOptimization =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_FOOTER_BACKGROUND_TINT_OPTIMIZATION, false);
            notificationMediaManagerBackgroundExecution =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_MEDIA_MANAGER_BACKGROUND_EXECUTION, true);
            notificationMinimalismPrototype =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_MINIMALISM_PROTOTYPE, false);
            notificationOverExpansionClippingFix =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_OVER_EXPANSION_CLIPPING_FIX, true);
            notificationPulsingFix =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_PULSING_FIX, true);
            notificationRowContentBinderRefactor =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_ROW_CONTENT_BINDER_REFACTOR, false);
            notificationRowUserContext =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_ROW_USER_CONTEXT, true);
            notificationViewFlipperPausingV2 =
                    properties.getBoolean(Flags.FLAG_NOTIFICATION_VIEW_FLIPPER_PAUSING_V2, true);
            notificationsBackgroundIcons =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_BACKGROUND_ICONS, false);
            notificationsFooterViewRefactor =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_FOOTER_VIEW_REFACTOR, true);
            notificationsHeadsUpRefactor =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_HEADS_UP_REFACTOR, true);
            notificationsHideOnDisplaySwitch =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_HIDE_ON_DISPLAY_SWITCH, false);
            notificationsIconContainerRefactor =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_ICON_CONTAINER_REFACTOR, true);
            notificationsImprovedHunAnimation =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_IMPROVED_HUN_ANIMATION, true);
            notificationsLiveDataStoreRefactor =
                    properties.getBoolean(Flags.FLAG_NOTIFICATIONS_LIVE_DATA_STORE_REFACTOR, true);
            notifyPowerManagerUserActivityBackground =
                    properties.getBoolean(Flags.FLAG_NOTIFY_POWER_MANAGER_USER_ACTIVITY_BACKGROUND, true);
            pinInputFieldStyledFocusState =
                    properties.getBoolean(Flags.FLAG_PIN_INPUT_FIELD_STYLED_FOCUS_STATE, true);
            predictiveBackAnimateBouncer =
                    properties.getBoolean(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_BOUNCER, true);
            predictiveBackAnimateDialogs =
                    properties.getBoolean(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_DIALOGS, true);
            predictiveBackAnimateShade =
                    properties.getBoolean(Flags.FLAG_PREDICTIVE_BACK_ANIMATE_SHADE, false);
            predictiveBackSysui =
                    properties.getBoolean(Flags.FLAG_PREDICTIVE_BACK_SYSUI, true);
            priorityPeopleSection =
                    properties.getBoolean(Flags.FLAG_PRIORITY_PEOPLE_SECTION, true);
            privacyDotUnfoldWrongCornerFix =
                    properties.getBoolean(Flags.FLAG_PRIVACY_DOT_UNFOLD_WRONG_CORNER_FIX, true);
            pssAppSelectorAbruptExitFix =
                    properties.getBoolean(Flags.FLAG_PSS_APP_SELECTOR_ABRUPT_EXIT_FIX, true);
            pssAppSelectorRecentsSplitScreen =
                    properties.getBoolean(Flags.FLAG_PSS_APP_SELECTOR_RECENTS_SPLIT_SCREEN, true);
            pssTaskSwitcher =
                    properties.getBoolean(Flags.FLAG_PSS_TASK_SWITCHER, false);
            qsCustomTileClickGuaranteedBugFix =
                    properties.getBoolean(Flags.FLAG_QS_CUSTOM_TILE_CLICK_GUARANTEED_BUG_FIX, true);
            qsNewPipeline =
                    properties.getBoolean(Flags.FLAG_QS_NEW_PIPELINE, true);
            qsNewTiles =
                    properties.getBoolean(Flags.FLAG_QS_NEW_TILES, false);
            qsNewTilesFuture =
                    properties.getBoolean(Flags.FLAG_QS_NEW_TILES_FUTURE, false);
            qsTileFocusState =
                    properties.getBoolean(Flags.FLAG_QS_TILE_FOCUS_STATE, true);
            qsUiRefactor =
                    properties.getBoolean(Flags.FLAG_QS_UI_REFACTOR, false);
            quickSettingsVisualHapticsLongpress =
                    properties.getBoolean(Flags.FLAG_QUICK_SETTINGS_VISUAL_HAPTICS_LONGPRESS, true);
            recordIssueQsTile =
                    properties.getBoolean(Flags.FLAG_RECORD_ISSUE_QS_TILE, true);
            refactorGetCurrentUser =
                    properties.getBoolean(Flags.FLAG_REFACTOR_GET_CURRENT_USER, true);
            registerBatteryControllerReceiversInCorestartable =
                    properties.getBoolean(Flags.FLAG_REGISTER_BATTERY_CONTROLLER_RECEIVERS_IN_CORESTARTABLE, false);
            registerNewWalletCardInBackground =
                    properties.getBoolean(Flags.FLAG_REGISTER_NEW_WALLET_CARD_IN_BACKGROUND, true);
            registerWallpaperNotifierBackground =
                    properties.getBoolean(Flags.FLAG_REGISTER_WALLPAPER_NOTIFIER_BACKGROUND, true);
            registerZenModeContentObserverBackground =
                    properties.getBoolean(Flags.FLAG_REGISTER_ZEN_MODE_CONTENT_OBSERVER_BACKGROUND, true);
            removeDreamOverlayHideOnTouch =
                    properties.getBoolean(Flags.FLAG_REMOVE_DREAM_OVERLAY_HIDE_ON_TOUCH, true);
            restToUnlock =
                    properties.getBoolean(Flags.FLAG_REST_TO_UNLOCK, false);
            restartDreamOnUnocclude =
                    properties.getBoolean(Flags.FLAG_RESTART_DREAM_ON_UNOCCLUDE, false);
            revampedBouncerMessages =
                    properties.getBoolean(Flags.FLAG_REVAMPED_BOUNCER_MESSAGES, true);
            runFingerprintDetectOnDismissibleKeyguard =
                    properties.getBoolean(Flags.FLAG_RUN_FINGERPRINT_DETECT_ON_DISMISSIBLE_KEYGUARD, true);
            sceneContainer =
                    properties.getBoolean(Flags.FLAG_SCENE_CONTAINER, false);
            screenshareNotificationHidingBugFix =
                    properties.getBoolean(Flags.FLAG_SCREENSHARE_NOTIFICATION_HIDING_BUG_FIX, true);
            screenshotActionDismissSystemWindows =
                    properties.getBoolean(Flags.FLAG_SCREENSHOT_ACTION_DISMISS_SYSTEM_WINDOWS, true);
            screenshotPrivateProfileAccessibilityAnnouncementFix =
                    properties.getBoolean(Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_ACCESSIBILITY_ANNOUNCEMENT_FIX, true);
            screenshotPrivateProfileBehaviorFix =
                    properties.getBoolean(Flags.FLAG_SCREENSHOT_PRIVATE_PROFILE_BEHAVIOR_FIX, true);
            screenshotScrollCropViewCrashFix =
                    properties.getBoolean(Flags.FLAG_SCREENSHOT_SCROLL_CROP_VIEW_CRASH_FIX, true);
            screenshotShelfUi2 =
                    properties.getBoolean(Flags.FLAG_SCREENSHOT_SHELF_UI2, true);
            shadeCollapseActivityLaunchFix =
                    properties.getBoolean(Flags.FLAG_SHADE_COLLAPSE_ACTIVITY_LAUNCH_FIX, false);
            shaderlibLoadingEffectRefactor =
                    properties.getBoolean(Flags.FLAG_SHADERLIB_LOADING_EFFECT_REFACTOR, true);
            sliceBroadcastRelayInBackground =
                    properties.getBoolean(Flags.FLAG_SLICE_BROADCAST_RELAY_IN_BACKGROUND, true);
            sliceManagerBinderCallBackground =
                    properties.getBoolean(Flags.FLAG_SLICE_MANAGER_BINDER_CALL_BACKGROUND, true);
            smartspaceLockscreenViewmodel =
                    properties.getBoolean(Flags.FLAG_SMARTSPACE_LOCKSCREEN_VIEWMODEL, true);
            smartspaceRelocateToBottom =
                    properties.getBoolean(Flags.FLAG_SMARTSPACE_RELOCATE_TO_BOTTOM, false);
            smartspaceRemoteviewsRendering =
                    properties.getBoolean(Flags.FLAG_SMARTSPACE_REMOTEVIEWS_RENDERING, false);
            statusBarMonochromeIconsFix =
                    properties.getBoolean(Flags.FLAG_STATUS_BAR_MONOCHROME_ICONS_FIX, true);
            statusBarScreenSharingChips =
                    properties.getBoolean(Flags.FLAG_STATUS_BAR_SCREEN_SHARING_CHIPS, true);
            statusBarStaticInoutIndicators =
                    properties.getBoolean(Flags.FLAG_STATUS_BAR_STATIC_INOUT_INDICATORS, false);
            switchUserOnBg =
                    properties.getBoolean(Flags.FLAG_SWITCH_USER_ON_BG, true);
            sysuiTeamfood =
                    properties.getBoolean(Flags.FLAG_SYSUI_TEAMFOOD, true);
            themeOverlayControllerWakefulnessDeprecation =
                    properties.getBoolean(Flags.FLAG_THEME_OVERLAY_CONTROLLER_WAKEFULNESS_DEPRECATION, false);
            translucentOccludingActivityFix =
                    properties.getBoolean(Flags.FLAG_TRANSLUCENT_OCCLUDING_ACTIVITY_FIX, false);
            truncatedStatusBarIconsFix =
                    properties.getBoolean(Flags.FLAG_TRUNCATED_STATUS_BAR_ICONS_FIX, true);
            udfpsViewPerformance =
                    properties.getBoolean(Flags.FLAG_UDFPS_VIEW_PERFORMANCE, true);
            unfoldAnimationBackgroundProgress =
                    properties.getBoolean(Flags.FLAG_UNFOLD_ANIMATION_BACKGROUND_PROGRESS, true);
            updateUserSwitcherBackground =
                    properties.getBoolean(Flags.FLAG_UPDATE_USER_SWITCHER_BACKGROUND, true);
            validateKeyboardShortcutHelperIconUri =
                    properties.getBoolean(Flags.FLAG_VALIDATE_KEYBOARD_SHORTCUT_HELPER_ICON_URI, true);
            visualInterruptionsRefactor =
                    properties.getBoolean(Flags.FLAG_VISUAL_INTERRUPTIONS_REFACTOR, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace systemui "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        }
        systemui_is_cached = true;
    }

    @Override
    
    public boolean activityTransitionUseLargestWindow() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return activityTransitionUseLargestWindow;

    }

    @Override
    
    public boolean ambientTouchMonitorListenToDisplayChanges() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return ambientTouchMonitorListenToDisplayChanges;

    }

    @Override
    
    public boolean appClipsBacklinks() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return appClipsBacklinks;

    }

    @Override
    
    public boolean bindKeyguardMediaVisibility() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return bindKeyguardMediaVisibility;

    }

    @Override
    
    public boolean bpTalkback() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!biometrics_framework_is_cached) {
                load_overrides_biometrics_framework();
            }
        }
        return bpTalkback;

    }

    @Override
    
    public boolean brightnessSliderFocusState() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return brightnessSliderFocusState;

    }

    @Override
    
    public boolean centralizedStatusBarHeightFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return centralizedStatusBarHeightFix;

    }

    @Override
    
    public boolean clipboardNoninteractiveOnLockscreen() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return clipboardNoninteractiveOnLockscreen;

    }

    @Override
    
    public boolean clockReactiveVariants() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return clockReactiveVariants;

    }

    @Override
    
    public boolean communalBouncerDoNotModifyPluginOpen() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return communalBouncerDoNotModifyPluginOpen;

    }

    @Override
    
    public boolean communalHub() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!communal_is_cached) {
                load_overrides_communal();
            }
        }
        return communalHub;

    }

    @Override
    
    public boolean composeBouncer() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return composeBouncer;

    }

    @Override
    
    public boolean composeLockscreen() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return composeLockscreen;

    }

    @Override
    
    public boolean confineNotificationTouchToViewWidth() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return confineNotificationTouchToViewWidth;

    }

    @Override
    
    public boolean constraintBp() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!biometrics_framework_is_cached) {
                load_overrides_biometrics_framework();
            }
        }
        return constraintBp;

    }

    @Override
    
    public boolean contextualTipsAssistantDismissFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return contextualTipsAssistantDismissFix;

    }

    @Override
    
    public boolean coroutineTracing() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return coroutineTracing;

    }

    @Override
    
    public boolean createWindowlessWindowMagnifier() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return createWindowlessWindowMagnifier;

    }

    @Override
    
    public boolean dedicatedNotifInflationThread() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return dedicatedNotifInflationThread;

    }

    @Override
    
    public boolean delayShowMagnificationButton() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return delayShowMagnificationButton;

    }

    @Override
    
    public boolean delayedWakelockReleaseOnBackgroundThread() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return delayedWakelockReleaseOnBackgroundThread;

    }

    @Override
    
    public boolean deviceEntryUdfpsRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return deviceEntryUdfpsRefactor;

    }

    @Override
    
    public boolean disableContextualTipsFrequencyCheck() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return disableContextualTipsFrequencyCheck;

    }

    @Override
    
    public boolean disableContextualTipsIosSwitcherCheck() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return disableContextualTipsIosSwitcherCheck;

    }

    @Override
    
    public boolean dozeuiSchedulingAlarmsBackgroundExecution() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return dozeuiSchedulingAlarmsBackgroundExecution;

    }

    @Override
    
    public boolean dreamInputSessionPilferOnce() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return dreamInputSessionPilferOnce;

    }

    @Override
    
    public boolean dreamOverlayBouncerSwipeDirectionFiltering() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return dreamOverlayBouncerSwipeDirectionFiltering;

    }

    @Override
    
    public boolean dualShade() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return dualShade;

    }

    @Override
    
    public boolean edgeBackGestureHandlerThread() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return edgeBackGestureHandlerThread;

    }

    @Override
    
    public boolean edgebackGestureHandlerGetRunningTasksBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return edgebackGestureHandlerGetRunningTasksBackground;

    }

    @Override
    
    public boolean enableBackgroundKeyguardOndrawnCallback() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableBackgroundKeyguardOndrawnCallback;

    }

    @Override
    
    public boolean enableContextualTipForMuteVolume() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableContextualTipForMuteVolume;

    }

    @Override
    
    public boolean enableContextualTipForPowerOff() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableContextualTipForPowerOff;

    }

    @Override
    
    public boolean enableContextualTipForTakeScreenshot() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableContextualTipForTakeScreenshot;

    }

    @Override
    
    public boolean enableContextualTips() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableContextualTips;

    }

    @Override
    
    public boolean enableEfficientDisplayRepository() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableEfficientDisplayRepository;

    }

    @Override
    
    public boolean enableLayoutTracing() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableLayoutTracing;

    }

    @Override
    
    public boolean enableViewCaptureTracing() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enableViewCaptureTracing;

    }

    @Override
    
    public boolean enableWidgetPickerSizeFilter() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!communal_is_cached) {
                load_overrides_communal();
            }
        }
        return enableWidgetPickerSizeFilter;

    }

    @Override
    
    public boolean enforceBrightnessBaseUserRestriction() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return enforceBrightnessBaseUserRestriction;

    }

    @Override
    
    public boolean exampleFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return exampleFlag;

    }

    @Override
    
    public boolean fastUnlockTransition() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return fastUnlockTransition;

    }

    @Override
    
    public boolean fixImageWallpaperCrashSurfaceAlreadyReleased() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return fixImageWallpaperCrashSurfaceAlreadyReleased;

    }

    @Override
    
    public boolean fixScreenshotActionDismissSystemWindows() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return fixScreenshotActionDismissSystemWindows;

    }

    @Override
    
    public boolean floatingMenuAnimatedTuck() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuAnimatedTuck;

    }

    @Override
    
    public boolean floatingMenuDragToEdit() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuDragToEdit;

    }

    @Override
    
    public boolean floatingMenuDragToHide() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuDragToHide;

    }

    @Override
    
    public boolean floatingMenuImeDisplacementAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuImeDisplacementAnimation;

    }

    @Override
    
    public boolean floatingMenuNarrowTargetContentObserver() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuNarrowTargetContentObserver;

    }

    @Override
    
    public boolean floatingMenuOverlapsNavBarsFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuOverlapsNavBarsFlag;

    }

    @Override
    
    public boolean floatingMenuRadiiAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return floatingMenuRadiiAnimation;

    }

    @Override
    
    public boolean getConnectedDeviceNameUnsynchronized() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return getConnectedDeviceNameUnsynchronized;

    }

    @Override
    
    public boolean glanceableHubAllowKeyguardWhenDreaming() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return glanceableHubAllowKeyguardWhenDreaming;

    }

    @Override
    
    public boolean glanceableHubFullscreenSwipe() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return glanceableHubFullscreenSwipe;

    }

    @Override
    
    public boolean glanceableHubGestureHandle() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return glanceableHubGestureHandle;

    }

    @Override
    
    public boolean glanceableHubShortcutButton() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return glanceableHubShortcutButton;

    }

    @Override
    
    public boolean hapticBrightnessSlider() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return hapticBrightnessSlider;

    }

    @Override
    
    public boolean hapticVolumeSlider() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return hapticVolumeSlider;

    }

    @Override
    
    public boolean hearingAidsQsTileDialog() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return hearingAidsQsTileDialog;

    }

    @Override
    
    public boolean hearingDevicesDialogRelatedTools() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return hearingDevicesDialogRelatedTools;

    }

    @Override
    
    public boolean keyboardDockingIndicator() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return keyboardDockingIndicator;

    }

    @Override
    
    public boolean keyboardShortcutHelperRewrite() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return keyboardShortcutHelperRewrite;

    }

    @Override
    
    public boolean keyguardBottomAreaRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return keyguardBottomAreaRefactor;

    }

    @Override
    
    public boolean keyguardWmStateRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return keyguardWmStateRefactor;

    }

    @Override
    
    public boolean lightRevealMigration() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return lightRevealMigration;

    }

    @Override
    
    public boolean mediaControlsLockscreenShadeBugFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return mediaControlsLockscreenShadeBugFix;

    }

    @Override
    
    public boolean mediaControlsRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return mediaControlsRefactor;

    }

    @Override
    
    public boolean mediaControlsUserInitiatedDeleteintent() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return mediaControlsUserInitiatedDeleteintent;

    }

    @Override
    
    public boolean migrateClocksToBlueprint() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return migrateClocksToBlueprint;

    }

    @Override
    
    public boolean newAodTransition() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return newAodTransition;

    }

    @Override
    
    public boolean newTouchpadGesturesTutorial() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return newTouchpadGesturesTutorial;

    }

    @Override
    
    public boolean newVolumePanel() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return newVolumePanel;

    }

    @Override
    
    public boolean notificationAsyncGroupHeaderInflation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationAsyncGroupHeaderInflation;

    }

    @Override
    
    public boolean notificationAsyncHybridViewInflation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationAsyncHybridViewInflation;

    }

    @Override
    
    public boolean notificationAvalancheSuppression() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationAvalancheSuppression;

    }

    @Override
    
    public boolean notificationAvalancheThrottleHun() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationAvalancheThrottleHun;

    }

    @Override
    
    public boolean notificationBackgroundTintOptimization() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationBackgroundTintOptimization;

    }

    @Override
    
    public boolean notificationColorUpdateLogger() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationColorUpdateLogger;

    }

    @Override
    
    public boolean notificationContentAlphaOptimization() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationContentAlphaOptimization;

    }

    @Override
    
    public boolean notificationFooterBackgroundTintOptimization() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationFooterBackgroundTintOptimization;

    }

    @Override
    
    public boolean notificationMediaManagerBackgroundExecution() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationMediaManagerBackgroundExecution;

    }

    @Override
    
    public boolean notificationMinimalismPrototype() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationMinimalismPrototype;

    }

    @Override
    
    public boolean notificationOverExpansionClippingFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationOverExpansionClippingFix;

    }

    @Override
    
    public boolean notificationPulsingFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationPulsingFix;

    }

    @Override
    
    public boolean notificationRowContentBinderRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationRowContentBinderRefactor;

    }

    @Override
    
    public boolean notificationRowUserContext() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationRowUserContext;

    }

    @Override
    
    public boolean notificationViewFlipperPausingV2() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationViewFlipperPausingV2;

    }

    @Override
    
    public boolean notificationsBackgroundIcons() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsBackgroundIcons;

    }

    @Override
    
    public boolean notificationsFooterViewRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsFooterViewRefactor;

    }

    @Override
    
    public boolean notificationsHeadsUpRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsHeadsUpRefactor;

    }

    @Override
    
    public boolean notificationsHideOnDisplaySwitch() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsHideOnDisplaySwitch;

    }

    @Override
    
    public boolean notificationsIconContainerRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsIconContainerRefactor;

    }

    @Override
    
    public boolean notificationsImprovedHunAnimation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsImprovedHunAnimation;

    }

    @Override
    
    public boolean notificationsLiveDataStoreRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notificationsLiveDataStoreRefactor;

    }

    @Override
    
    public boolean notifyPowerManagerUserActivityBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return notifyPowerManagerUserActivityBackground;

    }

    @Override
    
    public boolean pinInputFieldStyledFocusState() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return pinInputFieldStyledFocusState;

    }

    @Override
    
    public boolean predictiveBackAnimateBouncer() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return predictiveBackAnimateBouncer;

    }

    @Override
    
    public boolean predictiveBackAnimateDialogs() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return predictiveBackAnimateDialogs;

    }

    @Override
    
    public boolean predictiveBackAnimateShade() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return predictiveBackAnimateShade;

    }

    @Override
    
    public boolean predictiveBackSysui() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return predictiveBackSysui;

    }

    @Override
    
    public boolean priorityPeopleSection() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return priorityPeopleSection;

    }

    @Override
    
    public boolean privacyDotUnfoldWrongCornerFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return privacyDotUnfoldWrongCornerFix;

    }

    @Override
    
    public boolean pssAppSelectorAbruptExitFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return pssAppSelectorAbruptExitFix;

    }

    @Override
    
    public boolean pssAppSelectorRecentsSplitScreen() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return pssAppSelectorRecentsSplitScreen;

    }

    @Override
    
    public boolean pssTaskSwitcher() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return pssTaskSwitcher;

    }

    @Override
    
    public boolean qsCustomTileClickGuaranteedBugFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsCustomTileClickGuaranteedBugFix;

    }

    @Override
    
    public boolean qsNewPipeline() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsNewPipeline;

    }

    @Override
    
    public boolean qsNewTiles() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsNewTiles;

    }

    @Override
    
    public boolean qsNewTilesFuture() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsNewTilesFuture;

    }

    @Override
    
    public boolean qsTileFocusState() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsTileFocusState;

    }

    @Override
    
    public boolean qsUiRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return qsUiRefactor;

    }

    @Override
    
    public boolean quickSettingsVisualHapticsLongpress() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return quickSettingsVisualHapticsLongpress;

    }

    @Override
    
    public boolean recordIssueQsTile() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return recordIssueQsTile;

    }

    @Override
    
    public boolean refactorGetCurrentUser() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return refactorGetCurrentUser;

    }

    @Override
    
    public boolean registerBatteryControllerReceiversInCorestartable() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return registerBatteryControllerReceiversInCorestartable;

    }

    @Override
    
    public boolean registerNewWalletCardInBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return registerNewWalletCardInBackground;

    }

    @Override
    
    public boolean registerWallpaperNotifierBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return registerWallpaperNotifierBackground;

    }

    @Override
    
    public boolean registerZenModeContentObserverBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return registerZenModeContentObserverBackground;

    }

    @Override
    
    public boolean removeDreamOverlayHideOnTouch() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return removeDreamOverlayHideOnTouch;

    }

    @Override
    
    public boolean restToUnlock() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return restToUnlock;

    }

    @Override
    
    public boolean restartDreamOnUnocclude() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return restartDreamOnUnocclude;

    }

    @Override
    
    public boolean revampedBouncerMessages() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return revampedBouncerMessages;

    }

    @Override
    
    public boolean runFingerprintDetectOnDismissibleKeyguard() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return runFingerprintDetectOnDismissibleKeyguard;

    }

    @Override
    
    public boolean saveAndRestoreMagnificationSettingsButtons() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return saveAndRestoreMagnificationSettingsButtons;

    }

    @Override
    
    public boolean sceneContainer() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return sceneContainer;

    }

    @Override
    
    public boolean screenshareNotificationHidingBugFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshareNotificationHidingBugFix;

    }

    @Override
    
    public boolean screenshotActionDismissSystemWindows() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshotActionDismissSystemWindows;

    }

    @Override
    
    public boolean screenshotPrivateProfileAccessibilityAnnouncementFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshotPrivateProfileAccessibilityAnnouncementFix;

    }

    @Override
    
    public boolean screenshotPrivateProfileBehaviorFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshotPrivateProfileBehaviorFix;

    }

    @Override
    
    public boolean screenshotScrollCropViewCrashFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshotScrollCropViewCrashFix;

    }

    @Override
    
    public boolean screenshotShelfUi2() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return screenshotShelfUi2;

    }

    @Override
    
    public boolean shadeCollapseActivityLaunchFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return shadeCollapseActivityLaunchFix;

    }

    @Override
    
    public boolean shaderlibLoadingEffectRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return shaderlibLoadingEffectRefactor;

    }

    @Override
    
    public boolean sliceBroadcastRelayInBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return sliceBroadcastRelayInBackground;

    }

    @Override
    
    public boolean sliceManagerBinderCallBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return sliceManagerBinderCallBackground;

    }

    @Override
    
    public boolean smartspaceLockscreenViewmodel() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return smartspaceLockscreenViewmodel;

    }

    @Override
    
    public boolean smartspaceRelocateToBottom() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return smartspaceRelocateToBottom;

    }

    @Override
    
    public boolean smartspaceRemoteviewsRendering() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return smartspaceRemoteviewsRendering;

    }

    @Override
    
    public boolean statusBarMonochromeIconsFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return statusBarMonochromeIconsFix;

    }

    @Override
    
    public boolean statusBarScreenSharingChips() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return statusBarScreenSharingChips;

    }

    @Override
    
    public boolean statusBarStaticInoutIndicators() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return statusBarStaticInoutIndicators;

    }

    @Override
    
    public boolean switchUserOnBg() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return switchUserOnBg;

    }

    @Override
    
    public boolean sysuiTeamfood() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return sysuiTeamfood;

    }

    @Override
    
    public boolean themeOverlayControllerWakefulnessDeprecation() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return themeOverlayControllerWakefulnessDeprecation;

    }

    @Override
    
    public boolean translucentOccludingActivityFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return translucentOccludingActivityFix;

    }

    @Override
    
    public boolean truncatedStatusBarIconsFix() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return truncatedStatusBarIconsFix;

    }

    @Override
    
    public boolean udfpsViewPerformance() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return udfpsViewPerformance;

    }

    @Override
    
    public boolean unfoldAnimationBackgroundProgress() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return unfoldAnimationBackgroundProgress;

    }

    @Override
    
    public boolean updateUserSwitcherBackground() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return updateUserSwitcherBackground;

    }

    @Override
    
    public boolean validateKeyboardShortcutHelperIconUri() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return validateKeyboardShortcutHelperIconUri;

    }

    @Override
    
    public boolean visualInterruptionsRefactor() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return visualInterruptionsRefactor;

    }

}

