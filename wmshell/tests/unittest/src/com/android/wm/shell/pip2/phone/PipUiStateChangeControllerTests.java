/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.Flags;
import android.app.PictureInPictureUiState;
import android.os.Bundle;
import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

/**
 * Unit test against {@link PipUiStateChangeController}.
 */
@RunWith(AndroidTestingRunner.class)
public class PipUiStateChangeControllerTests {

    @Mock
    private PipTransitionState mPipTransitionState;

    private Consumer<PictureInPictureUiState> mPictureInPictureUiStateConsumer;
    private ArgumentCaptor<PictureInPictureUiState> mArgumentCaptor;

    private PipUiStateChangeController mPipUiStateChangeController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPipUiStateChangeController = new PipUiStateChangeController(mPipTransitionState);
        mPictureInPictureUiStateConsumer = spy(pictureInPictureUiState -> {});
        mPipUiStateChangeController.setPictureInPictureUiStateConsumer(
                mPictureInPictureUiStateConsumer);
        mArgumentCaptor = ArgumentCaptor.forClass(PictureInPictureUiState.class);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_UI_STATE_CALLBACK_ON_ENTERING)
    public void onPipTransitionStateChanged_swipePipStart_callbackIsTransitioningToPipTrue() {
        when(mPipTransitionState.isInSwipePipToHomeTransition()).thenReturn(true);

        mPipUiStateChangeController.onPipTransitionStateChanged(
                PipTransitionState.UNDEFINED, PipTransitionState.SWIPING_TO_PIP, Bundle.EMPTY);

        verify(mPictureInPictureUiStateConsumer).accept(mArgumentCaptor.capture());
        assertTrue(mArgumentCaptor.getValue().isTransitioningToPip());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_UI_STATE_CALLBACK_ON_ENTERING)
    public void onPipTransitionStateChanged_swipePipOngoing_noCallbackIsTransitioningToPip() {
        when(mPipTransitionState.isInSwipePipToHomeTransition()).thenReturn(true);

        mPipUiStateChangeController.onPipTransitionStateChanged(
                PipTransitionState.SWIPING_TO_PIP, PipTransitionState.ENTERING_PIP, Bundle.EMPTY);

        verifyNoMoreInteractions(mPictureInPictureUiStateConsumer);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_UI_STATE_CALLBACK_ON_ENTERING)
    public void onPipTransitionStateChanged_swipePipFinish_callbackIsTransitioningToPipFalse() {
        when(mPipTransitionState.isInSwipePipToHomeTransition()).thenReturn(true);

        mPipUiStateChangeController.onPipTransitionStateChanged(
                PipTransitionState.SWIPING_TO_PIP, PipTransitionState.ENTERED_PIP, Bundle.EMPTY);

        verify(mPictureInPictureUiStateConsumer).accept(mArgumentCaptor.capture());
        assertFalse(mArgumentCaptor.getValue().isTransitioningToPip());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_UI_STATE_CALLBACK_ON_ENTERING)
    public void onPipTransitionStateChanged_tapHomeStart_callbackIsTransitioningToPipTrue() {
        when(mPipTransitionState.isInSwipePipToHomeTransition()).thenReturn(false);

        mPipUiStateChangeController.onPipTransitionStateChanged(
                PipTransitionState.UNDEFINED, PipTransitionState.ENTERING_PIP, Bundle.EMPTY);

        verify(mPictureInPictureUiStateConsumer).accept(mArgumentCaptor.capture());
        assertTrue(mArgumentCaptor.getValue().isTransitioningToPip());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_PIP_UI_STATE_CALLBACK_ON_ENTERING)
    public void onPipTransitionStateChanged_tapHomeFinish_callbackIsTransitioningToPipFalse() {
        when(mPipTransitionState.isInSwipePipToHomeTransition()).thenReturn(false);

        mPipUiStateChangeController.onPipTransitionStateChanged(
                PipTransitionState.ENTERING_PIP, PipTransitionState.ENTERED_PIP, Bundle.EMPTY);

        verify(mPictureInPictureUiStateConsumer).accept(mArgumentCaptor.capture());
        assertFalse(mArgumentCaptor.getValue().isTransitioningToPip());
    }
}
