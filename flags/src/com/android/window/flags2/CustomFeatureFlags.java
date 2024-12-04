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
    
    public boolean activityEmbeddingAnimationCustomizationFlag() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG,
                FeatureFlags::activityEmbeddingAnimationCustomizationFlag);
    }

    @Override
    
    public boolean activityEmbeddingInteractiveDividerFlag() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
                FeatureFlags::activityEmbeddingInteractiveDividerFlag);
    }

    @Override
    
    public boolean activityEmbeddingOverlayPresentationFlag() {
        return getValue(Flags.FLAG_ACTIVITY_EMBEDDING_OVERLAY_PRESENTATION_FLAG,
                FeatureFlags::activityEmbeddingOverlayPresentationFlag);
    }

    @Override
    
    public boolean activitySnapshotByDefault() {
        return getValue(Flags.FLAG_ACTIVITY_SNAPSHOT_BY_DEFAULT,
                FeatureFlags::activitySnapshotByDefault);
    }

    @Override
    
    public boolean activityWindowInfoFlag() {
        return getValue(Flags.FLAG_ACTIVITY_WINDOW_INFO_FLAG,
                FeatureFlags::activityWindowInfoFlag);
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
    
    public boolean alwaysDeferTransitionWhenApplyWct() {
        return getValue(Flags.FLAG_ALWAYS_DEFER_TRANSITION_WHEN_APPLY_WCT,
                FeatureFlags::alwaysDeferTransitionWhenApplyWct);
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
    
    public boolean balRequireOptInByPendingIntentCreator() {
        return getValue(Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
                FeatureFlags::balRequireOptInByPendingIntentCreator);
    }

    @Override
    
    public boolean balRequireOptInSameUid() {
        return getValue(Flags.FLAG_BAL_REQUIRE_OPT_IN_SAME_UID,
                FeatureFlags::balRequireOptInSameUid);
    }

    @Override
    
    public boolean balRespectAppSwitchStateWhenCheckBoundByForegroundUid() {
        return getValue(Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
                FeatureFlags::balRespectAppSwitchStateWhenCheckBoundByForegroundUid);
    }

    @Override
    
    public boolean balShowToasts() {
        return getValue(Flags.FLAG_BAL_SHOW_TOASTS,
                FeatureFlags::balShowToasts);
    }

    @Override
    
    public boolean balShowToastsBlocked() {
        return getValue(Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED,
                FeatureFlags::balShowToastsBlocked);
    }

    @Override
    
    public boolean blastSyncNotificationShadeOnDisplaySwitch() {
        return getValue(Flags.FLAG_BLAST_SYNC_NOTIFICATION_SHADE_ON_DISPLAY_SWITCH,
                FeatureFlags::blastSyncNotificationShadeOnDisplaySwitch);
    }

    @Override
    
    public boolean bundleClientTransactionFlag() {
        return getValue(Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG,
                FeatureFlags::bundleClientTransactionFlag);
    }

    @Override
    
    public boolean cameraCompatForFreeform() {
        return getValue(Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
                FeatureFlags::cameraCompatForFreeform);
    }

    @Override
    
    public boolean closeToSquareConfigIncludesStatusBar() {
        return getValue(Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
                FeatureFlags::closeToSquareConfigIncludesStatusBar);
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
    
    public boolean deferDisplayUpdates() {
        return getValue(Flags.FLAG_DEFER_DISPLAY_UPDATES,
                FeatureFlags::deferDisplayUpdates);
    }

    @Override
    
    public boolean delayNotificationToMagnificationWhenRecentsWindowToFrontTransition() {
        return getValue(Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION,
                FeatureFlags::delayNotificationToMagnificationWhenRecentsWindowToFrontTransition);
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
    
    public boolean disableObjectPool() {
        return getValue(Flags.FLAG_DISABLE_OBJECT_POOL,
                FeatureFlags::disableObjectPool);
    }

    @Override
    
    public boolean disableThinLetterboxingPolicy() {
        return getValue(Flags.FLAG_DISABLE_THIN_LETTERBOXING_POLICY,
                FeatureFlags::disableThinLetterboxingPolicy);
    }

    @Override
    
    public boolean doNotCheckIntersectionWhenNonMagnifiableWindowTransitions() {
        return getValue(Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS,
                FeatureFlags::doNotCheckIntersectionWhenNonMagnifiableWindowTransitions);
    }

    @Override
    
    public boolean drawSnapshotAspectRatioMatch() {
        return getValue(Flags.FLAG_DRAW_SNAPSHOT_ASPECT_RATIO_MATCH,
                FeatureFlags::drawSnapshotAspectRatioMatch);
    }

    @Override
    
    public boolean edgeToEdgeByDefault() {
        return getValue(Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT,
                FeatureFlags::edgeToEdgeByDefault);
    }

    @Override
    
    public boolean embeddedActivityBackNavFlag() {
        return getValue(Flags.FLAG_EMBEDDED_ACTIVITY_BACK_NAV_FLAG,
                FeatureFlags::embeddedActivityBackNavFlag);
    }

    @Override
    
    public boolean enableAdditionalWindowsAboveStatusBar() {
        return getValue(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR,
                FeatureFlags::enableAdditionalWindowsAboveStatusBar);
    }

    @Override
    
    public boolean enableAppHeaderWithTaskDensity() {
        return getValue(Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
                FeatureFlags::enableAppHeaderWithTaskDensity);
    }

    @Override
    
    public boolean enableBufferTransformHintFromDisplay() {
        return getValue(Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
                FeatureFlags::enableBufferTransformHintFromDisplay);
    }

    @Override
    
    public boolean enableCameraCompatForDesktopWindowing() {
        return getValue(Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
                FeatureFlags::enableCameraCompatForDesktopWindowing);
    }

    @Override
    
    public boolean enableCompatuiSysuiLauncher() {
        return getValue(Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER,
                FeatureFlags::enableCompatuiSysuiLauncher);
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
    
    public boolean enableDesktopWindowingQuickSwitch() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
                FeatureFlags::enableDesktopWindowingQuickSwitch);
    }

    @Override
    
    public boolean enableDesktopWindowingScvhCache() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE,
                FeatureFlags::enableDesktopWindowingScvhCache);
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
    
    public boolean enableDesktopWindowingWallpaperActivity() {
        return getValue(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
                FeatureFlags::enableDesktopWindowingWallpaperActivity);
    }

    @Override
    
    public boolean enableScaledResizing() {
        return getValue(Flags.FLAG_ENABLE_SCALED_RESIZING,
                FeatureFlags::enableScaledResizing);
    }

    @Override
    
    public boolean enableTaskStackObserverInShell() {
        return getValue(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
                FeatureFlags::enableTaskStackObserverInShell);
    }

    @Override
    
    public boolean enableThemedAppHeaders() {
        return getValue(Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
                FeatureFlags::enableThemedAppHeaders);
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
    
    public boolean enableWmExtensionsForAllFlag() {
        return getValue(Flags.FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG,
                FeatureFlags::enableWmExtensionsForAllFlag);
    }

    @Override
    
    public boolean enforceEdgeToEdge() {
        return getValue(Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
                FeatureFlags::enforceEdgeToEdge);
    }

    @Override
    
    public boolean ensureWallpaperInTransitions() {
        return getValue(Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS,
                FeatureFlags::ensureWallpaperInTransitions);
    }

    @Override
    
    public boolean explicitRefreshRateHints() {
        return getValue(Flags.FLAG_EXPLICIT_REFRESH_RATE_HINTS,
                FeatureFlags::explicitRefreshRateHints);
    }

    @Override
    
    public boolean fifoPriorityForMajorUiProcesses() {
        return getValue(Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
                FeatureFlags::fifoPriorityForMajorUiProcesses);
    }

    @Override
    
    public boolean fixNoContainerUpdateWithoutResize() {
        return getValue(Flags.FLAG_FIX_NO_CONTAINER_UPDATE_WITHOUT_RESIZE,
                FeatureFlags::fixNoContainerUpdateWithoutResize);
    }

    @Override
    
    public boolean fixPipRestoreToOverlay() {
        return getValue(Flags.FLAG_FIX_PIP_RESTORE_TO_OVERLAY,
                FeatureFlags::fixPipRestoreToOverlay);
    }

    @Override
    
    public boolean fullscreenDimFlag() {
        return getValue(Flags.FLAG_FULLSCREEN_DIM_FLAG,
                FeatureFlags::fullscreenDimFlag);
    }

    @Override
    
    public boolean getDimmerOnClosing() {
        return getValue(Flags.FLAG_GET_DIMMER_ON_CLOSING,
                FeatureFlags::getDimmerOnClosing);
    }

    @Override
    
    public boolean immersiveAppRepositioning() {
        return getValue(Flags.FLAG_IMMERSIVE_APP_REPOSITIONING,
                FeatureFlags::immersiveAppRepositioning);
    }

    @Override
    
    public boolean insetsControlChangedItem() {
        return getValue(Flags.FLAG_INSETS_CONTROL_CHANGED_ITEM,
                FeatureFlags::insetsControlChangedItem);
    }

    @Override
    
    public boolean insetsControlSeq() {
        return getValue(Flags.FLAG_INSETS_CONTROL_SEQ,
                FeatureFlags::insetsControlSeq);
    }

    @Override
    
    public boolean insetsDecoupledConfiguration() {
        return getValue(Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
                FeatureFlags::insetsDecoupledConfiguration);
    }

    @Override
    
    public boolean introduceSmootherDimmer() {
        return getValue(Flags.FLAG_INTRODUCE_SMOOTHER_DIMMER,
                FeatureFlags::introduceSmootherDimmer);
    }

    @Override
    
    public boolean keyguardAppearTransition() {
        return getValue(Flags.FLAG_KEYGUARD_APPEAR_TRANSITION,
                FeatureFlags::keyguardAppearTransition);
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
    
    public boolean moveAnimationOptionsToChange() {
        return getValue(Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE,
                FeatureFlags::moveAnimationOptionsToChange);
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
    
    public boolean noConsecutiveVisibilityEvents() {
        return getValue(Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS,
                FeatureFlags::noConsecutiveVisibilityEvents);
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
    
    public boolean predictiveBackSystemAnims() {
        return getValue(Flags.FLAG_PREDICTIVE_BACK_SYSTEM_ANIMS,
                FeatureFlags::predictiveBackSystemAnims);
    }

    @Override
    
    public boolean rearDisplayDisableForceDesktopSystemDecorations() {
        return getValue(Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
                FeatureFlags::rearDisplayDisableForceDesktopSystemDecorations);
    }

    @Override
    
    public boolean releaseSnapshotAggressively() {
        return getValue(Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
                FeatureFlags::releaseSnapshotAggressively);
    }

    @Override
    
    public boolean removePrepareSurfaceInPlacement() {
        return getValue(Flags.FLAG_REMOVE_PREPARE_SURFACE_IN_PLACEMENT,
                FeatureFlags::removePrepareSurfaceInPlacement);
    }

    @Override
    
    public boolean screenRecordingCallbacks() {
        return getValue(Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
                FeatureFlags::screenRecordingCallbacks);
    }

    @Override
    
    public boolean sdkDesiredPresentTime() {
        return getValue(Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
                FeatureFlags::sdkDesiredPresentTime);
    }

    @Override
    
    public boolean secureWindowState() {
        return getValue(Flags.FLAG_SECURE_WINDOW_STATE,
                FeatureFlags::secureWindowState);
    }

    @Override
    
    public boolean setScPropertiesInClient() {
        return getValue(Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
                FeatureFlags::setScPropertiesInClient);
    }

    @Override
    
    public boolean skipSleepingWhenSwitchingDisplay() {
        return getValue(Flags.FLAG_SKIP_SLEEPING_WHEN_SWITCHING_DISPLAY,
                FeatureFlags::skipSleepingWhenSwitchingDisplay);
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
    
    public boolean taskFragmentSystemOrganizerFlag() {
        return getValue(Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG,
                FeatureFlags::taskFragmentSystemOrganizerFlag);
    }

    @Override
    
    public boolean transitReadyTracking() {
        return getValue(Flags.FLAG_TRANSIT_READY_TRACKING,
                FeatureFlags::transitReadyTracking);
    }

    @Override
    
    public boolean trustedPresentationListenerForWindow() {
        return getValue(Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
                FeatureFlags::trustedPresentationListenerForWindow);
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
    
    public boolean useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds() {
        return getValue(Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS,
                FeatureFlags::useWindowOriginalTouchableRegionWhenMagnificationRecomputeBounds);
    }

    @Override
    
    public boolean userMinAspectRatioAppDefault() {
        return getValue(Flags.FLAG_USER_MIN_ASPECT_RATIO_APP_DEFAULT,
                FeatureFlags::userMinAspectRatioAppDefault);
    }

    @Override
    
    public boolean waitForTransitionOnDisplaySwitch() {
        return getValue(Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH,
                FeatureFlags::waitForTransitionOnDisplaySwitch);
    }

    @Override
    
    public boolean wallpaperOffsetAsync() {
        return getValue(Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
                FeatureFlags::wallpaperOffsetAsync);
    }

    @Override
    
    public boolean windowSessionRelayoutInfo() {
        return getValue(Flags.FLAG_WINDOW_SESSION_RELAYOUT_INFO,
                FeatureFlags::windowSessionRelayoutInfo);
    }

    @Override
    
    public boolean windowTokenConfigThreadSafe() {
        return getValue(Flags.FLAG_WINDOW_TOKEN_CONFIG_THREAD_SAFE,
                FeatureFlags::windowTokenConfigThreadSafe);
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
                Flags.FLAG_ACTIVITY_EMBEDDING_ANIMATION_CUSTOMIZATION_FLAG,
                Flags.FLAG_ACTIVITY_EMBEDDING_INTERACTIVE_DIVIDER_FLAG,
                Flags.FLAG_ACTIVITY_EMBEDDING_OVERLAY_PRESENTATION_FLAG,
                Flags.FLAG_ACTIVITY_SNAPSHOT_BY_DEFAULT,
                Flags.FLAG_ACTIVITY_WINDOW_INFO_FLAG,
                Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
                Flags.FLAG_ALLOW_HIDE_SCM_BUTTON,
                Flags.FLAG_ALLOWS_SCREEN_SIZE_DECOUPLED_FROM_STATUS_BAR_AND_CUTOUT,
                Flags.FLAG_ALWAYS_DEFER_TRANSITION_WHEN_APPLY_WCT,
                Flags.FLAG_ALWAYS_DRAW_MAGNIFICATION_FULLSCREEN_BORDER,
                Flags.FLAG_ALWAYS_UPDATE_WALLPAPER_PERMISSION,
                Flags.FLAG_APP_COMPAT_PROPERTIES_API,
                Flags.FLAG_APP_COMPAT_REFACTORING,
                Flags.FLAG_BAL_DONT_BRING_EXISTING_BACKGROUND_TASK_STACK_TO_FG,
                Flags.FLAG_BAL_IMPROVE_REAL_CALLER_VISIBILITY_CHECK,
                Flags.FLAG_BAL_IMPROVED_METRICS,
                Flags.FLAG_BAL_REQUIRE_OPT_IN_BY_PENDING_INTENT_CREATOR,
                Flags.FLAG_BAL_REQUIRE_OPT_IN_SAME_UID,
                Flags.FLAG_BAL_RESPECT_APP_SWITCH_STATE_WHEN_CHECK_BOUND_BY_FOREGROUND_UID,
                Flags.FLAG_BAL_SHOW_TOASTS,
                Flags.FLAG_BAL_SHOW_TOASTS_BLOCKED,
                Flags.FLAG_BLAST_SYNC_NOTIFICATION_SHADE_ON_DISPLAY_SWITCH,
                Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG,
                Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
                Flags.FLAG_CLOSE_TO_SQUARE_CONFIG_INCLUDES_STATUS_BAR,
                Flags.FLAG_CONFIGURABLE_FONT_SCALE_DEFAULT,
                Flags.FLAG_COVER_DISPLAY_OPT_IN,
                Flags.FLAG_DEFER_DISPLAY_UPDATES,
                Flags.FLAG_DELAY_NOTIFICATION_TO_MAGNIFICATION_WHEN_RECENTS_WINDOW_TO_FRONT_TRANSITION,
                Flags.FLAG_DELEGATE_UNHANDLED_DRAGS,
                Flags.FLAG_DELETE_CAPTURE_DISPLAY,
                Flags.FLAG_DENSITY_390_API,
                Flags.FLAG_DISABLE_OBJECT_POOL,
                Flags.FLAG_DISABLE_THIN_LETTERBOXING_POLICY,
                Flags.FLAG_DO_NOT_CHECK_INTERSECTION_WHEN_NON_MAGNIFIABLE_WINDOW_TRANSITIONS,
                Flags.FLAG_DRAW_SNAPSHOT_ASPECT_RATIO_MATCH,
                Flags.FLAG_EDGE_TO_EDGE_BY_DEFAULT,
                Flags.FLAG_EMBEDDED_ACTIVITY_BACK_NAV_FLAG,
                Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR,
                Flags.FLAG_ENABLE_APP_HEADER_WITH_TASK_DENSITY,
                Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
                Flags.FLAG_ENABLE_CAMERA_COMPAT_FOR_DESKTOP_WINDOWING,
                Flags.FLAG_ENABLE_COMPATUI_SYSUI_LAUNCHER,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_QUICK_SWITCH,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SCVH_CACHE,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASK_LIMIT,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS,
                Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY,
                Flags.FLAG_ENABLE_SCALED_RESIZING,
                Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL,
                Flags.FLAG_ENABLE_THEMED_APP_HEADERS,
                Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS,
                Flags.FLAG_ENABLE_WINDOWING_EDGE_DRAG_RESIZE,
                Flags.FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG,
                Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
                Flags.FLAG_ENSURE_WALLPAPER_IN_TRANSITIONS,
                Flags.FLAG_EXPLICIT_REFRESH_RATE_HINTS,
                Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
                Flags.FLAG_FIX_NO_CONTAINER_UPDATE_WITHOUT_RESIZE,
                Flags.FLAG_FIX_PIP_RESTORE_TO_OVERLAY,
                Flags.FLAG_FULLSCREEN_DIM_FLAG,
                Flags.FLAG_GET_DIMMER_ON_CLOSING,
                Flags.FLAG_IMMERSIVE_APP_REPOSITIONING,
                Flags.FLAG_INSETS_CONTROL_CHANGED_ITEM,
                Flags.FLAG_INSETS_CONTROL_SEQ,
                Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
                Flags.FLAG_INTRODUCE_SMOOTHER_DIMMER,
                Flags.FLAG_KEYGUARD_APPEAR_TRANSITION,
                Flags.FLAG_LETTERBOX_BACKGROUND_WALLPAPER,
                Flags.FLAG_MOVABLE_CUTOUT_CONFIGURATION,
                Flags.FLAG_MOVE_ANIMATION_OPTIONS_TO_CHANGE,
                Flags.FLAG_MULTI_CROP,
                Flags.FLAG_NAV_BAR_TRANSPARENT_BY_DEFAULT,
                Flags.FLAG_NO_CONSECUTIVE_VISIBILITY_EVENTS,
                Flags.FLAG_NO_VISIBILITY_EVENT_ON_DISPLAY_STATE_CHANGE,
                Flags.FLAG_OFFLOAD_COLOR_EXTRACTION,
                Flags.FLAG_PREDICTIVE_BACK_SYSTEM_ANIMS,
                Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
                Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
                Flags.FLAG_REMOVE_PREPARE_SURFACE_IN_PLACEMENT,
                Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
                Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
                Flags.FLAG_SECURE_WINDOW_STATE,
                Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
                Flags.FLAG_SKIP_SLEEPING_WHEN_SWITCHING_DISPLAY,
                Flags.FLAG_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI,
                Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER,
                Flags.FLAG_SURFACE_TRUSTED_OVERLAY,
                Flags.FLAG_SYNC_SCREEN_CAPTURE,
                Flags.FLAG_TASK_FRAGMENT_SYSTEM_ORGANIZER_FLAG,
                Flags.FLAG_TRANSIT_READY_TRACKING,
                Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
                Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
                Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
                Flags.FLAG_USE_WINDOW_ORIGINAL_TOUCHABLE_REGION_WHEN_MAGNIFICATION_RECOMPUTE_BOUNDS,
                Flags.FLAG_USER_MIN_ASPECT_RATIO_APP_DEFAULT,
                Flags.FLAG_WAIT_FOR_TRANSITION_ON_DISPLAY_SWITCH,
                Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
                Flags.FLAG_WINDOW_SESSION_RELAYOUT_INFO,
                Flags.FLAG_WINDOW_TOKEN_CONFIG_THREAD_SAFE
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
            Arrays.asList(
                    Flags.FLAG_ACTIVITY_SNAPSHOT_BY_DEFAULT,
                    Flags.FLAG_ACTIVITY_WINDOW_INFO_FLAG,
                    Flags.FLAG_ALLOW_DISABLE_ACTIVITY_RECORD_INPUT_SINK,
                    Flags.FLAG_ALLOWS_SCREEN_SIZE_DECOUPLED_FROM_STATUS_BAR_AND_CUTOUT,
                    Flags.FLAG_APP_COMPAT_PROPERTIES_API,
                    Flags.FLAG_APP_COMPAT_REFACTORING,
                    Flags.FLAG_BUNDLE_CLIENT_TRANSACTION_FLAG,
                    Flags.FLAG_CAMERA_COMPAT_FOR_FREEFORM,
                    Flags.FLAG_CONFIGURABLE_FONT_SCALE_DEFAULT,
                    Flags.FLAG_COVER_DISPLAY_OPT_IN,
                    Flags.FLAG_DEFER_DISPLAY_UPDATES,
                    Flags.FLAG_DELEGATE_UNHANDLED_DRAGS,
                    Flags.FLAG_DELETE_CAPTURE_DISPLAY,
                    Flags.FLAG_DENSITY_390_API,
                    Flags.FLAG_DISABLE_OBJECT_POOL,
                    Flags.FLAG_DRAW_SNAPSHOT_ASPECT_RATIO_MATCH,
                    Flags.FLAG_ENABLE_BUFFER_TRANSFORM_HINT_FROM_DISPLAY,
                    Flags.FLAG_ENABLE_SCALED_RESIZING,
                    Flags.FLAG_ENABLE_WM_EXTENSIONS_FOR_ALL_FLAG,
                    Flags.FLAG_ENFORCE_EDGE_TO_EDGE,
                    Flags.FLAG_EXPLICIT_REFRESH_RATE_HINTS,
                    Flags.FLAG_FIFO_PRIORITY_FOR_MAJOR_UI_PROCESSES,
                    Flags.FLAG_FIX_NO_CONTAINER_UPDATE_WITHOUT_RESIZE,
                    Flags.FLAG_GET_DIMMER_ON_CLOSING,
                    Flags.FLAG_INSETS_CONTROL_SEQ,
                    Flags.FLAG_INSETS_DECOUPLED_CONFIGURATION,
                    Flags.FLAG_INTRODUCE_SMOOTHER_DIMMER,
                    Flags.FLAG_KEYGUARD_APPEAR_TRANSITION,
                    Flags.FLAG_MOVABLE_CUTOUT_CONFIGURATION,
                    Flags.FLAG_REAR_DISPLAY_DISABLE_FORCE_DESKTOP_SYSTEM_DECORATIONS,
                    Flags.FLAG_RELEASE_SNAPSHOT_AGGRESSIVELY,
                    Flags.FLAG_REMOVE_PREPARE_SURFACE_IN_PLACEMENT,
                    Flags.FLAG_SCREEN_RECORDING_CALLBACKS,
                    Flags.FLAG_SDK_DESIRED_PRESENT_TIME,
                    Flags.FLAG_SECURE_WINDOW_STATE,
                    Flags.FLAG_SET_SC_PROPERTIES_IN_CLIENT,
                    Flags.FLAG_SKIP_SLEEPING_WHEN_SWITCHING_DISPLAY,
                    Flags.FLAG_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI,
                    Flags.FLAG_SURFACE_CONTROL_INPUT_RECEIVER,
                    Flags.FLAG_SURFACE_TRUSTED_OVERLAY,
                    Flags.FLAG_SYNC_SCREEN_CAPTURE,
                    Flags.FLAG_TRUSTED_PRESENTATION_LISTENER_FOR_WINDOW,
                    Flags.FLAG_UNTRUSTED_EMBEDDING_ANY_APP_PERMISSION,
                    Flags.FLAG_UNTRUSTED_EMBEDDING_STATE_SHARING,
                    Flags.FLAG_WALLPAPER_OFFSET_ASYNC,
                    Flags.FLAG_WINDOW_SESSION_RELAYOUT_INFO,
                    ""
            )
    );
}
