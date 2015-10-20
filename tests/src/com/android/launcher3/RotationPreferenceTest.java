package com.android.launcher3;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.test.InstrumentationTestCase;

/**
 * Test for auto rotate preference.
 */
public class RotationPreferenceTest extends InstrumentationTestCase {

    private UiDevice mDevice;
    private Context mTargetContext;
    private String mTargetPackage;

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
        goToLauncher();

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
        goToLauncher();

        Rect hotseat = getHotseatBounds();
        assertTrue(hotseat.width() < hotseat.height());
    }

    private void goToLauncher() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mTargetPackage)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getContext().startActivity(homeIntent);
        mDevice.findObject(new UiSelector().packageName(mTargetPackage)).waitForExists(6000);
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
