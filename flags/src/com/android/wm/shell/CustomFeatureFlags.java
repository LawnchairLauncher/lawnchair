package com.android.wm.shell;

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

    public boolean bubbleViewInfoExecutors() {
        return getValue(Flags.FLAG_BUBBLE_VIEW_INFO_EXECUTORS,
            FeatureFlags::bubbleViewInfoExecutors);
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

    public boolean enableBubbleStashing() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_STASHING,
            FeatureFlags::enableBubbleStashing);
    }

    @Override

    public boolean enableBubbleTaskViewListener() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_TASK_VIEW_LISTENER,
            FeatureFlags::enableBubbleTaskViewListener);
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

    public boolean enablePipUmoExperience() {
        return getValue(Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            FeatureFlags::enablePipUmoExperience);
    }

    @Override

    public boolean enableRecentsBookendTransition() {
        return getValue(Flags.FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION,
            FeatureFlags::enableRecentsBookendTransition);
    }

    @Override

    public boolean enableRetrievableBubbles() {
        return getValue(Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            FeatureFlags::enableRetrievableBubbles);
    }

    @Override

    public boolean enableShellTopTaskTracking() {
        return getValue(Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            FeatureFlags::enableShellTopTaskTracking);
    }

    @Override

    public boolean enableTaskViewControllerCleanup() {
        return getValue(Flags.FLAG_ENABLE_TASK_VIEW_CONTROLLER_CLEANUP,
            FeatureFlags::enableTaskViewControllerCleanup);
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

    public boolean fixMissingUserChangeCallbacks() {
        return getValue(Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            FeatureFlags::fixMissingUserChangeCallbacks);
    }

    @Override

    public boolean onlyReuseBubbledTaskWhenLaunchedFromBubble() {
        return getValue(Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE,
            FeatureFlags::onlyReuseBubbledTaskWhenLaunchedFromBubble);
    }

    @Override

    public boolean taskViewRepository() {
        return getValue(Flags.FLAG_TASK_VIEW_REPOSITORY,
            FeatureFlags::taskViewRepository);
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
            Flags.FLAG_BUBBLE_VIEW_INFO_EXECUTORS,
            Flags.FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER,
            Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
            Flags.FLAG_ENABLE_BUBBLE_BAR,
            Flags.FLAG_ENABLE_BUBBLE_BAR_ON_PHONES,
            Flags.FLAG_ENABLE_BUBBLE_STASHING,
            Flags.FLAG_ENABLE_BUBBLE_TASK_VIEW_LISTENER,
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
            Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            Flags.FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION,
            Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            Flags.FLAG_ENABLE_TASK_VIEW_CONTROLLER_CLEANUP,
            Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
            Flags.FLAG_ENABLE_TASKBAR_ON_PHONES,
            Flags.FLAG_ENABLE_TINY_TASKBAR,
            Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE,
            Flags.FLAG_TASK_VIEW_REPOSITORY
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
        Arrays.asList(
            Flags.FLAG_BUBBLE_VIEW_INFO_EXECUTORS,
            Flags.FLAG_ENABLE_AUTO_TASK_STACK_CONTROLLER,
            Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
            Flags.FLAG_ENABLE_BUBBLE_BAR,
            Flags.FLAG_ENABLE_BUBBLE_BAR_ON_PHONES,
            Flags.FLAG_ENABLE_BUBBLE_STASHING,
            Flags.FLAG_ENABLE_BUBBLE_TASK_VIEW_LISTENER,
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
            Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
            Flags.FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION,
            Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
            Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING,
            Flags.FLAG_ENABLE_TASK_VIEW_CONTROLLER_CLEANUP,
            Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
            Flags.FLAG_ENABLE_TASKBAR_ON_PHONES,
            Flags.FLAG_ENABLE_TINY_TASKBAR,
            Flags.FLAG_FIX_MISSING_USER_CHANGE_CALLBACKS,
            Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE,
            Flags.FLAG_TASK_VIEW_REPOSITORY,
            ""
        )
    );
}
