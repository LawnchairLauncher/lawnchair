/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.pip2;

import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.intThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.gui.BorderSettings;
import android.gui.BoxShadowSettings;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;
import android.view.SurfaceControl;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.common.BoxShadowHelper;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;


/**
 * Unit test against {@link PipSurfaceTransactionHelper}.
 */
@SmallTest
@TestableLooper.RunWithLooper
@EnableFlags(Flags.FLAG_ENABLE_PIP2)
@RunWith(ParameterizedAndroidJunit4.class)
public class PipSurfaceTransactionHelperTest {

    private static final int CORNER_RADIUS = 10;
    private static final int SHADOW_RADIUS = 20;
    private static final float MIRROR_OPACITY = 0.5f;
    private static final Rect PIP_BOUNDS = new Rect(0, 0, 500, 500);

    private final BoxShadowSettings mLightBoxShadowSettings = new BoxShadowSettings();
    private final BorderSettings mLightBorderSettings = new BorderSettings();
    private final BoxShadowSettings mDarkBoxShadowSettings = new BoxShadowSettings();
    private final BorderSettings mDarkBorderSettings = new BorderSettings();

    private static final int[] LIGHT_SHADOW_STYLES = {
            R.style.BoxShadowParamsPIPLight1, R.style.BoxShadowParamsPIPLight2};
    private static final int[] DARK_SHADOW_STYLES = {
            R.style.BoxShadowParamsPIPDark1, R.style.BoxShadowParamsPIPDark2};
    private static final int LIGHT_BORDER_STYLE = R.style.BorderSettingsPIPLight;
    private static final int DARK_BORDER_STYLE = R.style.BorderSettingsPIPDark;

    @Mock private Context mMockContext;
    @Mock private Resources mMockResources;
    @Mock private SurfaceControl.Transaction mMockTransaction;
    @Mock private ShellInit mMockShellInit;
    @Mock private PipDisplayLayoutState mMockPipDisplayLayoutState;
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private SurfaceControl mTestLeash;


    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
            .mockStatic(BoxShadowHelper.class)
            .mockStatic(PipUtils.class)
            .build();


    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_ENABLE_PIP_BOX_SHADOWS);
    }

    public PipSurfaceTransactionHelperTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getResources()).thenReturn(mMockResources);
        when(mMockResources.getDimensionPixelSize(eq(R.dimen.pip_corner_radius)))
                .thenReturn(CORNER_RADIUS);
        when(mMockResources.getDimensionPixelSize(eq(R.dimen.pip_shadow_radius)))
                .thenReturn(SHADOW_RADIUS);
        when(mMockResources.getFloat(eq(R.dimen.config_pipDraggingAcrossDisplaysOpacity)))
                .thenReturn(MIRROR_OPACITY);
        when(mMockTransaction.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);

        mPipSurfaceTransactionHelper = new PipSurfaceTransactionHelper(mMockContext,
                mMockShellInit, mMockPipDisplayLayoutState);
        // Directly call onInit instead of using ShellInit
        mPipSurfaceTransactionHelper.onInit();
        mTestLeash = new SurfaceControl.Builder()
                .setContainerLayer()
                .setName("PipSurfaceTransactionHelperTest")
                .setCallsite("PipSurfaceTransactionHelperTest")
                .build();

        when(mMockTransaction.setCornerRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setShadowRadius(any(SurfaceControl.class), anyFloat()))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setBoxShadowSettings(any(SurfaceControl.class),
                any(BoxShadowSettings.class)))
                .thenReturn(mMockTransaction);
        when(mMockTransaction.setBorderSettings(any(SurfaceControl.class),
                any(BorderSettings.class)))
                .thenReturn(mMockTransaction);

        when(BoxShadowHelper.getBoxShadowSettings(
                eq(mMockContext), aryEq(LIGHT_SHADOW_STYLES))).thenReturn(
                mLightBoxShadowSettings);
        when(BoxShadowHelper.getBorderSettings(
                eq(mMockContext), eq(LIGHT_BORDER_STYLE))).thenReturn(mLightBorderSettings);

        when(BoxShadowHelper.getBoxShadowSettings(
                eq(mMockContext), aryEq(DARK_SHADOW_STYLES))).thenReturn(mDarkBoxShadowSettings);
        when(BoxShadowHelper.getBorderSettings(
                eq(mMockContext), eq(DARK_BORDER_STYLE))).thenReturn(mDarkBorderSettings);


    }

    @Test
    public void round_doNotApply_setZeroCornerRadius() {
        mPipSurfaceTransactionHelper.round(mMockTransaction, mTestLeash, false /* apply */);

        verify(mMockTransaction).setCornerRadius(eq(mTestLeash), eq(0f));
    }

    @Test
    public void round_doApply_setExactCornerRadius() {
        mPipSurfaceTransactionHelper.round(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setCornerRadius(eq(mTestLeash), eq((float) CORNER_RADIUS));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS)
    public void shadow_doNotApply_setZeroShadowRadius() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, false /* apply */);

        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq(0f));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS)
    public void shadow_doApply_setExactShadowRadius() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setShadowRadius(eq(mTestLeash), eq((float) SHADOW_RADIUS));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS)
    public void shadow_flagEnabled_applyFalse_setsEmptyBoxShadowAndBorder() {
        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, false /* apply */);

        ArgumentCaptor<BoxShadowSettings> boxShadow = ArgumentCaptor.forClass(
                BoxShadowSettings.class);
        ArgumentCaptor<BorderSettings> border = ArgumentCaptor.forClass(BorderSettings.class);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash), boxShadow.capture());
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), border.capture());
        verify(mMockTransaction, never()).setShadowRadius(any(), anyFloat());

        assertEquals(0, boxShadow.getValue().boxShadows.length);
        assertEquals(0, border.getValue().strokeWidth, 0.0);
        assertEquals(0, border.getValue().color);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS)
    public void onThemeChanged_switchToDarkTheme_usesDarkSettingsOnShadow() {
        when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(true);

        mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);

        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash),
                eq(mDarkBoxShadowSettings));
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), eq(mDarkBorderSettings));
        verify(mMockTransaction, never()).setShadowRadius(any(), anyFloat());
    }


    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_BOX_SHADOWS)
    public void onThemeChanged_switchToLightTheme_usesLightSettingsOnShadow() {
        when(PipUtils.isDarkSystemTheme(mMockContext)).thenReturn(false);

        mPipSurfaceTransactionHelper.onThemeChanged(mMockContext);

        mPipSurfaceTransactionHelper.shadow(mMockTransaction, mTestLeash, true /* apply */);

        verify(mMockTransaction).setBoxShadowSettings(eq(mTestLeash),
                eq(mLightBoxShadowSettings));
        verify(mMockTransaction).setBorderSettings(eq(mTestLeash), eq(mLightBorderSettings));
        verify(mMockTransaction, never()).setShadowRadius(any(), anyFloat());
    }

    @Test
    public void setMirrorTransformations_setsAlphaAndLayer() {
        mPipSurfaceTransactionHelper.setMirrorTransformations(mMockTransaction, mTestLeash);

        verify(mMockTransaction).setAlpha(eq(mTestLeash), eq(MIRROR_OPACITY));
        verify(mMockTransaction).setLayer(eq(mTestLeash), eq(Integer.MAX_VALUE));
        verify(mMockTransaction).show(eq(mTestLeash));
    }

    @Test
    public void setPipTransformations_setsMatrixAndLayer() {
        mPipSurfaceTransactionHelper.setPipTransformations(mTestLeash, mMockTransaction, PIP_BOUNDS,
                PIP_BOUNDS, 0);

        verify(mMockTransaction).setMatrix(eq(mTestLeash), any(), any());
        verify(mMockTransaction).setLayer(eq(mTestLeash),
                intThat((layer) -> layer < Integer.MAX_VALUE));
    }
}
