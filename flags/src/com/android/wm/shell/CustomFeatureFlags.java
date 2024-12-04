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

    public boolean animateBubbleSizeChange() {
        return getValue(Flags.FLAG_ANIMATE_BUBBLE_SIZE_CHANGE,
                FeatureFlags::animateBubbleSizeChange);
    }

    @Override
    
    public boolean enableAppPairs() {
        return getValue(Flags.FLAG_ENABLE_APP_PAIRS,
                FeatureFlags::enableAppPairs);
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
    
    public boolean enableBubbleStashing() {
        return getValue(Flags.FLAG_ENABLE_BUBBLE_STASHING,
                FeatureFlags::enableBubbleStashing);
    }

    @Override
    
    public boolean enableBubblesLongPressNavHandle() {
        return getValue(Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE,
                FeatureFlags::enableBubblesLongPressNavHandle);
    }

    @Override
    
    public boolean enableLeftRightSplitInPortrait() {
        return getValue(Flags.FLAG_ENABLE_LEFT_RIGHT_SPLIT_IN_PORTRAIT,
                FeatureFlags::enableLeftRightSplitInPortrait);
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
    
    public boolean enablePip2Implementation() {
        return getValue(Flags.FLAG_ENABLE_PIP2_IMPLEMENTATION,
                FeatureFlags::enablePip2Implementation);
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
    
    public boolean enableSplitContextual() {
        return getValue(Flags.FLAG_ENABLE_SPLIT_CONTEXTUAL,
                FeatureFlags::enableSplitContextual);
    }

    @Override
    
    public boolean enableTaskbarNavbarUnification() {
        return getValue(Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
                FeatureFlags::enableTaskbarNavbarUnification);
    }

    @Override
    
    public boolean enableTinyTaskbar() {
        return getValue(Flags.FLAG_ENABLE_TINY_TASKBAR,
                FeatureFlags::enableTinyTaskbar);
    }

    @Override
    
    public boolean onlyReuseBubbledTaskWhenLaunchedFromBubble() {
        return getValue(Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE,
                FeatureFlags::onlyReuseBubbledTaskWhenLaunchedFromBubble);
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
                Flags.FLAG_ANIMATE_BUBBLE_SIZE_CHANGE,
                Flags.FLAG_ENABLE_APP_PAIRS,
                Flags.FLAG_ENABLE_BUBBLE_ANYTHING,
                Flags.FLAG_ENABLE_BUBBLE_BAR,
                Flags.FLAG_ENABLE_BUBBLE_STASHING,
                Flags.FLAG_ENABLE_BUBBLES_LONG_PRESS_NAV_HANDLE,
                Flags.FLAG_ENABLE_LEFT_RIGHT_SPLIT_IN_PORTRAIT,
                Flags.FLAG_ENABLE_NEW_BUBBLE_ANIMATIONS,
                Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW,
                Flags.FLAG_ENABLE_PIP2_IMPLEMENTATION,
                Flags.FLAG_ENABLE_PIP_UMO_EXPERIENCE,
                Flags.FLAG_ENABLE_RETRIEVABLE_BUBBLES,
                Flags.FLAG_ENABLE_SPLIT_CONTEXTUAL,
                Flags.FLAG_ENABLE_TASKBAR_NAVBAR_UNIFICATION,
                Flags.FLAG_ENABLE_TINY_TASKBAR,
                Flags.FLAG_ONLY_REUSE_BUBBLED_TASK_WHEN_LAUNCHED_FROM_BUBBLE
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
            Arrays.asList(
                    Flags.FLAG_ENABLE_PIP2_IMPLEMENTATION,
                    ""
            )
    );
}
