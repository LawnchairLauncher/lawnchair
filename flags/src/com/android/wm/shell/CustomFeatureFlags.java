package com.android.wm.shell;


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

    public boolean bugRotationButtonCoverBubble() {
        return getValue(Flags.FLAG_BUG_ROTATION_BUTTON_COVER_BUBBLE,
            FeatureFlags::bugRotationButtonCoverBubble);
    }

    @Override

    public boolean dismissPipFromLockscreen() {
        return getValue(Flags.FLAG_DISMISS_PIP_FROM_LOCKSCREEN,
            FeatureFlags::dismissPipFromLockscreen);
    }

    @Override

    public boolean enable2x1Split() {
        return getValue(Flags.FLAG_ENABLE_2X1_SPLIT,
            FeatureFlags::enable2x1Split);
    }

    @Override

    public boolean enableAutoTaskStackController() {
        return getValue(Flags.FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER,
            FeatureFlags::enableAutoTaskStackController);
    }

    @Override

    public boolean enableBubbleAnything() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
            FeatureFlags::enableBubbleAnything);
    }

    @Override

    public boolean enableBubbleBar() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_BAR,
            FeatureFlags::enableBubbleBar);
    }

    @Override

    public boolean enableBubbleBarOnPhones() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_BAR_ON_PHONES,
            FeatureFlags::enableBubbleBarOnPhones);
    }

    @Override

    public boolean enableBubbleBarToFloatingTransition() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_BAR_TO_FLOATING_TRANSITION,
            FeatureFlags::enableBubbleBarToFloatingTransition);
    }

    @Override

    public boolean enableBubbleEventHistoryLogs() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS,
            FeatureFlags::enableBubbleEventHistoryLogs);
    }

    @Override

    public boolean enableBubbleStashing() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_STASHING,
            FeatureFlags::enableBubbleStashing);
    }

    @Override

    public boolean enableBubbleToFullscreen() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
            FeatureFlags::enableBubbleToFullscreen);
    }

    @Override

    public boolean enableBubblesLongPressNavHandle() {
        return getValue(Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE,
            FeatureFlags::enableBubblesLongPressNavHandle);
    }

    @Override

    public boolean enableCreateAnyBubble() {
        return getValue(Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
            FeatureFlags::enableCreateAnyBubble);
    }

    @Override

    public boolean enableDynamicInsetsForAppLaunch() {
        return getValue(Flags.FLAG_ENABLE_DYNAMIC_INSETS_FOR_APP_LAUNCH,
            FeatureFlags::enableDynamicInsetsForAppLaunch);
    }

    @Override

    public boolean enableFlexibleSplit() {
        return getValue(Flags.FLAG_ENABLE_FLEXIBLE_SPLIT,
            FeatureFlags::enableFlexibleSplit);
    }

    @Override

    public boolean enableFlexibleTwoAppSplit() {
        return getValue(Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT,
            FeatureFlags::enableFlexibleTwoAppSplit);
    }

    @Override

    public boolean enableGsf() {
        return getValue(Flags.FLAG_ENABLE_GSF,
            FeatureFlags::enableGsf);
    }

    @Override

    public boolean enableMagneticSplitDivider() {
        return getValue(Flags.FLAG_ENABLE_MAGNETIC_SPLIT_DIVIDER,
            FeatureFlags::enableMagneticSplitDivider);
    }

    @Override

    public boolean enableNewBubbleAnimations() {
        return getValue(Flags.FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS,
            FeatureFlags::enableNewBubbleAnimations);
    }

    @Override

    public boolean enableOptionalBubbleOverflow() {
        return getValue(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW,
            FeatureFlags::enableOptionalBubbleOverflow);
    }

    @Override

    public boolean enablePip2() {
        return getValue(Flags.FLAG_ENABLE_PIP2,
            FeatureFlags::enablePip2);
    }

    @Override

    public boolean enablePip2OnTv() {
        return getValue(Flags.FLAG_ENABLE_PIP2_ON_TV,
            FeatureFlags::enablePip2OnTv);
    }

    @Override

    public boolean enablePipBoxShadows() {
        return getValue(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS,
            FeatureFlags::enablePipBoxShadows);
    }

    @Override

    public boolean enablePipUmoExperience() {
        return getValue(Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            FeatureFlags::enablePipUmoExperience);
    }

    @Override

    public boolean enableRetrievableBubbles() {
        return getValue(Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            FeatureFlags::enableRetrievableBubbles);
    }

    @Override

    public boolean enableShellRestartBubbleCleanup() {
        return getValue(Flags.FLAG_ENABLE_SHELL_RESTART_BUBBLE_CLEANUP,
            FeatureFlags::enableShellRestartBubbleCleanup);
    }

    @Override

    public boolean enableShellTopTaskTracking() {
        return getValue(Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            FeatureFlags::enableShellTopTaskTracking);
    }

    @Override

    public boolean enableTaskbarNavbarUnification() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
            FeatureFlags::enableTaskbarNavbarUnification);
    }

    @Override

    public boolean enableTaskbarOnPhones() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_ON_PHONES,
            FeatureFlags::enableTaskbarOnPhones);
    }

    @Override

    public boolean enableTinyTaskbar() {
        return getValue(Flags.FLAG_ENABLE_TINY_TASKBAR,
            FeatureFlags::enableTinyTaskbar);
    }

    @Override

    public boolean fixBubbleStackViewExpandedWhenAdded() {
        return getValue(Flags.FLAG_FIX_BUBBLE_STACK_VIEW_EXPANDED_WHEN_ADDED,
            FeatureFlags::fixBubbleStackViewExpandedWhenAdded);
    }

    @Override

    public boolean fixBubblesAddSameBubbleBeingRemoved() {
        return getValue(Flags.FLAG_FIX_BUBBLES_ADD_SAME_BUBBLE_BEING_REMOVED,
            FeatureFlags::fixBubblesAddSameBubbleBeingRemoved);
    }

    @Override

    public boolean fixBubblesCancelAnimation() {
        return getValue(Flags.FLAG_FIX_BUBBLES_CANCEL_ANIMATION,
            FeatureFlags::fixBubblesCancelAnimation);
    }

    @Override

    public boolean fixBubblesExpandedSysuiFlag() {
        return getValue(Flags.FLAG_FIX_BUBBLES_EXPANDED_SYSUI_FLAG,
            FeatureFlags::fixBubblesExpandedSysuiFlag);
    }

    @Override

    public boolean fixBubblesImeFocusFlicker() {
        return getValue(Flags.FLAG_FIX_BUBBLES_IME_FOCUS_FLICKER,
            FeatureFlags::fixBubblesImeFocusFlicker);
    }

    @Override

    public boolean fixExitSplitOnEnterBubble() {
        return getValue(Flags.FLAG_FIX_EXIT_SPLIT_ON_ENTER_BUBBLE,
            FeatureFlags::fixExitSplitOnEnterBubble);
    }

    @Override

    public boolean fixMissingUserChangeCallbacks() {
        return getValue(Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            FeatureFlags::fixMissingUserChangeCallbacks);
    }

    @Override

    public boolean fixTaskViewRotationAnimation() {
        return getValue(Flags.FLAG_FIX_TASK_VIEW_ROTATION_ANIMATION,
            FeatureFlags::fixTaskViewRotationAnimation);
    }

    @Override

    public boolean splitDisableChildTaskBounds() {
        return getValue(Flags.FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS,
            FeatureFlags::splitDisableChildTaskBounds);
    }

    @Override

    public boolean splitToFullSetWindowMode() {
        return getValue(Flags.FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE,
            FeatureFlags::splitToFullSetWindowMode);
    }

    @Override

    public boolean taskViewTransitionsRefactor() {
        return getValue(Flags.FLAG_TASK_VIEW_TRANSITIONS_REFACTOR,
            FeatureFlags::taskViewTransitionsRefactor);
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
            Flags.FLAG_BUG_ROTATION_BUTTON_COVER_BUBBLE,
            Flags.FLAG_DISMISS_PIP_FROM_LOCKSCREEN,
            Flags.FLAG_ENABLE_2X1_SPLIT,
            Flags.FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER,
            Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
            Flags.FLAG_ENABLE_BUBBLE_BAR,
            Flags.FLAG_ENABLE_BUBBLE_BAR_ON_PHONES,
            Flags.FLAG_ENABLE_BUBBLE_BAR_TO_FLOATING_TRANSITION,
            Flags.FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS,
            Flags.FLAG_ENABLE_BUBBLE_STASHING,
            Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
            Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE,
            Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
            Flags.FLAG_ENABLE_DYNAMIC_INSETS_FOR_APP_LAUNCH,
            Flags.FLAG_ENABLE_FLEXIBLE_SPLIT,
            Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT,
            Flags.FLAG_ENABLE_GSF,
            Flags.FLAG_ENABLE_MAGNETIC_SPLIT_DIVIDER,
            Flags.FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS,
            Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW,
            Flags.FLAG_ENABLE_PIP2,
            Flags.FLAG_ENABLE_PIP2_ON_TV,
            Flags.FLAG_ENABLE_PIP_BOX_SHADOWS,
            Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            Flags.FLAG_ENABLE_SHELL_RESTART_BUBBLE_CLEANUP,
            Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
            Flags.FLAG_ENABLE_TASKBAR_ON_PHONES,
            Flags.FLAG_ENABLE_TINY_TASKBAR,
            Flags.FLAG_FIX_BUBBLE_STACK_VIEW_EXPANDED_WHEN_ADDED,
            Flags.FLAG_FIX_BUBBLES_ADD_SAME_BUBBLE_BEING_REMOVED,
            Flags.FLAG_FIX_BUBBLES_CANCEL_ANIMATION,
            Flags.FLAG_FIX_BUBBLES_EXPANDED_SYSUI_FLAG,
            Flags.FLAG_FIX_BUBBLES_IME_FOCUS_FLICKER,
            Flags.FLAG_FIX_EXIT_SPLIT_ON_ENTER_BUBBLE,
            Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            Flags.FLAG_FIX_TASK_VIEW_ROTATION_ANIMATION,
            Flags.FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS,
            Flags.FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE,
            Flags.FLAG_TASK_VIEW_TRANSITIONS_REFACTOR
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_BUG_ROTATION_BUTTON_COVER_BUBBLE,
            Flags.FLAG_DISMISS_PIP_FROM_LOCKSCREEN,
            Flags.FLAG_ENABLE_2X1_SPLIT,
            Flags.FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER,
            Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
            Flags.FLAG_ENABLE_BUBBLE_BAR,
            Flags.FLAG_ENABLE_BUBBLE_BAR_ON_PHONES,
            Flags.FLAG_ENABLE_BUBBLE_BAR_TO_FLOATING_TRANSITION,
            Flags.FLAG_ENABLE_BUBBLE_EVENT_HISTORY_LOGS,
            Flags.FLAG_ENABLE_BUBBLE_STASHING,
            Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
            Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE,
            Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
            Flags.FLAG_ENABLE_DYNAMIC_INSETS_FOR_APP_LAUNCH,
            Flags.FLAG_ENABLE_FLEXIBLE_SPLIT,
            Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT,
            Flags.FLAG_ENABLE_GSF,
            Flags.FLAG_ENABLE_MAGNETIC_SPLIT_DIVIDER,
            Flags.FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS,
            Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW,
            Flags.FLAG_ENABLE_PIP2,
            Flags.FLAG_ENABLE_PIP2_ON_TV,
            Flags.FLAG_ENABLE_PIP_BOX_SHADOWS,
            Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            Flags.FLAG_ENABLE_SHELL_RESTART_BUBBLE_CLEANUP,
            Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
            Flags.FLAG_ENABLE_TASKBAR_ON_PHONES,
            Flags.FLAG_ENABLE_TINY_TASKBAR,
            Flags.FLAG_FIX_BUBBLE_STACK_VIEW_EXPANDED_WHEN_ADDED,
            Flags.FLAG_FIX_BUBBLES_ADD_SAME_BUBBLE_BEING_REMOVED,
            Flags.FLAG_FIX_BUBBLES_CANCEL_ANIMATION,
            Flags.FLAG_FIX_BUBBLES_EXPANDED_SYSUI_FLAG,
            Flags.FLAG_FIX_BUBBLES_IME_FOCUS_FLICKER,
            Flags.FLAG_FIX_EXIT_SPLIT_ON_ENTER_BUBBLE,
            Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            Flags.FLAG_FIX_TASK_VIEW_ROTATION_ANIMATION,
            Flags.FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS,
            Flags.FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE,
            Flags.FLAG_TASK_VIEW_TRANSITIONS_REFACTOR,
            ""
        )
    );
}
