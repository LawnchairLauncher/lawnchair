package com.android.wm.shell;


/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER = "com.android.wm.shell.enable_auto_task_stack_controller";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_ANYTHING = "com.android.wm.shell.enable_bubble_anything";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_APP_COMPAT_FIXES = "com.android.wm.shell.enable_bubble_app_compat_fixes";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR = "com.android.wm.shell.enable_bubble_bar";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR_ON_PHONES = "com.android.wm.shell.enable_bubble_bar_on_phones";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_STASHING = "com.android.wm.shell.enable_bubble_stashing";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_SWIPE_UP_CLEANUP = "com.android.wm.shell.enable_bubble_swipe_up_cleanup";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_TASK_VIEW_LISTENER = "com.android.wm.shell.enable_bubble_task_view_listener";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_TO_FULLSCREEN = "com.android.wm.shell.enable_bubble_to_fullscreen";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE = "com.android.wm.shell.enable_bubbles_long_press_nav_handle";
    /** @hide */
    public static final String FLAG_ENABLE_CREATE_ANY_BUBBLE = "com.android.wm.shell.enable_create_any_bubble";
    /** @hide */
    public static final String FLAG_ENABLE_DYNAMIC_INSETS_FOR_APP_LAUNCH = "com.android.wm.shell.enable_dynamic_insets_for_app_launch";
    /** @hide */
    public static final String FLAG_ENABLE_ENTER_SPLIT_REMOVE_BUBBLE = "com.android.wm.shell.enable_enter_split_remove_bubble";
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
    public static final String FLAG_ENABLE_PIP_BOX_SHADOWS = "com.android.wm.shell.enable_pip_box_shadows";
    /** @hide */
    public static final String FLAG_ENABLE_PIP_UMO_EXPERIENCE = "com.android.wm.shell.enable_pip_umo_experience";
    /** @hide */
    public static final String FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION = "com.android.wm.shell.enable_recents_bookend_transition";
    /** @hide */
    public static final String FLAG_ENABLE_RETRIEVABLE_BUBBLES = "com.android.wm.shell.enable_retrievable_bubbles";
    /** @hide */
    public static final String FLAG_ENABLE_SHELL_TOP_TASK_TRACKING = "com.android.wm.shell.enable_shell_top_task_tracking";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION = "com.android.wm.shell.enable_taskbar_navbar_unification";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_ON_PHONES = "com.android.wm.shell.enable_taskbar_on_phones";
    /** @hide */
    public static final String FLAG_ENABLE_TINY_TASKBAR = "com.android.wm.shell.enable_tiny_taskbar";
    /** @hide */
    public static final String FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS = "com.android.wm.shell.fix_missing_user_change_callbacks";
    /** @hide */
    public static final String FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE = "com.android.wm.shell.only_reuse_bubbled_task_when_launched_from_bubble";
    /** @hide */
    public static final String FLAG_TASK_VIEW_REPOSITORY = "com.android.wm.shell.task_view_repository";
    /** @hide */
    public static final String FLAG_TASK_VIEW_TRANSITIONS_REFACTOR = "com.android.wm.shell.task_view_transitions_refactor";


    public static boolean enableAutoTaskStackController() {
        
        return FEATURE_FLAGS.enableAutoTaskStackController();
    }


    public static boolean enableBubbleAnything() {
        
        return FEATURE_FLAGS.enableBubbleAnything();
    }


    public static boolean enableBubbleAppCompatFixes() {
        
        return FEATURE_FLAGS.enableBubbleAppCompatFixes();
    }


    public static boolean enableBubbleBar() {
        
        return FEATURE_FLAGS.enableBubbleBar();
    }


    public static boolean enableBubbleBarOnPhones() {
        
        return FEATURE_FLAGS.enableBubbleBarOnPhones();
    }


    public static boolean enableBubbleStashing() {
        
        return FEATURE_FLAGS.enableBubbleStashing();
    }


    public static boolean enableBubbleSwipeUpCleanup() {
        
        return FEATURE_FLAGS.enableBubbleSwipeUpCleanup();
    }


    public static boolean enableBubbleTaskViewListener() {
        
        return FEATURE_FLAGS.enableBubbleTaskViewListener();
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


    public static boolean enableEnterSplitRemoveBubble() {
        
        return FEATURE_FLAGS.enableEnterSplitRemoveBubble();
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


    public static boolean enablePipBoxShadows() {
        
        return FEATURE_FLAGS.enablePipBoxShadows();
    }


    public static boolean enablePipUmoExperience() {
        
        return FEATURE_FLAGS.enablePipUmoExperience();
    }


    public static boolean enableRecentsBookendTransition() {
        
        return FEATURE_FLAGS.enableRecentsBookendTransition();
    }


    public static boolean enableRetrievableBubbles() {
        
        return FEATURE_FLAGS.enableRetrievableBubbles();
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


    public static boolean fixMissingUserChangeCallbacks() {
        
        return FEATURE_FLAGS.fixMissingUserChangeCallbacks();
    }


    public static boolean onlyReuseBubbledTaskWhenLaunchedFromBubble() {
        
        return FEATURE_FLAGS.onlyReuseBubbledTaskWhenLaunchedFromBubble();
    }


    public static boolean taskViewRepository() {
        
        return FEATURE_FLAGS.taskViewRepository();
    }


    public static boolean taskViewTransitionsRefactor() {
        
        return FEATURE_FLAGS.taskViewTransitionsRefactor();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
