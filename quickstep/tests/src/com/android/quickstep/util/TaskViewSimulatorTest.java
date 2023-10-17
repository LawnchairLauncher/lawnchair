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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.ArrayMap;
import android.view.RemoteAnimationTarget;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.DisplayController.Info;
import com.android.launcher3.util.LauncherModelHelper;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.ReflectionHelpers;
import com.android.launcher3.util.RotationUtils;
import com.android.launcher3.util.WindowBounds;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;
import com.android.quickstep.FallbackActivityInterface;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.SurfaceTransaction.MockProperties;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
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

        private Point mDisplaySize = new Point();
        private Rect mDisplayInsets = new Rect();
        private Rect mAppBounds = new Rect();
        private Rect mLauncherInsets = new Rect();

        private Rect mAppInsets;

        private int mAppRotation = -1;
        private DeviceProfile mDeviceProfile;

        TaskMatrixVerifier withLauncherSize(int width, int height) {
            mDisplaySize.set(width, height);
            if (mAppBounds.isEmpty()) {
                mAppBounds.set(0, 0, width, height);
            }
            return this;
        }

        TaskMatrixVerifier withInsets(Rect insets) {
            mDisplayInsets.set(insets);
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
            LauncherModelHelper helper = new LauncherModelHelper();
            try {
                helper.sandboxContext.allow(SystemUiProxy.INSTANCE);
                int rotation = mDisplaySize.x > mDisplaySize.y
                        ? Surface.ROTATION_90 : Surface.ROTATION_0;
                CachedDisplayInfo cdi =
                        new CachedDisplayInfo(mDisplaySize, rotation, new Rect());
                WindowBounds wm = new WindowBounds(
                        new Rect(0, 0, mDisplaySize.x, mDisplaySize.y),
                        mDisplayInsets);
                List<WindowBounds> allBounds = new ArrayList<>(4);
                for (int i = 0; i < 4; i++) {
                    Rect boundsR = new Rect(wm.bounds);
                    Rect insetsR = new Rect(wm.insets);

                    RotationUtils.rotateRect(insetsR, RotationUtils.deltaRotation(rotation, i));
                    RotationUtils.rotateRect(boundsR, RotationUtils.deltaRotation(rotation, i));
                    boundsR.set(0, 0, Math.abs(boundsR.width()), Math.abs(boundsR.height()));
                    allBounds.add(new WindowBounds(boundsR, insetsR));
                }

                WindowManagerProxy wmProxy = mock(WindowManagerProxy.class);
                doReturn(cdi).when(wmProxy).getDisplayInfo(any());
                doReturn(wm).when(wmProxy).getRealBounds(any(), any());
                doReturn(NavigationMode.NO_BUTTON).when(wmProxy).getNavigationMode(any());

                ArrayMap<CachedDisplayInfo, List<WindowBounds>> perDisplayBoundsCache =
                        new ArrayMap<>();
                perDisplayBoundsCache.put(cdi.normalize(), allBounds);

                DisplayController.Info mockInfo = new Info(
                        helper.sandboxContext, wmProxy, perDisplayBoundsCache);

                DisplayController controller =
                        DisplayController.INSTANCE.get(helper.sandboxContext);
                controller.close();
                ReflectionHelpers.setField(controller, "mInfo", mockInfo);

                mDeviceProfile = InvariantDeviceProfile.INSTANCE.get(helper.sandboxContext)
                        .getBestMatch(mAppBounds.width(), mAppBounds.height(), rotation);
                mDeviceProfile.updateInsets(mLauncherInsets);

                TaskViewSimulator tvs = new TaskViewSimulator(helper.sandboxContext,
                        FallbackActivityInterface.INSTANCE);
                tvs.setDp(mDeviceProfile);

                int launcherRotation = mockInfo.rotation;
                if (mAppRotation < 0) {
                    mAppRotation = launcherRotation;
                }

                tvs.getOrientationState().update(launcherRotation, mAppRotation);
                if (mAppInsets == null) {
                    mAppInsets = new Rect(mLauncherInsets);
                }
                tvs.setPreviewBounds(mAppBounds, mAppInsets);

                tvs.fullScreenProgress.value = 1;
                tvs.recentsViewScale.value = tvs.getFullScreenScale();
                tvs.apply(this);
            } finally {
                helper.destroy();
            }
        }

        @Override
        public SurfaceTransaction createSurfaceParams(BuilderProxy proxy) {
            RecordingSurfaceTransaction transaction = new RecordingSurfaceTransaction();
            proxy.onBuildTargetParams(
                    transaction.mockProperties, mock(RemoteAnimationTarget.class), this);
            return transaction;
        }

        @Override
        public void applySurfaceParams(SurfaceTransaction params) {
            Assert.assertTrue(params instanceof RecordingSurfaceTransaction);
            MockProperties p = ((RecordingSurfaceTransaction) params).mockProperties;

            // Verify that the task position remains the same
            RectF newAppBounds = new RectF(mAppBounds);
            p.matrix.mapRect(newAppBounds);
            Assert.assertThat(newAppBounds, new AlmostSame(mAppBounds));

            System.err.println("Bounds mapped: " + mAppBounds + " => " + newAppBounds);
        }
    }

    private static class AlmostSame extends TypeSafeMatcher<RectF>  {

        // Allow .1% error margin to account for float to int conversions
        private final float mErrorFactor = .001f;
        private final Rect mExpected;

        AlmostSame(Rect expected) {
            mExpected = expected;
        }

        @Override
        protected boolean matchesSafely(RectF item) {
            float errorWidth = mErrorFactor * mExpected.width();
            float errorHeight = mErrorFactor * mExpected.height();
            return Math.abs(item.left - mExpected.left) < errorWidth
                    && Math.abs(item.top - mExpected.top) < errorHeight
                    && Math.abs(item.right - mExpected.right) < errorWidth
                    && Math.abs(item.bottom - mExpected.bottom) < errorHeight;
        }

        @Override
        public void describeTo(Description description) {
            description.appendValue(mExpected);
        }
    }
}
