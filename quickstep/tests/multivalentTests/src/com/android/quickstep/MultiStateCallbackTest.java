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

package com.android.quickstep;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.util.LauncherMultivalentJUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

@SmallTest
@RunWith(LauncherMultivalentJUnit.class)
public class MultiStateCallbackTest {

    private int mFlagCount = 0;
    private int getNextStateFlag() {
        int index = 1 << mFlagCount;
        mFlagCount++;
        return index;
    }

    private final MultiStateCallback mMultiStateCallback = new MultiStateCallback(new String[0]);
    private final Runnable mCallback = spy(new Runnable() {
        @Override
        public void run() {}
    });
    private final Consumer<Boolean> mListener = spy(new Consumer<Boolean>() {
        @Override
        public void accept(Boolean isOn) {}
    });

    @Test
    public void testSetState_trackedProperly() {
        int watchedAnime = getNextStateFlag();

        assertThat(mMultiStateCallback.getState()).isEqualTo(0);
        assertThat(mMultiStateCallback.hasStates(watchedAnime)).isFalse();

        mMultiStateCallback.setState(watchedAnime);

        assertThat(mMultiStateCallback.getState()).isEqualTo(watchedAnime);
        assertThat(mMultiStateCallback.hasStates(watchedAnime)).isTrue();
    }

    @Test
    public void testSetState_withMultipleStates_trackedProperly() {
        int watchedAnime = getNextStateFlag();
        int sharedMemes = getNextStateFlag();

        mMultiStateCallback.setState(watchedAnime);
        mMultiStateCallback.setState(sharedMemes);

        assertThat(mMultiStateCallback.getState()).isEqualTo(watchedAnime | sharedMemes);
        assertThat(mMultiStateCallback.hasStates(watchedAnime)).isTrue();
        assertThat(mMultiStateCallback.hasStates(sharedMemes)).isTrue();
        assertThat(mMultiStateCallback.hasStates(watchedAnime | sharedMemes)).isTrue();
    }

    @Test
    public void testClearState_trackedProperly() {
        int lovedAnime = getNextStateFlag();

        mMultiStateCallback.setState(lovedAnime);
        mMultiStateCallback.clearState(lovedAnime);

        assertThat(mMultiStateCallback.getState()).isEqualTo(0);
        assertThat(mMultiStateCallback.hasStates(lovedAnime)).isFalse();
    }

    @Test
    public void testClearState_withMultipleState_trackedProperly() {
        int lovedAnime = getNextStateFlag();
        int talkedAboutAnime = getNextStateFlag();

        mMultiStateCallback.setState(lovedAnime);
        mMultiStateCallback.setState(talkedAboutAnime);
        mMultiStateCallback.clearState(talkedAboutAnime);

        assertThat(mMultiStateCallback.getState()).isEqualTo(lovedAnime);
        assertThat(mMultiStateCallback.hasStates(lovedAnime)).isTrue();
        assertThat(mMultiStateCallback.hasStates(talkedAboutAnime)).isFalse();
        assertThat(mMultiStateCallback.hasStates(lovedAnime | talkedAboutAnime)).isFalse();
    }

    @Test
    public void testCallbackDoesNotRun_withoutState() {
        int watchedOnePiece = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedOnePiece, mCallback);

        verify(mCallback, never()).run();
    }

    @Test
    public void testCallbackDoesNotRun_whenNotTracked() {
        int watchedJujutsuKaisen = getNextStateFlag();

        mMultiStateCallback.setState(watchedJujutsuKaisen);

        verify(mCallback, never()).run();
    }

    @Test
    public void testCallbackRuns_afterTrackedAndStateSet() {
        int watchedHunterXHunter = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedHunterXHunter, mCallback);
        mMultiStateCallback.setState(watchedHunterXHunter);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackRuns_onUiThread() {
        int watchedHunterXHunter = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedHunterXHunter, mCallback);
        mMultiStateCallback.setStateOnUiThread(watchedHunterXHunter);

        runOnMainSync(() -> verify(mCallback, times(1)).run());
    }

    @Test
    public void testCallbackRuns_agnosticallyToCallOrder() {
        int watchedFullMetalAlchemist = getNextStateFlag();

        mMultiStateCallback.setState(watchedFullMetalAlchemist);
        mMultiStateCallback.runOnceAtState(watchedFullMetalAlchemist, mCallback);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackRuns_onlyOnceAfterStateSet() {
        int watchedBleach = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedBleach, mCallback);
        mMultiStateCallback.setState(watchedBleach);
        mMultiStateCallback.setState(watchedBleach);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackRuns_onlyOnceAfterClearState() {
        int rememberedGreatShow = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(rememberedGreatShow, mCallback);
        mMultiStateCallback.setState(rememberedGreatShow);
        mMultiStateCallback.clearState(rememberedGreatShow);
        mMultiStateCallback.setState(rememberedGreatShow);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackDoesNotRun_withoutFullStateSet() {
        int watchedMobPsycho = getNextStateFlag();
        int watchedVinlandSaga = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedMobPsycho | watchedVinlandSaga, mCallback);
        mMultiStateCallback.setState(watchedMobPsycho);

        verify(mCallback, times(0)).run();
    }

    @Test
    public void testCallbackRuns_withFullStateSet_agnosticallyToCallOrder() {
        int watchedReZero = getNextStateFlag();
        int watchedJojosBizareAdventure = getNextStateFlag();

        mMultiStateCallback.setState(watchedJojosBizareAdventure);
        mMultiStateCallback.runOnceAtState(watchedReZero | watchedJojosBizareAdventure, mCallback);
        mMultiStateCallback.setState(watchedReZero);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackRuns_withFullStateSet_asIntegerMask() {
        int watchedPokemon = getNextStateFlag();
        int watchedDigimon = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedPokemon | watchedDigimon, mCallback);
        mMultiStateCallback.setState(watchedPokemon | watchedDigimon);

        verify(mCallback, times(1)).run();
    }

    @Test
    public void testCallbackDoesNotRun_afterClearState() {
        int watchedMonster = getNextStateFlag();
        int watchedPingPong = getNextStateFlag();

        mMultiStateCallback.runOnceAtState(watchedMonster | watchedPingPong, mCallback);
        mMultiStateCallback.setState(watchedMonster);
        mMultiStateCallback.clearState(watchedMonster);
        mMultiStateCallback.setState(watchedPingPong);

        verify(mCallback, times(0)).run();
    }

    @Test
    public void testlistenerRuns_multipleTimes() {
        int watchedSteinsGate = getNextStateFlag();

        mMultiStateCallback.addChangeListener(watchedSteinsGate, mListener);
        mMultiStateCallback.setState(watchedSteinsGate);

        // Called exactly one
        verify(mListener, times(1)).accept(anyBoolean());
        // Called exactly once with isOn = true
        verify(mListener, times(1)).accept(eq(true));
        // Never called with isOn = false
        verify(mListener, times(0)).accept(eq(false));

        mMultiStateCallback.clearState(watchedSteinsGate);

        // Called exactly twice
        verify(mListener, times(2)).accept(anyBoolean());
        // Called exactly once with isOn = true
        verify(mListener, times(1)).accept(eq(true));
        // Called exactly once with isOn = false
        verify(mListener, times(1)).accept(eq(false));
    }

    @Test
    public void testlistenerDoesNotRun_forUnchangedState() {
        int watchedSteinsGate = getNextStateFlag();

        mMultiStateCallback.addChangeListener(watchedSteinsGate, mListener);
        mMultiStateCallback.setState(watchedSteinsGate);
        mMultiStateCallback.setState(watchedSteinsGate);

        // State remained unchanged
        verify(mListener, times(1)).accept(anyBoolean());
        // Called exactly once with isOn = true
        verify(mListener, times(1)).accept(eq(true));
    }

    private static void runOnMainSync(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }
}
