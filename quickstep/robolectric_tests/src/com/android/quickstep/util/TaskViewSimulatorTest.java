/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.util;

import static android.view.Display.DEFAULT_DISPLAY;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.view.Surface;
import android.view.SurfaceControl;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.shadows.LShadowDisplay;
import com.android.launcher3.util.DefaultDisplay;
import com.android.quickstep.LauncherActivityInterface;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDisplayManager;

@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class TaskViewSimulatorTest {

    @Test
    public void taskProperlyScaled_portrait_noRotation_sameInsets1() {
        new TaskMatrixVerifier()
                .withLauncherSize(1200, 2450)
                .withInsets(new Rect(0, 80, 0, 120))
                .verifyNoTransforms();
    }

    @Test
    public void taskProperlyScaled_portrait_noRotation_sameInsets2() {
        new TaskMatrixVerifier()
                .withLauncherSize(1200, 2450)
                .withInsets(new Rect(55, 80, 55, 120))
                .verifyNoTransforms();
    }

    @Test
    public void taskProperlyScaled_landscape_noRotation_sameInsets1() {
        new TaskMatrixVerifier()
                .withLauncherSize(2450, 1250)
                .withInsets(new Rect(0, 80, 0, 40))
                .verifyNoTransforms();
    }

    @Test
    public void taskProperlyScaled_landscape_noRotation_sameInsets2() {
        new TaskMatrixVerifier()
                .withLauncherSize(2450, 1250)
                .withInsets(new Rect(0, 80, 120, 0))
                .verifyNoTransforms();
    }

    @Test
    public void taskProperlyScaled_landscape_noRotation_sameInsets3() {
        new TaskMatrixVerifier()
                .withLauncherSize(2450, 1250)
                .withInsets(new Rect(55, 80, 55, 120))
                .verifyNoTransforms();
    }

    @Test
    public void taskProperlyScaled_landscape_rotated() {
        new TaskMatrixVerifier()
                .withLauncherSize(1200, 2450)
                .withInsets(new Rect(0, 80, 0, 120))
                .withAppBounds(
                        new Rect(0, 0, 2450, 1200),
                        new Rect(0, 80, 0, 120),
                        Surface.ROTATION_90)
                .verifyNoTransforms();
    }

    private static class TaskMatrixVerifier extends TransformParams {

        private final Context mContext = RuntimeEnvironment.application;

        private Rect mAppBounds = new Rect();
        private Rect mLauncherInsets = new Rect();

        private Rect mAppInsets;

        private int mAppRotation = -1;
        private DeviceProfile mDeviceProfile;

        TaskMatrixVerifier withLauncherSize(int width, int height) {
            ShadowDisplayManager.changeDisplay(DEFAULT_DISPLAY,
                    String.format("w%sdp-h%sdp-mdpi", width, height));
            if (mAppBounds.isEmpty()) {
                mAppBounds.set(0, 0, width, height);
            }
            return this;
        }

        TaskMatrixVerifier withInsets(Rect insets) {
            LShadowDisplay shadowDisplay = Shadow.extract(
                    mContext.getSystemService(DisplayManager.class).getDisplay(DEFAULT_DISPLAY));
            shadowDisplay.setInsets(insets);
            mLauncherInsets.set(insets);
            return this;
        }

        TaskMatrixVerifier withAppBounds(Rect bounds, Rect insets, int appRotation) {
            mAppBounds.set(bounds);
            mAppInsets = insets;
            mAppRotation = appRotation;
            return this;
        }

        void verifyNoTransforms() {
            mDeviceProfile = InvariantDeviceProfile.INSTANCE.get(mContext)
                    .getDeviceProfile(mContext);
            mDeviceProfile.updateInsets(mLauncherInsets);

            TaskViewSimulator tvs = new TaskViewSimulator(mContext,
                    LauncherActivityInterface.INSTANCE);
            tvs.setDp(mDeviceProfile);

            int launcherRotation = DefaultDisplay.INSTANCE.get(mContext).getInfo().rotation;
            if (mAppRotation < 0) {
                mAppRotation = launcherRotation;
            }
            tvs.setLayoutRotation(launcherRotation, mAppRotation);
            if (mAppInsets == null) {
                mAppInsets = new Rect(mLauncherInsets);
            }
            tvs.setPreviewBounds(mAppBounds, mAppInsets);

            tvs.fullScreenProgress.value = 1;
            tvs.recentsViewScale.value = tvs.getFullScreenScale();
            tvs.apply(this);
        }

        @Override
        public SurfaceParams[] createSurfaceParams(BuilderProxy proxy) {
            SurfaceParams.Builder builder = new SurfaceParams.Builder((SurfaceControl) null);
            proxy.onBuildTargetParams(builder, null, this);
            return new SurfaceParams[] {builder.build()};
        }

        @Override
        public void applySurfaceParams(SurfaceParams[] params) {
            // Verify that the task position remains the same
            RectF newAppBounds = new RectF(mAppBounds);
            params[0].matrix.mapRect(newAppBounds);
            Assert.assertThat(newAppBounds, new AlmostSame(mAppBounds));

            System.err.println("Bounds mapped: " + mAppBounds + " => " + newAppBounds);
        }
    }

    private static class AlmostSame extends TypeSafeMatcher<RectF>  {

        // Allow 1px error margin to account for float to int conversions
        private final float mError = 1f;
        private final Rect mExpected;

        AlmostSame(Rect expected) {
            mExpected = expected;
        }

        @Override
        protected boolean matchesSafely(RectF item) {
            return Math.abs(item.left - mExpected.left) < mError
                    && Math.abs(item.top - mExpected.top) < mError
                    && Math.abs(item.right - mExpected.right) < mError
                    && Math.abs(item.bottom - mExpected.bottom) < mError;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(mExpected);
        }
    }
}
