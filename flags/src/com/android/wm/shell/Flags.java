package com.android.wm.shell;
// TODO(b/303773055): Remove the annotation after access issue is resolved.
/** @hide */
public final class Flags {
    /** @hide */
    public static final String FLAG_ANIMATE_BUBBLE_SIZE_CHANGE = "com.android.wm.shell.animate_bubble_size_change";
    /** @hide */
    public static final String FLAG_ENABLE_APP_PAIRS = "com.android.wm.shell.enable_app_pairs";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_ANYTHING = "com.android.wm.shell.enable_bubble_anything";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_BAR = "com.android.wm.shell.enable_bubble_bar";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLE_STASHING = "com.android.wm.shell.enable_bubble_stashing";
    /** @hide */
    public static final String FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE = "com.android.wm.shell.enable_bubbles_long_press_nav_handle";
    /** @hide */
    public static final String FLAG_ENABLE_LEFT_RIGHT_SPLIT_IN_PORTRAIT = "com.android.wm.shell.enable_left_right_split_in_portrait";
    /** @hide */
    public static final String FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS = "com.android.wm.shell.enable_new_bubble_animations";
    /** @hide */
    public static final String FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW = "com.android.wm.shell.enable_optional_bubble_overflow";
    /** @hide */
    public static final String FLAG_ENABLE_PIP2_IMPLEMENTATION = "com.android.wm.shell.enable_pip2_implementation";
    /** @hide */
    public static final String FLAG_ENABLE_PIP_UMO_EXPERIENCE = "com.android.wm.shell.enable_pip_umo_experience";
    /** @hide */
    public static final String FLAG_ENABLE_RETRIEVABLE_BUBBLES = "com.android.wm.shell.enable_retrievable_bubbles";
    /** @hide */
    public static final String FLAG_ENABLE_SPLIT_CONTEXTUAL = "com.android.wm.shell.enable_split_contextual";
    /** @hide */
    public static final String FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION = "com.android.wm.shell.enable_taskbar_navbar_unification";
    /** @hide */
    public static final String FLAG_ENABLE_TINY_TASKBAR = "com.android.wm.shell.enable_tiny_taskbar";
    /** @hide */
    public static final String FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE = "com.android.wm.shell.only_reuse_bubbled_task_when_launched_from_bubble";
    
    
    public static boolean animateBubbleSizeChange() {
        return FEATURE_FLAGS.animateBubbleSizeChange();
    }
    
    
    public static boolean enableAppPairs() {
        return FEATURE_FLAGS.enableAppPairs();
    }
    
    
    public static boolean enableBubbleAnything() {
        return FEATURE_FLAGS.enableBubbleAnything();
    }
    
    
    public static boolean enableBubbleBar() {
        return FEATURE_FLAGS.enableBubbleBar();
    }
    
    
    public static boolean enableBubbleStashing() {
        return FEATURE_FLAGS.enableBubbleStashing();
    }
    
    
    public static boolean enableBubblesLongPressNavHandle() {
        return FEATURE_FLAGS.enableBubblesLongPressNavHandle();
    }
    
    
    public static boolean enableLeftRightSplitInPortrait() {
        return FEATURE_FLAGS.enableLeftRightSplitInPortrait();
    }
    
    
    public static boolean enableNewBubbleAnimations() {
        return FEATURE_FLAGS.enableNewBubbleAnimations();
    }
    
    
    public static boolean enableOptionalBubbleOverflow() {
        return FEATURE_FLAGS.enableOptionalBubbleOverflow();
    }
    
    public static boolean enablePip2Implementation() {
        return FEATURE_FLAGS.enablePip2Implementation();
    }
    
    
    public static boolean enablePipUmoExperience() {
        return FEATURE_FLAGS.enablePipUmoExperience();
    }
    
    
    public static boolean enableRetrievableBubbles() {
        return FEATURE_FLAGS.enableRetrievableBubbles();
    }
    
    
    public static boolean enableSplitContextual() {
        return FEATURE_FLAGS.enableSplitContextual();
    }
    
    
    public static boolean enableTaskbarNavbarUnification() {
        return FEATURE_FLAGS.enableTaskbarNavbarUnification();
    }
    
    
    public static boolean enableTinyTaskbar() {
        return FEATURE_FLAGS.enableTinyTaskbar();
    }
    
    
    public static boolean onlyReuseBubbledTaskWhenLaunchedFromBubble() {
        return FEATURE_FLAGS.onlyReuseBubbledTaskWhenLaunchedFromBubble();
    }

    private static FeatureFlags FEATURE_FLAGS = new FeatureFlagsImpl();

}
