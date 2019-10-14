package com.android.launcher3.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.launcher3.config.FlagOverrideRule.FlagOverride;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Sample Robolectric test that demonstrates flag-overriding.
 */
@RunWith(RobolectricTestRunner.class)
public class FlagOverrideSampleTest {

    // Check out https://junit.org/junit4/javadoc/4.12/org/junit/Rule.html for more information
    // on @Rules.
    @Rule
    public final FlagOverrideRule flags = new FlagOverrideRule();

    /**
     * Test if flag can be overriden to true via annoation.
     */
    @FlagOverride(key = "FAKE_LANDSCAPE_UI", value = true)
    @Test
    public void withFlagOn() {
        assertTrue(FeatureFlags.FAKE_LANDSCAPE_UI.get());
    }

    /**
     * Test if flag can be overriden to false via annoation.
     */
    @FlagOverride(key = "FAKE_LANDSCAPE_UI", value = false)
    @Test
    public void withFlagOff() {
        assertFalse(FeatureFlags.FAKE_LANDSCAPE_UI.get());
    }
}
