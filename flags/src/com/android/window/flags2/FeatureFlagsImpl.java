package com.android.window.flags2;
// TODO(b/303773055): Remove the annotation after access issue is resolved.

import com.android.quickstep.util.DeviceConfigHelper;

import java.nio.file.Files;
import java.nio.file.Paths;
/** @hide */
public final class FeatureFlagsImpl implements FeatureFlags {
    private static final boolean isReadFromNew = Files.exists(Paths.get("/metadata/aconfig/boot/enable_only_new_storage"));
    private static volatile boolean isCached = false;
    private static volatile boolean accessibility_is_cached = false;
    private static volatile boolean large_screen_experiences_app_compat_is_cached = false;
    private static volatile boolean lse_desktop_experience_is_cached = false;
    private static volatile boolean multitasking_is_cached = false;
    private static volatile boolean responsible_apis_is_cached = false;
    private static volatile boolean systemui_is_cached = false;
    private static volatile boolean wear_frameworks_is_cached = false;
    private static volatile boolean window_surfaces_is_cached = false;
    private static volatile boolean windowing_frontend_is_cached = false;
    private static volatile boolean windowing_sdk_is_cached = false;
    private static boolean activityEmbeddingAnimationCustomizationFlag = false;
    private static boolean activityEmbeddingInteractiveDividerFlag = true;
    private static boolean activityEmbeddingOverlayPresentationFlag = true;
    private static boolean allowHideScmButton = true;
    private static boolean alwaysDeferTransitionWhenApplyWct = true;
    private static boolean alwaysDrawMagnificationFullscreenBorder = true;
    private static boolean alwaysUpdateWallpaperPermission = true;
    private static boolean balDontBringExistingBackgroundTaskStackToFg = true;
    private static boolean balImproveRealCallerVisibilityCheck = true;
    private static boolean balImprovedMetrics = true;
    private static boolean balRequireOptInByPendingIntentCreator = true;
    private static boolean balRequireOptInSameUid = false;
    private static boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid = true;
    private static boolean balShowToasts = false;
    private static boolean balShowToastsBlocked = true;
    private static boolean blastSyncNotificationShadeOnDisplaySwitch = true;
    private static boolean closeToSquareConfigIncludesStatusBar = false;
    private static boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition = true;
    private static boolean disableThinLetterboxingPolicy = true;
    private static boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions = true;
    private static boolean edgeToEdgeByDefault = false;
    private static boolean embeddedActivityBackNavFlag = true;
    private static boolean enableAdditionalWindowsAboveStatusBar = false;
    private static boolean enableAppHeaderWithTaskDensity = true;
    private static boolean enableCameraCompatForDesktopWindowing = true;
    private static boolean enableCompatuiSysuiLauncher = false;
    private static boolean enableDesktopWindowingImmersiveHandleHiding = false;
    private static boolean enableDesktopWindowingModalsPolicy = true;
    private static boolean enableDesktopWindowingMode = false;
    private static boolean enableDesktopWindowingQuickSwitch = false;
    private static boolean enableDesktopWindowingScvhCache = false;
    private static boolean enableDesktopWindowingSizeConstraints = false;
    private static boolean enableDesktopWindowingTaskLimit = true;
    private static boolean enableDesktopWindowingTaskbarRunningApps = true;
    private static boolean enableDesktopWindowingWallpaperActivity = false;
    private static boolean enableTaskStackObserverInShell = true;
    private static boolean enableThemedAppHeaders = true;
    private static boolean enableWindowingDynamicInitialBounds = false;
    private static boolean enableWindowingEdgeDragResize = false;
    private static boolean ensureWallpaperInTransitions = false;
    private static boolean fixPipRestoreToOverlay = true;
    private static boolean fullscreenDimFlag = true;
    private static boolean immersiveAppRepositioning = true;
    private static boolean insetsControlChangedItem = false;
    private static boolean letterboxBackgroundWallpaper = false;
    private static boolean moveAnimationOptionsToChange = true;
    private static boolean multiCrop = true;
    private static boolean navBarTransparentByDefault = true;
    private static boolean noConsecutiveVisibilityEvents = true;
    private static boolean noVisibilityEventOnDisplayStateChange = true;
    private static boolean offloadColorExtraction = false;
    private static boolean predictiveBackSystemAnims = true;
    private static boolean taskFragmentSystemOrganizerFlag = true;
    private static boolean transitReadyTracking = false;
    private static boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds = true;
    private static boolean userMinAspectRatioAppDefault = true;
    private static boolean waitForTransitionOnDisplaySwitch = true;
    private static boolean windowTokenConfigThreadSafe = true;


    private void init() {
        isCached = true;
    }




    private void load_overrides_accessibility() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            alwaysDrawMagnificationFullscreenBorder =
                    properties.getBoolean(Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER, true);
            delayNotificationToMagnificationWhenRecentsWindowToFrontTransition =
                    properties.getBoolean(Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION, true);
            doNotCheckIntersectionWhenNonMagnifiableWindowTransitions =
                    properties.getBoolean(Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS, true);
            useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds =
                    properties.getBoolean(Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace accessibility "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        accessibility_is_cached = true;
    }

    private void load_overrides_large_screen_experiences_app_compat() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            allowHideScmButton =
                    properties.getBoolean(Flags.FLAG_ALLOW_HIDE_SCM_BUTTON, true);
            disableThinLetterboxingPolicy =
                    properties.getBoolean(Flags.FLAG_DISABLE_THIN_LETTERBOXING_POLICY, true);
            enableCompatuiSysuiLauncher =
                    properties.getBoolean(Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER, false);
            immersiveAppRepositioning =
                    properties.getBoolean(Flags.FLAG_IMMERSIVE_APP_REPOSITIONING, true);
            letterboxBackgroundWallpaper =
                    properties.getBoolean(Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER, false);
            userMinAspectRatioAppDefault =
                    properties.getBoolean(Flags.FLAG_USER_MIN_ASPECT_RATIO_APP_DEFAULT, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace large_screen_experiences_app_compat "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        large_screen_experiences_app_compat_is_cached = true;
    }

    private void load_overrides_lse_desktop_experience() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            enableAdditionalWindowsAboveStatusBar =
                    properties.getBoolean(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR, false);
            enableAppHeaderWithTaskDensity =
                    properties.getBoolean(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY, true);
            enableCameraCompatForDesktopWindowing =
                    properties.getBoolean(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING, true);
            enableDesktopWindowingImmersiveHandleHiding =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING, false);
            enableDesktopWindowingModalsPolicy =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY, true);
            enableDesktopWindowingMode =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE, true);
            enableDesktopWindowingQuickSwitch =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH, false);
            enableDesktopWindowingScvhCache =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE, false);
            enableDesktopWindowingSizeConstraints =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS, false);
            enableDesktopWindowingTaskLimit =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT, true);
            enableDesktopWindowingTaskbarRunningApps =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS, true);
            enableDesktopWindowingWallpaperActivity =
                    properties.getBoolean(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY, false);
            enableTaskStackObserverInShell =
                    properties.getBoolean(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL, true);
            enableThemedAppHeaders =
                    properties.getBoolean(Flags.FLAG_ENABLE_THEMED_APP_HEADERS, true);
            enableWindowingDynamicInitialBounds =
                    properties.getBoolean(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS, false);
            enableWindowingEdgeDragResize =
                    properties.getBoolean(Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE, false);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace lse_desktop_experience "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        lse_desktop_experience_is_cached = true;
    }

    private void load_overrides_multitasking() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace multitasking "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        multitasking_is_cached = true;
    }

    private void load_overrides_responsible_apis() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            balDontBringExistingBackgroundTaskStackToFg =
                    properties.getBoolean(Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG, true);
            balImproveRealCallerVisibilityCheck =
                    properties.getBoolean(Flags.FLAG_BAL_IMPROVE_REAL_CALLER_VISIBILITY_CHECK, true);
            balImprovedMetrics =
                    properties.getBoolean(Flags.FLAG_BAL_IMPROVED_METRICS, true);
            balRequireOptInByPendingIntentCreator =
                    properties.getBoolean(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR, true);
            balRequireOptInSameUid =
                    properties.getBoolean(Flags.FLAG_BAL_REQUIRE_OPT_IN_SAME_UID, false);
            balRespectAppSwitchStateWhenCheckBoundByForegroundUid =
                    properties.getBoolean(Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID, true);
            balShowToasts =
                    properties.getBoolean(Flags.FLAG_BAL_SHOW_TOASTS, false);
            balShowToastsBlocked =
                    properties.getBoolean(Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace responsible_apis "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        responsible_apis_is_cached = true;
    }

    private void load_overrides_systemui() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            multiCrop =
                    properties.getBoolean(Flags.FLAG_MULTI_CROP, true);
            noConsecutiveVisibilityEvents =
                    properties.getBoolean(Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS, true);
            offloadColorExtraction =
                    properties.getBoolean(Flags.FLAG_OFFLOAD_COLOR_EXTRACTION, false);
            predictiveBackSystemAnims =
                    properties.getBoolean(Flags.FLAG_PREDICTIVE_BACK_SYSTEM_ANIMS, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace systemui "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        systemui_is_cached = true;
    }

    private void load_overrides_wear_frameworks() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            alwaysUpdateWallpaperPermission =
                    properties.getBoolean(Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION, true);
            noVisibilityEventOnDisplayStateChange =
                    properties.getBoolean(Flags.FLAG_NO_VISIBILITY_EVENT_ON_DISPLAY_STATE_CHANGE, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace wear_frameworks "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        wear_frameworks_is_cached = true;
    }

    private void load_overrides_window_surfaces() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace window_surfaces "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        window_surfaces_is_cached = true;
    }

    private void load_overrides_windowing_frontend() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            blastSyncNotificationShadeOnDisplaySwitch =
                    properties.getBoolean(Flags.FLAG_BLAST_SYNC_NOTIFICATION_SHADE_ON_DISPLAY_SWITCH, true);
            closeToSquareConfigIncludesStatusBar =
                    properties.getBoolean(Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR, false);
            edgeToEdgeByDefault =
                    properties.getBoolean(Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT, false);
            ensureWallpaperInTransitions =
                    properties.getBoolean(Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS, false);
            navBarTransparentByDefault =
                    properties.getBoolean(Flags.FLAG_NAV_BAR_TRANSPARENT_BY_DEFAULT, true);
            transitReadyTracking =
                    properties.getBoolean(Flags.FLAG_TRANSIT_READY_TRACKING, false);
            waitForTransitionOnDisplaySwitch =
                    properties.getBoolean(Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace windowing_frontend "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        windowing_frontend_is_cached = true;
    }

    private void load_overrides_windowing_sdk() {
        try {
            var properties = DeviceConfigHelper.Companion.getPrefs();
            activityEmbeddingAnimationCustomizationFlag =
                    properties.getBoolean(Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG, false);
            activityEmbeddingInteractiveDividerFlag =
                    properties.getBoolean(Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG, true);
            activityEmbeddingOverlayPresentationFlag =
                    properties.getBoolean(Flags.FLAG_ACTIVITY_EMBEDDING_OVERLAY_PRESENTATION_FLAG, true);
            alwaysDeferTransitionWhenApplyWct =
                    properties.getBoolean(Flags.FLAG_ALWAYS_DEFER_TRANSITION_WHEN_APPLY_WCT, true);
            embeddedActivityBackNavFlag =
                    properties.getBoolean(Flags.FLAG_EMBEDDED_ACTIVITY_BACK_NAV_FLAG, true);
            fixPipRestoreToOverlay =
                    properties.getBoolean(Flags.FLAG_FIX_PIP_RESTORE_TO_OVERLAY, true);
            fullscreenDimFlag =
                    properties.getBoolean(Flags.FLAG_FULLSCREEN_DIM_FLAG, true);
            insetsControlChangedItem =
                    properties.getBoolean(Flags.FLAG_INSETS_CONTROL_CHANGED_ITEM, false);
            moveAnimationOptionsToChange =
                    properties.getBoolean(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE, true);
            taskFragmentSystemOrganizerFlag =
                    properties.getBoolean(Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG, true);
            windowTokenConfigThreadSafe =
                    properties.getBoolean(Flags.FLAG_WINDOW_TOKEN_CONFIG_THREAD_SAFE, true);
        } catch (NullPointerException e) {
            throw new RuntimeException(
                    "Cannot read value from namespace windowing_sdk "
                            + "from DeviceConfig. It could be that the code using flag "
                            + "executed before SettingsProvider initialization. Please use "
                            + "fixed read-only flag by adding is_fixed_read_only: true in "
                            + "flag declaration.",
                    e
            );
        } catch (SecurityException e) {
            // for isolated process case, skip loading flag value from the storage, use the default
        }
        windowing_sdk_is_cached = true;
    }

    @Override
   
    public boolean activityEmbeddingAnimationCustomizationFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return activityEmbeddingAnimationCustomizationFlag;

    }

    @Override
   
    public boolean activityEmbeddingInteractiveDividerFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return activityEmbeddingInteractiveDividerFlag;

    }

    @Override
   
    public boolean activityEmbeddingOverlayPresentationFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return activityEmbeddingOverlayPresentationFlag;

    }

    @Override
   
    public boolean activitySnapshotByDefault() {
        return true;

    }

    @Override
   
    public boolean activityWindowInfoFlag() {
        return true;

    }

    @Override
   
    public boolean allowDisableActivityRecordInputSink() {
        return true;

    }

    @Override
   
    public boolean allowHideScmButton() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return allowHideScmButton;

    }

    @Override
   
    public boolean allowsScreenSizeDecoupledFromStatusBarAndCutout() {
        return true;

    }

    @Override
   
    public boolean alwaysDeferTransitionWhenApplyWct() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return alwaysDeferTransitionWhenApplyWct;

    }

    @Override
   
    public boolean alwaysDrawMagnificationFullscreenBorder() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return alwaysDrawMagnificationFullscreenBorder;

    }

    @Override
   
    public boolean alwaysUpdateWallpaperPermission() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!wear_frameworks_is_cached) {
                load_overrides_wear_frameworks();
            }
        }
        return alwaysUpdateWallpaperPermission;

    }

    @Override
   
    public boolean appCompatPropertiesApi() {
        return true;

    }

    @Override
   
    public boolean appCompatRefactoring() {
        return false;

    }

    @Override
   
    public boolean balDontBringExistingBackgroundTaskStackToFg() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balDontBringExistingBackgroundTaskStackToFg;

    }

    @Override
   
    public boolean balImproveRealCallerVisibilityCheck() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balImproveRealCallerVisibilityCheck;

    }

    @Override
   
    public boolean balImprovedMetrics() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balImprovedMetrics;

    }

    @Override
   
    public boolean balRequireOptInByPendingIntentCreator() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balRequireOptInByPendingIntentCreator;

    }

    @Override
   
    public boolean balRequireOptInSameUid() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balRequireOptInSameUid;

    }

    @Override
   
    public boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balRespectAppSwitchStateWhenCheckBoundByForegroundUid;

    }

    @Override
   
    public boolean balShowToasts() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balShowToasts;

    }

    @Override
   
    public boolean balShowToastsBlocked() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!responsible_apis_is_cached) {
                load_overrides_responsible_apis();
            }
        }
        return balShowToastsBlocked;

    }

    @Override
   
    public boolean blastSyncNotificationShadeOnDisplaySwitch() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return blastSyncNotificationShadeOnDisplaySwitch;

    }

    @Override
   
    public boolean bundleClientTransactionFlag() {
        return true;

    }

    @Override
   
    public boolean cameraCompatForFreeform() {
        return true;

    }

    @Override
   
    public boolean closeToSquareConfigIncludesStatusBar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return closeToSquareConfigIncludesStatusBar;

    }

    @Override
   
    public boolean configurableFontScaleDefault() {
        return true;

    }

    @Override
   
    public boolean coverDisplayOptIn() {
        return true;

    }

    @Override
   
    public boolean deferDisplayUpdates() {
        return true;

    }

    @Override
   
    public boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return delayNotificationToMagnificationWhenRecentsWindowToFrontTransition;

    }

    @Override
   
    public boolean delegateUnhandledDrags() {
        return true;

    }

    @Override
   
    public boolean deleteCaptureDisplay() {
        return true;

    }

    @Override
   
    public boolean density390Api() {
        return true;

    }

    @Override
   
    public boolean disableObjectPool() {
        return true;

    }

    @Override
   
    public boolean disableThinLetterboxingPolicy() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return disableThinLetterboxingPolicy;

    }

    @Override
   
    public boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return doNotCheckIntersectionWhenNonMagnifiableWindowTransitions;

    }

    @Override
   
    public boolean drawSnapshotAspectRatioMatch() {
        return false;

    }

    @Override
   
    public boolean edgeToEdgeByDefault() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return edgeToEdgeByDefault;

    }

    @Override
   
    public boolean embeddedActivityBackNavFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return embeddedActivityBackNavFlag;

    }

    @Override
   
    public boolean enableAdditionalWindowsAboveStatusBar() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableAdditionalWindowsAboveStatusBar;

    }

    @Override
   
    public boolean enableAppHeaderWithTaskDensity() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableAppHeaderWithTaskDensity;

    }

    @Override
   
    public boolean enableBufferTransformHintFromDisplay() {
        return true;

    }

    @Override
   
    public boolean enableCameraCompatForDesktopWindowing() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableCameraCompatForDesktopWindowing;

    }

    @Override
   
    public boolean enableCompatuiSysuiLauncher() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return enableCompatuiSysuiLauncher;

    }

    @Override
   
    public boolean enableDesktopWindowingImmersiveHandleHiding() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingImmersiveHandleHiding;

    }

    @Override
   
    public boolean enableDesktopWindowingModalsPolicy() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingModalsPolicy;

    }

    @Override
   
    public boolean enableDesktopWindowingMode() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingMode;

    }

    @Override
   
    public boolean enableDesktopWindowingQuickSwitch() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingQuickSwitch;

    }

    @Override
   
    public boolean enableDesktopWindowingScvhCache() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingScvhCache;

    }

    @Override
   
    public boolean enableDesktopWindowingSizeConstraints() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingSizeConstraints;

    }

    @Override
   
    public boolean enableDesktopWindowingTaskLimit() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingTaskLimit;

    }

    @Override
   
    public boolean enableDesktopWindowingTaskbarRunningApps() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingTaskbarRunningApps;

    }

    @Override
   
    public boolean enableDesktopWindowingWallpaperActivity() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableDesktopWindowingWallpaperActivity;

    }

    @Override
   
    public boolean enableScaledResizing() {
        return false;

    }

    @Override
   
    public boolean enableTaskStackObserverInShell() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableTaskStackObserverInShell;

    }

    @Override
   
    public boolean enableThemedAppHeaders() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableThemedAppHeaders;

    }

    @Override
   
    public boolean enableWindowingDynamicInitialBounds() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableWindowingDynamicInitialBounds;

    }

    @Override
   
    public boolean enableWindowingEdgeDragResize() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!lse_desktop_experience_is_cached) {
                load_overrides_lse_desktop_experience();
            }
        }
        return enableWindowingEdgeDragResize;

    }

    @Override
   
    public boolean enableWmExtensionsForAllFlag() {
        return true;

    }

    @Override
   
    public boolean enforceEdgeToEdge() {
        return true;

    }

    @Override
   
    public boolean ensureWallpaperInTransitions() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return ensureWallpaperInTransitions;

    }

    @Override
   
    public boolean explicitRefreshRateHints() {
        return true;

    }

    @Override
   
    public boolean fifoPriorityForMajorUiProcesses() {
        return false;

    }

    @Override
   
    public boolean fixNoContainerUpdateWithoutResize() {
        return false;

    }

    @Override
   
    public boolean fixPipRestoreToOverlay() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return fixPipRestoreToOverlay;

    }

    @Override
   
    public boolean fullscreenDimFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return fullscreenDimFlag;

    }

    @Override
   
    public boolean getDimmerOnClosing() {
        return true;

    }

    @Override
   
    public boolean immersiveAppRepositioning() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return immersiveAppRepositioning;

    }

    @Override
   
    public boolean insetsControlChangedItem() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return insetsControlChangedItem;

    }

    @Override
   
    public boolean insetsControlSeq() {
        return false;

    }

    @Override
   
    public boolean insetsDecoupledConfiguration() {
        return true;

    }

    @Override
   
    public boolean introduceSmootherDimmer() {
        return true;

    }

    @Override
   
    public boolean keyguardAppearTransition() {
        return true;

    }

    @Override
   
    public boolean letterboxBackgroundWallpaper() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return letterboxBackgroundWallpaper;

    }

    @Override
   
    public boolean movableCutoutConfiguration() {
        return true;

    }

    @Override
   
    public boolean moveAnimationOptionsToChange() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return moveAnimationOptionsToChange;

    }

    @Override
   
    public boolean multiCrop() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return multiCrop;

    }

    @Override
   
    public boolean navBarTransparentByDefault() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return navBarTransparentByDefault;

    }

    @Override
   
    public boolean noConsecutiveVisibilityEvents() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return noConsecutiveVisibilityEvents;

    }

    @Override
   
    public boolean noVisibilityEventOnDisplayStateChange() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!wear_frameworks_is_cached) {
                load_overrides_wear_frameworks();
            }
        }
        return noVisibilityEventOnDisplayStateChange;

    }

    @Override
   
    public boolean offloadColorExtraction() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return offloadColorExtraction;

    }

    @Override
   
    public boolean predictiveBackSystemAnims() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!systemui_is_cached) {
                load_overrides_systemui();
            }
        }
        return predictiveBackSystemAnims;

    }

    @Override
   
    public boolean rearDisplayDisableForceDesktopSystemDecorations() {
        return true;

    }

    @Override
   
    public boolean releaseSnapshotAggressively() {
        return false;

    }

    @Override
   
    public boolean removePrepareSurfaceInPlacement() {
        return true;

    }

    @Override
   
    public boolean screenRecordingCallbacks() {
        return true;

    }

    @Override
   
    public boolean sdkDesiredPresentTime() {
        return true;

    }

    @Override
   
    public boolean secureWindowState() {
        return true;

    }

    @Override
   
    public boolean setScPropertiesInClient() {
        return false;

    }

    @Override
   
    public boolean skipSleepingWhenSwitchingDisplay() {
        return true;

    }

    @Override
   
    public boolean supportsMultiInstanceSystemUi() {
        return true;

    }

    @Override
   
    public boolean surfaceControlInputReceiver() {
        return true;

    }

    @Override
   
    public boolean surfaceTrustedOverlay() {
        return true;

    }

    @Override
   
    public boolean syncScreenCapture() {
        return true;

    }

    @Override
   
    public boolean taskFragmentSystemOrganizerFlag() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return taskFragmentSystemOrganizerFlag;

    }

    @Override
   
    public boolean transitReadyTracking() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return transitReadyTracking;

    }

    @Override
   
    public boolean trustedPresentationListenerForWindow() {
        return true;

    }

    @Override
   
    public boolean untrustedEmbeddingAnyAppPermission() {
        return true;

    }

    @Override
   
    public boolean untrustedEmbeddingStateSharing() {
        return true;

    }

    @Override
   
    public boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!accessibility_is_cached) {
                load_overrides_accessibility();
            }
        }
        return useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds;

    }

    @Override
   
    public boolean userMinAspectRatioAppDefault() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!large_screen_experiences_app_compat_is_cached) {
                load_overrides_large_screen_experiences_app_compat();
            }
        }
        return userMinAspectRatioAppDefault;

    }

    @Override
   
    public boolean waitForTransitionOnDisplaySwitch() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_frontend_is_cached) {
                load_overrides_windowing_frontend();
            }
        }
        return waitForTransitionOnDisplaySwitch;

    }

    @Override
   
    public boolean wallpaperOffsetAsync() {
        return true;

    }

    @Override
   
    public boolean windowSessionRelayoutInfo() {
        return true;

    }

    @Override
   
    public boolean windowTokenConfigThreadSafe() {
        if (isReadFromNew) {
            if (!isCached) {
                init();
            }
        } else {
            if (!windowing_sdk_is_cached) {
                load_overrides_windowing_sdk();
            }
        }
        return windowTokenConfigThreadSafe;

    }

}



