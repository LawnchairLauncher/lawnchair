package com.android.systemui.shared;

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
    
    public boolean bouncerAreaExclusion() {
        return getValue(Flags.FLAG_BOUNCER_AREA_EXCLUSION,
                FeatureFlags::bouncerAreaExclusion);
    }

    @Override
    
    public boolean enableHomeDelay() {
        return getValue(Flags.FLAG_ENABLE_HOME_DELAY,
                FeatureFlags::enableHomeDelay);
    }

    @Override
    
    public boolean exampleSharedFlag() {
        return getValue(Flags.FLAG_EXAMPLE_SHARED_FLAG,
                FeatureFlags::exampleSharedFlag);
    }

    @Override
    
    public boolean returnAnimationFrameworkLibrary() {
        return getValue(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
                FeatureFlags::returnAnimationFrameworkLibrary);
    }

    @Override
    
    public boolean shadeAllowBackGesture() {
        return getValue(Flags.FLAG_SHADE_ALLOW_BACK_GESTURE,
                FeatureFlags::shadeAllowBackGesture);
    }

    @Override
    
    public boolean sidefpsControllerRefactor() {
        return getValue(Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR,
                FeatureFlags::sidefpsControllerRefactor);
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
                Flags.FLAG_BOUNCER_AREA_EXCLUSION,
                Flags.FLAG_ENABLE_HOME_DELAY,
                Flags.FLAG_EXAMPLE_SHARED_FLAG,
                Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
                Flags.FLAG_SHADE_ALLOW_BACK_GESTURE,
                Flags.FLAG_SIDEFPS_CONTROLLER_REFACTOR
        );
    }

    private Set<String> mReadOnlyFlagsSet = new HashSet<>(
            Arrays.asList(
                    ""
            )
    );
}
