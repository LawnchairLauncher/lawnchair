package com.android.wm.shell;


/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_BUG_ROTATION_BUTTON_COVER_BUBBLE = "com.android.wm.shell.bug_rotation_button_cover_bubble";
    /** @hide */
    public static final String FLAG_DISMISS_PIP_FROM_LOCKSCREEN = "com.android.wm.shell.dismiss_pip_from_lockscreen";
    /** @hide */
    public static final String FLAG_ENABLE_2X1_SPLIT = "com.android.wm.shell.enable_2x1_split";
    /** @hide */
    public static final String FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER = "com.android.wm.shell.enable_auto_task_stack_controller";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_ANYTHING = "com.android.wm.shell.enable_bubble_anything";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR = "com.android.wm.shell.enable_bubble_bar";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR_ON_PHONES = "com.android.wm.shell.enable_bubble_bar_on_phones";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR_TO_FLOATING_TRANSITION = "com.android.wm.shell.enable_bubble_bar_to_floating_transition";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS = "com.android.wm.shell.enable_bubble_event_history_logs";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_STASHING = "com.android.wm.shell.enable_bubble_stashing";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_TO_FULLSCREEN = "com.android.wm.shell.enable_bubble_to_fullscreen";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE = "com.android.wm.shell.enable_bubbles_long_press_nav_handle";
    /** @hide */
    public static final String FLAG_ENABLE_CREATE_ANY_BUBBLE = "com.android.wm.shell.enable_create_any_bubble";
    /** @hide */
    public static final String FLAG_ENABLE_DYNAMIC_INSETS_FOR_APP_LAUNCH = "com.android.wm.shell.enable_dynamic_insets_for_app_launch";
    /** @hide */
    public static final String FLAG_ENABLE_FLEXIBLE_SPLIT = "com.android.wm.shell.enable_flexible_split";
    /** @hide */
    public static final String FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT = "com.android.wm.shell.enable_flexible_two_app_split";
    /** @hide */
    public static final String FLAG_ENABLE_GSF = "com.android.wm.shell.enable_gsf";
    /** @hide */
    public static final String FLAG_ENABLE_MAGNETIC_SPLIT_DIVIDER = "com.android.wm.shell.enable_magnetic_split_divider";
    /** @hide */
    public static final String FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS = "com.android.wm.shell.enable_new_bubble_animations";
    /** @hide */
    public static final String FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW = "com.android.wm.shell.enable_optional_bubble_overflow";
    /** @hide */
    public static final String FLAG_ENABLE_PIP2 = "com.android.wm.shell.enable_pip2";
    /** @hide */
    public static final String FLAG_ENABLE_PIP2_ON_TV = "com.android.wm.shell.enable_pip2_on_tv";
    /** @hide */
    public static final String FLAG_ENABLE_PIP_BOX_SHADOWS = "com.android.wm.shell.enable_pip_box_shadows";
    /** @hide */
    public static final String FLAG_ENABLE_PIP_UMO_EXPERIENCE = "com.android.wm.shell.enable_pip_umo_experience";
    /** @hide */
    public static final String FLAG_ENABLE_RETRIEVABLE_BUBBLES = "com.android.wm.shell.enable_retrievable_bubbles";
    /** @hide */
    public static final String FLAG_ENABLE_SHELL_RESTART_BUBBLE_CLEANUP = "com.android.wm.shell.enable_shell_restart_bubble_cleanup";
    /** @hide */
    public static final String FLAG_ENABLE_SHELL_TOP_TASK_TRACKING = "com.android.wm.shell.enable_shell_top_task_tracking";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION = "com.android.wm.shell.enable_taskbar_navbar_unification";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_ON_PHONES = "com.android.wm.shell.enable_taskbar_on_phones";
    /** @hide */
    public static final String FLAG_ENABLE_TINY_TASKBAR = "com.android.wm.shell.enable_tiny_taskbar";
    /** @hide */
    public static final String FLAG_FIX_BUBBLE_STACK_VIEW_EXPANDED_WHEN_ADDED = "com.android.wm.shell.fix_bubble_stack_view_expanded_when_added";
    /** @hide */
    public static final String FLAG_FIX_BUBBLES_ADD_SAME_BUBBLE_BEING_REMOVED = "com.android.wm.shell.fix_bubbles_add_same_bubble_being_removed";
    /** @hide */
    public static final String FLAG_FIX_BUBBLES_CANCEL_ANIMATION = "com.android.wm.shell.fix_bubbles_cancel_animation";
    /** @hide */
    public static final String FLAG_FIX_BUBBLES_EXPANDED_SYSUI_FLAG = "com.android.wm.shell.fix_bubbles_expanded_sysui_flag";
    /** @hide */
    public static final String FLAG_FIX_BUBBLES_IME_FOCUS_FLICKER = "com.android.wm.shell.fix_bubbles_ime_focus_flicker";
    /** @hide */
    public static final String FLAG_FIX_EXIT_SPLIT_ON_ENTER_BUBBLE = "com.android.wm.shell.fix_exit_split_on_enter_bubble";
    /** @hide */
    public static final String FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS = "com.android.wm.shell.fix_missing_user_change_callbacks";
    /** @hide */
    public static final String FLAG_FIX_TASK_VIEW_ROTATION_ANIMATION = "com.android.wm.shell.fix_task_view_rotation_animation";
    /** @hide */
    public static final String FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS = "com.android.wm.shell.split_disable_child_task_bounds";
    /** @hide */
    public static final String FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE = "com.android.wm.shell.split_to_full_set_window_mode";
    /** @hide */
    public static final String FLAG_TASK_VIEW_TRANSITIONS_REFACTOR = "com.android.wm.shell.task_view_transitions_refactor";


    public static boolean bugRotationButtonCoverBubble() {
        
        return FEATURE_FLAGS.bugRotationButtonCoverBubble();
    }


    public static boolean dismissPipFromLockscreen() {
        
        return FEATURE_FLAGS.dismissPipFromLockscreen();
    }


    public static boolean enable2x1Split() {
        
        return FEATURE_FLAGS.enable2x1Split();
    }


    public static boolean enableAutoTaskStackController() {
        
        return FEATURE_FLAGS.enableAutoTaskStackController();
    }


    public static boolean enableBubbleAnything() {
        
        return FEATURE_FLAGS.enableBubbleAnything();
    }


    public static boolean enableBubbleBar() {
        
        return FEATURE_FLAGS.enableBubbleBar();
    }


    public static boolean enableBubbleBarOnPhones() {
        
        return FEATURE_FLAGS.enableBubbleBarOnPhones();
    }


    public static boolean enableBubbleBarToFloatingTransition() {
        
        return FEATURE_FLAGS.enableBubbleBarToFloatingTransition();
    }


    public static boolean enableBubbleEventHistoryLogs() {
        
        return FEATURE_FLAGS.enableBubbleEventHistoryLogs();
    }


    public static boolean enableBubbleStashing() {
        
        return FEATURE_FLAGS.enableBubbleStashing();
    }


    public static boolean enableBubbleToFullscreen() {
        
        return FEATURE_FLAGS.enableBubbleToFullscreen();
    }


    public static boolean enableBubblesLongPressNavHandle() {
        
        return FEATURE_FLAGS.enableBubblesLongPressNavHandle();
    }


    public static boolean enableCreateAnyBubble() {
        
        return FEATURE_FLAGS.enableCreateAnyBubble();
    }


    public static boolean enableDynamicInsetsForAppLaunch() {
        
        return FEATURE_FLAGS.enableDynamicInsetsForAppLaunch();
    }


    public static boolean enableFlexibleSplit() {
        
        return FEATURE_FLAGS.enableFlexibleSplit();
    }


    public static boolean enableFlexibleTwoAppSplit() {
        
        return FEATURE_FLAGS.enableFlexibleTwoAppSplit();
    }


    public static boolean enableGsf() {
        
        return FEATURE_FLAGS.enableGsf();
    }


    public static boolean enableMagneticSplitDivider() {
        
        return FEATURE_FLAGS.enableMagneticSplitDivider();
    }


    public static boolean enableNewBubbleAnimations() {
        
        return FEATURE_FLAGS.enableNewBubbleAnimations();
    }


    public static boolean enableOptionalBubbleOverflow() {
        
        return FEATURE_FLAGS.enableOptionalBubbleOverflow();
    }


    public static boolean enablePip2() {
        
        return FEATURE_FLAGS.enablePip2();
    }


    public static boolean enablePip2OnTv() {
        
        return FEATURE_FLAGS.enablePip2OnTv();
    }


    public static boolean enablePipBoxShadows() {
        
        return FEATURE_FLAGS.enablePipBoxShadows();
    }


    public static boolean enablePipUmoExperience() {
        
        return FEATURE_FLAGS.enablePipUmoExperience();
    }


    public static boolean enableRetrievableBubbles() {
        
        return FEATURE_FLAGS.enableRetrievableBubbles();
    }


    public static boolean enableShellRestartBubbleCleanup() {
        
        return FEATURE_FLAGS.enableShellRestartBubbleCleanup();
    }


    public static boolean enableShellTopTaskTracking() {
        
        return FEATURE_FLAGS.enableShellTopTaskTracking();
    }


    public static boolean enableTaskbarNavbarUnification() {
        
        return FEATURE_FLAGS.enableTaskbarNavbarUnification();
    }


    public static boolean enableTaskbarOnPhones() {
        
        return FEATURE_FLAGS.enableTaskbarOnPhones();
    }


    public static boolean enableTinyTaskbar() {
        
        return FEATURE_FLAGS.enableTinyTaskbar();
    }


    public static boolean fixBubbleStackViewExpandedWhenAdded() {
        
        return FEATURE_FLAGS.fixBubbleStackViewExpandedWhenAdded();
    }


    public static boolean fixBubblesAddSameBubbleBeingRemoved() {
        
        return FEATURE_FLAGS.fixBubblesAddSameBubbleBeingRemoved();
    }


    public static boolean fixBubblesCancelAnimation() {
        
        return FEATURE_FLAGS.fixBubblesCancelAnimation();
    }


    public static boolean fixBubblesExpandedSysuiFlag() {
        
        return FEATURE_FLAGS.fixBubblesExpandedSysuiFlag();
    }


    public static boolean fixBubblesImeFocusFlicker() {
        
        return FEATURE_FLAGS.fixBubblesImeFocusFlicker();
    }


    public static boolean fixExitSplitOnEnterBubble() {
        
        return FEATURE_FLAGS.fixExitSplitOnEnterBubble();
    }


    public static boolean fixMissingUserChangeCallbacks() {
        
        return FEATURE_FLAGS.fixMissingUserChangeCallbacks();
    }


    public static boolean fixTaskViewRotationAnimation() {
        
        return FEATURE_FLAGS.fixTaskViewRotationAnimation();
    }


    public static boolean splitDisableChildTaskBounds() {
        
        return FEATURE_FLAGS.splitDisableChildTaskBounds();
    }


    public static boolean splitToFullSetWindowMode() {
        
        return FEATURE_FLAGS.splitToFullSetWindowMode();
    }


    public static boolean taskViewTransitionsRefactor() {
        
        return FEATURE_FLAGS.taskViewTransitionsRefactor();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
