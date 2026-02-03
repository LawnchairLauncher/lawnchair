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

package com.android.wm.shell.pip2.phone;

import static com.android.internal.jank.Cuj.CUJ_PIP_TRANSITION;

import android.annotation.IntDef;
import android.content.Context;
import android.os.Handler;
import android.view.SurfaceControl;

import com.android.internal.jank.InteractionJankMonitor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Helps track PIP CUJ interactions
 */
public class PipInteractionHandler {
    @IntDef(prefix = {"INTERACTION_"}, value = {
            INTERACTION_EXIT_PIP,
            INTERACTION_EXIT_PIP_TO_SPLIT
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface Interaction {}

    public static final int INTERACTION_EXIT_PIP = 0;
    public static final int INTERACTION_EXIT_PIP_TO_SPLIT = 1;

    private final Context mContext;
    private final Handler mHandler;
    private final InteractionJankMonitor mInteractionJankMonitor;

    public PipInteractionHandler(Context context, Handler handler,
            InteractionJankMonitor interactionJankMonitor) {
        mContext = context;
        mHandler = handler;
        mInteractionJankMonitor = interactionJankMonitor;
    }

    /**
     * Begin tracking PIP CUJ.
     *
     * @param leash PIP leash.
     * @param interaction Tag for interaction.
     */
    public void begin(SurfaceControl leash, @Interaction int interaction) {
        mInteractionJankMonitor.begin(leash, mContext, mHandler, CUJ_PIP_TRANSITION,
                pipInteractionToString(interaction));
    }

    /**
     * End tracking CUJ.
     */
    public void end() {
        mInteractionJankMonitor.end(CUJ_PIP_TRANSITION);
    }

    /**
     * Converts an interaction to a string representation used for tagging.
     *
     * @param interaction Interaction to track.
     * @return String representation of the interaction.
     */
    public static String pipInteractionToString(@Interaction int interaction) {
        return switch (interaction) {
            case INTERACTION_EXIT_PIP -> "EXIT_PIP";
            case INTERACTION_EXIT_PIP_TO_SPLIT -> "EXIT_PIP_TO_SPLIT";
            default -> "";
        };
    }
}
