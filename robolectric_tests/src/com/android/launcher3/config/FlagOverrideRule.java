package com.android.launcher3.config;


import com.android.launcher3.config.BaseFlags.BaseTogglableFlag;
import com.android.launcher3.uioverrides.TogglableFlag;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.robolectric.RuntimeEnvironment;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test rule that makes overriding flags in Robolectric tests easier. This rule clears all flags
 * before and after your test, avoiding one test method affecting subsequent methods.
 *
 * <p>Usage:
 * <pre>
 * {@literal @}Rule public final FlagOverrideRule flags = new FlagOverrideRule();
 *
 * {@literal @}FlagOverride(flag = "FOO", value=true)
 * {@literal @}Test public void myTest() {
 *     ...
 * }
 * </pre>
 */
public final class FlagOverrideRule implements TestRule {

    /**
     * Container annotation for handling multiple {@link FlagOverride} annotations.
     * <p>
     * <p>Don't use this directly, use repeated {@link FlagOverride} annotations instead.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface FlagOverrides {
        FlagOverride[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    @Repeatable(FlagOverrides.class)
    public @interface FlagOverride {
        String key();

        boolean value();
    }

    private boolean ruleInProgress;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                FeatureFlags.initialize(RuntimeEnvironment.application.getApplicationContext());
                ruleInProgress = true;
                try {
                    clearOverrides();
                    applyAnnotationOverrides(description);
                    base.evaluate();
                } finally {
                    ruleInProgress = false;
                    clearOverrides();
                }
            }
        };
    }

    private void override(BaseTogglableFlag flag, boolean newValue) {
        if (!ruleInProgress) {
            throw new IllegalStateException(
                    "Rule isn't in progress. Did you remember to mark it with @Rule?");
        }
        flag.setForTests(newValue);
    }

    private void applyAnnotationOverrides(Description description) {
        for (Annotation annotation : description.getAnnotations()) {
            if (annotation.annotationType() == FlagOverride.class) {
                applyAnnotation((FlagOverride) annotation);
            } else if (annotation.annotationType() == FlagOverrides.class) {
                // Note: this branch is hit if the annotation is repeated
                for (FlagOverride flagOverride : ((FlagOverrides) annotation).value()) {
                    applyAnnotation(flagOverride);
                }
            }
        }
    }

    private void applyAnnotation(FlagOverride flagOverride) {
        boolean found = false;
        for (TogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            if (flag.getKey().equals(flagOverride.key())) {
                override(flag, flagOverride.value());
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("Flag " + flagOverride.key() + " not found");
        }
    }

    /**
     * Resets all flags to their default values.
     */
    private void clearOverrides() {
        for (BaseTogglableFlag flag : FeatureFlags.getTogglableFlags()) {
            flag.setForTests(flag.getDefaultValue());
        }
    }
}
