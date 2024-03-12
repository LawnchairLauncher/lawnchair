/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.quickstep;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.systemui.shared.rotation.RotationButton;
import com.android.systemui.shared.rotation.RotationButtonController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

/** SysUI equivalent */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class NavigationBarRotationContextTest {
    private static final int DEFAULT_ROTATE = 0;
    private static final int DEFAULT_DISPLAY = 0;


    private RotationButtonController mRotationButtonController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Context mTargetContext = InstrumentationRegistry.getTargetContext();
        final View view = new View(mTargetContext);
        RotationButton rotationButton = mock(RotationButton.class);
        mRotationButtonController = new RotationButtonController(mTargetContext, 0, 0, 0, 0, 0, 0,
                () -> 0);
        mRotationButtonController.setRotationButton(rotationButton, null);
        // Due to a mockito issue, only spy the object after setting the initial state
        mRotationButtonController = spy(mRotationButtonController);
        final AnimatedVectorDrawable kbd = mock(AnimatedVectorDrawable.class);
        doReturn(view).when(rotationButton).getCurrentView();
        doReturn(true).when(rotationButton).acceptRotationProposal();
    }

    @Test
    public void testOnInvalidRotationProposal() {
        mRotationButtonController.onRotationProposal(DEFAULT_ROTATE + 1,
                false /* isValid */);
        verify(mRotationButtonController, times(1))
                .setRotateSuggestionButtonState(false /* visible */);
    }

    @Test
    public void testOnSameRotationProposal() {
        mRotationButtonController.onRotationProposal(DEFAULT_ROTATE,
                true /* isValid */);
        verify(mRotationButtonController, times(1))
                .setRotateSuggestionButtonState(false /* visible */);
    }

    @Test
    public void testOnRotationProposalShowButtonShowNav() {
        // No navigation bar should not call to set visibility state
        mRotationButtonController.onBehaviorChanged(DEFAULT_DISPLAY,
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        mRotationButtonController.onNavigationBarWindowVisibilityChange(false /* showing */);
        verify(mRotationButtonController, times(0)).setRotateSuggestionButtonState(
                false /* visible */);
        verify(mRotationButtonController, times(0)).setRotateSuggestionButtonState(
                true /* visible */);

        // No navigation bar with rotation change should not call to set visibility state
        mRotationButtonController.onRotationProposal(DEFAULT_ROTATE + 1,
                true /* isValid */);
        verify(mRotationButtonController, times(0)).setRotateSuggestionButtonState(
                false /* visible */);
        verify(mRotationButtonController, times(0)).setRotateSuggestionButtonState(
                true /* visible */);

        // Since rotation has changed rotation should be pending, show mButton when showing nav bar
        mRotationButtonController.onNavigationBarWindowVisibilityChange(true /* showing */);
        verify(mRotationButtonController, times(1)).setRotateSuggestionButtonState(
                true /* visible */);
    }

    @Test
    public void testOnRotationProposalShowButton() {
        // Navigation bar being visible should not call to set visibility state
        mRotationButtonController.onNavigationBarWindowVisibilityChange(true /* showing */);
        verify(mRotationButtonController, times(0))
                .setRotateSuggestionButtonState(false /* visible */);
        verify(mRotationButtonController, times(0))
                .setRotateSuggestionButtonState(true /* visible */);

        // Navigation bar is visible and rotation requested
        mRotationButtonController.onRotationProposal(DEFAULT_ROTATE + 1,
                true /* isValid */);
        verify(mRotationButtonController, times(1))
                .setRotateSuggestionButtonState(true /* visible */);
    }
}
