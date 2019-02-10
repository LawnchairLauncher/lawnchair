package com.android.launcher3.config;

import com.android.launcher3.config.FlagOverrideRule.FlagOverride;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Sample Robolectric test that demonstrates flag-overriding.
 */
@RunWith(RobolectricTestRunner.class)
public class FlagOverrideSampleTest {

    // Check out https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html for more information
    // on @Rules.
    @Rule
    public final FlagOverrideRule flags = new FlagOverrideRule();

    @FlagOverride(key = "EXAMPLE_FLAG", value = true)
    @FlagOverride(key = "QUICK_SWITCH", value = false)
    @Test
    public void withFlagOn() {
        assertTrue(FeatureFlags.EXAMPLE_FLAG.get());
        assertFalse(FeatureFlags.QUICK_SWITCH.get());
        assertFalse(FeatureFlags.STYLE_WALLPAPER.get());
    }


    @FlagOverride(key = "EXAMPLE_FLAG", value = false)
    @Test
    public void withFlagOff() {
        assertFalse(FeatureFlags.EXAMPLE_FLAG.get());
    }
}
