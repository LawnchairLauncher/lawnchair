/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.systemui.unfold.util;

import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages progress listeners that can have smaller lifespan than the unfold animation.
 * Allows to limit getting transition updates to only when
 * {@link ScopedUnfoldTransitionProgressProvider#setReadyToHandleTransition} is called
 * with readyToHandleTransition = true
 *
 * If the transition has already started by the moment when the clients are ready to play
 * the transition then it will report transition started callback and current animation progress.
 */
public final class ScopedUnfoldTransitionProgressProvider implements
        UnfoldTransitionProgressProvider, TransitionProgressListener {

    private static final float PROGRESS_UNSET = -1f;

    @Nullable
    private UnfoldTransitionProgressProvider mSource;

    private final List<TransitionProgressListener> mListeners = new ArrayList<>();

    private boolean mIsReadyToHandleTransition;
    private boolean mIsTransitionRunning;
    private float mLastTransitionProgress = PROGRESS_UNSET;

    public ScopedUnfoldTransitionProgressProvider() {
        this(null);
    }

    public ScopedUnfoldTransitionProgressProvider(
            @Nullable UnfoldTransitionProgressProvider source) {
        setSourceProvider(source);
    }

    /**
     * Sets the source for the unfold transition progress updates,
     * it replaces current provider if it is already set
     * @param provider transition provider that emits transition progress updates
     */
    public void setSourceProvider(@Nullable UnfoldTransitionProgressProvider provider) {
        if (mSource != null) {
            mSource.removeCallback(this);
        }

        if (provider != null) {
            mSource = provider;
            mSource.addCallback(this);
        } else {
            mSource = null;
        }
    }

    /**
     * Allows to notify this provide whether the listeners can play the transition or not.
     * Call this method with readyToHandleTransition = true when all listeners
     * are ready to consume the transition progress events.
     * Call it with readyToHandleTransition = false when listeners can't process the events.
     */
    public void setReadyToHandleTransition(boolean isReadyToHandleTransition) {
        if (mIsTransitionRunning) {
            if (isReadyToHandleTransition) {
                mListeners.forEach(TransitionProgressListener::onTransitionStarted);

                if (mLastTransitionProgress != PROGRESS_UNSET) {
                    mListeners.forEach(listener ->
                            listener.onTransitionProgress(mLastTransitionProgress));
                }
            } else {
                mIsTransitionRunning = false;
                mListeners.forEach(TransitionProgressListener::onTransitionFinished);
            }
        }

        mIsReadyToHandleTransition = isReadyToHandleTransition;
    }

    @Override
    public void addCallback(@NonNull TransitionProgressListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeCallback(@NonNull TransitionProgressListener listener) {
        mListeners.remove(listener);
    }

    @Override
    public void destroy() {
        mSource.removeCallback(this);
    }

    @Override
    public void onTransitionStarted() {
        this.mIsTransitionRunning = true;
        if (mIsReadyToHandleTransition) {
            mListeners.forEach(TransitionProgressListener::onTransitionStarted);
        }
    }

    @Override
    public void onTransitionProgress(float progress) {
        if (mIsReadyToHandleTransition) {
            mListeners.forEach(listener -> listener.onTransitionProgress(progress));
        }

        mLastTransitionProgress = progress;
    }

    @Override
    public void onTransitionFinished() {
        if (mIsReadyToHandleTransition) {
            mListeners.forEach(TransitionProgressListener::onTransitionFinished);
        }

        mIsTransitionRunning = false;
        mLastTransitionProgress = PROGRESS_UNSET;
    }
}
