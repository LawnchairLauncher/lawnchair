/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import android.annotation.IntegerRes;
import android.content.ComponentName;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Handler;
import android.os.VibrationEffect;
import android.util.SparseArray;

import com.android.systemui.plugins.VolumeDialogController.Callbacks;
import com.android.systemui.plugins.VolumeDialogController.State;
import com.android.systemui.plugins.VolumeDialogController.StreamState;
import com.android.systemui.plugins.annotations.DependsOn;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * Manages the VolumeDialog.
 *
 * Accessible through {@link PluginDependency}
 */
@ProvidesInterface(version = VolumeDialogController.VERSION)
@DependsOn(target = StreamState.class)
@DependsOn(target = State.class)
@DependsOn(target = Callbacks.class)
public interface VolumeDialogController {
    int VERSION = 1;

    void setActiveStream(int stream);
    void setStreamVolume(int stream, int userLevel);
    void setRingerMode(int ringerModeNormal, boolean external);

    boolean hasVibrator();
    void vibrate(VibrationEffect effect);
    void scheduleTouchFeedback();

    AudioManager getAudioManager();

    void notifyVisible(boolean visible);

    void addCallback(Callbacks callbacks, Handler handler);
    void removeCallback(Callbacks callbacks);

    void userActivity();
    void getState();

    /**
     * Get Captions enabled state
     *
     * @param checkForSwitchState set true when we'd like to switch captions enabled state after
     *                            getting the latest captions state.
     */
    void getCaptionsEnabledState(boolean checkForSwitchState);

    /**
     * Set Captions enabled state
     *
     * @param enabled the captions enabled state we'd like to update.
     */
    void setCaptionsEnabledState(boolean enabled);

    /**
     * Get Captions component state
     *
     * @param fromTooltip if it's triggered from tooltip.
     */
    void getCaptionsComponentState(boolean fromTooltip);

    @ProvidesInterface(version = StreamState.VERSION)
    public static final class StreamState {
        public static final int VERSION = 1;

        public boolean dynamic;
        public int level;
        public int levelMin;
        public int levelMax;
        public boolean muted;
        public boolean muteSupported;
        public @IntegerRes int name;
        public String remoteLabel;
        public boolean routedToBluetooth;

        public StreamState copy() {
            final StreamState rt = new StreamState();
            rt.dynamic = dynamic;
            rt.level = level;
            rt.levelMin = levelMin;
            rt.levelMax = levelMax;
            rt.muted = muted;
            rt.muteSupported = muteSupported;
            rt.name = name;
            rt.remoteLabel = remoteLabel;
            rt.routedToBluetooth = routedToBluetooth;
            return rt;
        }
    }

    @ProvidesInterface(version = State.VERSION)
    public static final class State {
        public static final int VERSION = 1;

        public static int NO_ACTIVE_STREAM = -1;

        public final SparseArray<StreamState> states = new SparseArray<>();

        public int ringerModeInternal;
        public int ringerModeExternal;
        public int zenMode;
        public ComponentName effectsSuppressor;
        public String effectsSuppressorName;
        public int activeStream = NO_ACTIVE_STREAM;
        public boolean disallowAlarms;
        public boolean disallowMedia;
        public boolean disallowSystem;
        public boolean disallowRinger;

        public State copy() {
            final State rt = new State();
            for (int i = 0; i < states.size(); i++) {
                rt.states.put(states.keyAt(i), states.valueAt(i).copy());
            }
            rt.ringerModeExternal = ringerModeExternal;
            rt.ringerModeInternal = ringerModeInternal;
            rt.zenMode = zenMode;
            if (effectsSuppressor != null) {
                rt.effectsSuppressor = effectsSuppressor.clone();
            }
            rt.effectsSuppressorName = effectsSuppressorName;
            rt.activeStream = activeStream;
            rt.disallowAlarms = disallowAlarms;
            rt.disallowMedia = disallowMedia;
            rt.disallowSystem = disallowSystem;
            rt.disallowRinger = disallowRinger;
            return rt;
        }

        @Override
        public String toString() {
            return toString(0);
        }

        public String toString(int indent) {
            final StringBuilder sb = new StringBuilder("{");
            if (indent > 0) sep(sb, indent);
            for (int i = 0; i < states.size(); i++) {
                if (i > 0) {
                    sep(sb, indent);
                }
                final int stream = states.keyAt(i);
                final StreamState ss = states.valueAt(i);
                sb.append(AudioSystem.streamToString(stream)).append(":").append(ss.level)
                        .append('[').append(ss.levelMin).append("..").append(ss.levelMax)
                        .append(']');
                if (ss.muted) sb.append(" [MUTED]");
                if (ss.dynamic) sb.append(" [DYNAMIC]");
            }
            sep(sb, indent); sb.append("ringerModeExternal:").append(ringerModeExternal);
            sep(sb, indent); sb.append("ringerModeInternal:").append(ringerModeInternal);
            sep(sb, indent); sb.append("zenMode:").append(zenMode);
            sep(sb, indent); sb.append("effectsSuppressor:").append(effectsSuppressor);
            sep(sb, indent); sb.append("effectsSuppressorName:").append(effectsSuppressorName);
            sep(sb, indent); sb.append("activeStream:").append(activeStream);
            sep(sb, indent); sb.append("disallowAlarms:").append(disallowAlarms);
            sep(sb, indent); sb.append("disallowMedia:").append(disallowMedia);
            sep(sb, indent); sb.append("disallowSystem:").append(disallowSystem);
            sep(sb, indent); sb.append("disallowRinger:").append(disallowRinger);
            if (indent > 0) sep(sb, indent);
            return sb.append('}').toString();
        }

        private static void sep(StringBuilder sb, int indent) {
            if (indent > 0) {
                sb.append('\n');
                for (int i = 0; i < indent; i++) {
                    sb.append(' ');
                }
            } else {
                sb.append(',');
            }
        }
    }

    @ProvidesInterface(version = Callbacks.VERSION)
    public interface Callbacks {
        int VERSION = 2;

        // requires version 1
        void onShowRequested(int reason, boolean keyguardLocked, int lockTaskModeState);
        void onDismissRequested(int reason);
        void onStateChanged(State state);
        void onLayoutDirectionChanged(int layoutDirection);
        void onConfigurationChanged();
        void onShowVibrateHint();
        void onShowSilentHint();
        void onScreenOff();
        void onShowSafetyWarning(int flags);
        void onAccessibilityModeChanged(Boolean showA11yStream);

        /**
         * Callback function for captions component state changed event
         *
         * @param isComponentEnabled the lateset captions component state.
         * @param fromTooltip if it's triggered from tooltip.
         */
        void onCaptionComponentStateChanged(Boolean isComponentEnabled, Boolean fromTooltip);

        /**
         * Callback function for captions enabled state changed event
         *
         * @param isEnabled the lateset captions enabled state.
         * @param checkBeforeSwitch intend to switch captions enabled state after the callback.
         */
        void onCaptionEnabledStateChanged(Boolean isEnabled, Boolean checkBeforeSwitch);
        // requires version 2
        void onShowCsdWarning(@AudioManager.CsdWarning int csdWarning, int durationMs);

        /**
         * Callback function for when the volume changed due to a physical key press.
         */
        void onVolumeChangedFromKey();
    }
}
