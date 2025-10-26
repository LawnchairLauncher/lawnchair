package com.android.window.flags2;

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

    public boolean actionModeEdgeToEdge() {
        return getValue(Flags.FLAG_ACTION_MODE_EDGE_TO_EDGE,
            FeatureFlags::actionModeEdgeToEdge);
    }

    @Override

    public boolean activityEmbeddingAnimationCustomizationFlag() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG,
            FeatureFlags::activityEmbeddingAnimationCustomizationFlag);
    }

    @Override

    public boolean activityEmbeddingDelayTaskFragmentFinishForActivityLaunch() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_DELAY_TASK_FRAGMENT_FINISH_FOR_ACTIVITY_LAUNCH,
            FeatureFlags::activityEmbeddingDelayTaskFragmentFinishForActivityLaunch);
    }

    @Override

    public boolean activityEmbeddingInteractiveDividerFlag() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
            FeatureFlags::activityEmbeddingInteractiveDividerFlag);
    }

    @Override

    public boolean activityEmbeddingMetrics() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_METRICS,
            FeatureFlags::activityEmbeddingMetrics);
    }

    @Override

    public boolean activityEmbeddingSupportForConnectedDisplays() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::activityEmbeddingSupportForConnectedDisplays);
    }

    @Override

    public boolean allowDisableActivityRecordInputSink() {
        return getValue(Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            FeatureFlags::allowDisableActivityRecordInputSink);
    }

    @Override

    public boolean allowHideScmButton() {
        return getValue(Flags.FLAG_ALLOW_HIDE_SCM_BUTTON,
            FeatureFlags::allowHideScmButton);
    }

    @Override

    public boolean allowsScreenSizeDecoupledFromStatusBarAndCutout() {
        return getValue(Flags.FLAG_ALLOWS_SCREEN_SIZE_DECOUPLED_FROM_STATUS_BAR_AND_CUTOUT,
            FeatureFlags::allowsScreenSizeDecoupledFromStatusBarAndCutout);
    }

    @Override

    public boolean alwaysDrawMagnificationFullscreenBorder() {
        return getValue(Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            FeatureFlags::alwaysDrawMagnificationFullscreenBorder);
    }

    @Override

    public boolean alwaysUpdateWallpaperPermission() {
        return getValue(Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
            FeatureFlags::alwaysUpdateWallpaperPermission);
    }

    @Override

    public boolean aodTransition() {
        return getValue(Flags.FLAG_AOD_TRANSITION,
            FeatureFlags::aodTransition);
    }

    @Override

    public boolean appCompatAsyncRelayout() {
        return getValue(Flags.FLAG_APP_COMPAT_ASYNC_RELAYOUT,
            FeatureFlags::appCompatAsyncRelayout);
    }

    @Override

    public boolean appCompatPropertiesApi() {
        return getValue(Flags.FLAG_APP_COMPAT_PROPERTIES_API,
            FeatureFlags::appCompatPropertiesApi);
    }

    @Override

    public boolean appCompatRefactoring() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING,
            FeatureFlags::appCompatRefactoring);
    }

    @Override

    public boolean appCompatUiFramework() {
        return getValue(Flags.FLAG_APP_COMPAT_UI_FRAMEWORK,
            FeatureFlags::appCompatUiFramework);
    }

    @Override

    public boolean appHandleNoRelayoutOnExclusionChange() {
        return getValue(Flags.FLAG_APP_HANDLE_NO_RELAYOUT_ON_EXCLUSION_CHANGE,
            FeatureFlags::appHandleNoRelayoutOnExclusionChange);
    }

    @Override

    public boolean applyLifecycleOnPipChange() {
        return getValue(Flags.FLAG_APPLY_LIFECYCLE_ON_PIP_CHANGE,
            FeatureFlags::applyLifecycleOnPipChange);
    }

    @Override

    public boolean avoidRebindingIntentionallyDisconnectedWallpaper() {
        return getValue(Flags.FLAG_AVOID_REBINDING_INTENTIONALLY_DISCONNECTED_WALLPAPER,
            FeatureFlags::avoidRebindingIntentionallyDisconnectedWallpaper);
    }

    @Override

    public boolean backupAndRestoreForUserAspectRatioSettings() {
        return getValue(Flags.FLAG_BACKUP_AND_RESTORE_FOR_USER_ASPECT_RATIO_SETTINGS,
            FeatureFlags::backupAndRestoreForUserAspectRatioSettings);
    }

    @Override

    public boolean balAdditionalLogging() {
        return getValue(Flags.FLAG_BAL_ADDITIONAL_LOGGING,
            FeatureFlags::balAdditionalLogging);
    }

    @Override

    public boolean balAdditionalStartModes() {
        return getValue(Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            FeatureFlags::balAdditionalStartModes);
    }

    @Override

    public boolean balClearAllowlistDuration() {
        return getValue(Flags.FLAG_BAL_CLEAR_ALLOWLIST_DURATION,
            FeatureFlags::balClearAllowlistDuration);
    }

    @Override

    public boolean balDontBringExistingBackgroundTaskStackToFg() {
        return getValue(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            FeatureFlags::balDontBringExistingBackgroundTaskStackToFg);
    }

    @Override

    public boolean balImproveRealCallerVisibilityCheck() {
        return getValue(Flags.FLAG_BAL_IMPROVE_REAL_CALLER_VISIBILITY_CHECK,
            FeatureFlags::balImproveRealCallerVisibilityCheck);
    }

    @Override

    public boolean balImprovedMetrics() {
        return getValue(Flags.FLAG_BAL_IMPROVED_METRICS,
            FeatureFlags::balImprovedMetrics);
    }

    @Override

    public boolean balReduceGracePeriod() {
        return getValue(Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            FeatureFlags::balReduceGracePeriod);
    }

    @Override

    public boolean balRequireOptInByPendingIntentCreator() {
        return getValue(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
            FeatureFlags::balRequireOptInByPendingIntentCreator);
    }

    @Override

    public boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid() {
        return getValue(Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
            FeatureFlags::balRespectAppSwitchStateWhenCheckBoundByForegroundUid);
    }

    @Override

    public boolean balSendIntentWithOptions() {
        return getValue(Flags.FLAG_BAL_SEND_INTENT_WITH_OPTIONS,
            FeatureFlags::balSendIntentWithOptions);
    }

    @Override

    public boolean balShowToastsBlocked() {
        return getValue(Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED,
            FeatureFlags::balShowToastsBlocked);
    }

    @Override

    public boolean balStrictModeGracePeriod() {
        return getValue(Flags.FLAG_BAL_STRICT_MODE_GRACE_PERIOD,
            FeatureFlags::balStrictModeGracePeriod);
    }

    @Override

    public boolean balStrictModeRo() {
        return getValue(Flags.FLAG_BAL_STRICT_MODE_RO,
            FeatureFlags::balStrictModeRo);
    }

    @Override

    public boolean betterSupportNonMatchParentActivity() {
        return getValue(Flags.FLAG_BETTER_SUPPORT_NON_MATCH_PARENT_ACTIVITY,
            FeatureFlags::betterSupportNonMatchParentActivity);
    }

    @Override

    public boolean cacheWindowStyle() {
        return getValue(Flags.FLAG_CACHE_WINDOW_STYLE,
            FeatureFlags::cacheWindowStyle);
    }

    @Override

    public boolean cameraCompatForFreeform() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
            FeatureFlags::cameraCompatForFreeform);
    }

    @Override

    public boolean cameraCompatFullscreenPickSameTaskActivity() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            FeatureFlags::cameraCompatFullscreenPickSameTaskActivity);
    }

    @Override

    public boolean checkDisabledSnapshotsInTaskPersister() {
        return getValue(Flags.FLAG_CHECK_DISABLED_SNAPSHOTS_IN_TASK_PERSISTER,
            FeatureFlags::checkDisabledSnapshotsInTaskPersister);
    }

    @Override

    public boolean cleanupDispatchPendingTransactionsRemoteException() {
        return getValue(Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION,
            FeatureFlags::cleanupDispatchPendingTransactionsRemoteException);
    }

    @Override

    public boolean clearSystemVibrator() {
        return getValue(Flags.FLAG_CLEAR_SYSTEM_VIBRATOR,
            FeatureFlags::clearSystemVibrator);
    }

    @Override

    public boolean closeToSquareConfigIncludesStatusBar() {
        return getValue(Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            FeatureFlags::closeToSquareConfigIncludesStatusBar);
    }

    @Override

    public boolean condenseConfigurationChangeForSimpleMode() {
        return getValue(Flags.FLAG_CONDENSE_CONFIGURATION_CHANGE_FOR_SIMPLE_MODE,
            FeatureFlags::condenseConfigurationChangeForSimpleMode);
    }

    @Override

    public boolean configurableFontScaleDefault() {
        return getValue(Flags.FLAG_CONFIGURABLE_FONT_SCALE_DEFAULT,
            FeatureFlags::configurableFontScaleDefault);
    }

    @Override

    public boolean coverDisplayOptIn() {
        return getValue(Flags.FLAG_COVER_DISPLAY_OPT_IN,
            FeatureFlags::coverDisplayOptIn);
    }

    @Override

    public boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition() {
        return getValue(Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION,
            FeatureFlags::delayNotificationToMagnificationWhenRecentsWindowToFrontTransition);
    }

    @Override

    public boolean delegateBackGestureToShell() {
        return getValue(Flags.FLAG_DELEGATE_BACK_GESTURE_TO_SHELL,
            FeatureFlags::delegateBackGestureToShell);
    }

    @Override

    public boolean delegateUnhandledDrags() {
        return getValue(Flags.FLAG_DELEGATE_UNHANDLED_DRAGS,
            FeatureFlags::delegateUnhandledDrags);
    }

    @Override

    public boolean deleteCaptureDisplay() {
        return getValue(Flags.FLAG_DELETE_CAPTURE_DISPLAY,
            FeatureFlags::deleteCaptureDisplay);
    }

    @Override

    public boolean density390Api() {
        return getValue(Flags.FLAG_DENSITY_390_API,
            FeatureFlags::density390Api);
    }

    @Override

    public boolean disableDesktopLaunchParamsOutsideDesktopBugFix() {
        return getValue(Flags.FLAG_DISABLE_DESKTOP_LAUNCH_PARAMS_OUTSIDE_DESKTOP_BUG_FIX,
            FeatureFlags::disableDesktopLaunchParamsOutsideDesktopBugFix);
    }

    @Override

    public boolean disableNonResizableAppSnapResizing() {
        return getValue(Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING,
            FeatureFlags::disableNonResizableAppSnapResizing);
    }

    @Override

    public boolean disableOptOutEdgeToEdge() {
        return getValue(Flags.FLAG_DISABLE_OPT_OUT_EDGE_TO_EDGE,
            FeatureFlags::disableOptOutEdgeToEdge);
    }

    @Override

    public boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions() {
        return getValue(Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS,
            FeatureFlags::doNotCheckIntersectionWhenNonMagnifiableWindowTransitions);
    }

    @Override

    public boolean earlyLaunchHint() {
        return getValue(Flags.FLAG_EARLY_LAUNCH_HINT,
            FeatureFlags::earlyLaunchHint);
    }

    @Override

    public boolean edgeToEdgeByDefault() {
        return getValue(Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT,
            FeatureFlags::edgeToEdgeByDefault);
    }

    @Override

    public boolean enableAccessibleCustomHeaders() {
        return getValue(Flags.FLAG_ENABLE_ACCESSIBLE_CUSTOM_HEADERS,
            FeatureFlags::enableAccessibleCustomHeaders);
    }

    @Override

    public boolean enableActivityEmbeddingSupportForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enableActivityEmbeddingSupportForConnectedDisplays);
    }

    @Override

    public boolean enableAppHeaderWithTaskDensity() {
        return getValue(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            FeatureFlags::enableAppHeaderWithTaskDensity);
    }

    @Override

    public boolean enableBorderSettings() {
        return getValue(Flags.FLAG_ENABLE_BORDER_SETTINGS,
            FeatureFlags::enableBorderSettings);
    }

    @Override

    public boolean enableBufferTransformHintFromDisplay() {
        return getValue(Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
            FeatureFlags::enableBufferTransformHintFromDisplay);
    }

    @Override

    public boolean enableBugFixesForSecondaryDisplay() {
        return getValue(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            FeatureFlags::enableBugFixesForSecondaryDisplay);
    }

    @Override

    public boolean enableCameraCompatForDesktopWindowing() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            FeatureFlags::enableCameraCompatForDesktopWindowing);
    }

    @Override

    public boolean enableCameraCompatForDesktopWindowingOptOut() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT,
            FeatureFlags::enableCameraCompatForDesktopWindowingOptOut);
    }

    @Override

    public boolean enableCameraCompatForDesktopWindowingOptOutApi() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT_API,
            FeatureFlags::enableCameraCompatForDesktopWindowingOptOutApi);
    }

    @Override

    public boolean enableCameraCompatTrackTaskAndAppBugfix() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX,
            FeatureFlags::enableCameraCompatTrackTaskAndAppBugfix);
    }

    @Override

    public boolean enableCaptionCompatInsetConversion() {
        return getValue(Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_CONVERSION,
            FeatureFlags::enableCaptionCompatInsetConversion);
    }

    @Override

    public boolean enableCaptionCompatInsetForceConsumption() {
        return getValue(Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION,
            FeatureFlags::enableCaptionCompatInsetForceConsumption);
    }

    @Override

    public boolean enableCaptionCompatInsetForceConsumptionAlways() {
        return getValue(Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS,
            FeatureFlags::enableCaptionCompatInsetForceConsumptionAlways);
    }

    @Override

    public boolean enableCascadingWindows() {
        return getValue(Flags.FLAG_ENABLE_CASCADING_WINDOWS,
            FeatureFlags::enableCascadingWindows);
    }

    @Override

    public boolean enableCompatUiVisibilityStatus() {
        return getValue(Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            FeatureFlags::enableCompatUiVisibilityStatus);
    }

    @Override

    public boolean enableCompatuiSysuiLauncher() {
        return getValue(Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER,
            FeatureFlags::enableCompatuiSysuiLauncher);
    }

    @Override

    public boolean enableConnectedDisplaysDnd() {
        return getValue(Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND,
            FeatureFlags::enableConnectedDisplaysDnd);
    }

    @Override

    public boolean enableConnectedDisplaysPip() {
        return getValue(Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP,
            FeatureFlags::enableConnectedDisplaysPip);
    }

    @Override

    public boolean enableConnectedDisplaysWindowDrag() {
        return getValue(Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            FeatureFlags::enableConnectedDisplaysWindowDrag);
    }

    @Override

    public boolean enableDesktopAppHandleAnimation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            FeatureFlags::enableDesktopAppHandleAnimation);
    }

    @Override

    public boolean enableDesktopAppLaunchAlttabTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS,
            FeatureFlags::enableDesktopAppLaunchAlttabTransitions);
    }

    @Override

    public boolean enableDesktopAppLaunchAlttabTransitionsBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,
            FeatureFlags::enableDesktopAppLaunchAlttabTransitionsBugfix);
    }

    @Override

    public boolean enableDesktopAppLaunchTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS,
            FeatureFlags::enableDesktopAppLaunchTransitions);
    }

    @Override

    public boolean enableDesktopAppLaunchTransitionsBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
            FeatureFlags::enableDesktopAppLaunchTransitionsBugfix);
    }

    @Override

    public boolean enableDesktopCloseShortcutBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX,
            FeatureFlags::enableDesktopCloseShortcutBugfix);
    }

    @Override

    public boolean enableDesktopCloseTaskAnimationInDtcBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX,
            FeatureFlags::enableDesktopCloseTaskAnimationInDtcBugfix);
    }

    @Override

    public boolean enableDesktopImeBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
            FeatureFlags::enableDesktopImeBugfix);
    }

    @Override

    public boolean enableDesktopImmersiveDragBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX,
            FeatureFlags::enableDesktopImmersiveDragBugfix);
    }

    @Override

    public boolean enableDesktopIndicatorInSeparateThreadBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX,
            FeatureFlags::enableDesktopIndicatorInSeparateThreadBugfix);
    }

    @Override

    public boolean enableDesktopModeThroughDevOption() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
            FeatureFlags::enableDesktopModeThroughDevOption);
    }

    @Override

    public boolean enableDesktopOpeningDeeplinkMinimizeAnimationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
            FeatureFlags::enableDesktopOpeningDeeplinkMinimizeAnimationBugfix);
    }

    @Override

    public boolean enableDesktopRecentsTransitionsCornersBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX,
            FeatureFlags::enableDesktopRecentsTransitionsCornersBugfix);
    }

    @Override

    public boolean enableDesktopSwipeBackMinimizeAnimationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_SWIPE_BACK_MINIMIZE_ANIMATION_BUGFIX,
            FeatureFlags::enableDesktopSwipeBackMinimizeAnimationBugfix);
    }

    @Override

    public boolean enableDesktopSystemDialogsTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            FeatureFlags::enableDesktopSystemDialogsTransitions);
    }

    @Override

    public boolean enableDesktopTabTearingMinimizeAnimationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            FeatureFlags::enableDesktopTabTearingMinimizeAnimationBugfix);
    }

    @Override

    public boolean enableDesktopTaskbarOnFreeformDisplays() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS,
            FeatureFlags::enableDesktopTaskbarOnFreeformDisplays);
    }

    @Override

    public boolean enableDesktopTrampolineCloseAnimationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,
            FeatureFlags::enableDesktopTrampolineCloseAnimationBugfix);
    }

    @Override

    public boolean enableDesktopWallpaperActivityForSystemUser() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
            FeatureFlags::enableDesktopWallpaperActivityForSystemUser);
    }

    @Override

    public boolean enableDesktopWindowingAppHandleEducation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION,
            FeatureFlags::enableDesktopWindowingAppHandleEducation);
    }

    @Override

    public boolean enableDesktopWindowingAppToWeb() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB,
            FeatureFlags::enableDesktopWindowingAppToWeb);
    }

    @Override

    public boolean enableDesktopWindowingAppToWebEducation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION,
            FeatureFlags::enableDesktopWindowingAppToWebEducation);
    }

    @Override

    public boolean enableDesktopWindowingAppToWebEducationIntegration() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION,
            FeatureFlags::enableDesktopWindowingAppToWebEducationIntegration);
    }

    @Override

    public boolean enableDesktopWindowingBackNavigation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
            FeatureFlags::enableDesktopWindowingBackNavigation);
    }

    @Override

    public boolean enableDesktopWindowingEnterTransitionBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITION_BUGFIX,
            FeatureFlags::enableDesktopWindowingEnterTransitionBugfix);
    }

    @Override

    public boolean enableDesktopWindowingEnterTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITIONS,
            FeatureFlags::enableDesktopWindowingEnterTransitions);
    }

    @Override

    public boolean enableDesktopWindowingExitByMinimizeTransitionBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,
            FeatureFlags::enableDesktopWindowingExitByMinimizeTransitionBugfix);
    }

    @Override

    public boolean enableDesktopWindowingExitTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS,
            FeatureFlags::enableDesktopWindowingExitTransitions);
    }

    @Override

    public boolean enableDesktopWindowingExitTransitionsBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX,
            FeatureFlags::enableDesktopWindowingExitTransitionsBugfix);
    }

    @Override

    public boolean enableDesktopWindowingHsum() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
            FeatureFlags::enableDesktopWindowingHsum);
    }

    @Override

    public boolean enableDesktopWindowingImmersiveHandleHiding() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING,
            FeatureFlags::enableDesktopWindowingImmersiveHandleHiding);
    }

    @Override

    public boolean enableDesktopWindowingModalsPolicy() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
            FeatureFlags::enableDesktopWindowingModalsPolicy);
    }

    @Override

    public boolean enableDesktopWindowingMode() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            FeatureFlags::enableDesktopWindowingMode);
    }

    @Override

    public boolean enableDesktopWindowingMultiInstanceFeatures() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MULTI_INSTANCE_FEATURES,
            FeatureFlags::enableDesktopWindowingMultiInstanceFeatures);
    }

    @Override

    public boolean enableDesktopWindowingPersistence() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
            FeatureFlags::enableDesktopWindowingPersistence);
    }

    @Override

    public boolean enableDesktopWindowingPip() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP,
            FeatureFlags::enableDesktopWindowingPip);
    }

    @Override

    public boolean enableDesktopWindowingQuickSwitch() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
            FeatureFlags::enableDesktopWindowingQuickSwitch);
    }

    @Override

    public boolean enableDesktopWindowingScvhCacheBugFix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE_BUG_FIX,
            FeatureFlags::enableDesktopWindowingScvhCacheBugFix);
    }

    @Override

    public boolean enableDesktopWindowingSizeConstraints() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
            FeatureFlags::enableDesktopWindowingSizeConstraints);
    }

    @Override

    public boolean enableDesktopWindowingTaskLimit() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
            FeatureFlags::enableDesktopWindowingTaskLimit);
    }

    @Override

    public boolean enableDesktopWindowingTaskbarRunningApps() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
            FeatureFlags::enableDesktopWindowingTaskbarRunningApps);
    }

    @Override

    public boolean enableDesktopWindowingTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TRANSITIONS,
            FeatureFlags::enableDesktopWindowingTransitions);
    }

    @Override

    public boolean enableDesktopWindowingWallpaperActivity() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
            FeatureFlags::enableDesktopWindowingWallpaperActivity);
    }

    @Override

    public boolean enableDeviceStateAutoRotateSettingLogging() {
        return getValue(Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_LOGGING,
            FeatureFlags::enableDeviceStateAutoRotateSettingLogging);
    }

    @Override

    public boolean enableDeviceStateAutoRotateSettingRefactor() {
        return getValue(Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR,
            FeatureFlags::enableDeviceStateAutoRotateSettingRefactor);
    }

    @Override

    public boolean enableDisplayDisconnectInteraction() {
        return getValue(Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
            FeatureFlags::enableDisplayDisconnectInteraction);
    }

    @Override

    public boolean enableDisplayFocusInShellTransitions() {
        return getValue(Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
            FeatureFlags::enableDisplayFocusInShellTransitions);
    }

    @Override

    public boolean enableDisplayReconnectInteraction() {
        return getValue(Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
            FeatureFlags::enableDisplayReconnectInteraction);
    }

    @Override

    public boolean enableDisplayWindowingModeSwitching() {
        return getValue(Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
            FeatureFlags::enableDisplayWindowingModeSwitching);
    }

    @Override

    public boolean enableDragResizeSetUpInBgThread() {
        return getValue(Flags.FLAG_ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD,
            FeatureFlags::enableDragResizeSetUpInBgThread);
    }

    @Override

    public boolean enableDragToDesktopIncomingTransitionsBugfix() {
        return getValue(Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX,
            FeatureFlags::enableDragToDesktopIncomingTransitionsBugfix);
    }

    @Override

    public boolean enableDragToMaximize() {
        return getValue(Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE,
            FeatureFlags::enableDragToMaximize);
    }

    @Override

    public boolean enableDynamicRadiusComputationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            FeatureFlags::enableDynamicRadiusComputationBugfix);
    }

    @Override

    public boolean enableFullScreenWindowOnRemovingSplitScreenStageBugfix() {
        return getValue(Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            FeatureFlags::enableFullScreenWindowOnRemovingSplitScreenStageBugfix);
    }

    @Override

    public boolean enableFullyImmersiveInDesktop() {
        return getValue(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
            FeatureFlags::enableFullyImmersiveInDesktop);
    }

    @Override

    public boolean enableHandleInputFix() {
        return getValue(Flags.FLAG_ENABLE_HANDLE_INPUT_FIX,
            FeatureFlags::enableHandleInputFix);
    }

    @Override

    public boolean enableHoldToDragAppHandle() {
        return getValue(Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            FeatureFlags::enableHoldToDragAppHandle);
    }

    @Override

    public boolean enableInputLayerTransitionFix() {
        return getValue(Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            FeatureFlags::enableInputLayerTransitionFix);
    }

    @Override

    public boolean enableMinimizeButton() {
        return getValue(Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            FeatureFlags::enableMinimizeButton);
    }

    @Override

    public boolean enableModalsFullscreenWithPermission() {
        return getValue(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            FeatureFlags::enableModalsFullscreenWithPermission);
    }

    @Override

    public boolean enableMoveToNextDisplayShortcut() {
        return getValue(Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            FeatureFlags::enableMoveToNextDisplayShortcut);
    }

    @Override

    public boolean enableMultiDisplaySplit() {
        return getValue(Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT,
            FeatureFlags::enableMultiDisplaySplit);
    }

    @Override

    public boolean enableMultidisplayTrackpadBackGesture() {
        return getValue(Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE,
            FeatureFlags::enableMultidisplayTrackpadBackGesture);
    }

    @Override

    public boolean enableMultipleDesktopsBackend() {
        return getValue(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            FeatureFlags::enableMultipleDesktopsBackend);
    }

    @Override

    public boolean enableMultipleDesktopsFrontend() {
        return getValue(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            FeatureFlags::enableMultipleDesktopsFrontend);
    }

    @Override

    public boolean enableNonDefaultDisplaySplit() {
        return getValue(Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            FeatureFlags::enableNonDefaultDisplaySplit);
    }

    @Override

    public boolean enableOpaqueBackgroundForTransparentWindows() {
        return getValue(Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            FeatureFlags::enableOpaqueBackgroundForTransparentWindows);
    }

    @Override

    public boolean enablePerDisplayDesktopWallpaperActivity() {
        return getValue(Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
            FeatureFlags::enablePerDisplayDesktopWallpaperActivity);
    }

    @Override

    public boolean enablePerDisplayPackageContextCacheInStatusbarNotif() {
        return getValue(Flags.FLAG_ENABLE_PER_DISPLAY_PACKAGE_CONTEXT_CACHE_IN_STATUSBAR_NOTIF,
            FeatureFlags::enablePerDisplayPackageContextCacheInStatusbarNotif);
    }

    @Override

    public boolean enablePersistingDisplaySizeForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enablePersistingDisplaySizeForConnectedDisplays);
    }

    @Override

    public boolean enablePresentationForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enablePresentationForConnectedDisplays);
    }

    @Override

    public boolean enableProjectedDisplayDesktopMode() {
        return getValue(Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
            FeatureFlags::enableProjectedDisplayDesktopMode);
    }

    @Override

    public boolean enableQuickswitchDesktopSplitBugfix() {
        return getValue(Flags.FLAG_ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,
            FeatureFlags::enableQuickswitchDesktopSplitBugfix);
    }

    @Override

    public boolean enableRequestFullscreenBugfix() {
        return getValue(Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            FeatureFlags::enableRequestFullscreenBugfix);
    }

    @Override

    public boolean enableResizingMetrics() {
        return getValue(Flags.FLAG_ENABLE_RESIZING_METRICS,
            FeatureFlags::enableResizingMetrics);
    }

    @Override

    public boolean enableRestartMenuForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enableRestartMenuForConnectedDisplays);
    }

    @Override

    public boolean enableRestoreToPreviousSizeFromDesktopImmersive() {
        return getValue(Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
            FeatureFlags::enableRestoreToPreviousSizeFromDesktopImmersive);
    }

    @Override

    public boolean enableShellInitialBoundsRegressionBugFix() {
        return getValue(Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            FeatureFlags::enableShellInitialBoundsRegressionBugFix);
    }

    @Override

    public boolean enableSizeCompatModeImprovementsForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_SIZE_COMPAT_MODE_IMPROVEMENTS_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enableSizeCompatModeImprovementsForConnectedDisplays);
    }

    @Override

    public boolean enableStartLaunchTransitionFromTaskbarBugfix() {
        return getValue(Flags.FLAG_ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,
            FeatureFlags::enableStartLaunchTransitionFromTaskbarBugfix);
    }

    @Override

    public boolean enableTaskResizingKeyboardShortcuts() {
        return getValue(Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
            FeatureFlags::enableTaskResizingKeyboardShortcuts);
    }

    @Override

    public boolean enableTaskStackObserverInShell() {
        return getValue(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
            FeatureFlags::enableTaskStackObserverInShell);
    }

    @Override

    public boolean enableTaskbarConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
            FeatureFlags::enableTaskbarConnectedDisplays);
    }

    @Override

    public boolean enableTaskbarOverflow() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_OVERFLOW,
            FeatureFlags::enableTaskbarOverflow);
    }

    @Override

    public boolean enableTaskbarRecentsLayoutTransition() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,
            FeatureFlags::enableTaskbarRecentsLayoutTransition);
    }

    @Override

    public boolean enableThemedAppHeaders() {
        return getValue(Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
            FeatureFlags::enableThemedAppHeaders);
    }

    @Override

    public boolean enableTileResizing() {
        return getValue(Flags.FLAG_ENABLE_TILE_RESIZING,
            FeatureFlags::enableTileResizing);
    }

    @Override

    public boolean enableTopVisibleRootTaskPerUserTracking() {
        return getValue(Flags.FLAG_ENABLE_TOP_VISIBLE_ROOT_TASK_PER_USER_TRACKING,
            FeatureFlags::enableTopVisibleRootTaskPerUserTracking);
    }

    @Override

    public boolean enableVisualIndicatorInTransitionBugfix() {
        return getValue(Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX,
            FeatureFlags::enableVisualIndicatorInTransitionBugfix);
    }

    @Override

    public boolean enableWindowContextResourcesUpdateOnConfigChange() {
        return getValue(Flags.FLAG_ENABLE_WINDOW_CONTEXT_RESOURCES_UPDATE_ON_CONFIG_CHANGE,
            FeatureFlags::enableWindowContextResourcesUpdateOnConfigChange);
    }

    @Override

    public boolean enableWindowingDynamicInitialBounds() {
        return getValue(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
            FeatureFlags::enableWindowingDynamicInitialBounds);
    }

    @Override

    public boolean enableWindowingEdgeDragResize() {
        return getValue(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
            FeatureFlags::enableWindowingEdgeDragResize);
    }

    @Override

    public boolean enableWindowingScaledResizing() {
        return getValue(Flags.FLAG_ENABLE_WINDOWING_SCALED_RESIZING,
            FeatureFlags::enableWindowingScaledResizing);
    }

    @Override

    public boolean enableWindowingTransitionHandlersObservers() {
        return getValue(Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS,
            FeatureFlags::enableWindowingTransitionHandlersObservers);
    }

    @Override

    public boolean enforceEdgeToEdge() {
        return getValue(Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
            FeatureFlags::enforceEdgeToEdge);
    }

    @Override

    public boolean ensureKeyguardDoesTransitionStarting() {
        return getValue(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING,
            FeatureFlags::ensureKeyguardDoesTransitionStarting);
    }

    @Override

    public boolean ensureWallpaperInTransitions() {
        return getValue(Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS,
            FeatureFlags::ensureWallpaperInTransitions);
    }

    @Override

    public boolean ensureWallpaperInWearTransitions() {
        return getValue(Flags.FLAG_ENSURE_WALLPAPER_IN_WEAR_TRANSITIONS,
            FeatureFlags::ensureWallpaperInWearTransitions);
    }

    @Override

    public boolean enterDesktopByDefaultOnFreeformDisplays() {
        return getValue(Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS,
            FeatureFlags::enterDesktopByDefaultOnFreeformDisplays);
    }

    @Override

    public boolean excludeCaptionFromAppBounds() {
        return getValue(Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS,
            FeatureFlags::excludeCaptionFromAppBounds);
    }

    @Override

    public boolean excludeDrawingAppThemeSnapshotFromLock() {
        return getValue(Flags.FLAG_EXCLUDE_DRAWING_APP_THEME_SNAPSHOT_FROM_LOCK,
            FeatureFlags::excludeDrawingAppThemeSnapshotFromLock);
    }

    @Override

    public boolean excludeTaskFromRecents() {
        return getValue(Flags.FLAG_EXCLUDE_TASK_FROM_RECENTS,
            FeatureFlags::excludeTaskFromRecents);
    }

    @Override

    public boolean fifoPriorityForMajorUiProcesses() {
        return getValue(Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            FeatureFlags::fifoPriorityForMajorUiProcesses);
    }

    @Override

    public boolean fixHideOverlayApi() {
        return getValue(Flags.FLAG_FIX_HIDE_OVERLAY_API,
            FeatureFlags::fixHideOverlayApi);
    }

    @Override

    public boolean fixLayoutExistingTask() {
        return getValue(Flags.FLAG_FIX_LAYOUT_EXISTING_TASK,
            FeatureFlags::fixLayoutExistingTask);
    }

    @Override

    public boolean fixViewRootCallTrace() {
        return getValue(Flags.FLAG_FIX_VIEW_ROOT_CALL_TRACE,
            FeatureFlags::fixViewRootCallTrace);
    }

    @Override

    public boolean forceCloseTopTransparentFullscreenTask() {
        return getValue(Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK,
            FeatureFlags::forceCloseTopTransparentFullscreenTask);
    }

    @Override

    public boolean formFactorBasedDesktopFirstSwitch() {
        return getValue(Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
            FeatureFlags::formFactorBasedDesktopFirstSwitch);
    }

    @Override

    public boolean getDimmerOnClosing() {
        return getValue(Flags.FLAG_GET_DIMMER_ON_CLOSING,
            FeatureFlags::getDimmerOnClosing);
    }

    @Override

    public boolean ignoreAspectRatioRestrictionsForResizeableFreeformActivities() {
        return getValue(Flags.FLAG_IGNORE_ASPECT_RATIO_RESTRICTIONS_FOR_RESIZEABLE_FREEFORM_ACTIVITIES,
            FeatureFlags::ignoreAspectRatioRestrictionsForResizeableFreeformActivities);
    }

    @Override

    public boolean ignoreCornerRadiusAndShadows() {
        return getValue(Flags.FLAG_IGNORE_CORNER_RADIUS_AND_SHADOWS,
            FeatureFlags::ignoreCornerRadiusAndShadows);
    }

    @Override

    public boolean includeTopTransparentFullscreenTaskInDesktopHeuristic() {
        return getValue(Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
            FeatureFlags::includeTopTransparentFullscreenTaskInDesktopHeuristic);
    }

    @Override

    public boolean inheritTaskBoundsForTrampolineTaskLaunches() {
        return getValue(Flags.FLAG_INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES,
            FeatureFlags::inheritTaskBoundsForTrampolineTaskLaunches);
    }

    @Override

    public boolean insetsDecoupledConfiguration() {
        return getValue(Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
            FeatureFlags::insetsDecoupledConfiguration);
    }

    @Override

    public boolean jankApi() {
        return getValue(Flags.FLAG_JANK_API,
            FeatureFlags::jankApi);
    }

    @Override

    public boolean keepAppWindowHideWhileLocked() {
        return getValue(Flags.FLAG_KEEP_APP_WINDOW_HIDE_WHILE_LOCKED,
            FeatureFlags::keepAppWindowHideWhileLocked);
    }

    @Override

    public boolean keyboardShortcutsToSwitchDesks() {
        return getValue(Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            FeatureFlags::keyboardShortcutsToSwitchDesks);
    }

    @Override

    public boolean keyguardGoingAwayTimeout() {
        return getValue(Flags.FLAG_KEYGUARD_GOING_AWAY_TIMEOUT,
            FeatureFlags::keyguardGoingAwayTimeout);
    }

    @Override

    public boolean letterboxBackgroundWallpaper() {
        return getValue(Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            FeatureFlags::letterboxBackgroundWallpaper);
    }

    @Override

    public boolean movableCutoutConfiguration() {
        return getValue(Flags.FLAG_MOVABLE_CUTOUT_CONFIGURATION,
            FeatureFlags::movableCutoutConfiguration);
    }

    @Override

    public boolean moveToExternalDisplayShortcut() {
        return getValue(Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            FeatureFlags::moveToExternalDisplayShortcut);
    }

    @Override

    public boolean multiCrop() {
        return getValue(Flags.FLAG_MULTI_CROP,
            FeatureFlags::multiCrop);
    }

    @Override

    public boolean navBarTransparentByDefault() {
        return getValue(Flags.FLAG_NAV_BAR_TRANSPARENT_BY_DEFAULT,
            FeatureFlags::navBarTransparentByDefault);
    }

    @Override

    public boolean nestedTasksWithIndependentBounds() {
        return getValue(Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS,
            FeatureFlags::nestedTasksWithIndependentBounds);
    }

    @Override

    public boolean noConsecutiveVisibilityEvents() {
        return getValue(Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS,
            FeatureFlags::noConsecutiveVisibilityEvents);
    }

    @Override

    public boolean noDuplicateSurfaceDestroyedEvents() {
        return getValue(Flags.FLAG_NO_DUPLICATE_SURFACE_DESTROYED_EVENTS,
            FeatureFlags::noDuplicateSurfaceDestroyedEvents);
    }

    @Override

    public boolean noVisibilityEventOnDisplayStateChange() {
        return getValue(Flags.FLAG_NO_VISIBILITY_EVENT_ON_DISPLAY_STATE_CHANGE,
            FeatureFlags::noVisibilityEventOnDisplayStateChange);
    }

    @Override

    public boolean offloadColorExtraction() {
        return getValue(Flags.FLAG_OFFLOAD_COLOR_EXTRACTION,
            FeatureFlags::offloadColorExtraction);
    }

    @Override

    public boolean portWindowSizeAnimation() {
        return getValue(Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            FeatureFlags::portWindowSizeAnimation);
    }

    @Override

    public boolean predictiveBackDefaultEnableSdk36() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_DEFAULT_ENABLE_SDK_36,
            FeatureFlags::predictiveBackDefaultEnableSdk36);
    }

    @Override

    public boolean predictiveBackPrioritySystemNavigationObserver() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            FeatureFlags::predictiveBackPrioritySystemNavigationObserver);
    }

    @Override

    public boolean predictiveBackSwipeEdgeNoneApi() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API,
            FeatureFlags::predictiveBackSwipeEdgeNoneApi);
    }

    @Override

    public boolean predictiveBackSystemOverrideCallback() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK,
            FeatureFlags::predictiveBackSystemOverrideCallback);
    }

    @Override

    public boolean predictiveBackThreeButtonNav() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_THREE_BUTTON_NAV,
            FeatureFlags::predictiveBackThreeButtonNav);
    }

    @Override

    public boolean predictiveBackTimestampApi() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            FeatureFlags::predictiveBackTimestampApi);
    }

    @Override

    public boolean processPriorityPolicyForMultiWindowMode() {
        return getValue(Flags.FLAG_PROCESS_PRIORITY_POLICY_FOR_MULTI_WINDOW_MODE,
            FeatureFlags::processPriorityPolicyForMultiWindowMode);
    }

    @Override

    public boolean rearDisplayDisableForceDesktopSystemDecorations() {
        return getValue(Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            FeatureFlags::rearDisplayDisableForceDesktopSystemDecorations);
    }

    @Override

    public boolean recordTaskSnapshotsBeforeShutdown() {
        return getValue(Flags.FLAG_RECORD_TASK_SNAPSHOTS_BEFORE_SHUTDOWN,
            FeatureFlags::recordTaskSnapshotsBeforeShutdown);
    }

    @Override

    public boolean reduceChangedExclusionRectsMsgs() {
        return getValue(Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            FeatureFlags::reduceChangedExclusionRectsMsgs);
    }

    @Override

    public boolean reduceKeyguardTransitions() {
        return getValue(Flags.FLAG_REDUCE_KEYGUARD_TRANSITIONS,
            FeatureFlags::reduceKeyguardTransitions);
    }

    @Override

    public boolean reduceTaskSnapshotMemoryUsage() {
        return getValue(Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            FeatureFlags::reduceTaskSnapshotMemoryUsage);
    }

    @Override

    public boolean reduceUnnecessaryMeasure() {
        return getValue(Flags.FLAG_REDUCE_UNNECESSARY_MEASURE,
            FeatureFlags::reduceUnnecessaryMeasure);
    }

    @Override

    public boolean relativeInsets() {
        return getValue(Flags.FLAG_RELATIVE_INSETS,
            FeatureFlags::relativeInsets);
    }

    @Override

    public boolean releaseSnapshotAggressively() {
        return getValue(Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
            FeatureFlags::releaseSnapshotAggressively);
    }

    @Override

    public boolean releaseUserAspectRatioWm() {
        return getValue(Flags.FLAG_RELEASE_USER_ASPECT_RATIO_WM,
            FeatureFlags::releaseUserAspectRatioWm);
    }

    @Override

    public boolean removeActivityStarterDreamCallback() {
        return getValue(Flags.FLAG_REMOVE_ACTIVITY_STARTER_DREAM_CALLBACK,
            FeatureFlags::removeActivityStarterDreamCallback);
    }

    @Override

    public boolean removeDeferHidingClient() {
        return getValue(Flags.FLAG_REMOVE_DEFER_HIDING_CLIENT,
            FeatureFlags::removeDeferHidingClient);
    }

    @Override

    public boolean removeDepartTargetFromMotion() {
        return getValue(Flags.FLAG_REMOVE_DEPART_TARGET_FROM_MOTION,
            FeatureFlags::removeDepartTargetFromMotion);
    }

    @Override

    public boolean reparentWindowTokenApi() {
        return getValue(Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            FeatureFlags::reparentWindowTokenApi);
    }

    @Override

    public boolean respectNonTopVisibleFixedOrientation() {
        return getValue(Flags.FLAG_RESPECT_NON_TOP_VISIBLE_FIXED_ORIENTATION,
            FeatureFlags::respectNonTopVisibleFixedOrientation);
    }

    @Override

    public boolean respectOrientationChangeForUnresizeable() {
        return getValue(Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            FeatureFlags::respectOrientationChangeForUnresizeable);
    }

    @Override

    public boolean safeRegionLetterboxing() {
        return getValue(Flags.FLAG_SAFE_REGION_LETTERBOXING,
            FeatureFlags::safeRegionLetterboxing);
    }

    @Override

    public boolean safeReleaseSnapshotAggressively() {
        return getValue(Flags.FLAG_SAFE_RELEASE_SNAPSHOT_AGGRESSIVELY,
            FeatureFlags::safeReleaseSnapshotAggressively);
    }

    @Override

    public boolean schedulingForNotificationShade() {
        return getValue(Flags.FLAG_SCHEDULING_FOR_NOTIFICATION_SHADE,
            FeatureFlags::schedulingForNotificationShade);
    }

    @Override

    public boolean scrambleSnapshotFileName() {
        return getValue(Flags.FLAG_SCRAMBLE_SNAPSHOT_FILE_NAME,
            FeatureFlags::scrambleSnapshotFileName);
    }

    @Override

    public boolean screenRecordingCallbacks() {
        return getValue(Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
            FeatureFlags::screenRecordingCallbacks);
    }

    @Override

    public boolean scrollingFromLetterbox() {
        return getValue(Flags.FLAG_SCROLLING_FROM_LETTERBOX,
            FeatureFlags::scrollingFromLetterbox);
    }

    @Override

    public boolean sdkDesiredPresentTime() {
        return getValue(Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
            FeatureFlags::sdkDesiredPresentTime);
    }

    @Override

    public boolean setScPropertiesInClient() {
        return getValue(Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
            FeatureFlags::setScPropertiesInClient);
    }

    @Override

    public boolean showAppHandleLargeScreens() {
        return getValue(Flags.FLAG_SHOW_APP_HANDLE_LARGE_SCREENS,
            FeatureFlags::showAppHandleLargeScreens);
    }

    @Override

    public boolean showDesktopExperienceDevOption() {
        return getValue(Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            FeatureFlags::showDesktopExperienceDevOption);
    }

    @Override

    public boolean showDesktopWindowingDevOption() {
        return getValue(Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION,
            FeatureFlags::showDesktopWindowingDevOption);
    }

    @Override

    public boolean showHomeBehindDesktop() {
        return getValue(Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP,
            FeatureFlags::showHomeBehindDesktop);
    }

    @Override

    public boolean skipCompatUiEducationInDesktopMode() {
        return getValue(Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE,
            FeatureFlags::skipCompatUiEducationInDesktopMode);
    }

    @Override

    public boolean skipDecorViewRelayoutWhenClosingBugfix() {
        return getValue(Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            FeatureFlags::skipDecorViewRelayoutWhenClosingBugfix);
    }

    @Override

    public boolean supportWidgetIntentsOnConnectedDisplay() {
        return getValue(Flags.FLAG_SUPPORT_WIDGET_INTENTS_ON_CONNECTED_DISPLAY,
            FeatureFlags::supportWidgetIntentsOnConnectedDisplay);
    }

    @Override

    public boolean supportsDragAssistantToMultiwindow() {
        return getValue(Flags.FLAG_SUPPORTS_DRAG_ASSISTANT_TO_MULTIWINDOW,
            FeatureFlags::supportsDragAssistantToMultiwindow);
    }

    @Override

    public boolean supportsMultiInstanceSystemUi() {
        return getValue(Flags.FLAG_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI,
            FeatureFlags::supportsMultiInstanceSystemUi);
    }

    @Override

    public boolean surfaceControlInputReceiver() {
        return getValue(Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER,
            FeatureFlags::surfaceControlInputReceiver);
    }

    @Override

    public boolean surfaceTrustedOverlay() {
        return getValue(Flags.FLAG_SURFACE_TRUSTED_OVERLAY,
            FeatureFlags::surfaceTrustedOverlay);
    }

    @Override

    public boolean syncScreenCapture() {
        return getValue(Flags.FLAG_SYNC_SCREEN_CAPTURE,
            FeatureFlags::syncScreenCapture);
    }

    @Override

    public boolean systemUiPostAnimationEnd() {
        return getValue(Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            FeatureFlags::systemUiPostAnimationEnd);
    }

    @Override

    public boolean taskFragmentSystemOrganizerFlag() {
        return getValue(Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG,
            FeatureFlags::taskFragmentSystemOrganizerFlag);
    }

    @Override

    public boolean touchPassThroughOptIn() {
        return getValue(Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            FeatureFlags::touchPassThroughOptIn);
    }

    @Override

    public boolean trackSystemUiContextBeforeWms() {
        return getValue(Flags.FLAG_TRACK_SYSTEM_UI_CONTEXT_BEFORE_WMS,
            FeatureFlags::trackSystemUiContextBeforeWms);
    }

    @Override

    public boolean transitReadyTracking() {
        return getValue(Flags.FLAG_TRANSIT_READY_TRACKING,
            FeatureFlags::transitReadyTracking);
    }

    @Override

    public boolean transitTrackerPlumbing() {
        return getValue(Flags.FLAG_TRANSIT_TRACKER_PLUMBING,
            FeatureFlags::transitTrackerPlumbing);
    }

    @Override

    public boolean trustedPresentationListenerForWindow() {
        return getValue(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
            FeatureFlags::trustedPresentationListenerForWindow);
    }

    @Override

    public boolean unifyBackNavigationTransition() {
        return getValue(Flags.FLAG_UNIFY_BACK_NAVIGATION_TRANSITION,
            FeatureFlags::unifyBackNavigationTransition);
    }

    @Override

    public boolean universalResizableByDefault() {
        return getValue(Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT,
            FeatureFlags::universalResizableByDefault);
    }

    @Override

    public boolean untrustedEmbeddingAnyAppPermission() {
        return getValue(Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
            FeatureFlags::untrustedEmbeddingAnyAppPermission);
    }

    @Override

    public boolean untrustedEmbeddingStateSharing() {
        return getValue(Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
            FeatureFlags::untrustedEmbeddingStateSharing);
    }

    @Override

    public boolean updateDimsWhenWindowShown() {
        return getValue(Flags.FLAG_UPDATE_DIMS_WHEN_WINDOW_SHOWN,
            FeatureFlags::updateDimsWhenWindowShown);
    }

    @Override

    public boolean useCachedInsetsForDisplaySwitch() {
        return getValue(Flags.FLAG_USE_CACHED_INSETS_FOR_DISPLAY_SWITCH,
            FeatureFlags::useCachedInsetsForDisplaySwitch);
    }

    @Override

    public boolean useRtFrameCallbackForSplashScreenTransfer() {
        return getValue(Flags.FLAG_USE_RT_FRAME_CALLBACK_FOR_SPLASH_SCREEN_TRANSFER,
            FeatureFlags::useRtFrameCallbackForSplashScreenTransfer);
    }

    @Override

    public boolean useTasksDimOnly() {
        return getValue(Flags.FLAG_USE_TASKS_DIM_ONLY,
            FeatureFlags::useTasksDimOnly);
    }

    @Override

    public boolean useVisibleRequestedForProcessTracker() {
        return getValue(Flags.FLAG_USE_VISIBLE_REQUESTED_FOR_PROCESS_TRACKER,
            FeatureFlags::useVisibleRequestedForProcessTracker);
    }

    @Override

    public boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds() {
        return getValue(Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS,
            FeatureFlags::useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds);
    }

    @Override

    public boolean vdmForceAppUniversalResizableApi() {
        return getValue(Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            FeatureFlags::vdmForceAppUniversalResizableApi);
    }

    @Override

    public boolean wallpaperOffsetAsync() {
        return getValue(Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
            FeatureFlags::wallpaperOffsetAsync);
    }

    @Override

    public boolean wlinfoOncreate() {
        return getValue(Flags.FLAG_WLINFO_ONCREATE,
            FeatureFlags::wlinfoOncreate);
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
            Flags.FLAG_ACTION_MODE_EDGE_TO_EDGE,
            Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_DELAY_TASK_FRAGMENT_FINISH_FOR_ACTIVITY_LAUNCH,
            Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_METRICS,
            Flags.FLAG_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            Flags.FLAG_ALLOW_HIDE_SCM_BUTTON,
            Flags.FLAG_ALLOWS_SCREEN_SIZE_DECOUPLED_FROM_STATUS_BAR_AND_CUTOUT,
            Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
            Flags.FLAG_AOD_TRANSITION,
            Flags.FLAG_APP_COMPAT_ASYNC_RELAYOUT,
            Flags.FLAG_APP_COMPAT_PROPERTIES_API,
            Flags.FLAG_APP_COMPAT_REFACTORING,
            Flags.FLAG_APP_COMPAT_UI_FRAMEWORK,
            Flags.FLAG_APP_HANDLE_NO_RELAYOUT_ON_EXCLUSION_CHANGE,
            Flags.FLAG_APPLY_LIFECYCLE_ON_PIP_CHANGE,
            Flags.FLAG_AVOID_REBINDING_INTENTIONALLY_DISCONNECTED_WALLPAPER,
            Flags.FLAG_BACKUP_AND_RESTORE_FOR_USER_ASPECT_RATIO_SETTINGS,
            Flags.FLAG_BAL_ADDITIONAL_LOGGING,
            Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            Flags.FLAG_BAL_CLEAR_ALLOWLIST_DURATION,
            Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            Flags.FLAG_BAL_IMPROVE_REAL_CALLER_VISIBILITY_CHECK,
            Flags.FLAG_BAL_IMPROVED_METRICS,
            Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
            Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
            Flags.FLAG_BAL_SEND_INTENT_WITH_OPTIONS,
            Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED,
            Flags.FLAG_BAL_STRICT_MODE_GRACE_PERIOD,
            Flags.FLAG_BAL_STRICT_MODE_RO,
            Flags.FLAG_BETTER_SUPPORT_NON_MATCH_PARENT_ACTIVITY,
            Flags.FLAG_CACHE_WINDOW_STYLE,
            Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
            Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            Flags.FLAG_CHECK_DISABLED_SNAPSHOTS_IN_TASK_PERSISTER,
            Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION,
            Flags.FLAG_CLEAR_SYSTEM_VIBRATOR,
            Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            Flags.FLAG_CONDENSE_CONFIGURATION_CHANGE_FOR_SIMPLE_MODE,
            Flags.FLAG_CONFIGURABLE_FONT_SCALE_DEFAULT,
            Flags.FLAG_COVER_DISPLAY_OPT_IN,
            Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION,
            Flags.FLAG_DELEGATE_BACK_GESTURE_TO_SHELL,
            Flags.FLAG_DELEGATE_UNHANDLED_DRAGS,
            Flags.FLAG_DELETE_CAPTURE_DISPLAY,
            Flags.FLAG_DENSITY_390_API,
            Flags.FLAG_DISABLE_DESKTOP_LAUNCH_PARAMS_OUTSIDE_DESKTOP_BUG_FIX,
            Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING,
            Flags.FLAG_DISABLE_OPT_OUT_EDGE_TO_EDGE,
            Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS,
            Flags.FLAG_EARLY_LAUNCH_HINT,
            Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT,
            Flags.FLAG_ENABLE_ACCESSIBLE_CUSTOM_HEADERS,
            Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            Flags.FLAG_ENABLE_BORDER_SETTINGS,
            Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
            Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT_API,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_CONVERSION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS,
            Flags.FLAG_ENABLE_CASCADING_WINDOWS,
            Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
            Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SWIPE_BACK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MULTI_INSTANCE_FEATURES,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE_BUG_FIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_LOGGING,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR,
            Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
            Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
            Flags.FLAG_ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD,
            Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
            Flags.FLAG_ENABLE_HANDLE_INPUT_FIX,
            Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_PER_DISPLAY_PACKAGE_CONTEXT_CACHE_IN_STATUSBAR_NOTIF,
            Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
            Flags.FLAG_ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_RESIZING_METRICS,
            Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
            Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            Flags.FLAG_ENABLE_SIZE_COMPAT_MODE_IMPROVEMENTS_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,
            Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
            Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
            Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_TASKBAR_OVERFLOW,
            Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,
            Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
            Flags.FLAG_ENABLE_TILE_RESIZING,
            Flags.FLAG_ENABLE_TOP_VISIBLE_ROOT_TASK_PER_USER_TRACKING,
            Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_WINDOW_CONTEXT_RESOURCES_UPDATE_ON_CONFIG_CHANGE,
            Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
            Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
            Flags.FLAG_ENABLE_WINDOWING_SCALED_RESIZING,
            Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS,
            Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
            Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING,
            Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS,
            Flags.FLAG_ENSURE_WALLPAPER_IN_WEAR_TRANSITIONS,
            Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS,
            Flags.FLAG_EXCLUDE_DRAWING_APP_THEME_SNAPSHOT_FROM_LOCK,
            Flags.FLAG_EXCLUDE_TASK_FROM_RECENTS,
            Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            Flags.FLAG_FIX_HIDE_OVERLAY_API,
            Flags.FLAG_FIX_LAYOUT_EXISTING_TASK,
            Flags.FLAG_FIX_VIEW_ROOT_CALL_TRACE,
            Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK,
            Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
            Flags.FLAG_GET_DIMMER_ON_CLOSING,
            Flags.FLAG_IGNORE_ASPECT_RATIO_RESTRICTIONS_FOR_RESIZEABLE_FREEFORM_ACTIVITIES,
            Flags.FLAG_IGNORE_CORNER_RADIUS_AND_SHADOWS,
            Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
            Flags.FLAG_INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES,
            Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
            Flags.FLAG_JANK_API,
            Flags.FLAG_KEEP_APP_WINDOW_HIDE_WHILE_LOCKED,
            Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            Flags.FLAG_KEYGUARD_GOING_AWAY_TIMEOUT,
            Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            Flags.FLAG_MOVABLE_CUTOUT_CONFIGURATION,
            Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            Flags.FLAG_MULTI_CROP,
            Flags.FLAG_NAV_BAR_TRANSPARENT_BY_DEFAULT,
            Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS,
            Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS,
            Flags.FLAG_NO_DUPLICATE_SURFACE_DESTROYED_EVENTS,
            Flags.FLAG_NO_VISIBILITY_EVENT_ON_DISPLAY_STATE_CHANGE,
            Flags.FLAG_OFFLOAD_COLOR_EXTRACTION,
            Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            Flags.FLAG_PREDICTIVE_BACK_DEFAULT_ENABLE_SDK_36,
            Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API,
            Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK,
            Flags.FLAG_PREDICTIVE_BACK_THREE_BUTTON_NAV,
            Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            Flags.FLAG_PROCESS_PRIORITY_POLICY_FOR_MULTI_WINDOW_MODE,
            Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            Flags.FLAG_RECORD_TASK_SNAPSHOTS_BEFORE_SHUTDOWN,
            Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            Flags.FLAG_REDUCE_KEYGUARD_TRANSITIONS,
            Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            Flags.FLAG_REDUCE_UNNECESSARY_MEASURE,
            Flags.FLAG_RELATIVE_INSETS,
            Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
            Flags.FLAG_RELEASE_USER_ASPECT_RATIO_WM,
            Flags.FLAG_REMOVE_ACTIVITY_STARTER_DREAM_CALLBACK,
            Flags.FLAG_REMOVE_DEFER_HIDING_CLIENT,
            Flags.FLAG_REMOVE_DEPART_TARGET_FROM_MOTION,
            Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            Flags.FLAG_RESPECT_NON_TOP_VISIBLE_FIXED_ORIENTATION,
            Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            Flags.FLAG_SAFE_REGION_LETTERBOXING,
            Flags.FLAG_SAFE_RELEASE_SNAPSHOT_AGGRESSIVELY,
            Flags.FLAG_SCHEDULING_FOR_NOTIFICATION_SHADE,
            Flags.FLAG_SCRAMBLE_SNAPSHOT_FILE_NAME,
            Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
            Flags.FLAG_SCROLLING_FROM_LETTERBOX,
            Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
            Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
            Flags.FLAG_SHOW_APP_HANDLE_LARGE_SCREENS,
            Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION,
            Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP,
            Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE,
            Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            Flags.FLAG_SUPPORT_WIDGET_INTENTS_ON_CONNECTED_DISPLAY,
            Flags.FLAG_SUPPORTS_DRAG_ASSISTANT_TO_MULTIWINDOW,
            Flags.FLAG_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI,
            Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER,
            Flags.FLAG_SURFACE_TRUSTED_OVERLAY,
            Flags.FLAG_SYNC_SCREEN_CAPTURE,
            Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG,
            Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            Flags.FLAG_TRACK_SYSTEM_UI_CONTEXT_BEFORE_WMS,
            Flags.FLAG_TRANSIT_READY_TRACKING,
            Flags.FLAG_TRANSIT_TRACKER_PLUMBING,
            Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
            Flags.FLAG_UNIFY_BACK_NAVIGATION_TRANSITION,
            Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT,
            Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
            Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
            Flags.FLAG_UPDATE_DIMS_WHEN_WINDOW_SHOWN,
            Flags.FLAG_USE_CACHED_INSETS_FOR_DISPLAY_SWITCH,
            Flags.FLAG_USE_RT_FRAME_CALLBACK_FOR_SPLASH_SCREEN_TRANSFER,
            Flags.FLAG_USE_TASKS_DIM_ONLY,
            Flags.FLAG_USE_VISIBLE_REQUESTED_FOR_PROCESS_TRACKER,
            Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS,
            Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
            Flags.FLAG_WLINFO_ONCREATE
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_ACTION_MODE_EDGE_TO_EDGE,
            Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_DELAY_TASK_FRAGMENT_FINISH_FOR_ACTIVITY_LAUNCH,
            Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_METRICS,
            Flags.FLAG_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            Flags.FLAG_ALLOW_HIDE_SCM_BUTTON,
            Flags.FLAG_ALLOWS_SCREEN_SIZE_DECOUPLED_FROM_STATUS_BAR_AND_CUTOUT,
            Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
            Flags.FLAG_AOD_TRANSITION,
            Flags.FLAG_APP_COMPAT_ASYNC_RELAYOUT,
            Flags.FLAG_APP_COMPAT_PROPERTIES_API,
            Flags.FLAG_APP_COMPAT_REFACTORING,
            Flags.FLAG_APP_COMPAT_UI_FRAMEWORK,
            Flags.FLAG_APP_HANDLE_NO_RELAYOUT_ON_EXCLUSION_CHANGE,
            Flags.FLAG_APPLY_LIFECYCLE_ON_PIP_CHANGE,
            Flags.FLAG_AVOID_REBINDING_INTENTIONALLY_DISCONNECTED_WALLPAPER,
            Flags.FLAG_BACKUP_AND_RESTORE_FOR_USER_ASPECT_RATIO_SETTINGS,
            Flags.FLAG_BAL_ADDITIONAL_LOGGING,
            Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            Flags.FLAG_BAL_CLEAR_ALLOWLIST_DURATION,
            Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            Flags.FLAG_BAL_IMPROVE_REAL_CALLER_VISIBILITY_CHECK,
            Flags.FLAG_BAL_IMPROVED_METRICS,
            Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
            Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
            Flags.FLAG_BAL_SEND_INTENT_WITH_OPTIONS,
            Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED,
            Flags.FLAG_BAL_STRICT_MODE_GRACE_PERIOD,
            Flags.FLAG_BAL_STRICT_MODE_RO,
            Flags.FLAG_BETTER_SUPPORT_NON_MATCH_PARENT_ACTIVITY,
            Flags.FLAG_CACHE_WINDOW_STYLE,
            Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
            Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            Flags.FLAG_CHECK_DISABLED_SNAPSHOTS_IN_TASK_PERSISTER,
            Flags.FLAG_CLEANUP_DISPATCH_PENDING_TRANSACTIONS_REMOTE_EXCEPTION,
            Flags.FLAG_CLEAR_SYSTEM_VIBRATOR,
            Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            Flags.FLAG_CONDENSE_CONFIGURATION_CHANGE_FOR_SIMPLE_MODE,
            Flags.FLAG_CONFIGURABLE_FONT_SCALE_DEFAULT,
            Flags.FLAG_COVER_DISPLAY_OPT_IN,
            Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION,
            Flags.FLAG_DELEGATE_BACK_GESTURE_TO_SHELL,
            Flags.FLAG_DELEGATE_UNHANDLED_DRAGS,
            Flags.FLAG_DELETE_CAPTURE_DISPLAY,
            Flags.FLAG_DENSITY_390_API,
            Flags.FLAG_DISABLE_DESKTOP_LAUNCH_PARAMS_OUTSIDE_DESKTOP_BUG_FIX,
            Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING,
            Flags.FLAG_DISABLE_OPT_OUT_EDGE_TO_EDGE,
            Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS,
            Flags.FLAG_EARLY_LAUNCH_HINT,
            Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT,
            Flags.FLAG_ENABLE_ACCESSIBLE_CUSTOM_HEADERS,
            Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            Flags.FLAG_ENABLE_BORDER_SETTINGS,
            Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
            Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT_API,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_CONVERSION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS,
            Flags.FLAG_ENABLE_CASCADING_WINDOWS,
            Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
            Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SWIPE_BACK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_TASKBAR_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_ENABLE_DESKTOP_TRAMPOLINE_CLOSE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WALLPAPER_ACTIVITY_FOR_SYSTEM_USER,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_TO_WEB_EDUCATION_INTEGRATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTER_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_HSUM,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MULTI_INSTANCE_FEATURES,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE_BUG_FIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_LOGGING,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR,
            Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
            Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
            Flags.FLAG_ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD,
            Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
            Flags.FLAG_ENABLE_HANDLE_INPUT_FIX,
            Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_PER_DISPLAY_PACKAGE_CONTEXT_CACHE_IN_STATUSBAR_NOTIF,
            Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
            Flags.FLAG_ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_RESIZING_METRICS,
            Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
            Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            Flags.FLAG_ENABLE_SIZE_COMPAT_MODE_IMPROVEMENTS_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,
            Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
            Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
            Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_TASKBAR_OVERFLOW,
            Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,
            Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
            Flags.FLAG_ENABLE_TILE_RESIZING,
            Flags.FLAG_ENABLE_TOP_VISIBLE_ROOT_TASK_PER_USER_TRACKING,
            Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_WINDOW_CONTEXT_RESOURCES_UPDATE_ON_CONFIG_CHANGE,
            Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
            Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
            Flags.FLAG_ENABLE_WINDOWING_SCALED_RESIZING,
            Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS,
            Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
            Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING,
            Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS,
            Flags.FLAG_ENSURE_WALLPAPER_IN_WEAR_TRANSITIONS,
            Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS,
            Flags.FLAG_EXCLUDE_DRAWING_APP_THEME_SNAPSHOT_FROM_LOCK,
            Flags.FLAG_EXCLUDE_TASK_FROM_RECENTS,
            Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            Flags.FLAG_FIX_HIDE_OVERLAY_API,
            Flags.FLAG_FIX_LAYOUT_EXISTING_TASK,
            Flags.FLAG_FIX_VIEW_ROOT_CALL_TRACE,
            Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK,
            Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
            Flags.FLAG_GET_DIMMER_ON_CLOSING,
            Flags.FLAG_IGNORE_ASPECT_RATIO_RESTRICTIONS_FOR_RESIZEABLE_FREEFORM_ACTIVITIES,
            Flags.FLAG_IGNORE_CORNER_RADIUS_AND_SHADOWS,
            Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
            Flags.FLAG_INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES,
            Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
            Flags.FLAG_JANK_API,
            Flags.FLAG_KEEP_APP_WINDOW_HIDE_WHILE_LOCKED,
            Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            Flags.FLAG_KEYGUARD_GOING_AWAY_TIMEOUT,
            Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            Flags.FLAG_MOVABLE_CUTOUT_CONFIGURATION,
            Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            Flags.FLAG_MULTI_CROP,
            Flags.FLAG_NAV_BAR_TRANSPARENT_BY_DEFAULT,
            Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS,
            Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS,
            Flags.FLAG_NO_DUPLICATE_SURFACE_DESTROYED_EVENTS,
            Flags.FLAG_NO_VISIBILITY_EVENT_ON_DISPLAY_STATE_CHANGE,
            Flags.FLAG_OFFLOAD_COLOR_EXTRACTION,
            Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            Flags.FLAG_PREDICTIVE_BACK_DEFAULT_ENABLE_SDK_36,
            Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API,
            Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK,
            Flags.FLAG_PREDICTIVE_BACK_THREE_BUTTON_NAV,
            Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            Flags.FLAG_PROCESS_PRIORITY_POLICY_FOR_MULTI_WINDOW_MODE,
            Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            Flags.FLAG_RECORD_TASK_SNAPSHOTS_BEFORE_SHUTDOWN,
            Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            Flags.FLAG_REDUCE_KEYGUARD_TRANSITIONS,
            Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            Flags.FLAG_REDUCE_UNNECESSARY_MEASURE,
            Flags.FLAG_RELATIVE_INSETS,
            Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
            Flags.FLAG_RELEASE_USER_ASPECT_RATIO_WM,
            Flags.FLAG_REMOVE_ACTIVITY_STARTER_DREAM_CALLBACK,
            Flags.FLAG_REMOVE_DEFER_HIDING_CLIENT,
            Flags.FLAG_REMOVE_DEPART_TARGET_FROM_MOTION,
            Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            Flags.FLAG_RESPECT_NON_TOP_VISIBLE_FIXED_ORIENTATION,
            Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            Flags.FLAG_SAFE_REGION_LETTERBOXING,
            Flags.FLAG_SAFE_RELEASE_SNAPSHOT_AGGRESSIVELY,
            Flags.FLAG_SCHEDULING_FOR_NOTIFICATION_SHADE,
            Flags.FLAG_SCRAMBLE_SNAPSHOT_FILE_NAME,
            Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
            Flags.FLAG_SCROLLING_FROM_LETTERBOX,
            Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
            Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
            Flags.FLAG_SHOW_APP_HANDLE_LARGE_SCREENS,
            Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION,
            Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP,
            Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE,
            Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            Flags.FLAG_SUPPORT_WIDGET_INTENTS_ON_CONNECTED_DISPLAY,
            Flags.FLAG_SUPPORTS_DRAG_ASSISTANT_TO_MULTIWINDOW,
            Flags.FLAG_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI,
            Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER,
            Flags.FLAG_SURFACE_TRUSTED_OVERLAY,
            Flags.FLAG_SYNC_SCREEN_CAPTURE,
            Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG,
            Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            Flags.FLAG_TRACK_SYSTEM_UI_CONTEXT_BEFORE_WMS,
            Flags.FLAG_TRANSIT_READY_TRACKING,
            Flags.FLAG_TRANSIT_TRACKER_PLUMBING,
            Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
            Flags.FLAG_UNIFY_BACK_NAVIGATION_TRANSITION,
            Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT,
            Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
            Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
            Flags.FLAG_UPDATE_DIMS_WHEN_WINDOW_SHOWN,
            Flags.FLAG_USE_CACHED_INSETS_FOR_DISPLAY_SWITCH,
            Flags.FLAG_USE_RT_FRAME_CALLBACK_FOR_SPLASH_SCREEN_TRANSFER,
            Flags.FLAG_USE_TASKS_DIM_ONLY,
            Flags.FLAG_USE_VISIBLE_REQUESTED_FOR_PROCESS_TRACKER,
            Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS,
            Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
            Flags.FLAG_WLINFO_ONCREATE,
            ""
        )
    );
}
