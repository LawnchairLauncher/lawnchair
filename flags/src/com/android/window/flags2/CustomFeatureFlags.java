package com.android.window.flags2;


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

    public boolean activityEmbeddingAbortCrossUidLaunchInFinishingTaskFragment() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_ABORT_CROSS_UID_LAUNCH_IN_FINISHING_TASK_FRAGMENT,
            FeatureFlags::activityEmbeddingAbortCrossUidLaunchInFinishingTaskFragment);
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

    public boolean allowDisableActivityRecordInputSink() {
        return getValue(Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            FeatureFlags::allowDisableActivityRecordInputSink);
    }

    @Override

    public boolean alwaysDrawMagnificationFullscreenBorder() {
        return getValue(Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            FeatureFlags::alwaysDrawMagnificationFullscreenBorder);
    }

    @Override

    public boolean alwaysSeqIdLayout() {
        return getValue(Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT,
            FeatureFlags::alwaysSeqIdLayout);
    }

    @Override

    public boolean alwaysSeqIdLayoutWear() {
        return getValue(Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT_WEAR,
            FeatureFlags::alwaysSeqIdLayoutWear);
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

    public boolean appCompatRefactoringFixMultiwindowTaskHierarchy() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
            FeatureFlags::appCompatRefactoringFixMultiwindowTaskHierarchy);
    }

    @Override

    public boolean appCompatRefactoringForceChangeForLetterboxTransitions() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_FORCE_CHANGE_FOR_LETTERBOX_TRANSITIONS,
            FeatureFlags::appCompatRefactoringForceChangeForLetterboxTransitions);
    }

    @Override

    public boolean appCompatRefactoringRoundedCorners() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS,
            FeatureFlags::appCompatRefactoringRoundedCorners);
    }

    @Override

    public boolean appCompatRefactoringRoundedCornersAnimation() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS_ANIMATION,
            FeatureFlags::appCompatRefactoringRoundedCornersAnimation);
    }

    @Override

    public boolean appCompatRefactoringSetAppboundsToNullWhenEmpty() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_SET_APPBOUNDS_TO_NULL_WHEN_EMPTY,
            FeatureFlags::appCompatRefactoringSetAppboundsToNullWhenEmpty);
    }

    @Override

    public boolean appCompatRefactoringSkipStartingWindowLetterbox() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_SKIP_STARTING_WINDOW_LETTERBOX,
            FeatureFlags::appCompatRefactoringSkipStartingWindowLetterbox);
    }

    @Override

    public boolean appCompatRefactoringUseActivityLeashForLetterboxing() {
        return getValue(Flags.FLAG_APP_COMPAT_REFACTORING_USE_ACTIVITY_LEASH_FOR_LETTERBOXING,
            FeatureFlags::appCompatRefactoringUseActivityLeashForLetterboxing);
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

    public boolean applyDeskActivationOnUserSwitch() {
        return getValue(Flags.FLAG_APPLY_DESK_ACTIVATION_ON_USER_SWITCH,
            FeatureFlags::applyDeskActivationOnUserSwitch);
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

    public boolean balAdditionalStartModes() {
        return getValue(Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            FeatureFlags::balAdditionalStartModes);
    }

    @Override

    public boolean balCheckBroadcastWhenDispatched() {
        return getValue(Flags.FLAG_BAL_CHECK_BROADCAST_WHEN_DISPATCHED,
            FeatureFlags::balCheckBroadcastWhenDispatched);
    }

    @Override

    public boolean balCoverIntentSender() {
        return getValue(Flags.FLAG_BAL_COVER_INTENT_SENDER,
            FeatureFlags::balCoverIntentSender);
    }

    @Override

    public boolean balDontBringExistingBackgroundTaskStackToFg() {
        return getValue(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            FeatureFlags::balDontBringExistingBackgroundTaskStackToFg);
    }

    @Override

    public boolean balReduceGracePeriod() {
        return getValue(Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            FeatureFlags::balReduceGracePeriod);
    }

    @Override

    public boolean balReportAbortedActivityStarts() {
        return getValue(Flags.FLAG_BAL_REPORT_ABORTED_ACTIVITY_STARTS,
            FeatureFlags::balReportAbortedActivityStarts);
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

    public boolean balStrictModeRo() {
        return getValue(Flags.FLAG_BAL_STRICT_MODE_RO,
            FeatureFlags::balStrictModeRo);
    }

    @Override

    public boolean cameraCompatFullscreenPickSameTaskActivity() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            FeatureFlags::cameraCompatFullscreenPickSameTaskActivity);
    }

    @Override

    public boolean cameraCompatLandscapeCameraSupport() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT,
            FeatureFlags::cameraCompatLandscapeCameraSupport);
    }

    @Override

    public boolean cameraCompatUnifyCameraPolicies() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES,
            FeatureFlags::cameraCompatUnifyCameraPolicies);
    }

    @Override

    public boolean clearReusableScvhOnRelease() {
        return getValue(Flags.FLAG_CLEAR_REUSABLE_SCVH_ON_RELEASE,
            FeatureFlags::clearReusableScvhOnRelease);
    }

    @Override

    public boolean closeFullscreenAndSplitscreenKeyboardShortcut() {
        return getValue(Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT,
            FeatureFlags::closeFullscreenAndSplitscreenKeyboardShortcut);
    }

    @Override

    public boolean closeTaskKeyboardShortcut() {
        return getValue(Flags.FLAG_CLOSE_TASK_KEYBOARD_SHORTCUT,
            FeatureFlags::closeTaskKeyboardShortcut);
    }

    @Override

    public boolean closeToSquareConfigIncludesStatusBar() {
        return getValue(Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            FeatureFlags::closeToSquareConfigIncludesStatusBar);
    }

    @Override

    public boolean coverDisplayOptIn() {
        return getValue(Flags.FLAG_COVER_DISPLAY_OPT_IN,
            FeatureFlags::coverDisplayOptIn);
    }

    @Override

    public boolean currentAnimatorScaleUsesSharedMemory() {
        return getValue(Flags.FLAG_CURRENT_ANIMATOR_SCALE_USES_SHARED_MEMORY,
            FeatureFlags::currentAnimatorScaleUsesSharedMemory);
    }

    @Override

    public boolean defaultDeskWithoutWarmupMigration() {
        return getValue(Flags.FLAG_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION,
            FeatureFlags::defaultDeskWithoutWarmupMigration);
    }

    @Override

    public boolean deferResumeFocusInNonFocusedWindow() {
        return getValue(Flags.FLAG_DEFER_RESUME_FOCUS_IN_NON_FOCUSED_WINDOW,
            FeatureFlags::deferResumeFocusInNonFocusedWindow);
    }

    @Override

    public boolean density390Api() {
        return getValue(Flags.FLAG_DENSITY_390_API,
            FeatureFlags::density390Api);
    }

    @Override

    public boolean deprecateSurfaceAnimationFrameCallback() {
        return getValue(Flags.FLAG_DEPRECATE_SURFACE_ANIMATION_FRAME_CALLBACK,
            FeatureFlags::deprecateSurfaceAnimationFrameCallback);
    }

    @Override

    public boolean deprecateWindowAnimatorFrameCallback() {
        return getValue(Flags.FLAG_DEPRECATE_WINDOW_ANIMATOR_FRAME_CALLBACK,
            FeatureFlags::deprecateWindowAnimatorFrameCallback);
    }

    @Override

    public boolean dimmingWallpaperForMaximizedAndTiled() {
        return getValue(Flags.FLAG_DIMMING_WALLPAPER_FOR_MAXIMIZED_AND_TILED,
            FeatureFlags::dimmingWallpaperForMaximizedAndTiled);
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

    public boolean disableRestoreNonFullscreenBoundsOnConfigurationChange() {
        return getValue(Flags.FLAG_DISABLE_RESTORE_NON_FULLSCREEN_BOUNDS_ON_CONFIGURATION_CHANGE,
            FeatureFlags::disableRestoreNonFullscreenBoundsOnConfigurationChange);
    }

    @Override

    public boolean doNotForceWallpaperForFreeformTask() {
        return getValue(Flags.FLAG_DO_NOT_FORCE_WALLPAPER_FOR_FREEFORM_TASK,
            FeatureFlags::doNotForceWallpaperForFreeformTask);
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

    public boolean enableAppHandlePositionReporting() {
        return getValue(Flags.FLAG_ENABLE_APP_HANDLE_POSITION_REPORTING,
            FeatureFlags::enableAppHandlePositionReporting);
    }

    @Override

    public boolean enableAppHeaderWithTaskDensity() {
        return getValue(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            FeatureFlags::enableAppHeaderWithTaskDensity);
    }

    @Override

    public boolean enableAppToWebEducationAnimation() {
        return getValue(Flags.FLAG_ENABLE_APP_TO_WEB_EDUCATION_ANIMATION,
            FeatureFlags::enableAppToWebEducationAnimation);
    }

    @Override

    public boolean enableAutoRestartOnDisplayMove() {
        return getValue(Flags.FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE,
            FeatureFlags::enableAutoRestartOnDisplayMove);
    }

    @Override

    public boolean enableBackupAndRestoreDisplayWindowSettings() {
        return getValue(Flags.FLAG_ENABLE_BACKUP_AND_RESTORE_DISPLAY_WINDOW_SETTINGS,
            FeatureFlags::enableBackupAndRestoreDisplayWindowSettings);
    }

    @Override

    public boolean enableBlockNonDesktopDisplayWindowDragBugfix() {
        return getValue(Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
            FeatureFlags::enableBlockNonDesktopDisplayWindowDragBugfix);
    }

    @Override

    public boolean enableBorderSettings() {
        return getValue(Flags.FLAG_ENABLE_BORDER_SETTINGS,
            FeatureFlags::enableBorderSettings);
    }

    @Override

    public boolean enableBoundsRestoringOnTilingExit() {
        return getValue(Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_TILING_EXIT,
            FeatureFlags::enableBoundsRestoringOnTilingExit);
    }

    @Override

    public boolean enableBoxShadowSettings() {
        return getValue(Flags.FLAG_ENABLE_BOX_SHADOW_SETTINGS,
            FeatureFlags::enableBoxShadowSettings);
    }

    @Override

    public boolean enableBugFixesForSecondaryDisplay() {
        return getValue(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            FeatureFlags::enableBugFixesForSecondaryDisplay);
    }

    @Override

    public boolean enableCameraCompatCompatibilityInfoRotateAndCropBugfix() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_COMPATIBILITY_INFO_ROTATE_AND_CROP_BUGFIX,
            FeatureFlags::enableCameraCompatCompatibilityInfoRotateAndCropBugfix);
    }

    @Override

    public boolean enableCameraCompatExternalDisplayRotationBugfix() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            FeatureFlags::enableCameraCompatExternalDisplayRotationBugfix);
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

    public boolean enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX,
            FeatureFlags::enableCameraCompatSandboxDisplayRotationOnExternalDisplaysBugfix);
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

    public boolean enableCloseLidInteraction() {
        return getValue(Flags.FLAG_ENABLE_CLOSE_LID_INTERACTION,
            FeatureFlags::enableCloseLidInteraction);
    }

    @Override

    public boolean enableCompatUiDesktopModeSynchronizationBugfix() {
        return getValue(Flags.FLAG_ENABLE_COMPAT_UI_DESKTOP_MODE_SYNCHRONIZATION_BUGFIX,
            FeatureFlags::enableCompatUiDesktopModeSynchronizationBugfix);
    }

    @Override

    public boolean enableCompatUiVisibilityStatus() {
        return getValue(Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            FeatureFlags::enableCompatUiVisibilityStatus);
    }

    @Override

    public boolean enableCompatuiSysuiLauncherFix() {
        return getValue(Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER_FIX,
            FeatureFlags::enableCompatuiSysuiLauncherFix);
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

    public boolean enableConnectedDisplaysWallpaperPresentations() {
        return getValue(Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WALLPAPER_PRESENTATIONS,
            FeatureFlags::enableConnectedDisplaysWallpaperPresentations);
    }

    @Override

    public boolean enableConnectedDisplaysWindowDrag() {
        return getValue(Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            FeatureFlags::enableConnectedDisplaysWindowDrag);
    }

    @Override

    public boolean enableCrashLoggingForDesktop() {
        return getValue(Flags.FLAG_ENABLE_CRASH_LOGGING_FOR_DESKTOP,
            FeatureFlags::enableCrashLoggingForDesktop);
    }

    @Override

    public boolean enableCrossDisplaysAppLaunchTransition() {
        return getValue(Flags.FLAG_ENABLE_CROSS_DISPLAYS_APP_LAUNCH_TRANSITION,
            FeatureFlags::enableCrossDisplaysAppLaunchTransition);
    }

    @Override

    public boolean enableCrossDisplaysPipTaskLaunch() {
        return getValue(Flags.FLAG_ENABLE_CROSS_DISPLAYS_PIP_TASK_LAUNCH,
            FeatureFlags::enableCrossDisplaysPipTaskLaunch);
    }

    @Override

    public boolean enableDesktopAppHandleAnimation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            FeatureFlags::enableDesktopAppHandleAnimation);
    }

    @Override

    public boolean enableDesktopAppHeaderStateChangeAnnouncements() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS,
            FeatureFlags::enableDesktopAppHeaderStateChangeAnnouncements);
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

    public boolean enableDesktopAppLaunchBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,
            FeatureFlags::enableDesktopAppLaunchBugfix);
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

    public boolean enableDesktopFirstBasedDefaultToDesktopBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
            FeatureFlags::enableDesktopFirstBasedDefaultToDesktopBugfix);
    }

    @Override

    public boolean enableDesktopFirstBasedDragToMaximize() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE,
            FeatureFlags::enableDesktopFirstBasedDragToMaximize);
    }

    @Override

    public boolean enableDesktopFirstFullscreenRefocusBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,
            FeatureFlags::enableDesktopFirstFullscreenRefocusBugfix);
    }

    @Override

    public boolean enableDesktopFirstListener() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_LISTENER,
            FeatureFlags::enableDesktopFirstListener);
    }

    @Override

    public boolean enableDesktopFirstPolicyInLpm() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            FeatureFlags::enableDesktopFirstPolicyInLpm);
    }

    @Override

    public boolean enableDesktopFirstTopFullscreenBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
            FeatureFlags::enableDesktopFirstTopFullscreenBugfix);
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

    public boolean enableDesktopInvisibleTaskRemovalCleanupBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_INVISIBLE_TASK_REMOVAL_CLEANUP_BUGFIX,
            FeatureFlags::enableDesktopInvisibleTaskRemovalCleanupBugfix);
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

    public boolean enableDesktopSplitscreenTransitionBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_SPLITSCREEN_TRANSITION_BUGFIX,
            FeatureFlags::enableDesktopSplitscreenTransitionBugfix);
    }

    @Override

    public boolean enableDesktopSystemDialogsTransitions() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            FeatureFlags::enableDesktopSystemDialogsTransitions);
    }

    @Override

    public boolean enableDesktopTabTearingLaunchAnimation() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION,
            FeatureFlags::enableDesktopTabTearingLaunchAnimation);
    }

    @Override

    public boolean enableDesktopTabTearingMinimizeAnimationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            FeatureFlags::enableDesktopTabTearingMinimizeAnimationBugfix);
    }

    @Override

    public boolean enableDesktopTaskLimitSeparateTransition() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION,
            FeatureFlags::enableDesktopTaskLimitSeparateTransition);
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

    public boolean enableDesktopWindowingEnterpriseBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX,
            FeatureFlags::enableDesktopWindowingEnterpriseBugfix);
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

    public boolean enableDesktopWindowingPipInOverviewBugfix() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP_IN_OVERVIEW_BUGFIX,
            FeatureFlags::enableDesktopWindowingPipInOverviewBugfix);
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

    public boolean enableDialogDisplayFixes() {
        return getValue(Flags.FLAG_ENABLE_DIALOG_DISPLAY_FIXES,
            FeatureFlags::enableDialogDisplayFixes);
    }

    @Override

    public boolean enableDisplayCompatMode() {
        return getValue(Flags.FLAG_ENABLE_DISPLAY_COMPAT_MODE,
            FeatureFlags::enableDisplayCompatMode);
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

    public boolean enableDragEndStableBoundsReset() {
        return getValue(Flags.FLAG_ENABLE_DRAG_END_STABLE_BOUNDS_RESET,
            FeatureFlags::enableDragEndStableBoundsReset);
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

    public boolean enableDraggingPipAcrossDisplays() {
        return getValue(Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS,
            FeatureFlags::enableDraggingPipAcrossDisplays);
    }

    @Override

    public boolean enableDrawingAppHandle() {
        return getValue(Flags.FLAG_ENABLE_DRAWING_APP_HANDLE,
            FeatureFlags::enableDrawingAppHandle);
    }

    @Override

    public boolean enableDreamActivityWindowingExclusion() {
        return getValue(Flags.FLAG_ENABLE_DREAM_ACTIVITY_WINDOWING_EXCLUSION,
            FeatureFlags::enableDreamActivityWindowingExclusion);
    }

    @Override

    public boolean enableDynamicRadiusComputationBugfix() {
        return getValue(Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            FeatureFlags::enableDynamicRadiusComputationBugfix);
    }

    @Override

    public boolean enableEmptyDeskOnMinimize() {
        return getValue(Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
            FeatureFlags::enableEmptyDeskOnMinimize);
    }

    @Override

    public boolean enableExperimentalBubblesController() {
        return getValue(Flags.FLAG_ENABLE_EXPERIMENTAL_BUBBLES_CONTROLLER,
            FeatureFlags::enableExperimentalBubblesController);
    }

    @Override

    public boolean enableExternalDisplayPersistenceBugfix() {
        return getValue(Flags.FLAG_ENABLE_EXTERNAL_DISPLAY_PERSISTENCE_BUGFIX,
            FeatureFlags::enableExternalDisplayPersistenceBugfix);
    }

    @Override

    public boolean enableFreeformBoxShadows() {
        return getValue(Flags.FLAG_ENABLE_FREEFORM_BOX_SHADOWS,
            FeatureFlags::enableFreeformBoxShadows);
    }

    @Override

    public boolean enableFreeformDisplayLaunchParams() {
        return getValue(Flags.FLAG_ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,
            FeatureFlags::enableFreeformDisplayLaunchParams);
    }

    @Override

    public boolean enableFullScreenWindowOnRemovingSplitScreenStageBugfix() {
        return getValue(Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            FeatureFlags::enableFullScreenWindowOnRemovingSplitScreenStageBugfix);
    }

    @Override

    public boolean enableFullscreenWindowControls() {
        return getValue(Flags.FLAG_ENABLE_FULLSCREEN_WINDOW_CONTROLS,
            FeatureFlags::enableFullscreenWindowControls);
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

    public boolean enableHandlersDebuggingMode() {
        return getValue(Flags.FLAG_ENABLE_HANDLERS_DEBUGGING_MODE,
            FeatureFlags::enableHandlersDebuggingMode);
    }

    @Override

    public boolean enableHoldToDragAppHandle() {
        return getValue(Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            FeatureFlags::enableHoldToDragAppHandle);
    }

    @Override

    public boolean enableIndependentBackInProjected() {
        return getValue(Flags.FLAG_ENABLE_INDEPENDENT_BACK_IN_PROJECTED,
            FeatureFlags::enableIndependentBackInProjected);
    }

    @Override

    public boolean enableInorderTransitionCallbacksForDesktop() {
        return getValue(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP,
            FeatureFlags::enableInorderTransitionCallbacksForDesktop);
    }

    @Override

    public boolean enableInputLayerTransitionFix() {
        return getValue(Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            FeatureFlags::enableInputLayerTransitionFix);
    }

    @Override

    public boolean enableInteractionDependentTabTearingBounds() {
        return getValue(Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS,
            FeatureFlags::enableInteractionDependentTabTearingBounds);
    }

    @Override

    public boolean enableInteractivePictureInPicture() {
        return getValue(Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE,
            FeatureFlags::enableInteractivePictureInPicture);
    }

    @Override

    public boolean enableKeyGestureHandlerForSysui() {
        return getValue(Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI,
            FeatureFlags::enableKeyGestureHandlerForSysui);
    }

    @Override

    public boolean enableLauncherHandleGoHomeKeyboardShortcut() {
        return getValue(Flags.FLAG_ENABLE_LAUNCHER_HANDLE_GO_HOME_KEYBOARD_SHORTCUT,
            FeatureFlags::enableLauncherHandleGoHomeKeyboardShortcut);
    }

    @Override

    public boolean enableMinimizeButton() {
        return getValue(Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            FeatureFlags::enableMinimizeButton);
    }

    @Override

    public boolean enableMirrorDisplayNoActivity() {
        return getValue(Flags.FLAG_ENABLE_MIRROR_DISPLAY_NO_ACTIVITY,
            FeatureFlags::enableMirrorDisplayNoActivity);
    }

    @Override

    public boolean enableModalsFullscreenWithPermission() {
        return getValue(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            FeatureFlags::enableModalsFullscreenWithPermission);
    }

    @Override

    public boolean enableModalsFullscreenWithPlatformSignature() {
        return getValue(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE,
            FeatureFlags::enableModalsFullscreenWithPlatformSignature);
    }

    @Override

    public boolean enableMoveToNextDisplayShortcut() {
        return getValue(Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            FeatureFlags::enableMoveToNextDisplayShortcut);
    }

    @Override

    public boolean enableMultiDisplayHomeFocusBugFix() {
        return getValue(Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX,
            FeatureFlags::enableMultiDisplayHomeFocusBugFix);
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

    public boolean enableMultipleDesktopsDefaultActivationInDesktopFirstDisplays() {
        return getValue(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
            FeatureFlags::enableMultipleDesktopsDefaultActivationInDesktopFirstDisplays);
    }

    @Override

    public boolean enableMultipleDesktopsFrontend() {
        return getValue(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            FeatureFlags::enableMultipleDesktopsFrontend);
    }

    @Override

    public boolean enableNoWindowDecorationForDesks() {
        return getValue(Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS,
            FeatureFlags::enableNoWindowDecorationForDesks);
    }

    @Override

    public boolean enableNonDefaultDisplaySplit() {
        return getValue(Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            FeatureFlags::enableNonDefaultDisplaySplit);
    }

    @Override

    public boolean enableNonDefaultDisplaySplitBugfix() {
        return getValue(Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX,
            FeatureFlags::enableNonDefaultDisplaySplitBugfix);
    }

    @Override

    public boolean enableOpaqueBackgroundForTransparentWindows() {
        return getValue(Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            FeatureFlags::enableOpaqueBackgroundForTransparentWindows);
    }

    @Override

    public boolean enableOverflowButtonForTaskbarPinnedItems() {
        return getValue(Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS,
            FeatureFlags::enableOverflowButtonForTaskbarPinnedItems);
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

    public boolean enablePerDisplayWindowDecorViewHostPool() {
        return getValue(Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL,
            FeatureFlags::enablePerDisplayWindowDecorViewHostPool);
    }

    @Override

    public boolean enablePersistingDisplaySizeForConnectedDisplays() {
        return getValue(Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            FeatureFlags::enablePersistingDisplaySizeForConnectedDisplays);
    }

    @Override

    public boolean enablePinningAppWithContextMenu() {
        return getValue(Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU,
            FeatureFlags::enablePinningAppWithContextMenu);
    }

    @Override

    public boolean enablePipParamsUpdateNotificationBugfix() {
        return getValue(Flags.FLAG_ENABLE_PIP_PARAMS_UPDATE_NOTIFICATION_BUGFIX,
            FeatureFlags::enablePipParamsUpdateNotificationBugfix);
    }

    @Override

    public boolean enablePresentationDisallowedOnUnfocusedHostTask() {
        return getValue(Flags.FLAG_ENABLE_PRESENTATION_DISALLOWED_ON_UNFOCUSED_HOST_TASK,
            FeatureFlags::enablePresentationDisallowedOnUnfocusedHostTask);
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

    public boolean enableRejectHomeTransition() {
        return getValue(Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION,
            FeatureFlags::enableRejectHomeTransition);
    }

    @Override

    public boolean enableRemoveStatusBarInputLayer() {
        return getValue(Flags.FLAG_ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER,
            FeatureFlags::enableRemoveStatusBarInputLayer);
    }

    @Override

    public boolean enableRequestFullscreenBugfix() {
        return getValue(Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            FeatureFlags::enableRequestFullscreenBugfix);
    }

    @Override

    public boolean enableRequestFullscreenRefactor() {
        return getValue(Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_REFACTOR,
            FeatureFlags::enableRequestFullscreenRefactor);
    }

    @Override

    public boolean enableRequestFullscreenRestoreFreeformBugfix() {
        return getValue(Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_RESTORE_FREEFORM_BUGFIX,
            FeatureFlags::enableRequestFullscreenRestoreFreeformBugfix);
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

    public boolean enableSeeThroughTaskFragments() {
        return getValue(Flags.FLAG_ENABLE_SEE_THROUGH_TASK_FRAGMENTS,
            FeatureFlags::enableSeeThroughTaskFragments);
    }

    @Override

    public boolean enableShellInitialBoundsRegressionBugFix() {
        return getValue(Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            FeatureFlags::enableShellInitialBoundsRegressionBugFix);
    }

    @Override

    public boolean enableShrinkWindowBoundsAfterDrag() {
        return getValue(Flags.FLAG_ENABLE_SHRINK_WINDOW_BOUNDS_AFTER_DRAG,
            FeatureFlags::enableShrinkWindowBoundsAfterDrag);
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

    public boolean enableSysDecorsCallbacksViaWm() {
        return getValue(Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM,
            FeatureFlags::enableSysDecorsCallbacksViaWm);
    }

    @Override

    public boolean enableTallAppHeaders() {
        return getValue(Flags.FLAG_ENABLE_TALL_APP_HEADERS,
            FeatureFlags::enableTallAppHeaders);
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

    public boolean enableTaskbarRecentTasksThrottleBugfix() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX,
            FeatureFlags::enableTaskbarRecentTasksThrottleBugfix);
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

    public boolean enableTransitionOnActivitySetRequestedOrientation() {
        return getValue(Flags.FLAG_ENABLE_TRANSITION_ON_ACTIVITY_SET_REQUESTED_ORIENTATION,
            FeatureFlags::enableTransitionOnActivitySetRequestedOrientation);
    }

    @Override

    public boolean enableUpdatedDisplayConnectionDialog() {
        return getValue(Flags.FLAG_ENABLE_UPDATED_DISPLAY_CONNECTION_DIALOG,
            FeatureFlags::enableUpdatedDisplayConnectionDialog);
    }

    @Override

    public boolean enableUpscalingSizeCompatOnExitingDesktopBugfix() {
        return getValue(Flags.FLAG_ENABLE_UPSCALING_SIZE_COMPAT_ON_EXITING_DESKTOP_BUGFIX,
            FeatureFlags::enableUpscalingSizeCompatOnExitingDesktopBugfix);
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

    public boolean enableWindowDecorationRefactor() {
        return getValue(Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
            FeatureFlags::enableWindowDecorationRefactor);
    }

    @Override

    public boolean enableWindowDropSmoothTransition() {
        return getValue(Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
            FeatureFlags::enableWindowDropSmoothTransition);
    }

    @Override

    public boolean enableWindowRepositioningApi() {
        return getValue(Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
            FeatureFlags::enableWindowRepositioningApi);
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

    public boolean enableWindowingTaskStackOrderBugfix() {
        return getValue(Flags.FLAG_ENABLE_WINDOWING_TASK_STACK_ORDER_BUGFIX,
            FeatureFlags::enableWindowingTaskStackOrderBugfix);
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

    public boolean ensureKeyguardDoesTransitionStartingBugFix() {
        return getValue(Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING_BUG_FIX,
            FeatureFlags::ensureKeyguardDoesTransitionStartingBugFix);
    }

    @Override

    public boolean ensureWallpaperDrawnOnDisplaySwitch() {
        return getValue(Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
            FeatureFlags::ensureWallpaperDrawnOnDisplaySwitch);
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

    public boolean excludeDeskRootsFromDesktopTasks() {
        return getValue(Flags.FLAG_EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS,
            FeatureFlags::excludeDeskRootsFromDesktopTasks);
    }

    @Override

    public boolean excludingLayerFromTaskSnapshot() {
        return getValue(Flags.FLAG_EXCLUDING_LAYER_FROM_TASK_SNAPSHOT,
            FeatureFlags::excludingLayerFromTaskSnapshot);
    }

    @Override

    public boolean fallbackToFocusedDisplay() {
        return getValue(Flags.FLAG_FALLBACK_TO_FOCUSED_DISPLAY,
            FeatureFlags::fallbackToFocusedDisplay);
    }

    @Override

    public boolean fallbackTransitionPlayer() {
        return getValue(Flags.FLAG_FALLBACK_TRANSITION_PLAYER,
            FeatureFlags::fallbackTransitionPlayer);
    }

    @Override

    public boolean fifoPriorityForMajorUiProcesses() {
        return getValue(Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            FeatureFlags::fifoPriorityForMajorUiProcesses);
    }

    @Override

    public boolean fixBalReparentExistingTask() {
        return getValue(Flags.FLAG_FIX_BAL_REPARENT_EXISTING_TASK,
            FeatureFlags::fixBalReparentExistingTask);
    }

    @Override

    public boolean fixBubbleTrampolineAnimation() {
        return getValue(Flags.FLAG_FIX_BUBBLE_TRAMPOLINE_ANIMATION,
            FeatureFlags::fixBubbleTrampolineAnimation);
    }

    @Override

    public boolean fixHideOverlayApi() {
        return getValue(Flags.FLAG_FIX_HIDE_OVERLAY_API,
            FeatureFlags::fixHideOverlayApi);
    }

    @Override

    public boolean fixLeakingVisualIndicator() {
        return getValue(Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
            FeatureFlags::fixLeakingVisualIndicator);
    }

    @Override

    public boolean fixRapidTopResumedSwitch() {
        return getValue(Flags.FLAG_FIX_RAPID_TOP_RESUMED_SWITCH,
            FeatureFlags::fixRapidTopResumedSwitch);
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

    public boolean grantManageKeyGesturesToRecents() {
        return getValue(Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS,
            FeatureFlags::grantManageKeyGesturesToRecents);
    }

    @Override

    public boolean handleIncompatibleTasksInDesktopLaunchParams() {
        return getValue(Flags.FLAG_HANDLE_INCOMPATIBLE_TASKS_IN_DESKTOP_LAUNCH_PARAMS,
            FeatureFlags::handleIncompatibleTasksInDesktopLaunchParams);
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

    public boolean ignoreCurrentParamsInDesktopLaunchParams() {
        return getValue(Flags.FLAG_IGNORE_CURRENT_PARAMS_IN_DESKTOP_LAUNCH_PARAMS,
            FeatureFlags::ignoreCurrentParamsInDesktopLaunchParams);
    }

    @Override

    public boolean ignoreOverrideTaskBoundsIfIncompatibleWithDisplay() {
        return getValue(Flags.FLAG_IGNORE_OVERRIDE_TASK_BOUNDS_IF_INCOMPATIBLE_WITH_DISPLAY,
            FeatureFlags::ignoreOverrideTaskBoundsIfIncompatibleWithDisplay);
    }

    @Override

    public boolean imeBackCallbackLeakPrevention() {
        return getValue(Flags.FLAG_IME_BACK_CALLBACK_LEAK_PREVENTION,
            FeatureFlags::imeBackCallbackLeakPrevention);
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

    public boolean jankApi() {
        return getValue(Flags.FLAG_JANK_API,
            FeatureFlags::jankApi);
    }

    @Override

    public boolean keyboardShortcutsToSwitchDesks() {
        return getValue(Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            FeatureFlags::keyboardShortcutsToSwitchDesks);
    }

    @Override

    public boolean keyguardRemoveDefaultDisplayUsage() {
        return getValue(Flags.FLAG_KEYGUARD_REMOVE_DEFAULT_DISPLAY_USAGE,
            FeatureFlags::keyguardRemoveDefaultDisplayUsage);
    }

    @Override

    public boolean letterboxBackgroundWallpaper() {
        return getValue(Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            FeatureFlags::letterboxBackgroundWallpaper);
    }

    @Override

    public boolean limitSystemFullscreenOverrideToDefaultDisplay() {
        return getValue(Flags.FLAG_LIMIT_SYSTEM_FULLSCREEN_OVERRIDE_TO_DEFAULT_DISPLAY,
            FeatureFlags::limitSystemFullscreenOverrideToDefaultDisplay);
    }

    @Override

    public boolean migrateBasicLegacyReady() {
        return getValue(Flags.FLAG_MIGRATE_BASIC_LEGACY_READY,
            FeatureFlags::migrateBasicLegacyReady);
    }

    @Override

    public boolean moveToExternalDisplayShortcut() {
        return getValue(Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            FeatureFlags::moveToExternalDisplayShortcut);
    }

    @Override

    public boolean moveToNextDisplayShortcutWithProjectedMode() {
        return getValue(Flags.FLAG_MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE,
            FeatureFlags::moveToNextDisplayShortcutWithProjectedMode);
    }

    @Override

    public boolean multiCrop() {
        return getValue(Flags.FLAG_MULTI_CROP,
            FeatureFlags::multiCrop);
    }

    @Override

    public boolean multipleSystemNavigationObserverCallbacks() {
        return getValue(Flags.FLAG_MULTIPLE_SYSTEM_NAVIGATION_OBSERVER_CALLBACKS,
            FeatureFlags::multipleSystemNavigationObserverCallbacks);
    }

    @Override

    public boolean nestedTasksWithIndependentBoundsBugfix() {
        return getValue(Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS_BUGFIX,
            FeatureFlags::nestedTasksWithIndependentBoundsBugfix);
    }

    @Override

    public boolean noAlphaRotationEnterAnimation() {
        return getValue(Flags.FLAG_NO_ALPHA_ROTATION_ENTER_ANIMATION,
            FeatureFlags::noAlphaRotationEnterAnimation);
    }

    @Override

    public boolean optOutOverrideOrientationToUser() {
        return getValue(Flags.FLAG_OPT_OUT_OVERRIDE_ORIENTATION_TO_USER,
            FeatureFlags::optOutOverrideOrientationToUser);
    }

    @Override

    public boolean parallelCdTransitionsDuringRecents() {
        return getValue(Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS,
            FeatureFlags::parallelCdTransitionsDuringRecents);
    }

    @Override

    public boolean polishCloseWallpaperIncludesOpenChange() {
        return getValue(Flags.FLAG_POLISH_CLOSE_WALLPAPER_INCLUDES_OPEN_CHANGE,
            FeatureFlags::polishCloseWallpaperIncludesOpenChange);
    }

    @Override

    public boolean portWindowSizeAnimation() {
        return getValue(Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            FeatureFlags::portWindowSizeAnimation);
    }

    @Override

    public boolean predictiveBackCallbackCancellationFix() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_CALLBACK_CANCELLATION_FIX,
            FeatureFlags::predictiveBackCallbackCancellationFix);
    }

    @Override

    public boolean predictiveBackDelayWmTransition() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            FeatureFlags::predictiveBackDelayWmTransition);
    }

    @Override

    public boolean predictiveBackInterceptTransition() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_INTERCEPT_TRANSITION,
            FeatureFlags::predictiveBackInterceptTransition);
    }

    @Override

    public boolean predictiveBackPrioritySystemNavigationObserver() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            FeatureFlags::predictiveBackPrioritySystemNavigationObserver);
    }

    @Override

    public boolean predictiveBackStopKeycodeBackForwarding() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_STOP_KEYCODE_BACK_FORWARDING,
            FeatureFlags::predictiveBackStopKeycodeBackForwarding);
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

    public boolean predictiveBackTimestampApi() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            FeatureFlags::predictiveBackTimestampApi);
    }

    @Override

    public boolean preserveRecentsTaskConfigurationOnRelaunch() {
        return getValue(Flags.FLAG_PRESERVE_RECENTS_TASK_CONFIGURATION_ON_RELAUNCH,
            FeatureFlags::preserveRecentsTaskConfigurationOnRelaunch);
    }

    @Override

    public boolean rearDisplayDisableForceDesktopSystemDecorations() {
        return getValue(Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            FeatureFlags::rearDisplayDisableForceDesktopSystemDecorations);
    }

    @Override

    public boolean reduceChangedExclusionRectsMsgs() {
        return getValue(Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            FeatureFlags::reduceChangedExclusionRectsMsgs);
    }

    @Override

    public boolean reduceTaskSnapshotMemoryUsage() {
        return getValue(Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            FeatureFlags::reduceTaskSnapshotMemoryUsage);
    }

    @Override

    public boolean reenableAppHandleAnimations() {
        return getValue(Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
            FeatureFlags::reenableAppHandleAnimations);
    }

    @Override

    public boolean reenableAppHandleColorAnimations() {
        return getValue(Flags.FLAG_REENABLE_APP_HANDLE_COLOR_ANIMATIONS,
            FeatureFlags::reenableAppHandleColorAnimations);
    }

    @Override

    public boolean refactorMatchParentBounds() {
        return getValue(Flags.FLAG_REFACTOR_MATCH_PARENT_BOUNDS,
            FeatureFlags::refactorMatchParentBounds);
    }

    @Override

    public boolean relativeInsets() {
        return getValue(Flags.FLAG_RELATIVE_INSETS,
            FeatureFlags::relativeInsets);
    }

    @Override

    public boolean releaseAllTransitionSurfaces() {
        return getValue(Flags.FLAG_RELEASE_ALL_TRANSITION_SURFACES,
            FeatureFlags::releaseAllTransitionSurfaces);
    }

    @Override

    public boolean removeDeskOnLastTaskRemoval() {
        return getValue(Flags.FLAG_REMOVE_DESK_ON_LAST_TASK_REMOVAL,
            FeatureFlags::removeDeskOnLastTaskRemoval);
    }

    @Override

    public boolean removeGetDimmer() {
        return getValue(Flags.FLAG_REMOVE_GET_DIMMER,
            FeatureFlags::removeGetDimmer);
    }

    @Override

    public boolean reparentWindowTokenApi() {
        return getValue(Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            FeatureFlags::reparentWindowTokenApi);
    }

    @Override

    public boolean repositoryBasedPersistence() {
        return getValue(Flags.FLAG_REPOSITORY_BASED_PERSISTENCE,
            FeatureFlags::repositoryBasedPersistence);
    }

    @Override

    public boolean repositoryBasedPersistenceBgThread() {
        return getValue(Flags.FLAG_REPOSITORY_BASED_PERSISTENCE_BG_THREAD,
            FeatureFlags::repositoryBasedPersistenceBgThread);
    }

    @Override

    public boolean respectFullscreenActivityOptionInDesktopLaunchParams() {
        return getValue(Flags.FLAG_RESPECT_FULLSCREEN_ACTIVITY_OPTION_IN_DESKTOP_LAUNCH_PARAMS,
            FeatureFlags::respectFullscreenActivityOptionInDesktopLaunchParams);
    }

    @Override

    public boolean respectOrientationChangeForUnresizeable() {
        return getValue(Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            FeatureFlags::respectOrientationChangeForUnresizeable);
    }

    @Override

    public boolean respectRequestedTaskSnapshotResolution() {
        return getValue(Flags.FLAG_RESPECT_REQUESTED_TASK_SNAPSHOT_RESOLUTION,
            FeatureFlags::respectRequestedTaskSnapshotResolution);
    }

    @Override

    public boolean restoreUserAspectRatioSettingsUsingService() {
        return getValue(Flags.FLAG_RESTORE_USER_ASPECT_RATIO_SETTINGS_USING_SERVICE,
            FeatureFlags::restoreUserAspectRatioSettingsUsingService);
    }

    @Override

    public boolean restrictFreeformHiddenSystemBarsToFillingTasks() {
        return getValue(Flags.FLAG_RESTRICT_FREEFORM_HIDDEN_SYSTEM_BARS_TO_FILLING_TASKS,
            FeatureFlags::restrictFreeformHiddenSystemBarsToFillingTasks);
    }

    @Override

    public boolean returnAllVisibleActivitiesForVis() {
        return getValue(Flags.FLAG_RETURN_ALL_VISIBLE_ACTIVITIES_FOR_VIS,
            FeatureFlags::returnAllVisibleActivitiesForVis);
    }

    @Override

    public boolean rootTaskForBubble() {
        return getValue(Flags.FLAG_ROOT_TASK_FOR_BUBBLE,
            FeatureFlags::rootTaskForBubble);
    }

    @Override

    public boolean safeRegionLetterboxingV1() {
        return getValue(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1,
            FeatureFlags::safeRegionLetterboxingV1);
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

    public boolean scvhSetFocusable() {
        return getValue(Flags.FLAG_SCVH_SET_FOCUSABLE,
            FeatureFlags::scvhSetFocusable);
    }

    @Override

    public boolean scvhSurfaceControlLifetimeFix() {
        return getValue(Flags.FLAG_SCVH_SURFACE_CONTROL_LIFETIME_FIX,
            FeatureFlags::scvhSurfaceControlLifetimeFix);
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

    public boolean showBiometricPromptSecondaryDisplayMessage() {
        return getValue(Flags.FLAG_SHOW_BIOMETRIC_PROMPT_SECONDARY_DISPLAY_MESSAGE,
            FeatureFlags::showBiometricPromptSecondaryDisplayMessage);
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

    public boolean skipDeactivationOfDeskWithNothingInFront() {
        return getValue(Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
            FeatureFlags::skipDeactivationOfDeskWithNothingInFront);
    }

    @Override

    public boolean skipDecorViewRelayoutWhenClosingBugfix() {
        return getValue(Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            FeatureFlags::skipDecorViewRelayoutWhenClosingBugfix);
    }

    @Override

    public boolean supportGeminiOnMultiDisplay() {
        return getValue(Flags.FLAG_SUPPORT_GEMINI_ON_MULTI_DISPLAY,
            FeatureFlags::supportGeminiOnMultiDisplay);
    }

    @Override

    public boolean systemContentPriority() {
        return getValue(Flags.FLAG_SYSTEM_CONTENT_PRIORITY,
            FeatureFlags::systemContentPriority);
    }

    @Override

    public boolean systemUiPostAnimationEnd() {
        return getValue(Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            FeatureFlags::systemUiPostAnimationEnd);
    }

    @Override

    public boolean taskFragmentCompanionActivity() {
        return getValue(Flags.FLAG_TASK_FRAGMENT_COMPANION_ACTIVITY,
            FeatureFlags::taskFragmentCompanionActivity);
    }

    @Override

    public boolean taskbarRunningTasksInSplitscreenSelect() {
        return getValue(Flags.FLAG_TASKBAR_RUNNING_TASKS_IN_SPLITSCREEN_SELECT,
            FeatureFlags::taskbarRunningTasksInSplitscreenSelect);
    }

    @Override

    public boolean toggleFullscreenStateViaFullscreenKey() {
        return getValue(Flags.FLAG_TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY,
            FeatureFlags::toggleFullscreenStateViaFullscreenKey);
    }

    @Override

    public boolean touchPassThroughOptIn() {
        return getValue(Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            FeatureFlags::touchPassThroughOptIn);
    }

    @Override

    public boolean transferStartingWindowToNextWhenInvisible() {
        return getValue(Flags.FLAG_TRANSFER_STARTING_WINDOW_TO_NEXT_WHEN_INVISIBLE,
            FeatureFlags::transferStartingWindowToNextWhenInvisible);
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

    public boolean transitionHandlerCujTags() {
        return getValue(Flags.FLAG_TRANSITION_HANDLER_CUJ_TAGS,
            FeatureFlags::transitionHandlerCujTags);
    }

    @Override

    public boolean unifyShellBinders() {
        return getValue(Flags.FLAG_UNIFY_SHELL_BINDERS,
            FeatureFlags::unifyShellBinders);
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

    public boolean updateTaskCropInSync() {
        return getValue(Flags.FLAG_UPDATE_TASK_CROP_IN_SYNC,
            FeatureFlags::updateTaskCropInSync);
    }

    @Override

    public boolean useInputReportedFocusForAccessibility() {
        return getValue(Flags.FLAG_USE_INPUT_REPORTED_FOCUS_FOR_ACCESSIBILITY,
            FeatureFlags::useInputReportedFocusForAccessibility);
    }

    @Override

    public boolean vdmForceAppUniversalResizableApi() {
        return getValue(Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            FeatureFlags::vdmForceAppUniversalResizableApi);
    }

    @Override

    public boolean waitForPresentFenceOnDisplaySwitch() {
        return getValue(Flags.FLAG_WAIT_FOR_PRESENT_FENCE_ON_DISPLAY_SWITCH,
            FeatureFlags::waitForPresentFenceOnDisplaySwitch);
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
            Flags.FLAG_ACTIVITY_EMBEDDING_ABORT_CROSS_UID_LAUNCH_IN_FINISHING_TASK_FRAGMENT,
            Flags.FLAG_ACTIVITY_EMBEDDING_DELAY_TASK_FRAGMENT_FINISH_FOR_ACTIVITY_LAUNCH,
            Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_METRICS,
            Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT,
            Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT_WEAR,
            Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
            Flags.FLAG_AOD_TRANSITION,
            Flags.FLAG_APP_COMPAT_PROPERTIES_API,
            Flags.FLAG_APP_COMPAT_REFACTORING,
            Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
            Flags.FLAG_APP_COMPAT_REFACTORING_FORCE_CHANGE_FOR_LETTERBOX_TRANSITIONS,
            Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS,
            Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS_ANIMATION,
            Flags.FLAG_APP_COMPAT_REFACTORING_SET_APPBOUNDS_TO_NULL_WHEN_EMPTY,
            Flags.FLAG_APP_COMPAT_REFACTORING_SKIP_STARTING_WINDOW_LETTERBOX,
            Flags.FLAG_APP_COMPAT_REFACTORING_USE_ACTIVITY_LEASH_FOR_LETTERBOXING,
            Flags.FLAG_APP_COMPAT_UI_FRAMEWORK,
            Flags.FLAG_APP_HANDLE_NO_RELAYOUT_ON_EXCLUSION_CHANGE,
            Flags.FLAG_APPLY_DESK_ACTIVATION_ON_USER_SWITCH,
            Flags.FLAG_APPLY_LIFECYCLE_ON_PIP_CHANGE,
            Flags.FLAG_AVOID_REBINDING_INTENTIONALLY_DISCONNECTED_WALLPAPER,
            Flags.FLAG_BACKUP_AND_RESTORE_FOR_USER_ASPECT_RATIO_SETTINGS,
            Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            Flags.FLAG_BAL_CHECK_BROADCAST_WHEN_DISPATCHED,
            Flags.FLAG_BAL_COVER_INTENT_SENDER,
            Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            Flags.FLAG_BAL_REPORT_ABORTED_ACTIVITY_STARTS,
            Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
            Flags.FLAG_BAL_SEND_INTENT_WITH_OPTIONS,
            Flags.FLAG_BAL_STRICT_MODE_RO,
            Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            Flags.FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT,
            Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES,
            Flags.FLAG_CLEAR_REUSABLE_SCVH_ON_RELEASE,
            Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT,
            Flags.FLAG_CLOSE_TASK_KEYBOARD_SHORTCUT,
            Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            Flags.FLAG_COVER_DISPLAY_OPT_IN,
            Flags.FLAG_CURRENT_ANIMATOR_SCALE_USES_SHARED_MEMORY,
            Flags.FLAG_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION,
            Flags.FLAG_DEFER_RESUME_FOCUS_IN_NON_FOCUSED_WINDOW,
            Flags.FLAG_DENSITY_390_API,
            Flags.FLAG_DEPRECATE_SURFACE_ANIMATION_FRAME_CALLBACK,
            Flags.FLAG_DEPRECATE_WINDOW_ANIMATOR_FRAME_CALLBACK,
            Flags.FLAG_DIMMING_WALLPAPER_FOR_MAXIMIZED_AND_TILED,
            Flags.FLAG_DISABLE_DESKTOP_LAUNCH_PARAMS_OUTSIDE_DESKTOP_BUG_FIX,
            Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING,
            Flags.FLAG_DISABLE_RESTORE_NON_FULLSCREEN_BOUNDS_ON_CONFIGURATION_CHANGE,
            Flags.FLAG_DO_NOT_FORCE_WALLPAPER_FOR_FREEFORM_TASK,
            Flags.FLAG_ENABLE_ACCESSIBLE_CUSTOM_HEADERS,
            Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_APP_HANDLE_POSITION_REPORTING,
            Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            Flags.FLAG_ENABLE_APP_TO_WEB_EDUCATION_ANIMATION,
            Flags.FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE,
            Flags.FLAG_ENABLE_BACKUP_AND_RESTORE_DISPLAY_WINDOW_SETTINGS,
            Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_BORDER_SETTINGS,
            Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_TILING_EXIT,
            Flags.FLAG_ENABLE_BOX_SHADOW_SETTINGS,
            Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_COMPATIBILITY_INFO_ROTATE_AND_CROP_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT_API,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_CONVERSION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS,
            Flags.FLAG_ENABLE_CASCADING_WINDOWS,
            Flags.FLAG_ENABLE_CLOSE_LID_INTERACTION,
            Flags.FLAG_ENABLE_COMPAT_UI_DESKTOP_MODE_SYNCHRONIZATION_BUGFIX,
            Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER_FIX,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WALLPAPER_PRESENTATIONS,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            Flags.FLAG_ENABLE_CRASH_LOGGING_FOR_DESKTOP,
            Flags.FLAG_ENABLE_CROSS_DISPLAYS_APP_LAUNCH_TRANSITION,
            Flags.FLAG_ENABLE_CROSS_DISPLAYS_PIP_TASK_LAUNCH,
            Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_LISTENER,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INVISIBLE_TASK_REMOVAL_CLEANUP_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
            Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SPLITSCREEN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION,
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
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX,
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
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP_IN_OVERVIEW_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE_BUG_FIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_LOGGING,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR,
            Flags.FLAG_ENABLE_DIALOG_DISPLAY_FIXES,
            Flags.FLAG_ENABLE_DISPLAY_COMPAT_MODE,
            Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
            Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
            Flags.FLAG_ENABLE_DRAG_END_STABLE_BOUNDS_RESET,
            Flags.FLAG_ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD,
            Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS,
            Flags.FLAG_ENABLE_DRAWING_APP_HANDLE,
            Flags.FLAG_ENABLE_DREAM_ACTIVITY_WINDOWING_EXCLUSION,
            Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
            Flags.FLAG_ENABLE_EXPERIMENTAL_BUBBLES_CONTROLLER,
            Flags.FLAG_ENABLE_EXTERNAL_DISPLAY_PERSISTENCE_BUGFIX,
            Flags.FLAG_ENABLE_FREEFORM_BOX_SHADOWS,
            Flags.FLAG_ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,
            Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            Flags.FLAG_ENABLE_FULLSCREEN_WINDOW_CONTROLS,
            Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
            Flags.FLAG_ENABLE_HANDLE_INPUT_FIX,
            Flags.FLAG_ENABLE_HANDLERS_DEBUGGING_MODE,
            Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            Flags.FLAG_ENABLE_INDEPENDENT_BACK_IN_PROJECTED,
            Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP,
            Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS,
            Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE,
            Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI,
            Flags.FLAG_ENABLE_LAUNCHER_HANDLE_GO_HOME_KEYBOARD_SHORTCUT,
            Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            Flags.FLAG_ENABLE_MIRROR_DISPLAY_NO_ACTIVITY,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE,
            Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS,
            Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_PER_DISPLAY_PACKAGE_CONTEXT_CACHE_IN_STATUSBAR_NOTIF,
            Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL,
            Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU,
            Flags.FLAG_ENABLE_PIP_PARAMS_UPDATE_NOTIFICATION_BUGFIX,
            Flags.FLAG_ENABLE_PRESENTATION_DISALLOWED_ON_UNFOCUSED_HOST_TASK,
            Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
            Flags.FLAG_ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION,
            Flags.FLAG_ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_REFACTOR,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_RESTORE_FREEFORM_BUGFIX,
            Flags.FLAG_ENABLE_RESIZING_METRICS,
            Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
            Flags.FLAG_ENABLE_SEE_THROUGH_TASK_FRAGMENTS,
            Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            Flags.FLAG_ENABLE_SHRINK_WINDOW_BOUNDS_AFTER_DRAG,
            Flags.FLAG_ENABLE_SIZE_COMPAT_MODE_IMPROVEMENTS_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,
            Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM,
            Flags.FLAG_ENABLE_TALL_APP_HEADERS,
            Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
            Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
            Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_TASKBAR_OVERFLOW,
            Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX,
            Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,
            Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
            Flags.FLAG_ENABLE_TILE_RESIZING,
            Flags.FLAG_ENABLE_TOP_VISIBLE_ROOT_TASK_PER_USER_TRACKING,
            Flags.FLAG_ENABLE_TRANSITION_ON_ACTIVITY_SET_REQUESTED_ORIENTATION,
            Flags.FLAG_ENABLE_UPDATED_DISPLAY_CONNECTION_DIALOG,
            Flags.FLAG_ENABLE_UPSCALING_SIZE_COMPAT_ON_EXITING_DESKTOP_BUGFIX,
            Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_WINDOW_CONTEXT_RESOURCES_UPDATE_ON_CONFIG_CHANGE,
            Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
            Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
            Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
            Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
            Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
            Flags.FLAG_ENABLE_WINDOWING_SCALED_RESIZING,
            Flags.FLAG_ENABLE_WINDOWING_TASK_STACK_ORDER_BUGFIX,
            Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS,
            Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
            Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING_BUG_FIX,
            Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
            Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS,
            Flags.FLAG_EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS,
            Flags.FLAG_EXCLUDING_LAYER_FROM_TASK_SNAPSHOT,
            Flags.FLAG_FALLBACK_TO_FOCUSED_DISPLAY,
            Flags.FLAG_FALLBACK_TRANSITION_PLAYER,
            Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            Flags.FLAG_FIX_BAL_REPARENT_EXISTING_TASK,
            Flags.FLAG_FIX_BUBBLE_TRAMPOLINE_ANIMATION,
            Flags.FLAG_FIX_HIDE_OVERLAY_API,
            Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
            Flags.FLAG_FIX_RAPID_TOP_RESUMED_SWITCH,
            Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK,
            Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
            Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS,
            Flags.FLAG_HANDLE_INCOMPATIBLE_TASKS_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_IGNORE_ASPECT_RATIO_RESTRICTIONS_FOR_RESIZEABLE_FREEFORM_ACTIVITIES,
            Flags.FLAG_IGNORE_CORNER_RADIUS_AND_SHADOWS,
            Flags.FLAG_IGNORE_CURRENT_PARAMS_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_IGNORE_OVERRIDE_TASK_BOUNDS_IF_INCOMPATIBLE_WITH_DISPLAY,
            Flags.FLAG_IME_BACK_CALLBACK_LEAK_PREVENTION,
            Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
            Flags.FLAG_INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES,
            Flags.FLAG_JANK_API,
            Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            Flags.FLAG_KEYGUARD_REMOVE_DEFAULT_DISPLAY_USAGE,
            Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            Flags.FLAG_LIMIT_SYSTEM_FULLSCREEN_OVERRIDE_TO_DEFAULT_DISPLAY,
            Flags.FLAG_MIGRATE_BASIC_LEGACY_READY,
            Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            Flags.FLAG_MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE,
            Flags.FLAG_MULTI_CROP,
            Flags.FLAG_MULTIPLE_SYSTEM_NAVIGATION_OBSERVER_CALLBACKS,
            Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS_BUGFIX,
            Flags.FLAG_NO_ALPHA_ROTATION_ENTER_ANIMATION,
            Flags.FLAG_OPT_OUT_OVERRIDE_ORIENTATION_TO_USER,
            Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS,
            Flags.FLAG_POLISH_CLOSE_WALLPAPER_INCLUDES_OPEN_CHANGE,
            Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            Flags.FLAG_PREDICTIVE_BACK_CALLBACK_CANCELLATION_FIX,
            Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            Flags.FLAG_PREDICTIVE_BACK_INTERCEPT_TRANSITION,
            Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            Flags.FLAG_PREDICTIVE_BACK_STOP_KEYCODE_BACK_FORWARDING,
            Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API,
            Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK,
            Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            Flags.FLAG_PRESERVE_RECENTS_TASK_CONFIGURATION_ON_RELAUNCH,
            Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
            Flags.FLAG_REENABLE_APP_HANDLE_COLOR_ANIMATIONS,
            Flags.FLAG_REFACTOR_MATCH_PARENT_BOUNDS,
            Flags.FLAG_RELATIVE_INSETS,
            Flags.FLAG_RELEASE_ALL_TRANSITION_SURFACES,
            Flags.FLAG_REMOVE_DESK_ON_LAST_TASK_REMOVAL,
            Flags.FLAG_REMOVE_GET_DIMMER,
            Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            Flags.FLAG_REPOSITORY_BASED_PERSISTENCE,
            Flags.FLAG_REPOSITORY_BASED_PERSISTENCE_BG_THREAD,
            Flags.FLAG_RESPECT_FULLSCREEN_ACTIVITY_OPTION_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            Flags.FLAG_RESPECT_REQUESTED_TASK_SNAPSHOT_RESOLUTION,
            Flags.FLAG_RESTORE_USER_ASPECT_RATIO_SETTINGS_USING_SERVICE,
            Flags.FLAG_RESTRICT_FREEFORM_HIDDEN_SYSTEM_BARS_TO_FILLING_TASKS,
            Flags.FLAG_RETURN_ALL_VISIBLE_ACTIVITIES_FOR_VIS,
            Flags.FLAG_ROOT_TASK_FOR_BUBBLE,
            Flags.FLAG_SAFE_REGION_LETTERBOXING_V1,
            Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
            Flags.FLAG_SCROLLING_FROM_LETTERBOX,
            Flags.FLAG_SCVH_SET_FOCUSABLE,
            Flags.FLAG_SCVH_SURFACE_CONTROL_LIFETIME_FIX,
            Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
            Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
            Flags.FLAG_SHOW_APP_HANDLE_LARGE_SCREENS,
            Flags.FLAG_SHOW_BIOMETRIC_PROMPT_SECONDARY_DISPLAY_MESSAGE,
            Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION,
            Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP,
            Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE,
            Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
            Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            Flags.FLAG_SUPPORT_GEMINI_ON_MULTI_DISPLAY,
            Flags.FLAG_SYSTEM_CONTENT_PRIORITY,
            Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            Flags.FLAG_TASK_FRAGMENT_COMPANION_ACTIVITY,
            Flags.FLAG_TASKBAR_RUNNING_TASKS_IN_SPLITSCREEN_SELECT,
            Flags.FLAG_TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY,
            Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            Flags.FLAG_TRANSFER_STARTING_WINDOW_TO_NEXT_WHEN_INVISIBLE,
            Flags.FLAG_TRANSIT_READY_TRACKING,
            Flags.FLAG_TRANSIT_TRACKER_PLUMBING,
            Flags.FLAG_TRANSITION_HANDLER_CUJ_TAGS,
            Flags.FLAG_UNIFY_SHELL_BINDERS,
            Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT,
            Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
            Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
            Flags.FLAG_UPDATE_DIMS_WHEN_WINDOW_SHOWN,
            Flags.FLAG_UPDATE_TASK_CROP_IN_SYNC,
            Flags.FLAG_USE_INPUT_REPORTED_FOCUS_FOR_ACCESSIBILITY,
            Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            Flags.FLAG_WAIT_FOR_PRESENT_FENCE_ON_DISPLAY_SWITCH
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_ACTIVITY_EMBEDDING_ABORT_CROSS_UID_LAUNCH_IN_FINISHING_TASK_FRAGMENT,
            Flags.FLAG_ACTIVITY_EMBEDDING_DELAY_TASK_FRAGMENT_FINISH_FOR_ACTIVITY_LAUNCH,
            Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
            Flags.FLAG_ACTIVITY_EMBEDDING_METRICS,
            Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
            Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
            Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT,
            Flags.FLAG_ALWAYS_SEQ_ID_LAYOUT_WEAR,
            Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
            Flags.FLAG_AOD_TRANSITION,
            Flags.FLAG_APP_COMPAT_PROPERTIES_API,
            Flags.FLAG_APP_COMPAT_REFACTORING,
            Flags.FLAG_APP_COMPAT_REFACTORING_FIX_MULTIWINDOW_TASK_HIERARCHY,
            Flags.FLAG_APP_COMPAT_REFACTORING_FORCE_CHANGE_FOR_LETTERBOX_TRANSITIONS,
            Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS,
            Flags.FLAG_APP_COMPAT_REFACTORING_ROUNDED_CORNERS_ANIMATION,
            Flags.FLAG_APP_COMPAT_REFACTORING_SET_APPBOUNDS_TO_NULL_WHEN_EMPTY,
            Flags.FLAG_APP_COMPAT_REFACTORING_SKIP_STARTING_WINDOW_LETTERBOX,
            Flags.FLAG_APP_COMPAT_REFACTORING_USE_ACTIVITY_LEASH_FOR_LETTERBOXING,
            Flags.FLAG_APP_COMPAT_UI_FRAMEWORK,
            Flags.FLAG_APP_HANDLE_NO_RELAYOUT_ON_EXCLUSION_CHANGE,
            Flags.FLAG_APPLY_DESK_ACTIVATION_ON_USER_SWITCH,
            Flags.FLAG_APPLY_LIFECYCLE_ON_PIP_CHANGE,
            Flags.FLAG_AVOID_REBINDING_INTENTIONALLY_DISCONNECTED_WALLPAPER,
            Flags.FLAG_BACKUP_AND_RESTORE_FOR_USER_ASPECT_RATIO_SETTINGS,
            Flags.FLAG_BAL_ADDITIONAL_START_MODES,
            Flags.FLAG_BAL_CHECK_BROADCAST_WHEN_DISPATCHED,
            Flags.FLAG_BAL_COVER_INTENT_SENDER,
            Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
            Flags.FLAG_BAL_REDUCE_GRACE_PERIOD,
            Flags.FLAG_BAL_REPORT_ABORTED_ACTIVITY_STARTS,
            Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
            Flags.FLAG_BAL_SEND_INTENT_WITH_OPTIONS,
            Flags.FLAG_BAL_STRICT_MODE_RO,
            Flags.FLAG_CAMERA_COMPAT_FULLSCREEN_PICK_SAME_TASK_ACTIVITY,
            Flags.FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT,
            Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES,
            Flags.FLAG_CLEAR_REUSABLE_SCVH_ON_RELEASE,
            Flags.FLAG_CLOSE_FULLSCREEN_AND_SPLITSCREEN_KEYBOARD_SHORTCUT,
            Flags.FLAG_CLOSE_TASK_KEYBOARD_SHORTCUT,
            Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
            Flags.FLAG_COVER_DISPLAY_OPT_IN,
            Flags.FLAG_CURRENT_ANIMATOR_SCALE_USES_SHARED_MEMORY,
            Flags.FLAG_DEFAULT_DESK_WITHOUT_WARMUP_MIGRATION,
            Flags.FLAG_DEFER_RESUME_FOCUS_IN_NON_FOCUSED_WINDOW,
            Flags.FLAG_DENSITY_390_API,
            Flags.FLAG_DEPRECATE_SURFACE_ANIMATION_FRAME_CALLBACK,
            Flags.FLAG_DEPRECATE_WINDOW_ANIMATOR_FRAME_CALLBACK,
            Flags.FLAG_DIMMING_WALLPAPER_FOR_MAXIMIZED_AND_TILED,
            Flags.FLAG_DISABLE_DESKTOP_LAUNCH_PARAMS_OUTSIDE_DESKTOP_BUG_FIX,
            Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING,
            Flags.FLAG_DISABLE_RESTORE_NON_FULLSCREEN_BOUNDS_ON_CONFIGURATION_CHANGE,
            Flags.FLAG_DO_NOT_FORCE_WALLPAPER_FOR_FREEFORM_TASK,
            Flags.FLAG_ENABLE_ACCESSIBLE_CUSTOM_HEADERS,
            Flags.FLAG_ENABLE_ACTIVITY_EMBEDDING_SUPPORT_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_APP_HANDLE_POSITION_REPORTING,
            Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
            Flags.FLAG_ENABLE_APP_TO_WEB_EDUCATION_ANIMATION,
            Flags.FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE,
            Flags.FLAG_ENABLE_BACKUP_AND_RESTORE_DISPLAY_WINDOW_SETTINGS,
            Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_BORDER_SETTINGS,
            Flags.FLAG_ENABLE_BOUNDS_RESTORING_ON_TILING_EXIT,
            Flags.FLAG_ENABLE_BOX_SHADOW_SETTINGS,
            Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_COMPATIBILITY_INFO_ROTATE_AND_CROP_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_EXTERNAL_DISPLAY_ROTATION_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING_OPT_OUT_API,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX,
            Flags.FLAG_ENABLE_CAMERA_COMPAT_TRACK_TASK_AND_APP_BUGFIX,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_CONVERSION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION,
            Flags.FLAG_ENABLE_CAPTION_COMPAT_INSET_FORCE_CONSUMPTION_ALWAYS,
            Flags.FLAG_ENABLE_CASCADING_WINDOWS,
            Flags.FLAG_ENABLE_CLOSE_LID_INTERACTION,
            Flags.FLAG_ENABLE_COMPAT_UI_DESKTOP_MODE_SYNCHRONIZATION_BUGFIX,
            Flags.FLAG_ENABLE_COMPAT_UI_VISIBILITY_STATUS,
            Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER_FIX,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_DND,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_PIP,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WALLPAPER_PRESENTATIONS,
            Flags.FLAG_ENABLE_CONNECTED_DISPLAYS_WINDOW_DRAG,
            Flags.FLAG_ENABLE_CRASH_LOGGING_FOR_DESKTOP,
            Flags.FLAG_ENABLE_CROSS_DISPLAYS_APP_LAUNCH_TRANSITION,
            Flags.FLAG_ENABLE_CROSS_DISPLAYS_PIP_TASK_LAUNCH,
            Flags.FLAG_ENABLE_DESKTOP_APP_HANDLE_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_ALTTAB_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_APP_LAUNCH_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_SHORTCUT_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_CLOSE_TASK_ANIMATION_IN_DTC_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DEFAULT_TO_DESKTOP_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_BASED_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_FULLSCREEN_REFOCUS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_LISTENER,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_POLICY_IN_LPM,
            Flags.FLAG_ENABLE_DESKTOP_FIRST_TOP_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_IMMERSIVE_DRAG_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INDICATOR_IN_SEPARATE_THREAD_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_INVISIBLE_TASK_REMOVAL_CLEANUP_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_MODE_THROUGH_DEV_OPTION,
            Flags.FLAG_ENABLE_DESKTOP_OPENING_DEEPLINK_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SPLITSCREEN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_SYSTEM_DIALOGS_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_LAUNCH_ANIMATION,
            Flags.FLAG_ENABLE_DESKTOP_TAB_TEARING_MINIMIZE_ANIMATION_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_TASK_LIMIT_SEPARATE_TRANSITION,
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
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_ENTERPRISE_BUGFIX,
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
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PIP_IN_OVERVIEW_BUGFIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE_BUG_FIX,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TRANSITIONS,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_LOGGING,
            Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR,
            Flags.FLAG_ENABLE_DIALOG_DISPLAY_FIXES,
            Flags.FLAG_ENABLE_DISPLAY_COMPAT_MODE,
            Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
            Flags.FLAG_ENABLE_DISPLAY_RECONNECT_INTERACTION,
            Flags.FLAG_ENABLE_DISPLAY_WINDOWING_MODE_SWITCHING,
            Flags.FLAG_ENABLE_DRAG_END_STABLE_BOUNDS_RESET,
            Flags.FLAG_ENABLE_DRAG_RESIZE_SET_UP_IN_BG_THREAD,
            Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX,
            Flags.FLAG_ENABLE_DRAG_TO_MAXIMIZE,
            Flags.FLAG_ENABLE_DRAGGING_PIP_ACROSS_DISPLAYS,
            Flags.FLAG_ENABLE_DRAWING_APP_HANDLE,
            Flags.FLAG_ENABLE_DREAM_ACTIVITY_WINDOWING_EXCLUSION,
            Flags.FLAG_ENABLE_DYNAMIC_RADIUS_COMPUTATION_BUGFIX,
            Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
            Flags.FLAG_ENABLE_EXPERIMENTAL_BUBBLES_CONTROLLER,
            Flags.FLAG_ENABLE_EXTERNAL_DISPLAY_PERSISTENCE_BUGFIX,
            Flags.FLAG_ENABLE_FREEFORM_BOX_SHADOWS,
            Flags.FLAG_ENABLE_FREEFORM_DISPLAY_LAUNCH_PARAMS,
            Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            Flags.FLAG_ENABLE_FULLSCREEN_WINDOW_CONTROLS,
            Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP,
            Flags.FLAG_ENABLE_HANDLE_INPUT_FIX,
            Flags.FLAG_ENABLE_HANDLERS_DEBUGGING_MODE,
            Flags.FLAG_ENABLE_HOLD_TO_DRAG_APP_HANDLE,
            Flags.FLAG_ENABLE_INDEPENDENT_BACK_IN_PROJECTED,
            Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP,
            Flags.FLAG_ENABLE_INPUT_LAYER_TRANSITION_FIX,
            Flags.FLAG_ENABLE_INTERACTION_DEPENDENT_TAB_TEARING_BOUNDS,
            Flags.FLAG_ENABLE_INTERACTIVE_PICTURE_IN_PICTURE,
            Flags.FLAG_ENABLE_KEY_GESTURE_HANDLER_FOR_SYSUI,
            Flags.FLAG_ENABLE_LAUNCHER_HANDLE_GO_HOME_KEYBOARD_SHORTCUT,
            Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
            Flags.FLAG_ENABLE_MIRROR_DISPLAY_NO_ACTIVITY,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
            Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE,
            Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_HOME_FOCUS_BUG_FIX,
            Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_MULTIDISPLAY_TRACKPAD_BACK_GESTURE,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_DEFAULT_ACTIVATION_IN_DESKTOP_FIRST_DISPLAYS,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_FRONTEND,
            Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT,
            Flags.FLAG_ENABLE_NON_DEFAULT_DISPLAY_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS,
            Flags.FLAG_ENABLE_OVERFLOW_BUTTON_FOR_TASKBAR_PINNED_ITEMS,
            Flags.FLAG_ENABLE_PER_DISPLAY_DESKTOP_WALLPAPER_ACTIVITY,
            Flags.FLAG_ENABLE_PER_DISPLAY_PACKAGE_CONTEXT_CACHE_IN_STATUSBAR_NOTIF,
            Flags.FLAG_ENABLE_PER_DISPLAY_WINDOW_DECOR_VIEW_HOST_POOL,
            Flags.FLAG_ENABLE_PERSISTING_DISPLAY_SIZE_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PINNING_APP_WITH_CONTEXT_MENU,
            Flags.FLAG_ENABLE_PIP_PARAMS_UPDATE_NOTIFICATION_BUGFIX,
            Flags.FLAG_ENABLE_PRESENTATION_DISALLOWED_ON_UNFOCUSED_HOST_TASK,
            Flags.FLAG_ENABLE_PRESENTATION_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_PROJECTED_DISPLAY_DESKTOP_MODE,
            Flags.FLAG_ENABLE_QUICKSWITCH_DESKTOP_SPLIT_BUGFIX,
            Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION,
            Flags.FLAG_ENABLE_REMOVE_STATUS_BAR_INPUT_LAYER,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_BUGFIX,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_REFACTOR,
            Flags.FLAG_ENABLE_REQUEST_FULLSCREEN_RESTORE_FREEFORM_BUGFIX,
            Flags.FLAG_ENABLE_RESIZING_METRICS,
            Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_RESTORE_TO_PREVIOUS_SIZE_FROM_DESKTOP_IMMERSIVE,
            Flags.FLAG_ENABLE_SEE_THROUGH_TASK_FRAGMENTS,
            Flags.FLAG_ENABLE_SHELL_INITIAL_BOUNDS_REGRESSION_BUG_FIX,
            Flags.FLAG_ENABLE_SHRINK_WINDOW_BOUNDS_AFTER_DRAG,
            Flags.FLAG_ENABLE_SIZE_COMPAT_MODE_IMPROVEMENTS_FOR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_START_LAUNCH_TRANSITION_FROM_TASKBAR_BUGFIX,
            Flags.FLAG_ENABLE_SYS_DECORS_CALLBACKS_VIA_WM,
            Flags.FLAG_ENABLE_TALL_APP_HEADERS,
            Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS,
            Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
            Flags.FLAG_ENABLE_TASKBAR_CONNECTED_DISPLAYS,
            Flags.FLAG_ENABLE_TASKBAR_OVERFLOW,
            Flags.FLAG_ENABLE_TASKBAR_RECENT_TASKS_THROTTLE_BUGFIX,
            Flags.FLAG_ENABLE_TASKBAR_RECENTS_LAYOUT_TRANSITION,
            Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
            Flags.FLAG_ENABLE_TILE_RESIZING,
            Flags.FLAG_ENABLE_TOP_VISIBLE_ROOT_TASK_PER_USER_TRACKING,
            Flags.FLAG_ENABLE_TRANSITION_ON_ACTIVITY_SET_REQUESTED_ORIENTATION,
            Flags.FLAG_ENABLE_UPDATED_DISPLAY_CONNECTION_DIALOG,
            Flags.FLAG_ENABLE_UPSCALING_SIZE_COMPAT_ON_EXITING_DESKTOP_BUGFIX,
            Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX,
            Flags.FLAG_ENABLE_WINDOW_CONTEXT_RESOURCES_UPDATE_ON_CONFIG_CHANGE,
            Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR,
            Flags.FLAG_ENABLE_WINDOW_DROP_SMOOTH_TRANSITION,
            Flags.FLAG_ENABLE_WINDOW_REPOSITIONING_API,
            Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
            Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
            Flags.FLAG_ENABLE_WINDOWING_SCALED_RESIZING,
            Flags.FLAG_ENABLE_WINDOWING_TASK_STACK_ORDER_BUGFIX,
            Flags.FLAG_ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS,
            Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
            Flags.FLAG_ENSURE_KEYGUARD_DOES_TRANSITION_STARTING_BUG_FIX,
            Flags.FLAG_ENSURE_WALLPAPER_DRAWN_ON_DISPLAY_SWITCH,
            Flags.FLAG_ENTER_DESKTOP_BY_DEFAULT_ON_FREEFORM_DISPLAYS,
            Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS,
            Flags.FLAG_EXCLUDE_DESK_ROOTS_FROM_DESKTOP_TASKS,
            Flags.FLAG_EXCLUDING_LAYER_FROM_TASK_SNAPSHOT,
            Flags.FLAG_FALLBACK_TO_FOCUSED_DISPLAY,
            Flags.FLAG_FALLBACK_TRANSITION_PLAYER,
            Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
            Flags.FLAG_FIX_BAL_REPARENT_EXISTING_TASK,
            Flags.FLAG_FIX_BUBBLE_TRAMPOLINE_ANIMATION,
            Flags.FLAG_FIX_HIDE_OVERLAY_API,
            Flags.FLAG_FIX_LEAKING_VISUAL_INDICATOR,
            Flags.FLAG_FIX_RAPID_TOP_RESUMED_SWITCH,
            Flags.FLAG_FORCE_CLOSE_TOP_TRANSPARENT_FULLSCREEN_TASK,
            Flags.FLAG_FORM_FACTOR_BASED_DESKTOP_FIRST_SWITCH,
            Flags.FLAG_GRANT_MANAGE_KEY_GESTURES_TO_RECENTS,
            Flags.FLAG_HANDLE_INCOMPATIBLE_TASKS_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_IGNORE_ASPECT_RATIO_RESTRICTIONS_FOR_RESIZEABLE_FREEFORM_ACTIVITIES,
            Flags.FLAG_IGNORE_CORNER_RADIUS_AND_SHADOWS,
            Flags.FLAG_IGNORE_CURRENT_PARAMS_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_IGNORE_OVERRIDE_TASK_BOUNDS_IF_INCOMPATIBLE_WITH_DISPLAY,
            Flags.FLAG_IME_BACK_CALLBACK_LEAK_PREVENTION,
            Flags.FLAG_INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC,
            Flags.FLAG_INHERIT_TASK_BOUNDS_FOR_TRAMPOLINE_TASK_LAUNCHES,
            Flags.FLAG_JANK_API,
            Flags.FLAG_KEYBOARD_SHORTCUTS_TO_SWITCH_DESKS,
            Flags.FLAG_KEYGUARD_REMOVE_DEFAULT_DISPLAY_USAGE,
            Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
            Flags.FLAG_LIMIT_SYSTEM_FULLSCREEN_OVERRIDE_TO_DEFAULT_DISPLAY,
            Flags.FLAG_MIGRATE_BASIC_LEGACY_READY,
            Flags.FLAG_MOVE_TO_EXTERNAL_DISPLAY_SHORTCUT,
            Flags.FLAG_MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE,
            Flags.FLAG_MULTI_CROP,
            Flags.FLAG_MULTIPLE_SYSTEM_NAVIGATION_OBSERVER_CALLBACKS,
            Flags.FLAG_NESTED_TASKS_WITH_INDEPENDENT_BOUNDS_BUGFIX,
            Flags.FLAG_NO_ALPHA_ROTATION_ENTER_ANIMATION,
            Flags.FLAG_OPT_OUT_OVERRIDE_ORIENTATION_TO_USER,
            Flags.FLAG_PARALLEL_CD_TRANSITIONS_DURING_RECENTS,
            Flags.FLAG_POLISH_CLOSE_WALLPAPER_INCLUDES_OPEN_CHANGE,
            Flags.FLAG_PORT_WINDOW_SIZE_ANIMATION,
            Flags.FLAG_PREDICTIVE_BACK_CALLBACK_CANCELLATION_FIX,
            Flags.FLAG_PREDICTIVE_BACK_DELAY_WM_TRANSITION,
            Flags.FLAG_PREDICTIVE_BACK_INTERCEPT_TRANSITION,
            Flags.FLAG_PREDICTIVE_BACK_PRIORITY_SYSTEM_NAVIGATION_OBSERVER,
            Flags.FLAG_PREDICTIVE_BACK_STOP_KEYCODE_BACK_FORWARDING,
            Flags.FLAG_PREDICTIVE_BACK_SWIPE_EDGE_NONE_API,
            Flags.FLAG_PREDICTIVE_BACK_SYSTEM_OVERRIDE_CALLBACK,
            Flags.FLAG_PREDICTIVE_BACK_TIMESTAMP_API,
            Flags.FLAG_PRESERVE_RECENTS_TASK_CONFIGURATION_ON_RELAUNCH,
            Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
            Flags.FLAG_REDUCE_CHANGED_EXCLUSION_RECTS_MSGS,
            Flags.FLAG_REDUCE_TASK_SNAPSHOT_MEMORY_USAGE,
            Flags.FLAG_REENABLE_APP_HANDLE_ANIMATIONS,
            Flags.FLAG_REENABLE_APP_HANDLE_COLOR_ANIMATIONS,
            Flags.FLAG_REFACTOR_MATCH_PARENT_BOUNDS,
            Flags.FLAG_RELATIVE_INSETS,
            Flags.FLAG_RELEASE_ALL_TRANSITION_SURFACES,
            Flags.FLAG_REMOVE_DESK_ON_LAST_TASK_REMOVAL,
            Flags.FLAG_REMOVE_GET_DIMMER,
            Flags.FLAG_REPARENT_WINDOW_TOKEN_API,
            Flags.FLAG_REPOSITORY_BASED_PERSISTENCE,
            Flags.FLAG_REPOSITORY_BASED_PERSISTENCE_BG_THREAD,
            Flags.FLAG_RESPECT_FULLSCREEN_ACTIVITY_OPTION_IN_DESKTOP_LAUNCH_PARAMS,
            Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE,
            Flags.FLAG_RESPECT_REQUESTED_TASK_SNAPSHOT_RESOLUTION,
            Flags.FLAG_RESTORE_USER_ASPECT_RATIO_SETTINGS_USING_SERVICE,
            Flags.FLAG_RESTRICT_FREEFORM_HIDDEN_SYSTEM_BARS_TO_FILLING_TASKS,
            Flags.FLAG_RETURN_ALL_VISIBLE_ACTIVITIES_FOR_VIS,
            Flags.FLAG_ROOT_TASK_FOR_BUBBLE,
            Flags.FLAG_SAFE_REGION_LETTERBOXING_V1,
            Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
            Flags.FLAG_SCROLLING_FROM_LETTERBOX,
            Flags.FLAG_SCVH_SET_FOCUSABLE,
            Flags.FLAG_SCVH_SURFACE_CONTROL_LIFETIME_FIX,
            Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
            Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
            Flags.FLAG_SHOW_APP_HANDLE_LARGE_SCREENS,
            Flags.FLAG_SHOW_BIOMETRIC_PROMPT_SECONDARY_DISPLAY_MESSAGE,
            Flags.FLAG_SHOW_DESKTOP_EXPERIENCE_DEV_OPTION,
            Flags.FLAG_SHOW_DESKTOP_WINDOWING_DEV_OPTION,
            Flags.FLAG_SHOW_HOME_BEHIND_DESKTOP,
            Flags.FLAG_SKIP_COMPAT_UI_EDUCATION_IN_DESKTOP_MODE,
            Flags.FLAG_SKIP_DEACTIVATION_OF_DESK_WITH_NOTHING_IN_FRONT,
            Flags.FLAG_SKIP_DECOR_VIEW_RELAYOUT_WHEN_CLOSING_BUGFIX,
            Flags.FLAG_SUPPORT_GEMINI_ON_MULTI_DISPLAY,
            Flags.FLAG_SYSTEM_CONTENT_PRIORITY,
            Flags.FLAG_SYSTEM_UI_POST_ANIMATION_END,
            Flags.FLAG_TASK_FRAGMENT_COMPANION_ACTIVITY,
            Flags.FLAG_TASKBAR_RUNNING_TASKS_IN_SPLITSCREEN_SELECT,
            Flags.FLAG_TOGGLE_FULLSCREEN_STATE_VIA_FULLSCREEN_KEY,
            Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN,
            Flags.FLAG_TRANSFER_STARTING_WINDOW_TO_NEXT_WHEN_INVISIBLE,
            Flags.FLAG_TRANSIT_READY_TRACKING,
            Flags.FLAG_TRANSIT_TRACKER_PLUMBING,
            Flags.FLAG_TRANSITION_HANDLER_CUJ_TAGS,
            Flags.FLAG_UNIFY_SHELL_BINDERS,
            Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT,
            Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
            Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
            Flags.FLAG_UPDATE_DIMS_WHEN_WINDOW_SHOWN,
            Flags.FLAG_UPDATE_TASK_CROP_IN_SYNC,
            Flags.FLAG_USE_INPUT_REPORTED_FOCUS_FOR_ACCESSIBILITY,
            Flags.FLAG_VDM_FORCE_APP_UNIVERSAL_RESIZABLE_API,
            Flags.FLAG_WAIT_FOR_PRESENT_FENCE_ON_DISPLAY_SWITCH,
            ""
        )
    );
}
