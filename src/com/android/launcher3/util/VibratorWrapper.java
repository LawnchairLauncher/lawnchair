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
package com.android.launcher3.util;

import static android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK;
import static android.os.VibrationEffect.createOneShot;
import static android.os.VibrationEffect.createPredefined;
import static android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;

import javax.inject.Inject;

import com.android.launcher3.Utilities;

/**
 * Wrapper around {@link Vibrator} to easily perform haptic feedback where necessary.
 */
@LauncherAppSingleton
public class VibratorWrapper {

    public static final DaggerSingletonObject<VibratorWrapper> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getVibratorWrapper);

    public static final AudioAttributes VIBRATION_ATTRS = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

    public static final VibrationEffect EFFECT_CLICK =
        Utilities.ATLEAST_Q ? createPredefined(VibrationEffect.EFFECT_CLICK) : createOneShot(1000L, VibrationEffect.DEFAULT_AMPLITUDE);
    @VisibleForTesting
    static final Uri HAPTIC_FEEDBACK_URI = Settings.System.getUriFor(HAPTIC_FEEDBACK_ENABLED);

    @VisibleForTesting static final float LOW_TICK_SCALE = 0.9f;

    /**
     * Haptic when entering overview.
     */
    public static final VibrationEffect OVERVIEW_HAPTIC = EFFECT_CLICK;

    private final Vibrator mVibrator;
    private final boolean mHasVibrator;

    @VisibleForTesting
    final SettingsCache.OnChangeListener mHapticChangeListener =
            isEnabled -> mIsHapticFeedbackEnabled = isEnabled;

    private boolean mIsHapticFeedbackEnabled;

    @Inject
    public VibratorWrapper(@ApplicationContext Context context, SettingsCache settingsCache,
            DaggerSingletonTracker tracker) {

        mVibrator = context.getSystemService(Vibrator.class);
        mHasVibrator = mVibrator.hasVibrator();
        if (mHasVibrator) {
            MAIN_EXECUTOR.execute(
                    () -> settingsCache.register(HAPTIC_FEEDBACK_URI, mHapticChangeListener));
            mIsHapticFeedbackEnabled = settingsCache.getValue(HAPTIC_FEEDBACK_URI, 0);
            tracker.addCloseable(
                    () -> settingsCache.unregister(HAPTIC_FEEDBACK_URI, mHapticChangeListener));
        } else {
            mIsHapticFeedbackEnabled = false;
        }
    }

    /**
     * This should be used to cancel a haptic in case where the haptic shouldn't be vibrating. For
     * example, when no animation is happening but a vibrator happens to be vibrating still.
     */
    public void cancelVibrate() {
        UI_HELPER_EXECUTOR.execute(mVibrator::cancel);
    }

    /** Vibrates with the given effect if haptic feedback is available and enabled. */
    public void vibrate(VibrationEffect vibrationEffect) {
        if (mHasVibrator && mIsHapticFeedbackEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> mVibrator.vibrate(vibrationEffect, VIBRATION_ATTRS));
        }
    }

    /**
     * Vibrates with a single primitive, if supported, or use a fallback effect instead. This only
     * vibrates if haptic feedback is available and enabled.
     */
    @SuppressLint("NewApi")
    public void vibrate(int primitiveId, float primitiveScale, VibrationEffect fallbackEffect) {
        if (mHasVibrator && mIsHapticFeedbackEnabled) {
            UI_HELPER_EXECUTOR.execute(() -> {
                if (primitiveId >= 0 && mVibrator.areAllPrimitivesSupported(primitiveId)) {
                    mVibrator.vibrate(VibrationEffect.startComposition()
                            .addPrimitive(primitiveId, primitiveScale)
                            .compose(), VIBRATION_ATTRS);
                } else {
                    mVibrator.vibrate(fallbackEffect, VIBRATION_ATTRS);
                }
            });
        }
    }

    /** Indicates that Taskbar has been invoked. */
    public void vibrateForTaskbarUnstash() {
        if (Utilities.ATLEAST_S && mVibrator.areAllPrimitivesSupported(PRIMITIVE_LOW_TICK)) {
            VibrationEffect primitiveLowTickEffect = VibrationEffect
                    .startComposition()
                    .addPrimitive(PRIMITIVE_LOW_TICK, LOW_TICK_SCALE)
                    .compose();

            vibrate(primitiveLowTickEffect);
        }
    }
}
