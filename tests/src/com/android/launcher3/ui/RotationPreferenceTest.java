package com.android.launcher3.ui;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Test for auto rotate preference.
 */
@MediumTest
public class RotationPreferenceTest extends LauncherInstrumentationTestCase {

    private SharedPreferences mPrefs;
    private boolean mOriginalRotationValue;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = UiDevice.getInstance(getInstrumentation());
        mTargetContext = getInstrumentation().getTargetContext();
        mTargetPackage = mTargetContext.getPackageName();
        mPrefs = Utilities.getPrefs(mTargetContext);
        mOriginalRotationValue = mPrefs.getBoolean(Utilities.ALLOW_ROTATION_PREFERENCE_KEY, false);
    }

    @Override
    protected void tearDown() throws Exception {
        setRotationEnabled(mOriginalRotationValue);
        super.tearDown();
    }

    public void testRotation_disabled() throws Exception {
        if (mTargetContext.getResources().getBoolean(R.bool.allow_rotation)) {
            // This is a tablet. The test is only valid to mobile devices.
            return;
        }

        setRotationEnabled(false);
        mDevice.setOrientationRight();
        startLauncher();

        Rect hotseat = getHotseatBounds();
        assertTrue(hotseat.width() > hotseat.height());
    }

    public void testRotation_enabled() throws Exception {
        if (mTargetContext.getResources().getBoolean(R.bool.allow_rotation)) {
            // This is a tablet. The test is only valid to mobile devices.
            return;
        }

        setRotationEnabled(true);
        mDevice.setOrientationRight();
        startLauncher();

        Rect hotseat = getHotseatBounds();
        assertTrue(hotseat.width() < hotseat.height());
    }

    private void setRotationEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(Utilities.ALLOW_ROTATION_PREFERENCE_KEY, enabled).commit();
    }

    private Rect getHotseatBounds() throws Exception {
        UiObject hotseat = mDevice.findObject(
                new UiSelector().resourceId(mTargetPackage + ":id/hotseat"));
        hotseat.waitForExists(6000);
        return hotseat.getVisibleBounds();
    }
}
