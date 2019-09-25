package com.android.launcher3.config;


import com.android.launcher3.config.FeatureFlags.BaseTogglableFlag;
import com.android.launcher3.uioverrides.TogglableFlag;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public Statement apply(Statement base, Description description) {
        return new MyStatement(base, description);
    }

    private class MyStatement extends Statement {

        private final Statement mBase;
        private final Description mDescription;


        MyStatement(Statement base, Description description) {
            mBase = base;
            mDescription = description;
        }

        @Override
        public void evaluate() throws Throwable {
            Map<String, BaseTogglableFlag> allFlags = FeatureFlags.getTogglableFlags().stream()
                    .collect(Collectors.toMap(TogglableFlag::getKey, Function.identity()));

            HashMap<BaseTogglableFlag, Boolean> changedValues = new HashMap<>();
            FlagOverride[] overrides = new FlagOverride[0];
            try {
                for (Annotation annotation : mDescription.getAnnotations()) {
                    if (annotation.annotationType() == FlagOverride.class) {
                        overrides = new FlagOverride[] { (FlagOverride) annotation };
                    } else if (annotation.annotationType() == FlagOverrides.class) {
                        // Note: this branch is hit if the annotation is repeated
                        overrides = ((FlagOverrides) annotation).value();
                    }
                }
                for (FlagOverride override : overrides) {
                    BaseTogglableFlag flag = allFlags.get(override.key());
                    changedValues.put(flag, flag.get());
                    flag.setForTests(override.value());
                }
                mBase.evaluate();
            } finally {
                // Clear the values
                changedValues.forEach(BaseTogglableFlag::setForTests);
            }
        }
    }
}
